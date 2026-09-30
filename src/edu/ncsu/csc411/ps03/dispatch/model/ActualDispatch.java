package edu.ncsu.csc411.ps03.dispatch.model;

import java.util.HashMap;
import java.util.Map;

/**
 * The station Raleigh actually sent to each incident. This is context, not a score: real
 * dispatch also weighs unit availability, apparatus type, and coverage. Stations may
 * repeat. Incidents with no published station are unmatched; Raleigh left the field
 * blank from May 2021 through 2025.
 */
public class ActualDispatch {
	private final int[] workerByTask; // -1 when unmatched
	private final int unmatched;

	public ActualDispatch(DispatchMatrix matrix) {
		Map<String, Integer> index = new HashMap<String, Integer>();
		for (int worker = 0; worker < matrix.size(); worker++) {
			index.put(matrix.station(worker).getId(), Integer.valueOf(worker));
		}
		this.workerByTask = new int[matrix.realTaskCount()];
		int missing = 0;
		for (int task = 0; task < this.workerByTask.length; task++) {
			String id = matrix.incident(task).getActualStationId();
			Integer worker = id == null ? null : index.get(id);
			this.workerByTask[task] = worker == null ? -1 : worker.intValue();
			if (worker == null) {
				missing++;
			}
		}
		this.unmatched = missing;
	}

	/** The worker (station index) that responded to a real task, or -1. */
	public int workerFor(int task) { return this.workerByTask[task]; }
	public int unmatchedCount() { return this.unmatched; }
	public int realTaskCount() { return this.workerByTask.length; }

	/** False when no incident in the window has a published station. */
	public boolean isAvailable() { return this.unmatched < this.workerByTask.length; }
}
