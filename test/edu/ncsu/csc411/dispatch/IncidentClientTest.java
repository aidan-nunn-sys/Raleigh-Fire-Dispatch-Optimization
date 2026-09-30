package edu.ncsu.csc411.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import edu.ncsu.csc411.ps03.dispatch.data.ArcGisClient;
import edu.ncsu.csc411.ps03.dispatch.data.Incident;
import edu.ncsu.csc411.ps03.dispatch.data.IncidentParser;

public class IncidentClientTest {
	private static final Set<String> KNOWN = Fixtures.knownStationIds();

	private static String read(String path) throws IOException {
		return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
	}

	private static List<Incident> parseAll(ArcGisClient.Page page) {
		List<Incident> incidents = new ArrayList<Incident>();
		for (Map<String, Object> feature : page.features) {
			Incident incident = IncidentParser.fromFeature(feature, KNOWN);
			if (incident != null) {
				incidents.add(incident);
			}
		}
		return incidents;
	}

	@Test
	public void historicalFixtureParses() throws IOException {
		ArcGisClient.Page page = ArcGisClient.parsePage(read("test/resources/raleigh_historical_sample.json"));
		assertEquals(4, page.features.size());
		assertFalse(page.exceededTransferLimit);
		List<Incident> incidents = parseAll(page);
		assertEquals("feature without geometry is skipped", 3, incidents.size());

		Incident a = incidents.get(0);
		assertEquals("18-0030226", a.getId());
		assertEquals(1536963057000L, a.getDispatchMillis());
		assertEquals(Long.valueOf(1536963309000L), a.getArriveMillis());
		assertEquals("S09", a.getActualStationId());
		assertEquals(IncidentParser.ALARM, a.getGroup());
		assertEquals(35.828, a.getLat(), 1e-9);
		assertEquals(-78.645, a.getLon(), 1e-9);

		Incident b = incidents.get(1);
		assertEquals("S17", b.getActualStationId());
		assertEquals(IncidentParser.SERVICE, b.getGroup());
		assertEquals(35.857, b.getLat(), 1e-9);
		assertEquals(-78.715, b.getLon(), 1e-9);

		Incident c = incidents.get(2);
		assertEquals("24-042814", c.getId());
		assertNull("2021-05..2025 records have no station", c.getActualStationId());
		assertEquals(IncidentParser.ALARM, c.getGroup());
		assertEquals(35.854, c.getLat(), 1e-9);
		assertEquals(-78.711, c.getLon(), 1e-9);
	}

	@Test
	public void pastMonthFixtureParses() throws IOException {
		ArcGisClient.Page page = ArcGisClient.parsePage(read("test/resources/raleigh_pastmonth_sample.json"));
		assertTrue(page.exceededTransferLimit);
		List<Incident> incidents = parseAll(page);
		assertEquals(3, incidents.size());
		assertEquals("S16", incidents.get(0).getActualStationId());
		assertEquals(IncidentParser.ALARM, incidents.get(0).getGroup());
		assertEquals("S22", incidents.get(1).getActualStationId());
		assertEquals(IncidentParser.ALARM, incidents.get(1).getGroup());
		Incident c = incidents.get(2);
		assertEquals("S03", c.getActualStationId());
		assertNull(c.getArriveMillis());
		assertEquals(IncidentParser.SERVICE, c.getGroup());
		assertEquals(35.758, c.getLat(), 1e-9);
		assertEquals(-78.613, c.getLon(), 1e-9);
	}

	@Test
	public void normalizesStationIds() {
		assertEquals("S14", IncidentParser.normalizeStation(Double.valueOf(14.0), KNOWN));
		assertEquals("S07", IncidentParser.normalizeStation(Integer.valueOf(7), KNOWN));
		assertEquals("S03", IncidentParser.normalizeStation("Station 03", KNOWN));
		assertEquals("S09", IncidentParser.normalizeStation("station 9", KNOWN));
		assertNull("there is no station 13", IncidentParser.normalizeStation("Station 13", KNOWN));
		assertNull(IncidentParser.normalizeStation(Integer.valueOf(13), KNOWN));
		assertNull(IncidentParser.normalizeStation(Integer.valueOf(99), KNOWN));
		assertNull(IncidentParser.normalizeStation(null, KNOWN));
		assertNull(IncidentParser.normalizeStation("Engine 4", KNOWN));
		assertNull(IncidentParser.normalizeStation("", KNOWN));
	}

	@Test
	public void classifiesGroups() {
		assertEquals(IncidentParser.FIRE, IncidentParser.classify("Building fire", null, null));
		assertEquals(IncidentParser.FIRE, IncidentParser.classify(null, "Fire", "Cooking Fire"));
		assertEquals(IncidentParser.HAZARDOUS, IncidentParser.classify("Gas leak (natural gas or LPG)", null, null));
		assertEquals(IncidentParser.HAZARDOUS, IncidentParser.classify(null, "Hazardous Situation", "Power Line Down"));
		assertEquals(IncidentParser.ALARM, IncidentParser.classify("Alarm system activation, no fire", null, null));
		assertEquals(IncidentParser.ALARM,
				IncidentParser.classify("Smoke detector activation no fire - unintentional", null, null));
		assertEquals(IncidentParser.ALARM,
				IncidentParser.classify("Detector activation no fire - unintentional", null, null));
		assertEquals(IncidentParser.ALARM,
				IncidentParser.classify("Smoke detector activation due to malfunction", null, null));
		assertEquals(IncidentParser.ALARM,
				IncidentParser.classify("CO detector activation due to malfunction", null, null));
		assertEquals(IncidentParser.FIRE, IncidentParser.classify("Building fire", null, null));
		assertEquals(IncidentParser.HAZARDOUS, IncidentParser.classify("Carbon monoxide incident", null, null));
		assertEquals(IncidentParser.SERVICE, IncidentParser.classify("Public service", null, null));
		assertEquals(IncidentParser.SERVICE, IncidentParser.classify(null, null, null));
	}

	@Test
	public void errorResponseBecomesIOException() {
		try {
			ArcGisClient.parsePage("{\"error\":{\"code\":400,\"message\":\"Invalid query\",\"details\":[]}}");
			fail("expected IOException");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("Invalid query"));
		}
	}

	@Test(expected = IOException.class)
	public void nonJsonResponseBecomesIOException() throws IOException {
		ArcGisClient.parsePage("<html>502 Bad Gateway</html>");
	}

	@Test
	public void queryAllFollowsExceededTransferLimit() throws IOException {
		final List<String> urls = new ArrayList<String>();
		ArcGisClient fake = new ArcGisClient() {
			@Override
			protected String get(String url) {
				urls.add(url);
				if (url.contains("resultOffset=0&")) {
					return "{\"features\":[{\"attributes\":{}},{\"attributes\":{}}],\"exceededTransferLimit\":true}";
				}
				return "{\"features\":[{\"attributes\":{}}]}";
			}
		};
		List<Map<String, Object>> all = fake.queryAll("https://example.test/FeatureServer/0", "1=1", "OBJECTID", "");
		assertEquals(3, all.size());
		assertEquals(2, urls.size());
		assertTrue(urls.get(1).contains("resultOffset=2&"));
	}

	@Test
	public void buildQueryUrlEncodesAndNeverAsksForAddress() {
		String url = ArcGisClient.buildQueryUrl("https://example.test/FeatureServer/0",
				"station IS NOT NULL", "incident_number,station", "&orderByFields=OBJECTID", 0);
		assertTrue(url.startsWith("https://example.test/FeatureServer/0/query?"));
		assertTrue(url.contains("where=station+IS+NOT+NULL"));
		assertTrue(url.contains("outSR=4326"));
		assertTrue(url.contains("f=json"));
		assertTrue(url.contains("resultRecordCount=2000"));
		assertTrue(url.endsWith("&orderByFields=OBJECTID"));
		assertFalse(IncidentParser.HISTORICAL_FIELDS.toLowerCase().contains("address"));
		assertFalse(IncidentParser.PAST_MONTH_FIELDS.toLowerCase().contains("address"));
	}
}
