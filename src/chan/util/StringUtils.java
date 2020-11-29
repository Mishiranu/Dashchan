package chan.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import chan.annotation.Extendable;
import chan.annotation.Public;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.text.HtmlParser;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	// TODO CHAN
	// Remove this method after updating
	// archiverbt dangeru desustorage nulldvachin
	// Added: 05.10.20 18:45
	@SuppressWarnings({"StringEquality", "EqualsReplaceableByObjectsCall"})
	@Deprecated
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
	public static int nearestIndexOf(CharSequence string, int start, char... what) {
		int length = string.length();
		for (int i = start; i < length; i++) {
			char c = string.charAt(i);
			for (char check : what) {
				if (check == c) {
					return i;
				}
			}
		}
		return -1;
	}

	public static int indexOf(CharSequence string, int fromIndex, CharSequence what) {
		int target = what.length();
		if (target == 0) {
			return fromIndex;
		}
		int length = string.length();
		int count = 0;
		for (int i = fromIndex; i < length; i++) {
			char c = string.charAt(i);
			if (c == what.charAt(count)) {
				if (++count == target) {
					return i - target + 1;
				}
			} else {
				count = 0;
			}
		}
		return -1;
	}

	@Extendable
	public interface ReplacementCallback {
		@Extendable
		String getReplacement(Matcher matcher);
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

	public static String formatHex(byte[] bytes) {
		if (bytes != null) {
			StringBuilder builder = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) {
				if ((b & 0xf0) == 0) {
					builder.append(0);
				}
				builder.append(Integer.toString(b & 0xff, 16));
			}
			return builder.toString();
		}
		return null;
	}

	public static String formatFileSize(long size, boolean upperCase) {
		size /= 1000;
		return size >= 1000 ? String.format(Locale.US, "%.1f", size / 1000f) + " MB"
				: size + (upperCase ? " KB" : " kB");
	}

	public static String formatFileSizeMegabytes(long size) {
		return String.format(Locale.US, "%.2f", size / 1000f / 1000f) + " MB";
	}

	public static String stripTrailingZeros(String string) {
		int length = string.length();
		for (int i = length - 1; i >= 0; i--) {
			char c = string.charAt(i);
			if (c == '0') {
				length = i;
			} else if (c == '.') {
				length = i;
				break;
			} else {
				break;
			}
		}
		return length < string.length() ? string.substring(0, length) : string;
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
		if (string != null) {
			StringBuilder builder = null;
			int length = string.length();
			for (int i = 0; i < length; i++) {
				char c = string.charAt(i);
				if (c == '\\' || c == '/' && !isPath || c == ':' ||
						c == '*' || c == '?' || c == '|' || c == '<' || c == '>') {
					if (builder == null) {
						builder = new StringBuilder(string);
					}
					builder.setCharAt(i, '_');
				}
			}
			if (builder != null) {
				return builder.toString();
			}
		}
		return string;
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

	public static String getNormalizedOriginalName(String originalName, String fileName) {
		return getNormalizedOriginalName(originalName, fileName, getFileExtension(fileName));
	}

	private static String getNormalizedOriginalName(String originalName, String fileName, String fileExtension) {
		if (!isEmpty(originalName)) {
			originalName = escapeFile(originalName, false);
			if (fileExtension == null) {
				fileExtension = getFileExtension(fileName);
			}
			if (fileExtension != null) {
				String normalizedOriginalExtension = getNormalizedExtension(getFileExtension(originalName));
				String normalizedFileExtension = getNormalizedExtension(fileExtension);
				if (!normalizedFileExtension.equals(normalizedOriginalExtension)) {
					originalName += "." + fileExtension;
				}
				if (fileName.equals(originalName)) {
					return null;
				}
			}
			return originalName;
		}
		return null;
	}

	public static String getNormalizedExtension(String extension) {
		String normalizedExtension = C.EXTENSION_TRANSFORMATION.get(extension);
		if (normalizedExtension == null) {
			normalizedExtension = extension;
		}
		return normalizedExtension;
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
		if (!isEmpty(string)) {
			ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
			clipboard.setPrimaryClip(ClipData.newPlainText(null, string));
		}
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
		if (!isEmpty(path)) {
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
		if (string == null) {
			return "";
		}
		int length = string.length();
		if (length == 0) {
			return "";
		}
		boolean checkHtmlTag = false;
		boolean checkHtmlEntity = false;
		boolean removeSpaces = false;
		for (int i = 0; i < length; i++) {
			char c = string.charAt(i);
			switch (c) {
				case '<': {
					checkHtmlTag = true;
					break;
				}
				case '>': {
					if (checkHtmlTag) {
						return HtmlParser.clear(string);
					}
					break;
				}
				case '&': {
					checkHtmlEntity = true;
					break;
				}
				case ';': {
					if (checkHtmlEntity) {
						return HtmlParser.clear(string);
					}
					break;
				}
				case ' ':
				case '\n':
				case '\r':
				case '\t': {
					removeSpaces = true;
					break;
				}
			}
		}
		if (removeSpaces) {
			StringBuilder builder = new StringBuilder();
			boolean lastSpace = true;
			for (int i = 0; i < length; i++) {
				char c = string.charAt(i);
				if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
					if (!lastSpace) {
						lastSpace = true;
						builder.append(' ');
					}
				} else {
					lastSpace = false;
					builder.append(c);
				}
			}
			if (lastSpace) {
				int builderLength = builder.length();
				if (builderLength > 0) {
					builder.setLength(builderLength - 1);
				}
			}
			return builder.toString();
		} else {
			return string;
		}
	}

	@Public
	public static String unescapeHtml(String string) {
		if (isEmpty(string)) {
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
						// Not a number, ignore exception
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

	public static CharSequence reduceEmptyLines(CharSequence text) {
		SpannableStringBuilder builder = null;
		int lineBreaks = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\n') {
				lineBreaks++;
			} else {
				if (lineBreaks > 1) {
					if (builder == null) {
						builder = new SpannableStringBuilder(text);
						text = builder;
					}
					builder.setSpan(new RelativeSizeSpan(0.75f), i - lineBreaks, i,
							SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
				lineBreaks = 0;
			}
		}
		return text;
	}
}
