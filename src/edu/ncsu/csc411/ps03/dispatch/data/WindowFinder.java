package edu.ncsu.csc411.ps03.dispatch.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Finds the busiest stretches of dispatch activity with a 1-minute sliding window. */
public class WindowFinder {
	public static final long STEP_MILLIS = 60000L;

	private WindowFinder() {}

	/**
	 * Up to k window start times in [from, to − window], busiest first and never overlapping
	 * one another. Ties go to the earlier start. Windows with no incidents are never returned.
	 */
	public static List<Long> busiestWindows(List<Incident> incidents, long from, long to, long windowMillis, int k) {
		List<Long> chosen = new ArrayList<Long>();
		if (to - windowMillis < from) {
			return chosen;
		}
		long[] times = sortedTimes(incidents);
		int steps = (int) ((to - windowMillis - from) / STEP_MILLIS) + 1;
		int[] counts = new int[steps];
		for (int s = 0; s < steps; s++) {
			long start = from + s * STEP_MILLIS;
			counts[s] = lowerBound(times, start + windowMillis) - lowerBound(times, start);
		}
		while (chosen.size() < k) {
			int best = -1;
			for (int s = 0; s < steps; s++) {
				if (counts[s] > 0 && (best < 0 || counts[s] > counts[best])
						&& !overlaps(from + s * STEP_MILLIS, chosen, windowMillis)) {
					best = s;
				}
			}
			if (best < 0) {
				break;
			}
			chosen.add(Long.valueOf(from + best * STEP_MILLIS));
		}
		return chosen;
	}

	/** Incidents dispatched in [start, start + windowMillis). */
	public static int countInWindow(List<Incident> incidents, long start, long windowMillis) {
		long[] times = sortedTimes(incidents);
		return lowerBound(times, start + windowMillis) - lowerBound(times, start);
	}

	private static boolean overlaps(long start, List<Long> chosen, long windowMillis) {
		for (Long other : chosen) {
			if (Math.abs(start - other.longValue()) < windowMillis) {
				return true;
			}
		}
		return false;
	}

	private static long[] sortedTimes(List<Incident> incidents) {
		long[] times = new long[incidents.size()];
		for (int i = 0; i < times.length; i++) {
			times[i] = incidents.get(i).getDispatchMillis();
		}
		Arrays.sort(times);
		return times;
	}

	/** The first index whose time is >= key. */
	private static int lowerBound(long[] times, long key) {
		int lo = 0, hi = times.length;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (times[mid] < key) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		return lo;
	}
}
