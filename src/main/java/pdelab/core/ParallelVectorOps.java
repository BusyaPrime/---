package pdelab.core;

/**
 * Параллельные навороты для VectorOps (выжимаем ядра).
 */
public class ParallelVectorOps {

    private static class CopyOp implements ParallelExecutor.ArrayOp {
        double[] src, dst;

        public void set(double[] src, double[] dst) {
            this.src = src;
            this.dst = dst;
        }

        @Override
        public void compute(int start, int end) {
            System.arraycopy(src, start, dst, start, end - start);
        }
    }

    private static final CopyOp copyOp = new CopyOp();

    public static void copy(double[] src, double[] dst) {
        copyOp.set(src, dst);
        ParallelExecutor.executeContiguous(src.length, copyOp);
    }

    private static class AxpyOp implements ParallelExecutor.ArrayOp {
        double a;
        double[] x, y;

        public void set(double a, double[] x, double[] y) {
            this.a = a;
            this.x = x;
            this.y = y;
        }

        @Override
        public void compute(int start, int end) {
            for (int i = start; i < end; i++)
                y[i] += a * x[i];
        }
    }

    private static final AxpyOp axpyOp = new AxpyOp();

    public static void axpy(double a, double[] x, double[] y) {
        axpyOp.set(a, x, y);
        ParallelExecutor.executeContiguous(x.length, axpyOp);
    }

    private static class AxpbyOp implements ParallelExecutor.ArrayOp {
        double a, b;
        double[] x, y;

        public void set(double a, double[] x, double b, double[] y) {
            this.a = a;
            this.x = x;
            this.b = b;
            this.y = y;
        }

        @Override
        public void compute(int start, int end) {
            for (int i = start; i < end; i++)
                y[i] = a * x[i] + b * y[i];
        }
    }

    private static final AxpbyOp axpbyOp = new AxpbyOp();

    public static void axpby(double a, double[] x, double b, double[] y) {
        axpbyOp.set(a, x, b, y);
        ParallelExecutor.executeContiguous(x.length, axpbyOp);
    }

    private static class AddScaledOp implements ParallelExecutor.ArrayOp {
        double a;
        double[] x, y, res;

        public void set(double[] x, double a, double[] y, double[] res) {
            this.x = x;
            this.a = a;
            this.y = y;
            this.res = res;
        }

        @Override
        public void compute(int start, int end) {
            for (int i = start; i < end; i++)
                res[i] = x[i] + a * y[i];
        }
    }

    private static final AddScaledOp addScaledOp = new AddScaledOp();

    public static void addScaled(double[] x, double a, double[] y, double[] res) {
        addScaledOp.set(x, a, y, res);
        ParallelExecutor.executeContiguous(x.length, addScaledOp);
    }

    private static class DotOp implements ParallelExecutor.ReduceOp {
        double[] x, y;

        public void set(double[] x, double[] y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public double compute(int start, int end) {
            double sum = 0.0;
            for (int i = start; i < end; i++)
                sum += x[i] * y[i];
            return sum;
        }
    }

    private static final DotOp dotOp = new DotOp();

    public static double dot(double[] x, double[] y) {
        dotOp.set(x, y);
        return ParallelExecutor.reduceContiguous(x.length, dotOp);
    }

    public static double normL2(double[] x) {
        return Math.sqrt(dot(x, x));
    }
}
