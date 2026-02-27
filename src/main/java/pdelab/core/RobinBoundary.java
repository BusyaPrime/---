package pdelab.core;

public class RobinBoundary implements BoundaryOperator {
    private final double a, b;
    private final double gLeft, gRight, gBottom, gTop;

    public RobinBoundary(double a, double b, double gLeft, double gRight, double gBottom, double gTop) {
        this.a = a;
        this.b = b;
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

        if (Math.abs(a) < 1e-14 && Math.abs(b) < 1e-14)
            return;

        double coeffXLeft = a - b / hx;
        double coeffYBottom = a - b / hy;

        for (int i = 0; i < nx; i++) {
            uFull[grid.idx(i, 0)] = (gBottom + uFull[grid.idx(i, 1)] * (b / hy)) / coeffYBottom;
            uFull[grid.idx(i, ny - 1)] = (gTop + uFull[grid.idx(i, ny - 2)] * (b / hy)) / (a + b / hy);
        }

        for (int j = 1; j < ny - 1; j++) {
            uFull[grid.idx(0, j)] = (gLeft + uFull[grid.idx(1, j)] * (b / hx)) / coeffXLeft;
            uFull[grid.idx(nx - 1, j)] = (gRight + uFull[grid.idx(nx - 2, j)] * (b / hx)) / (a + b / hx);
        }
    }
}
