package pdelab.solver;

public interface LinearSolver {
    enum Status {
        CONVERGED, MAX_ITERS, FAIL_NON_SPD, FAIL_NUMERIC
    }

    record SolveResult(Status status, int iterations, double absResidual, double relResidual) {
    }

    SolveResult solve(MatrixOperator A, Preconditioner M, double[] b, double[] x);
}
