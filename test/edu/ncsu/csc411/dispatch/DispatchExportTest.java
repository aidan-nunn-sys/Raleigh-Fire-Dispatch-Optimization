package edu.ncsu.csc411.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.ncsu.csc411.ps03.dispatch.data.Scenario;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchSession;
import edu.ncsu.csc411.ps03.dispatch.model.TravelModel;
import edu.ncsu.csc411.ps03.dispatch.ui.DispatchExport;

public class DispatchExportTest {
	private static final TravelModel MODEL = new TravelModel(60, 120);

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static DispatchSession session(String actualA, String actualB, String actualC) {
		return new DispatchSession(new Scenario("Test", "", Fixtures.stationsAlongLine(4), Arrays.asList(
				Fixtures.incident("A", 0, 35.78, -78.70, actualA),
				Fixtures.incident("B", 1, 35.78, -78.69, actualB),
				Fixtures.incident("C", 2, 35.78, -78.68, actualC))), MODEL);
	}

	private List<String> export(DispatchSession session) throws IOException {
		File out = new File(this.tmp.getRoot(), "sub/dispatch_test.csv");
		DispatchExport.write(out.getPath(), session);
		return Files.readAllLines(out.toPath(), StandardCharsets.UTF_8);
	}

	@Test
	public void fileNameIsASlugUnderOutputs() {
		assertEquals("outputs/dispatch_florence_peak.csv", DispatchExport.fileName("Florence peak"));
		assertEquals("outputs/dispatch_latest_sep_24_17_05_19_05_9.csv",
				DispatchExport.fileName("Latest Sep 24 17:05-19:05 (9)"));
		assertEquals("outputs/dispatch_scenario.csv", DispatchExport.fileName("///"));
	}

	@Test
	public void writesOneRowPerIncidentThenSummary() throws IOException {
		List<String> lines = export(session("S01", null, null));
		assertEquals(DispatchExport.HEADER, lines.get(0));
		assertTrue(lines.get(1), lines.get(1).startsWith("A,Fire,"));
		assertTrue(lines.get(1), lines.get(1).contains(",S01,1.00"));     // actual S01, 60 s turnout = 1.00 min
		assertTrue(lines.get(2), lines.get(2).endsWith(",,"));           // B has no actual station
		assertEquals("", lines.get(4));
		assertEquals(DispatchExport.SUMMARY_HEADER, lines.get(5));
		assertTrue(lines.get(6).startsWith("SA best,3,"));
		assertTrue(lines.get(7).startsWith("Optimal,3,3.00,1.00,1.00,0.0000"));
		assertTrue(lines.get(8).startsWith("Greedy,3,"));
		assertTrue(lines.get(9), lines.get(9).startsWith("Actual,1,") && lines.get(9).endsWith(",NA"));
		assertEquals(10, lines.size());
		for (String line : lines) {
			assertFalse(line.toLowerCase().contains("address"));
		}
	}

	@Test
	public void actualIsNaWhenNoStationPublished() throws IOException {
		List<String> lines = export(session(null, null, null));
		assertEquals("Actual,0,NA,NA,NA,NA", lines.get(9));
	}
}
