package edu.ncsu.csc411.ps03.dispatch.data;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns ArcGIS incident features from either Raleigh feed into Incident objects.
 * Historical records carry an integer "station"; past-month records carry a
 * "station_name" string like "Station 14". Both normalize to "S14".
 */
public class IncidentParser {
	public static final String FIRE = "Fire";
	public static final String HAZARDOUS = "Hazardous";
	public static final String ALARM = "Alarm";
	public static final String SERVICE = "Service/Other";

	/** Fields requested from Fire_Incidents_Public. Never includes address. */
	public static final String HISTORICAL_FIELDS = "OBJECTID,incident_number,dispatch_date_time,arrive_date_time,"
			+ "station,incident_type_description,incident_group_name,incident_type_name";
	/** Fields requested from Fire_Incidents_Past_Month. Never includes address. */
	public static final String PAST_MONTH_FIELDS = "OBJECTID,incident_number,dispatch_date_time,arrive_date_time,"
			+ "station_name,incident_group_name,incident_type_name";

	private static final Pattern STATION_NAME = Pattern.compile("(?i)\\s*station\\s*0*(\\d{1,3})\\s*");

	private IncidentParser() {}

	/** Returns "S14" for 14, 14.0, or "Station 14"; null when missing or not one of the known stations. */
	public static String normalizeStation(Object raw, Set<String> knownStationIds) {
		Integer number = null;
		if (raw instanceof Number) {
			number = Integer.valueOf(((Number) raw).intValue());
		} else if (raw instanceof String) {
			Matcher m = STATION_NAME.matcher((String) raw);
			if (m.matches()) {
				number = Integer.valueOf(m.group(1));
			}
		}
		if (number == null) {
			return null;
		}
		String id = String.format(Locale.US, "S%02d", number);
		return knownStationIds.contains(id) ? id : null;
	}

	/**
	 * Buckets an incident into Fire, Hazardous, Alarm, or Service/Other for map colors.
	 * Pre-2026 records only have typeDescription; newer ones have groupName and typeName.
	 * Alarms are checked first because "Alarm system activation, no fire" and detector
	 * activation/malfunction descriptions mention "fire" without being one.
	 */
	public static String classify(String typeDescription, String groupName, String typeName) {
		String text = (clean(typeDescription) + " " + clean(groupName) + " " + clean(typeName)).toLowerCase();
		if (text.contains("alarm") || text.contains("detector") || text.contains("activation")
				|| text.contains("malfunction") || text.contains("false") || text.contains("no fire")) {
			return ALARM;
		}
		if (text.contains("fire")) {
			return FIRE;
		}
		if (text.contains("hazard") || text.contains("gas") || text.contains("spill") || text.contains("leak")
				|| text.contains("power line") || text.contains("wire") || text.contains("electric")
				|| text.contains("carbon monoxide") || text.contains("chemical")) {
			return HAZARDOUS;
		}
		return SERVICE;
	}

	/**
	 * Builds an Incident from one ArcGIS feature, or returns null when the feature has no
	 * dispatch time or no point geometry. Coordinates are rounded to 3 decimals here, at ingest.
	 */
	public static Incident fromFeature(Map<String, Object> feature, Set<String> knownStationIds) {
		Map<String, Object> attrs = ArcGisClient.attributes(feature);
		Map<String, Object> geom = ArcGisClient.geometry(feature);
		Object dispatch = attrs.get("dispatch_date_time");
		if (!(dispatch instanceof Number) || geom == null
				|| !(geom.get("x") instanceof Number) || !(geom.get("y") instanceof Number)) {
			return null;
		}
		Object arrive = attrs.get("arrive_date_time");
		Object rawStation = attrs.containsKey("station") ? attrs.get("station") : attrs.get("station_name");
		Object number = attrs.get("incident_number");
		return new Incident(
				number == null ? "" : number.toString().trim(),
				((Number) dispatch).longValue(),
				arrive instanceof Number ? Long.valueOf(((Number) arrive).longValue()) : null,
				classify(asString(attrs.get("incident_type_description")),
						asString(attrs.get("incident_group_name")),
						asString(attrs.get("incident_type_name"))),
				round3(((Number) geom.get("y")).doubleValue()),
				round3(((Number) geom.get("x")).doubleValue()),
				normalizeStation(rawStation, knownStationIds));
	}

	/** Rounds to 3 decimals (about 100 m of latitude). */
	public static double round3(double value) {
		return Math.round(value * 1000.0) / 1000.0;
	}

	private static String asString(Object o) {
		return o == null ? null : o.toString();
	}

	private static String clean(String s) {
		return s == null ? "" : s.trim();
	}
}
