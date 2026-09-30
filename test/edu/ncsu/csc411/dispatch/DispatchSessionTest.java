package edu.ncsu.csc411.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import edu.ncsu.csc411.ps03.dispatch.data.Incident;
import edu.ncsu.csc411.ps03.dispatch.data.Scenario;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchSession;
import edu.ncsu.csc411.ps03.dispatch.model.TravelModel;

public class DispatchSessionTest {
	private static final TravelModel MODEL = new TravelModel(60, 120);

	/** Six stations on a line and `count` incidents spread along it. */
	static Scenario scenario(int count) {
		List<Incident> incidents = new ArrayList<Incident>();
		for (int i = 0; i < count; i++) {
			incidents.add(Fixtures.incident("I" + i, i, 35.78, -78.70 + 0.013 * i, null));
		}
		return new Scenario("Test", "", Fixtures.stationsAlongLine(6), incidents);
	}

	/** A session over scenario(count) with the test travel model. */
	static DispatchSession scenarioSession(int count) {
		return new DispatchSession(scenario(count), MODEL);
	}

	@Test
	public void startsAtIterationZeroWithValidConfigurations() {
		DispatchSession s = new DispatchSession(scenario(4), MODEL);
		assertEquals(0, s.getIteration());
		assertEquals(1, s.getHistory().size());
		assertTrue(Fixtures.isPermutation(s.getCurrentConfiguration()));
		assertTrue(Fixtures.isPermutation(s.getBestConfiguration()));
		assertTrue(Fixtures.isPermutation(s.getOptimalConfiguration()));
		assertTrue(Fixtures.isPermutation(s.getGreedyConfiguration()));
		assertEquals(4, s.getOptimalMetrics().getCount());
	}

	@Test
	public void stepsAppendHistoryAndBestNeverGetsWorse() {
		DispatchSession s = new DispatchSession(scenario(5), MODEL);
		for (int i = 0; i < 300; i++) {
			s.step();
		}
		assertEquals(300, s.getIteration());
		List<double[]> history = s.getHistory();
		assertEquals(301, history.size());
		for (int i = 1; i < history.size(); i++) {
			assertTrue("best got worse at " + i, history.get(i)[1] <= history.get(i - 1)[1] + 1e-9);
		}
		assertTrue(s.getBestMetrics().getTotalMinutes() >= s.getOptimalMetrics().getTotalMinutes() - 1e-9);
	}

	@Test
	public void resetStartsANewRun() {
		DispatchSession s = new DispatchSession(scenario(4), MODEL);
		for (int i = 0; i < 20; i++) {
			s.step();
		}
		s.reset();
		assertEquals(0, s.getIteration());
		assertEquals(0, s.getBestIteration());
		assertEquals(1, s.getHistory().size());
	}

	@Test
	public void optimalIsNoWorseThanGreedy() {
		DispatchSession s = new DispatchSession(scenario(5), MODEL);
		assertTrue(s.getOptimalMetrics().getTotalMinutes() <= s.getGreedyMetrics().getTotalMinutes() + 1e-9);
	}

	@Test
	public void emptyScenarioIsSafe() {
		DispatchSession s = new DispatchSession(scenario(0), MODEL);
		for (int i = 0; i < 5; i++) {
			s.step();
		}
		assertTrue(s.getOptimalMetrics().isEmpty());
		assertEquals(0.0, s.getBestMetrics().gapTo(s.getOptimalMetrics()), 0.0);
		assertTrue(s.bestIsOptimal());
		for (double[] point : s.getHistory()) {
			assertEquals(0.0, point[0], 0.0);
			assertEquals(0.0, point[1], 0.0);
		}
	}

	@Test
	public void workerForTaskInvertsAConfiguration() {
		int[] config = {2, 0, 1};
		assertEquals(1, DispatchSession.workerForTask(config, 0));
		assertEquals(0, DispatchSession.workerForTask(config, 2));
		assertEquals(-1, DispatchSession.workerForTask(config, 5));
	}
}
