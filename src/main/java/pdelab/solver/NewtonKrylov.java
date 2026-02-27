package pdelab.solver;

import pdelab.core.Grid2D;
import pdelab.core.VectorOps;
import pdelab.core.ParallelVectorOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jacobian-Free Newton-Krylov (JFNK) Солвер: крошим нелинейные PDEхи (без сборки Якобиана!).
 * Solves F(u) = 0 by finding Newton updates \Delta u without explicitly
 * без изнурительной сборки матрицы Якобиана J.
 * Вместо этого профит от J*v апроксимируем через finite differences: J*v \approx
 * (F(u + \epsilon v) - F(u)) / \epsilon.
 */
public class NewtonKrylov {
    private static final Logger log = LoggerFactory.getLogger(NewtonKrylov.class);

    private final Grid2D grid;
    private final int maxNewtonIters;
    private final double newtonTol;

    // Внутренний PCG чисто для подзадачи Крылова
    private final PCG innerKrylovSolver;

    /**
     * Functional interface representing the non-linear residual evaluator F(u).
     */
    public interface NonLinearFunction {
        /**
         * Считаем нелинейную невязку F(u) и плюем в F_u.
         */
        void evaluate(double[] u, double[] F_u);
    }

    public NewtonKrylov(Grid2D grid, int maxNewtonIters, double newtonTol, int maxKrylovIters, double krylovTol) {
        this.grid = grid;
        this.maxNewtonIters = maxNewtonIters;
        this.newtonTol = newtonTol;
        this.innerKrylovSolver = new PCG(grid, maxKrylovIters, krylovTol);
    }

    /**
     * Решаем нелинейку F(u) = 0 через JFNK (плюс Backtracking Line Search для страховки)
     * Search.
     * 
     * @param F The non-linear residual function.
     * @param u The solution vector (in/out: provides initial guess, returns final
     *          solution).
     * @return Number of total Newton iterations.
     */
    public int solve(NonLinearFunction F, double[] u) {
        int nInt = grid.numInterior();
        double[] F_u = new double[nInt]; // Base numerical residual F(u)
        double[] delta_u = new double[nInt]; // The Newton update search direction

        // Темповые массивы для JFNK внутри PCG (чтобы памяти не натекло)
        double[] u_plus_eps_v = new double[nInt];
        double[] F_u_plus_eps_v = new double[nInt];

        // Таргет RHS для PCG (собираем линейную систему J * \Delta u = -F(u))
        double[] rhs = new double[nInt];

        for (int iter = 0; iter < maxNewtonIters; iter++) {
            // 1. Считаем текущую невязку (residual): F(u)
            F.evaluate(u, F_u);

            // Чек на сходимость (пробили толерантность или еще мучаемся?)
            double residualNorm = ParallelVectorOps.normL2(F_u);
            log.debug("Newton Iter {}: ||F(u)|| = {}", iter, residualNorm);
            if (residualNorm < newtonTol) {
                log.info("JFNK Converged in {} iterations. Final ||F(u)|| = {}", iter, residualNorm);
                return iter;
            }

            // Таргет для PCG - это -F(u)
            VectorOps.copy(F_u, rhs);
            ParallelVectorOps.axpy(-2.0, F_u, rhs); // rhs = F_u - 2*F_u = -F_u

            // 2. Зануляем начальное приближение (guess) для PCG
            java.util.Arrays.fill(delta_u, 0.0);

            // 3. Подрубаем Jacobian-Free оператор умножения матрицы на вектор (J*v)
            MatrixOperator JFNK_Operator = new MatrixOperator() {
                @Override
                public void multiply(double[] in, double[] out) {
                    // \epsilon - это микро-возмущение (perturbation) для аппроксимации.
                    // Sophisticated JFNK scales \epsilon by ||u|| to prevent floating point
                    // cancellation.
                    double vNorm = ParallelVectorOps.normL2(in);
                    if (vNorm == 0) {
                        java.util.Arrays.fill(out, 0.0);
                        return;
                    }

                    double uNorm = ParallelVectorOps.normL2(u);
                    double b = 1e-8; // Machine eps sqrt
                    double eps = b * (1.0 + uNorm) / vNorm;

                    // Считаем матан (тут греем проц) F(u + \epsilon v)
                    VectorOps.copy(u, u_plus_eps_v);
                    ParallelVectorOps.axpy(eps, in, u_plus_eps_v);
                    F.evaluate(u_plus_eps_v, F_u_plus_eps_v);

                    // J*v \approx (F(u + \epsilon v) - F(u)) / \epsilon
                    VectorOps.copy(F_u_plus_eps_v, out);
                    ParallelVectorOps.axpy(-1.0, F_u, out);

                    // Scale output by 1/eps
                    double inv_eps = 1.0 / eps;
                    for (int i = 0; i < nInt; i++) {
                        out[i] *= inv_eps;
                    }
                }
            };

            // 4. Решаем J * delta_u = -F(u) (В идеале с прекондеем, но JFNK пока и
            // applies Right Preconditioning)
            // так сжует голый Крылов без прекондея)
            Preconditioner identityM = new Preconditioner() {
                @Override
                public void apply(double[] in, double[] out) {
                    VectorOps.copy(in, out);
                }

                @Override
                public void updateFactor(double factor) {
                }
            };

            LinearSolver.SolveResult pcgRes = innerKrylovSolver.solve(JFNK_Operator, identityM, rhs, delta_u);
            log.debug("  Inner PCG required {} iters. RelRes: {}", pcgRes.iterations(), pcgRes.relResidual());

            // 5. Жмем Backtracking Line Search (По брутальному правилу Армихо)
            // u_{new} = u + \alpha * delta_u
            double alpha = 1.0;
            double c = 1e-4; // Armijo constant
            double tau = 0.5; // Backtrack shrink factor
            boolean lineSearchConverged = false;

            double[] u_candidate = new double[nInt];
            double[] F_candidate = new double[nInt];

            // Производная по направлению \nabla F \cdot delta_u апроксимируется. Но мы тупо
            // чекаем ||F(u_new)|| < (1 - \alpha * c) ||F(u)||
            for (int ls = 0; ls < 10; ls++) {
                VectorOps.copy(u, u_candidate);
                ParallelVectorOps.axpy(alpha, delta_u, u_candidate);
                F.evaluate(u_candidate, F_candidate);

                double candidateNorm = ParallelVectorOps.normL2(F_candidate);

                if (candidateNorm <= (1.0 - alpha * c) * residualNorm) {
                    // Update accepted
                    VectorOps.copy(u_candidate, u);
                    lineSearchConverged = true;
                    log.debug("  Line Search успешно схавал шаг с alpha = {}", alpha);
                    break;
                }

                alpha *= tau; // Shrink step
            }

            if (!lineSearchConverged) {
                log.warn("  Line Search не нашел нормального спуска. Форсим фулл шаг Ньютона! (пан или пропал).");
                VectorOps.copy(u, u_candidate);
                ParallelVectorOps.axpy(1.0, delta_u, u_candidate);
                VectorOps.copy(u_candidate, u);
            }
        }

        log.warn("JFNK hit max iterations ({}) without full convergence.", maxNewtonIters);
        return maxNewtonIters;
    }
}
