package pdelab.core;

public class DirichletBoundary implements BoundaryOperator {
    private final MMS mms;

    public DirichletBoundary(MMS mms) {
        this.mms = mms;
    }

    @Override
    public void apply(Grid2D grid, double[] uFull, double t) {
        int nx = grid.Nx();
        int ny = grid.Ny();
        double hx = grid.hx();
        double hy = grid.hy();

        // Дно (j=0) и Крыша (j=ny-1)
        for (int i = 0; i < nx; i++) {
            double x = i * hx;
            uFull[grid.idx(i, 0)] = mms.exactSolution(x, 0.0, t);
            uFull[grid.idx(i, ny - 1)] = mms.exactSolution(x, (ny - 1) * hy, t);
        }

        // Левая стена (i=0) и Правая стена (i=nx-1), скипаем углы
        for (int j = 1; j < ny - 1; j++) {
            double y = j * hy;
            uFull[grid.idx(0, j)] = mms.exactSolution(0.0, y, t);
            uFull[grid.idx(nx - 1, j)] = mms.exactSolution((nx - 1) * hx, y, t);
        }
    }
}
