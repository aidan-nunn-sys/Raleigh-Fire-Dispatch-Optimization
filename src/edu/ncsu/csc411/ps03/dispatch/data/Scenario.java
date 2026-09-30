package edu.ncsu.csc411.ps03.dispatch.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * One replay window: the stations plus the incidents treated as simultaneous.
 * Incidents are kept in dispatch order, and any beyond the station count are
 * dropped with a warning so the assignment stays square.
 */
public class Scenario {
	/** Dispatch time, then incident id, so ties always sort the same way. */
	public static final Comparator<Incident> DISPATCH_ORDER = new Comparator<Incident>() {
		@Override
		public int compare(Incident a, Incident b) {
			int byTime = Long.compare(a.getDispatchMillis(), b.getDispatchMillis());
			return byTime != 0 ? byTime : a.getId().compareTo(b.getId());
		}
	};

	private final String name;
	private final String window;
	private final List<Station> stations;
	private final List<Incident> incidents;
	private final List<String> warnings;

	public Scenario(String name, String window, List<Station> stations, List<Incident> incidents) {
		this.name = name;
		this.window = window;
		this.stations = Collections.unmodifiableList(new ArrayList<Station>(stations));
		List<Incident> sorted = new ArrayList<Incident>(incidents);
		Collections.sort(sorted, DISPATCH_ORDER);
		List<String> notes = new ArrayList<String>();
		if (sorted.size() > stations.size()) {
			notes.add(String.format("Kept the first %d of %d incidents by dispatch time.", stations.size(), sorted.size()));
			sorted = new ArrayList<Incident>(sorted.subList(0, stations.size()));
		}
		if (sorted.isEmpty()) {
			notes.add("No incidents in this window.");
		}
		this.incidents = Collections.unmodifiableList(sorted);
		this.warnings = Collections.unmodifiableList(notes);
	}

	public String getName() { return this.name; }
	public String getWindow() { return this.window; }
	public List<Station> getStations() { return this.stations; }
	public List<Incident> getIncidents() { return this.incidents; }
	public List<String> getWarnings() { return this.warnings; }
}
