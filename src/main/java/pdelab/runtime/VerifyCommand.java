package pdelab.runtime;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;

import pdelab.core.SpatialConvergenceVerifier;
import pdelab.core.TemporalConvergenceVerifier;
import pdelab.core.VariableKappaConvergenceVerifier;

@Command(name = "verify", description = "Прогоняет жесткие ручные тесты на сходимость и инварианты матана", mixinStandardHelpOptions = true)
public class VerifyCommand implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(VerifyCommand.class);

    @Option(names = { "--xml" }, description = "Выплюнуть результаты в XML формате (для CI)")
    boolean xmlOutput = false;

    @Override
    public void run() {
        log.info("Запускаем нативную математическую верификацию (ща проверим матан)...");

        int passed = 0;
        int failed = 0;

        StringBuilder report = new StringBuilder();
        report.append("# PDE-LAB Суровый Репорт Математической Верификации\n\n");
        report.append("## Матрица Верификации (Че по тестам)\n\n");
        report.append("| Сьют (Suite) | Метрика (Таргет) | Статус |\n");
        report.append("|---|---|---|\n");

        log.info("[1/4] Чекаем сходимость по пространству (Homogeneous Dirichlet, Crank-Nicolson, O(h^2))...");
        try {
            new SpatialConvergenceVerifier().verifySpatialOrder();
            report.append("| `SpatialConvergence: CN, Homogeneous` | Наклон ~ -2.0, R^2 > 0.995 | НОРМ (PASS) |\n");
            passed++;
        } catch (Throwable t) {
            log.error("Пространственная верификация жестко отвалилась", t);
            report.append("| `SpatialConvergence: CN, Homogeneous` | Наклон ~ -2.0, R^2 > 0.995 | ФЕЙЛ (FAIL) |\n");
            failed++;
        }

        log.info("[2/4] Чекаем сходимость по времени (Crank-Nicolson, O(dt^2))...");
        try {
            new TemporalConvergenceVerifier().verifyCrankNicolsonOrder();
            report.append(
                    "| `TemporalConvergence: CN, Non-Zero Dirichlet` | Наклон ~ 2.0, R^2 > 0.995 | НОРМ (PASS) |\n");
            passed++;
        } catch (Throwable t) {
            log.error("По времени (Crank-Nicolson) верификация отвалилась", t);
            report.append(
                    "| `TemporalConvergence: CN, Non-Zero Dirichlet` | Наклон ~ 2.0, R^2 > 0.995 | ФЕЙЛ (FAIL) |\n");
            failed++;
        }

        log.info("[3/4] Чекаем сходимость по времени (Backward Euler, O(dt))...");
        try {
            new TemporalConvergenceVerifier().verifyBackwardEulerOrder();
            report.append(
                    "| `TemporalConvergence: BE, Non-Zero Dirichlet` | Наклон ~ 1.0, R^2 > 0.995 | НОРМ (PASS) |\n");
            passed++;
        } catch (Throwable t) {
            log.error("По времени (Backward Euler) верификация отвалилась", t);
            report.append(
                    "| `TemporalConvergence: BE, Non-Zero Dirichlet` | Наклон ~ 1.0, R^2 > 0.995 | ФЕЙЛ (FAIL) |\n");
            failed++;
        }

        log.info("[4/5] Чекаем сходимость по пространству с плавающей каппой (Variable Diffusivity, O(h^2))...");
        try {
            new VariableKappaConvergenceVerifier().verifySpatialOrderWithVariableKappa();
            report.append("| `SpatialConvergence: Variable Kappa` | Наклон ~ -2.0, R^2 > 0.995 | НОРМ (PASS) |\n");
            passed++;
        } catch (Throwable t) {
            log.error("Верификация плавающей каппы отвалилась", t);
            report.append("| `SpatialConvergence: Variable Kappa` | Наклон ~ -2.0, R^2 > 0.995 | ФЕЙЛ (FAIL) |\n");
            failed++;
        }

        log.info("[5/5] Чекаем сходимость конвекции (Upwind IMEX, O(h))...");
        try {
            new pdelab.core.ConvectionConvergenceVerifier().verifyConvectionUpwindOrder();
            report.append("| `ConvectionConvergence: IMEX Upwind` | Наклон ~ -1.0, R^2 > 0.985 | НОРМ (PASS) |\n");
            passed++;
        } catch (Throwable t) {
            log.error("Конвективная верификация отвалилась", t);
            report.append("| `ConvectionConvergence: IMEX Upwind` | Наклон ~ -1.0, R^2 > 0.985 | ФЕЙЛ (FAIL) |\n");
            failed++;
        }

        report.append("\n## Итого (Summary)\n");
        report.append(String.format("- **Всего сюитов (Прогнали всего):** %d\n", passed + failed));
        report.append(String.format("- **Прошли (Passed):** %d\n", passed));
        report.append(String.format("- **Упали (Failed):** %d\n\n", failed));

        if (failed == 0) {
            report.append(
                    "**Статус Системы (System Status): SECURE**. Все математические инварианты железобетонно доказаны. 🤓\n");
            log.info("Все нативные сюиты успешно отработали без единого фейла.");
        } else {
            report.append(
                    "**Статус Системы (System Status): DEGRADED**. Зафиксированы математические аномалии (баги в матане).\n");
            log.error("Нативные бета-тесты посыпались, лови {} фейлов!", failed);
        }

        try {
            java.nio.file.Files.writeString(new File("verification_report.md").toPath(), report.toString());
            log.info("Сгенерили математически-трушный репорт тут: verification_report.md");
        } catch (Exception e) {
            log.error("Не осилили выплюнуть markdown репорт", e);
        }
    }
}
