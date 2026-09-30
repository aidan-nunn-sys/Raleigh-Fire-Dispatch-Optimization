package edu.ncsu.csc411.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import edu.ncsu.csc411.ps03.dispatch.data.Incident;
import edu.ncsu.csc411.ps03.dispatch.data.Station;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchSession;
import edu.ncsu.csc411.ps03.dispatch.model.Geo;
import edu.ncsu.csc411.ps03.dispatch.model.TravelModel;
import edu.ncsu.csc411.ps03.dispatch.ui.MapPanel;
import edu.ncsu.csc411.ps03.dispatch.ui.MapProjection;
import edu.ncsu.csc411.ps03.utils.ColorPalette;

public class MapPanelTest {
	private static final int W = 600, H = 500, M = 20;

	/** A small square ring around the fixture stations. */
	private static List<double[][]> outline() {
		List<double[][]> rings = new ArrayList<double[][]>();
		rings.add(new double[][] {{35.76, -78.71}, {35.80, -78.71}, {35.80, -78.64}, {35.76, -78.64}});
		return rings;
	}

	private static MapPanel panel(int incidents) {
		MapPanel panel = new MapPanel(outline(), W, H);
		panel.setSession(new DispatchSession(DispatchSessionTest.scenario(incidents), new TravelModel(60, 120)));
		return panel;
	}

	private static BufferedImage paint(MapPanel panel) {
		BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		panel.paint(g);
		g.dispose();
		return image;
	}

	@Test
	public void boundsFitInsideTheBoxWithMargin() {
		MapProjection p = new MapProjection(35.7, 35.9, -78.8, -78.5, W, H, M);
		for (Point2D.Double pt : Arrays.asList(p.toScreen(35.7, -78.8), p.toScreen(35.9, -78.5))) {
			assertTrue(pt.x >= M - 1e-6 && pt.x <= W - M + 1e-6);
			assertTrue(pt.y >= M - 1e-6 && pt.y <= H - M + 1e-6);
		}
	}

	@Test
	public void northIsUpAndEastIsRight() {
		MapProjection p = new MapProjection(35.7, 35.9, -78.8, -78.5, W, H, M);
		assertTrue(p.toScreen(35.85, -78.6).y < p.toScreen(35.75, -78.6).y);
		assertTrue(p.toScreen(35.8, -78.55).x > p.toScreen(35.8, -78.75).x);
	}

	@Test
	public void aspectIsPreserved() {
		MapProjection p = new MapProjection(35.7, 35.9, -78.8, -78.5, W, H, M);
		Point2D.Double a = p.toScreen(35.8, -78.65);
		Point2D.Double north = p.toScreen(35.81, -78.65);
		Point2D.Double east = p.toScreen(35.8, -78.64);
		double pixelRatio = a.distance(north) / a.distance(east);
		double mileRatio = Geo.miles(35.8, -78.65, 35.81, -78.65) / Geo.miles(35.8, -78.65, 35.8, -78.64);
		assertEquals(mileRatio, pixelRatio, mileRatio * 0.01);
	}

	@Test
	public void fitIncludesFarIncidents() {
		List<Incident> far = Arrays.asList(Fixtures.incident("FAR", 0, 35.80, -78.20, null));
		MapProjection p = MapProjection.fit(outline(), new ArrayList<Station>(), far, W, H, M);
		Point2D.Double pt = p.toScreen(35.80, -78.20);
		assertTrue(pt.x <= W - M + 1e-6);
		assertTrue(pt.x >= M - 1e-6);
	}

	@Test
	public void emptyInputsDoNotProduceNaN() {
		MapProjection p = MapProjection.fit(new ArrayList<double[][]>(), new ArrayList<Station>(),
				new ArrayList<Incident>(), W, H, M);
		Point2D.Double pt = p.toScreen(35.8, -78.6);
		assertFalse(Double.isNaN(pt.x) || Double.isInfinite(pt.x) || Double.isNaN(pt.y) || Double.isInfinite(pt.y));
	}

	@Test
	public void incidentAtFindsTheDrawnIncident() {
		MapPanel panel = panel(3);
		Incident second = DispatchSessionTest.scenario(3).getIncidents().get(1);
		Point2D.Double pt = panel.getProjection().toScreen(second.getLat(), second.getLon());
		assertEquals(1, panel.incidentAt((int) Math.round(pt.x), (int) Math.round(pt.y)));
		assertEquals(-1, panel.incidentAt(0, 0));
	}

	@Test
	public void tooltipNamesGroupAndSaStation() {
		MapPanel panel = panel(3);
		String tip = panel.tooltipFor(0);
		assertTrue(tip, tip.contains("Fire"));
		assertTrue(tip, tip.contains("SA: S0"));
		assertNull(panel.tooltipFor(-1));
	}

	@Test
	public void paintsAllLayersWithStationsOnTop() {
		MapPanel panel = panel(3);
		panel.setShowOptimal(true);
		panel.setShowActual(true);
		BufferedImage image = paint(panel);
		// Station S06 (-78.65) sits well away from every incident, so its triangle is visible.
		Point2D.Double s6 = panel.getProjection().toScreen(35.78, -78.65);
		assertEquals(ColorPalette.BLACK.getRGB() & 0xFFFFFF,
				image.getRGB((int) Math.round(s6.x), (int) Math.round(s6.y)) & 0xFFFFFF);
	}

	@Test
	public void paintsEmptyScenario() {
		paint(panel(0));
	}
}
