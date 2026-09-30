package edu.ncsu.csc411.ps03.dispatch.ui;

import java.awt.geom.Point2D;
import java.util.List;

import edu.ncsu.csc411.ps03.dispatch.data.Incident;
import edu.ncsu.csc411.ps03.dispatch.data.Station;

/**
 * Equirectangular projection for a city-sized map. Longitude is scaled by cos(lat0) so a
 * mile is the same number of pixels north-south and east-west. North is up, and the bounds
 * are centered in the box inside a margin.
 */
public class MapProjection {
	private final double maxLat;
	private final double minLon;
	private final double cosLat;
	private final double scale;
	private final double offsetX;
	private final double offsetY;

	public MapProjection(double minLat, double maxLat, double minLon, double maxLon, int width, int height, int margin) {
		this.maxLat = maxLat;
		this.minLon = minLon;
		this.cosLat = Math.cos(Math.toRadians((minLat + maxLat) / 2));
		double spanX = (maxLon - minLon) * this.cosLat;
		double spanY = maxLat - minLat;
		double innerW = Math.max(1, width - 2 * margin);
		double innerH = Math.max(1, height - 2 * margin);
		this.scale = Math.min(innerW / Math.max(spanX, 1e-6), innerH / Math.max(spanY, 1e-6));
		this.offsetX = margin + (innerW - spanX * this.scale) / 2;
		this.offsetY = margin + (innerH - spanY * this.scale) / 2;
	}

	/** Fits the outline, stations, and incidents; outline points are {lat, lon}. */
	public static MapProjection fit(List<double[][]> outline, List<Station> stations, List<Incident> incidents,
			int width, int height, int margin) {
		double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
		double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
		for (double[][] ring : outline) {
			for (double[] point : ring) {
				minLat = Math.min(minLat, point[0]);
				maxLat = Math.max(maxLat, point[0]);
				minLon = Math.min(minLon, point[1]);
				maxLon = Math.max(maxLon, point[1]);
			}
		}
		for (Station s : stations) {
			minLat = Math.min(minLat, s.getLat());
			maxLat = Math.max(maxLat, s.getLat());
			minLon = Math.min(minLon, s.getLon());
			maxLon = Math.max(maxLon, s.getLon());
		}
		for (Incident i : incidents) {
			minLat = Math.min(minLat, i.getLat());
			maxLat = Math.max(maxLat, i.getLat());
			minLon = Math.min(minLon, i.getLon());
			maxLon = Math.max(maxLon, i.getLon());
		}
		if (minLat > maxLat) {
			// Nothing to fit: fall back to a box around Raleigh.
			return new MapProjection(35.70, 35.95, -78.80, -78.50, width, height, margin);
		}
		return new MapProjection(minLat, maxLat, minLon, maxLon, width, height, margin);
	}

	public Point2D.Double toScreen(double lat, double lon) {
		return new Point2D.Double(this.offsetX + (lon - this.minLon) * this.cosLat * this.scale,
				this.offsetY + (this.maxLat - lat) * this.scale);
	}
}
