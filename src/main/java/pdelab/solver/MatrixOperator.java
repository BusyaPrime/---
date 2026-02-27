package pdelab.solver;

public interface MatrixOperator {
    /**
     * Прогоняем линейный оператор A по вектору x, выплевываем в y.
     * y = A * x
     */
    void multiply(double[] x, double[] y);
}
