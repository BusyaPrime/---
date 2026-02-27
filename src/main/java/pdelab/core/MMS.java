package pdelab.core;

/**
 * Фреймворк MMS (Method of Manufactured Solutions) чисто под верификацию матана.
 */
public class MMS {
    public enum TestCase {
        HOMOGENEOUS,
        NON_ZERO_DIRICHLET,
        VARIABLE_KAPPA,
        CONVECTION,
        ROTATING_HUMP,
        ANISOTROPIC_DIFFUSION
    }

    private final TestCase testCase;
    private final double alpha;

    public MMS(TestCase testCase, double alpha) {
        this.testCase = testCase;
        this.alpha = alpha;
    }

    public double getKappa(double x, double y) {
        if (testCase == TestCase.VARIABLE_KAPPA) {
            return 1.0 + 0.1 * Math.sin(2.0 * Math.PI * x) * Math.sin(2.0 * Math.PI * y);
        }
        return 1.0;
    }

    public double exactSolution(double x, double y, double t) {
        if (testCase == TestCase.HOMOGENEOUS || testCase == TestCase.VARIABLE_KAPPA
                || testCase == TestCase.CONVECTION || testCase == TestCase.ANISOTROPIC_DIFFUSION) {
            // u(x,y,t) = sin(PI x) sin(PI y) exp(-t)
            return Math.sin(Math.PI * x) * Math.sin(Math.PI * y) * Math.exp(-t);
        } else if (testCase == TestCase.ROTATING_HUMP) {
            // Вращаем гауссиану: крутим изначальное условие по потоку (rotating hump - демка конвекции).
            // Note: Тут MMS exactSolution чисто для затравочки (initial condition).
            // (t=0)
            // or an analytical proxy.
            double cx = 0.5;
            double cy = 0.5;
            double r2 = (x - cx) * (x - cx) + (y - cy) * (y - cy);
            return Math.exp(-100.0 * r2) * Math.exp(-t);
        } else {
            // NON_ZERO_DIRICHLET: u(x,y,t) = (1+x)(1+y) exp(-t)
            return (1.0 + x) * (1.0 + y) * Math.exp(-t);
        }
    }

    public double exactForcing(double x, double y, double t) {
        if (testCase == TestCase.HOMOGENEOUS || testCase == TestCase.ANISOTROPIC_DIFFUSION
                || testCase == TestCase.ROTATING_HUMP) {
            // u = sin(px)sin(py)e^{-t}
            // u_t = -u
            // L u = -2 PI^2 u
            // f = u_t - alpha L u = -u + 2 alpha PI^2 u
            double u = exactSolution(x, y, t);
            return -u + 2.0 * alpha * Math.PI * Math.PI * u;

        } else if (testCase == TestCase.NON_ZERO_DIRICHLET) {
            // u = (1+x)(1+y) e^{-t}
            // u_t = -u
            // L u = 0 (since d2x(1+x)=0)
            // f = -u
            return -exactSolution(x, y, t);

        } else if (testCase == TestCase.VARIABLE_KAPPA) {
            double u = exactSolution(x, y, t);
            double ut = -u;

            // k = 1 + 0.1 sin(2pix) sin(2piy)
            double k = getKappa(x, y);
            double lapu = -2.0 * Math.PI * Math.PI * u;

            // grad k = [0.2 pi cos(2pix) sin(2piy), 0.2 pi sin(2pix) cos(2piy)]
            double kx = 0.2 * Math.PI * Math.cos(2.0 * Math.PI * x) * Math.sin(2.0 * Math.PI * y);
            double ky = 0.2 * Math.PI * Math.sin(2.0 * Math.PI * x) * Math.cos(2.0 * Math.PI * y);

            // grad u = [pi cos(pix) sin(piy), pi sin(pix) cos(piy)] e^{-t}
            double ux = Math.PI * Math.cos(Math.PI * x) * Math.sin(Math.PI * y) * Math.exp(-t);
            double uy = Math.PI * Math.sin(Math.PI * x) * Math.cos(Math.PI * y) * Math.exp(-t);

            double div_k_grad_u = k * lapu + kx * ux + ky * uy;

            return ut - alpha * div_k_grad_u;

        } else { // CONVECTION
            // PDE: u_t = alpha L u - b \cdot \nabla u + f
            // Vector field b = [1.0, 1.0].
            double u = exactSolution(x, y, t);
            double ut = -u;
            double lapu = -2.0 * Math.PI * Math.PI * u;

            double ux = Math.PI * Math.cos(Math.PI * x) * Math.sin(Math.PI * y) * Math.exp(-t);
            double uy = Math.PI * Math.sin(Math.PI * x) * Math.cos(Math.PI * y) * Math.exp(-t);

            // b \cdot \nabla u = 1.0 * ux + 1.0 * uy
            double b_dot_grad_u = ux + uy;

            // f = u_t - alpha L u + b \cdot \nabla u
            return ut - alpha * lapu + b_dot_grad_u;

        }
    }

    public void evaluateForcing(Grid2D grid, double t, double[] f) {
        int nx = grid.Nx();
        int ny = grid.Ny();
        double hx = grid.hx();
        double hy = grid.hy();

        for (int j = 0; j < ny; j++) {
            double y = j * hy;
            for (int i = 0; i < nx; i++) {
                double x = i * hx;
                f[grid.idx(i, j)] = exactForcing(x, y, t);
            }
        }
    }

    public void evaluateExact(Grid2D grid, double t, double[] uExact) {
        int nx = grid.Nx();
        int ny = grid.Ny();
        double hx = grid.hx();
        double hy = grid.hy();

        for (int j = 0; j < ny; j++) {
            double y = j * hy;
            for (int i = 0; i < nx; i++) {
                double x = i * hx;
                uExact[grid.idx(i, j)] = exactSolution(x, y, t);
            }
        }
    }

    public void evaluateKappa(Grid2D grid, double[] kFull) {
        int nx = grid.Nx();
        int ny = grid.Ny();
        double hx = grid.hx();
        double hy = grid.hy();

        for (int j = 0; j < ny; j++) {
            double y = j * hy;
            for (int i = 0; i < nx; i++) {
                double x = i * hx;
                kFull[grid.idx(i, j)] = getKappa(x, y);
            }
        }
    }
}
