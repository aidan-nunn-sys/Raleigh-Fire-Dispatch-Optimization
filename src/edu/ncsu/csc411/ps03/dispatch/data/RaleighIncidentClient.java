package edu.ncsu.csc411.ps03.dispatch.data;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The data behind the "Fetch latest" button: the Fire_Incidents_Past_Month feed (a rolling
 * 30 days). It only makes requests when asked. It keeps the result in a git-ignored cache for
 * the configured TTL, and falls back to that cache (at any age) when the network fails.
 */
public class RaleighIncidentClient {
	public static final String DEFAULT_CACHE = "inputs/raleigh/cache/latest.csv";
	static final String SOURCE_PREFIX = "Fire_Incidents_Past_Month fetched ";
	private static final DateTimeFormatter START =
			DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.US).withZone(ScenarioIO.RALEIGH);
	private static final DateTimeFormatter END =
			DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(ScenarioIO.RALEIGH);

	/** What load() found. */
	public static class Result {
		public final List<Incident> incidents;
		public final long fetchedAtMillis;
		public final boolean fromCache;
		/** Why the cache was used or saving failed; null after a clean fresh fetch. */
		public final String message;

		Result(List<Incident> incidents, long fetchedAtMillis, boolean fromCache, String message) {
			this.incidents = Collections.unmodifiableList(incidents);
			this.fetchedAtMillis = fetchedAtMillis;
			this.fromCache = fromCache;
			this.message = message;
		}
	}

	private final ArcGisClient client;
	private final String cachePath;
	private final Set<String> knownStationIds;
	private final long ttlMillis;

	public RaleighIncidentClient(ArcGisClient client, String cachePath, Set<String> knownStationIds, int ttlMinutes) {
		this.client = client;
		this.cachePath = cachePath;
		this.knownStationIds = knownStationIds;
		this.ttlMillis = ttlMinutes * 60000L;
	}

	/**
	 * Uses a cache younger than the TTL without any request. Otherwise it fetches the feed and
	 * rewrites the cache. When the fetch fails it returns the cache at any age, or throws
	 * when there is none.
	 */
	public Result load() throws IOException {
		Result cached = readCache();
		long now = now();
		if (cached != null && now - cached.fetchedAtMillis < this.ttlMillis) {
			return new Result(cached.incidents, cached.fetchedAtMillis, true,
					"Using the copy fetched " + describeAge(now - cached.fetchedAtMillis) + ".");
		}
		List<Incident> fresh;
		try {
			fresh = fetch();
		} catch (IOException e) {
			if (cached == null) {
				throw e;
			}
			return new Result(cached.incidents, cached.fetchedAtMillis, true, "Fetch failed (" + e.getMessage()
					+ "); using the copy fetched " + describeAge(now - cached.fetchedAtMillis) + ".");
		}
		try {
			writeCache(fresh, now);
		} catch (IOException e) {
			return new Result(fresh, now, false, "Fetched, but could not save the cache: " + e.getMessage());
		}
		return new Result(fresh, now, false, null);
	}

	/** The clock; tests override it. */
	protected long now() {
		return System.currentTimeMillis();
	}

	private List<Incident> fetch() throws IOException {
		List<Incident> incidents = new ArrayList<Incident>();
		for (Map<String, Object> feature : this.client.queryAll(FetchStaticData.INCIDENTS_PAST_MONTH, "1=1",
				IncidentParser.PAST_MONTH_FIELDS, "&orderByFields=OBJECTID")) {
			Incident incident = IncidentParser.fromFeature(feature, this.knownStationIds);
			if (incident != null) {
				incidents.add(incident);
			}
		}
		Collections.sort(incidents, Scenario.DISPATCH_ORDER);
		return incidents;
	}

	private void writeCache(List<Incident> incidents, long fetchedAt) throws IOException {
		String window = incidents.isEmpty() ? "" : ScenarioIO.toIso(incidents.get(0).getDispatchMillis()) + "/"
				+ ScenarioIO.toIso(incidents.get(incidents.size() - 1).getDispatchMillis());
		ScenarioIO.writeIncidents(this.cachePath, "Latest (live)", window,
				SOURCE_PREFIX + ScenarioIO.toIso(fetchedAt), incidents);
	}

	/** The cached copy, or null when it is missing or unreadable. */
	private Result readCache() {
		if (!new File(this.cachePath).isFile()) {
			return null;
		}
		try {
			String source = ScenarioIO.readHeader(this.cachePath).get("source");
			if (source == null || !source.startsWith(SOURCE_PREFIX)) {
				return null;
			}
			long fetchedAt = ScenarioIO.fromIso(source.substring(SOURCE_PREFIX.length()).trim());
			return new Result(ScenarioIO.readIncidents(this.cachePath), fetchedAt, true, null);
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/**
	 * Up to k of the busiest non-overlapping windows as Scenarios, busiest first. Each one
	 * keeps at most one incident per station; Scenario records a warning when it trims.
	 */
	public static List<Scenario> busiestScenarios(List<Incident> incidents, List<Station> stations, long windowMillis, int k) {
		List<Scenario> scenarios = new ArrayList<Scenario>();
		if (incidents.isEmpty()) {
			return scenarios;
		}
		long first = Long.MAX_VALUE, last = Long.MIN_VALUE;
		for (Incident incident : incidents) {
			first = Math.min(first, incident.getDispatchMillis());
			last = Math.max(last, incident.getDispatchMillis());
		}
		long from = first - Math.floorMod(first, WindowFinder.STEP_MILLIS);
		long to = last - Math.floorMod(last, WindowFinder.STEP_MILLIS) + windowMillis;
		for (Long startObject : WindowFinder.busiestWindows(incidents, from, to, windowMillis, k)) {
			long start = startObject.longValue();
			List<Incident> inWindow = new ArrayList<Incident>();
			for (Incident incident : incidents) {
				if (incident.getDispatchMillis() >= start && incident.getDispatchMillis() < start + windowMillis) {
					inWindow.add(incident);
				}
			}
			String name = String.format(Locale.US, "Latest %s-%s (%d)", START.format(Instant.ofEpochMilli(start)),
					END.format(Instant.ofEpochMilli(start + windowMillis)), inWindow.size());
			scenarios.add(new Scenario(name, ScenarioIO.toIso(start) + "/" + ScenarioIO.toIso(start + windowMillis),
					stations, inWindow));
		}
		return scenarios;
	}

	/** "just now", "12 min ago", or "3 h ago". */
	public static String describeAge(long ageMillis) {
		long minutes = Math.max(0L, ageMillis) / 60000L;
		if (minutes < 1) {
			return "just now";
		}
		if (minutes < 120) {
			return minutes + " min ago";
		}
		return (minutes / 60) + " h ago";
	}
}
