package pdelab.runtime;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "pdelab", mixinStandardHelpOptions = true, version = "1.0.0", description = "High-Performance 2D PDE Solver and Benchmarking Suite", subcommands = {
        RunCommand.class,
        VerifyCommand.class,
        BenchCommand.class
})
public class PdeLabCLI implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
