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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.text.Editable;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.widget.EditText;

import chan.annotation.Extendable;
import chan.annotation.Public;
import chan.content.ChanMarkup;

@Extendable
public class CommentEditor {
	@Public public static final int FLAG_ONE_LINE = 0x00000001;

	private final SparseArray<Tag> tags = new SparseArray<>();
	private final SparseIntArray similar = new SparseIntArray();

	private String unorderedListMark = "- ";
	private String orderedListMark;

	public static class Tag {
		public final String open;
		public final String close;
		public final int flags;

		public Tag(String open, String close, int flags) {
			this.open = open;
			this.close = close;
			this.flags = flags;
		}
	}

	public static class FormatResult {
		public final int start;
		public final int end;

		public FormatResult(int start, int end) {
			this.start = start;
			this.end = end;
		}
	}

	@Public
	public CommentEditor() {}

	@Public
	public final void addTag(int what, String open, String close) {
		addTag(what, open, close, 0);
	}

	@Public
	public final void addTag(int what, String open, String close, int flags) {
		if (open == null || close == null) {
			throw new NullPointerException();
		}
		tags.put(what, new Tag(open, close, flags));
	}

	@Public
	public final void setUnorderedListMark(String mark) {
		unorderedListMark = mark;
	}

	@Public
	public final void setOrderedListMark(String mark) {
		orderedListMark = mark;
	}

	public final String getUnorderedListMark() {
		return unorderedListMark;
	}

	public final String getOrderedListMark() {
		return orderedListMark;
	}

	public final String getTag(int what, boolean close) {
		Tag tag = tags.get(what);
		if (tag == null) {
			return null;
		}
		return close ? tag.close : tag.open;
	}

	public String getTag(int what, boolean close, int length) {
		return getTag(what, close);
	}

	public final SparseArray<Tag> getAllTags() {
		return tags;
	}

	public final void handleSimilar(int supportedTags) {
		similar.clear();
		int size = tags.size();
		for (int i = 0; i < size; i++) {
			int what1 = tags.keyAt(i);
			if ((supportedTags & what1) == what1) {
				Tag tag1 = tags.get(what1);
				for (int j = i + 1; j < size; j++) {
					int what2 = tags.keyAt(j);
					if ((supportedTags & what2) == what2) {
						Tag tag2 = tags.get(what2);
						if (tag1.open.endsWith(tag2.open) && tag1.close.startsWith(tag2.close)) {
							similar.put(what2, what1);
						}
						if (tag2.open.endsWith(tag1.open) && tag2.close.startsWith(tag1.close)) {
							similar.put(what1, what2);
						}
					}
				}
			}
		}
	}

	public final void formatSelectedText(EditText commentView, int what) {
		int start = commentView.getSelectionStart();
		int end = commentView.getSelectionEnd();
		if (start == -1 || end == -1) {
			return;
		}
		FormatResult result = formatSelectedText(commentView.getText(), what, start, end);
		if (result != null) {
			commentView.setSelection(result.start, result.end);
		}
	}

	public FormatResult formatSelectedText(Editable editable, int what, int start, int end) {
		Tag tag = tags.get(what);
		if (tag == null) {
			return null;
		}
		boolean oneLine = (tag.flags & FLAG_ONE_LINE) == FLAG_ONE_LINE;
		if (oneLine && end > start) {
			return formatSelectedTextOneLine(editable, what, tag, start, end);
		}
		return formatSelectedTextLineDirectly(editable, what, tag, start, end);
	}

	private FormatResult formatSelectedTextOneLine(Editable editable, int what, Tag tag, int start, int end) {
		int newStart = -1;
		int newEnd = -1;
		int nextLine = start;
		String open = tag.open;
		String close = tag.close;
		for (int i = start; i <= end; i++) {
			char c = i == end ? '\n' : editable.charAt(i);
			if (c == '\n') {
				int lineStart = nextLine;
				int lineEnd = i;
				if (lineEnd > lineStart) {
					boolean mayCutStart = lineStart > start;
					boolean mayCutEnd = lineEnd < end;
					if (mayCutStart || mayCutEnd) {
						String line = editable.subSequence(lineStart, lineEnd).toString();
						boolean cutStart = mayCutStart && line.startsWith(open);
						boolean cutEnd = mayCutEnd && line.endsWith(close);
						int openLength = open.length();
						int closeLength = close.length();
						int similar = this.similar.get(what, -1);
						if (similar != -1) {
							Tag similarTag = tags.get(similar);
							if (cutStart && line.startsWith(similarTag.open) &&
									!line.startsWith(open + similarTag.open)) {
								openLength = similarTag.open.length();
							}
							if (cutEnd && line.endsWith(similarTag.close) &&
									!line.endsWith(similarTag.close + close)) {
								closeLength = similarTag.close.length();
							}
						}
						if (mayCutStart && mayCutEnd) {
							if (cutStart && cutEnd) {
								// Handle lines with open tag at start and close tag at end
								lineStart += openLength;
								lineEnd -= closeLength;
							}
						} else {
							if (cutStart) {
								lineStart += openLength;
							}
							if (cutEnd) {
								lineEnd -= closeLength;
							}
						}
					}
					FormatResult result = formatSelectedTextLineDirectly(editable, what, tag, lineStart, lineEnd);
					int startShift = result.start - lineStart;
					int endShift = result.end - lineEnd;
					end += startShift + endShift;
					i += startShift + endShift;
					if (newStart == -1) {
						newStart = result.start;
						start += startShift; // Count first start shift
					}
					newEnd = result.end;
				} else {
					if (newStart == -1) {
						newStart = start;
					}
					newEnd = end;
				}
				nextLine = i + 1;
			}
		}
		return new FormatResult(newStart, newEnd);
	}

	private FormatResult formatSelectedTextLineDirectly(Editable editable, int what, Tag tag, int start, int end) {
		int similar = this.similar.get(what, -1);
		if (similar != -1) {
			String text = editable.toString();
			Tag similarTag = tags.get(similar);
			String textBeforeSelection = text.substring(Math.max(0, start - similarTag.open.length() - 1), start);
			String textAfterSelection = text.substring(end, Math.min(text.length(),
					end + similarTag.open.length() + 1));
			if (textBeforeSelection.endsWith(similarTag.open)) {
				boolean apply;
				if (textBeforeSelection.length() == similarTag.open.length()) {
					apply = true;
				} else {
					apply = textBeforeSelection.charAt(0) != similarTag.open.charAt(similarTag.open.length() - 1);
				}
				if (apply && textAfterSelection.startsWith(similarTag.close)) {
					if (textAfterSelection.length() == similarTag.close.length()) {
						apply = true;
					} else {
						apply = textAfterSelection.charAt(similarTag.close.length()) != similarTag.close.charAt(0);
					}
					if (apply) {
						int sh = tag.open.length();
						editable.insert(start, tag.open).insert(end + sh, tag.close);
						return new FormatResult(start + sh, end + sh);
					}
				}
			}
		}
		String text = editable.toString();
		String open = tag.open;
		String close = tag.close;
		String selectedText = text.substring(start, end);
		String textBeforeSelection = text.substring(Math.max(0, start - open.length()), start);
		String textAfterSelection = text.substring(end, Math.min(text.length(), end + close.length()));
		if (textBeforeSelection.equalsIgnoreCase(open) && textAfterSelection.equalsIgnoreCase(close)) {
			editable.replace(start - open.length(), end + close.length(), selectedText);
			return new FormatResult(start - open.length(), end - open.length());
		} else {
			editable.replace(start, end, open + selectedText + close);
			return new FormatResult(start + open.length(), end + open.length());
		}
	}

	public String removeTags(String comment) {
		if (comment != null) {
			SparseArray<CommentEditor.Tag> tags = getAllTags();
			for (int i = 0; i < tags.size(); i++) {
				CommentEditor.Tag tag = tags.get(tags.keyAt(i));
				if (tag != null) {
					if (tag.open != null) {
						comment = comment.replace(tag.open, "");
					}
					if (tag.close != null) {
						comment = comment.replace(tag.close, "");
					}
				}
			}
		}
		return comment;
	}

	@Extendable
	public static class BulletinBoardCodeCommentEditor extends CommentEditor {
		@Public
		public BulletinBoardCodeCommentEditor() {
			addTag(ChanMarkup.TAG_BOLD, "[b]", "[/b]");
			addTag(ChanMarkup.TAG_ITALIC, "[i]", "[/i]");
			addTag(ChanMarkup.TAG_UNDERLINE, "[u]", "[/u]");
			addTag(ChanMarkup.TAG_OVERLINE, "[o]", "[/o]");
			addTag(ChanMarkup.TAG_STRIKE, "[s]", "[/s]");
			addTag(ChanMarkup.TAG_SUBSCRIPT, "[sub]", "[/sub]");
			addTag(ChanMarkup.TAG_SUPERSCRIPT, "[sup]", "[/sup]");
			addTag(ChanMarkup.TAG_SPOILER, "[spoiler]", "[/spoiler]");
			addTag(ChanMarkup.TAG_CODE, "[code]", "[/code]");
			addTag(ChanMarkup.TAG_ASCII_ART, "[aa]", "[/aa]");
		}
	}

	@Extendable
	public static class WakabaMarkCommentEditor extends CommentEditor {
		private static final Pattern MULTIPLE_STRIKES = Pattern.compile("^((?:\\^H)+)");

		@Public
		public WakabaMarkCommentEditor() {
			addTag(ChanMarkup.TAG_BOLD, "**", "**", FLAG_ONE_LINE);
			addTag(ChanMarkup.TAG_ITALIC, "*", "*", FLAG_ONE_LINE);
			addTag(ChanMarkup.TAG_SPOILER, "%%", "%%", FLAG_ONE_LINE);
			addTag(ChanMarkup.TAG_CODE, "`", "`", FLAG_ONE_LINE);
		}

		@Override
		public String getTag(int what, boolean close, int length) {
			String result = super.getTag(what, close, length);
			if (what == ChanMarkup.TAG_STRIKE && result == null) {
				if (close) {
					StringBuilder builder = new StringBuilder();
					for (int i = 0; i < length; i++) {
						builder.append("^H");
					}
					return builder.toString();
				} else {
					return "";
				}
			}
			return result;
		}

		@Override
		public FormatResult formatSelectedText(Editable editable, int what, int start, int end) {
			if (what == ChanMarkup.TAG_STRIKE && super.getTag(what, false) == null) {
				String text = editable.toString();
				String textAfterSelection = text.substring(end, text.length());
				Matcher matcher = MULTIPLE_STRIKES.matcher(textAfterSelection);
				if (matcher.find()) {
					String strikes = matcher.group(1);
					editable.replace(end, end + strikes.length(), "");
					return new FormatResult(start, end);
				} else {
					StringBuilder builder = new StringBuilder();
					int count = end - start;
					if (count > 0) {
						for (int i = 0; i < count; i++) {
							builder.append("^H");
						}
						editable.insert(end, builder.toString());
						return new FormatResult(start, end);
					} else {
						editable.insert(end, "^H");
						return new FormatResult(end + 2, end + 2);
					}
				}
			} else {
				return super.formatSelectedText(editable, what, start, end);
			}
		}

		@Override
		public String removeTags(String comment) {
			comment = super.removeTags(comment);
			if (comment != null) {
				comment = comment.replace("^H", "");
			}
			return comment;
		}
	}
}