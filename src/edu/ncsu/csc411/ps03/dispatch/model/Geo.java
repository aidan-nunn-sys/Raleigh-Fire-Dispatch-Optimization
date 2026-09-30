package edu.ncsu.csc411.ps03.dispatch.model;

/** Great-circle distance on a spherical Earth. */
public class Geo {
	public static final double EARTH_RADIUS_MILES = 3958.8;

	private Geo() {}

	/** Haversine distance in miles between two points given in degrees. */
	public static double miles(double lat1, double lon1, double lat2, double lon2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLon = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(dLon / 2) * Math.sin(dLon / 2);
		return 2 * EARTH_RADIUS_MILES * Math.asin(Math.sqrt(a));
	}
}
