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

package chan.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.SpannableStringBuilder;

import chan.annotation.Extendable;
import chan.annotation.Public;

import com.mishiranu.dashchan.text.HtmlParser;

@Public
public class StringUtils {
	public static String cutIfLongerToLine(String string, int maxLength, boolean dots) {
		string = string.replaceAll("\r", "").trim();
		int index = string.indexOf("\n");
		if (index > maxLength / 3) {
			return string.substring(0, index).trim();
		}
		if (string.length() > maxLength) {
			return string.substring(0, maxLength).trim().replaceAll(" +", " ") + (dots ? "\u2026" : "");
		}
		return string.replaceAll(" +", " ");
	}

	@Public
	public static boolean isEmpty(CharSequence string) {
		return string == null || string.length() == 0;
	}

	@Public
	public static boolean isEmptyOrWhitespace(CharSequence string) {
		return string == null || string.toString().trim().length() == 0;
	}

	@Public
	public static String emptyIfNull(CharSequence string) {
		return string == null ? "" : string.toString();
	}

	@Public
	public static String nullIfEmpty(String string) {
		return isEmpty(string) ? null : string;
	}

	@SuppressWarnings("StringEquality")
	@Public
	public static boolean equals(String first, String second) {
		return first == second || first != null && first.equals(second);
	}

	@SuppressWarnings("StringEquality")
	public static int compare(String first, String second, boolean ignoreCase) {
		if (first == second) {
			return 0;
		}
		if (first == null) {
			return -1;
		}
		if (second == null) {
			return 1;
		}
		if (ignoreCase) {
			first = first.toUpperCase(Locale.getDefault());
			second = second.toUpperCase(Locale.getDefault());
		}
		return first.compareTo(second);
	}

	@Public
	public static int nearestIndexOf(String string, int start, String... what) {
		int index = -1;
		for (String itWhat : what) {
			int itIndex = string.indexOf(itWhat, start);
			if (itIndex >= 0 && (itIndex < index || index == -1)) {
				index = itIndex;
			}
		}
		return index;
	}

	@Public
	public static int nearestIndexOf(String string, int start, char... what) {
		int index = -1;
		for (char itWhat : what) {
			int itIndex = string.indexOf(itWhat, start);
			if (itIndex >= 0 && (itIndex < index || index == -1)) {
				index = itIndex;
			}
		}
		return index;
	}

	@Extendable
	public interface ReplacementCallback {
		@Extendable
		public String getReplacement(Matcher matcher);
	}

	@Public
	public static String replaceAll(String string, String regularExpression, ReplacementCallback replacementCallback) {
		return replaceAll(string, Pattern.compile(regularExpression), replacementCallback);
	}

	@Public
	public static String replaceAll(String string, Pattern pattern, ReplacementCallback replacementCallback) {
		if (string != null) {
			if (pattern == null) {
				throw new NullPointerException("pattern is null");
			}
			if (replacementCallback == null) {
				throw new NullPointerException("replacementCallback is null");
			}
			StringBuffer buffer = null;
			Matcher matcher = pattern.matcher(string);
			while (matcher.find()) {
				if (buffer == null) {
					buffer = new StringBuffer();
				}
				String replacement = replacementCallback.getReplacement(matcher);
				if (replacement != null) {
					replacement = Matcher.quoteReplacement(replacement);
				}
				matcher.appendReplacement(buffer, replacement);
			}
			if (buffer != null) {
				matcher.appendTail(buffer);
				string = buffer.toString();
			}
		}
		return string;
	}

	public static SpannableStringBuilder appendSpan(SpannableStringBuilder builder, CharSequence text,
			Object... spans) {
		int start = builder.length();
		builder.append(text);
		int end = builder.length();
		for (Object what : spans) {
			builder.setSpan(what, start, end, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		return builder;
	}

	public static String removeSingleDot(String string) {
		if (string == null) {
			return null;
		}
		if (!string.endsWith(".")) {
			return string;
		}
		String temp = string.replace(".", "");
		if (string.length() - temp.length() == 1) {
			string = temp;
		}
		return string;
	}

	public static String escapeFile(String string, boolean isPath) {
		return string != null ? string.replaceAll(isPath ? "[:\\\\*?|<>]" : "[:\\\\/*?|<>]", "_") : null;
	}

	private static int findLinkEnd(String string, int start) {
		int end = -1;
		int braces = 0;
		boolean validEndReached = false;
		int length = string.length();
		CYCLE: for (int i = start;; i++) {
			boolean prevValidEndReached = validEndReached;
			validEndReached = false;
			char c = i < length ? string.charAt(i) : '\0';
			switch (c) {
				case '(':
				case '[':
				case '{': {
					braces++;
					break;
				}
				case ')':
				case ']':
				case '}': {
					if (--braces < 0) {
						end = i;
						break CYCLE;
					}
					break;
				}
				case '\0':
				case '\n':
				case '\r':
				case '\t':
				case ' ':
				case '<':
				case '"': {
					end = prevValidEndReached ? i - 1 : i;
					break CYCLE;
				}
				case '.':
				case ',':
				case ':':
				case ';': {
					validEndReached = true;
					break;
				}
			}
			if (c == '\0') {
				break;
			}
		}
		return end;
	}

	@Public
	public static String linkify(String string) {
		if (string != null) {
			ArrayList<int[]> candidates = null;
			int index = -1;
			int length = string.length();
			boolean insideLink = false;
			while (true) {
				if (insideLink) {
					index = string.indexOf("</a>", index + 1);
					if (index == -1) {
						break;
					}
					insideLink = false;
					continue;
				} else {
					int httpIndex = string.indexOf("http", index + 1);
					if (httpIndex == -1) {
						break;
					}
					int openLinkIndex = string.indexOf("<a ", index + 1);
					if (openLinkIndex != -1 && openLinkIndex < httpIndex) {
						insideLink = true;
						index = openLinkIndex;
						continue;
					}
					index = httpIndex;
				}
				if (index + 8 < length) {
					boolean https = string.charAt(index + 4) == 's';
					if ("://".equals(string.substring(index + 4 + (https ? 1 : 0), index + 7 + (https ? 1 : 0)))) {
						// http:// or https:// reached
						if (index >= 6) {
							String before = string.substring(index - 6, index);
							if (before.contains("href=")) continue; // Ignore <a href="https://..."> reached
						}
						int start = index + 7 + (https ? 1 : 0);
						int end = findLinkEnd(string, start);
						if (end > start) {
							if (end + 4 <= length) {
								String after = string.substring(end, end + 4);
								if ("</a>".equals(after)) {
									index = end + 3;
									continue; // Ignore <a ...>https://...</a>
								}
							}
							if (candidates == null) {
								candidates = new ArrayList<>();
							}
							candidates.add(new int[] {index, end});
							index = end - 1;
						}
					}
				}
			}
			if (candidates != null) {
				StringBuilder builder = new StringBuilder();
				int[] prev = null;
				for (int[] links : candidates) {
					int from = prev != null ? prev[1] : 0;
					builder.append(string, from, links[0]);
					builder.append("<a href=\"");
					builder.append(string, links[0], links[1]);
					builder.append("\">");
					builder.append(string, links[0], links[1]);
					builder.append("</a>");
					prev = links;
				}
				builder.append(string, prev[1], length);
				string = builder.toString();
			}
		}
		return string;
	}

	public static String fixParsedUriString(String uriString) {
		if (uriString != null) {
			int end = findLinkEnd(uriString, 0);
			if (end >= 0) {
				uriString = uriString.substring(0, end);
			}
		}
		return uriString;
	}

	public static void copyToClipboard(Context context, String string) {
		ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
		clipboard.setPrimaryClip(ClipData.newPlainText(null, string));
	}

	private static final Pattern PATTERN_BOARD_NAME = Pattern.compile("/?([\\w_-]+)/?");

	public static String validateBoardName(String boardName) {
		if (boardName != null) {
			Matcher matcher = PATTERN_BOARD_NAME.matcher(boardName);
			if (matcher.matches()) {
				return matcher.group(1);
			}
		}
		return null;
	}

	public static String getFileExtension(String path) {
		if (path != null) {
			int index1 = path.lastIndexOf('/');
			int index2 = path.lastIndexOf('.');
			if (index1 > index2 || index2 < 0) {
				return null;
			}
			return path.substring(index2 + 1).toLowerCase(Locale.US);
		}
		return null;
	}

	public static String formatBoardTitle(String chanName, String boardName, String title) {
		return '/' + (isEmpty(boardName) ? chanName : boardName) + (isEmpty(title) ? '/'
				: "/ — " + title);
	}

	public static String formatThreadTitle(String chanName, String boardName, String threadNumber) {
		return '/' + (isEmpty(boardName) ? chanName : boardName) + '/' + threadNumber;
	}

	@Public
	public static String clearHtml(String string) {
		return HtmlParser.clear(string);
	}

	@Public
	public static String unescapeHtml(String string) {
		if (StringUtils.isEmpty(string)) {
			return "";
		}
		StringBuilder builder = new StringBuilder(string.length());
		int index = 0;
		while (index < string.length()) {
			int start = string.indexOf('&', index);
			int end = start >= index ? string.indexOf(';', start) : -1;
			if (start >= index && end > start) {
				builder.append(string, index, start);
				int realStart = string.lastIndexOf('&', end);
				if (realStart > start) {
					builder.append(string, start, realStart);
					start = realStart;
				}
				int value = -1;
				String entity = string.substring(start + 1, end);
				if (entity.startsWith("#") && !entity.contains("+") && !entity.contains("-")) {
					try {
						if (entity.startsWith("#x") || entity.startsWith("#X")) {
							value = Integer.parseInt(entity.substring(2), 16);
						} else {
							value = Integer.parseInt(entity.substring(1));
						}
					} catch (NumberFormatException e) {
						// Ignore
					}
				} else {
					value = HtmlParser.SCHEMA.getEntity(entity);
					if (value == 0) {
						value = -1;
					}
				}
				if (value >= 0) {
					builder.append((char) value);
				} else {
					builder.append(string, start, end + 1);
				}
				index = end + 1;
			} else {
				builder.append(string, index, string.length());
				break;
			}
		}
		return builder.toString();
	}

	private static final MessageDigest DIGEST_SHA_256;

	static {
		try {
			DIGEST_SHA_256 = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	public static String calculateSha256(String string) {
		byte[] bytes;
		synchronized (DIGEST_SHA_256) {
			DIGEST_SHA_256.reset();
			bytes = DIGEST_SHA_256.digest(emptyIfNull(string).getBytes());
		}
		StringBuilder hashBuilder = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			if ((b & 0xf0) == 0) {
				hashBuilder.append(0);
			}
			hashBuilder.append(Integer.toString(b & 0xf, 16));
		}
		return hashBuilder.toString();
	}
}