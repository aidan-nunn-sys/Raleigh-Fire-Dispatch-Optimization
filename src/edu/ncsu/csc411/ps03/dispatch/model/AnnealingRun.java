package edu.ncsu.csc411.ps03.dispatch.model;

import edu.ncsu.csc411.ps03.environment.Environment;

/**
 * Drives the student's simulated annealing (ConfigurationSolver, unchanged) through the
 * PS03 Environment one iteration at a time, and remembers the iteration where the best
 * score was first reached.
 */
public class AnnealingRun {
	private final Environment env;
	private int iteration;
	private long bestScore;
	private int bestIteration;

	public AnnealingRun(int[][] values) {
		this.env = new Environment(values);
		this.iteration = 0;
		this.bestScore = this.env.calcScore(this.env.getBestConfiguration());
		this.bestIteration = 0;
	}

	/** One call to Environment.updateConfiguration(), i.e. one solver iteration. */
	public void step() {
		this.env.updateConfiguration();
		this.iteration++;
		long score = this.env.calcScore(this.env.getBestConfiguration());
		if (score > this.bestScore) {
			this.bestScore = score;
			this.bestIteration = this.iteration;
		}
	}

	public int getIteration() { return this.iteration; }
	public int getBestIteration() { return this.bestIteration; }
	public long getBestScore() { return this.bestScore; }

	/**
	 * The solver's current state. Environment starts its current configuration as all
	 * zeros (not a valid assignment), so before the first step this returns the solver's
	 * starting configuration instead.
	 */
	public int[] getCurrentConfiguration() {
		return this.iteration == 0 ? this.env.getBestConfiguration().clone() : this.env.getCurrentConfiguration().clone();
	}

	public int[] getBestConfiguration() {
		return this.env.getBestConfiguration().clone();
	}
}
