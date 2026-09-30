package edu.ncsu.csc411.ps03.dispatch.data;

/** A Raleigh Fire Department station, e.g. id "S07", label "RFD #7". Immutable. */
public class Station {
	private final String id;
	private final String label;
	private final double lat;
	private final double lon;

	public Station(String id, String label, double lat, double lon) {
		this.id = id;
		this.label = label;
		this.lat = lat;
		this.lon = lon;
	}

	public String getId() { return this.id; }
	public String getLabel() { return this.label; }
	public double getLat() { return this.lat; }
	public double getLon() { return this.lon; }

	/** The station number used for map labels, e.g. "S07" -> 7. */
	public int getNumber() { return Integer.parseInt(this.id.substring(1)); }

	@Override
	public String toString() { return this.id; }
}
