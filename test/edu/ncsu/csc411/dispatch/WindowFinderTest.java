package edu.ncsu.csc411.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import edu.ncsu.csc411.ps03.dispatch.data.Incident;
import edu.ncsu.csc411.ps03.dispatch.data.WindowFinder;

public class WindowFinderTest {
	private static final long MIN = 60000L;
	private static final long HOUR = 60 * MIN;

	/** Three calls near minute 0 and four calls at minutes 200-203, all relative to BASE_MILLIS. */
	private static List<Incident> twoClusters() {
		List<Incident> incidents = new ArrayList<Incident>();
		for (int minute : new int[] {0, 5, 10, 200, 201, 202, 203}) {
			incidents.add(Fixtures.incident("I" + minute, minute, 35.78, -78.65, null));
		}
		return incidents;
	}

	@Test
	public void busiestWindowIsEarliestStartThatHoldsTheCluster() {
		long base = Fixtures.BASE_MILLIS;
		List<Long> starts = WindowFinder.busiestWindows(twoClusters(), base, base + 300 * MIN, HOUR, 1);
		// [144, 204) is the first 60-minute window that contains minutes 200..203 (end is exclusive).
		assertEquals(Arrays.asList(Long.valueOf(base + 144 * MIN)), starts);
	}

	@Test
	public void laterWindowsDoNotOverlapAndSkipEmptyOnes() {
		long base = Fixtures.BASE_MILLIS;
		List<Long> starts = WindowFinder.busiestWindows(twoClusters(), base, base + 300 * MIN, HOUR, 5);
		assertEquals(Arrays.asList(Long.valueOf(base + 144 * MIN), Long.valueOf(base)), starts);
	}

	@Test
	public void countIsHalfOpen() {
		long base = Fixtures.BASE_MILLIS;
		assertEquals(3, WindowFinder.countInWindow(twoClusters(), base + 143 * MIN, HOUR));
		assertEquals(4, WindowFinder.countInWindow(twoClusters(), base + 144 * MIN, HOUR));
	}

	@Test
	public void emptyInputOrShortRangeGivesNoWindows() {
		long base = Fixtures.BASE_MILLIS;
		assertTrue(WindowFinder.busiestWindows(new ArrayList<Incident>(), base, base + 300 * MIN, HOUR, 5).isEmpty());
		assertTrue(WindowFinder.busiestWindows(twoClusters(), base, base + 30 * MIN, HOUR, 5).isEmpty());
	}
}
