package pdelab.core;

import org.junit.jupiter.api.Test;
import pdelab.solver.TimeStepper;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InvariantTest {

    @Test
    public void testBackwardEulerEnergyDecay() {
        // Прогоняем Backward Euler с нулями на границах и f=0.
        // Energy ||u||_2 обязан monotonically decay over time exactly,
        // и пруфаем железобетонную стабильность BE.
        Grid2D grid = new Grid2D(64, 64, 1.0, 1.0);
        double dt = 0.05;
        double alpha = 0.1;

        // У нас тут чистая диффузия (Гомоген!): u = e^{-2 pi^2 alpha t} sin(pi x) sin(pi y)
        // Поэтому f = 0 и энергия строго затухает прямиком в ад (decay).
        MMS mms = new MMS(MMS.TestCase.HOMOGENEOUS, alpha);

        TimeStepper stepper = new TimeStepper(grid, TimeStepper.Scheme.BACKWARD_EULER, alpha, dt, 1000, 1e-12, null,
                "JACOBI", new DirichletBoundary(mms));
        stepper.initExact(0.0, mms);

        double prevEnergy = ParallelVectorOps.normL2(stepper.getU());

        int steps = 10;
        double t = 0.0;
        for (int i = 0; i < steps; i++) {
            stepper.step(t, mms);
            t += dt;
            double energy = ParallelVectorOps.normL2(stepper.getU());

            assertTrue(energy <= prevEnergy, "Energy must decay monotonically in Backward Euler");
            assertTrue(energy > 0.0, "Energy should not be absolutely zero yet");

            prevEnergy = energy;
        }
    }
}
