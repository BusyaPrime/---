package pdelab.benchmarks;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;
import pdelab.core.Grid2D;
import pdelab.core.Stencil;
import pdelab.core.VectorOps;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CoreKernelsBenchmark {

    @Param({ "256", "512" })
    int N;

    private Grid2D grid;
    private double[] uFull;
    private double[] uInt;
    private double[] LuInt;
    private double[] vInt;

    @Setup(Level.Trial)
    public void setup() {
        grid = new Grid2D(N, N, 1.0, 1.0);
        uFull = new double[grid.size()];
        uInt = new double[grid.numInterior()];
        LuInt = new double[grid.numInterior()];
        vInt = new double[grid.numInterior()];

        java.util.Random rnd = new java.util.Random(42);
        for (int i = 0; i < uFull.length; i++)
            uFull[i] = rnd.nextDouble();
        for (int i = 0; i < uInt.length; i++) {
            uInt[i] = rnd.nextDouble();
            vInt[i] = rnd.nextDouble();
        }
    }

    @Benchmark
    public void benchStencil() {
        Stencil.applyLaplacianInterior(grid, uInt, uFull, LuInt);
    }

    @Benchmark
    public double benchDotProduct() {
        return VectorOps.dot(uInt, vInt);
    }

    @Benchmark
    public void benchAxpy() {
        VectorOps.axpy(0.5, uInt, vInt);
    }
}
