package pdelab.solver;

import pdelab.core.Grid2D;
import pdelab.core.Stencil;

/**
 * Маппит математический оператор A = I - factor * Laplacian.
 * Работает люто быстро, без единой аллокации (purely allocation-free).
 */
public class ImplicitMatrix implements MatrixOperator {
    private final Grid2D grid;
    private double factor; // Множитель: dt * alpha / 2 для Crank-Nicolson, или тупо dt * alpha для
                           // Backward Euler
    private final double[] tempLx;
    private final double[] kXFull;
    private final double[] kYFull;

    public ImplicitMatrix(Grid2D grid, double factor, double[] tempLx) {
        this(grid, factor, tempLx, null, null);
    }

    public ImplicitMatrix(Grid2D grid, double factor, double[] tempLx, double[] kXFull, double[] kYFull) {
        this.grid = grid;
        this.factor = factor;
        this.tempLx = tempLx;
        this.kXFull = kXFull;
        this.kYFull = kYFull;
    }

    public void updateFactor(double factor) {
        this.factor = factor;
    }

    @Override
    public void multiply(double[] x, double[] y) {
        // 1. Lx = L * x (Считаем Лапласиан)
        // x — вектор чисто по внутренним узлам (размер numInterior). Пробрасываем null
        // вместо uFull, так что все границы железобетонно считаются нулевыми (strict
        // zero).
        if (this.kXFull != null) {
            Stencil.applyDivKGradInterior(grid, x, null, kXFull, kYFull, tempLx);
        } else {
            Stencil.applyLaplacianInterior(grid, x, null, tempLx);
        }

        // 2. y = x - factor * Lx (Собираем итоговую правую часть оператора)
        // Делаем в один заход через saxpy стайл: y[i] = x[i] - factor * tempLx[i]
        pdelab.core.ParallelVectorOps.addScaled(x, -factor, tempLx, y);
    }
}
