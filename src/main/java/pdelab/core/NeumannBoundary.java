package pdelab.core;

public class NeumannBoundary implements BoundaryOperator {
    private final double gLeft, gRight, gBottom, gTop;

    public NeumannBoundary(double gLeft, double gRight, double gBottom, double gTop) {
        this.gLeft = gLeft;
        this.gRight = gRight;
        this.gBottom = gBottom;
        this.gTop = gTop;
    }

    @Override
    public void apply(Grid2D grid, double[] uFull, double t) {
        int nx = grid.Nx();
        int ny = grid.Ny();
        double hx = grid.hx();
        double hy = grid.hy();

        for (int i = 0; i < nx; i++) {
            uFull[grid.idx(i, 0)] = uFull[grid.idx(i, 1)] - hy * gBottom;
            uFull[grid.idx(i, ny - 1)] = uFull[grid.idx(i, ny - 2)] + hy * gTop;
        }

        for (int j = 1; j < ny - 1; j++) {
            uFull[grid.idx(0, j)] = uFull[grid.idx(1, j)] - hx * gLeft;
            uFull[grid.idx(nx - 1, j)] = uFull[grid.idx(nx - 2, j)] + hx * gRight;
        }
    }
}
