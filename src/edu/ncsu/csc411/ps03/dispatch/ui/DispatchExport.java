package edu.ncsu.csc411.ps03.dispatch.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import edu.ncsu.csc411.ps03.dispatch.data.Incident;
import edu.ncsu.csc411.ps03.dispatch.model.ActualDispatch;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchMatrix;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchMetrics;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchSession;

/**
 * Writes the window's current scenario to outputs/dispatch_<scenario>.csv. There is one row per
 * incident, with each method's station and estimated minutes, then a blank line and a summary
 * table. The SA columns use SA's best configuration so far.
 */
public class DispatchExport {
	public static final String HEADER = "incident_id,group,sa_station,sa_min,opt_station,opt_min,"
			+ "greedy_station,greedy_min,actual_station,actual_min";
	public static final String SUMMARY_HEADER = "method,incidents,total_min,avg_min,worst_min,gap_to_optimal";

	private DispatchExport() {}

	/** "Florence peak" → outputs/dispatch_florence_peak.csv. */
	public static String fileName(String scenarioName) {
		String slug = scenarioName.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
		return "outputs/dispatch_" + (slug.isEmpty() ? "scenario" : slug) + ".csv";
	}

	public static void write(String path, DispatchSession session) throws IOException {
		File parent = new File(path).getAbsoluteFile().getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
			throw new IOException("Could not create " + parent);
		}
		DispatchMatrix m = session.getMatrix();
		ActualDispatch actual = session.getActual();
		int[] sa = session.getBestConfiguration();
		int[] optimal = session.getOptimalConfiguration();
		int[] greedy = session.getGreedyConfiguration();
		PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8));
		try {
			out.println(HEADER);
			for (int task = 0; task < m.realTaskCount(); task++) {
				Incident incident = m.incident(task);
				out.println(incident.getId() + "," + incident.getGroup()
						+ cell(m, task, DispatchSession.workerForTask(sa, task))
						+ cell(m, task, DispatchSession.workerForTask(optimal, task))
						+ cell(m, task, DispatchSession.workerForTask(greedy, task))
						+ cell(m, task, actual.workerFor(task)));
			}
			out.println();
			out.println(SUMMARY_HEADER);
			DispatchMetrics best = session.getOptimalMetrics();
			summary(out, "SA best", session.getBestMetrics(), best, true);
			summary(out, "Optimal", best, best, true);
			summary(out, "Greedy", session.getGreedyMetrics(), best, true);
			if (actual.isAvailable()) {
				// With unmatched incidents Actual covers fewer calls, so a gap would mislead.
				summary(out, "Actual", session.getActualMetrics(), best, actual.unmatchedCount() == 0);
			} else {
				out.println("Actual,0,NA,NA,NA,NA");
			}
		} finally {
			out.close();
		}
	}

	private static String cell(DispatchMatrix m, int task, int worker) {
		if (worker < 0) {
			return ",,";
		}
		return String.format(Locale.US, ",%s,%.2f", m.station(worker).getId(), m.estimateSeconds(task, worker) / 60.0);
	}

	private static void summary(PrintWriter out, String method, DispatchMetrics metrics, DispatchMetrics optimal,
			boolean withGap) {
		out.printf(Locale.US, "%s,%d,%.2f,%.2f,%.2f,%s%n", method, metrics.getCount(), metrics.getTotalMinutes(),
				metrics.getAverageMinutes(), metrics.getWorstMinutes(),
				withGap ? String.format(Locale.US, "%.4f", metrics.gapTo(optimal)) : "NA");
	}
}
