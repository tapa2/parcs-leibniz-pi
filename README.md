# PARCS: computing π with the Leibniz series

Lab work #1, course "Cloud Computing", Taras Shevchenko National University of Kyiv, 2026.
Author: Taras Tertychnyi, group TK-42.

## Task

Implement a parallel computation of a series sum in the distributed system PARCS (Java version)
and run it on a cluster of virtual machines in Google Cloud:

    pi = 4 * (1 - 1/3 + 1/5 - 1/7 + ...) = 4 * sum_{k=0}^{N-1} (-1)^k / (2k + 1)

Measure the run time for different numbers of workers and compute the speedup `Sp = T1 / Tp`
and the efficiency `Ep = Sp / p`.
