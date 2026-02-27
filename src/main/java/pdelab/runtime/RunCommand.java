package pdelab.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import pdelab.core.Grid2D;
import pdelab.core.MMS;
import pdelab.core.DirichletBoundary;
import pdelab.core.Metrics;
import pdelab.core.ParallelExecutor;
import pdelab.solver.TimeStepper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "run", description = "Поднимает и крутит главный солвер PDE (дискретный)", mixinStandardHelpOptions = true)
public class RunCommand implements Callable<Integer> {
    private static final Logger log = LoggerFactory.getLogger(RunCommand.class);

    @Option(names = { "--config" }, required = true, description = "Путь до JSON-конфига со спеками симуляции")
    String configPath;

    @Option(names = { "--profile" }, description = "Врубить хардкорный профайлинг (например jfr)")
    String profile;

    @Option(names = { "--adaptive" }, description = "Врубить адаптивный шаг по времени (Step-Doubling LTE)")
    boolean adaptive;

    @Option(names = { "--adaptiveTol" }, description = "Толерантность LTE для адаптивного dt", defaultValue = "1e-3")
    double adaptiveTol;

    @Override
    public Integer call() throws Exception {
        boolean useJfr = "jfr".equalsIgnoreCase(profile);
        if (useJfr) {
            log.info("С CLI прилетел запрос на JFR профайлинг (ща запишем все)");
        }

        ObjectMapper mapper = new ObjectMapper();

        // Единый источник истины для конфига (Single source of truth)
        Config config = mapper.readValue(new File(configPath), Config.class);
        config.validate(); // Жесткая валидация спеков

        // Инициализируем (поднимаем базовые структуры, выделяем память) выделяем память лопатами (compute engine)
        // под пул с жирным количеством тредов
        int jvmProcs = Runtime.getRuntime().availableProcessors();
        int effectiveThreads = config.threads() == 0 ? jvmProcs : config.threads();
        if (effectiveThreads > jvmProcs) {
            log.warn("Запрошено потоков ({}) больше чем вообще есть ядер в JVM ({}). Ну, погнали, но будет больно.",
                    effectiveThreads,
                    jvmProcs);
        }
        log.info("Подняли ParallelExecutor на {} потоках (крутим ядра)", effectiveThreads);
        ParallelExecutor.init(effectiveThreads);

        // Реестр артефактов (собираем пруфы)
        ArtifactRegistry registry = new ArtifactRegistry(config.outDir() != null ? config.outDir() : "artifacts");
        registry.dumpEnv();

        // Дампим сырой конфиг прямо в артефакты для честной воспроизводимости
        Files.copy(Path.of(configPath), registry.getPath("config.json").toPath());

        // Дампим эффективный конфиг, учитывая все поднятые дефолты (типа авто-потоков)
        Config effectiveConfig = new Config(
                config.Nx(), config.Ny(), config.Lx(), config.Ly(), config.alpha(), config.T(), config.dt(),
                config.scheme(), config.maxIters(), config.tol(), effectiveThreads, config.outDir(), config.testCase(),
                config.preconditioner(), config.kappaAveraging() != null ? config.kappaAveraging() : "ARITHMETIC");
        mapper.writerWithDefaultPrettyPrinter().writeValue(registry.getPath("effective_config.json"), effectiveConfig);

        log.info("Врубаем матан! Симуляция погнала, трекаем в: {}", registry.getPath("").getAbsolutePath());

        // Сетап окружения (тут собираем моки) через JFR (JDK Flight Recorder, шоб всё как на ладони)
        // API
        jdk.jfr.Recording recording = null;
        if (useJfr) {
            try {
                recording = new jdk.jfr.Recording();
                recording.setToDisk(true);
                recording.setDestination(registry.getPath("profile.jfr").toPath());
                recording.start();
                log.info("JFR запись полетела (пишем логи железа).");
            } catch (Exception e) {
                log.error("Словили ошибку при старте JFR (проверьте что у вас JDK > 11 и есть права)", e);
            }
        }

        Grid2D grid = new Grid2D(config.Nx(), config.Ny(), config.Lx(), config.Ly());

        TimeStepper.Scheme scheme = config.scheme().equalsIgnoreCase("BE") ? TimeStepper.Scheme.BACKWARD_EULER
                : TimeStepper.Scheme.CRANK_NICOLSON;

        MMS.TestCase testCase = MMS.TestCase.HOMOGENEOUS;
        if (config.testCase() != null) {
            if (config.testCase().equalsIgnoreCase("NON_TRIVIAL")
                    || config.testCase().equalsIgnoreCase("NON_ZERO_DIRICHLET")) {
                testCase = MMS.TestCase.NON_ZERO_DIRICHLET;
            } else if (config.testCase().equalsIgnoreCase("VARIABLE_KAPPA")) {
                testCase = MMS.TestCase.VARIABLE_KAPPA;
            }
        }

        MMS mms = new MMS(testCase, config.alpha());

        double[] kFull = null;
        if (testCase == MMS.TestCase.VARIABLE_KAPPA) {
            kFull = new double[grid.size()];
            mms.evaluateKappa(grid, kFull);
        }

        TimeStepper stepper = new TimeStepper(
                grid, scheme, config.alpha(), config.dt(), config.maxIters(), config.tol(), kFull,
                config.preconditioner(), new DirichletBoundary(mms));

        stepper.initExact(0.0, mms);

        double t = 0.0;

        long wallStart = System.nanoTime();

        if (adaptive) {
            log.info("Подрубаем Адаптивный шаг по времени (Step-Doubling) с толерантностью LTE={}", adaptiveTol);
            double currentDt = config.dt();
            double p = scheme == TimeStepper.Scheme.CRANK_NICOLSON ? 2.0 : 1.0;
            double[] savedU = new double[grid.size()];
            double[] u1 = new double[grid.size()];

            int stepCount = 0;
            try (java.io.PrintWriter logWriter = new java.io.PrintWriter(registry.getPath("adaptive_log.csv"))) {
                logWriter.println("step,t,currentDt,error,accepted");
                while (t < config.T() - 1e-12) {
                    if (t + currentDt > config.T()) {
                        currentDt = config.T() - t;
                    }

                    stepper.copyState(savedU);

                    // Делаем один фулл-степ на currentDt
                    stepper.setDt(currentDt);
                    stepper.step(t, mms);
                    stepper.copyState(u1);

                    // Откатываем стейт и делаем два полушага currentDt/2
                    stepper.restoreState(savedU);
                    stepper.setDt(currentDt / 2.0);
                    stepper.step(t, mms);
                    stepper.step(t + currentDt / 2.0, mms);

                    double[] u2 = stepper.getU();
                    double error = Metrics.computeL2Error(grid, u1, u2) / (Math.pow(2.0, p) - 1.0);

                    boolean accepted = false;
                    if (error <= adaptiveTol || currentDt < 1e-7) { // принимаем шаг (посадка мягкая)
                        t += currentDt;
                        stepCount++;
                        accepted = true;
                        if (stepCount % (Math.max(1, (int) (config.T() / config.dt() / 10))) == 0) {
                            log.info(String.format("Адаптивный прогресс: t=%.4f/%.4f dt=%.2e err=%.2e", t, config.T(),
                                    currentDt, error));
                        }
                    } else { // откат (сломали математику)
                        stepper.restoreState(savedU);
                    }
                    logWriter.printf("%d,%f,%e,%e,%b%n", stepCount, t, currentDt, error, accepted);

                    if (error > 0.0) {
                        currentDt = currentDt * Math.pow(adaptiveTol / error, 1.0 / (p + 1.0));
                    } else {
                        currentDt *= 2.0;
                    }
                }
            }
            log.info("Закончили Адаптивный степпинг, сожрали {} шагов", stepCount);

        } else {
            int steps = (int) Math.round(config.T() / config.dt());
            for (int i = 0; i < steps; i++) {
                stepper.step(t, mms);
                t += config.dt();

                if (i % (steps / 10 + 1) == 0) {
                    log.info(String.format("Прогресс: %.1f%% (t=%.4f)", 100.0 * i / steps, t));
                }
            }
        }

        long wallEnd = System.nanoTime();
        double wallTimeSec = (wallEnd - wallStart) / 1e9;

        double[] uExact = new double[grid.size()];
        mms.evaluateExact(grid, t, uExact);

        double errorL2 = Metrics.computeL2Error(grid, stepper.getU(), uExact);
        double errorLinf = Metrics.computeLinfError(grid, stepper.getU(), uExact);
        double errorRel = Metrics.computeRelativeL2Error(grid, stepper.getU(), uExact);

        java.util.Map<String, Object> metrics = new java.util.HashMap<>();
        metrics.put("threadsRequested", config.threads());
        metrics.put("threadsEffective", effectiveThreads);
        metrics.put("chunkStrategy", "AUTO_DYNAMIC");
        metrics.put("wallTimeSeconds", wallTimeSec);
        metrics.put("totalPcgIters", stepper.getTotalPcgIters());
        metrics.put("maxAbsResidual", stepper.getMaxAbsResidual());
        metrics.put("maxRelResidual", stepper.getMaxRelResidual());
        metrics.put("errorL2", errorL2);
        metrics.put("errorLinf", errorLinf);
        metrics.put("errorRelL2", errorRel);

        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(registry.getPath("metrics.json").getAbsolutePath()),
                metrics);

        if (recording != null) {
            recording.stop();
            recording.close();
            log.info("JFR запись успешно сдамплена сюда: {}", registry.getPath("profile.jfr").getAbsolutePath());
        }

        log.info(String.format("Готово (Done)! Заняло времени: %.3f s, Ошибка L2: %e", wallTimeSec, errorL2));

        return 0;
    }
}
