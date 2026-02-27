package pdelab.solver;

public interface Preconditioner {
    /**
     * Solves M z = r => z = M^-1 r
     */
    void apply(double[] r, double[] z);

    /**
     * Updates the internal operators for a new time step size (factor).
     * 
     * @param factor The new integration factor
     */
    void updateFactor(double factor);
}
