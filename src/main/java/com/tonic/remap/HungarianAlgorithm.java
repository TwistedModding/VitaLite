// Source-adapted implementation of the Hungarian algorithm (for minimization).
// Credit: public-domain/adapted from common implementations.
package com.tonic.remap;

import java.util.Arrays;

public class HungarianAlgorithm {
    private final double[][] cost;
    private final int n;
    private final double[] u;
    private final double[] v;
    private final int[] p;
    private final int[] way;

    public HungarianAlgorithm(double[][] costMatrix) {
        this.n = costMatrix.length;
        this.cost = new double[n + 1][n + 1]; // 1-based
        for (int i = 1; i <= n; i++) {
            System.arraycopy(costMatrix[i - 1], 0, cost[i], 1, n);
        }
        u = new double[n + 1];
        v = new double[n + 1];
        p = new int[n + 1];
        way = new int[n + 1];
    }

    /**
     * Executes and returns array where result[row] = assigned column (0-based), or -1.
     */
    public int[] execute() {
        for (int i = 1; i <= n; ++i) {
            p[0] = i;
            int j0 = 0;
            double[] minv = new double[n + 1];
            boolean[] used = new boolean[n + 1];
            Arrays.fill(minv, Double.POSITIVE_INFINITY);
            while (true) {
                used[j0] = true;
                int i0 = p[j0];
                int j1 = -1;
                double delta = Double.POSITIVE_INFINITY;
                for (int j = 1; j <= n; ++j) {
                    if (used[j]) continue;
                    double cur = cost[i0][j] - u[i0] - v[j];
                    if (cur < minv[j]) {
                        minv[j] = cur;
                        way[j] = j0;
                    }
                    if (minv[j] < delta) {
                        delta = minv[j];
                        j1 = j;
                    }
                }
                for (int j = 0; j <= n; ++j) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
                if (p[j0] == 0) break;
            }
            while (true) {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
                if (j0 == 0) break;
            }
        }

        int[] assignment = new int[n];
        Arrays.fill(assignment, -1);
        for (int j = 1; j <= n; ++j) {
            if (p[j] != 0 && p[j] - 1 < n && j - 1 < n) {
                assignment[p[j] - 1] = j - 1;
            }
        }
        return assignment;
    }
}
