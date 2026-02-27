package pdelab.solver;

import org.junit.jupiter.api.Test;
import pdelab.core.Grid2D;
import pdelab.core.MMS;
import pdelab.core.BoundaryOperator;
import pdelab.core.ParallelExecutor;

import static org.junit.jupiter.api.Assertions.*;

public class EnergyDecayTest {

    @Test
    public void testBackwardEulerEnergyDecay() {
        ParallelExecutor.init(4);
        try {
            Grid2D grid = new Grid2D(32, 32, 1.0, 1.0);
            MMS baseMms = new MMS(MMS.TestCase.HOMOGENEOUS, 1.0);

            BoundaryOperator zeroDirichlet = new BoundaryOperator() {
                @Override
                public void apply(Grid2D grid, double[] u, double t) {
                    int nx = grid.Nx();
                    int ny = grid.Ny();
                    for (int i = 0; i < nx; i++) {
                        u[grid.idx(i, 0)] = 0.0;
                        u[grid.idx(i, ny - 1)] = 0.0;
                    }
                    for (int j = 0; j < ny; j++) {
                        u[grid.idx(0, j)] = 0.0;
                        u[grid.idx(nx - 1, j)] = 0.0;
                    }
                }
            };

            // Овверайднули MMS, шоб жестко вбить f = 0 ради правильного теста
            MMS zeroForcingMms = new MMS(MMS.TestCase.HOMOGENEOUS, 1.0) {
                @Override
                public void evaluateForcing(Grid2D grid, double t, double[] f) {
                    java.util.Arrays.fill(f, 0.0);
                }
            };

            TimeStepper stepper = new TimeStepper(grid, TimeStepper.Scheme.BACKWARD_EULER, 1.0, 0.05, 100, 1e-10,
                    zeroDirichlet);

            // Инициализируем (поднимаем базовые структуры, выделяем память) с раскаченным non-zero стейтом в пузе
            double[] u = stepper.getU();
            for (int i = 0; i < grid.size(); i++) {
                u[i] = Math.sin(i * 0.1);
            }
            zeroDirichlet.apply(grid, u, 0); // прибили гвоздями границы в 0

            double previousEnergy = computeEnergy(u);
            assertTrue(previousEnergy > 1.0, "Initial energy should be substantial");

            for (int step = 0; step < 10; step++) {
                stepper.step(step * 0.05, zeroForcingMms);
                double currentEnergy = computeEnergy(stepper.getU());

                assertTrue(currentEnergy <= previousEnergy + 1e-12,
                        "Энергия обязана монотонно тухнуть в Backward Euler при f=0. До этого было: " + previousEnergy
                                + ", Curr: " + currentEnergy);

                previousEnergy = currentEnergy;
            }
        } finally {
            // Треды ParallelExecutor-а - демоны, ребутаются без боли и памяти не жрут.
        }
    }

    private double computeEnergy(double[] u) {
        double e = 0.0;
        for (double val : u) {
            e += val * val;
        }
        return e;
    }
}
