package pdelab.runtime;

public record Config(
                int Nx,
                int Ny,
                double Lx,
                double Ly,
                double alpha,
                double T,
                double dt,
                String scheme,
                int maxIters,
                double tol,
                int threads,
                String outDir,
                String testCase,
                String preconditioner,
                String kappaAveraging) {

        /**
         * Enforces strict validation rules over configuration parameters.
         * Throws IllegalArgumentException on failure.
         */
        public void validate() {
                if (Nx < 3 || Ny < 3)
                        throw new IllegalArgumentException(
                                        "Сетка Nx, Ny обязана быть >= 3, иначе внутри пусто");
                if (Lx <= 0.0 || Ly <= 0.0)
                        throw new IllegalArgumentException("Физические размеры Lx, Ly должны быть строго > 0.0");
                if (alpha <= 0.0)
                        throw new IllegalArgumentException("Коэффициент диффузии alpha должен быть строго > 0.0");
                if (T <= 0.0)
                        throw new IllegalArgumentException("Время симуляции T должно быть строго > 0.0");
                if (dt <= 0.0 || dt > T)
                        throw new IllegalArgumentException("Шаг dt должен быть строго > 0.0 и <= T");
                if (maxIters <= 0)
                        throw new IllegalArgumentException("maxIters вообще-то должен быть > 0");
                if (tol <= 0.0 || Double.isNaN(tol))
                        throw new IllegalArgumentException("Толерантность (Tolerance) обязана быть строго больше нуля");
                if (threads < 0)
                        throw new IllegalArgumentException("Количество потоков не может быть отрицательным. Ставь 0 для автодетекта ядер.");

                if (!scheme.equals("CN") && !scheme.equals("BE")) {
                        throw new IllegalArgumentException("Unsupported scheme: " + scheme + ". Expected 'CN' or 'BE'");
                }
                if (!testCase.equals("HOMOGENEOUS") && !testCase.equals("NON_TRIVIAL")
                                && !testCase.equals("NON_ZERO_DIRICHLET") && !testCase.equals("VARIABLE_KAPPA")) {
                        throw new IllegalArgumentException("Unsupported testCase: " + testCase);
                }
                if (preconditioner != null && !preconditioner.equals("JACOBI") && !preconditioner.equals("SSOR")
                                && !preconditioner.equals("MG")) {
                        throw new IllegalArgumentException("Unsupported preconditioner: " + preconditioner
                                        + ". Expected 'JACOBI', 'SSOR', or 'MG'");
                }
                if (kappaAveraging != null && !kappaAveraging.equals("ARITHMETIC")
                                && !kappaAveraging.equals("HARMONIC")) {
                        throw new IllegalArgumentException("Unsupported kappaAveraging: " + kappaAveraging
                                        + ". Expected 'ARITHMETIC' or 'HARMONIC'");
                }
        }
}
