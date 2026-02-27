package pdelab.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class VectorOpsTest {

    @Test
    public void testDotProduct() {
        double[] a = { 1.0, 2.0, 3.0 };
        double[] b = { 4.0, 5.0, 6.0 };

        double dot = ParallelVectorOps.dot(a, b);
        assertEquals(1 * 4 + 2 * 5 + 3 * 6, dot, 1e-12);
    }

    @Test
    public void testAxpy() {
        double[] x = { 1.0, 2.0, 3.0 };
        double[] y = { 10.0, 20.0, 30.0 };

        ParallelVectorOps.axpy(2.0, x, y); // y = 2*x + y

        assertEquals(12.0, y[0], 1e-12);
        assertEquals(24.0, y[1], 1e-12);
        assertEquals(36.0, y[2], 1e-12);
    }

    @Test
    public void testNormL2() {
        double[] x = { 3.0, 4.0 };
        double norm = ParallelVectorOps.normL2(x);
        assertEquals(5.0, norm, 1e-12);
    }

    @Test
    public void testZeroNorm() {
        double[] x = { 0.0, 0.0, 0.0 };
        double norm = ParallelVectorOps.normL2(x);
        assertEquals(0.0, norm, 1e-12);
    }

    @Test
    public void testNaNsPropagate() {
        double[] x = { 1.0, Double.NaN, 3.0 };
        double norm = ParallelVectorOps.normL2(x);
        assertTrue(Double.isNaN(norm));
    }
}
