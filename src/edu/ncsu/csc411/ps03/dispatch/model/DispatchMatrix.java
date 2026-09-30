package edu.ncsu.csc411.ps03.dispatch.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.ncsu.csc411.ps03.dispatch.data.Incident;
import edu.ncsu.csc411.ps03.dispatch.data.Scenario;
import edu.ncsu.csc411.ps03.dispatch.data.Station;

/**
 * Turns stations and incidents into a square PS03 value matrix, values[task][worker].
 * Workers are stations in list order. Tasks are the incidents in list order, followed
 * by all-zero "stay home" rows, so the matrix is always stations × stations. A value is
 * MAX_SECONDS minus the estimated response seconds, floored at 0, so higher is better.
 */
public class DispatchMatrix {
	public static final int MAX_SECONDS = 3600;

	private final List<Station> stations;
	private final List<Incident> incidents;
	private final int[][] seconds; // [task][worker], real tasks only
	private final int[][] values;

	public DispatchMatrix(Scenario scenario, TravelModel model) {
		this(scenario.getStations(), scenario.getIncidents(), model);
	}

	public DispatchMatrix(List<Station> stations, List<Incident> incidents, TravelModel model) {
		if (stations.isEmpty()) {
			throw new IllegalArgumentException("No stations");
		}
		if (incidents.size() > stations.size()) {
			throw new IllegalArgumentException("More incidents (" + incidents.size()
					+ ") than stations (" + stations.size() + ")");
		}
		this.stations = Collections.unmodifiableList(new ArrayList<Station>(stations));
		this.incidents = Collections.unmodifiableList(new ArrayList<Incident>(incidents));
		int n = stations.size();
		this.seconds = new int[incidents.size()][n];
		this.values = new int[n][n];
		for (int task = 0; task < incidents.size(); task++) {
			for (int worker = 0; worker < n; worker++) {
				this.seconds[task][worker] = model.estimateSeconds(stations.get(worker), incidents.get(task));
				this.values[task][worker] = Math.max(0, MAX_SECONDS - this.seconds[task][worker]);
			}
		}
	}

	public int size() { return this.values.length; }
	public int realTaskCount() { return this.incidents.size(); }
	public boolean isReal(int task) { return task < this.incidents.size(); }
	/** Defined for real tasks only ({@link #isReal(int)}); dummy tasks have no estimate. */
	public int estimateSeconds(int task, int worker) { return this.seconds[task][worker]; }
	public Station station(int worker) { return this.stations.get(worker); }
	public Incident incident(int task) { return this.incidents.get(task); }
	public List<Station> getStations() { return this.stations; }
	public List<Incident> getIncidents() { return this.incidents; }

	/** A fresh deep copy of values[task][worker], safe for the caller to modify. */
	public int[][] getValues() {
		int[][] copy = new int[this.values.length][];
		for (int i = 0; i < this.values.length; i++) {
			copy[i] = this.values[i].clone();
		}
		return copy;
	}
}
