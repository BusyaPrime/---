package pdelab.solver;

import org.junit.jupiter.api.Test;
import pdelab.core.Grid2D;
import static org.junit.jupiter.api.Assertions.*;

public class SSORPreconditionerTest {

    @Test
    public void testSSORApplicationConstantK() {
        Grid2D grid = new Grid2D(5, 5, 1.0, 1.0);
        SSORPreconditioner precond = new SSORPreconditioner(grid, 0.1, 1.5);

        double[] r = new double[grid.numInterior()];
        double[] z = new double[grid.numInterior()];

        for (int i = 0; i < r.length; i++)
            r[i] = 1.0;

        precond.apply(r, z);

        // Массив Z обязан измениться после прекондея
        assertNotEquals(0.0, z[0]);
    }

    @Test
    public void testSSORApplicationVariableK() {
        Grid2D grid = new Grid2D(5, 5, 1.0, 1.0);
        double[] kFull = new double[grid.size()];
        for (int i = 0; i < kFull.length; i++)
            kFull[i] = 1.0 + 0.1 * i;

        SSORPreconditioner precond = new SSORPreconditioner(grid, 0.1, 1.5, kFull);

        double[] r = new double[grid.numInterior()];
        double[] z = new double[grid.numInterior()];

        for (int i = 0; i < r.length; i++)
            r[i] = 1.0;

        precond.apply(r, z);

        assertNotEquals(0.0, z[0]);
    }
}
