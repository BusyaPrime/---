package pdelab.runtime;

import picocli.CommandLine.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import pdelab.benchmarks.CoreKernelsBenchmark;

@Command(name = "bench", description = "Run low-level JMH microbenchmarks for Stencil/VectorOps", mixinStandardHelpOptions = true)
public class BenchCommand implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(BenchCommand.class);

    @Override
    public void run() {
        log.info("Starting Native JMH Benchmarks inside PDE-LAB JVM...");
        log.info("Изолируем энвайронменты потоков и греем ядра JIT-компилятором...");

        try {
            Options opt = new OptionsBuilder()
                    .include(CoreKernelsBenchmark.class.getSimpleName())
                    .warmupIterations(3)
                    .measurementIterations(5)
                    .forks(1)
                    .build();

            new Runner(opt).run();

            log.info("Native JMH Benchmarks finalized successfully.");
        } catch (Exception e) {
            log.error("Уронили нативный фреймворк JMH (перф-тесты в коме)!", e);
        }
    }
}
