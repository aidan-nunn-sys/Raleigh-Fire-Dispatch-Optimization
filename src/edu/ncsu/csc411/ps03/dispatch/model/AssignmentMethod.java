package edu.ncsu.csc411.ps03.dispatch.model;

/** A strategy that gives every worker a distinct task, PS03-style: config[worker] = task. */
public interface AssignmentMethod {
	String name();

	int[] solve(int[][] values);

	/** The same score as Environment.calcScore: the sum of values[config[w]][w]. */
	static long score(int[][] values, int[] config) {
		long score = 0;
		for (int worker = 0; worker < config.length; worker++) {
			score += values[config[worker]][worker];
		}
		return score;
	}
}
