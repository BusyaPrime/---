package pdelab.core;

/**
 * Утилиты для статистики и подбивания базы под критерии сходимости.
 */
public class Stats {

    public record RegressionResult(double slope, double intercept, double rSquared) {
    }

    /**
     * Шарашит линейную регрессию методом наименьших квадратов в log2-log2.
     * Иксы и игреки обязаны быть одной длины (иначе фейл).
     * Evaluates y = C * x^slope => log2(y) = log2(C) + slope * log2(x)
     */
    public static RegressionResult logLogRegression(double[] x, double[] y) {
        if (x.length != y.length || x.length < 2) {
            throw new IllegalArgumentException("Invalid input arrays for regression");
        }

        int n = x.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;

        for (int i = 0; i < n; i++) {
            double lx = Math.log(x[i]) / Math.log(2.0);
            double ly = Math.log(y[i]) / Math.log(2.0);
            sumX += lx;
            sumY += ly;
            sumXY += lx * ly;
            sumX2 += lx * lx;
            sumY2 += ly * ly;
        }

        double xMean = sumX / n;
        double yMean = sumY / n;

        double sxx = sumX2 - n * xMean * xMean;
        double sxy = sumXY - n * xMean * yMean;
        double syy = sumY2 - n * yMean * yMean;

        double slope = sxy / sxx;
        double intercept = yMean - slope * xMean;

        // R^2 иногда может слегка перевалить за 1.0 (пробивает крышу из-за флоатов).
        double rSquared = (sxy * sxy) / (sxx * syy);
        rSquared = Math.min(1.0, rSquared);

        return new RegressionResult(slope, intercept, rSquared);
    }
}
