/*
 * Copyright 2014-2016 Fukurou Mishiranu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chan.text;

import java.util.Locale;

import chan.annotation.Extendable;
import chan.annotation.Public;
import chan.util.StringUtils;

@Public
public final class GroupParser {
	@Extendable
	public interface Callback {
		@Extendable
		public boolean onStartElement(GroupParser parser, String tagName, String attrs) throws ParseException;

		@Extendable
		public void onEndElement(GroupParser parser, String tagName) throws ParseException;

		@Extendable
		public void onText(GroupParser parser, String source, int start, int end) throws ParseException;

		@Extendable
		public void onGroupComplete(GroupParser parser, String text) throws ParseException;
	}

	private final String source;
	private final Callback callback;

	private final StringBuilder groupBuilder = new StringBuilder();

	private String groupTagName;
	private int groupCount = -1;

	private static final int MARK_STATE_NONE = 0;
	private static final int MARK_STATE_MARK = 1;
	private static final int MARK_STATE_RESET = 2;

	private boolean markAvailable = false;
	private int markCalled = MARK_STATE_NONE;
	private int markIndex = 0;

	@Public
	public static void parse(String source, Callback callback) throws ParseException {
		try {
			new GroupParser(source, callback).convert();
		} catch (RuntimeException e) {
			throw new ParseException(e);
		}
	}

	private GroupParser(String source, Callback callback) {
		this.source = StringUtils.emptyIfNull(source);
		this.callback = callback;
	}

	private void convert() throws ParseException {
		String source = this.source;
		String lowerCaseSource = null;
		int length = source.length();
		int index = source.indexOf('<');
		if (index > 0) {
			onText(0, index);
		}
		char[] tagNameEndCharacters = {' ', '\r', '\n', '\t'};
		char[] tagStartEnd = {'<', '>'};
		while (index != -1) {
			char next = source.charAt(index + 1);
			if (next == '!') {
				// Skip comment
				if (source.startsWith("<!--", index)) {
					index = source.indexOf("-->", index);
				} else {
					index = source.indexOf(">", index);
				}
				index = source.indexOf('<', index);
			} else {
				int start = index;
				int end = -1;
				boolean endsWithGt = true;
				boolean inApostrophes = false;
				boolean inQuotes = false;
				// Find tag end including cases when <> are a part of attribute
				// E.g. <span onlick="test.innerHTML='<p>test</p>'">
				for (int i = start + 1, to = Math.min(index + 500, length); i < to; i++) {
					char c = source.charAt(i);
					if (c == '"' && !inApostrophes) {
						inQuotes = !inQuotes;
					} else if (c == '\'' && !inQuotes) {
						inApostrophes = !inApostrophes;
					} else if (c == '<' && !inApostrophes && !inQuotes) {
						// Malformed HTML, e.g. <span style="color: #fff"<p>test</p>
						end = i - 1;
						endsWithGt = false;
						break;
					} else if (c == '>' && !inApostrophes && !inQuotes) {
						end = i;
						break;
					}
				}
				if (end == -1) {
					end = StringUtils.nearestIndexOf(source, start + 1, tagStartEnd);
					if (end == -1) {
						end = Math.min(start + 50, length);
						throw new ParseException("Malformed HTML after " + start + ": end of tag was not found ("
								+ source.substring(start, end) + ")");
					}
					endsWithGt = source.charAt(end) == '>';
				}
				if (end - start <= 1) {
					// Empty tag, handle as text characters
					onText(start, start + 1);
					index = source.indexOf('<', start + 1);
					continue;
				}
				boolean close = next == '/';
				String fullTag = source.substring(start + (close ? 2 : 1), end + (endsWithGt ? 0 : 1));
				String tagName = fullTag;
				String attrs = null;
				int t = StringUtils.nearestIndexOf(fullTag, 0, tagNameEndCharacters);
				if (t >= 0) {
					tagName = fullTag.substring(0, t);
					if (!close) {
						attrs = fullTag.substring(t + 1);
					}
				} else {
					t = fullTag.indexOf('/');
					if (t >= 0) {
						tagName = fullTag.substring(0, t);
					}
				}
				tagName = tagName.toLowerCase(Locale.US);
				if (!close && ("script".equals(tagName) || "style".equals(tagName))) {
					if (lowerCaseSource == null) {
						lowerCaseSource = source.toLowerCase(Locale.US);
					}
					index = lowerCaseSource.indexOf("</" + tagName, index + 1);
					if (index == -1) {
						throw new ParseException("Can't find " + tagName + " closing after " + start);
					}
					end = index + 3 + tagName.length();
				} else {
					markCalled = MARK_STATE_NONE;
					markAvailable = true;
					if (close) {
						onEndElement(tagName, start, end + 1);
					} else {
						onStartElement(tagName, attrs, start, end + 1);
					}
					markAvailable = false;
					if (markCalled == MARK_STATE_MARK) {
						markIndex = start;
					} else if (markCalled == MARK_STATE_RESET) {
						index = markIndex;
						continue;
					}
				}
				index = source.indexOf('<', end);
				start = end + 1;
				end = index >= 0 ? index : length;
				if (start < end) {
					onText(start, end);
				}
			}
		}
	}

	private boolean isGroupMode() {
		return this.groupTagName != null;
	}

	private void onStartElement(String tagName, String attrs, int start, int end) throws ParseException {
		if (isGroupMode()) {
			if (tagName.equals(this.groupTagName)) {
				this.groupCount++;
			}
			this.groupBuilder.append(source, start, end);
		} else {
			boolean groupStart = callback.onStartElement(this, tagName, attrs);
			if (groupStart) {
				groupTagName = tagName;
				groupCount = 1;
				groupBuilder.setLength(0);
			}
		}
	}

	private void onEndElement(String tagName, int start, int end) throws ParseException {
		if (!isGroupMode()) {
			callback.onEndElement(this, tagName);
		} else if (tagName.equals(groupTagName)) {
			groupCount--;
			if (groupCount == 0) {
				callback.onGroupComplete(this, groupBuilder.toString());
				groupTagName = null;
			}
		}
		if (isGroupMode()) {
			groupBuilder.append(source, start, end);
		}
	}

	private void onText(int start, int end) throws ParseException {
		if (isGroupMode()) {
			groupBuilder.append(source, start, end);
		} else {
			callback.onText(this, source, start, end);
		}
	}

	private void checkMarkAvailable() {
		if (!markAvailable) {
			throw new IllegalStateException("This method can only be called in onStartElement or onEndElement methods");
		}
	}

	@Public
	public void mark() {
		checkMarkAvailable();
		markCalled = MARK_STATE_MARK;
	}

	@Public
	public void reset() {
		checkMarkAvailable();
		markCalled = MARK_STATE_RESET;
	}

	@Public
	public String getAttr(String attrs, String attr) {
		if (attrs != null) {
			int index = attrs.indexOf(attr + "=");
			if (index >= 0) {
				index += attr.length() + 1;
				char c = attrs.charAt(index);
				if (c == '\'' || c == '"') {
					int end = attrs.indexOf(c, index + 1);
					if (index < end) {
						return attrs.substring(index + 1, end);
					}
				} else {
					int endIndex = StringUtils.nearestIndexOf(attrs, index, ' ', '\r', '\n', '\t');
					if (endIndex >= index) {
						return attrs.substring(index, endIndex);
					}
					return attrs.substring(index);
				}
			}
		}
		return null;
	}
}