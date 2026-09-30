package edu.ncsu.csc411.ps03.dispatch.benchmark;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;

import edu.ncsu.csc411.ps03.dispatch.data.Incident;
import edu.ncsu.csc411.ps03.dispatch.data.Scenario;
import edu.ncsu.csc411.ps03.dispatch.data.ScenarioIO;
import edu.ncsu.csc411.ps03.dispatch.data.Station;
import edu.ncsu.csc411.ps03.dispatch.model.ActualDispatch;
import edu.ncsu.csc411.ps03.dispatch.model.AnnealingRun;
import edu.ncsu.csc411.ps03.dispatch.model.AssignmentMethod;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchMatrix;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchMetrics;
import edu.ncsu.csc411.ps03.dispatch.model.GreedyNearestMethod;
import edu.ncsu.csc411.ps03.dispatch.model.HungarianMethod;
import edu.ncsu.csc411.ps03.dispatch.model.TravelModel;

/**
 * Headless evidence for the +X report. Writes outputs/benchmark_scenarios.csv (SA against
 * the optimum, Greedy, and Actual on each committed scenario) and outputs/benchmark_scaling.csv
 * (SA against the optimum as the problem grows). The scaling instances are seeded, but the
 * student's solver seeds its own Random, so SA numbers vary slightly between runs.
 */
public class DispatchBenchmark {
	static final String DIR = "inputs/raleigh/";
	static final String[] SCENARIOS = {"florence_peak", "helene_peak", "typical_weekday"};
	static final int TRIALS = 100;
	static final int ITERATIONS = 1000;
	static final int[] SIZES = {6, 8, 10, 12, 16, 20, 24, 28};
	static final int INSTANCES = 30;
	static final int INSTANCE_TRIALS = 20;

	/** SA results over repeated trials on one matrix. */
	static class TrialStats {
		double[] gaps;
		int hits;
		double meanBestIteration;
		double meanRuntimeMs;
	}

	public static void main(String[] args) throws IOException {
		List<Station> stations = ScenarioIO.readStations(DIR + "stations.csv");
		TravelModel model = TravelModel.load(DIR + "travel_model.properties");
		printTravelModel();
		writeScenarios(stations, model);
		writeScaling(stations, model);
	}

	static TrialStats runTrials(DispatchMatrix matrix, DispatchMetrics optimal, long optimalScore, int trials) {
		int[][] values = matrix.getValues();
		TrialStats stats = new TrialStats();
		stats.gaps = new double[trials];
		long iterationSum = 0, nanos = 0;
		for (int t = 0; t < trials; t++) {
			long start = System.nanoTime();
			AnnealingRun run = new AnnealingRun(values);
			for (int i = 0; i < ITERATIONS; i++) {
				run.step();
			}
			nanos += System.nanoTime() - start;
			stats.gaps[t] = DispatchMetrics.forConfiguration(matrix, run.getBestConfiguration()).gapTo(optimal);
			if (run.getBestScore() >= optimalScore) {
				stats.hits++;
			}
			iterationSum += run.getBestIteration();
		}
		stats.meanBestIteration = iterationSum / (double) trials;
		stats.meanRuntimeMs = nanos / 1e6 / trials;
		return stats;
	}

	private static void writeScenarios(List<Station> stations, TravelModel model) throws IOException {
		PrintWriter out = new PrintWriter("outputs/benchmark_scenarios.csv");
		try {
			out.println("scenario,incidents,optimal_total_min,optimal_avg_min,greedy_total_min,greedy_gap,"
					+ "actual_total_min,actual_avg_min,actual_unmatched,sa_trials,sa_iterations,sa_pct_optimal,"
					+ "sa_mean_gap,sa_median_gap,sa_worst_gap,sa_mean_best_iteration,sa_mean_runtime_ms");
			for (String name : SCENARIOS) {
				Scenario scenario = ScenarioIO.readScenario(DIR + "scenarios/" + name + ".csv", stations);
				for (String warning : scenario.getWarnings()) {
					System.out.println(name + ": " + warning);
				}
				DispatchMatrix matrix = new DispatchMatrix(scenario, model);
				int[][] values = matrix.getValues();
				int[] optimalConfig = new HungarianMethod().solve(values);
				DispatchMetrics optimal = DispatchMetrics.forConfiguration(matrix, optimalConfig);
				long optimalScore = AssignmentMethod.score(values, optimalConfig);
				DispatchMetrics greedy = DispatchMetrics.forConfiguration(matrix, new GreedyNearestMethod().solve(values));
				ActualDispatch actual = new ActualDispatch(matrix);
				DispatchMetrics actualMetrics = DispatchMetrics.forActual(matrix, actual);
				TrialStats sa = runTrials(matrix, optimal, optimalScore, TRIALS);

				out.printf(Locale.US, "%s,%d,%.2f,%.2f,%.2f,%.4f,%s,%s,%d,%d,%d,%.1f,%.4f,%.4f,%.4f,%.1f,%.2f%n",
						name, matrix.realTaskCount(),
						optimal.getTotalMinutes(), optimal.getAverageMinutes(),
						greedy.getTotalMinutes(), greedy.gapTo(optimal),
						actual.isAvailable() ? String.format(Locale.US, "%.2f", actualMetrics.getTotalMinutes()) : "NA",
						actual.isAvailable() ? String.format(Locale.US, "%.2f", actualMetrics.getAverageMinutes()) : "NA",
						actual.unmatchedCount(), TRIALS, ITERATIONS,
						100.0 * sa.hits / TRIALS, mean(sa.gaps), median(sa.gaps), max(sa.gaps),
						sa.meanBestIteration, sa.meanRuntimeMs);
				System.out.printf(Locale.US, "%-16s k=%2d  optimal %.1f min  greedy +%.1f%%  SA optimal in %.0f%% of runs, mean gap %.2f%%%n",
						name, matrix.realTaskCount(), optimal.getTotalMinutes(), 100 * greedy.gapTo(optimal),
						100.0 * sa.hits / TRIALS, 100 * mean(sa.gaps));
			}
		} finally {
			out.close();
		}
	}

	private static void writeScaling(List<Station> stations, TravelModel model) throws IOException {
		List<Incident> day = ScenarioIO.readIncidents(DIR + "scenarios/florence_day.csv");
		PrintWriter out = new PrintWriter("outputs/benchmark_scaling.csv");
		try {
			out.println("n,n_factorial,instances,trials_per_instance,sa_pct_optimal,sa_mean_gap,sa_median_gap,greedy_mean_gap");
			for (int n : SIZES) {
				if (n > stations.size() || n > day.size()) {
					System.out.println("Skipping n=" + n + ": not enough stations or incidents");
					continue;
				}
				List<Double> gaps = new ArrayList<Double>();
				int hits = 0;
				double greedyGapSum = 0;
				for (int instance = 0; instance < INSTANCES; instance++) {
					Random random = new Random(1000L * n + instance);
					List<Station> pickedStations = new ArrayList<Station>(stations);
					Collections.shuffle(pickedStations, random);
					pickedStations = new ArrayList<Station>(pickedStations.subList(0, n));
					List<Incident> pickedIncidents = new ArrayList<Incident>(day);
					Collections.shuffle(pickedIncidents, random);
					pickedIncidents = new ArrayList<Incident>(pickedIncidents.subList(0, n));
					Collections.sort(pickedIncidents, Scenario.DISPATCH_ORDER);

					DispatchMatrix matrix = new DispatchMatrix(pickedStations, pickedIncidents, model);
					int[][] values = matrix.getValues();
					int[] optimalConfig = new HungarianMethod().solve(values);
					DispatchMetrics optimal = DispatchMetrics.forConfiguration(matrix, optimalConfig);
					greedyGapSum += DispatchMetrics.forConfiguration(matrix, new GreedyNearestMethod().solve(values)).gapTo(optimal);
					TrialStats sa = runTrials(matrix, optimal, AssignmentMethod.score(values, optimalConfig), INSTANCE_TRIALS);
					hits += sa.hits;
					for (double gap : sa.gaps) {
						gaps.add(Double.valueOf(gap));
					}
				}
				double[] all = new double[gaps.size()];
				for (int k = 0; k < all.length; k++) {
					all[k] = gaps.get(k).doubleValue();
				}
				out.printf(Locale.US, "%d,%s,%d,%d,%.1f,%.4f,%.4f,%.4f%n", n, factorial(n), INSTANCES, INSTANCE_TRIALS,
						100.0 * hits / all.length, mean(all), median(all), greedyGapSum / INSTANCES);
				System.out.printf(Locale.US, "n=%2d  SA optimal in %.1f%% of runs, mean gap %.2f%%%n",
						n, 100.0 * hits / all.length, 100 * mean(all));
			}
		} finally {
			out.close();
		}
	}

	private static void printTravelModel() throws IOException {
		Properties props = new Properties();
		InputStream in = new FileInputStream(DIR + "travel_model.properties");
		try {
			props.load(in);
		} finally {
			in.close();
		}
		for (String key : new String[] {"TURNOUT_SECONDS", "SECONDS_PER_MILE", "SAMPLE_SIZE", "R_SQUARED",
				"FIT_FROM", "FIT_TO", "CHECK_SAMPLE_SIZE", "CHECK_MAE_SECONDS", "CHECK_BIAS_SECONDS"}) {
			System.out.println(key + "=" + props.getProperty(key, "(missing)"));
		}
	}

	static BigInteger factorial(int n) {
		BigInteger result = BigInteger.ONE;
		for (int k = 2; k <= n; k++) {
			result = result.multiply(BigInteger.valueOf(k));
		}
		return result;
	}

	static double mean(double[] xs) {
		double sum = 0;
		for (double x : xs) {
			sum += x;
		}
		return xs.length == 0 ? 0.0 : sum / xs.length;
	}

	static double median(double[] xs) {
		if (xs.length == 0) {
			return 0.0;
		}
		double[] sorted = xs.clone();
		Arrays.sort(sorted);
		int mid = sorted.length / 2;
		return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2.0;
	}

	static double max(double[] xs) {
		double best = 0.0;
		for (double x : xs) {
			best = Math.max(best, x);
		}
		return best;
	}
}
