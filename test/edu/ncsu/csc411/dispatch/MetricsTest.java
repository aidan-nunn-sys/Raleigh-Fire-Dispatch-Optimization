package edu.ncsu.csc411.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

import edu.ncsu.csc411.ps03.dispatch.data.Incident;
import edu.ncsu.csc411.ps03.dispatch.model.ActualDispatch;
import edu.ncsu.csc411.ps03.dispatch.model.AnnealingRun;
import edu.ncsu.csc411.ps03.dispatch.model.AssignmentMethod;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchMatrix;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchMetrics;
import edu.ncsu.csc411.ps03.dispatch.model.HungarianMethod;
import edu.ncsu.csc411.ps03.dispatch.model.TravelModel;
import edu.ncsu.csc411.ps03.environment.Environment;
import edu.ncsu.csc411.ps03.utils.InputManager;

public class MetricsTest {
	private static final TravelModel MODEL = new TravelModel(60, 120);

	/** Stations S01 (-78.70), S02 (-78.69), S03 (-78.68). A sits on S01, B sits on S03. */
	private static DispatchMatrix twoIncidents(String actualA, String actualB) {
		return new DispatchMatrix(Fixtures.stationsAlongLine(3), Arrays.asList(
				Fixtures.incident("A", 0, 35.78, -78.70, actualA),
				Fixtures.incident("B", 1, 35.78, -78.68, actualB)), MODEL);
	}

	@Test
	public void configurationMetricsSkipDummyRows() {
		DispatchMatrix m = twoIncidents(null, null);
		// S01 → A, S02 → dummy, S03 → B: both responses are turnout only (60 s).
		DispatchMetrics metrics = DispatchMetrics.forConfiguration(m, new int[] {0, 2, 1});
		assertEquals(2, metrics.getCount());
		assertEquals(2.0, metrics.getTotalMinutes(), 1e-9);
		assertEquals(1.0, metrics.getAverageMinutes(), 1e-9);
		assertEquals(1.0, metrics.getWorstMinutes(), 1e-9);
	}

	@Test
	public void gapToOptimalIsRelativeExtraMinutes() {
		DispatchMatrix m = twoIncidents(null, null);
		DispatchMetrics optimal = DispatchMetrics.forConfiguration(m, new HungarianMethod().solve(m.getValues()));
		assertEquals(2.0, optimal.getTotalMinutes(), 1e-9);
		// S01 → B, S02 → dummy, S03 → A: both cross the 0.02° gap.
		DispatchMetrics crossed = DispatchMetrics.forConfiguration(m, new int[] {1, 2, 0});
		double expected = (m.estimateSeconds(1, 0) + m.estimateSeconds(0, 2)) / 60.0;
		assertEquals(expected, crossed.getTotalMinutes(), 1e-9);
		assertEquals((expected - 2.0) / 2.0, crossed.gapTo(optimal), 1e-9);
		assertEquals(0.0, optimal.gapTo(optimal), 0.0);
	}

	@Test
	public void actualSkipsUnmatchedIncidents() {
		DispatchMatrix m = twoIncidents("S02", null);
		ActualDispatch actual = new ActualDispatch(m);
		assertEquals(1, actual.workerFor(0));
		assertEquals(-1, actual.workerFor(1));
		assertEquals(1, actual.unmatchedCount());
		assertTrue(actual.isAvailable());
		DispatchMetrics metrics = DispatchMetrics.forActual(m, actual);
		assertEquals(1, metrics.getCount());
		assertEquals(m.estimateSeconds(0, 1) / 60.0, metrics.getTotalMinutes(), 1e-9);
	}

	@Test
	public void actualAllowsTheSameStationTwice() {
		DispatchMatrix m = twoIncidents("S02", "S02");
		DispatchMetrics metrics = DispatchMetrics.forActual(m, new ActualDispatch(m));
		assertEquals(2, metrics.getCount());
	}

	@Test
	public void actualUnavailableWhenNoStationPublished() {
		DispatchMatrix m = twoIncidents(null, null);
		ActualDispatch actual = new ActualDispatch(m);
		assertFalse(actual.isAvailable());
		assertEquals(2, actual.unmatchedCount());
		assertTrue(DispatchMetrics.forActual(m, actual).isEmpty());
	}

	@Test
	public void emptyWindowHasZeroGapNotNaN() {
		DispatchMatrix m = new DispatchMatrix(Fixtures.stationsAlongLine(28), new ArrayList<Incident>(), MODEL);
		DispatchMetrics optimal = DispatchMetrics.forConfiguration(m, new HungarianMethod().solve(m.getValues()));
		assertTrue(optimal.isEmpty());
		assertEquals(0.0, optimal.getAverageMinutes(), 0.0);
		assertEquals(0.0, optimal.gapTo(optimal), 0.0);
		assertFalse(new ActualDispatch(m).isAvailable());
	}

	@Test
	public void annealingRunStartsFromAValidConfiguration() {
		int[][] map = InputManager.loadMap("inputs/public/input03.txt");
		AnnealingRun run = new AnnealingRun(map);
		assertEquals(0, run.getIteration());
		assertTrue(Fixtures.isPermutation(run.getCurrentConfiguration()));
	}

	@Test
	public void annealingRunNeverBeatsTheOptimum() {
		int[][] map = InputManager.loadMap("inputs/public/input03.txt");
		long optimum = AssignmentMethod.score(map, new HungarianMethod().solve(map));
		AnnealingRun run = new AnnealingRun(map);
		for (int k = 0; k < 1000; k++) {
			run.step();
		}
		assertEquals(1000, run.getIteration());
		assertTrue(run.getBestScore() <= optimum);
		assertTrue(run.getBestIteration() >= 0 && run.getBestIteration() <= 1000);
		assertTrue(new Environment(map).validConfiguration(run.getBestConfiguration()));
		assertEquals(run.getBestScore(), AssignmentMethod.score(map, run.getBestConfiguration()));
	}
}
