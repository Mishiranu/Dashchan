package chan.text;

import androidx.annotation.NonNull;
import chan.annotation.Public;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Objects;

@Public
public class JsonSerial {
	private static final JsonFactory FACTORY = new JsonFactory();

	@Public
	public static Reader reader(byte[] input) throws IOException, ParseException {
		return new ReaderImpl(input);
	}

	@Public
	public static Reader reader(InputStream input) throws IOException, ParseException {
		return new ReaderImpl(input);
	}

	@Public
	public static Writer writer() throws IOException {
		return new WriterImpl();
	}

	@Public
	public static Writer writer(OutputStream output) throws IOException {
		return new WriterImpl(output);
	}

	@Public
	public enum ValueType {
		@Public SCALAR,
		@Public OBJECT,
		@Public ARRAY
	}

	@Public
	public interface Reader extends Closeable {
		@Public void startObject() throws IOException, ParseException;
		@Public void startArray() throws IOException, ParseException;
		@Public boolean endStruct() throws IOException, ParseException;
		@Public String nextName() throws IOException, ParseException;
		@Public ValueType valueType() throws IOException, ParseException;
		@Public int nextInt() throws IOException, ParseException;
		@Public long nextLong() throws IOException, ParseException;
		@Public double nextDouble() throws IOException, ParseException;
		@Public boolean nextBoolean() throws IOException, ParseException;
		@Public String nextString() throws IOException, ParseException;
		@Public void skip() throws IOException, ParseException;
	}

	@Public
	public interface Writer extends Closeable {
		@Public void startObject() throws IOException;
		@Public void endObject() throws IOException;
		@Public void startArray() throws IOException;
		@Public void endArray() throws IOException;
		@Public void name(@NonNull String name) throws IOException;
		@Public void value(int value) throws IOException;
		@Public void value(long value) throws IOException;
		@Public void value(double value) throws IOException;
		@Public void value(boolean value) throws IOException;
		@Public void value(@NonNull String value) throws IOException;
		@Public void flush() throws IOException;
		@Public byte[] build() throws IOException;
	}

	private static class ReaderImpl implements Reader {
		private final JsonParser parser;
		private String currentName;

		public ReaderImpl(byte[] input) throws IOException, ParseException {
			try {
				parser = FACTORY.createParser(input);
				nextTokenUnchecked();
			} catch (JsonProcessingException e) {
				throw new ParseException(e);
			}
		}

		public ReaderImpl(InputStream input) throws IOException, ParseException {
			try {
				parser = FACTORY.createParser(input);
				nextTokenUnchecked();
			} catch (JsonProcessingException e) {
				throw new ParseException(e);
			}
		}

		private void nextTokenUnchecked() throws IOException, ParseException {
			try {
				while (true) {
					JsonToken token = parser.nextToken();
					if (token == JsonToken.FIELD_NAME) {
						String name = parser.getCurrentName();
						token = parser.nextToken();
						if (token != JsonToken.VALUE_NULL) {
							currentName = name;
							break;
						}
					} else if (token != JsonToken.VALUE_NULL) {
						break;
					}
				}
			} catch (JsonProcessingException e) {
				throw new ParseException(e);
			}
		}

		private boolean checkToken(JsonToken token) {
			if (currentName != null) {
				return token == JsonToken.FIELD_NAME;
			} else {
				return parser.hasToken(token);
			}
		}

		private void throwIllegalState() throws ParseException {
			throw new ParseException("Illegal state: " + (currentName != null
					? JsonToken.FIELD_NAME : parser.getCurrentToken()));
		}

		@Override
		public void startObject() throws IOException, ParseException {
			if (checkToken(JsonToken.START_OBJECT)) {
				nextTokenUnchecked();
			} else {
				throwIllegalState();
			}
		}

		@Override
		public void startArray() throws IOException, ParseException {
			if (checkToken(JsonToken.START_ARRAY)) {
				nextTokenUnchecked();
			} else {
				throwIllegalState();
			}
		}

		@Override
		public boolean endStruct() throws IOException, ParseException {
			JsonToken token = parser.getCurrentToken();
			if (token == null) {
				throwIllegalState();
			} else if (token.isStructEnd()) {
				nextTokenUnchecked();
				return true;
			}
			return false;
		}

		@Override
		public String nextName() throws IOException, ParseException {
			try {
				if (checkToken(JsonToken.FIELD_NAME)) {
					String name = currentName;
					if (name == null) {
						name = parser.getCurrentName();
						nextTokenUnchecked();
					} else {
						currentName = null;
					}
					return name;
				} else {
					throwIllegalState();
					return null;
				}
			} catch (JsonProcessingException e) {
				throw new ParseException(e);
			}
		}

		@Override
		public ValueType valueType() throws ParseException {
			if (currentName != null) {
				throwIllegalState();
			}
			JsonToken token = parser.getCurrentToken();
			if (token == null) {
				throwIllegalState();
			} else if (token.isScalarValue()) {
				return ValueType.SCALAR;
			} else if (token == JsonToken.START_OBJECT) {
				return ValueType.OBJECT;
			} else if (token == JsonToken.START_ARRAY) {
				return ValueType.ARRAY;
			}
			throwIllegalState();
			return null;
		}

		@Override
		public int nextInt() throws IOException, ParseException {
			try {
				if (checkToken(JsonToken.VALUE_NUMBER_INT)) {
					int value = parser.getIntValue();
					nextTokenUnchecked();
					return value;
				} else if (checkToken(JsonToken.VALUE_NUMBER_FLOAT)) {
					int value = (int) parser.getFloatValue();
					nextTokenUnchecked();
					return value;
				} else if (checkToken(JsonToken.VALUE_STRING)) {
					int value;
					try {
						value = Integer.parseInt(parser.getText());
					} catch (NumberFormatException e) {
						throw new ParseException(e);
					}
					nextTokenUnchecked();
					return value;
				} else {
					throwIllegalState();
					return 0;
				}
			} catch (JsonProcessingException e) {
				throw new ParseException(e);
			}
		}

		@Override
		public long nextLong() throws IOException, ParseException {
			try {
				if (checkToken(JsonToken.VALUE_NUMBER_INT)) {
					long value = parser.getLongValue();
					nextTokenUnchecked();
					return value;
				} else if (checkToken(JsonToken.VALUE_NUMBER_FLOAT)) {
					long value = (long) parser.getDoubleValue();
					nextTokenUnchecked();
					return value;
				} else if (checkToken(JsonToken.VALUE_STRING)) {
					long value;
					try {
						value = Long.parseLong(parser.getText());
					} catch (NumberFormatException e) {
						throw new ParseException(e);
					}
					nextTokenUnchecked();
					return value;
				} else {
					throwIllegalState();
					return 0;
				}
			} catch (JsonProcessingException e) {
				throw new ParseException(e);
			}
		}

		@Override
		public double nextDouble() throws IOException, ParseException {
			try {
				if (checkToken(JsonToken.VALUE_NUMBER_INT)) {
					long value = parser.getLongValue();
					nextTokenUnchecked();
					return value;
				} else if (checkToken(JsonToken.VALUE_NUMBER_FLOAT)) {
					double value = parser.getDoubleValue();
					nextTokenUnchecked();
					return value;
				} else if (checkToken(JsonToken.VALUE_STRING)) {
					double value;
					try {
						value = Double.parseDouble(parser.getText());
					} catch (NumberFormatException e) {
						throw new ParseException(e);
					}
					nextTokenUnchecked();
					return value;
				} else {
					throwIllegalState();
					return 0;
				}
			} catch (JsonProcessingException e) {
				throw new ParseException(e);
			}
		}

		@Override
		public boolean nextBoolean() throws IOException, ParseException {
			try {
				if (checkToken(JsonToken.VALUE_TRUE)) {
					nextTokenUnchecked();
					return true;
				} else if (checkToken(JsonToken.VALUE_FALSE)) {
					nextTokenUnchecked();
					return false;
				} else if (checkToken(JsonToken.VALUE_NUMBER_INT)) {
					int value = parser.getIntValue();
					nextTokenUnchecked();
					return value != 0;
				} else if (checkToken(JsonToken.VALUE_STRING)) {
					int value;
					String text = parser.getText();
					String textLower = text.toLowerCase(Locale.US);
					if ("true".equals(textLower)) {
						value = 1;
					} else if ("false".equals(textLower)) {
						value = 0;
					} else {
						try {
							value = Integer.parseInt(parser.getText());
						} catch (NumberFormatException e) {
							throw new ParseException(e);
						}
					}
					nextTokenUnchecked();
					return value != 0;
				} else {
					throwIllegalState();
					return false;
				}
			} catch (JsonProcessingException e) {
				throw new ParseException(e);
			}
		}

		@Override
		public String nextString() throws IOException, ParseException {
			try {
				if (checkToken(JsonToken.VALUE_STRING)) {
					String value = parser.getText();
					nextTokenUnchecked();
					return value;
				} else {
					if (currentName == null) {
						JsonToken token = parser.getCurrentToken();
						if (token != null && token.isScalarValue()) {
							String value = parser.getText();
							nextTokenUnchecked();
							return value;
						}
					}
					throwIllegalState();
					return null;
				}
			} catch (JsonProcessingException e) {
				throw new ParseException(e);
			}
		}

		@Override
		public void skip() throws IOException, ParseException {
			try {
				if (currentName != null) {
					throwIllegalState();
				}
				JsonToken token = parser.getCurrentToken();
				if (token == null || token == JsonToken.FIELD_NAME || token.isStructEnd()) {
					throwIllegalState();
				} else if (token.isStructStart()) {
					parser.skipChildren();
				}
				nextTokenUnchecked();
			} catch (JsonProcessingException e) {
				throw new ParseException(e);
			}
		}

		@Override
		public void close() throws IOException {
			parser.close();
		}
	}

	private static class WriterImpl implements Writer {
		private final ByteArrayOutputStream output;
		private final JsonGenerator generator;

		public WriterImpl() throws IOException {
			output = new ByteArrayOutputStream();
			generator = FACTORY.createGenerator(output);
		}

		public WriterImpl(OutputStream output) throws IOException {
			this.output = null;
			generator = FACTORY.createGenerator(output);
		}

		@Override
		public void startObject() throws IOException {
			generator.writeStartObject();
		}

		@Override
		public void endObject() throws IOException {
			generator.writeEndObject();
		}

		@Override
		public void startArray() throws IOException {
			generator.writeStartArray();
		}

		@Override
		public void endArray() throws IOException {
			generator.writeEndArray();
		}

		@Override
		public void name(@NonNull String name) throws IOException {
			Objects.requireNonNull(name);
			generator.writeFieldName(name);
		}

		@Override
		public void value(int value) throws IOException {
			generator.writeNumber(value);
		}

		@Override
		public void value(long value) throws IOException {
			generator.writeNumber(value);
		}

		@Override
		public void value(double value) throws IOException {
			generator.writeNumber(value);
		}

		@Override
		public void value(boolean value) throws IOException {
			generator.writeBoolean(value);
		}

		@Override
		public void value(@NonNull String value) throws IOException {
			Objects.requireNonNull(value);
			generator.writeString(value);
		}

		@Override
		public void flush() throws IOException {
			generator.flush();
		}

		@Override
		public byte[] build() throws IOException {
			flush();
			return output.toByteArray();
		}

		@Override
		public void close() throws IOException {
			generator.close();
		}
	}
}
