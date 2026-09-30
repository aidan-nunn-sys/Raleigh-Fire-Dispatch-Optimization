package edu.ncsu.csc411.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import edu.ncsu.csc411.ps03.dispatch.data.Incident;
import edu.ncsu.csc411.ps03.dispatch.data.Scenario;
import edu.ncsu.csc411.ps03.dispatch.data.Station;
import edu.ncsu.csc411.ps03.dispatch.model.AssignmentMethod;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchMatrix;
import edu.ncsu.csc411.ps03.dispatch.model.TravelModel;
import edu.ncsu.csc411.ps03.environment.Environment;

public class MatrixBuilderTest {
	private static final TravelModel MODEL = new TravelModel(60, 120);
	private static final List<Station> STATIONS = Fixtures.stationsAlongLine(28);

	private static DispatchMatrix threeIncidents() {
		List<Incident> incidents = Arrays.asList(
				Fixtures.incident("A", 0, 35.78, -78.70, null),
				Fixtures.incident("B", 1, 35.78, -78.55, null),
				Fixtures.incident("C", 2, 35.80, -78.60, null));
		return new DispatchMatrix(new Scenario("t", "", STATIONS, incidents), MODEL);
	}

	@Test
	public void alwaysSquareAtStationCount() {
		int[][] values = threeIncidents().getValues();
		assertEquals(28, values.length);
		for (int[] row : values) {
			assertEquals(28, row.length);
		}
		DispatchMatrix empty = new DispatchMatrix(new Scenario("e", "", STATIONS, new ArrayList<Incident>()), MODEL);
		assertEquals(28, empty.getValues().length);
		assertEquals(0, empty.realTaskCount());
	}

	@Test
	public void dummyRowsAreAllZero() {
		DispatchMatrix m = threeIncidents();
		int[][] values = m.getValues();
		assertEquals(3, m.realTaskCount());
		for (int task = 3; task < 28; task++) {
			for (int worker = 0; worker < 28; worker++) {
				assertEquals(0, values[task][worker]);
			}
		}
	}

	@Test
	public void closerStationScoresStrictlyHigher() {
		int[][] values = threeIncidents().getValues();
		assertEquals(DispatchMatrix.MAX_SECONDS - 60, values[0][0]); // incident A sits on station S01
		assertTrue(values[0][0] > values[0][1]);
		assertTrue(values[0][1] > values[0][2]);
	}

	@Test
	public void valueIsMaxSecondsMinusEstimate() {
		DispatchMatrix m = threeIncidents();
		int[][] values = m.getValues();
		for (int task = 0; task < m.realTaskCount(); task++) {
			for (int worker = 0; worker < m.size(); worker++) {
				assertEquals(DispatchMatrix.MAX_SECONDS - m.estimateSeconds(task, worker), values[task][worker]);
			}
		}
	}

	@Test
	public void farIncidentClipsToZeroButKeepsSeconds() {
		Incident far = Fixtures.incident("FAR", 0, 38.0, -78.70, null); // ~150 miles north
		DispatchMatrix m = new DispatchMatrix(new Scenario("far", "", STATIONS, Arrays.asList(far)), MODEL);
		assertEquals(0, m.getValues()[0][0]);
		assertTrue(m.estimateSeconds(0, 0) > DispatchMatrix.MAX_SECONDS);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsMoreIncidentsThanStations() {
		List<Incident> incidents = new ArrayList<Incident>();
		for (int k = 0; k < 4; k++) {
			incidents.add(Fixtures.incident("I" + k, k, 35.78, -78.70, null));
		}
		new DispatchMatrix(Fixtures.stationsAlongLine(3), incidents, MODEL);
	}

	@Test
	public void getValuesReturnsACopy() {
		DispatchMatrix m = threeIncidents();
		m.getValues()[0][0] = -1;
		assertEquals(DispatchMatrix.MAX_SECONDS - 60, m.getValues()[0][0]);
	}

	@Test
	public void scoreMatchesEnvironmentCalcScore() {
		int[][] values = threeIncidents().getValues();
		int[] identity = new int[28];
		for (int w = 0; w < 28; w++) {
			identity[w] = w;
		}
		assertEquals(new Environment(values).calcScore(identity), AssignmentMethod.score(values, identity));
	}
}
