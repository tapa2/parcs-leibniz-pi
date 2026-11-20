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
 *   1. Sequential run on this machine -> base time T1.
 *   2. For every worker count p: split [0, N) into p equal ranges,
 *      send one range to each worker, add up the partial sums.
 *   3. Print a table: best and average time, speedup T1/Tp, efficiency Sp/p.
 */
public class Main {

    /** Number of terms summed by one call of blockSum. */
    static final long BLOCK = 100_000L;

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

        System.out.println("N = " + n + ", repeats = " + repeats);

        // 2. Sequential baseline.
        // Speedup uses the BEST (minimal) time of all repeats: it is the least
        // affected by outside noise (other processes, CPU heating up).
        // The average is printed too, for comparison.
        double seqPi = 0.0;
        double seqTotal = 0.0;
        double seqBest = Double.MAX_VALUE;
        for (int r = 0; r < repeats; r++) {
            long t0 = System.nanoTime();
            seqPi = 4.0 * partialSum(0, n);
            double t = (System.nanoTime() - t0) / 1e9;
            System.out.printf("  sequential run %d: %.3f s%n", r + 1, t);
            seqTotal += t;
            seqBest = Math.min(seqBest, t);
        }
        double seqAvg = seqTotal / repeats;
        System.out.printf("Sequential: pi = %.12f, best = %.3f s, avg = %.3f s%n",
                seqPi, seqBest, seqAvg);

        // 3. Parallel runs
        AMInfo info = new AMInfo(curtask, null);
        List<String> table = new ArrayList<>();

        for (int p : workerCounts) {
            double pi = 0.0;
            double total = 0.0;
            double best = Double.MAX_VALUE;
            for (int r = 0; r < repeats; r++) {
                long start = System.nanoTime();
                pi = parallelPi(info, n, p);
                double t = (System.nanoTime() - start) / 1e9;
                System.out.printf("  %d workers, run %d: %.3f s%n", p, r + 1, t);
                total += t;
                best = Math.min(best, t);
            }
            double avg = total / repeats;

            double speedup = seqBest / best;
            double efficiency = speedup / p;
            table.add(String.format("%7d | %7.3f | %7.3f | %6.2fx | %9.1f%% | %.12f",
                    p, best, avg, speedup, efficiency * 100, pi));
        }

        // 4. Summary table (printed at the end, so it is not mixed with run logs)
        System.out.println();
        System.out.println("workers | best, s |  avg, s | speedup | efficiency | pi");
        System.out.printf("   seq. | %7.3f | %7.3f |   1.00x |     100.0%% | %.12f%n",
                seqBest, seqAvg, seqPi);
        for (String row : table) {
            System.out.println(row);
        }

        System.out.println();
        System.out.printf("Math.PI = %.12f, error = %.3e%n", Math.PI, Math.abs(seqPi - Math.PI));

        curtask.end();
    }
}
