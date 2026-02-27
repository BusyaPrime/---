package pdelab.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ParallelExecutorTest {

    @Test
    public void testContiguousExecution() {
        ParallelExecutor.init(4);
        int[] arr = new int[100];

        ParallelExecutor.executeContiguous(100, (start, end) -> {
            for (int i = start; i < end; i++) {
                arr[i] = i;
            }
        });

        for (int i = 0; i < 100; i++) {
            assertEquals(i, arr[i]);
        }
    }

    @Test
    public void testReductionContiguous() {
        ParallelExecutor.init(4);
        double result = ParallelExecutor.reduceContiguous(100, (start, end) -> {
            double sum = 0;
            for (int i = start; i < end; i++)
                sum += 1.0;
            return sum;
        });
        assertEquals(100.0, result, 1e-9);
    }
}
