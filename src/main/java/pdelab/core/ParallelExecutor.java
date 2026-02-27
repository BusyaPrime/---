package pdelab.core;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.BrokenBarrierException;

/**
 * Parallel execution engine strictly avoiding object allocations during tight
 * mathematical loops.
 * Uses a fixed pool of long-lived threads synchronized via a master
 * CyclicBarrier.
 */
public class ParallelExecutor {

    private static int threads = Runtime.getRuntime().availableProcessors();
    private static WorkerThread[] workers;
    private static CyclicBarrier barrier;

    private static volatile ArrayOp currentArrayOp;
    private static volatile ReduceOp currentReduceOp;
    private static volatile int globalLength;
    private static volatile boolean terminateWorkers = false;

    // Thred-local куски сумм. Набили паддингом до 64 байт шоб не ловить false-sharing.
    // sharing.
    private static final int CACHE_LINE_PADDING = 8;
    private static double[] partialSums;

    private static final int MIN_CHUNK_SIZE = 8192; // Prevent thread-thrashing on small arrays

    private static int getOptimalChunkSize(int totalLength) {
        int targetChunks = threads * 4;
        int chunk = Math.max(1, (int) Math.ceil((double) totalLength / targetChunks));
        return Math.max(chunk, MIN_CHUNK_SIZE);
    }

    public static synchronized void init(int numThreads) {
        if (workers != null) {
            terminateWorkers = true;
            if (barrier != null) {
                barrier.reset(); // Trigger BrokenBarrierException in waiting workers
            }
            for (WorkerThread w : workers) {
                if (w != null) {
                    w.interrupt();
                    try {
                        w.join(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        terminateWorkers = false;
        threads = numThreads;
        barrier = new CyclicBarrier(threads + 1); // +1 поток под мэйн-тред (дирижер).
        workers = new WorkerThread[threads];
        partialSums = new double[threads * CACHE_LINE_PADDING];

        for (int i = 0; i < threads; i++) {
            workers[i] = new WorkerThread(i);
            workers[i].start();
        }
    }

    private static void ensureInit() {
        if (workers == null || terminateWorkers) {
            init(Runtime.getRuntime().availableProcessors());
        }
    }

    @FunctionalInterface
    public interface ArrayOp {
        void compute(int start, int end);
    }

    @FunctionalInterface
    public interface ReduceOp {
        double compute(int start, int end);
    }

    public static void executeContiguous(int length, ArrayOp op) {
        ensureInit();
        if (length <= MIN_CHUNK_SIZE || threads == 1) {
            op.compute(0, length);
            return;
        }

        currentArrayOp = op;
        currentReduceOp = null;
        globalLength = length;

        try {
            barrier.await(); // Пинок воркерам: алга!
            barrier.await(); // Ждем пока воркеры дожуют таски
        } catch (InterruptedException | BrokenBarrierException e) {
            throw new RuntimeException("Parallel execute interrupted", e);
        }
    }

    public static double reduceContiguous(int length, ReduceOp op) {
        ensureInit();
        if (length <= MIN_CHUNK_SIZE || threads == 1) {
            return op.compute(0, length);
        }

        currentReduceOp = op;
        currentArrayOp = null;
        globalLength = length;

        try {
            barrier.await(); // Пинок воркерам: алга!
            barrier.await(); // Ждем пока воркеры дожуют таски
        } catch (InterruptedException | BrokenBarrierException e) {
            throw new RuntimeException("Parallel execute interrupted", e);
        }

        double total = 0.0;
        for (int i = 0; i < threads; i++) {
            total += partialSums[i * CACHE_LINE_PADDING];
        }
        return total;
    }

    private static class WorkerThread extends Thread {
        private final int id;

        public WorkerThread(int id) {
            super("PDE-Worker-" + id);
            this.id = id;
            this.setDaemon(true);
        }

        @Override
        public void run() {
            while (!terminateWorkers) {
                try {
                    barrier.await(); // Воркер скучает, ждет таску...
                    if (terminateWorkers)
                        break;

                    int length = globalLength;
                    int chunkSize = getOptimalChunkSize(length);

                    // Паттерн динамического чанкинга (рвём цикл без лишних объектов, zero-allocation).
                    int currentIndex = id * chunkSize;
                    double localSum = 0.0;

                    ArrayOp opArray = currentArrayOp;
                    ReduceOp opReduce = currentReduceOp;

                    while (currentIndex < length) {
                        int end = Math.min(currentIndex + chunkSize, length);

                        if (opArray != null) {
                            opArray.compute(currentIndex, end);
                        } else if (opReduce != null) {
                            localSum += opReduce.compute(currentIndex, end);
                        }

                        currentIndex += threads * chunkSize;
                    }

                    if (opReduce != null) {
                        partialSums[id * CACHE_LINE_PADDING] = localSum;
                    }

                    barrier.await(); // Signal completion
                } catch (InterruptedException | BrokenBarrierException e) {
                    if (terminateWorkers)
                        break;
                }
            }
        }
    }
}
