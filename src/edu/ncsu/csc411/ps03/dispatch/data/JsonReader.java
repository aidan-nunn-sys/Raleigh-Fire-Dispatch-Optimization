package edu.ncsu.csc411.ps03.dispatch.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal recursive-descent JSON parser, so the project needs no JSON jar.
 * Objects become LinkedHashMap, arrays become ArrayList, numbers become Double,
 * and true/false/null become Boolean/null.
 */
public class JsonReader {
	private final String text;
	private int pos;

	private JsonReader(String text) {
		this.text = text;
		this.pos = 0;
	}

	/** Parses one complete JSON document. Throws IllegalArgumentException on malformed input. */
	public static Object parse(String text) {
		if (text == null) {
			throw new IllegalArgumentException("JSON text is null");
		}
		JsonReader reader = new JsonReader(text);
		Object value = reader.readValue();
		reader.skipWhitespace();
		if (reader.pos != text.length()) {
			throw reader.error("Unexpected trailing characters");
		}
		return value;
	}

	private Object readValue() {
		skipWhitespace();
		char c = peek();
		if (c == '{') {
			return readObject();
		} else if (c == '[') {
			return readArray();
		} else if (c == '"') {
			return readString();
		} else if (c == 't') {
			expect("true");
			return Boolean.TRUE;
		} else if (c == 'f') {
			expect("false");
			return Boolean.FALSE;
		} else if (c == 'n') {
			expect("null");
			return null;
		} else if (c == '-' || (c >= '0' && c <= '9')) {
			return readNumber();
		}
		throw error(this.pos >= this.text.length() ? "Unexpected end of input" : "Unexpected character '" + c + "'");
	}

	private Map<String, Object> readObject() {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		this.pos++; // skip '{'
		skipWhitespace();
		if (peek() == '}') {
			this.pos++;
			return map;
		}
		while (true) {
			skipWhitespace();
			if (peek() != '"') {
				throw error("Expected a string key");
			}
			String key = readString();
			skipWhitespace();
			if (peek() != ':') {
				throw error("Expected ':'");
			}
			this.pos++;
			map.put(key, readValue());
			skipWhitespace();
			char c = peek();
			if (c == ',') {
				this.pos++;
			} else if (c == '}') {
				this.pos++;
				return map;
			} else {
				throw error("Expected ',' or '}'");
			}
		}
	}

	private List<Object> readArray() {
		List<Object> list = new ArrayList<Object>();
		this.pos++; // skip '['
		skipWhitespace();
		if (peek() == ']') {
			this.pos++;
			return list;
		}
		while (true) {
			list.add(readValue());
			skipWhitespace();
			char c = peek();
			if (c == ',') {
				this.pos++;
			} else if (c == ']') {
				this.pos++;
				return list;
			} else {
				throw error("Expected ',' or ']'");
			}
		}
	}

	private String readString() {
		this.pos++; // skip opening quote
		StringBuilder sb = new StringBuilder();
		while (true) {
			if (this.pos >= this.text.length()) {
				throw error("Unterminated string");
			}
			char c = this.text.charAt(this.pos++);
			if (c == '"') {
				return sb.toString();
			}
			if (c != '\\') {
				sb.append(c);
				continue;
			}
			if (this.pos >= this.text.length()) {
				throw error("Unterminated escape");
			}
			char e = this.text.charAt(this.pos++);
			switch (e) {
				case '"': sb.append('"'); break;
				case '\\': sb.append('\\'); break;
				case '/': sb.append('/'); break;
				case 'b': sb.append('\b'); break;
				case 'f': sb.append('\f'); break;
				case 'n': sb.append('\n'); break;
				case 'r': sb.append('\r'); break;
				case 't': sb.append('\t'); break;
				case 'u':
					if (this.pos + 4 > this.text.length()) {
						throw error("Short unicode escape");
					}
					try {
						sb.append((char) Integer.parseInt(this.text.substring(this.pos, this.pos + 4), 16));
					} catch (NumberFormatException nfe) {
						throw error("Bad unicode escape");
					}
					this.pos += 4;
					break;
				default:
					throw error("Bad escape '\\" + e + "'");
			}
		}
	}

	private Double readNumber() {
		int start = this.pos;
		while (this.pos < this.text.length() && "0123456789.eE+-".indexOf(this.text.charAt(this.pos)) >= 0) {
			this.pos++;
		}
		String number = this.text.substring(start, this.pos);
		try {
			return Double.valueOf(number);
		} catch (NumberFormatException nfe) {
			throw error("Bad number '" + number + "'");
		}
	}

	private void expect(String word) {
		if (!this.text.startsWith(word, this.pos)) {
			throw error("Expected '" + word + "'");
		}
		this.pos += word.length();
	}

	private void skipWhitespace() {
		while (this.pos < this.text.length() && " \t\r\n".indexOf(this.text.charAt(this.pos)) >= 0) {
			this.pos++;
		}
	}

	private char peek() {
		return this.pos < this.text.length() ? this.text.charAt(this.pos) : '\0';
	}

	private IllegalArgumentException error(String message) {
		return new IllegalArgumentException(message + " at position " + this.pos);
	}
}
