package pdelab.solver;

import pdelab.core.Grid2D;
import java.util.Arrays;

/**
 * Symmetric Successive Over-Relaxation (SSOR) прекондей для буста нашего PCG
 * solver.
 * Applies one forward and one backward SOR sweep implicitly using the 5-point
 * stencil.
 */
public class SSORPreconditioner implements Preconditioner {
    private final Grid2D grid;
    private double factor;
    private final double omega;
    private final double[] kFull;
    private final double[] invDiagArray;

    public SSORPreconditioner(Grid2D grid, double factor, double omega) {
        this(grid, factor, omega, null);
    }

    public SSORPreconditioner(Grid2D grid, double factor, double omega, double[] kFull) {
        this.grid = grid;
        this.omega = omega;
        this.kFull = kFull;

        int nInt = grid.numInterior();
        this.invDiagArray = new double[nInt];

        updateFactor(factor);
    }

    @Override
    public void updateFactor(double factor) {
        this.factor = factor;
        int inX = grid.inX();
        int inY = grid.inY();
        double ihx2 = grid.ihx2();
        double ihy2 = grid.ihy2();

        if (kFull == null) {
            double diagA = 1.0 + factor * 2.0 * (ihx2 + ihy2);
            Arrays.fill(invDiagArray, 1.0 / diagA);
        } else {
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
        int inX = grid.inX();
        int inY = grid.inY();
        double ihx2 = grid.ihx2();
        double ihy2 = grid.ihy2();

        // z обнулен чисто для старта M^-1
        Arrays.fill(z, 0.0);

        // Forward Sweep
        for (int j = 0; j < inY; j++) {
            int globalJ = j + 1;
            int intOffset = j * inX;
            for (int i = 0; i < inX; i++) {
                int globalI = i + 1;
                int intIdx = intOffset + i;

                double leftZ = (i == 0) ? 0.0 : z[intIdx - 1];
                double downZ = (j == 0) ? 0.0 : z[intIdx - inX];

                double L_val = 0.0;
                if (kFull == null) {
                    L_val = factor * (leftZ * ihx2 + downZ * ihy2);
                } else {
                    double kCenter = kFull[grid.idx(globalI, globalJ)];
                    double kLeft = kFull[grid.idx(globalI - 1, globalJ)];
                    double kDown = kFull[grid.idx(globalI, globalJ - 1)];

                    double k_i_minus_half = 0.5 * (kCenter + kLeft);
                    double k_j_minus_half = 0.5 * (kCenter + kDown);

                    L_val = factor * (k_i_minus_half * leftZ * ihx2 + k_j_minus_half * downZ * ihy2);
                }

                // z_i = z_i + omega * (r_i - A_ii z_i + L_val + U_val) / A_ii
                // раз уж z_i пока по нулям, U_val тут обнуляется (оптимизируем луп):
                z[intIdx] = omega * invDiagArray[intIdx] * (r[intIdx] + L_val);
            }
        }

        // Backward Sweep
        for (int j = inY - 1; j >= 0; j--) {
            int globalJ = j + 1;
            int intOffset = j * inX;
            for (int i = inX - 1; i >= 0; i--) {
                int globalI = i + 1;
                int intIdx = intOffset + i;

                double rightZ = (i == inX - 1) ? 0.0 : z[intIdx + 1];
                double upZ = (j == inY - 1) ? 0.0 : z[intIdx + inX];
                double leftZ = (i == 0) ? 0.0 : z[intIdx - 1];
                double downZ = (j == 0) ? 0.0 : z[intIdx - inX];

                double L_val = 0.0;
                double U_val = 0.0;

                if (kFull == null) {
                    L_val = factor * (leftZ * ihx2 + downZ * ihy2);
                    U_val = factor * (rightZ * ihx2 + upZ * ihy2);
                } else {
                    double globalIdx = grid.idx(globalI, globalJ);
                    double kCenter = kFull[(int) globalIdx];
                    double kLeft = kFull[grid.idx(globalI - 1, globalJ)];
                    double kRight = kFull[grid.idx(globalI + 1, globalJ)];
                    double kDown = kFull[grid.idx(globalI, globalJ - 1)];
                    double kUp = kFull[grid.idx(globalI, globalJ + 1)];

                    double k_i_minus_half = 0.5 * (kCenter + kLeft);
                    double k_j_minus_half = 0.5 * (kCenter + kDown);
                    double k_i_plus_half = 0.5 * (kCenter + kRight);
                    double k_j_plus_half = 0.5 * (kCenter + kUp);

                    L_val = factor * (k_i_minus_half * leftZ * ihx2 + k_j_minus_half * downZ * ihy2);
                    U_val = factor * (k_i_plus_half * rightZ * ihx2 + k_j_plus_half * upZ * ihy2);
                }

                double a_ii = 1.0 / invDiagArray[intIdx];
                double res = r[intIdx] + L_val + U_val - a_ii * z[intIdx];

                z[intIdx] = z[intIdx] + omega * invDiagArray[intIdx] * res;
            }
        }
    }
}
