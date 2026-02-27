package pdelab.solver;

import org.junit.jupiter.api.Test;
import pdelab.core.Grid2D;
import pdelab.core.ParallelExecutor;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

public class MGPreconditionerTest {

    @Test
    public void testMGFlatScaling() {
        ParallelExecutor.init(4);
        try {
            int[] sizes = { 17, 33, 65 }; // 2^k + 1
            int[] jacobiIters = new int[sizes.length];
            int[] mgIters = new int[sizes.length];

            for (int k = 0; k < sizes.length; k++) {
                int N = sizes[k];
                Grid2D grid = new Grid2D(N, N, 1.0, 1.0);

                // Typical Crank-Nicolson factor
                double factor = 0.01 * 1.0 / 2.0;
                double[] tempLxInt = new double[grid.numInterior()];
                ImplicitMatrix A = new ImplicitMatrix(grid, factor, tempLxInt, null, null);

                // Right hand side (random-ish)
                double[] rhs = new double[grid.numInterior()];
                for (int i = 0; i < rhs.length; i++) {
                    rhs[i] = Math.sin((i + 1) * 0.1);
                }

                // Jacobi Solve
                double[] xJac = new double[rhs.length];
                Preconditioner jacobi = new JacobiPreconditioner(grid, factor, null);
                PCG pcgJac = new PCG(grid, 1000, 1e-8);
                LinearSolver.SolveResult resJac = pcgJac.solve(A, jacobi, rhs, xJac);
                jacobiIters[k] = resJac.iterations();

                // MG Solve
                double[] xMg = new double[rhs.length];
                Arrays.fill(xMg, 0.0);
                Preconditioner mg = new MGPreconditioner(grid, factor, null);
                PCG pcgMg = new PCG(grid, 1000, 1e-8);
                LinearSolver.SolveResult resMg = pcgMg.solve(A, mg, rhs, xMg);
                mgIters[k] = resMg.iterations();

                // MG обязан drastically reduce iters
                assertTrue(mgIters[k] < jacobiIters[k] / 2, "MG must be significantly faster than Jacobi");
            }

            // Пруфаем (Assert) flat scaling: Итерации MultiGrid для сетки 65 обязаны быть прямо рядышком с 17 (flat scaling)
            // Технически это прекондей для PCG, так что он может чуток подпухнуть, но
            // basically plateau
            int mgGrow = mgIters[2] - mgIters[0];
            int jacGrow = jacobiIters[2] - jacobiIters[0];
            assertTrue(mgGrow < jacGrow / 5,
                    "MG (Multigrid) обязан выдать nearly flat scaling в отличии от позорно-взрывного роста у Якоби");

            System.out.println("Jacobi Iters: " + Arrays.toString(jacobiIters));
            System.out.println("MG Iters:     " + Arrays.toString(mgIters));

        } finally {
            // Let daemon threads sleep
        }
    }
}
