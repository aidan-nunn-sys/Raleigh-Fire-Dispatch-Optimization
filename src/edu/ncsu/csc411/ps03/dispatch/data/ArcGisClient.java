package edu.ncsu.csc411.ps03.dispatch.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Polite access to ArcGIS REST query endpoints. Every request sends an identifying
 * User-Agent and uses 10 s timeouts, and pages are fetched one at a time.
 */
public class ArcGisClient {
	public static final String USER_AGENT = "CSC411-PS03-DispatchDemo (educational)";
	public static final int TIMEOUT_MS = 10000;
	public static final int PAGE_SIZE = 2000;

	/** One parsed page of a query response. */
	public static class Page {
		public final List<Map<String, Object>> features;
		public final boolean exceededTransferLimit;

		Page(List<Map<String, Object>> features, boolean exceededTransferLimit) {
			this.features = features;
			this.exceededTransferLimit = exceededTransferLimit;
		}
	}

	/** Builds a query URL. extraParams is appended as-is, e.g. "&orderByFields=OBJECTID". */
	public static String buildQueryUrl(String layerUrl, String where, String outFields, String extraParams, int offset) {
		return layerUrl + "/query?where=" + encode(where)
				+ "&outFields=" + encode(outFields)
				+ "&outSR=4326&f=json"
				+ "&resultOffset=" + offset
				+ "&resultRecordCount=" + PAGE_SIZE
				+ (extraParams == null ? "" : extraParams);
	}

	/** Parses one response body. An ArcGIS {"error": ...} body or non-JSON text becomes an IOException. */
	@SuppressWarnings("unchecked")
	public static Page parsePage(String json) throws IOException {
		Object root;
		try {
			root = JsonReader.parse(json);
		} catch (IllegalArgumentException iae) {
			throw new IOException("Unreadable ArcGIS response: " + iae.getMessage());
		}
		if (!(root instanceof Map)) {
			throw new IOException("ArcGIS response is not a JSON object");
		}
		Map<String, Object> map = (Map<String, Object>) root;
		if (map.get("error") instanceof Map) {
			throw new IOException("ArcGIS error: " + ((Map<String, Object>) map.get("error")).get("message"));
		}
		List<Map<String, Object>> features = new ArrayList<Map<String, Object>>();
		if (map.get("features") instanceof List) {
			for (Object feature : (List<Object>) map.get("features")) {
				if (feature instanceof Map) {
					features.add((Map<String, Object>) feature);
				}
			}
		}
		return new Page(features, Boolean.TRUE.equals(map.get("exceededTransferLimit")));
	}

	/** Fetches every page of a query, one request at a time. */
	public List<Map<String, Object>> queryAll(String layerUrl, String where, String outFields, String extraParams)
			throws IOException {
		List<Map<String, Object>> all = new ArrayList<Map<String, Object>>();
		int offset = 0;
		while (true) {
			Page page = parsePage(get(buildQueryUrl(layerUrl, where, outFields, extraParams, offset)));
			all.addAll(page.features);
			if (!page.exceededTransferLimit || page.features.isEmpty()) {
				return all;
			}
			offset += page.features.size();
		}
	}

	/** Plain GET with the project's User-Agent and timeouts. Protected so tests can fake it. */
	protected String get(String url) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		try {
			conn.setRequestProperty("User-Agent", USER_AGENT);
			conn.setConnectTimeout(TIMEOUT_MS);
			conn.setReadTimeout(TIMEOUT_MS);
			int code = conn.getResponseCode();
			if (code != HttpURLConnection.HTTP_OK) {
				throw new IOException("HTTP " + code + " from " + url);
			}
			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
			try {
				StringBuilder sb = new StringBuilder();
				char[] buffer = new char[8192];
				int n;
				while ((n = in.read(buffer)) != -1) {
					sb.append(buffer, 0, n);
				}
				return sb.toString();
			} finally {
				in.close();
			}
		} finally {
			conn.disconnect();
		}
	}

	/** The feature's attributes, or an empty map. */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> attributes(Map<String, Object> feature) {
		Object attrs = feature.get("attributes");
		return attrs instanceof Map ? (Map<String, Object>) attrs : new HashMap<String, Object>();
	}

	/** The feature's geometry, or null when it has none. */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> geometry(Map<String, Object> feature) {
		Object geom = feature.get("geometry");
		return geom instanceof Map ? (Map<String, Object>) geom : null;
	}

	private static String encode(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}
}
