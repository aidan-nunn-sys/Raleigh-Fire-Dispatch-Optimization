package edu.ncsu.csc411.ps03.dispatch.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.ncsu.csc411.ps03.dispatch.data.Scenario;

/**
 * Everything the dispatch window shows for one scenario: the value matrix, the fixed
 * baselines (Optimal, Greedy, Actual), and a step-by-step run of the student's simulated
 * annealing with its history of total minutes. There is no Swing here, so it can be unit tested.
 */
public class DispatchSession {
	private final Scenario scenario;
	private final DispatchMatrix matrix;
	private final int[][] values;
	private final int[] optimalConfig;
	private final int[] greedyConfig;
	private final long optimalScore;
	private final ActualDispatch actual;
	private final DispatchMetrics optimal;
	private final DispatchMetrics greedy;
	private final DispatchMetrics actualMetrics;
	private final List<double[]> history = new ArrayList<double[]>();
	private AnnealingRun run;
	private DispatchMetrics current;
	private DispatchMetrics best;

	public DispatchSession(Scenario scenario, TravelModel model) {
		this.scenario = scenario;
		this.matrix = new DispatchMatrix(scenario, model);
		this.values = this.matrix.getValues();
		this.optimalConfig = new HungarianMethod().solve(this.values);
		this.greedyConfig = new GreedyNearestMethod().solve(this.values);
		this.optimalScore = AssignmentMethod.score(this.values, this.optimalConfig);
		this.actual = new ActualDispatch(this.matrix);
		this.optimal = DispatchMetrics.forConfiguration(this.matrix, this.optimalConfig);
		this.greedy = DispatchMetrics.forConfiguration(this.matrix, this.greedyConfig);
		this.actualMetrics = DispatchMetrics.forActual(this.matrix, this.actual);
		reset();
	}

	/** Starts a fresh SA run (a new Environment and solver) and clears the history. */
	public void reset() {
		this.run = new AnnealingRun(this.values);
		this.history.clear();
		record();
	}

	/** One SA iteration. */
	public void step() {
		this.run.step();
		record();
	}

	private void record() {
		this.current = DispatchMetrics.forConfiguration(this.matrix, this.run.getCurrentConfiguration());
		this.best = DispatchMetrics.forConfiguration(this.matrix, this.run.getBestConfiguration());
		this.history.add(new double[] {this.current.getTotalMinutes(), this.best.getTotalMinutes()});
	}

	public Scenario getScenario() { return this.scenario; }
	public DispatchMatrix getMatrix() { return this.matrix; }
	public ActualDispatch getActual() { return this.actual; }
	public int getIteration() { return this.run.getIteration(); }
	public int getBestIteration() { return this.run.getBestIteration(); }
	/** True once SA's best score equals the Hungarian optimum. */
	public boolean bestIsOptimal() { return this.run.getBestScore() >= this.optimalScore; }

	public int[] getCurrentConfiguration() { return this.run.getCurrentConfiguration(); }
	public int[] getBestConfiguration() { return this.run.getBestConfiguration(); }
	public int[] getOptimalConfiguration() { return this.optimalConfig.clone(); }
	public int[] getGreedyConfiguration() { return this.greedyConfig.clone(); }

	public DispatchMetrics getCurrentMetrics() { return this.current; }
	public DispatchMetrics getBestMetrics() { return this.best; }
	public DispatchMetrics getOptimalMetrics() { return this.optimal; }
	public DispatchMetrics getGreedyMetrics() { return this.greedy; }
	public DispatchMetrics getActualMetrics() { return this.actualMetrics; }

	/** {current total minutes, best total minutes} per iteration; index 0 is the starting state. */
	public List<double[]> getHistory() { return Collections.unmodifiableList(this.history); }

	/** The worker holding task in a PS03 configuration (config[worker] = task), or -1. */
	public static int workerForTask(int[] config, int task) {
		for (int worker = 0; worker < config.length; worker++) {
			if (config[worker] == task) {
				return worker;
			}
		}
		return -1;
	}
}
