package pdelab.solver;

import org.junit.jupiter.api.Test;
import pdelab.core.Grid2D;
import pdelab.core.MMS;
import pdelab.core.DirichletBoundary;
import pdelab.core.NeumannBoundary;
import pdelab.core.RobinBoundary;
import pdelab.core.Metrics;
import pdelab.core.ParallelExecutor;

import static org.junit.jupiter.api.Assertions.*;

public class TimeStepperTest {

    @Test
    public void testNeumannIntegration() {
        int N = 16;
        Grid2D grid = new Grid2D(N, N, 1.0, 1.0);
        ParallelExecutor.init(2);

        // Neumann boundary: gLeft=0, gRight=0, gBottom=0, gTop=0
        NeumannBoundary neumann = new NeumannBoundary(0.0, 0.0, 0.0, 0.0);
        MMS mms = new MMS(MMS.TestCase.HOMOGENEOUS, 0.1);

        TimeStepper stepper = new TimeStepper(grid, TimeStepper.Scheme.BACKWARD_EULER, 0.1, 0.01, 100, 1e-6, neumann);
        stepper.initExact(0.0, mms);

        assertDoesNotThrow(() -> {
            stepper.step(0.0, mms);
            stepper.step(0.01, mms);
        }, "TimeStepper обязан прожевать Неймана без внутренних критов");

        // Ensure values are numerically viable
        double[] u = stepper.getU();
        assertFalse(Double.isNaN(u[grid.idx(N / 2, N / 2)]), "Внутри сетки не должно быть NaN-ов (всё по матану)");
    }

    @Test
    public void testRobinIntegration() {
        int N = 16;
        Grid2D grid = new Grid2D(N, N, 1.0, 1.0);
        ParallelExecutor.init(2);

        // Robin boundary: a=1, b=1, g=...
        RobinBoundary robin = new RobinBoundary(1.0, 1.0, 0.0, 0.0, 0.0, 0.0);
        MMS mms = new MMS(MMS.TestCase.HOMOGENEOUS, 0.1);

        TimeStepper stepper = new TimeStepper(grid, TimeStepper.Scheme.CRANK_NICOLSON, 0.1, 0.01, 100, 1e-6, robin);
        stepper.initExact(0.0, mms);

        assertDoesNotThrow(() -> {
            stepper.step(0.0, mms);
            stepper.step(0.01, mms);
        }, "TimeStepper обязан прожевать Робина без эксепшенов");

        double[] u = stepper.getU();
        assertFalse(Double.isNaN(u[grid.idx(N / 2, N / 2)]), "Внутри сетки не должно быть NaN-ов (всё по матану)");
    }

    @Test
    public void testNonSPDFallbackTrigger() {
        int N = 16;
        Grid2D grid = new Grid2D(N, N, 1.0, 1.0);
        ParallelExecutor.init(2);

        // Дисконтный alpha уводит матрицу в минуса (ломаем SPD)
        // Формула: (dt*alpha/2)*Лапласиан (Тут матан ломается 🤓)
        // has negative eigenvalues (Laplacian has negative evals, so -(negative) *
        // ...итого в матрицу летит сплошной негатив (negative). Гарантированный фейл PCG.
        double badAlpha = -1000.0;

        DirichletBoundary dirichlet = new DirichletBoundary(new MMS(MMS.TestCase.HOMOGENEOUS, badAlpha));
        MMS mms = new MMS(MMS.TestCase.HOMOGENEOUS, badAlpha);

        TimeStepper stepper = new TimeStepper(grid, TimeStepper.Scheme.BACKWARD_EULER, badAlpha, 0.1, 50, 1e-6,
                dirichlet);
        stepper.initExact(0.0, mms);

        assertDoesNotThrow(() -> {
            stepper.step(0.0, mms);
        }, "TimeStepper обязан ловить FAIL_NON_SPD и прыгать на MINRES, а не падать в обморок");

        // Пруфаем (Assert) мы тут реально что-то посчитали, а не просто обошли луп по красоте
        assertTrue(stepper.getTotalPcgIters() > 0, "Итерации MINRES должны агрегироваться в общую стату");
    }

    @Test
    public void testAdaptiveStepLogic() {
        int N = 16;
        Grid2D grid = new Grid2D(N, N, 1.0, 1.0);
        double initialDt = 0.05;
        double T = 0.15;
        double adaptiveTol = 1e-3;
        ParallelExecutor.init(2);

        MMS mms = new MMS(MMS.TestCase.NON_ZERO_DIRICHLET, 0.1);
        DirichletBoundary dirichlet = new DirichletBoundary(mms);
        TimeStepper stepper = new TimeStepper(grid, TimeStepper.Scheme.CRANK_NICOLSON, 0.1, initialDt, 100, 1e-6,
                dirichlet);
        stepper.initExact(0.0, mms);

        double t = 0.0;
        double currentDt = initialDt;
        int steps = 0;

        double[] savedU = new double[grid.size()];
        double[] u1 = new double[grid.size()];

        while (t < T - 1e-12 && steps < 50) {
            if (t + currentDt > T) {
                currentDt = T - t;
            }

            stepper.copyState(savedU);

            // 1 step of dt
            stepper.setDt(currentDt);
            stepper.step(t, mms);
            stepper.copyState(u1);

            // 2 steps of dt/2
            stepper.restoreState(savedU);
            stepper.setDt(currentDt / 2.0);
            stepper.step(t, mms);
            stepper.step(t + currentDt / 2.0, mms);

            double[] u2 = stepper.getU();
            double error = Metrics.computeL2Error(grid, u1, u2) / 3.0; // p=2 for CN -> 2^2-1 = 3

            if (error <= adaptiveTol || currentDt < 1e-7) {
                t += currentDt;
                steps++;
            } else {
                stepper.restoreState(savedU);
            }

            if (error > 0.0) {
                currentDt = currentDt * Math.pow(adaptiveTol / error, 1.0 / 3.0);
            } else {
                currentDt *= 2.0;
            }
        }

        assertTrue(steps > 0, "Адаптивный шаг обязан сделать хоть одну итерацию");
        assertEquals(T, t, 1e-5, "Адаптивный шаг должен четко упереться в финальный тайминг T");
    }
}
