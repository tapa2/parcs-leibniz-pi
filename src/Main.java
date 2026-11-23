import parcs.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * PARCS root module: computes pi with the Leibniz series
 *
 *     pi = 4 * sum_{k=0}^{N-1} (-1)^k / (2k + 1)
 *
 * Input file "input":
 *     line 1: N - number of series terms
 *     line 2: worker counts to test, e.g. "1 2 3 6"
 *     line 3: how many times to repeat every measurement (best time is used)
 *
 * Steps:
 *   1. For every worker count p: split [0, N) into p equal ranges,
 *      send one range to each worker, add up the partial sums.
 *   2. Print a table: best time, speedup T1/Tp, efficiency Sp/p, where
 *      T1 is the time with 1 worker (the same PARCS program on one daemon).
 *   3. Check the result against Math.PI.
 */
public class Main {

    /** Number of terms summed by one call of blockSum. */
    static final long BLOCK = 100_000L;

    /** Result of the measurements for one worker count. */
    record Result(int p, double best, double pi) {}

    /**
     * Partial sum of the Leibniz series for term indices [from, to).
     *
     * The range is processed in blocks of BLOCK terms. Reason: the JVM compiles
     * a method into fast machine code after it has been called many times.
     * One huge loop is called only once, so the JVM has to optimise it in the
     * middle of the loop (OSR), and that code was randomly ~4x slower.
     * With blocks, blockSum is called thousands of times and is always fast.
     */
    public static double partialSum(long from, long to) {
        double sum = 0.0;
        for (long start = from; start < to; start += BLOCK) {
            sum += blockSum(start, Math.min(start + BLOCK, to));
        }
        return sum;
    }

    /** Plain loop over the terms [from, to). */
    static double blockSum(long from, long to) {
        double sum = 0.0;
        for (long k = from; k < to; k++) {
            double term = 1.0 / (2.0 * k + 1.0);
            sum += (k % 2 == 0) ? term : -term;
        }
        return sum;
    }

    /** Runs the computation on p workers and returns the value of pi. */
    static double parallelPi(AMInfo info, long n, int p) {
        List<channel> channels = new ArrayList<>();
        long chunk = n / p;

        for (int i = 0; i < p; i++) {
            long from = i * chunk;
            long to = (i == p - 1) ? n : from + chunk; // last worker takes the remainder

            point pt = info.createPoint();
            channel c = pt.createChannel();
            pt.execute("SeriesWorker");
            c.write(from);
            c.write(to);
            channels.add(c);
        }

        double total = 0.0;
        for (channel c : channels) {
            total += c.readDouble();
        }
        return 4.0 * total;
    }

    public static void main(String[] args) throws Exception {
        task curtask = new task();
        curtask.addJarFile("SeriesWorker.jar");

        // 1. Read input
        Scanner in = new Scanner(new File(curtask.findFile("input")));
        long n = Long.parseLong(in.nextLine().trim());
        List<Integer> workerCounts = new ArrayList<>();
        for (String s : in.nextLine().trim().split("\\s+")) {
            workerCounts.add(Integer.parseInt(s));
        }
        int repeats = in.hasNextLine() ? Integer.parseInt(in.nextLine().trim()) : 1;
        in.close();
        if (!workerCounts.contains(1)) {
            workerCounts.add(0, 1); // 1 worker is the base for the speedup
        }

        System.out.println("N = " + n + ", repeats = " + repeats);

        // 2. Parallel runs.
        // The BEST (minimal) time of all repeats is used: it is the least
        // affected by outside noise (other VMs on the same host).
        AMInfo info = new AMInfo(curtask, null);
        List<Result> results = new ArrayList<>();

        for (int p : workerCounts) {
            double pi = 0.0;
            double best = Double.MAX_VALUE;
            for (int r = 0; r < repeats; r++) {
                long start = System.nanoTime();
                pi = parallelPi(info, n, p);
                double t = (System.nanoTime() - start) / 1e9;
                System.out.printf("  %d workers, run %d: %.3f s%n", p, r + 1, t);
                best = Math.min(best, t);
            }
            results.add(new Result(p, best, pi));
        }

        // 3. Summary table. T1 = best time with 1 worker.
        double t1 = 0.0;
        for (Result r : results) {
            if (r.p() == 1) {
                t1 = r.best();
            }
        }
        System.out.println();
        System.out.println("workers | best, s | speedup | efficiency | pi");
        for (Result r : results) {
            double speedup = t1 / r.best();
            System.out.printf("%7d | %7.3f | %6.2fx | %9.1f%% | %.12f%n",
                    r.p(), r.best(), speedup, speedup / r.p() * 100, r.pi());
        }

        double pi = results.get(results.size() - 1).pi();
        System.out.println();
        System.out.printf("Math.PI = %.12f, error = %.3e%n", Math.PI, Math.abs(pi - Math.PI));

        curtask.end();
    }
}
