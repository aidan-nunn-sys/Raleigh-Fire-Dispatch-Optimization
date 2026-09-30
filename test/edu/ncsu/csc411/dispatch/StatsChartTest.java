package edu.ncsu.csc411.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

import edu.ncsu.csc411.ps03.dispatch.data.Scenario;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchSession;
import edu.ncsu.csc411.ps03.dispatch.model.TravelModel;
import edu.ncsu.csc411.ps03.dispatch.ui.ConvergenceChart;
import edu.ncsu.csc411.ps03.dispatch.ui.StatsPanel;

public class StatsChartTest {
	private static final TravelModel MODEL = new TravelModel(60, 120);

	private static DispatchSession session(String actualA, String actualB, String actualC) {
		return new DispatchSession(new Scenario("Test", "", Fixtures.stationsAlongLine(4), Arrays.asList(
				Fixtures.incident("A", 0, 35.78, -78.70, actualA),
				Fixtures.incident("B", 1, 35.78, -78.69, actualB),
				Fixtures.incident("C", 2, 35.78, -78.68, actualC))), MODEL);
	}

	@Test
	public void showsEveryMethodRow() {
		DispatchSession s = session("S01", "S02", "S03");
		StatsPanel panel = new StatsPanel();
		panel.update(s);
		assertEquals("SA now", panel.getTableModel().getValueAt(StatsPanel.SA_NOW, 0));
		assertEquals("SA best", panel.getTableModel().getValueAt(StatsPanel.SA_BEST, 0));
		assertEquals("Optimal", panel.getTableModel().getValueAt(StatsPanel.OPTIMAL, 0));
		assertEquals("Greedy nearest", panel.getTableModel().getValueAt(StatsPanel.GREEDY, 0));
		assertEquals("Actual", panel.getTableModel().getValueAt(StatsPanel.ACTUAL, 0));
		assertEquals(String.format(Locale.US, "%.1f", s.getOptimalMetrics().getTotalMinutes()),
				panel.getTableModel().getValueAt(StatsPanel.OPTIMAL, 1));
	}

	@Test
	public void actualNotPublishedWhenNoStation() {
		StatsPanel panel = new StatsPanel();
		panel.update(session(null, null, null));
		assertEquals("not published", panel.getTableModel().getValueAt(StatsPanel.ACTUAL, 1));
	}

	@Test
	public void unmatchedCountIsShown() {
		StatsPanel panel = new StatsPanel();
		panel.update(session("S01", null, null));
		assertEquals("Actual (2 unmatched)", panel.getTableModel().getValueAt(StatsPanel.ACTUAL, 0));
	}

	@Test
	public void gapTextMentionsBestIteration() {
		DispatchSession s = session("S01", "S02", "S03");
		for (int i = 0; i < 50; i++) {
			s.step();
		}
		StatsPanel panel = new StatsPanel();
		panel.update(s);
		assertTrue(panel.getGapText(), panel.getGapText().contains("iteration " + s.getBestIteration()));
	}

	@Test
	public void showsScenarioWarnings() {
		DispatchSession s = new DispatchSession(new Scenario("Busy", "", Fixtures.stationsAlongLine(2), Arrays.asList(
				Fixtures.incident("A", 0, 35.78, -78.70, null),
				Fixtures.incident("B", 1, 35.78, -78.69, null),
				Fixtures.incident("C", 2, 35.78, -78.68, null))), MODEL);
		StatsPanel panel = new StatsPanel();
		panel.update(s);
		assertTrue(panel.getWarningText(), panel.getWarningText().contains("Kept the first 2 of 3"));
	}

	@Test
	public void yRangePadsAFlatLine() {
		List<double[]> flat = new ArrayList<double[]>();
		flat.add(new double[] {10, 10});
		double[] range = ConvergenceChart.yRange(flat, 10);
		assertTrue(range[1] > range[0]);
		assertTrue(range[0] <= 10 && range[1] >= 10);
		double[] zero = ConvergenceChart.yRange(new ArrayList<double[]>(), 0);
		assertTrue(zero[1] > zero[0]);
	}

	@Test
	public void yRangeCoversEveryPoint() {
		List<double[]> history = Arrays.asList(new double[] {30, 25}, new double[] {40, 22});
		double[] range = ConvergenceChart.yRange(history, 20);
		assertTrue(range[0] <= 20);
		assertTrue(range[1] >= 40);
	}

	@Test
	public void paintsHistoryAndEmptyScenario() {
		DispatchSession s = session("S01", "S02", "S03");
		for (int i = 0; i < 50; i++) {
			s.step();
		}
		for (DispatchSession each : Arrays.asList(s, DispatchSessionTest.scenarioSession(0))) {
			ConvergenceChart chart = new ConvergenceChart(800, 150);
			chart.setSession(each, 1000);
			BufferedImage image = new BufferedImage(800, 150, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = image.createGraphics();
			chart.paint(g);
			g.dispose();
		}
	}
}
