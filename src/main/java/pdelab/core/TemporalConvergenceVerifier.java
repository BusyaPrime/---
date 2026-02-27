package pdelab.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pdelab.solver.TimeStepper;

public class TemporalConvergenceVerifier {

    private static final Logger logger = LoggerFactory.getLogger(TemporalConvergenceVerifier.class);

    public void verifyCrankNicolsonOrder() {
        // Выкручиваем здоровенный N, чтобы пространственная погрешность сдохла.
        int N = 256;

        double[] dts = { 0.04, 0.02, 0.01, 0.005 };
        double[] errors = new double[4];
        for (int i = 0; i < 4; i++) {
            errors[i] = runTemporal(TimeStepper.Scheme.CRANK_NICOLSON, dts[i], N);
        }

        Stats.RegressionResult res = Stats.logLogRegression(dts, errors);
        logger.info("Temporal CN Observed slope: {}, R^2: {}", res.slope(), res.rSquared());

        if (res.slope() <= 1.8 || res.slope() >= 2.2) {
            throw new AssertionError("Порядок Crank-Nicolson обязан быть ~ 2.0, а он равен: " + res.slope());
        }
        if (res.rSquared() < 0.995) {
            throw new AssertionError("R^2 fit must be extremely tight");
        }
    }

    public void verifyBackwardEulerOrder() {
        int N = 256;

        double[] dts = { 0.04, 0.02, 0.01, 0.005 };
        double[] errors = new double[4];
        for (int i = 0; i < 4; i++) {
            errors[i] = runTemporal(TimeStepper.Scheme.BACKWARD_EULER, dts[i], N);
        }

        Stats.RegressionResult res = Stats.logLogRegression(dts, errors);
        logger.info("Temporal BE Observed slope: {}, R^2: {}", res.slope(), res.rSquared());

        if (res.slope() <= 0.8 || res.slope() >= 1.3) {
            throw new AssertionError("Порядок Backward Euler обязан быть ~ 1.0, а тут дичь: " + res.slope());
        }
        if (res.rSquared() < 0.995) {
            throw new AssertionError("R^2 fit must be extremely tight");
        }
    }

    private double runTemporal(TimeStepper.Scheme scheme, double dt, int N) {
        double T = 0.5;
        double alpha = 0.1;
        Grid2D grid = new Grid2D(N, N, 1.0, 1.0);
        MMS mms = new MMS(MMS.TestCase.NON_ZERO_DIRICHLET, alpha);

        TimeStepper stepper = new TimeStepper(grid, scheme, alpha, dt, 1000, 1e-12, null, "JACOBI",
                new DirichletBoundary(mms));
        stepper.initExact(0.0, mms);

        int steps = (int) Math.round(T / dt);
        double t = 0.0;

        for (int i = 0; i < steps; i++) {
            stepper.step(t, mms);
            t += dt;
        }

        double[] uExact = new double[grid.size()];
        mms.evaluateExact(grid, t, uExact);

        return Metrics.computeL2Error(grid, stepper.getU(), uExact);
    }
}
