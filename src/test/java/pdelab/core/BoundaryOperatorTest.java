package pdelab.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BoundaryOperatorTest {

    @Test
    public void testApplyNeumann() {
        Grid2D grid = new Grid2D(4, 4, 1.0, 1.0);
        double[] uFull = new double[grid.size()];

        // Инициализируем (поднимаем базовые структуры, выделяем память) interior to some values
        for (int j = 1; j < 3; j++) {
            for (int i = 1; i < 3; i++) {
                uFull[grid.idx(i, j)] = (double) (i + j);
            }
        }

        double gLeft = 1.0;
        double gRight = -1.0;
        double gBottom = 2.0;
        double gTop = -2.0;

        BoundaryOperator neumann = new NeumannBoundary(gLeft, gRight, gBottom, gTop);
        neumann.apply(grid, uFull, 0.0);

        double hx = grid.hx();
        double hy = grid.hy();

        // Left: u[0,j] = u[1,j] - hx * gLeft
        assertEquals(uFull[grid.idx(1, 1)] - hx * gLeft, uFull[grid.idx(0, 1)], 1e-10);

        // Right: u[3,j] = u[2,j] + hx * gRight
        assertEquals(uFull[grid.idx(2, 1)] + hx * gRight, uFull[grid.idx(3, 1)], 1e-10);

        // Bottom: u[i,0] = u[i,1] - hy * gBottom
        assertEquals(uFull[grid.idx(1, 1)] - hy * gBottom, uFull[grid.idx(1, 0)], 1e-10);

        // Top: u[i,3] = u[i,2] + hy * gTop
        assertEquals(uFull[grid.idx(1, 2)] + hy * gTop, uFull[grid.idx(1, 3)], 1e-10);
    }

    @Test
    public void testApplyRobin() {
        Grid2D grid = new Grid2D(4, 4, 1.0, 1.0);
        double[] uFull = new double[grid.size()];

        // Инициализируем (поднимаем базовые структуры, выделяем память) interior to some values
        for (int j = 1; j < 3; j++) {
            for (int i = 1; i < 3; i++) {
                uFull[grid.idx(i, j)] = (double) (i + j);
            }
        }

        double a = 2.0;
        double b = 0.5;
        double gLeft = 1.0;
        double gRight = -1.0;
        double gBottom = 2.0;
        double gTop = -2.0;

        BoundaryOperator robin = new RobinBoundary(a, b, gLeft, gRight, gBottom, gTop);
        robin.apply(grid, uFull, 0.0);

        double hx = grid.hx();

        // Left: u_0 = (gLeft - u_1 * (-b/hx)) / (a - b/hx)
        double expectedLeft = (gLeft + uFull[grid.idx(1, 1)] * (b / hx)) / (a - b / hx);
        assertEquals(expectedLeft, uFull[grid.idx(0, 1)], 1e-10);
    }
}
