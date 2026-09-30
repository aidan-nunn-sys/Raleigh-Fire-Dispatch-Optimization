package edu.ncsu.csc411.ps03.dispatch.data;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import edu.ncsu.csc411.ps03.dispatch.model.Geo;
import edu.ncsu.csc411.ps03.dispatch.model.TravelModel;

/**
 * Regenerates every committed file in inputs/raleigh/ from City of Raleigh and Wake County
 * open data. This is the only network code the graders never need: run it by hand, check
 * the printed summary, and commit the results. Requests are sequential and never ask for
 * an address. Pass "outline" to regenerate only raleigh_outline.csv.
 */
public class FetchStaticData {
	static final String INCIDENTS_HISTORICAL =
			"https://services.arcgis.com/v400IkDOw1ad7Yad/arcgis/rest/services/Fire_Incidents_Public/FeatureServer/0";
	static final String INCIDENTS_PAST_MONTH =
			"https://services.arcgis.com/v400IkDOw1ad7Yad/arcgis/rest/services/Fire_Incidents_Past_Month/FeatureServer/0";
	static final String STATIONS =
			"https://services1.arcgis.com/a7CWfuGP5ZnLYE7I/arcgis/rest/services/FireStations/FeatureServer/0";
	/** The city's own dissolved corporate limits: one feature whose rings are islands and holes. */
	static final String OUTLINE =
			"https://services.arcgis.com/v400IkDOw1ad7Yad/arcgis/rest/services/CorporateLimitsDissolved/FeatureServer/1";
	static final String DIR = "inputs/raleigh/";
	static final long TWO_HOURS = 2 * 60 * 60 * 1000L;
	private static final DateTimeFormatter SQL_UTC =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

	private final ArcGisClient client = new ArcGisClient();
	private final String today = LocalDate.now(ScenarioIO.RALEIGH).toString();

	public static void main(String[] args) throws IOException {
		if (args.length > 0 && "outline".equals(args[0])) {
			new FetchStaticData().fetchOutline();
		} else {
			new FetchStaticData().run();
		}
	}

	void run() throws IOException {
		List<Station> stations = fetchStations();
		ScenarioIO.writeStations(DIR + "stations.csv", stations);
		System.out.println("stations.csv: " + stations.size() + " stations");

		fetchOutline();

		Set<String> known = new HashSet<String>();
		for (Station s : stations) {
			known.add(s.getId());
		}
		writeBusiest("florence_peak.csv", "Florence peak", local(2018, 9, 13, 0), local(2018, 9, 18, 0), known);
		writeBusiest("helene_peak.csv", "Helene peak", local(2024, 9, 26, 0), local(2024, 9, 29, 0), known);
		writeFixed("typical_weekday.csv", "Typical weekday", local(2019, 3, 12, 17), local(2019, 3, 12, 19), known);
		writeFixed("florence_day.csv", "Florence day", local(2018, 9, 14, 0), local(2018, 9, 15, 0), known);

		fitTravelModel(stations, known);
	}

	private List<Station> fetchStations() throws IOException {
		List<Station> stations = new ArrayList<Station>();
		for (Map<String, Object> feature : this.client.queryAll(STATIONS, "AGENCY='RF'", "STATIONID,LABEL", "")) {
			Map<String, Object> attrs = ArcGisClient.attributes(feature);
			Map<String, Object> geom = ArcGisClient.geometry(feature);
			if (geom == null || attrs.get("STATIONID") == null) {
				continue;
			}
			stations.add(new Station(attrs.get("STATIONID").toString().trim(), String.valueOf(attrs.get("LABEL")).trim(),
					((Number) geom.get("y")).doubleValue(), ((Number) geom.get("x")).doubleValue()));
		}
		Collections.sort(stations, new Comparator<Station>() {
			@Override
			public int compare(Station a, Station b) {
				return a.getId().compareTo(b.getId());
			}
		});
		if (stations.size() != 28) {
			System.out.println("WARNING: expected 28 RFD stations, got " + stations.size());
		}
		return stations;
	}

	@SuppressWarnings("unchecked")
	private void fetchOutline() throws IOException {
		List<double[][]> rings = new ArrayList<double[][]>();
		int points = 0;
		for (Map<String, Object> feature : this.client.queryAll(OUTLINE, "1=1", "OBJECTID",
				"&maxAllowableOffset=0.0005")) {
			Map<String, Object> geom = ArcGisClient.geometry(feature);
			if (geom == null || !(geom.get("rings") instanceof List)) {
				continue;
			}
			for (Object ringObject : (List<Object>) geom.get("rings")) {
				List<Object> ring = (List<Object>) ringObject;
				double[][] ringPoints = new double[ring.size()][2];
				double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
				double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
				for (int i = 0; i < ring.size(); i++) {
					List<Object> xy = (List<Object>) ring.get(i);
					ringPoints[i][0] = ((Number) xy.get(1)).doubleValue();
					ringPoints[i][1] = ((Number) xy.get(0)).doubleValue();
					minLat = Math.min(minLat, ringPoints[i][0]);
					maxLat = Math.max(maxLat, ringPoints[i][0]);
					minLon = Math.min(minLon, ringPoints[i][1]);
					maxLon = Math.max(maxLon, ringPoints[i][1]);
				}
				if (maxLat - minLat >= 0.005 || maxLon - minLon >= 0.005) {
					rings.add(ringPoints);
					points += ringPoints.length;
				}
			}
		}
		ScenarioIO.writeOutline(DIR + "raleigh_outline.csv", rings);
		System.out.println("raleigh_outline.csv: " + rings.size() + " rings, " + points + " points"
				+ (points > 3000 ? " (WARNING: above the 3,000-point target)" : ""));
	}

	private List<Incident> fetchHistorical(long fromMillis, long toMillis, Set<String> known) throws IOException {
		String where = "dispatch_date_time>=TIMESTAMP '" + SQL_UTC.format(Instant.ofEpochMilli(fromMillis))
				+ "' AND dispatch_date_time<TIMESTAMP '" + SQL_UTC.format(Instant.ofEpochMilli(toMillis)) + "'";
		List<Incident> incidents = new ArrayList<Incident>();
		for (Map<String, Object> feature : this.client.queryAll(INCIDENTS_HISTORICAL, where,
				IncidentParser.HISTORICAL_FIELDS, "&orderByFields=OBJECTID")) {
			Incident incident = IncidentParser.fromFeature(feature, known);
			if (incident != null) {
				incidents.add(incident);
			}
		}
		Collections.sort(incidents, Scenario.DISPATCH_ORDER);
		return incidents;
	}

	private void writeBusiest(String file, String name, long from, long to, Set<String> known) throws IOException {
		List<Incident> all = fetchHistorical(from, to, known);
		List<Long> starts = WindowFinder.busiestWindows(all, from, to, TWO_HOURS, 1);
		if (starts.isEmpty()) {
			throw new IOException("No incidents found for " + name);
		}
		long start = starts.get(0).longValue();
		List<Incident> inWindow = new ArrayList<Incident>();
		for (Incident incident : all) {
			if (incident.getDispatchMillis() >= start && incident.getDispatchMillis() < start + TWO_HOURS) {
				inWindow.add(incident);
			}
		}
		writeWindow(file, name, start, start + TWO_HOURS, inWindow);
	}

	private void writeFixed(String file, String name, long from, long to, Set<String> known) throws IOException {
		writeWindow(file, name, from, to, fetchHistorical(from, to, known));
	}

	private void writeWindow(String file, String name, long from, long to, List<Incident> incidents) throws IOException {
		String window = ScenarioIO.toIso(from) + "/" + ScenarioIO.toIso(to);
		ScenarioIO.writeIncidents(DIR + "scenarios/" + file, name, window,
				"Fire_Incidents_Public fetched " + this.today, incidents);
		int matched = 0;
		for (Incident incident : incidents) {
			if (incident.getActualStationId() != null) {
				matched++;
			}
		}
		System.out.printf("scenarios/%s: %d incidents, %d with a published station (%s)%n",
				file, incidents.size(), matched, window);
	}

	private void fitTravelModel(List<Station> stations, Set<String> known) throws IOException {
		Map<String, Station> byId = new HashMap<String, Station>();
		for (Station s : stations) {
			byId.put(s.getId(), s);
		}
		double[][] fitSample = responseSamples(fetchHistorical(local(2019, 1, 1, 0), local(2020, 1, 1, 0), known), byId);
		TravelModel.Fit fit = TravelModel.fit(fitSample[0], fitSample[1]);

		List<Incident> current = new ArrayList<Incident>();
		for (Map<String, Object> feature : this.client.queryAll(INCIDENTS_PAST_MONTH, "1=1",
				IncidentParser.PAST_MONTH_FIELDS, "&orderByFields=OBJECTID")) {
			Incident incident = IncidentParser.fromFeature(feature, known);
			if (incident != null) {
				current.add(incident);
			}
		}
		double[][] check = responseSamples(current, byId);
		int checkSize = check[0].length;

		PrintWriter out = new PrintWriter(new OutputStreamWriter(
				new FileOutputStream(DIR + "travel_model.properties"), StandardCharsets.UTF_8));
		try {
			out.println("# Fitted by FetchStaticData on " + this.today
					+ ": seconds = TURNOUT_SECONDS + SECONDS_PER_MILE * straight-line miles");
			out.println("# Sample: 2019 historical incidents with a known station and arrive time, 60-1800 s response");
			out.printf(Locale.US, "TURNOUT_SECONDS=%.2f%n", fit.model.getTurnoutSeconds());
			out.printf(Locale.US, "SECONDS_PER_MILE=%.2f%n", fit.model.getSecondsPerMile());
			out.printf(Locale.US, "SAMPLE_SIZE=%d%n", fit.sampleSize);
			out.printf(Locale.US, "R_SQUARED=%.4f%n", fit.rSquared);
			out.println("FIT_FROM=2019-01-01");
			out.println("FIT_TO=2019-12-31");
			out.println("# Check: the 2019 model applied to the current past-month feed (same filters)");
			out.println("CHECK_SOURCE=Fire_Incidents_Past_Month fetched " + this.today);
			out.printf(Locale.US, "CHECK_SAMPLE_SIZE=%d%n", checkSize);
			if (checkSize > 0) {
				out.printf(Locale.US, "CHECK_MAE_SECONDS=%.1f%n", fit.model.meanAbsoluteError(check[0], check[1]));
				out.printf(Locale.US, "CHECK_BIAS_SECONDS=%.1f%n", fit.model.meanBias(check[0], check[1]));
			}
		} finally {
			out.close();
		}
		System.out.printf(Locale.US, "travel_model.properties: turnout %.1f s + %.1f s/mile, n=%d, R^2=%.3f; check n=%d%n",
				fit.model.getTurnoutSeconds(), fit.model.getSecondsPerMile(), fit.sampleSize, fit.rSquared, checkSize);
	}

	/** {miles[], seconds[]} for incidents with a known station and arrive time, keeping 60–1800 s responses. */
	static double[][] responseSamples(List<Incident> incidents, Map<String, Station> byId) {
		List<double[]> pairs = new ArrayList<double[]>();
		for (Incident incident : incidents) {
			Station station = incident.getActualStationId() == null ? null : byId.get(incident.getActualStationId());
			if (station == null || incident.getArriveMillis() == null) {
				continue;
			}
			double seconds = (incident.getArriveMillis().longValue() - incident.getDispatchMillis()) / 1000.0;
			if (seconds < 60 || seconds > 1800) {
				continue;
			}
			pairs.add(new double[] {
				Geo.miles(station.getLat(), station.getLon(), incident.getLat(), incident.getLon()), seconds});
		}
		double[][] out = new double[2][pairs.size()];
		for (int k = 0; k < pairs.size(); k++) {
			out[0][k] = pairs.get(k)[0];
			out[1][k] = pairs.get(k)[1];
		}
		return out;
	}

	private static long local(int year, int month, int day, int hour) {
		return LocalDateTime.of(year, month, day, hour, 0).atZone(ScenarioIO.RALEIGH).toInstant().toEpochMilli();
	}
}
