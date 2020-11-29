package chan.text;

import androidx.annotation.NonNull;
import chan.annotation.Extendable;
import chan.annotation.Public;
import chan.util.StringUtils;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Locale;

@Public
public final class GroupParser {
	@Public
	public static final class Attributes {
		private CharSequence html;

		@Public
		public String get(String attribute) {
			return extractAttr(html, attribute);
		}

		@Public
		public boolean contains(CharSequence string) {
			return StringUtils.indexOf(html, 0, string) >= 0;
		}
	}

	@Extendable
	public interface Callback {
		@Extendable
		boolean onStartElement(GroupParser parser, String tagName, Attributes attributes) throws ParseException;

		// TODO CHAN
		// Remove this method after updating
		// anonfm archiverbt chuckdfwk desustorage diochan owlchan ponychan princessluna shanachan sharechan
		// Added: 23.10.20 10:20
		@Deprecated
		@Extendable
		boolean onStartElement(GroupParser parser, String tagName, String attrs) throws ParseException;

		@Extendable
		void onEndElement(GroupParser parser, String tagName) throws ParseException;

		@Extendable
		void onText(GroupParser parser, CharSequence text) throws ParseException;

		// TODO CHAN
		// Remove this method after updating
		// anonfm archiverbt chuckdfwk desustorage diochan owlchan ponychan princessluna shanachan sharechan
		// Added: 23.10.20 10:20
		@Deprecated
		@Extendable
		void onText(GroupParser parser, String source, int start, int end) throws ParseException;

		@Extendable
		void onGroupComplete(GroupParser parser, String text) throws ParseException;
	}

	private static class SubBuilder implements CharSequence {
		private StringBuilder builder;
		private int start;
		private int end;

		@Override
		public int length() {
			return end - start;
		}

		@Override
		public char charAt(int index) {
			return builder.charAt(start + index);
		}

		@NonNull
		@Override
		public CharSequence subSequence(int start, int end) {
			if (start == 0 && end == length()) {
				return this;
			} else {
				SubBuilder result = new SubBuilder();
				result.builder = builder;
				result.start = this.start + start;
				result.end = this.start + end;
				return result;
			}
		}

		@NonNull
		@Override
		public String toString() {
			return builder.substring(start, end);
		}
	}

	private final Callback callback;

	private final StringBuilder groupBuilder = new StringBuilder();

	private String groupTagName;
	private int groupCount = -1;

	@Public
	public static void parse(String source, Callback callback) throws ParseException {
		try {
			parse(new StringReader(source), callback);
		} catch (IOException e) {
			throw new ParseException(e);
		}
	}

	@Public
	public static void parse(Reader reader, Callback callback) throws IOException, ParseException {
		new GroupParser(callback).convert(reader);
	}

	private GroupParser(Callback callback) {
		this.callback = callback;
	}

	private static class ReaderWrapper {
		public final StringBuilder stack = new StringBuilder();
		public final Reader reader;

		public boolean eof;
		public boolean readerEof;

		public ReaderWrapper(Reader reader) {
			this.reader = reader;
		}

		public char next() throws IOException {
			int newStackSize = stack.length() - 1;
			if (newStackSize >= 0) {
				char c = stack.charAt(newStackSize);
				stack.setLength(newStackSize);
				return c;
			} else if (!readerEof) {
				int ci = reader.read();
				if (ci < 0) {
					readerEof = true;
					eof = true;
				}
				return (char) ci;
			} else {
				eof = readerEof;
				return (char) -1;
			}
		}

		char readTo(StringBuilder builder, char[] stop) throws IOException {
			while (true) {
				char c = next();
				if (eof) {
					return (char) -1;
				}
				for (char s : stop) {
					if (c == s) {
						return c;
					}
				}
				if (builder != null) {
					builder.append(c);
				}
			}
		}

		void skipTo(boolean lowerCase, String end) throws IOException {
			int index = 0;
			while (true) {
				char c = next();
				if (eof) {
					break;
				}
				if (lowerCase) {
					c = Character.toLowerCase(c);
				}
				if (end.charAt(index) == c) {
					index++;
				} else if (end.charAt(0) == c) {
					index = 1;
				} else {
					index = 0;
				}
				if (index == end.length()) {
					break;
				}
			}
		}
	}

	private static final char[] CHARACTERS_TAG_NAME_END = {' ', '\r', '\n', '\t'};
	private static final char[] CHARACTERS_TAG_START_END = {'<', '>'};
	private static final char[] CHARACTERS_TAG_START = {'<'};
	private static final char[] CHARACTERS_TAG_END = {'>'};

	private void convert(Reader reader) throws IOException, ParseException {
		convertInternal(new ReaderWrapper(reader));
	}

	private void convertInternal(ReaderWrapper reader) throws IOException, ParseException {
		StringBuilder builder = new StringBuilder();
		reader.readTo(builder, CHARACTERS_TAG_START);
		if (builder.length() > 0) {
			onText(builder);
		}

		// loop is started with "<" character already read
		while (!reader.eof) {
			char next = reader.next();
			if (reader.eof) {
				break;
			}
			if (next == '!') {
				// Skip comment
				next = reader.next();
				if (reader.eof) {
					break;
				}
				boolean malformedComment = false;
				boolean commentHandled = false;
				if (next == '>') {
					next = reader.next();
					if (reader.eof) {
						break;
					}
					commentHandled = true;
				} else if (next == '-') {
					next = reader.next();
					if (reader.eof) {
						break;
					}
					if (next == '>') {
						next = reader.next();
						if (reader.eof) {
							break;
						}
						commentHandled = true;
					} else if (next != '-') {
						malformedComment = true;
					}
				} else {
					malformedComment = true;
				}
				if (!commentHandled) {
					if (malformedComment) {
						reader.readTo(null, CHARACTERS_TAG_END);
					} else {
						reader.skipTo(false, "-->");
					}
					if (reader.eof) {
						break;
					}
					next = reader.next();
					if (reader.eof) {
						break;
					}
				}
			} else {
				builder.setLength(0);
				builder.append('<');
				reader.stack.append(next);

				// Find tag end including cases when < or > is a part of attribute
				// E.g. <span onclick="test.innerHTML='<p>test</p>'">
				boolean inApostrophes = false;
				boolean inQuotes = false;
				// Found a valid end (ends with ">")
				boolean endsWithGt = true;
				boolean endFound = false;
				// Check illegal syntax in first "tryCount" characters only
				int tryCount = 500;
				for (int i = 0; i < tryCount; i++) {
					char c = reader.next();
					if (reader.eof) {
						break;
					}
					if (c == '"' && !inApostrophes) {
						inQuotes = !inQuotes;
					} else if (c == '\'' && !inQuotes) {
						inApostrophes = !inApostrophes;
					} else if (c == '<' && !inApostrophes && !inQuotes) {
						// Malformed HTML, e.g. <span style="color: #fff"<p>test</p>
						// The last "<" is going to stack
						reader.stack.append(c);
						endsWithGt = false;
						endFound = true;
						break;
					} else if (c == '>' && !inApostrophes && !inQuotes) {
						builder.append(c);
						endFound = true;
						break;
					}
					builder.append(c);
				}

				if (!endFound) {
					int index = StringUtils.nearestIndexOf(builder, 1, CHARACTERS_TAG_START_END);
					if (index >= 0) {
						// Put remaining characters to stack
						for (int i = builder.length() - 1; i >= index; i--) {
							reader.stack.append(builder.charAt(i));
						}
						builder.setLength(index + 1);
						if (builder.length() < 2) {
							throw new ParseException("Illegal parser state");
						}
						endsWithGt = builder.charAt(index) == '>';
						if (!endsWithGt) {
							reader.stack.append('<');
							builder.setLength(builder.length() - 1);
						}
					} else {
						char stop = reader.readTo(builder, CHARACTERS_TAG_START_END);
						if (reader.eof) {
							throw new ParseException("Malformed HTML: end of tag was not found (" + builder + ")");
						}
						endsWithGt = stop == '>';
						if (endsWithGt) {
							builder.append(stop);
						}
					}
				}

				if (builder.length() <= (endsWithGt ? 2 : 1)) {
					// Empty tag, handle as text characters
					onText(builder);
				} else {
					if (!endsWithGt) {
						builder.append('>');
					}
					boolean close = builder.charAt(1) == '/';
					boolean selfClose = builder.charAt(builder.length() - 2) == '/';
					int tagEnd = builder.length() - (selfClose ? 2 : 1);
					int tagNameStart = close ? 2 : 1;
					String tagName = "";
					int attrsStart = -1;
					int attrsEnd = -1;
					int tagNameEnd = StringUtils.nearestIndexOf(builder, tagNameStart, CHARACTERS_TAG_NAME_END);
					if (tagNameEnd < 0) {
						tagNameEnd = tagEnd;
					}
					if (tagNameEnd > tagNameStart) {
						tagName = builder.substring(tagNameStart, tagNameEnd);
						if (!close && tagEnd > tagNameEnd + 1) {
							attrsStart = tagNameEnd + 1;
							attrsEnd = tagEnd;
						}
					}
					// Ignore weird "</>" and "<//>" cases
					if (!tagName.isEmpty()) {
						String tagNameLower = tagName.toLowerCase(Locale.US);
						if (!close && ("script".equals(tagNameLower) || "style".equals(tagNameLower))) {
							reader.skipTo(true, "</" + tagNameLower + ">");
							if (reader.eof) {
								throw new ParseException("Can't find closing " + tagNameLower);
							}
						} else if (close) {
							onEndElement(tagNameLower, builder);
						} else {
							onStartElement(tagNameLower, builder, attrsStart, attrsEnd);
						}
					}
				}
				next = reader.next();
			}

			if (!reader.eof && next != '<') {
				builder.setLength(0);
				builder.append(next);
				reader.readTo(builder, CHARACTERS_TAG_START);
				if (builder.length() > 0) {
					onText(builder);
				}
			}
		}
	}

	private final SubBuilder workSubBuilder = new SubBuilder();
	private final Attributes workAttributes = new Attributes();
	private boolean legacyCallback = false;

	private void onStartElement(String tagName, StringBuilder source,
			int attrsStart, int attrsEnd) throws ParseException {
		if (groupTagName != null) {
			if (tagName.equals(groupTagName)) {
				groupCount++;
			}
			groupBuilder.append(source);
		} else {
			Attributes attributes = workAttributes;
			SubBuilder html = workSubBuilder;
			html.builder = source;
			html.start = attrsStart;
			html.end = attrsEnd;
			attributes.html = html;
			boolean groupStart = false;
			if (!legacyCallback) {
				try {
					groupStart = callback.onStartElement(this, tagName, attributes);
				} catch (AbstractMethodError | NoSuchMethodError e) {
					legacyCallback = true;
				}
			}
			if (legacyCallback) {
				groupStart = callback.onStartElement(this, tagName, attrsStart >= 0 ? html.toString() : null);
			}
			if (groupStart) {
				groupTagName = tagName;
				groupCount = 1;
				groupBuilder.setLength(0);
			}
		}
	}

	private void onEndElement(String tagName, CharSequence source) throws ParseException {
		if (groupTagName == null) {
			callback.onEndElement(this, tagName);
		} else if (tagName.equals(groupTagName)) {
			groupCount--;
			if (groupCount == 0) {
				callback.onGroupComplete(this, groupBuilder.toString());
				groupTagName = null;
			}
		}
		if (groupTagName != null) {
			groupBuilder.append(source);
		}
	}

	private void onText(CharSequence text) throws ParseException {
		if (groupTagName != null) {
			groupBuilder.append(text);
		} else {
			if (!legacyCallback) {
				try {
					callback.onText(this, text);
				} catch (AbstractMethodError | NoSuchMethodError e) {
					legacyCallback = true;
				}
			}
			if (legacyCallback) {
				callback.onText(this, text.toString(), 0, text.length());
			}
		}
	}

	// TODO CHAN
	// Remove this method after updating
	// anonfm archiverbt chuckdfwk desustorage diochan owlchan ponychan princessluna shanachan sharechan
	// Added: 23.10.20 10:20
	@Deprecated
	@Public
	public String getAttr(String attrs, String attr) {
		return extractAttr(attrs, attr);
	}

	public static String extractAttr(CharSequence html, String attribute) {
		if (html != null && !StringUtils.isEmpty(attribute)) {
			String find = attribute + "=";
			// Fast match \b${attr}=
			int index = -1;
			do {
				index = StringUtils.indexOf(html, index + 1, find);
			} while (index > 0 && !(html.charAt(index - 1) <= ' '));

			if (index >= 0) {
				index += attribute.length() + 1;
				char c = html.charAt(index);
				if (c == '\'' || c == '"') {
					for (int i = index + 1; i < html.length(); i++) {
						if (html.charAt(i) == c) {
							return html.subSequence(index + 1, i).toString();
						}
					}
				} else {
					int endIndex = StringUtils.nearestIndexOf(html, index, ' ', '\r', '\n', '\t');
					if (endIndex >= index) {
						return html.subSequence(index, endIndex).toString();
					}
					return html.subSequence(index, html.length()).toString();
				}
			}
		}
		return null;
	}
}
