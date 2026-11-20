import parcs.*;

/**
 * PARCS worker: receives a range of term indices [from, to) and returns
 * the partial sum of the Leibniz series over that range:
 *
 *     sum_{k=from}^{to-1} (-1)^k / (2k + 1)
 */
public class SeriesWorker implements AM {

    public void run(AMInfo info) {
        long from = info.parent.readLong();
        long to = info.parent.readLong();

        double sum = Main.partialSum(from, to);

        System.out.println("[worker] [" + from + ", " + to + ") -> " + sum);
        info.parent.write(sum);
    }
}
