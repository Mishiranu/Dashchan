package com.mishiranu.dashchan.text;

import android.net.Uri;
import android.util.Pair;
import chan.util.StringUtils;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

public class WakabaLikeHtmlBuilder {
	private static final ArrayList<Pair<String, String>> STYLES = new ArrayList<>();

	static {
		STYLES.add(new Pair<>("Photon", "https://mishiranu.github.io/Dashchan/wakaba/photon.css"));
		STYLES.add(new Pair<>("Futaba", "https://mishiranu.github.io/Dashchan/wakaba/futaba.css"));
		STYLES.add(new Pair<>("Burichan", "https://mishiranu.github.io/Dashchan/wakaba/burichan.css"));
		STYLES.add(new Pair<>("Gurochan", "https://mishiranu.github.io/Dashchan/wakaba/gurochan.css"));
	}

	private static final String CLIENT_URI = "https://github.com/Mishiranu/Dashchan/";

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yy(ccc)HH:mm:ss", Locale.US);

	static {
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("Etc/GMT"));
	}

	private final StringBuilder builder = new StringBuilder();

	public WakabaLikeHtmlBuilder(String threadTitle, String boardName, String boardTitle,
			String chanTitle, Uri threadUri, int postsCount, int filesCount) {
		StringBuilder builder = this.builder;
		builder.append("<!DOCTYPE html>\n<html>\n<head>\n")
				.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n");
		builder.append("<title>");
		if (!StringUtils.isEmpty(threadTitle)) {
			builder.append(threadTitle).append(" — ");
		}
		builder.append('/').append(boardName).append('/').append(" — ");
		if (!StringUtils.isEmpty(boardTitle)) {
			builder.append(boardTitle).append(" — ");
		}
		builder.append(chanTitle);
		builder.append("</title>\n");
		for (int i = 0; i < STYLES.size(); i++) {
			Pair<String, String> style = STYLES.get(i);
			builder.append("<link rel=\"");
			if (i > 0) {
				builder.append("alternate ");
			}
			builder.append("stylesheet\" type=\"text/css\" href=\"").append(style.second)
					.append("\" title=\"").append(style.first).append("\" />\n");
		}
		builder.append("<style type=\"text/css\">\nbody {margin: 0; padding: 8px; margin-bottom: auto;}\n")
				.append(".thumb {border: none; margin: 2px 20px; max-width: 200px; max-height: 200px;}\n")
				.append(".nothumb {float: left; background: #eee; border: 2px dashed #aaa;\n")
				.append("text-align: center; margin: 2px 20px; padding: 1em 0.5em 1em 0.5em;}\n")
				.append(".filesize {padding-left: 20px; display: inline-block;}\n")
				.append(".replyheader {padding: 0 0.25em 0 0;}\n")
				.append(".reflink a {color: inherit; text-decoration: none;}\n")
				.append(".withimage {min-width: 30em;}\n")
				.append(".postericon {max-height: 1em;}\n")
				.append("span.underline {text-decoration: underline;}\n")
				.append("span.overline {text-decoration: overline;}\n")
				.append("span.strike {text-decoration: line-through;}\n")
				.append("span.code {font-family: monospace; white-space: pre;}\n")
				.append("span.aa {font-family: Mona, \"MS PGothic\", monospace;}\n")
				.append("span.heading {font-weight: bold; font-size: 1.2rem;}\n</style>\n");
		builder.append("<script type=\"text/javascript\">\nfunction switchStyle(style)\n{\n\t")
				.append("var links = document.getElementsByTagName('link');\n\tfor (var i = 0; i < links.length; i++)")
				.append("\n\t{\n\t\tvar rel = links[i].getAttribute(\"rel\");")
				.append("\n\t\tvar title = links[i].getAttribute(\"title\");")
				.append("\n\t\tif (rel.indexOf(\"style\") != -1 && title) links[i].disabled = title != style;")
				.append("\n\t}\n}\nswitchStyle('Photon');\n</script>\n");
		builder.append("</head>\n<body>\n<div class=\"logo\">").append(boardTitle).append(" @ ").append(chanTitle)
				.append("</div>\n<div class=\"logo\" style=\"font-size: 1rem; margin-top: 0.25em;\">\n");
		for (int i = 0; i < STYLES.size(); i++) {
			Pair<String, String> style = STYLES.get(i);
			builder.append("[ <a href=\"javascript:switchStyle('").append(style.first).append("');\">")
					.append(style.first).append("</a> ]\n");
		}
		builder.append("</div>\n<hr />\n<div id=\"delform\" data-thread-uri=\"").append(threadUri.toString())
				.append("\" data-posts=\"").append(postsCount).append("\" data-files=\"")
				.append(filesCount).append("\">\n");
	}

	private boolean originalPost = true;
	private String number;
	private String subject;
	private String name;
	private String identifier;
	private String tripcode;
	private String capcode;
	private String email;
	private boolean sage;
	private boolean originalPoster;
	private long timestamp;
	private boolean deleted;
	private boolean useDefaultName;
	private String comment;
	private final ArrayList<IconItem> iconItems = new ArrayList<>();
	private final ArrayList<FileItem> fileItems = new ArrayList<>();

	private static class IconItem {
		public final String imageFile;
		public final String title;

		public IconItem(String imageFile, String title) {
			this.imageFile = imageFile;
			this.title = title;
		}
	}

	private static class FileItem {
		public final String imageFile;
		public final String thumbnailFile;
		public final String displayName;
		public final String originalName;
		public final int size;
		public final int width;
		public final int height;

		public FileItem(String imageFile, String thumbnailFile, String displayName, String originalName,
				int size, int width, int height) {
			this.imageFile = imageFile;
			this.thumbnailFile = thumbnailFile;
			this.displayName = displayName;
			this.originalName = originalName;
			this.size = size;
			this.width = width;
			this.height = height;
		}
	}

	public void addPost(String number, String subject, String name, String identifier, String tripcode, String capcode,
			String email, boolean sage, boolean originalPoster, long timestamp, boolean deleted,
			boolean useDefaultName, String comment) {
		closePost();
		this.number = number;
		this.subject = subject;
		this.name = name;
		this.identifier = identifier;
		this.tripcode = tripcode;
		this.capcode = capcode;
		this.email = email;
		this.sage = sage;
		this.originalPoster = originalPoster;
		this.timestamp = timestamp;
		this.deleted = deleted;
		this.useDefaultName = useDefaultName;
		this.comment = comment;
	}

	public void addIcon(String imageFile, String title) {
		iconItems.add(new IconItem(imageFile, title));
	}

	public void addFile(String imageFile, String thumbnailFile, String originalName, int size, int width, int height) {
		int index = imageFile.lastIndexOf('/');
		String displayName = index >= 0 ? imageFile.substring(index + 1) : imageFile;
		index = displayName.lastIndexOf('.');
		String extension = null;
		if (index >= 0) {
			extension = displayName.substring(index);
			displayName = displayName.substring(0, index);
		}
		int maxLength = 25;
		if (displayName.length() > maxLength) {
			displayName = displayName.substring(0, maxLength - 3) + "…" + displayName
					.substring(displayName.length() - 3);
		}
		if (extension != null) {
			displayName += extension;
		}
		fileItems.add(new FileItem(imageFile, thumbnailFile, displayName, originalName, size, width, height));
	}

	private void closePost() {
		String number = this.number;
		if (number != null) {
			StringBuilder builder = this.builder;
			builder.append("<span data-number=\"").append(number).append("\"></span>\n");
			if (originalPost) {
				originalPost = false;
				appendFiles();
				appendHeader(true);
				appendComment();
			} else {
				builder.append("<table>\n<tbody>\n<tr>\n<td class=\"doubledash\">&gt;&gt;</td>\n")
						.append("<td class=\"reply\" id=\"reply").append(number).append("\">\n");
				appendHeader(false);
				appendFiles();
				appendComment();
				builder.append("</td>\n</tr>\n</tbody>\n</table>\n");
			}
		}
		this.number = null;
		iconItems.clear();
		fileItems.clear();
	}

	private static String escapeHtml(String string) {
		return string != null ? string.replace("&", "&amp;").replace("\"", "&quot;")
				.replace("<", "&lt;").replace(">", "&gt;") : "";
	}

	private void appendHeader(boolean originalPost) {
		String number = this.number;
		String subject = this.subject;
		String name = this.name;
		String identifier = this.identifier;
		String tripcode = this.tripcode;
		String capcode = this.capcode;
		String email = this.email;
		long timestamp = this.timestamp;
		StringBuilder builder = this.builder;
		builder.append("<div");
		if (!originalPost) {
			builder.append(" class=\"replyheader\"");
		}
		builder.append(">\n<a name=\"").append(number).append("\"></a>\n<input type=\"checkbox\" value=\"")
				.append(number).append("\" disabled />\n");
		if (!StringUtils.isEmpty(subject)) {
			builder.append("<span class=\"replytitle\" data-subject=\"true\">").append(subject).append("</span>\n");
		}
		boolean hasName = !StringUtils.isEmpty(name);
		boolean hasIdentifier = !StringUtils.isEmpty(identifier);
		boolean hasEmail = !StringUtils.isEmpty(email);
		builder.append("<span class=\"postername\" ");
		if (hasName) {
			builder.append("data-name=\"").append(escapeHtml(name)).append("\"");
		}
		if (hasIdentifier) {
			builder.append(" data-identifier=\"").append(escapeHtml(identifier)).append("\"");
		}
		if (hasEmail) {
			builder.append(" data-email=\"").append(escapeHtml(email)).append("\"");
		}
		if (useDefaultName) {
			builder.append(" data-default-name=\"true\"");
		}
		builder.append('>');
		if (hasEmail) {
			if (!email.startsWith("mailto:")) {
				email = "mailto:" + email;
			}
			builder.append("<a href=\"").append(email).append("\">");
		}
		if (hasName) {
			builder.append(name);
		}
		if (hasEmail) {
			builder.append("</a>");
		}
		if (hasIdentifier) {
			builder.append(" ID: ").append(identifier);
		}
		builder.append("</span>\n");
		boolean hasTripcode = !StringUtils.isEmpty(tripcode);
		boolean hasCapcode = !StringUtils.isEmpty(capcode);
		boolean originalPoster = this.originalPoster;
		if (hasTripcode || hasCapcode || originalPoster) {
			builder.append("<span class=\"postertrip\"");
			if (hasTripcode) {
				builder.append(" data-tripcode=\"").append(escapeHtml(tripcode)).append("\"");
			}
			if (hasCapcode) {
				builder.append(" data-capcode=\"").append(escapeHtml(capcode)).append("\"");
			}
			if (originalPoster) {
				builder.append(" data-op=\"true\"");
			}
			builder.append('>');
			if (hasTripcode) {
				builder.append(tripcode);
			}
			if (hasCapcode) {
				if (hasTripcode) {
					builder.append(' ');
				}
				builder.append("## ").append(capcode);
			}
			if (originalPoster) {
				if (hasTripcode || hasCapcode) {
					builder.append(' ');
				}
				builder.append("# OP");
			}
			builder.append("</span>\n");
		}
		if (sage) {
			builder.append("<a href=\"mailto:sage\" data-sage=\"true\"></a>\n");
		}
		for (IconItem iconItem : iconItems) {
			builder.append("<img data-icon=\"true\" class=\"postericon\" src=\"")
					.append(iconItem.imageFile).append("\"");
			if (iconItem.title != null) {
				builder.append(" title=\"").append(escapeHtml(iconItem.title)).append("\"");
			}
			builder.append(" />\n");
		}
		builder.append("<span data-timestamp=\"").append(timestamp).append("\">").append(DATE_FORMAT.format(timestamp))
				.append("</span>\n");
		builder.append("<span class=\"reflink\">No.").append(number);
		if (deleted) {
			builder.append(" <span style=\"color: #f00\">DELETED</span>");
		}
		builder.append("</span>\n</div>\n");
	}

	private void appendComment() {
		StringBuilder builder = this.builder;
		builder.append("<blockquote data-comment=\"true\"");
		if (fileItems.size() > 0) {
			builder.append(" class=\"withimage\"");
		}
		builder.append(">\n").append(comment).append("\n</blockquote>\n");
	}

	private void appendFiles() {
		StringBuilder builder = this.builder;
		ArrayList<FileItem> fileItems = this.fileItems;
		boolean multiple = fileItems.size() > 1;
		for (FileItem fileItem : fileItems) {
			appendFile(fileItem, multiple);
		}
		if (multiple) {
			builder.append("<br style=\"clear: left;\" />\n");
		}
	}

	private void appendFile(FileItem fileItem, boolean multiple) {
		StringBuilder builder = this.builder;
		if (multiple) {
			builder.append("<div style=\"float: left;\">\n");
		}
		builder.append("<span class=\"filesize\" data-file=\"").append(fileItem.imageFile)
				.append("\" data-thumbnail=\"").append(fileItem.thumbnailFile != null ? fileItem.thumbnailFile : "");
		if (!StringUtils.isEmpty(fileItem.originalName)) {
			builder.append("\" data-original-name=\"").append(escapeHtml(fileItem.originalName));
		}
		builder.append("\" data-size=\"").append(fileItem.size).append("\" data-width=\"").append(fileItem.width)
				.append("\" data-height=\"").append(fileItem.height).append("\">\n");
		builder.append("File: <a target=\"_blank\" href=\"")
				.append(fileItem.imageFile).append("\">").append(fileItem.displayName).append("</a>\n");
		String size = null;
		if (fileItem.size > 0) {
			float sizeFloat;
			String dim;
			if (fileItem.size >= 2 * 1000 * 1000) {
				sizeFloat = fileItem.size / 1000f / 1000f;
				dim = "MB";
			} else if (fileItem.size >= 2 * 1000) {
				sizeFloat = fileItem.size / 1000f;
				dim = "kB";
			} else {
				sizeFloat = fileItem.size;
				dim = "B";
			}
			size = String.format(Locale.US, "%.2f", sizeFloat) + ' ' + dim;
		}
		boolean hasFileInfo = size != null || fileItem.width > 0 && fileItem.height > 0
				|| !StringUtils.isEmpty(fileItem.originalName);
		if (hasFileInfo) {
			if (multiple) {
				builder.append("<br />\n");
			}
			boolean hasTitleFileInfo = multiple && !StringUtils.isEmpty(fileItem.originalName);
			builder.append("(<em");
			if (hasTitleFileInfo) {
				builder.append(" title=\"");
				appendFileInfo(size, fileItem, false);
				builder.append("\"");
			}
			builder.append('>');
			appendFileInfo(size, fileItem, multiple);
			builder.append("</em>)\n");
		}
		builder.append("</span>\n<br />\n");
		if (fileItem.thumbnailFile != null) {
			builder.append("<a target=\"_blank\" href=\"").append(fileItem.imageFile).append("\">\n<img src=\"")
					.append(fileItem.thumbnailFile).append("\" class=\"thumb\"");
			if (!multiple) {
				builder.append(" style=\"float: left;\"");
			}
			builder.append(" />\n</a>\n");
		} else {
			builder.append("<div class=\"nothumb\">\n<a target=\"_blank\" href=\"").append(fileItem.imageFile)
					.append("\">No<br />thumbnail</a>\n</div>\n");
		}
		if (multiple) {
			builder.append("</div>\n");
		}
	}

	@SuppressWarnings("UnusedAssignment")
	private void appendFileInfo(String size, FileItem fileItem, boolean shortInfo) {
		StringBuilder builder = this.builder;
		boolean divider = false;
		if (size != null) {
			divider = true;
			builder.append(size);
		}
		if (fileItem.width > 0 && fileItem.height > 0) {
			if (divider) {
				builder.append(", ");
			} else {
				divider = true;
			}
			builder.append(fileItem.width).append('×').append(fileItem.height);
		}
		if (!StringUtils.isEmpty(fileItem.originalName)) {
			if (divider) {
				builder.append(", ");
			} else {
				divider = true;
			}
			if (shortInfo) {
				builder.append("…");
			} else {
				builder.append(escapeHtml(fileItem.originalName));
			}
		}
	}

	public String build() {
		closePost();
		return builder.append("<br style=\"clear: left;\" />\n<hr />\n</div>\n")
				.append("<p class=\"footer\">\n- <a href=\"").append(CLIENT_URI).append("\">dashchan</a> + ")
				.append("<a href=\"http://wakaba.c3.cx/\">wakaba</a> + ")
				.append("<a href=\"http://www.2chan.net/\">futaba</a> -\n</p>\n</body>\n</html>").toString();
	}
}
