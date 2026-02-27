package pdelab.core;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pdelab.solver.TimeStepper;

public class MMSTest {
    private static final Logger logger = LoggerFactory.getLogger(MMSTest.class);

    @Test
    public void testComprehensiveMMSPermutations() {
        MMS.TestCase[] cases = MMS.TestCase.values();
        TimeStepper.Scheme[] schemes = { TimeStepper.Scheme.CRANK_NICOLSON, TimeStepper.Scheme.BACKWARD_EULER,
                TimeStepper.Scheme.IMEX };

        for (MMS.TestCase c : cases) {
            for (TimeStepper.Scheme s : schemes) {
                // Skip combinations that are theoretically unstable or unsupported
                if (c == MMS.TestCase.CONVECTION && s != TimeStepper.Scheme.IMEX)
                    continue;
                if (c != MMS.TestCase.CONVECTION && s == TimeStepper.Scheme.IMEX)
                    continue;

                double err = runErrorAnalysis(c, s, 0.025, 20);
                org.junit.jupiter.api.Assertions.assertTrue(err < 0.2,
                        "Дикая погрешность для " + c + " with " + s + ": " + err);
            }
        }
    }

    private double runErrorAnalysis(MMS.TestCase testCase, TimeStepper.Scheme scheme, double dt, int N) {
        double T = 0.5;
        double alpha = 0.1;
        Grid2D grid = new Grid2D(N, N, 1.0, 1.0);
        MMS mms = new MMS(testCase, alpha);

        VectorField bField = null;
        if (testCase == MMS.TestCase.CONVECTION || testCase == MMS.TestCase.ROTATING_HUMP) {
            bField = new VectorField() {
                @Override
                public double bx(double x, double y) {
                    return 1.0;
                }

                @Override
                public double by(double x, double y) {
                    return 1.0;
                }
            };
        }

        TimeStepper stepper = new TimeStepper(grid, scheme, alpha, dt, 1000, 1e-10, null, "JACOBI",
                new DirichletBoundary(mms), bField);
        stepper.initExact(0.0, mms);

        int steps = (int) Math.round(T / dt);
        double t = 0.0;

        for (int i = 0; i < steps; i++) {
            stepper.step(t, mms);
            t += dt;
        }

        double[] uExact = new double[grid.size()];
        mms.evaluateExact(grid, t, uExact);

        double errorL2 = Metrics.computeL2Error(grid, stepper.getU(), uExact);
        double errorRel = Metrics.computeRelativeL2Error(grid, stepper.getU(), uExact);

        logger.info("Прогон {}, Сетка N={}, dt={} -> Загребли L2 Error: {}, RelL2: {}, Сожрано итераций PCG: {}",
                testCase, N, dt, String.format("%e", errorL2), String.format("%e", errorRel),
                stepper.getTotalPcgIters());

        return errorL2;
    }
}
