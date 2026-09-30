package edu.ncsu.csc411.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import edu.ncsu.csc411.ps03.dispatch.data.Incident;
import edu.ncsu.csc411.ps03.dispatch.data.IncidentParser;
import edu.ncsu.csc411.ps03.dispatch.data.Scenario;
import edu.ncsu.csc411.ps03.dispatch.data.ScenarioIO;
import edu.ncsu.csc411.ps03.dispatch.data.Station;

public class ScenarioTest {

	private static String tempPath() throws IOException {
		File f = File.createTempFile("dispatch", ".csv");
		f.deleteOnExit();
		return f.getPath();
	}

	@Test
	public void incidentRoundTripKeepsEveryField() throws IOException {
		Incident full = new Incident("18-012345", ScenarioIO.fromIso("2018-09-14T18:03:11-04:00"),
				Long.valueOf(ScenarioIO.fromIso("2018-09-14T18:08:40-04:00")), IncidentParser.FIRE, 35.781, -78.642, "S01");
		Incident sparse = new Incident("18-012346", ScenarioIO.fromIso("2018-09-14T18:10:00-04:00"),
				null, IncidentParser.SERVICE, 35.8, -78.6, null);
		String path = tempPath();
		ScenarioIO.writeIncidents(path, "Florence peak", "2018-09-14T18:00:00-04:00/2018-09-14T20:00:00-04:00",
				"Fire_Incidents_Public fetched 2026-09-26", Arrays.asList(full, sparse));

		Map<String, String> header = ScenarioIO.readHeader(path);
		assertEquals("Florence peak", header.get("name"));
		assertEquals("2018-09-14T18:00:00-04:00/2018-09-14T20:00:00-04:00", header.get("window"));
		assertEquals("Fire_Incidents_Public fetched 2026-09-26", header.get("source"));

		List<Incident> back = ScenarioIO.readIncidents(path);
		assertEquals(2, back.size());
		Incident a = back.get(0);
		assertEquals("18-012345", a.getId());
		assertEquals(full.getDispatchMillis(), a.getDispatchMillis());
		assertEquals(full.getArriveMillis(), a.getArriveMillis());
		assertEquals(IncidentParser.FIRE, a.getGroup());
		assertEquals(35.781, a.getLat(), 1e-9);
		assertEquals(-78.642, a.getLon(), 1e-9);
		assertEquals("S01", a.getActualStationId());
		Incident b = back.get(1);
		assertNull(b.getArriveMillis());
		assertNull(b.getActualStationId());
		assertEquals(IncidentParser.SERVICE, b.getGroup());
	}

	@Test
	public void isoUsesRaleighOffsetInSummerAndWinter() {
		assertEquals("2018-09-14T18:03:11-04:00", ScenarioIO.toIso(ScenarioIO.fromIso("2018-09-14T18:03:11-04:00")));
		assertEquals("2019-01-15T12:00:00-05:00", ScenarioIO.toIso(ScenarioIO.fromIso("2019-01-15T17:00:00Z")));
	}

	@Test
	public void trimsToStationCountWithWarning() {
		List<Incident> incidents = new ArrayList<Incident>();
		for (int minute = 29; minute >= 0; minute--) {
			incidents.add(Fixtures.incident("I" + minute, minute, 35.78, -78.65, null));
		}
		Scenario s = new Scenario("Busy", "", Fixtures.stationsAlongLine(28), incidents);
		assertEquals(28, s.getIncidents().size());
		assertEquals("I0", s.getIncidents().get(0).getId());
		assertEquals("I27", s.getIncidents().get(27).getId());
		assertEquals(1, s.getWarnings().size());
		assertTrue(s.getWarnings().get(0).contains("first 28 of 30"));
	}

	@Test
	public void sameDispatchTimeOrdersById() {
		List<Incident> incidents = Arrays.asList(
				Fixtures.incident("B", 0, 35.78, -78.65, null),
				Fixtures.incident("A", 0, 35.78, -78.65, null));
		Scenario s = new Scenario("Tie", "", Fixtures.stationsAlongLine(3), incidents);
		assertEquals("A", s.getIncidents().get(0).getId());
	}

	@Test
	public void emptyWindowWarns() {
		Scenario s = new Scenario("Quiet", "", Fixtures.stationsAlongLine(28), new ArrayList<Incident>());
		assertTrue(s.getIncidents().isEmpty());
		assertEquals(1, s.getWarnings().size());
	}

	@Test
	public void neverWritesAddressColumn() throws IOException {
		String path = tempPath();
		ScenarioIO.writeIncidents(path, "x", "", "", Arrays.asList(Fixtures.incident("I1", 0, 35.78, -78.65, "S01")));
		String text = new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
		assertFalse(text.toLowerCase().contains("address"));
		for (String line : text.split("\n")) {
			if (!line.startsWith("#")) {
				assertEquals(ScenarioIO.INCIDENT_HEADER, line.trim());
				break;
			}
		}
	}

	@Test
	public void toleratesCrlfBlankLinesAndSpaces() throws IOException {
		String path = tempPath();
		String text = "# name=Hand edited\r\n\r\n" + ScenarioIO.INCIDENT_HEADER + "\r\n"
				+ " 18-1 , 2018-09-14T18:03:11-04:00 , , Fire , 35.781 , -78.642 , S01 \r\n\r\n\r\n";
		Files.write(new File(path).toPath(), text.getBytes(StandardCharsets.UTF_8));
		List<Incident> back = ScenarioIO.readIncidents(path);
		assertEquals(1, back.size());
		assertEquals("18-1", back.get(0).getId());
		assertNull(back.get(0).getArriveMillis());
		assertEquals("S01", back.get(0).getActualStationId());
		assertEquals("Hand edited", ScenarioIO.readHeader(path).get("name"));
	}

	@Test(expected = IOException.class)
	public void rejectsWrongHeader() throws IOException {
		String path = tempPath();
		Files.write(new File(path).toPath(), "id,address\n1,somewhere\n".getBytes(StandardCharsets.UTF_8));
		ScenarioIO.readIncidents(path);
	}

	@Test(expected = IOException.class)
	public void rejectsBadRow() throws IOException {
		String path = tempPath();
		Files.write(new File(path).toPath(), (ScenarioIO.INCIDENT_HEADER + "\n1,not-a-date,,Fire,1,2,\n")
				.getBytes(StandardCharsets.UTF_8));
		ScenarioIO.readIncidents(path);
	}

	@Test
	public void stationsRoundTrip() throws IOException {
		String path = tempPath();
		ScenarioIO.writeStations(path, Fixtures.stationsAlongLine(3));
		List<Station> back = ScenarioIO.readStations(path);
		assertEquals(3, back.size());
		assertEquals("S02", back.get(1).getId());
		assertEquals("RFD #2", back.get(1).getLabel());
		assertEquals(-78.69, back.get(1).getLon(), 1e-6);
		assertEquals(2, back.get(1).getNumber());
	}

	@Test
	public void outlineRoundTrip() throws IOException {
		String path = tempPath();
		List<double[][]> rings = new ArrayList<double[][]>();
		rings.add(new double[][] {{35.7, -78.7}, {35.8, -78.7}, {35.8, -78.6}});
		rings.add(new double[][] {{35.9, -78.5}, {35.95, -78.5}});
		ScenarioIO.writeOutline(path, rings);
		List<double[][]> back = ScenarioIO.readOutline(path);
		assertEquals(2, back.size());
		assertEquals(3, back.get(0).length);
		assertEquals(35.95, back.get(1)[1][0], 1e-9);
		assertEquals(-78.5, back.get(1)[1][1], 1e-9);
	}

	@Test(expected = IOException.class)
	public void missingFileNamesThePath() throws IOException {
		ScenarioIO.readStations("inputs/raleigh/does_not_exist.csv");
	}
}
