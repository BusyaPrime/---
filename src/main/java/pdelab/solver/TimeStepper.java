package pdelab.solver;

import pdelab.core.Grid2D;
import pdelab.core.VectorOps;
import pdelab.core.ParallelVectorOps;
import pdelab.core.Stencil;
import pdelab.core.MMS;
import pdelab.core.BoundaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pdelab.core.VectorField;

/**
 * Отдувается за весь основной луп по времени (Time Loop). Вывозит весь движ.
 * Хот-луп без аллокаций (GC спит), всё хардкорно переиспользуется.
 */
public class TimeStepper {
    private static final Logger log = LoggerFactory.getLogger(TimeStepper.class);

    public enum Scheme {
        CRANK_NICOLSON,
        BACKWARD_EULER,
        IMEX
    }

    private final Grid2D grid;
    private final Scheme scheme;
    private final double alpha;
    private double dt;

    // Core state (Фулл сетки)
    private final double[] u;
    private final double[] uNext;
    private final double[] fFull; // для замера аналитики MMS

    // Темповские массивы для сборки RHS (Строго под Внутренние узлы)
    private final double[] uInt;
    private final double[] uNextInt;
    private final double[] fCurrentInt;
    private final double[] rhsInt;
    private final double[] tempLxInt;
    private final double[] kFull;
    private final double[] kXFull;
    private final double[] kYFull;
    private final double[] bXFull;
    private final double[] bYFull;

    private final LinearSolver linearSolver;
    private final LinearSolver fallbackSolver;
    private final ImplicitMatrix A;
    private final Preconditioner M;
    private final BoundaryOperator boundaryOperator;

    private long totalPcgIters = 0;
    private double maxAbsResidual = 0.0;
    private double maxRelResidual = 0.0;

    public TimeStepper(Grid2D grid, Scheme scheme, double alpha, double dt, int maxIters, double tol,
            BoundaryOperator boundaryOperator) {
        this(grid, scheme, alpha, dt, maxIters, tol, null, null, boundaryOperator, null);
    }

    public TimeStepper(Grid2D grid, Scheme scheme, double alpha, double dt, int maxIters, double tol, double[] kFull,
            String precondType, BoundaryOperator boundaryOperator) {
        this(grid, scheme, alpha, dt, maxIters, tol, kFull, precondType, boundaryOperator, null);
    }

    public TimeStepper(Grid2D grid, Scheme scheme, double alpha, double dt, int maxIters, double tol, double[] kFull,
            String precondType, BoundaryOperator boundaryOperator, VectorField bField) {
        this(grid, scheme, alpha, dt, maxIters, tol, kFull, precondType, boundaryOperator, bField, "ARITHMETIC");
    }

    public TimeStepper(Grid2D grid, Scheme scheme, double alpha, double dt, int maxIters, double tol, double[] kFull,
            String precondType, BoundaryOperator boundaryOperator, VectorField bField, String kappaAveraging) {
        this.grid = grid;
        this.scheme = scheme;
        this.alpha = alpha;
        this.dt = dt;

        int nFull = grid.size();
        this.u = new double[nFull];
        this.uNext = new double[nFull];
        this.fFull = new double[nFull];

        int nInt = grid.numInterior();
        this.uInt = new double[nInt];
        this.uNextInt = new double[nInt];
        this.rhsInt = new double[nInt];
        this.fCurrentInt = new double[nInt];
        this.tempLxInt = new double[nInt];

        double factor = scheme == Scheme.CRANK_NICOLSON ? (dt * alpha * 0.5) : (dt * alpha);
        this.kFull = kFull;

        if (this.kFull != null) {
            this.kXFull = new double[nFull];
            this.kYFull = new double[nFull];
            Stencil.precomputeDiffusivityArrays(grid, kFull, kXFull, kYFull, kappaAveraging);
        } else {
            this.kXFull = null;
            this.kYFull = null;
        }

        if (bField != null) {
            this.bXFull = new double[nFull];
            this.bYFull = new double[nFull];
            double maxVx = 0.0;
            double maxVy = 0.0;
            double minDx = Double.MAX_VALUE;
            double minDy = Double.MAX_VALUE;

            for (int j = 0; j < grid.Ny(); j++) {
                double y = grid.y()[j];
                if (j < grid.Ny() - 1)
                    minDy = Math.min(minDy, grid.y()[j + 1] - y);

                for (int i = 0; i < grid.Nx(); i++) {
                    double x = grid.x()[i];
                    if (i < grid.Nx() - 1)
                        minDx = Math.min(minDx, grid.x()[i + 1] - x);

                    int idx = grid.idx(i, j);
                    double vx = bField.bx(x, y);
                    double vy = bField.by(x, y);
                    this.bXFull[idx] = vx;
                    this.bYFull[idx] = vy;

                    maxVx = Math.max(maxVx, Math.abs(vx));
                    maxVy = Math.max(maxVy, Math.abs(vy));
                }
            }

            // Чекаем лимиты Куранта (CFL limit) для конвекции, иначе расплющит
            if (maxVx > 0.0 || maxVy > 0.0) {
                double dtCFL_x = maxVx > 0 ? minDx / maxVx : Double.MAX_VALUE;
                double dtCFL_y = maxVy > 0 ? minDy / maxVy : Double.MAX_VALUE;
                double maxSafeDt = Math.min(dtCFL_x, dtCFL_y);

                if (dt > maxSafeDt) {
                    throw new IllegalArgumentException(String.format(
                            "АЛЯРМ! CFL Condition порван на британский флаг! Для стабильности конвекции нужно dt <= %.6f, а мы подали dt = %.6f. "
                                    +
                                    "Max Vel = (%.2f, %.2f), Min Cell = (%.4f, %.4f)",
                            maxSafeDt, dt, maxVx, maxVy, minDx, minDy));
                }
                log.info("CFL чекер пробит успешно (мы в зеленой зоне): dt={} <= maxSafeDt={}", dt, maxSafeDt);
            }
        } else {
            this.bXFull = null;
            this.bYFull = null;
        }

        this.linearSolver = new PCG(grid, maxIters, tol);
        this.fallbackSolver = new MINRESFallback(grid, maxIters, tol);
        this.A = new ImplicitMatrix(grid, factor, tempLxInt, kXFull, kYFull);

        if ("SSOR".equalsIgnoreCase(precondType)) {
            // Для SSOR омега=1.5 — это классика жанра (золотое сечение)
            this.M = new SSORPreconditioner(grid, factor, 1.5, kFull);
        } else if ("MG".equalsIgnoreCase(precondType)) {
            this.M = new MGPreconditioner(grid, factor, kFull);
        } else {
            this.M = new JacobiPreconditioner(grid, factor, kFull);
        }
        this.boundaryOperator = boundaryOperator;
    }

    public double[] getU() {
        return u;
    }

    public double getDt() {
        return dt;
    }

    public void setDt(double newDt) {
        this.dt = newDt;
        double factor = scheme == Scheme.CRANK_NICOLSON ? (dt * alpha * 0.5) : (dt * alpha);
        A.updateFactor(factor);
        M.updateFactor(factor);
    }

    public void copyState(double[] dest) {
        System.arraycopy(u, 0, dest, 0, grid.size());
    }

    public void restoreState(double[] src) {
        System.arraycopy(src, 0, u, 0, grid.size());
    }

    public long getTotalPcgIters() {
        return totalPcgIters;
    }

    public double getMaxAbsResidual() {
        return maxAbsResidual;
    }

    public double getMaxRelResidual() {
        return maxRelResidual;
    }

    public void initExact(double t, MMS mms) {
        mms.evaluateExact(grid, t, u);
    }

    public void step(double t, MMS mms) {
        // 1. Считаем форсинг на ФУЛЛ сетке, потом выкусываем во ВНУТРЕННЮЮ
        if (scheme == Scheme.CRANK_NICOLSON) {
            mms.evaluateForcing(grid, t + dt * 0.5, fFull);
        } else {
            mms.evaluateForcing(grid, t + dt, fFull);
        }
        grid.extractInterior(fFull, fCurrentInt);

        // 2. Готовим стейт (убираем мусор с краев)
        boundaryOperator.apply(grid, u, t);
        grid.extractInterior(u, uInt);

        // 3. Собираем базовую правую часть (RHS) по явной схеме
        if (scheme == Scheme.CRANK_NICOLSON) {
            // rhs = u^n + dt*alpha/2 * L(u^n) + dt f^{n+1/2}
            if (this.kFull != null) {
                Stencil.applyDivKGradInterior(grid, uInt, u, kXFull, kYFull, tempLxInt);
            } else {
                Stencil.applyLaplacianInterior(grid, uInt, u, tempLxInt);
            }

            VectorOps.copy(uInt, rhsInt);
            ParallelVectorOps.axpy(dt * alpha * 0.5, tempLxInt, rhsInt);
            ParallelVectorOps.axpy(dt, fCurrentInt, rhsInt);
        } else {
            // BE: rhs = u^n + dt f^{n+1}
            VectorOps.copy(uInt, rhsInt);
            ParallelVectorOps.axpy(dt, fCurrentInt, rhsInt);
        }

        // 3.5 IMEX Конвекция (считаем явно на шаге t^n)
        if (this.bXFull != null && this.bYFull != null) {
            java.util.Arrays.fill(tempLxInt, 0.0);
            Stencil.applyConvectionUpwindInterior(grid, uInt, u, bXFull, bYFull, tempLxInt);
            ParallelVectorOps.axpy(-dt, tempLxInt, rhsInt); // rhs -= dt * (b \cdot \nabla u)
        }

        // 4. Пробрасываем Граничные Условия будущего шага в RHS
        // Хитрый финт: вычисляем L(u_future), занулив всё внутри.
        // Так мы чисто отщипываем влияние границ (factor * L_bnd(u_bnd)). Изящно.
        double factor = scheme == Scheme.CRANK_NICOLSON ? (dt * alpha * 0.5) : (dt * alpha);
        boundaryOperator.apply(grid, uNext, t + dt);
        java.util.Arrays.fill(uNextInt, 0.0);
        if (this.kFull != null) {
            Stencil.applyDivKGradInterior(grid, uNextInt, uNext, kXFull, kYFull, tempLxInt);
        } else {
            Stencil.applyLaplacianInterior(grid, uNextInt, uNext, tempLxInt);
        }
        ParallelVectorOps.axpy(factor, tempLxInt, rhsInt);

        // 5. Начальный guess для СЛАУ (берем с предыдущего шага, чтоб PCG меньше потел)
        VectorOps.copy(uInt, uNextInt);

        // 6. Скармливаем матрицу Решателю (только внутренние узлы!)
        LinearSolver.SolveResult result = linearSolver.solve(A, M, rhsInt, uNextInt);
        if (result.status() == LinearSolver.Status.FAIL_NON_SPD) {
            log.warn("PCG encountered FAIL_NON_SPD (indefinite matrix). Engaging MINRESFallback...");
            result = fallbackSolver.solve(A, M, rhsInt, uNextInt);
        }

        if (result.status() != LinearSolver.Status.CONVERGED && result.status() != LinearSolver.Status.MAX_ITERS) {
            throw new RuntimeException("Линейный солвер лег с треском, статус: " + result.status() + " at t=" + t);
        }
        this.totalPcgIters += result.iterations();
        this.maxAbsResidual = Math.max(this.maxAbsResidual, result.absResidual());
        this.maxRelResidual = Math.max(this.maxRelResidual, result.relResidual());

        // 7. Вливаем решенные внутренности обратно в фулл-стейт (границы не трогаем)
        grid.injectInterior(uNextInt, uNext);

        // 8. Двигаем время (свапаем массивы, никаких new double[])
        VectorOps.copy(uNext, u);
    }
}
