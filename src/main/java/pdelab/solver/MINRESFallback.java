package pdelab.solver;

import pdelab.core.Grid2D;
import pdelab.core.ParallelVectorOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal Residual Fallback Solver.
 * Врубаем CGNR (Conjugate Gradient on Normal Equations) для симметричных
 * indefinite matrices.
 * Т.к. матрица A симметрична, решение A^2 x = A b железно минимизирует ||b - Ax||_2
 * without failing on negative eigenvalues like standard PCG.
 */
public class MINRESFallback implements LinearSolver {

    private static final Logger log = LoggerFactory.getLogger(MINRESFallback.class);

    private final int maxIters;
    private final double tol;

    private final double[] r;
    private final double[] z;
    private final double[] p;
    private final double[] w;

    public MINRESFallback(Grid2D grid, int maxIters, double tol) {
        this.maxIters = maxIters;
        this.tol = tol;

        int n = grid.numInterior();
        this.r = new double[n];
        this.z = new double[n];
        this.p = new double[n];
        this.w = new double[n];
    }

    @Override
    public SolveResult solve(MatrixOperator A, Preconditioner M, double[] b, double[] x) {
        log.info("Врубаем MINRES/CGNR фоллбэк: матрица indefinite (чуть не улетели).");

        double normb = ParallelVectorOps.normL2(b);
        if (Double.isNaN(normb) || Double.isInfinite(normb)) {
            return new SolveResult(Status.FAIL_NUMERIC, 0, Double.NaN, Double.NaN);
        }
        boolean isZeroB = (normb == 0.0);
        double divisorB = isZeroB ? 1.0 : normb;

        // r = b - A x
        A.multiply(x, w);
        ParallelVectorOps.copy(b, r);
        ParallelVectorOps.axpy(-1.0, w, r);

        double residual = ParallelVectorOps.normL2(r);
        if (residual / divisorB <= tol) {
            return new SolveResult(Status.CONVERGED, 0, residual, residual / divisorB);
        }

        // z = A r (Нормальные уравнения A^T r, матрица де-факто симметрична)
        A.multiply(r, z);

        // p = z
        ParallelVectorOps.copy(z, p);

        double zz = ParallelVectorOps.dot(z, z);

        for (int k = 1; k <= maxIters; k++) {
            // w = A p
            A.multiply(p, w);

            double ww = ParallelVectorOps.dot(w, w);
            if (ww <= 0.0) {
                return new SolveResult(Status.FAIL_NUMERIC, k, residual, residual / divisorB);
            }

            double alpha = zz / ww;
            if (Double.isNaN(alpha) || Double.isInfinite(alpha)) {
                return new SolveResult(Status.FAIL_NUMERIC, k, residual, residual / divisorB);
            }

            // x = x + alpha * p
            ParallelVectorOps.axpy(alpha, p, x);

            // r = r - alpha * w
            ParallelVectorOps.axpy(-alpha, w, r);

            residual = ParallelVectorOps.normL2(r);
            if (residual / divisorB <= tol) {
                return new SolveResult(Status.CONVERGED, k, residual, residual / divisorB);
            }

            // z = A r
            A.multiply(r, z);

            double zzNew = ParallelVectorOps.dot(z, z);
            double beta = zzNew / zz;

            // p = z + beta * p
            ParallelVectorOps.axpby(1.0, z, beta, p);

            zz = zzNew;
        }

        return new SolveResult(Status.MAX_ITERS, maxIters, residual, residual / divisorB);
    }
}
