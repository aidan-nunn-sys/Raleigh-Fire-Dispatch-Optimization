package edu.ncsu.csc411.ps03.dispatch.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads and writes the committed CSV files under inputs/raleigh/. Incident files
 * never contain an address column. Readers skip blank lines and "#" comments,
 * trim every value, and throw an IOException naming the file on anything unexpected.
 */
public class ScenarioIO {
	public static final ZoneId RALEIGH = ZoneId.of("America/New_York");
	public static final String INCIDENT_HEADER = "incident_id,dispatch_iso,arrive_iso,group,lat,lon,actual_station";
	public static final String STATION_HEADER = "station_id,label,lat,lon";
	public static final String OUTLINE_HEADER = "ring,lat,lon";
	private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

	private ScenarioIO() {}

	/** Epoch millis → Raleigh local time with offset, e.g. 2018-09-14T18:03:11-04:00. */
	public static String toIso(long millis) {
		return Instant.ofEpochMilli(millis).atZone(RALEIGH).format(ISO);
	}

	/** ISO-8601 with an offset (or Z) → epoch millis. */
	public static long fromIso(String iso) {
		return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
	}

	public static List<Station> readStations(String path) throws IOException {
		List<Station> stations = new ArrayList<Station>();
		for (String line : dataLines(path, STATION_HEADER)) {
			String[] f = fields(line, 4, path);
			try {
				stations.add(new Station(f[0], f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3])));
			} catch (RuntimeException e) {
				throw badRow(path, line, e);
			}
		}
		return stations;
	}

	public static void writeStations(String path, List<Station> stations) throws IOException {
		PrintWriter out = open(path);
		try {
			out.println(STATION_HEADER);
			for (Station s : stations) {
				out.printf(Locale.US, "%s,%s,%.6f,%.6f%n", safe(s.getId()), safe(s.getLabel()), s.getLat(), s.getLon());
			}
		} finally {
			out.close();
		}
	}

	/** The "# key=value" lines at the top of an incident file. */
	public static Map<String, String> readHeader(String path) throws IOException {
		Map<String, String> header = new LinkedHashMap<String, String>();
		for (String raw : Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8)) {
			String line = raw.trim();
			if (!line.startsWith("#")) {
				continue;
			}
			String body = line.substring(1).trim();
			int eq = body.indexOf('=');
			if (eq > 0) {
				header.put(body.substring(0, eq).trim(), body.substring(eq + 1).trim());
			}
		}
		return header;
	}

	public static List<Incident> readIncidents(String path) throws IOException {
		List<Incident> incidents = new ArrayList<Incident>();
		for (String line : dataLines(path, INCIDENT_HEADER)) {
			String[] f = fields(line, 7, path);
			try {
				incidents.add(new Incident(f[0], fromIso(f[1]),
						f[2].isEmpty() ? null : Long.valueOf(fromIso(f[2])),
						f[3], Double.parseDouble(f[4]), Double.parseDouble(f[5]),
						f[6].isEmpty() ? null : f[6]));
			} catch (RuntimeException e) {
				throw badRow(path, line, e);
			}
		}
		return incidents;
	}

	public static void writeIncidents(String path, String name, String window, String source,
			List<Incident> incidents) throws IOException {
		PrintWriter out = open(path);
		try {
			out.println("# name=" + name);
			out.println("# window=" + window);
			out.println("# source=" + source);
			out.println(INCIDENT_HEADER);
			for (Incident i : incidents) {
				out.printf(Locale.US, "%s,%s,%s,%s,%.3f,%.3f,%s%n",
						safe(i.getId()),
						toIso(i.getDispatchMillis()),
						i.getArriveMillis() == null ? "" : toIso(i.getArriveMillis().longValue()),
						safe(i.getGroup()),
						i.getLat(), i.getLon(),
						i.getActualStationId() == null ? "" : safe(i.getActualStationId()));
			}
		} finally {
			out.close();
		}
	}

	/** Loads an incident file as a Scenario; the name defaults to the file name. */
	public static Scenario readScenario(String path, List<Station> stations) throws IOException {
		Map<String, String> header = readHeader(path);
		String name = header.containsKey("name") ? header.get("name") : new File(path).getName();
		String window = header.containsKey("window") ? header.get("window") : "";
		return new Scenario(name, window, stations, readIncidents(path));
	}

	/** Rings in file order; each point is {lat, lon}. */
	public static List<double[][]> readOutline(String path) throws IOException {
		Map<String, List<double[]>> rings = new LinkedHashMap<String, List<double[]>>();
		for (String line : dataLines(path, OUTLINE_HEADER)) {
			String[] f = fields(line, 3, path);
			try {
				if (!rings.containsKey(f[0])) {
					rings.put(f[0], new ArrayList<double[]>());
				}
				rings.get(f[0]).add(new double[] {Double.parseDouble(f[1]), Double.parseDouble(f[2])});
			} catch (RuntimeException e) {
				throw badRow(path, line, e);
			}
		}
		List<double[][]> out = new ArrayList<double[][]>();
		for (List<double[]> ring : rings.values()) {
			out.add(ring.toArray(new double[0][]));
		}
		return out;
	}

	public static void writeOutline(String path, List<double[][]> rings) throws IOException {
		PrintWriter out = open(path);
		try {
			out.println(OUTLINE_HEADER);
			for (int r = 0; r < rings.size(); r++) {
				for (double[] point : rings.get(r)) {
					out.printf(Locale.US, "%d,%.5f,%.5f%n", r, point[0], point[1]);
				}
			}
		} finally {
			out.close();
		}
	}

	private static List<String> dataLines(String path, String expectedHeader) throws IOException {
		List<String> lines = new ArrayList<String>();
		boolean headerSeen = false;
		for (String raw : Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8)) {
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			if (!headerSeen) {
				if (!line.replace(" ", "").equals(expectedHeader)) {
					throw new IOException(path + ": expected header '" + expectedHeader + "' but found '" + line + "'");
				}
				headerSeen = true;
				continue;
			}
			lines.add(line);
		}
		if (!headerSeen) {
			throw new IOException(path + ": missing header '" + expectedHeader + "'");
		}
		return lines;
	}

	private static String[] fields(String line, int count, String path) throws IOException {
		String[] parts = line.split(",", -1);
		if (parts.length != count) {
			throw new IOException(path + ": expected " + count + " columns in '" + line + "'");
		}
		for (int i = 0; i < parts.length; i++) {
			parts[i] = parts[i].trim();
		}
		return parts;
	}

	private static IOException badRow(String path, String line, RuntimeException cause) {
		return new IOException(path + ": bad row '" + line + "' (" + cause.getMessage() + ")");
	}

	private static PrintWriter open(String path) throws IOException {
		File parent = new File(path).getAbsoluteFile().getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
			throw new IOException("Could not create " + parent);
		}
		return new PrintWriter(new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8));
	}

	/** Commas would break the columns, so they become spaces. */
	private static String safe(String s) {
		return s == null ? "" : s.replace(',', ' ').trim();
	}
}
