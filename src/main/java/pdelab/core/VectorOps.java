package pdelab.core;

import java.util.Arrays;

/**
 * Pure primitive vector operations on 1D representations of numerical fields.
 * Операции пишут только в пре-аллоцированные массивы, шоб ГЦ не ругался (zero GC overhead).
 * zero-allocation
 * (GC-free) in inner loops.
 */
public class VectorOps {

    public static void fill(double[] x, double value) {
        Arrays.fill(x, value);
    }

    public static void copy(double[] src, double[] dst) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    /**
     * y = a * x + y
     */
    public static void axpy(double a, double[] x, double[] y) {
        int n = x.length;
        for (int i = 0; i < n; i++) {
            y[i] += a * x[i];
        }
    }

    /**
     * y = a * x + b * y
     */
    public static void axpby(double a, double[] x, double b, double[] y) {
        int n = x.length;
        for (int i = 0; i < n; i++) {
            y[i] = a * x[i] + b * y[i];
        }
    }

    /**
     * res = x + a * y
     */
    public static void addScaled(double[] x, double a, double[] y, double[] res) {
        int n = x.length;
        for (int i = 0; i < n; i++) {
            res[i] = x[i] + a * y[i];
        }
    }

    /**
     * Standard Inner Product (Dot Product).
     * Ловим catastrophic cancellation на огромных векторах (флоаты плачут),
     * но голый луп тут ради перфа. Алгоритм Кэхена оставим для параноиков.
     * used if needed.
     */
    public static double dot(double[] x, double[] y) {
        int n = x.length;
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += x[i] * y[i];
        }
        return sum;
    }

    public static double normL2(double[] x) {
        return Math.sqrt(dot(x, x));
    }

    public static double normLinf(double[] x) {
        double maxDiff = 0.0;
        int n = x.length;
        for (int i = 0; i < n; i++) {
            maxDiff = Math.max(maxDiff, Math.abs(x[i]));
        }
        return maxDiff;
    }
}
