package edu.ncsu.csc411.ps03.dispatch.model;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import edu.ncsu.csc411.ps03.dispatch.data.Incident;
import edu.ncsu.csc411.ps03.dispatch.data.Station;

/**
 * Estimated response time: seconds = TURNOUT_SECONDS + SECONDS_PER_MILE * straight-line miles.
 * FetchStaticData fits both constants and stores them in inputs/raleigh/travel_model.properties.
 * Straight-line distance is only a proxy for road distance.
 */
public class TravelModel {
	private final double turnoutSeconds;
	private final double secondsPerMile;

	/** The result of an ordinary least squares fit. */
	public static class Fit {
		public final TravelModel model;
		public final int sampleSize;
		public final double rSquared;

		Fit(TravelModel model, int sampleSize, double rSquared) {
			this.model = model;
			this.sampleSize = sampleSize;
			this.rSquared = rSquared;
		}
	}

	public TravelModel(double turnoutSeconds, double secondsPerMile) {
		this.turnoutSeconds = turnoutSeconds;
		this.secondsPerMile = secondsPerMile;
	}

	public static TravelModel load(String path) throws IOException {
		Properties props = new Properties();
		InputStream in = new FileInputStream(path);
		try {
			props.load(in);
		} finally {
			in.close();
		}
		return new TravelModel(requireDouble(props, "TURNOUT_SECONDS", path), requireDouble(props, "SECONDS_PER_MILE", path));
	}

	public double getTurnoutSeconds() { return this.turnoutSeconds; }
	public double getSecondsPerMile() { return this.secondsPerMile; }

	public double seconds(double miles) {
		return this.turnoutSeconds + this.secondsPerMile * miles;
	}

	public int estimateSeconds(Station station, Incident incident) {
		return (int) Math.round(seconds(Geo.miles(station.getLat(), station.getLon(), incident.getLat(), incident.getLon())));
	}

	/** Ordinary least squares fit of seconds against miles. */
	public static Fit fit(double[] miles, double[] seconds) {
		int n = miles.length;
		if (n < 2 || seconds.length != n) {
			throw new IllegalArgumentException("Need at least 2 paired samples");
		}
		double meanX = 0, meanY = 0;
		for (int k = 0; k < n; k++) {
			meanX += miles[k];
			meanY += seconds[k];
		}
		meanX /= n;
		meanY /= n;
		double sxx = 0, sxy = 0, syy = 0;
		for (int k = 0; k < n; k++) {
			double dx = miles[k] - meanX;
			double dy = seconds[k] - meanY;
			sxx += dx * dx;
			sxy += dx * dy;
			syy += dy * dy;
		}
		if (sxx == 0) {
			throw new IllegalArgumentException("All distances are equal");
		}
		double slope = sxy / sxx;
		double intercept = meanY - slope * meanX;
		double rSquared = syy == 0 ? 1.0 : (sxy * sxy) / (sxx * syy);
		return new Fit(new TravelModel(intercept, slope), n, rSquared);
	}

	/** Mean |actual − predicted| seconds; NaN for an empty sample. */
	public double meanAbsoluteError(double[] miles, double[] seconds) {
		double sum = 0;
		for (int k = 0; k < miles.length; k++) {
			sum += Math.abs(seconds[k] - seconds(miles[k]));
		}
		return sum / miles.length;
	}

	/** Mean (actual − predicted) seconds; positive means real responses are slower than the model. */
	public double meanBias(double[] miles, double[] seconds) {
		double sum = 0;
		for (int k = 0; k < miles.length; k++) {
			sum += seconds[k] - seconds(miles[k]);
		}
		return sum / miles.length;
	}

	private static double requireDouble(Properties props, String key, String path) throws IOException {
		String value = props.getProperty(key);
		if (value == null) {
			throw new IOException(path + " is missing " + key);
		}
		try {
			return Double.parseDouble(value.trim());
		} catch (NumberFormatException nfe) {
			throw new IOException(path + ": " + key + " is not a number: " + value);
		}
	}
}
