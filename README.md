# PARCS: computing π with the Leibniz series

Lab work #1, course "Cloud Computing", Taras Shevchenko National University of Kyiv, 2026.
Author: Taras Tertychnyi, group TK-42.

## Task

Implement a parallel computation of a series sum in the distributed system PARCS (Java version)
and run it on a cluster of virtual machines in Google Cloud:

    pi = 4 * (1 - 1/3 + 1/5 - 1/7 + ...) = 4 * sum_{k=0}^{N-1} (-1)^k / (2k + 1)

Measure the run time for different numbers of workers and compute the speedup `Sp = T1 / Tp`
and the efficiency `Ep = Sp / p`.

## How it works

- `Main` (root module) reads `out/input`, runs the sequential version to get `T1`, then for every
  worker count `p` splits the range `[0, N)` into `p` equal parts, creates a PARCS point per part,
  sends `from` and `to` over a channel and adds up the partial sums.
- `SeriesWorker` reads `from` and `to`, computes the partial sum and sends it back.
- The sum is computed in blocks of 100 000 terms, so the JIT compiler always compiles the hot loop
  normally (one huge loop is optimised mid-run via OSR and was randomly ~4x slower).

## Project structure

| Path | Purpose |
| --- | --- |
| `src/Main.java` | root module |
| `src/SeriesWorker.java` | worker |
| `out/input` | N, worker counts, number of repeats |
| `out/server` | IP of the hosts-server (written by `deploy.sh`) |
| `out/parcs.jar` | PARCS library |
| `Makefile` | build `Main.jar` / `SeriesWorker.jar` and run |
| `cloud/deploy.sh` | create the VMs, start daemons and hosts-server, run the lab |
| `cloud/cleanup.sh` | delete the VMs |
| `cloud/Daemon.jar`, `cloud/TCPHostsServer.jar` | PARCS server side ([lionell/labs](https://github.com/lionell/labs/tree/master/parcs)) |

## Running in Google Cloud

Cluster: 4 VMs `e2-standard-2` in `europe-central2-a` — `app` (hosts-server + Main) and
`daemon-1..3` (PARCS daemons). In Google Cloud Shell:

```
git clone https://github.com/tapa2/parcs-leibniz-pi.git
cd parcs-leibniz-pi
bash cloud/deploy.sh     # create VMs, start the cluster, run make
bash cloud/cleanup.sh    # delete VMs when finished
```

## Results

Google Cloud, N = 10^10, best of 3 runs, speedup relative to 1 worker:

| Workers | Best time, s | Speedup | Efficiency |
| --- | --- | --- | --- |
| 1 | 26.707 | 1.00x | 100% |
| 2 | 19.210 | 1.39x | 69.5% |
| 3 | 12.776 | 2.09x | 69.7% |
| 6 | 7.015 | 3.81x | 63.5% |

π = 3.141592653490, error vs `Math.PI` = 1.0e-10 ≈ 1/N.
Screenshots: [`docs/screenshots`](docs/screenshots). Full report: `Звіт_ЛР1_PARCS_Тертичний.docx`.
