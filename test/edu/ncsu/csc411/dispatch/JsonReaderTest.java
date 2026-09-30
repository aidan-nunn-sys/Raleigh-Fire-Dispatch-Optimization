package edu.ncsu.csc411.dispatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import edu.ncsu.csc411.ps03.dispatch.data.JsonReader;

public class JsonReaderTest {

	@Test
	public void parsesObjectWithEveryValueType() {
		Map<?, ?> obj = (Map<?, ?>) JsonReader.parse(
				"{\"a\": 1, \"b\": \"x\", \"c\": true, \"d\": false, \"e\": null}");
		assertEquals(5, obj.size());
		assertEquals(1.0, (Double) obj.get("a"), 0.0);
		assertEquals("x", obj.get("b"));
		assertEquals(Boolean.TRUE, obj.get("c"));
		assertEquals(Boolean.FALSE, obj.get("d"));
		assertTrue(obj.containsKey("e"));
		assertNull(obj.get("e"));
	}

	@Test
	public void parsesNestedArraysAndObjects() {
		Map<?, ?> obj = (Map<?, ?>) JsonReader.parse(
				"{\"features\":[{\"attributes\":{\"station\":14}},{\"geometry\":{\"x\":-78.6,\"y\":35.7}}],"
				+ "\"empty\":[],\"none\":{}}");
		List<?> features = (List<?>) obj.get("features");
		assertEquals(2, features.size());
		Map<?, ?> attrs = (Map<?, ?>) ((Map<?, ?>) features.get(0)).get("attributes");
		assertEquals(14.0, (Double) attrs.get("station"), 0.0);
		Map<?, ?> geom = (Map<?, ?>) ((Map<?, ?>) features.get(1)).get("geometry");
		assertEquals(-78.6, (Double) geom.get("x"), 1e-12);
		assertTrue(((List<?>) obj.get("empty")).isEmpty());
		assertTrue(((Map<?, ?>) obj.get("none")).isEmpty());
	}

	@Test
	public void decodesEscapes() {
		Object s = JsonReader.parse("\"q\\\" b\\\\ s\\/ n\\n t\\t u\\u00e9\"");
		assertEquals("q\" b\\ s/ n\n t\t ué", s);
	}

	@Test
	public void parsesNumbers() {
		List<?> nums = (List<?>) JsonReader.parse("[0, -12, 3.25, 1.5e3, -2E-2, 1787717693000]");
		assertEquals(0.0, (Double) nums.get(0), 0.0);
		assertEquals(-12.0, (Double) nums.get(1), 0.0);
		assertEquals(3.25, (Double) nums.get(2), 0.0);
		assertEquals(1500.0, (Double) nums.get(3), 0.0);
		assertEquals(-0.02, (Double) nums.get(4), 1e-15);
		assertEquals(1787717693000.0, (Double) nums.get(5), 0.0);
	}

	@Test
	public void toleratesWhitespace() {
		Map<?, ?> obj = (Map<?, ?>) JsonReader.parse(" \n\t{ \"a\" :\r\n [ 1 , 2 ] } \n");
		assertEquals(2, ((List<?>) obj.get("a")).size());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsMissingValue() {
		JsonReader.parse("{\"a\": }");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsUnterminatedString() {
		JsonReader.parse("\"abc");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsTrailingGarbage() {
		JsonReader.parse("{} x");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsTrailingComma() {
		JsonReader.parse("[1,2,]");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsMisspelledLiteral() {
		JsonReader.parse("nul");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsEmptyInput() {
		JsonReader.parse("");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsHtml() {
		JsonReader.parse("<html><body>502 Bad Gateway</body></html>");
	}
}
