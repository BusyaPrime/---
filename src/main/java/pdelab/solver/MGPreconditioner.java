package pdelab.solver;

import pdelab.core.Grid2D;
import pdelab.core.ParallelExecutor;
import pdelab.core.Stencil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MGPreconditioner implements Preconditioner {
    private static final Logger log = LoggerFactory.getLogger(MGPreconditioner.class);

    private final int maxLevels;
    private final List<Level> levels;
    private static final double OMEGA = 0.8; // Параметр затухания (damping) для взвешенного Якоби (шоб не разнесло)

    private static class Level {
        Grid2D grid;
        ImplicitMatrix A;
        double factor;
        double[] r;
        double[] z;
        double[] res;
        double[] kFull;
        double[] kXFull, kYFull;
        double[] diagA;

        public Level(Grid2D grid, double factor, double[] kFull) {
            this.grid = grid;
            this.factor = factor;
            int nInt = grid.numInterior();
            this.r = new double[nInt];
            this.z = new double[nInt];
            this.res = new double[nInt];
            this.kFull = kFull;

            double[] tempLxInt = new double[nInt];
            if (this.kFull != null) {
                this.kXFull = new double[grid.size()];
                this.kYFull = new double[grid.size()];
                Stencil.precomputeDiffusivityArrays(grid, kFull, kXFull, kYFull, "HARMONIC");
            }
            this.A = new ImplicitMatrix(grid, factor, tempLxInt, kXFull, kYFull);

            // Заранее считаем диагональ Якоби, чтоб потом не потеть в хот-лупе
            this.diagA = new double[nInt];
            updateDiagonal();
        }

        public void updateDiagonal() {
            int inX = grid.inX();
            int inY = grid.inY();
            int nx = grid.Nx();
            double ihx2 = grid.ihx2();
            double ihy2 = grid.ihy2();

            for (int j = 0; j < inY; j++) {
                int globalJ = j + 1;
                for (int i = 0; i < inX; i++) {
                    int globalI = i + 1;
                    int globalIdx = grid.idx(globalI, globalJ);
                    int intIdx = j * inX + i;

                    double lDiag;
                    if (kXFull != null) {
                        lDiag = -(kXFull[globalIdx] + kXFull[globalIdx - 1])
                                - (kYFull[globalIdx] + kYFull[globalIdx - nx]);
                    } else {
                        lDiag = -2.0 * (ihx2 + ihy2);
                    }
                    diagA[intIdx] = 1.0 - factor * lDiag;
                }
            }
        }
    }

    public MGPreconditioner(Grid2D fineGrid, double factor, double[] kFull) {
        this.levels = new ArrayList<>();
        Grid2D currentGrid = fineGrid;
        List<Grid2D> gridHierarchy = new ArrayList<>();
        gridHierarchy.add(currentGrid);

        // Загрубляем сетку (coarsen), пока интервалы делятся на 2, но держим минимум 3
        // внутренние точки (Nx=5), чтобы матан не схлопнулся
        while ((currentGrid.Nx() - 1) % 2 == 0 && (currentGrid.Ny() - 1) % 2 == 0 && currentGrid.Nx() > 3) {
            int newNx = (currentGrid.Nx() - 1) / 2 + 1;
            int newNy = (currentGrid.Ny() - 1) / 2 + 1;
            currentGrid = new Grid2D(newNx, newNy, fineGrid.Lx(), fineGrid.Ly());
            gridHierarchy.add(currentGrid);
            if (gridHierarchy.size() >= 5)
                break; // хватит 5 уровней, иначе улетим в Марианскую впадину
        }

        this.maxLevels = gridHierarchy.size();
        if (maxLevels == 1) {
            log.warn("MG Preconditioner: сетка не бьется на 2. Откатываемся до чистого Jacobi (будем тормозить).");
        } else {
            log.info("MG Preconditioner подняли, уровней рестрикции: {}.", maxLevels);
        }

        double[] currentK = kFull;
        for (int l = 0; l < maxLevels; l++) {
            Grid2D g = gridHierarchy.get(l);
            levels.add(new Level(g, factor, currentK));

            // Рестриктим (сужаем) kFull жестким инжекшном (без сглаживания)
            if (l < maxLevels - 1 && currentK != null) {
                Grid2D nextG = gridHierarchy.get(l + 1);
                double[] nextK = new double[nextG.size()];
                for (int j = 0; j < nextG.Ny(); j++) {
                    for (int i = 0; i < nextG.Nx(); i++) {
                        nextK[nextG.idx(i, j)] = currentK[g.idx(2 * i, 2 * j)];
                    }
                }
                currentK = nextK;
            }
        }
    }

    @Override
    public void updateFactor(double factor) {
        for (Level l : levels) {
            l.factor = factor;
            l.A.updateFactor(factor);
            l.updateDiagonal();
        }
    }

    @Override
    public void apply(double[] rIn, double[] zOut) {
        if (maxLevels == 1) {
            // Схлопываемся до дефолтного Якоби
            smooth(0, rIn, zOut, 1);
            return;
        }

        // Инициализируем (поднимаем базовые структуры, выделяем память) для топ-левела
        Level top = levels.get(0);
        System.arraycopy(rIn, 0, top.r, 0, rIn.length);
        Arrays.fill(top.z, 0.0);

        vCycle(0);

        System.arraycopy(top.z, 0, zOut, 0, zOut.length);
    }

    private void vCycle(int l) {
        Level lvl = levels.get(l);

        if (l == maxLevels - 1) {
            // Самая грубая сетка - гасим ошибку жестким сглаживанием (heavy smooth)
            Arrays.fill(lvl.z, 0.0);
            smooth(l, lvl.r, lvl.z, 50);
            return;
        }

        // Pre-smooth (Сбриваем высокие частоты)
        smooth(l, lvl.r, lvl.z, 2);

        // res = r - A*z (Вытаскиваем невязку)
        Arrays.fill(lvl.res, 0.0);
        lvl.A.multiply(lvl.z, lvl.res);
        for (int i = 0; i < lvl.res.length; i++) {
            lvl.res[i] = lvl.r[i] - lvl.res[i];
        }

        // Restrict (прокидываем residual на уровень пониже: l+1)
        Level nextLvl = levels.get(l + 1);
        restrict(lvl.grid, lvl.res, nextLvl.grid, nextLvl.r);
        Arrays.fill(nextLvl.z, 0.0);

        // Проваливаемся глубже в рекурсию (V-Cycle)
        vCycle(l + 1);

        // Prolongate (интерполируем коррекцию ошибки обратно наверх)
        Arrays.fill(lvl.res, 0.0); // юзаем res как темповый буфер для пролонгированной ошибки (экономим память)
        prolongate(nextLvl.grid, nextLvl.z, lvl.grid, lvl.res);

        // z = z + e (накатываем коррекцию)
        for (int i = 0; i < lvl.z.length; i++) {
            lvl.z[i] += lvl.res[i];
        }

        // Post-smooth (гасим шум после интерполяции)
        smooth(l, lvl.r, lvl.z, 2);
    }

    private void smooth(int l, double[] rhs, double[] z, int iters) {
        Level lvl = levels.get(l);
        double[] tempAx = lvl.res; // переиспользуем буфер для A*x (zero-allocation)

        for (int it = 0; it < iters; it++) {
            Arrays.fill(tempAx, 0.0);
            lvl.A.multiply(z, tempAx);
            for (int i = 0; i < z.length; i++) {
                z[i] += OMEGA * (rhs[i] - tempAx[i]) / lvl.diagA[i];
            }
        }
    }

    private void restrict(Grid2D fine, double[] r_h, Grid2D coarse, double[] r_H) {
        int cInX = coarse.inX();
        int cInY = coarse.inY();
        int fInX = fine.inX();
        int fInY = fine.inY();

        for (int cj = 0; cj < cInY; cj++) {
            int fj = 2 * (cj + 1) - 1; // Fine interior J (мелкий индекс) соответствующий крупному interior J
            for (int ci = 0; ci < cInX; ci++) {
                int fi = 2 * (ci + 1) - 1; // Fine interior I (мелкий индекс I)

                double center = val(r_h, fInX, fInY, fi, fj);
                double edges = val(r_h, fInX, fInY, fi - 1, fj) + val(r_h, fInX, fInY, fi + 1, fj) +
                        val(r_h, fInX, fInY, fi, fj - 1) + val(r_h, fInX, fInY, fi, fj + 1);
                double corners = val(r_h, fInX, fInY, fi - 1, fj - 1) + val(r_h, fInX, fInY, fi + 1, fj - 1) +
                        val(r_h, fInX, fInY, fi - 1, fj + 1) + val(r_h, fInX, fInY, fi + 1, fj + 1);

                r_H[cj * cInX + ci] = 0.25 * center + 0.125 * edges + 0.0625 * corners;
            }
        }
    }

    private void prolongate(Grid2D coarse, double[] e_H, Grid2D fine, double[] e_h) {
        int fInX = fine.inX();
        int fInY = fine.inY();
        int cInX = coarse.inX();
        int cInY = coarse.inY();

        for (int fj = 0; fj < fInY; fj++) {
            int fineNodeJ = fj + 1;
            boolean jEven = (fineNodeJ % 2 == 0);
            int cjDown = (fineNodeJ - 1) / 2 - 1;
            int cjUp = (fineNodeJ + 1) / 2 - 1;

            for (int fi = 0; fi < fInX; fi++) {
                int fineNodeI = fi + 1;
                boolean iEven = (fineNodeI % 2 == 0);
                int ciLeft = (fineNodeI - 1) / 2 - 1;
                int ciRight = (fineNodeI + 1) / 2 - 1;

                double val;
                if (iEven && jEven) {
                    // Полное совпадение узлов (Exact overlay)
                    val = val(e_H, cInX, cInY, (fineNodeI / 2) - 1, (fineNodeJ / 2) - 1);
                } else if (iEven && !jEven) {
                    val = 0.5 * (val(e_H, cInX, cInY, fineNodeI / 2 - 1, cjDown) +
                            val(e_H, cInX, cInY, fineNodeI / 2 - 1, cjUp));
                } else if (!iEven && jEven) {
                    val = 0.5 * (val(e_H, cInX, cInY, ciLeft, fineNodeJ / 2 - 1) +
                            val(e_H, cInX, cInY, ciRight, fineNodeJ / 2 - 1));
                } else {
                    val = 0.25 * (val(e_H, cInX, cInY, ciLeft, cjDown) +
                            val(e_H, cInX, cInY, ciRight, cjDown) +
                            val(e_H, cInX, cInY, ciLeft, cjUp) +
                            val(e_H, cInX, cInY, ciRight, cjUp));
                }
                e_h[fj * fInX + fi] = val;
            }
        }
    }

    private double val(double[] arr, int inX, int inY, int i, int j) {
        if (i < 0 || i >= inX || j < 0 || j >= inY)
            return 0.0;
        return arr[j * inX + i];
    }
}
