package pdelab.solver;

import org.junit.jupiter.api.Test;
import pdelab.core.Grid2D;
import static org.junit.jupiter.api.Assertions.*;

public class PCGTest {

    @Test
    public void testPcgThrowsOnNaN() {
        Grid2D grid = new Grid2D(5, 5, 1.0, 1.0);
        PCG pcg = new PCG(grid, 50, 1e-6);

        // Identity matrix
        MatrixOperator A = (x, y) -> {
            System.arraycopy(x, 0, y, 0, x.length);
        };
        Preconditioner M = new Preconditioner() {
            @Override
            public void apply(double[] x, double[] y) {
                System.arraycopy(x, 0, y, 0, x.length);
            }

            @Override
            public void updateFactor(double factor) {
            }
        };

        // Вектора должны сматчиться с numInterior() (для 5x5 это 3x3=9 узлов)
        double[] b = new double[9];
        b[0] = Double.NaN; // Впрыскиваем NaN (яд!)

        double[] x = new double[9];

        LinearSolver.SolveResult result = pcg.solve(A, M, b, x);
        assertEquals(LinearSolver.Status.FAIL_NUMERIC, result.status(),
                "PCG обязан чекать NaN и откидывать FAIL_NUMERIC");
    }

    @Test
    public void testPcgExactSolveForIdentity() {
        Grid2D grid = new Grid2D(5, 5, 1.0, 1.0);
        PCG pcg = new PCG(grid, 50, 1e-10);

        MatrixOperator A = (x, y) -> {
            System.arraycopy(x, 0, y, 0, x.length);
        };
        Preconditioner M = new Preconditioner() {
            @Override
            public void apply(double[] x, double[] y) {
                System.arraycopy(x, 0, y, 0, x.length);
            }

            @Override
            public void updateFactor(double factor) {
            }
        };

        // 5x5 full -> 3x3 interior -> 9 variables
        double[] b = new double[9];
        for (int i = 0; i < 9; i++)
            b[i] = i;

        double[] x = new double[9];

        LinearSolver.SolveResult result = pcg.solve(A, M, b, x);
        assertEquals(LinearSolver.Status.CONVERGED, result.status(), "Решение единичной матрицы обязано сойтись");
        assertEquals(1, result.iterations(), "Решение единичной матрицы обязано схлопнуться за 1 итерацию");

        // The interior matrix simply matches b perfectly
        for (int i = 0; i < 9; i++) {
            assertEquals(b[i], x[i], 1e-10, "Внутрянка обязана сматчиться с единичной матрицей");
        }
    }

    @Test
    public void testPcgThrowsOnNonSPDMatrix() {
        Grid2D grid = new Grid2D(5, 5, 1.0, 1.0);
        PCG pcg = new PCG(grid, 50, 1e-10);

        // Negative explicit matrix (Not Symmetric Positive Definite)
        MatrixOperator A = (x, y) -> {
            for (int i = 0; i < x.length; i++) {
                y[i] = -1.0 * x[i];
            }
        };
        Preconditioner M = new Preconditioner() {
            @Override
            public void apply(double[] x, double[] y) {
                System.arraycopy(x, 0, y, 0, x.length);
            }

            @Override
            public void updateFactor(double factor) {
            }
        };

        double[] b = new double[9];
        for (int i = 0; i < 9; i++)
            b[i] = i + 1.0;

        double[] x = new double[9];

        LinearSolver.SolveResult result = pcg.solve(A, M, b, x);
        assertEquals(LinearSolver.Status.FAIL_NON_SPD, result.status(),
                "PCG обязан ловить кривые не-SPD матрицы и кидать FAIL_NON_SPD");
    }
}
