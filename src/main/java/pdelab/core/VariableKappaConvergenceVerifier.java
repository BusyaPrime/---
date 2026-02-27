package pdelab.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pdelab.solver.TimeStepper;

public class VariableKappaConvergenceVerifier {

    private static final Logger logger = LoggerFactory.getLogger(VariableKappaConvergenceVerifier.class);

    public void verifySpatialOrderWithVariableKappa() {
        // Закинули микро dt, чтобы темпоральная погрешность ушла в нули (шоб не фонила).
        double dt = 1e-4;

        double[] sizes = { 32.0, 64.0, 128.0, 256.0 };
        double[] errors = new double[4];
        for (int i = 0; i < 4; i++) {
            errors[i] = runSpatial((int) sizes[i], dt);
        }

        Stats.RegressionResult res = Stats.logLogRegression(sizes, errors);

        logger.info("Variable Kappa Spatial Observed slope: {}, R^2: {}", res.slope(), res.rSquared());

        if (res.slope() <= -2.2 || res.slope() >= -1.8) {
            throw new AssertionError("Пространственный порядок обязан быть ~ -2.0, а тут вылезло: " + res.slope());
        }
        if (res.rSquared() < 0.995) {
            throw new AssertionError("R^2 fit must be extremely tight (>= 0.995)");
        }
    }

    private double runSpatial(int N, double dt) {
        double T = 0.5;
        double alpha = 0.1;
        Grid2D grid = new Grid2D(N, N, 1.0, 1.0);
        MMS mms = new MMS(MMS.TestCase.VARIABLE_KAPPA, alpha);

        double[] kFull = new double[grid.size()];
        mms.evaluateKappa(grid, kFull);

        TimeStepper stepper = new TimeStepper(grid, TimeStepper.Scheme.CRANK_NICOLSON, alpha, dt, 1000, 1e-12, kFull,
                "JACOBI", new DirichletBoundary(mms));
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
