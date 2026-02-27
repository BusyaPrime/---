package pdelab.runtime;

import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new PdeLabCLI()).execute(args);
        System.exit(exitCode);
    }
}
