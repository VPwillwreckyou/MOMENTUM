package dev.momentum.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small dependency-free JSON parser/serializer for the MOMENTUM service. */
public final class Json {
    private Json() {}

    public static Object parse(String input) {
        Parser parser = new Parser(input);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonException("Unexpected trailing content at position " + parser.index);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String input) {
        Object parsed = parse(input);
        if (!(parsed instanceof Map<?, ?>)) throw new JsonException("Expected a JSON object.");
        return (Map<String, Object>) parsed;
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString();
    }

    public static String pretty(Object value) {
        StringBuilder out = new StringBuilder();
        writePretty(out, value, 0);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            writeString(out, text);
        } else if (value instanceof Boolean bool) {
            out.append(bool);
        } else if (value instanceof Number number) {
            writeNumber(out, number);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                writeString(out, String.valueOf(entry.getKey()));
                out.append(':');
                writeValue(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) out.append(',');
                first = false;
                writeValue(out, item);
            }
            out.append(']');
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeNumber(StringBuilder out, Number number) {
        if (number instanceof Double d && (d.isNaN() || d.isInfinite())) {
            out.append("null");
        } else if (number instanceof Float f && (f.isNaN() || f.isInfinite())) {
            out.append("null");
        } else {
            out.append(number.toString());
        }
    }

    private static void writePretty(StringBuilder out, Object value, int depth) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append("{\n");
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                indent(out, depth + 1);
                writeString(out, String.valueOf(entry.getKey()));
                out.append(": ");
                writePretty(out, entry.getValue(), depth + 1);
                if (++index < map.size()) out.append(',');
                out.append('\n');
            }
            indent(out, depth);
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            List<Object> items = new ArrayList<>();
            for (Object item : iterable) items.add(item);
            if (items.isEmpty()) {
                out.append("[]");
                return;
            }
            out.append("[\n");
            for (int index = 0; index < items.size(); index++) {
                indent(out, depth + 1);
                writePretty(out, items.get(index), depth + 1);
                if (index < items.size() - 1) out.append(',');
                out.append('\n');
            }
            indent(out, depth);
            out.append(']');
        } else {
            writeValue(out, value);
        }
    }

    private static void indent(StringBuilder out, int depth) {
        out.append("  ".repeat(depth));
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        out.append('"');
    }

    public static final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public JsonException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String input;
        private int index;

        private Parser(String input) {
            this.input = input;
        }

        private boolean atEnd() {
            return index >= input.length();
        }

        private void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(input.charAt(index))) index++;
        }

        private Object parseValue() {
            skipWhitespace();
            if (atEnd()) throw new JsonException("Unexpected end of JSON.");
            char character = input.charAt(index);
            return switch (character) {
                case '{' -> parseObjectValue();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Object parseLiteral(String literal, Object value) {
            if (!input.startsWith(literal, index)) throw new JsonException("Invalid literal at position " + index);
            index += literal.length();
            return value;
        }

        private Map<String, Object> parseObjectValue() {
            Map<String, Object> map = new LinkedHashMap<>();
            index++;
            skipWhitespace();
            if (!atEnd() && input.charAt(index) == '}') {
                index++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (atEnd() || input.charAt(index) != '"') throw new JsonException("Expected object key at position " + index);
                String key = parseString();
                skipWhitespace();
                if (atEnd() || input.charAt(index) != ':') throw new JsonException("Expected ':' at position " + index);
                index++;
                map.put(key, parseValue());
                skipWhitespace();
                if (atEnd()) throw new JsonException("Unterminated object.");
                char separator = input.charAt(index++);
                if (separator == '}') return map;
                if (separator != ',') throw new JsonException("Expected ',' or '}' at position " + (index - 1));
            }
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            index++;
            skipWhitespace();
            if (!atEnd() && input.charAt(index) == ']') {
                index++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (atEnd()) throw new JsonException("Unterminated array.");
                char separator = input.charAt(index++);
                if (separator == ']') return list;
                if (separator != ',') throw new JsonException("Expected ',' or ']' at position " + (index - 1));
            }
        }

        private String parseString() {
            index++;
            StringBuilder out = new StringBuilder();
            while (!atEnd()) {
                char character = input.charAt(index++);
                if (character == '"') return out.toString();
                if (character == '\\') {
                    if (atEnd()) throw new JsonException("Unterminated escape sequence.");
                    char escaped = input.charAt(index++);
                    switch (escaped) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (index + 4 > input.length()) throw new JsonException("Invalid unicode escape.");
                            String hex = input.substring(index, index + 4);
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException exception) {
                                throw new JsonException("Invalid unicode escape.");
                            }
                            index += 4;
                        }
                        default -> throw new JsonException("Invalid escape character.");
                    }
                } else if (character < 0x20) {
                    throw new JsonException("Control character in string.");
                } else {
                    out.append(character);
                }
            }
            throw new JsonException("Unterminated string.");
        }

        private Number parseNumber() {
            int start = index;
            if (!atEnd() && input.charAt(index) == '-') index++;
            while (!atEnd() && Character.isDigit(input.charAt(index))) index++;
            boolean decimal = false;
            if (!atEnd() && input.charAt(index) == '.') {
                decimal = true;
                index++;
                while (!atEnd() && Character.isDigit(input.charAt(index))) index++;
            }
            if (!atEnd() && (input.charAt(index) == 'e' || input.charAt(index) == 'E')) {
                decimal = true;
                index++;
                if (!atEnd() && (input.charAt(index) == '+' || input.charAt(index) == '-')) index++;
                while (!atEnd() && Character.isDigit(input.charAt(index))) index++;
            }
            if (start == index) throw new JsonException("Invalid JSON value at position " + start);
            String number = input.substring(start, index);
            try {
                if (decimal) return Double.parseDouble(number);
                long parsed = Long.parseLong(number);
                if (parsed >= Integer.MIN_VALUE && parsed <= Integer.MAX_VALUE) return (int) parsed;
                return parsed;
            } catch (NumberFormatException exception) {
                throw new JsonException("Invalid number: " + number);
            }
        }
    }
}
