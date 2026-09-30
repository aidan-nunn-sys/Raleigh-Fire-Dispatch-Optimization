package edu.ncsu.csc411.dispatch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

import edu.ncsu.csc411.ps03.dispatch.model.AssignmentMethod;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchMatrix;
import edu.ncsu.csc411.ps03.dispatch.model.GreedyNearestMethod;
import edu.ncsu.csc411.ps03.dispatch.model.HungarianMethod;
import edu.ncsu.csc411.ps03.dispatch.model.TravelModel;
import edu.ncsu.csc411.ps03.environment.Environment;

public class GreedyTest {
	private final GreedyNearestMethod greedy = new GreedyNearestMethod();

	@Test
	public void handBuiltCaseTakesRowsInOrder() {
		int[][] values = {
			{10, 9, 1},  // task 0 takes worker 0
			{10, 2, 8},  // worker 0 is used, so task 1 takes worker 2
			{0, 0, 0},   // the dummy row takes the leftover worker 1
		};
		int[] config = this.greedy.solve(values);
		assertArrayEquals(new int[] {0, 2, 1}, config);
		assertEquals(18, AssignmentMethod.score(values, config));
		// Greedy is not optimal here: task 0 → worker 1 and task 1 → worker 0 scores 19.
		assertEquals(19, AssignmentMethod.score(values, new HungarianMethod().solve(values)));
	}

	@Test
	public void picksNearestFreeStationOnTheMap() {
		// Stations at lon -78.70, -78.69, -78.68. Both incidents are nearest to S02 (-78.69);
		// the second sits at -78.688, so once S02 is taken its nearest free station is S03.
		DispatchMatrix m = new DispatchMatrix(Fixtures.stationsAlongLine(3), Arrays.asList(
				Fixtures.incident("first", 0, 35.78, -78.69, null),
				Fixtures.incident("second", 1, 35.78, -78.688, null)), new TravelModel(60, 120));
		int[] config = this.greedy.solve(m.getValues());
		assertEquals(0, config[1]); // S02 → first
		assertEquals(1, config[2]); // S03 → second
		assertEquals(2, config[0]); // S01 stays home (dummy row)
	}

	@Test
	public void randomMatrixGivesAValidConfiguration() {
		Random random = new Random(411);
		int[][] values = new int[28][28];
		for (int i = 0; i < 28; i++) {
			for (int j = 0; j < 28; j++) {
				values[i][j] = random.nextInt(3600);
			}
		}
		int[] config = this.greedy.solve(values);
		assertTrue(Fixtures.isPermutation(config));
		assertTrue(new Environment(values).validConfiguration(config));
	}
}
