package pdelab.core;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

public class ConvectionLimiterTest {

    @Test
    public void testMUSCLTVDMonotonicity() {
        ParallelExecutor.init(4);
        try {
            Grid2D grid = new Grid2D(100, 3, 10.0, 1.0);
            double[] u = new double[grid.size()];

            // Create a sharp step function in the interior
            for (int j = 0; j < grid.Ny(); j++) {
                for (int i = 0; i < grid.Nx(); i++) {
                    if (i > 20 && i < 40)
                        u[grid.idx(i, j)] = 1.0;
                    else
                        u[grid.idx(i, j)] = 0.0;
                }
            }

            double[] uInt = new double[grid.numInterior()];
            grid.extractInterior(u, uInt);

            double[] bX = new double[grid.size()];
            double[] bY = new double[grid.size()];
            Arrays.fill(bX, 1.0); // Constant right flow
            Arrays.fill(bY, 0.0);

            double[] LuInt = new double[grid.numInterior()];

            // Simulate 1 Explicit Euler Step
            double dt = 0.01;
            for (int step = 0; step < 50; step++) {
                Arrays.fill(LuInt, 0.0);
                Stencil.applyConvectionUpwindInterior(grid, uInt, u, bX, bY, LuInt);

                // u_new = u_old - dt * LuInt
                for (int i = 0; i < grid.numInterior(); i++) {
                    uInt[i] -= dt * LuInt[i];
                }
                grid.injectInterior(uInt, u);
            }

            // Валидируем TVD свойства (чтобы осцилляции не размотали нам всю физику)
            // It обязан not have created new extremas (values > 1.0 or < 0.0)
            double maxVal = -1e9;
            double minVal = 1e9;

            for (int i = 0; i < grid.numInterior(); i++) {
                maxVal = Math.max(maxVal, uInt[i]);
                minVal = Math.min(minVal, uInt[i]);
            }

            System.out.println("Max post-convection: " + maxVal);
            System.out.println("Min post-convection: " + minVal);

            assertTrue(maxVal <= 1.000001,
                    "MUSCL TVD MUST NOT generate artificial positive oscillations! Found: " + maxVal);
            assertTrue(minVal >= -0.000001,
                    "MUSCL TVD MUST NOT generate artificial negative oscillations! Found: " + minVal);
        } finally {
            // Implicit daemon shutdown
        }
    }
}
