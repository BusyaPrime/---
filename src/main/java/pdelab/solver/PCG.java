package pdelab.solver;

import pdelab.core.Grid2D;
import pdelab.core.ParallelVectorOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Preconditioned Conjugate Gradient Solver (Метод сопряженных градиентов с
 * прекондеем).
 * Аллоцирует все свои кишки на старте, чтобы в хот-лупе GC даже не проснулся.
 * Zero-allocation, детка!
 */
public class PCG implements LinearSolver {

    private static final Logger log = LoggerFactory.getLogger(PCG.class);

    private final int maxIters;
    private final double tol;

    private final double[] r;
    private final double[] z;
    private final double[] p;
    private final double[] Ap;

    public PCG(Grid2D grid, int maxIters, double tol) {

        this.maxIters = maxIters;
        this.tol = tol;

        int n = grid.numInterior();
        this.r = new double[n];
        this.z = new double[n];
        this.p = new double[n];
        this.Ap = new double[n];
    }

    /**
     * Решаем СЛАУ.
     * Итерации мутируют массив 'x' in place (мутабельность тут во благо перфа).
     * Граничные условия Дирихле хэндлятся элегантно: просто зануляем residual на
     * границе и спим спокойно.
     */
    public SolveResult solve(MatrixOperator A, Preconditioner M, double[] b, double[] x) {
        double normb = ParallelVectorOps.normL2(b);
        if (Double.isNaN(normb) || Double.isInfinite(normb)) {
            log.error("PCG NaN/Inf detected in RHS vector!");
            return new SolveResult(Status.FAIL_NUMERIC, 0, Double.NaN, Double.NaN);
        }
        boolean isZeroB = (normb == 0.0);
        double divisorB = isZeroB ? 1.0 : normb;

        // 1. r = b - A x (Базовый невязон)
        A.multiply(x, Ap);
        ParallelVectorOps.copy(b, r);
        ParallelVectorOps.axpy(-1.0, Ap, r);

        double residual = ParallelVectorOps.normL2(r);
        if (Double.isNaN(residual) || Double.isInfinite(residual)) {
            log.error("PCG NaN/Inf detected in initial residual! normb={}", normb);
            return new SolveResult(Status.FAIL_NUMERIC, 0, residual, residual / divisorB);
        }
        if (residual / divisorB <= tol)
            return new SolveResult(Status.CONVERGED, 0, residual, residual / divisorB);

        // 2. z = M^-1 r (Применяем прекондей, чтоб матрица подобрела)
        M.apply(r, z);

        // 3. p = z (Направление поиска)
        ParallelVectorOps.copy(z, p);

        double rz = ParallelVectorOps.dot(r, z);

        for (int k = 1; k <= maxIters; k++) {
            A.multiply(p, Ap);

            double pAp = ParallelVectorOps.dot(p, Ap);
            if (pAp <= 0.0) {
                log.error(
                        "Матрица не является симметричной положительно определенной (Not SPD)! Архитектор будет в ярости. pAp = {}",
                        pAp);
                return new SolveResult(Status.FAIL_NON_SPD, k, residual, residual / divisorB);
            }

            double alpha = rz / pAp;
            if (Double.isNaN(alpha) || Double.isInfinite(alpha)) {
                log.error("PCG step alpha NaN/Inf at iteration {}. rz={}, pAp={}", k, rz, pAp);
                return new SolveResult(Status.FAIL_NUMERIC, k, residual, residual / divisorB);
            }

            // x = x + alpha * p (Шагаем к оптимуму)
            ParallelVectorOps.axpy(alpha, p, x);

            if (k % 50 == 0) {
                // Избегаем дрифта флоатинга (накапливаемой ошибки округления)
                // Периодически пересчитываем честный residual
                A.multiply(x, Ap);
                ParallelVectorOps.copy(b, r);
                ParallelVectorOps.axpy(-1.0, Ap, r);
            } else {
                // r = r - alpha * Ap
                ParallelVectorOps.axpy(-alpha, Ap, r);
            }

            residual = ParallelVectorOps.normL2(r);
            if (residual / divisorB <= tol) {
                return new SolveResult(Status.CONVERGED, k, residual, residual / divisorB);
            }

            M.apply(r, z);

            double rzNew = ParallelVectorOps.dot(r, z);
            double beta = rzNew / rz;

            // p = z + beta * p (Обновляем направление)
            ParallelVectorOps.axpby(1.0, z, beta, p);

            rz = rzNew;
        }

        return new SolveResult(Status.MAX_ITERS, maxIters, residual, residual / divisorB);
    }
}
