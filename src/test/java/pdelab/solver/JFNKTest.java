package pdelab.solver;

import org.junit.jupiter.api.Test;
import pdelab.core.Grid2D;
import pdelab.core.Stencil;
import pdelab.core.VectorOps;
import pdelab.core.ParallelVectorOps;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JFNKTest {

    @Test
    void testJFNK_NonLinearDiffusionReaction() {
        // Разваливаем стационарную нелинейную таску (steady-state):
        // -Laplacian(u) - exp(u) + f(x,y) = 0
        // на сетке 32x32 под жестким Дирихле.

        int n = 32;
        Grid2D grid = new Grid2D(n, n, 1.0, 1.0);
        int nInt = grid.numInterior();

        double[] u_true = new double[grid.size()]; // True solution: sin(\pi x) sin(\pi y)
        double[] f_rhs = new double[nInt]; // Derived RHS to match true solution

        // Сетап окружения (тут собираем моки) manufactured true solution
        double pi = Math.PI;
        for (int j = 0; j < grid.Ny(); j++) {
            double y = grid.y()[j];
            for (int i = 0; i < grid.Nx(); i++) {
                double x = grid.x()[i];
                u_true[grid.idx(i, j)] = Math.sin(pi * x) * Math.sin(pi * y);
            }
        }

        double[] u_true_int = new double[nInt];
        grid.extractInterior(u_true, u_true_int);

        // Считаем матан (тут греем проц) именно с таким форсингом (forcing): f(x,y) = -Laplacian(u_true) -
        // exp(u_true)
        double[] lap_u_int = new double[nInt];
        Stencil.applyLaplacianInterior(grid, u_true_int, u_true, lap_u_int);

        for (int i = 0; i < nInt; i++) {
            f_rhs[i] = -lap_u_int[i] - Math.exp(u_true_int[i]);
        }

        // Define JFNK Non-Linear Function F(u) = -Laplacian(u) - exp(u) + f
        NewtonKrylov.NonLinearFunction F = new NewtonKrylov.NonLinearFunction() {
            @Override
            public void evaluate(double[] u_guess_int, double[] F_out) {
                // Чтобы накатить Лапласиан без багов, нужен u_full со стерильными нулями на краях
                double[] u_full = new double[grid.size()];
                grid.injectInterior(u_guess_int, u_full);

                Stencil.applyLaplacianInterior(grid, u_guess_int, u_full, F_out);

                for (int i = 0; i < nInt; i++) {
                    // F_out initially holds Laplacian(u)
                    // F(u) = -Laplacian(u) - exp(u) - (-Laplacian(u_true) - exp(u_true))
                    // Сек, f_rhs = -Laplacian(u_true) - exp(u_true). (собираем нелинейный форсинг)
                    // So F_out = -F_out - exp(u) - f_rhs
                    F_out[i] = -F_out[i] - Math.exp(u_guess_int[i]) - f_rhs[i];
                }
            }
        };

        // Инициализируем (поднимаем базовые структуры, выделяем память) solver
        NewtonKrylov solver = new NewtonKrylov(grid, 20, 1e-8, 1000, 1e-9);

        // Заводимся с полного нуля (zero initial guess)
        double[] u_solve_int = new double[nInt]; // Initial guess is 0.0
        int iters = solver.solve(F, u_solve_int);

        assertTrue(iters < 20, "JFNK should converge rapidly for smooth exponential nonlinearity.");

        // Пруфаем (Assert) погрешность на уровне погрешности округления (копейки)
        double[] error = new double[nInt];
        VectorOps.copy(u_true_int, error);
        ParallelVectorOps.axpy(-1.0, u_solve_int, error);

        double maxError = 0;
        for (double e : error) {
            maxError = Math.max(maxError, Math.abs(e));
        }

        System.out.println("JFNK Max Error converging to True Solution: " + maxError);
        assertTrue(maxError < 1e-6, "JFNK тупо не сошелся к настоящему решению");
    }
}
