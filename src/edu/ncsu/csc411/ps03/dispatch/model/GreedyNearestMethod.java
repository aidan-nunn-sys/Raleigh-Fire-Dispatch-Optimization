package edu.ncsu.csc411.ps03.dispatch.model;

/**
 * Baseline: take tasks in row order (incidents are in dispatch order) and give each one
 * the free worker with the highest value, which is the nearest free station. The all-zero
 * dummy rows then pick up the leftover stations in index order.
 */
public class GreedyNearestMethod implements AssignmentMethod {

	@Override
	public String name() { return "Greedy nearest"; }

	@Override
	public int[] solve(int[][] values) {
		int n = values.length;
		int[] config = new int[n];
		boolean[] used = new boolean[n];
		for (int task = 0; task < n; task++) {
			int best = -1;
			// Highest value == nearest station, except when several stations clip to 0 (over ~54 mi),
			// where the lowest index wins.
			for (int worker = 0; worker < n; worker++) {
				if (!used[worker] && (best < 0 || values[task][worker] > values[task][best])) {
					best = worker;
				}
			}
			used[best] = true;
			config[best] = task;
		}
		return config;
	}
}
