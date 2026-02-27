package pdelab.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class StencilTest {

    @Test
    public void testLaplacianOnSmallGrid() {
        // 3x3 grid, length=2.0 means hx = 2.0 / (3-1) = 1.0
        Grid2D grid = new Grid2D(3, 3, 2.0, 2.0);
        // hx = 1.0, hy = 1.0.
        // 1/hx^2 = 1.0

        // Both arrays are size `numInterior()`. For 3x3, there is only 1 interior
        // point!
        double[] xInt = new double[1];
        xInt[0] = 1.0;

        double[] yInt = new double[1];
        Stencil.applyLaplacianInterior(grid, xInt, null, yInt);

        // Laplacian stencil: -4*center + top + bottom + left + right
        // Раз в центре торчит кол (1), а по краям нули, то y просто обязан
        // be -4.0
        assertEquals(-4.0, yInt[0], 1e-12);
    }
}
