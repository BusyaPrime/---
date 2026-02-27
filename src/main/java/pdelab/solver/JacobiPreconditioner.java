package pdelab.solver;

import pdelab.core.Grid2D;

public class JacobiPreconditioner implements Preconditioner {
    private double invDiag;
    private final double[] invDiagArray;
    private Grid2D grid;
    private double[] kFull;

    public JacobiPreconditioner(Grid2D grid, double factor) {
        this(grid, factor, null);
    }

    public JacobiPreconditioner(Grid2D grid, double factor, double[] kFull) {
        this.grid = grid;
        this.kFull = kFull;
        if (kFull == null) {
            this.invDiagArray = null;
        } else {
            int nInt = grid.numInterior();
            this.invDiagArray = new double[nInt];
        }
        updateFactor(factor);
    }

    @Override
    public void updateFactor(double factor) {
        if (kFull == null) {
            double diagA = 1.0 + factor * 2.0 * (grid.ihx2() + grid.ihy2());
            this.invDiag = 1.0 / diagA;
        } else {
            this.invDiag = 0.0;
            int inX = grid.inX();
            int inY = grid.inY();
            double ihx2 = grid.ihx2();
            double ihy2 = grid.ihy2();

            for (int j = 0; j < inY; j++) {
                int globalJ = j + 1;
                for (int i = 0; i < inX; i++) {
                    int globalI = i + 1;
                    int globalIdx = grid.idx(globalI, globalJ);
                    int intIdx = j * inX + i;

                    double kCenter = kFull[globalIdx];
                    double kLeft = kFull[grid.idx(globalI - 1, globalJ)];
                    double kRight = kFull[grid.idx(globalI + 1, globalJ)];
                    double kDown = kFull[grid.idx(globalI, globalJ - 1)];
                    double kUp = kFull[grid.idx(globalI, globalJ + 1)];

                    double k_i_plus_half = 0.5 * (kCenter + kRight);
                    double k_i_minus_half = 0.5 * (kCenter + kLeft);
                    double k_j_plus_half = 0.5 * (kCenter + kUp);
                    double k_j_minus_half = 0.5 * (kCenter + kDown);

                    double diagL = -(k_i_plus_half + k_i_minus_half) * ihx2 - (k_j_plus_half + k_j_minus_half) * ihy2;
                    double diagA = 1.0 - factor * diagL;
                    invDiagArray[intIdx] = 1.0 / diagA;
                }
            }
        }
    }

    @Override
    public void apply(double[] r, double[] z) {
        int n = r.length;
        if (invDiagArray == null) {
            for (int i = 0; i < n; i++) {
                z[i] = r[i] * invDiag;
            }
        } else {
            for (int i = 0; i < n; i++) {
                z[i] = r[i] * invDiagArray[i];
            }
        }
    }
}
