package edu.ncsu.csc411.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

import edu.ncsu.csc411.ps03.dispatch.data.Incident;
import edu.ncsu.csc411.ps03.dispatch.data.Station;
import edu.ncsu.csc411.ps03.dispatch.model.Geo;
import edu.ncsu.csc411.ps03.dispatch.model.TravelModel;

public class DistanceTest {
	private static final double BELLTOWER_LAT = 35.7866, BELLTOWER_LON = -78.6639;
	private static final double CAPITOL_LAT = 35.7804, CAPITOL_LON = -78.6391;
	private static final double REFERENCE_MILES = 1.45458;

	@Test
	public void belltowerToCapitolMatchesReference() {
		double miles = Geo.miles(BELLTOWER_LAT, BELLTOWER_LON, CAPITOL_LAT, CAPITOL_LON);
		assertEquals(REFERENCE_MILES, miles, REFERENCE_MILES * 0.01);
	}

	@Test
	public void distanceIsSymmetricAndZeroToSelf() {
		double there = Geo.miles(BELLTOWER_LAT, BELLTOWER_LON, CAPITOL_LAT, CAPITOL_LON);
		double back = Geo.miles(CAPITOL_LAT, CAPITOL_LON, BELLTOWER_LAT, BELLTOWER_LON);
		assertEquals(there, back, 1e-12);
		assertEquals(0.0, Geo.miles(BELLTOWER_LAT, BELLTOWER_LON, BELLTOWER_LAT, BELLTOWER_LON), 1e-12);
	}

	@Test
	public void estimateSecondsRoundsTurnoutPlusDriving() {
		TravelModel model = new TravelModel(60, 120);
		Station s = new Station("S01", "RFD #1", BELLTOWER_LAT, BELLTOWER_LON);
		Incident i = new Incident("x", 0L, null, "Fire", CAPITOL_LAT, CAPITOL_LON, null);
		assertEquals(235, model.estimateSeconds(s, i)); // 60 + 120 * 1.45458 = 234.55
	}

	@Test
	public void fitRecoversAnExactLine() {
		double[] miles = {0.5, 1, 2, 3, 5};
		double[] seconds = new double[miles.length];
		for (int k = 0; k < miles.length; k++) {
			seconds[k] = 60 + 120 * miles[k];
		}
		TravelModel.Fit fit = TravelModel.fit(miles, seconds);
		assertEquals(60.0, fit.model.getTurnoutSeconds(), 1e-9);
		assertEquals(120.0, fit.model.getSecondsPerMile(), 1e-9);
		assertEquals(1.0, fit.rSquared, 1e-12);
		assertEquals(5, fit.sampleSize);
	}

	@Test(expected = IllegalArgumentException.class)
	public void fitRejectsASinglePoint() {
		TravelModel.fit(new double[] {1}, new double[] {100});
	}

	@Test
	public void errorMetrics() {
		TravelModel model = new TravelModel(60, 120);
		double[] miles = {1, 2};
		double[] seconds = {190, 290}; // predicted 180 and 300
		assertEquals(10.0, model.meanAbsoluteError(miles, seconds), 1e-9);
		assertEquals(0.0, model.meanBias(miles, seconds), 1e-9);
	}

	@Test
	public void loadReadsPropertiesAndRejectsMissingKeys() throws IOException {
		File f = File.createTempFile("travel", ".properties");
		f.deleteOnExit();
		Files.write(f.toPath(), "# comment\nTURNOUT_SECONDS=75.5\nSECONDS_PER_MILE=110\n".getBytes(StandardCharsets.UTF_8));
		TravelModel model = TravelModel.load(f.getPath());
		assertEquals(75.5, model.getTurnoutSeconds(), 1e-9);
		assertEquals(110.0, model.getSecondsPerMile(), 1e-9);

		Files.write(f.toPath(), "TURNOUT_SECONDS=75.5\n".getBytes(StandardCharsets.UTF_8));
		try {
			TravelModel.load(f.getPath());
			fail("expected IOException");
		} catch (IOException expected) {
			// the message names SECONDS_PER_MILE
		}
	}
}
