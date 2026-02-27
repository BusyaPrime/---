package pdelab.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pdelab.solver.TimeStepper;

public class ConvectionConvergenceVerifier {

    private static final Logger logger = LoggerFactory.getLogger(ConvectionConvergenceVerifier.class);

    public void verifyConvectionUpwindOrder() {
        // Закинули микро dt, чтобы темпоральная погрешность ушла в нули (шоб не фонила).
        double dt = 1e-4;

        double[] sizes = { 32.0, 64.0, 128.0, 256.0 };
        double[] errors = new double[4];
        for (int i = 0; i < 4; i++) {
            errors[i] = runConvectionSpatial((int) sizes[i], dt);
        }

        Stats.RegressionResult res = Stats.logLogRegression(sizes, errors);

        logger.info("Convection (Upwind) Spatial Observed slope: {}, R^2: {}", res.slope(), res.rSquared());

        // MUSCL TVD with Minmod обязан сходиться от O(h^1.5) до O(h^2) (на гладких решениях).
        // solutions
        if (res.slope() >= -1.4 || res.slope() <= -2.2) {
            throw new AssertionError("MUSCL TVD Отвал башки: порядок MUSCL TVD должен быть между -1.5 и -2.1, а по факту " + res.slope());
        }
        if (res.rSquared() < 0.985) {
            throw new AssertionError("R^2 fit must be tight (>= 0.985)");
        }
    }

    private double runConvectionSpatial(int N, double dt) {
        double T = 0.5;
        double alpha = 0.1;
        Grid2D grid = new Grid2D(N, N, 1.0, 1.0);
        MMS mms = new MMS(MMS.TestCase.CONVECTION, alpha);

        // Constant convection field b = [1.0, 1.0]
        VectorField bField = new VectorField() {
            @Override
            public double bx(double x, double y) {
                return 1.0;
            }

            @Override
            public double by(double x, double y) {
                return 1.0;
            }
        };

        TimeStepper stepper = new TimeStepper(grid, TimeStepper.Scheme.IMEX, alpha, dt, 1000, 1e-12, null,
                "JACOBI", new DirichletBoundary(mms), bField);
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
