package edu.ncsu.csc411.ps03.dispatch.model;

/** Total, average, and worst estimated response minutes over the real incidents a method covered. */
public class DispatchMetrics {
	private final double totalMinutes;
	private final double averageMinutes;
	private final double worstMinutes;
	private final int count;

	private DispatchMetrics(long totalSeconds, int worstSeconds, int count) {
		this.totalMinutes = totalSeconds / 60.0;
		this.averageMinutes = count == 0 ? 0.0 : this.totalMinutes / count;
		this.worstMinutes = worstSeconds / 60.0;
		this.count = count;
	}

	/** Metrics for a PS03 configuration (config[worker] = task); dummy tasks are skipped. */
	public static DispatchMetrics forConfiguration(DispatchMatrix matrix, int[] config) {
		long total = 0;
		int worst = 0, count = 0;
		for (int worker = 0; worker < config.length; worker++) {
			int task = config[worker];
			if (matrix.isReal(task)) {
				int seconds = matrix.estimateSeconds(task, worker);
				total += seconds;
				worst = Math.max(worst, seconds);
				count++;
			}
		}
		return new DispatchMetrics(total, worst, count);
	}

	/** Metrics for what Raleigh actually sent; unmatched incidents are skipped. */
	public static DispatchMetrics forActual(DispatchMatrix matrix, ActualDispatch actual) {
		long total = 0;
		int worst = 0, count = 0;
		for (int task = 0; task < actual.realTaskCount(); task++) {
			int worker = actual.workerFor(task);
			if (worker >= 0) {
				int seconds = matrix.estimateSeconds(task, worker);
				total += seconds;
				worst = Math.max(worst, seconds);
				count++;
			}
		}
		return new DispatchMetrics(total, worst, count);
	}

	public double getTotalMinutes() { return this.totalMinutes; }
	public double getAverageMinutes() { return this.averageMinutes; }
	public double getWorstMinutes() { return this.worstMinutes; }
	public int getCount() { return this.count; }
	public boolean isEmpty() { return this.count == 0; }

	/** (total − optimal) / optimal; 0 when the optimal total is 0, e.g. an empty window. */
	public double gapTo(DispatchMetrics optimal) {
		if (optimal.totalMinutes == 0) {
			return 0.0;
		}
		return (this.totalMinutes - optimal.totalMinutes) / optimal.totalMinutes;
	}
}
