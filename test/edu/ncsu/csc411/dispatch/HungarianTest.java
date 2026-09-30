package edu.ncsu.csc411.dispatch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

import edu.ncsu.csc411.ps03.dispatch.model.AssignmentMethod;
import edu.ncsu.csc411.ps03.dispatch.model.HungarianMethod;
import edu.ncsu.csc411.ps03.environment.Environment;
import edu.ncsu.csc411.ps03.utils.InputManager;

public class HungarianTest {
	private final HungarianMethod hungarian = new HungarianMethod();

	private static long bruteForce(int[][] values, int[] config, boolean[] used, int worker) {
		if (worker == config.length) {
			return AssignmentMethod.score(values, config);
		}
		long best = Long.MIN_VALUE;
		for (int task = 0; task < config.length; task++) {
			if (!used[task]) {
				used[task] = true;
				config[worker] = task;
				best = Math.max(best, bruteForce(values, config, used, worker + 1));
				used[task] = false;
			}
		}
		return best;
	}

	@Test
	public void matchesBruteForceOn50Random6x6() {
		for (int seed = 0; seed < 50; seed++) {
			Random random = new Random(seed);
			int[][] values = new int[6][6];
			for (int i = 0; i < 6; i++) {
				for (int j = 0; j < 6; j++) {
					values[i][j] = random.nextInt(100);
				}
			}
			int[] config = this.hungarian.solve(values);
			assertTrue("seed " + seed, Fixtures.isPermutation(config));
			assertEquals("seed " + seed, bruteForce(values, new int[6], new boolean[6], 0),
					AssignmentMethod.score(values, config));
		}
	}

	@Test
	public void publicInputsMeetTestThresholds() {
		String[] files = {"input01", "input02", "input03", "input04", "input05"};
		int[] thresholds = {110, 450, 560, 500, 525};
		for (int k = 0; k < files.length; k++) {
			int[][] map = InputManager.loadMap("inputs/public/" + files[k] + ".txt");
			Environment env = new Environment(map);
			int[] config = this.hungarian.solve(map);
			assertTrue(files[k], env.validConfiguration(config));
			assertTrue(files[k] + " scored " + env.calcScore(config), env.calcScore(config) >= thresholds[k]);
		}
	}

	@Test
	public void solvesOneByOne() {
		assertArrayEquals(new int[] {0}, this.hungarian.solve(new int[][] {{7}}));
	}

	@Test
	public void allZeroMatrixStillGivesAPermutation() {
		assertTrue(Fixtures.isPermutation(this.hungarian.solve(new int[5][5])));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNonSquare() {
		this.hungarian.solve(new int[][] {{1, 2, 3}, {4, 5, 6}});
	}
}
