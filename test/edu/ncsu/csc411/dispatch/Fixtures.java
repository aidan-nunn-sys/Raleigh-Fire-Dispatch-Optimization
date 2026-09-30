package edu.ncsu.csc411.dispatch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.ncsu.csc411.ps03.dispatch.data.Incident;
import edu.ncsu.csc411.ps03.dispatch.data.IncidentParser;
import edu.ncsu.csc411.ps03.dispatch.data.Station;

/** Shared, network-free test data for the dispatch tests. */
public class Fixtures {
	/** 2018-09-14T18:00:00-04:00 */
	public static final long BASE_MILLIS = 1536962400000L;

	private Fixtures() {}

	/** The 28 real RFD station ids: S01 through S29 with no S13. */
	public static Set<String> knownStationIds() {
		Set<String> ids = new HashSet<String>();
		for (int i = 1; i <= 29; i++) {
			if (i != 13) {
				ids.add(String.format("S%02d", i));
			}
		}
		return ids;
	}

	/** n synthetic stations S01..Snn on an east-west line, 0.01 degrees of longitude apart. */
	public static List<Station> stationsAlongLine(int n) {
		List<Station> stations = new ArrayList<Station>();
		for (int i = 0; i < n; i++) {
			stations.add(new Station(String.format("S%02d", i + 1), "RFD #" + (i + 1), 35.78, -78.70 + 0.01 * i));
		}
		return stations;
	}

	/** A Fire incident dispatched `minute` minutes after BASE_MILLIS, arriving 5 minutes later. */
	public static Incident incident(String id, int minute, double lat, double lon, String actual) {
		long dispatch = BASE_MILLIS + minute * 60000L;
		return new Incident(id, dispatch, Long.valueOf(dispatch + 300000L), IncidentParser.FIRE, lat, lon, actual);
	}

	/** True when config holds each of 0..n-1 exactly once. */
	public static boolean isPermutation(int[] config) {
		boolean[] seen = new boolean[config.length];
		for (int task : config) {
			if (task < 0 || task >= config.length || seen[task]) {
				return false;
			}
			seen[task] = true;
		}
		return true;
	}
}
