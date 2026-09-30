package edu.ncsu.csc411.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.ncsu.csc411.ps03.dispatch.data.ArcGisClient;
import edu.ncsu.csc411.ps03.dispatch.data.Incident;
import edu.ncsu.csc411.ps03.dispatch.data.RaleighIncidentClient;
import edu.ncsu.csc411.ps03.dispatch.data.Scenario;
import edu.ncsu.csc411.ps03.dispatch.data.ScenarioIO;

public class RaleighIncidentClientTest {
	private static final long NOW = 1790000000000L;
	private static final long MINUTE = 60000L;

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();
	private String cachePath;
	private String pastMonthJson;

	@Before
	public void setUp() throws IOException {
		// The cache directory does not exist yet: load() has to create it.
		this.cachePath = new File(this.tmp.getRoot(), "cache/latest.csv").getPath();
		this.pastMonthJson = new String(Files.readAllBytes(Paths.get("test/resources/raleigh_pastmonth_sample.json")),
				StandardCharsets.UTF_8);
	}

	/** Serves the past-month fixture on the first page (null = offline) and counts requests. */
	private static ArcGisClient fakeArcGis(final String firstPage, final int[] calls) {
		return new ArcGisClient() {
			@Override
			protected String get(String url) throws IOException {
				calls[0]++;
				if (firstPage == null) {
					throw new IOException("offline");
				}
				return url.contains("resultOffset=0&") ? firstPage : "{\"features\":[]}";
			}
		};
	}

	private RaleighIncidentClient client(ArcGisClient arcGis, final long now) {
		return new RaleighIncidentClient(arcGis, this.cachePath, Fixtures.knownStationIds(), 60) {
			@Override
			protected long now() {
				return now;
			}
		};
	}

	@Test
	public void fetchesParsesAndWritesCache() throws IOException {
		int[] calls = {0};
		RaleighIncidentClient.Result r = client(fakeArcGis(this.pastMonthJson, calls), NOW).load();
		assertFalse(r.fromCache);
		assertEquals(NOW, r.fetchedAtMillis);
		assertEquals(3, r.incidents.size());
		assertEquals("S16", r.incidents.get(0).getActualStationId());
		String cache = new String(Files.readAllBytes(Paths.get(this.cachePath)), StandardCharsets.UTF_8);
		assertTrue(cache.contains("# source=Fire_Incidents_Past_Month fetched "));
		assertFalse(cache.toLowerCase().contains("address"));
	}

	@Test
	public void freshCacheSkipsTheNetwork() throws IOException {
		client(fakeArcGis(this.pastMonthJson, new int[1]), NOW).load();
		int[] calls = {0};
		RaleighIncidentClient.Result r = client(fakeArcGis(this.pastMonthJson, calls), NOW + 10 * MINUTE).load();
		assertEquals(0, calls[0]);
		assertTrue(r.fromCache);
		assertEquals(3, r.incidents.size());
		assertTrue(r.message, r.message.contains("10 min ago"));
	}

	@Test
	public void staleCacheIsRefetched() throws IOException {
		client(fakeArcGis(this.pastMonthJson, new int[1]), NOW).load();
		int[] calls = {0};
		RaleighIncidentClient.Result r = client(fakeArcGis(this.pastMonthJson, calls), NOW + 61 * MINUTE).load();
		assertTrue(calls[0] > 0);
		assertFalse(r.fromCache);
		assertEquals(NOW + 61 * MINUTE, r.fetchedAtMillis);
	}

	@Test
	public void failedFetchFallsBackToStaleCache() throws IOException {
		client(fakeArcGis(this.pastMonthJson, new int[1]), NOW).load();
		RaleighIncidentClient.Result r = client(fakeArcGis(null, new int[1]), NOW + 5 * 60 * MINUTE).load();
		assertTrue(r.fromCache);
		assertEquals(NOW, r.fetchedAtMillis);
		assertEquals(3, r.incidents.size());
		assertTrue(r.message, r.message.contains("Fetch failed"));
	}

	@Test
	public void offlineWithoutCacheThrows() {
		try {
			client(fakeArcGis(null, new int[1]), NOW).load();
			fail("expected IOException");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("offline"));
		}
	}

	@Test
	public void corruptCacheIsIgnored() throws IOException {
		new File(this.cachePath).getParentFile().mkdirs();
		Files.write(Paths.get(this.cachePath), "not,a,cache\n\u0000garbage".getBytes(StandardCharsets.UTF_8));
		RaleighIncidentClient.Result r = client(fakeArcGis(this.pastMonthJson, new int[1]), NOW).load();
		assertFalse(r.fromCache);
		assertEquals(3, r.incidents.size());
		assertEquals(3, ScenarioIO.readIncidents(this.cachePath).size());
	}

	@Test
	public void busiestScenariosAreNonOverlappingAndCapped() {
		List<Incident> incidents = new ArrayList<Incident>();
		for (int minute = 0; minute < 30; minute++) {
			incidents.add(Fixtures.incident("A" + minute, minute, 35.78, -78.65, null));
		}
		incidents.add(Fixtures.incident("B1", 300, 35.78, -78.65, null));
		incidents.add(Fixtures.incident("B2", 301, 35.78, -78.65, null));
		long window = 120 * MINUTE;
		List<Scenario> windows = RaleighIncidentClient.busiestScenarios(incidents, Fixtures.stationsAlongLine(28), window, 5);
		assertEquals(2, windows.size());
		assertEquals(28, windows.get(0).getIncidents().size());
		assertFalse(windows.get(0).getWarnings().isEmpty());
		assertEquals(2, windows.get(1).getIncidents().size());
		assertTrue(windows.get(0).getName(), windows.get(0).getName().startsWith("Latest "));
		long start0 = ScenarioIO.fromIso(windows.get(0).getWindow().split("/")[0]);
		long start1 = ScenarioIO.fromIso(windows.get(1).getWindow().split("/")[0]);
		assertTrue(Math.abs(start1 - start0) >= window);
	}

	@Test
	public void emptyFeedHasNoWindows() {
		assertTrue(RaleighIncidentClient.busiestScenarios(new ArrayList<Incident>(), Fixtures.stationsAlongLine(28),
				120 * MINUTE, 5).isEmpty());
	}

	@Test
	public void describesAge() {
		assertEquals("just now", RaleighIncidentClient.describeAge(30000L));
		assertEquals("5 min ago", RaleighIncidentClient.describeAge(5 * MINUTE));
		assertEquals("3 h ago", RaleighIncidentClient.describeAge(185 * MINUTE));
	}
}
