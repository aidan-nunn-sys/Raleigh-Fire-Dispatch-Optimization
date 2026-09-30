package edu.ncsu.csc411.ps03.dispatch.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import javax.swing.JPanel;

import edu.ncsu.csc411.ps03.dispatch.data.Incident;
import edu.ncsu.csc411.ps03.dispatch.data.IncidentParser;
import edu.ncsu.csc411.ps03.dispatch.data.ScenarioIO;
import edu.ncsu.csc411.ps03.dispatch.data.Station;
import edu.ncsu.csc411.ps03.dispatch.model.ActualDispatch;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchMatrix;
import edu.ncsu.csc411.ps03.dispatch.model.DispatchSession;
import edu.ncsu.csc411.ps03.utils.ColorPalette;

/**
 * The map: the Raleigh city limits, the 28 stations as numbered triangles, and incidents as
 * circles colored by group. SA's current assignment is drawn as solid lines colored per
 * station. There are optional overlays for the optimum (thin green) and what Raleigh actually
 * sent (dashed gray). Hovering an incident shows a tooltip.
 */
@SuppressWarnings("serial")
public class MapPanel extends JPanel {
	public static final int MARGIN = 20;
	public static final int HIT_RADIUS = 8;
	private static final int INCIDENT_RADIUS = 5;
	private static final Color OUTLINE_FILL = new Color(189, 195, 199, 90);
	private static final Color[] STATION_COLORS = {ColorPalette.RED, ColorPalette.ORANGE, ColorPalette.GREEN,
			ColorPalette.BLUE, ColorPalette.INDIGO, ColorPalette.BROWN, ColorPalette.CONCRETE, ColorPalette.LIGHTRED,
			ColorPalette.LIGHTORANGE, ColorPalette.LIGHTGREEN, ColorPalette.LIGHTBLUE, ColorPalette.LIGHTINDIGO};
	private static final Stroke SA_STROKE = new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private static final Stroke OPTIMAL_STROKE = new BasicStroke(1f);
	private static final Stroke ACTUAL_STROKE = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
			10f, new float[] {6f, 4f}, 0f);
	private static final DateTimeFormatter TIME =
			DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(ScenarioIO.RALEIGH);

	private final List<double[][]> outline;
	private DispatchSession session;
	private MapProjection projection;
	private boolean showOptimal;
	private boolean showActual;

	public MapPanel(List<double[][]> outline, int width, int height) {
		this.outline = outline;
		setPreferredSize(new Dimension(width, height));
		setSize(width, height);
		setBackground(ColorPalette.WHITE);
		setToolTipText(""); // registers with the ToolTipManager so getToolTipText(MouseEvent) is used
	}

	/** Shows a scenario and refits the map to it. */
	public void setSession(DispatchSession session) {
		this.session = session;
		Dimension size = getPreferredSize();
		this.projection = MapProjection.fit(this.outline, session.getScenario().getStations(),
				session.getScenario().getIncidents(), size.width, size.height, MARGIN);
		repaint();
	}

	public MapProjection getProjection() { return this.projection; }

	public void setShowOptimal(boolean show) {
		this.showOptimal = show;
		repaint();
	}

	public void setShowActual(boolean show) {
		this.showActual = show;
		repaint();
	}

	public static Color groupColor(String group) {
		if (IncidentParser.FIRE.equals(group)) {
			return ColorPalette.LIGHTRED;
		}
		if (IncidentParser.HAZARDOUS.equals(group)) {
			return ColorPalette.LIGHTORANGE;
		}
		if (IncidentParser.ALARM.equals(group)) {
			return ColorPalette.YELLOW;
		}
		return ColorPalette.LIGHTBLUE;
	}

	public static Color stationColor(int worker) {
		return STATION_COLORS[worker % STATION_COLORS.length];
	}

	/** The real task drawn nearest to (x, y) within HIT_RADIUS pixels, or -1. */
	public int incidentAt(int x, int y) {
		if (this.session == null) {
			return -1;
		}
		List<Incident> incidents = this.session.getScenario().getIncidents();
		int best = -1;
		double bestDistance = HIT_RADIUS;
		for (int task = 0; task < incidents.size(); task++) {
			double d = point(incidents.get(task)).distance(x, y);
			if (d <= bestDistance) {
				best = task;
				bestDistance = d;
			}
		}
		return best;
	}

	@Override
	public String getToolTipText(MouseEvent e) {
		return tooltipFor(incidentAt(e.getX(), e.getY()));
	}

	/** Group, dispatch time, SA's station, and its estimated minutes; null for -1. */
	public String tooltipFor(int task) {
		if (task < 0 || this.session == null) {
			return null;
		}
		DispatchMatrix m = this.session.getMatrix();
		Incident incident = m.incident(task);
		int worker = DispatchSession.workerForTask(this.session.getCurrentConfiguration(), task);
		String sa = worker < 0 ? "none" : String.format(Locale.US, "%s, %.1f min", m.station(worker).getId(),
				m.estimateSeconds(task, worker) / 60.0);
		return "<html><b>" + incident.getGroup() + "</b><br>Dispatched "
				+ TIME.format(Instant.ofEpochMilli(incident.getDispatchMillis())) + "<br>SA: " + sa + "</html>";
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (this.session == null) {
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			paintOutline(g2);
			DispatchMatrix m = this.session.getMatrix();
			if (this.showActual) {
				ActualDispatch actual = this.session.getActual();
				g2.setStroke(ACTUAL_STROKE);
				g2.setColor(ColorPalette.CONCRETE);
				for (int task = 0; task < m.realTaskCount(); task++) {
					line(g2, m, actual.workerFor(task), task);
				}
			}
			if (this.showOptimal) {
				int[] optimal = this.session.getOptimalConfiguration();
				g2.setStroke(OPTIMAL_STROKE);
				g2.setColor(ColorPalette.GREEN);
				for (int task = 0; task < m.realTaskCount(); task++) {
					line(g2, m, DispatchSession.workerForTask(optimal, task), task);
				}
			}
			int[] current = this.session.getCurrentConfiguration();
			g2.setStroke(SA_STROKE);
			for (int task = 0; task < m.realTaskCount(); task++) {
				int worker = DispatchSession.workerForTask(current, task);
				if (worker >= 0) {
					g2.setColor(stationColor(worker));
					line(g2, m, worker, task);
				}
			}
			paintStations(g2, m);
			paintIncidents(g2, m);
			paintLegend(g2);
		} finally {
			g2.dispose();
		}
	}

	/** All rings form one even-odd path, so the rings that are holes stay unfilled. */
	private void paintOutline(Graphics2D g2) {
		Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
		for (double[][] ring : this.outline) {
			if (ring.length < 3) {
				continue;
			}
			Point2D.Double first = this.projection.toScreen(ring[0][0], ring[0][1]);
			path.moveTo(first.x, first.y);
			for (int i = 1; i < ring.length; i++) {
				Point2D.Double p = this.projection.toScreen(ring[i][0], ring[i][1]);
				path.lineTo(p.x, p.y);
			}
			path.closePath();
		}
		g2.setStroke(new BasicStroke(1f));
		g2.setColor(OUTLINE_FILL);
		g2.fill(path);
		g2.setColor(ColorPalette.SILVER);
		g2.draw(path);
	}

	private void paintStations(Graphics2D g2, DispatchMatrix m) {
		g2.setFont(getFont().deriveFont(Font.BOLD, 10f));
		for (int worker = 0; worker < m.size(); worker++) {
			Station s = m.station(worker);
			Point2D.Double p = this.projection.toScreen(s.getLat(), s.getLon());
			int x = (int) Math.round(p.x), y = (int) Math.round(p.y);
			g2.setColor(ColorPalette.BLACK);
			g2.fillPolygon(new Polygon(new int[] {x, x - 7, x + 7}, new int[] {y - 8, y + 6, y + 6}, 3));
			g2.drawString(Integer.toString(s.getNumber()), x + 8, y - 4);
		}
	}

	private void paintIncidents(Graphics2D g2, DispatchMatrix m) {
		g2.setStroke(new BasicStroke(1f));
		for (int task = 0; task < m.realTaskCount(); task++) {
			Incident incident = m.incident(task);
			Point2D.Double p = point(incident);
			int x = (int) Math.round(p.x) - INCIDENT_RADIUS, y = (int) Math.round(p.y) - INCIDENT_RADIUS;
			g2.setColor(groupColor(incident.getGroup()));
			g2.fillOval(x, y, 2 * INCIDENT_RADIUS, 2 * INCIDENT_RADIUS);
			g2.setColor(ColorPalette.BLACK);
			g2.drawOval(x, y, 2 * INCIDENT_RADIUS, 2 * INCIDENT_RADIUS);
		}
	}

	private void paintLegend(Graphics2D g2) {
		String[] groups = {IncidentParser.FIRE, IncidentParser.HAZARDOUS, IncidentParser.ALARM, IncidentParser.SERVICE};
		g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
		int x = 8, y = getPreferredSize().height - 10;
		for (String group : groups) {
			g2.setColor(groupColor(group));
			g2.fillOval(x, y - 9, 10, 10);
			g2.setColor(ColorPalette.BLACK);
			g2.drawOval(x, y - 9, 10, 10);
			g2.drawString(group, x + 14, y);
			x += g2.getFontMetrics().stringWidth(group) + 28;
		}
		g2.drawString("▲ station   — SA   green: optimal   dashed: actual", x, y);
	}

	private void line(Graphics2D g2, DispatchMatrix m, int worker, int task) {
		if (worker < 0) {
			return;
		}
		Station s = m.station(worker);
		Point2D.Double a = this.projection.toScreen(s.getLat(), s.getLon());
		Point2D.Double b = point(m.incident(task));
		g2.drawLine((int) Math.round(a.x), (int) Math.round(a.y), (int) Math.round(b.x), (int) Math.round(b.y));
	}

	private Point2D.Double point(Incident incident) {
		return this.projection.toScreen(incident.getLat(), incident.getLon());
	}
}
