package pdelab.core;

/**
 * Жестко сверяет массивы с трушной аналитикой.
 */
public class Metrics {

    public static double computeL2Error(Grid2D grid, double[] uNum, double[] uExact) {
        double dVolume = grid.hx() * grid.hy();
        int inX = grid.inX();
        int inY = grid.inY();
        double sumSq = 0.0;
        for (int j = 1; j <= inY; j++) {
            for (int i = 1; i <= inX; i++) {
                int idx = grid.idx(i, j);
                double diff = uNum[idx] - uExact[idx];
                sumSq += diff * diff * dVolume;
            }
        }
        return Math.sqrt(sumSq);
    }

    public static double computeLinfError(Grid2D grid, double[] uNum, double[] uExact) {
        int inX = grid.inX();
        int inY = grid.inY();
        double maxError = 0.0;
        for (int j = 1; j <= inY; j++) {
            for (int i = 1; i <= inX; i++) {
                int idx = grid.idx(i, j);
                double diff = Math.abs(uNum[idx] - uExact[idx]);
                if (diff > maxError)
                    maxError = diff;
            }
        }
        return maxError;
    }

    public static double computeRelativeL2Error(Grid2D grid, double[] uNum, double[] uExact) {
        double l2Error = computeL2Error(grid, uNum, uExact);
        double dVolume = grid.hx() * grid.hy();
        int inX = grid.inX();
        int inY = grid.inY();
        double sumSqExact = 0.0;
        for (int j = 1; j <= inY; j++) {
            for (int i = 1; i <= inX; i++) {
                int idx = grid.idx(i, j);
                sumSqExact += uExact[idx] * uExact[idx] * dVolume;
            }
        }
        double normExact = Math.sqrt(sumSqExact);
        if (normExact < 1e-15)
            return l2Error; // Protection against division by zero
        return l2Error / normExact;
    }

    public static double computeH1SeminormError(Grid2D grid, double[] uNum, double[] uExact) {
        double dVolume = grid.hx() * grid.hy();
        double ihx = 1.0 / grid.hx();
        double ihy = 1.0 / grid.hy();
        int inX = grid.inX();
        int inY = grid.inY();
        double sumSq = 0.0;

        for (int j = 1; j <= inY; j++) {
            for (int i = 1; i <= inX; i++) {
                int idxRight = grid.idx(i + 1, j);
                int idxLeft = grid.idx(i - 1, j);
                int idxUp = grid.idx(i, j + 1);
                int idxDown = grid.idx(i, j - 1);

                double eRight = uNum[idxRight] - uExact[idxRight];
                double eLeft = uNum[idxLeft] - uExact[idxLeft];
                double eUp = uNum[idxUp] - uExact[idxUp];
                double eDown = uNum[idxDown] - uExact[idxDown];

                double dedx = (eRight - eLeft) * 0.5 * ihx;
                double dedy = (eUp - eDown) * 0.5 * ihy;

                sumSq += (dedx * dedx + dedy * dedy) * dVolume;
            }
        }
        return Math.sqrt(sumSq);
    }
}
