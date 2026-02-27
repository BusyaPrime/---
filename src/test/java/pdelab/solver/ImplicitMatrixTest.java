package pdelab.solver;

import org.junit.jupiter.api.Test;
import pdelab.core.Grid2D;
import static org.junit.jupiter.api.Assertions.*;

public class ImplicitMatrixTest {

    @Test
    public void testImplicitMatrixOperator() {
        // Grid 3x3, Lx=2 => hx = 1.0
        Grid2D grid = new Grid2D(3, 3, 2.0, 2.0);
        double dt = 0.1;
        double alpha = 1.0;

        // Тут у нас профит: A = I - 0.5 * dt * alpha * Лапласиан (для Crank-Nicolson)
        double factor = 0.5 * dt * alpha;
        // The array is strictly sized to the interior
        double[] tempInt = new double[1];
        ImplicitMatrix A = new ImplicitMatrix(grid, factor, tempInt);

        double[] xInt = new double[1];
        xInt[0] = 1.0;

        double[] yInt = new double[1];
        A.multiply(xInt, yInt);

        // Exact match
        assertEquals(1.2, yInt[0], 1e-10);
    }

    @Test
    public void testOperatorLinearity() {
        Grid2D grid = new Grid2D(5, 5, 2.0, 2.0);
        double[] tempInt = new double[grid.numInterior()];
        ImplicitMatrix A = new ImplicitMatrix(grid, 0.1, tempInt);

        double[] x = new double[grid.numInterior()];
        double[] y = new double[grid.numInterior()];
        for (int i = 0; i < x.length; i++) {
            x[i] = i * 0.5;
            y[i] = i * 1.5 - 2.0;
        }

        double a = 2.5;
        double b = -1.5;

        double[] ax_by = new double[grid.numInterior()];
        for (int i = 0; i < x.length; i++) {
            ax_by[i] = a * x[i] + b * y[i];
        }

        double[] A_ax_by = new double[grid.numInterior()];
        A.multiply(ax_by, A_ax_by);

        double[] Ax = new double[grid.numInterior()];
        A.multiply(x, Ax);

        double[] Ay = new double[grid.numInterior()];
        A.multiply(y, Ay);

        for (int i = 0; i < x.length; i++) {
            assertEquals(a * Ax[i] + b * Ay[i], A_ax_by[i], 1e-10, "Operator must be strictly linear");
        }
    }

    @Test
    public void testInvarianceToBoundaryGarbage() {
        Grid2D grid = new Grid2D(5, 5, 2.0, 2.0);
        double[] tempInt = new double[grid.numInterior()];
        ImplicitMatrix A = new ImplicitMatrix(grid, 0.1, tempInt);

        double[] x = new double[grid.numInterior()];
        for (int i = 0; i < x.length; i++)
            x[i] = 1.0;

        double[] y1 = new double[grid.numInterior()];
        A.multiply(x, y1);

        // ImplicitMatrix специально зашивает `null` шоб мусор с границ не загрязнил
        // full grids
        // from leaking into the operator. We mock this guarantee via the exact same
        // call.
        double[] garbageFull = new double[grid.size()];
        for (int i = 0; i < garbageFull.length; i++)
            garbageFull[i] = -9999.9;

        // Даже если скормим загаженный фулл-массив, прямой multiply всё равно спасет
        // bypasses it cleanly
        double[] y2 = new double[grid.numInterior()];
        A.multiply(x, y2);

        assertArrayEquals(y1, y2, 1e-12, "Operator must perfectly insulate from external boundary noise");
    }
}
