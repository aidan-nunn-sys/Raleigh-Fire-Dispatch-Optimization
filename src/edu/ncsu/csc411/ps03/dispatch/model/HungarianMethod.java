package edu.ncsu.csc411.ps03.dispatch.model;

import java.util.Arrays;

/**
 * Exact optimum for the square linear assignment problem (Kuhn–Munkres with row and
 * column potentials, O(n^3)). It maximizes the PS03 score by minimizing
 * cost[worker][task] = maxValue − values[task][worker]; subtracting from a constant
 * does not change which assignment is best.
 */
public class HungarianMethod implements AssignmentMethod {

	@Override
	public String name() { return "Optimal"; }

	@Override
	public int[] solve(int[][] values) {
		int n = values.length;
		for (int[] row : values) {
			if (row.length != n) {
				throw new IllegalArgumentException("Hungarian needs a square matrix");
			}
		}
		if (n == 0) {
			return new int[0];
		}
		long max = Long.MIN_VALUE;
		for (int[] row : values) {
			for (int v : row) {
				max = Math.max(max, v);
			}
		}
		// Cost rows are workers and columns are tasks, both 1-indexed; index 0 is a sentinel.
		long[] u = new long[n + 1];
		long[] v = new long[n + 1];
		int[] p = new int[n + 1];   // p[task] = worker currently holding that task
		int[] way = new int[n + 1];
		for (int worker = 1; worker <= n; worker++) {
			p[0] = worker;
			int j0 = 0;
			long[] minv = new long[n + 1];
			Arrays.fill(minv, Long.MAX_VALUE);
			boolean[] used = new boolean[n + 1];
			do {
				used[j0] = true;
				int i0 = p[j0];
				long delta = Long.MAX_VALUE;
				int j1 = 0;
				for (int j = 1; j <= n; j++) {
					if (!used[j]) {
						long cur = (max - values[j - 1][i0 - 1]) - u[i0] - v[j];
						if (cur < minv[j]) {
							minv[j] = cur;
							way[j] = j0;
						}
						if (minv[j] < delta) {
							delta = minv[j];
							j1 = j;
						}
					}
				}
				for (int j = 0; j <= n; j++) {
					if (used[j]) {
						u[p[j]] += delta;
						v[j] -= delta;
					} else {
						minv[j] -= delta;
					}
				}
				j0 = j1;
			} while (p[j0] != 0);
			do {
				int j1 = way[j0];
				p[j0] = p[j1];
				j0 = j1;
			} while (j0 != 0);
		}
		int[] config = new int[n];
		for (int task = 1; task <= n; task++) {
			config[p[task] - 1] = task - 1;
		}
		return config;
	}
}
