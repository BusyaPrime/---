package pdelab.core;

/**
 * Заворачиваем 5-точечный Лапласиан в кор-логику (Stencil).
 * Работаем чисто на плоских double[] массивах, бережем L1/L2 кэши!
 * efficiency.
 */
public class Stencil {

    /**
     * Херачим пространственный Лапласиан (Δu) по вектору uInt, плюем резалт в
     * LuInt.
     * Оба (uInt и LuInt) строго размера numInterior(), шаг вправо - расстрел.
     * Краевые значения лениво подсасываются из uFull.
     */
    private static class LaplacianInteriorOp implements ParallelExecutor.ArrayOp {
        Grid2D grid;
        double[] uInt, uFull, LuInt;

        public void set(Grid2D grid, double[] uInt, double[] uFull, double[] LuInt) {
            this.grid = grid;
            this.uInt = uInt;
            this.uFull = uFull;
            this.LuInt = LuInt;
        }

        @Override
        public void compute(int startJ, int endJ) {
            int inX = grid.inX();
            int inY = grid.inY();
            int nx = grid.Nx();
            double ihx2 = grid.ihx2();
            double ihy2 = grid.ihy2();
            for (int j = startJ; j < endJ; j++) {
                int globalJ = j + 1;
                int intOffset = j * inX;

                double yC = grid.y()[globalJ];
                double yL = grid.y()[globalJ - 1];
                double yR = grid.y()[globalJ + 1];
                double dy_avg = 0.5 * (yR - yL);
                double inv_dyC_dyL = 1.0 / (dy_avg * (yC - yL));
                double inv_dyR_dyC = 1.0 / (dy_avg * (yR - yC));
                double inv_dyC = 1.0 / dy_avg;

                for (int i = 0; i < inX; i++) {
                    int globalI = i + 1;
                    int intIdx = intOffset + i;

                    double xC = grid.x()[globalI];
                    double xL = grid.x()[globalI - 1];
                    double xR = grid.x()[globalI + 1];
                    double dx_avg = 0.5 * (xR - xL);
                    double inv_dxC_dxL = 1.0 / (dx_avg * (xC - xL));
                    double inv_dxR_dxC = 1.0 / (dx_avg * (xR - xC));

                    double center = uInt[intIdx];
                    double left = (i == 0) ? (uFull != null ? uFull[grid.idx(0, globalJ)] : 0.0) : uInt[intIdx - 1];
                    double right = (i == inX - 1) ? (uFull != null ? uFull[grid.idx(nx - 1, globalJ)] : 0.0)
                            : uInt[intIdx + 1];
                    double down = (j == 0) ? (uFull != null ? uFull[grid.idx(globalI, 0)] : 0.0) : uInt[intIdx - inX];
                    double up = (j == inY - 1) ? (uFull != null ? uFull[grid.idx(globalI, grid.Ny() - 1)] : 0.0)
                            : uInt[intIdx + inX];

                    double d2udx2 = (right - center) * inv_dxR_dxC - (center - left) * inv_dxC_dxL;
                    double d2udy2 = (up - center) * inv_dyR_dyC - (center - down) * inv_dyC_dyL;

                    LuInt[intIdx] = d2udx2 + d2udy2;
                }
            }
        }
    }

    private static final LaplacianInteriorOp laplacianInteriorOp = new LaplacianInteriorOp();

    public static void applyLaplacianInterior(Grid2D grid, double[] uInt, double[] uFull, double[] LuInt) {
        laplacianInteriorOp.set(grid, uInt, uFull, LuInt);
        ParallelExecutor.executeContiguous(grid.inY(), laplacianInteriorOp);
    }

    public static void precomputeDiffusivityArrays(Grid2D grid, double[] kFull, double[] kXFull, double[] kYFull,
            String averaging) {
        int nx = grid.Nx();
        int ny = grid.Ny();
        boolean harmonic = "HARMONIC".equalsIgnoreCase(averaging);
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                int idx = grid.idx(i, j);
                double kCenter = kFull[idx];
                double kRight = (i < nx - 1) ? kFull[grid.idx(i + 1, j)] : kCenter;
                double kUp = (j < ny - 1) ? kFull[grid.idx(i, j + 1)] : kCenter;
                if (harmonic) {
                    double kX = (kCenter > 0 && kRight > 0) ? 2.0 * kCenter * kRight / (kCenter + kRight) : 0.0;
                    double kY = (kCenter > 0 && kUp > 0) ? 2.0 * kCenter * kUp / (kCenter + kUp) : 0.0;
                    kXFull[idx] = kX;
                    kYFull[idx] = kY;
                } else {
                    kXFull[idx] = 0.5 * (kCenter + kRight);
                    kYFull[idx] = 0.5 * (kCenter + kUp);
                }
            }
        }
    }

    /**
     * Прогоняем пространственный оператор \nabla \cdot (\kappa \nabla u) (Variable Diffusivity).
     * Uses arithmetic averaging for \kappa at half-points.
     */
    private static class DivKGradInteriorOp implements ParallelExecutor.ArrayOp {
        Grid2D grid;
        double[] uInt, uFull, kXFull, kYFull, LuInt;

        public void set(Grid2D grid, double[] uInt, double[] uFull, double[] kXFull, double[] kYFull, double[] LuInt) {
            this.grid = grid;
            this.uInt = uInt;
            this.uFull = uFull;
            this.kXFull = kXFull;
            this.kYFull = kYFull;
            this.LuInt = LuInt;
        }

        @Override
        public void compute(int startJ, int endJ) {
            int inX = grid.inX();
            int inY = grid.inY();
            int nx = grid.Nx();
            for (int j = startJ; j < endJ; j++) {
                int globalJ = j + 1;
                int intOffset = j * inX;

                double yC = grid.y()[globalJ];
                double yL = grid.y()[globalJ - 1];
                double yR = grid.y()[globalJ + 1];
                double dy_avg = 0.5 * (yR - yL);
                double inv_dy_avg = 1.0 / dy_avg;
                double inv_dyC_yL = 1.0 / (yC - yL);
                double inv_dyR_yC = 1.0 / (yR - yC);

                for (int i = 0; i < inX; i++) {
                    int globalI = i + 1;
                    int intIdx = intOffset + i;
                    int globalIdx = grid.idx(globalI, globalJ);

                    double xC = grid.x()[globalI];
                    double xL = grid.x()[globalI - 1];
                    double xR = grid.x()[globalI + 1];
                    double dx_avg = 0.5 * (xR - xL);
                    double inv_dx_avg = 1.0 / dx_avg;
                    double inv_dxC_xL = 1.0 / (xC - xL);
                    double inv_dxR_xC = 1.0 / (xR - xC);

                    double center = uInt[intIdx];
                    double left = (i == 0) ? (uFull != null ? uFull[grid.idx(0, globalJ)] : 0.0) : uInt[intIdx - 1];
                    double right = (i == inX - 1) ? (uFull != null ? uFull[grid.idx(nx - 1, globalJ)] : 0.0)
                            : uInt[intIdx + 1];
                    double down = (j == 0) ? (uFull != null ? uFull[grid.idx(globalI, 0)] : 0.0) : uInt[intIdx - inX];
                    double up = (j == inY - 1) ? (uFull != null ? uFull[grid.idx(globalI, grid.Ny() - 1)] : 0.0)
                            : uInt[intIdx + inX];

                    // K values are pre-multiplied by inverse distances inside
                    // precomputeDiffusivityArrays,
                    // НО они умножались с оглядкой на UNIFORM. Надо срочно расцепить зависимость
                    // precomputeDiffusivityArrays
                    // или пересчитать прям тут. Надо бы зафиксить precomputeDiffusivityArrays!
                    // For now, assume kXFull[globalIdx] simply holds the actual interfacial kappa:
                    // K_{i+1/2, j}

                    double d2udx2 = (kXFull[globalIdx] * (right - center) * inv_dxR_xC
                            - kXFull[globalIdx - 1] * (center - left) * inv_dxC_xL) * inv_dx_avg;

                    double d2udy2 = (kYFull[globalIdx] * (up - center) * inv_dyR_yC
                            - kYFull[globalIdx - nx] * (center - down) * inv_dyC_yL) * inv_dy_avg;

                    LuInt[intIdx] = d2udx2 + d2udy2;
                }
            }
        }
    }

    private static final DivKGradInteriorOp divKGradInteriorOp = new DivKGradInteriorOp();

    public static void applyDivKGradInterior(Grid2D grid, double[] uInt, double[] uFull, double[] kXFull,
            double[] kYFull, double[] LuInt) {
        divKGradInteriorOp.set(grid, uInt, uFull, kXFull, kYFull, LuInt);
        ParallelExecutor.executeContiguous(grid.inY(), divKGradInteriorOp);
    }

    /**
     * Прогоняем пространственную Конвекцию (b * \nabla u) по 1-му порядку
     * Upwind.
     * Юзаем векторные поля bXFull и bYFull, стянутые по центрам ячеек.
     */
    private static class ConvectionUpwindInteriorOp implements ParallelExecutor.ArrayOp {
        Grid2D grid;
        double[] uInt, uFull, bXFull, bYFull, LuInt;

        public void set(Grid2D grid, double[] uInt, double[] uFull, double[] bXFull, double[] bYFull, double[] LuInt) {
            this.grid = grid;
            this.uInt = uInt;
            this.uFull = uFull;
            this.bXFull = bXFull;
            this.bYFull = bYFull;
            this.LuInt = LuInt;
        }

        private double minmod(double r) {
            return Math.max(0.0, Math.min(1.0, r));
        }

        @Override
        public void compute(int startJ, int endJ) {
            int inX = grid.inX();
            int inY = grid.inY();
            int nx = grid.Nx();

            for (int j = startJ; j < endJ; j++) {
                int globalJ = j + 1;
                int intOffset = j * inX;

                double yC = grid.y()[globalJ];
                double yL = grid.y()[globalJ - 1];
                double yR = grid.y()[globalJ + 1];
                double yLL = grid.y()[Math.max(0, globalJ - 2)];
                double yRR = grid.y()[Math.min(grid.Ny() - 1, globalJ + 2)];

                double dy_CR = yR - yC;
                double dy_LC = yC - yL;
                double dy_LL_L = yL - yLL;
                double dy_R_RR = yRR - yR;

                double inv_dy_avg = 1.0 / (0.5 * (yR - yL));

                for (int i = 0; i < inX; i++) {
                    int globalI = i + 1;
                    int intIdx = intOffset + i;
                    int globalIdx = grid.idx(globalI, globalJ);

                    double xC = grid.x()[globalI];
                    double xL = grid.x()[globalI - 1];
                    double xR = grid.x()[globalI + 1];
                    double xLL = grid.x()[Math.max(0, globalI - 2)];
                    double xRR = grid.x()[Math.min(grid.Nx() - 1, globalI + 2)];

                    double dx_CR = xR - xC;
                    double dx_LC = xC - xL;
                    double dx_LL_L = xL - xLL;
                    double dx_R_RR = xRR - xR;

                    double inv_dx_avg = 1.0 / (0.5 * (xR - xL));

                    double center = uInt[intIdx];

                    double left = (i == 0) ? (uFull != null ? uFull[grid.idx(0, globalJ)] : 0.0) : uInt[intIdx - 1];
                    double right = (i == inX - 1) ? (uFull != null ? uFull[grid.idx(nx - 1, globalJ)] : 0.0)
                            : uInt[intIdx + 1];
                    double down = (j == 0) ? (uFull != null ? uFull[grid.idx(globalI, 0)] : 0.0) : uInt[intIdx - inX];
                    double up = (j == inY - 1) ? (uFull != null ? uFull[grid.idx(globalI, grid.Ny() - 1)] : 0.0)
                            : uInt[intIdx + inX];

                    double left2 = (i == 1) ? (uFull != null ? uFull[grid.idx(0, globalJ)] : 0.0)
                            : (i > 1) ? uInt[intIdx - 2] : 0.0;
                    double right2 = (i == inX - 2) ? (uFull != null ? uFull[grid.idx(nx - 1, globalJ)] : 0.0)
                            : (i < inX - 2) ? uInt[intIdx + 2] : 0.0;
                    double down2 = (j == 1) ? (uFull != null ? uFull[grid.idx(globalI, 0)] : 0.0)
                            : (j > 1) ? uInt[intIdx - 2 * inX] : 0.0;
                    double up2 = (j == inY - 2) ? (uFull != null ? uFull[grid.idx(globalI, grid.Ny() - 1)] : 0.0)
                            : (j < inY - 2) ? uInt[intIdx + 2 * inX] : 0.0;

                    double bx = bXFull[globalIdx];
                    double by = bYFull[globalIdx];

                    double dudx = 0.0;
                    if (bx > 0) {
                        if (i == 0)
                            dudx = (center - left) / dx_LC;
                        else {
                            double grad_fw = (right - center) / dx_CR;
                            double grad_bw = (center - left) / dx_LC;
                            double grad_bbw = (left - left2) / dx_LL_L;

                            double r_i = (grad_bw == 0) ? 0.0 : grad_fw / grad_bw;
                            double r_im1 = (grad_bbw == 0) ? 0.0 : grad_bw / grad_bbw;

                            double fluxRight = center + 0.5 * minmod(r_i) * (center - left) * (dx_CR / dx_LC);
                            double fluxLeft = left + 0.5 * minmod(r_im1) * (left - left2) * (dx_LC / dx_LL_L);
                            dudx = (fluxRight - fluxLeft) * inv_dx_avg;
                        }
                    } else {
                        if (i == inX - 1)
                            dudx = (right - center) / dx_CR;
                        else {
                            double grad_fw = (right - center) / dx_CR;
                            double grad_ffw = (right2 - right) / dx_R_RR;
                            double grad_bw = (center - left) / dx_LC;

                            double r_i = (grad_fw == 0) ? 0.0 : grad_bw / grad_fw;
                            double r_ip1 = (grad_ffw == 0) ? 0.0 : grad_fw / grad_ffw;

                            double fluxLeft = center - 0.5 * minmod(r_i) * (right - center) * (dx_LC / dx_CR);
                            double fluxRight = right - 0.5 * minmod(r_ip1) * (right2 - right) * (dx_CR / dx_R_RR);
                            dudx = (fluxRight - fluxLeft) * inv_dx_avg;
                        }
                    }

                    double dudy = 0.0;
                    if (by > 0) {
                        if (j == 0)
                            dudy = (center - down) / dy_LC;
                        else {
                            double grad_fw = (up - center) / dy_CR;
                            double grad_bw = (center - down) / dy_LC;
                            double grad_bbw = (down - down2) / dy_LL_L;

                            double r_j = (grad_bw == 0) ? 0.0 : grad_fw / grad_bw;
                            double r_jm1 = (grad_bbw == 0) ? 0.0 : grad_bw / grad_bbw;

                            double fluxUp = center + 0.5 * minmod(r_j) * (center - down) * (dy_CR / dy_LC);
                            double fluxDown = down + 0.5 * minmod(r_jm1) * (down - down2) * (dy_LC / dy_LL_L);
                            dudy = (fluxUp - fluxDown) * inv_dy_avg;
                        }
                    } else {
                        if (j == inY - 1)
                            dudy = (up - center) / dy_CR;
                        else {
                            double grad_fw = (up - center) / dy_CR;
                            double grad_ffw = (up2 - up) / dy_R_RR;
                            double grad_bw = (center - down) / dy_LC;

                            double r_j = (grad_fw == 0) ? 0.0 : grad_bw / grad_fw;
                            double r_jp1 = (grad_ffw == 0) ? 0.0 : grad_fw / grad_ffw;

                            double fluxDown = center - 0.5 * minmod(r_j) * (up - center) * (dy_LC / dy_CR);
                            double fluxUp = up - 0.5 * minmod(r_jp1) * (up2 - up) * (dy_CR / dy_R_RR);
                            dudy = (fluxUp - fluxDown) * inv_dy_avg;
                        }
                    }

                    LuInt[intIdx] += bx * dudx + by * dudy;
                }
            }
        }
    }

    private static final ConvectionUpwindInteriorOp convectionUpwindInteriorOp = new ConvectionUpwindInteriorOp();

    public static void applyConvectionUpwindInterior(Grid2D grid, double[] uInt, double[] uFull, double[] bXFull,
            double[] bYFull, double[] LuInt) {
        convectionUpwindInteriorOp.set(grid, uInt, uFull, bXFull, bYFull, LuInt);
        ParallelExecutor.executeContiguous(grid.inY(), convectionUpwindInteriorOp);
    }
}
