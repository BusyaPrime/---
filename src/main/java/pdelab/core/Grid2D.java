package pdelab.core;

/**
 * Описывает 2D физический домен и параметры грида дискретизации.
 * Легковесный рекорд чисто под метадату (без жира).
 * Жестко НЕ СОДЕРЖИТ массивы данных (double[]), бережем кэш-линию!
 * predictability
 * отделяем мух (мету) от котлет (хардкорных лупов).
 */
public record Grid2D(
        int Nx,
        int Ny,
        double Lx,
        double Ly,
        double hx,
        double hy,
        double ihx2, // 1 / (hx*hx) (Отработает только для uniform-сетки)
        double ihy2, // 1 / (hy*hy) (Отработает только для uniform-сетки)
        double[] x, // Local x-coordinates
        double[] y // Local y-coordinates
) {
    public Grid2D(int Nx, int Ny, double Lx, double Ly) {
        this(Nx, Ny, Lx, Ly,
                Lx / (Nx - 1),
                Ly / (Ny - 1),
                1.0 / ((Lx / (Nx - 1)) * (Lx / (Nx - 1))),
                1.0 / ((Ly / (Ny - 1)) * (Ly / (Ny - 1))),
                generateUniform(Nx, Lx / (Nx - 1)),
                generateUniform(Ny, Ly / (Ny - 1)));
    }

    private static double[] generateUniform(int N, double h) {
        double[] coords = new double[N];
        for (int i = 0; i < N; i++) {
            coords[i] = i * h;
        }
        return coords;
    }

    /**
     * Число внутренних точек по иксу (вдоль X).
     */
    public int inX() {
        return Nx - 2;
    }

    /**
     * Число внутренних точек по игреку (вдоль Y).
     */
    public int inY() {
        return Ny - 2;
    }

    /**
     * Суммарно внутренних точек в гриде (сайз лин. системы).
     */
    public int numInterior() {
        return inX() * inY();
    }

    /**
     * Маппинг 2D (x,y) индекса грида в 1D плоский массив (ФУЛЛ грид).
     */
    public int idx(int i, int j) {
        return j * Nx + i;
    }

    /**
     * Маппинг 2D (x,y) глобал индекса в 1D для внутреннего грида.
     * INTERIOR grid.
     * Свято верим, что i в [1, Nx-2] и j в [1, Ny-2].
     */
    public int idxInterior(int i, int j) {
        return (j - 1) * inX() + (i - 1);
    }

    /**
     * Полный сайз ФУЛЛ грида (со всеми краями).
     */
    public int size() {
        return Nx * Ny;
    }

    /**
     * Парсим внутренности из фулл грида в интериор массив.
     */
    public void extractInterior(double[] full, double[] interior) {
        int inX = inX();
        int inY = inY();
        for (int j = 1; j <= inY; j++) {
            for (int i = 1; i <= inX; i++) {
                interior[idxInterior(i, j)] = full[idx(i, j)];
            }
        }
    }

    /**
     * Инжектим внутренний массив обратно в фулл грид.
     * Leaves boundaries in 'full' untouched.
     */
    public void injectInterior(double[] interior, double[] full) {
        int inX = inX();
        int inY = inY();
        for (int j = 1; j <= inY; j++) {
            for (int i = 1; i <= inX; i++) {
                full[idx(i, j)] = interior[idxInterior(i, j)];
            }
        }
    }
}
