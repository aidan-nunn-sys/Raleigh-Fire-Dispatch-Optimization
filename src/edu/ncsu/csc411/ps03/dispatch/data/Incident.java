package edu.ncsu.csc411.ps03.dispatch.data;

/**
 * One fire incident: when it was dispatched, where (rounded to ~100 m), what kind,
 * and which station actually responded. No address is ever kept. Immutable.
 */
public class Incident {
	private final String id;
	private final long dispatchMillis;
	private final Long arriveMillis;
	private final String group;
	private final double lat;
	private final double lon;
	private final String actualStationId;

	public Incident(String id, long dispatchMillis, Long arriveMillis, String group,
			double lat, double lon, String actualStationId) {
		this.id = id;
		this.dispatchMillis = dispatchMillis;
		this.arriveMillis = arriveMillis;
		this.group = group;
		this.lat = lat;
		this.lon = lon;
		this.actualStationId = actualStationId;
	}

	public String getId() { return this.id; }
	public long getDispatchMillis() { return this.dispatchMillis; }
	/** Null when the record has no arrival time. */
	public Long getArriveMillis() { return this.arriveMillis; }
	public String getGroup() { return this.group; }
	public double getLat() { return this.lat; }
	public double getLon() { return this.lon; }
	/** "S14" style id, or null when no station was published or it is not one of the 28. */
	public String getActualStationId() { return this.actualStationId; }

	@Override
	public String toString() { return this.id; }
}
