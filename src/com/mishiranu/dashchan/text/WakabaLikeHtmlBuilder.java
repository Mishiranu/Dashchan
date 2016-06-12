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

package com.mishiranu.dashchan.text;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

import android.net.Uri;
import android.util.Pair;

import chan.util.StringUtils;

public class WakabaLikeHtmlBuilder
{
	private static final ArrayList<Pair<String, String>> STYLES = new ArrayList<>();
	
	static
	{
		STYLES.add(new Pair<>("Photon", "http://mishiranu.github.io/Dashchan/wakaba/photon.css"));
		STYLES.add(new Pair<>("Futaba", "http://mishiranu.github.io/Dashchan/wakaba/futaba.css"));
		STYLES.add(new Pair<>("Burichan", "http://mishiranu.github.io/Dashchan/wakaba/burichan.css"));
		STYLES.add(new Pair<>("Gurochan", "http://mishiranu.github.io/Dashchan/wakaba/gurochan.css"));
	}
	
	private static final String CLIENT_URI = "https://github.com/Mishiranu/Dashchan/";
	
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yy(ccc)HH:mm:ss", Locale.US);
	
	static
	{
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("Etc/GMT"));
	}
	
	private final StringBuilder mBuilder = new StringBuilder();
	private final String mChanName;
	
	public WakabaLikeHtmlBuilder(String threadTitle, String chanName, String boardName, String boardTitle,
			String chanTitle, Uri threadUri, int postsCount, int filesCount)
	{
		mChanName = chanName;
		StringBuilder builder = mBuilder;
		builder.append("<!DOCTYPE html><html><head>")
				.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />");
		builder.append("<title>");
		if (!StringUtils.isEmpty(threadTitle)) builder.append(threadTitle).append(" — ");
		builder.append('/').append(boardName).append('/').append(" — ");
		if (!StringUtils.isEmpty(boardTitle)) builder.append(boardTitle).append(" — ");
		builder.append(chanTitle);
		builder.append("</title>");
		for (int i = 0; i < STYLES.size(); i++)
		{
			Pair<String, String> style = STYLES.get(i);
			builder.append("<link rel=\"");
			if (i > 0) builder.append("alternate ");
			builder.append("stylesheet\" type=\"text/css\" href=\"").append(style.second)
					.append("\" title=\"").append(style.first).append("\" />");
		}
		builder.append("<style type=\"text/css\">body {margin: 0; padding: 8px; margin-bottom: auto;} ")
				.append(".thumb {border: none; margin: 2px 20px; max-width: 200px; max-height: 200px;} ")
				.append(".nothumb {float: left; background: #eee; border: 2px dashed #aaa; ")
				.append("text-align: center; margin: 2px 20px; padding: 1em 0.5em 1em 0.5em;} ")
				.append(".filesize {padding-left: 20px; display: inline-block;} ")
				.append(".replyheader {padding: 0 0.25em 0 0;} ")
				.append(".reflink a {color: inherit; text-decoration: none;} ")
				.append(".withimage {min-width: 30em;} ")
				.append(".postericon {padding-right: 6px; max-height: 1em;} ")
				.append("span.overline {text-decoration: overline;} ")
				.append("span.code {font-family: monospace; white-space: pre;} ")
				.append("span.aa {font-family: Mona, \"MS PGothic\", monospace;} ")
				.append("span.heading {font-weight: bold; font-size: 1.2rem;}</style>");
		builder.append("<script type=\"text/javascript\">function switchStyle(style) {")
				.append("var links = document.getElementsByTagName('link'); for (var i = 0; i < links.length; i++) {")
				.append("var rel = links[i].getAttribute(\"rel\"); var title = links[i].getAttribute(\"title\"); ")
				.append("if (rel.indexOf(\"style\") != -1 && title) links[i].disabled = title != style;}} ")
				.append("switchStyle('Photon');</script>");
		builder.append("</head><body><div class=\"logo\">").append(boardTitle).append(" @ ").append(chanTitle)
				.append("</div><div class=\"logo\" style=\"font-size: 1rem; margin-top: 0.25em;\">");
		for (int i = 0; i < STYLES.size(); i++)
		{
			Pair<String, String> style = STYLES.get(i);
			if (i > 0) builder.append(' ');
			builder.append("[ <a href=\"javascript:switchStyle('").append(style.first).append("');\">")
					.append(style.first).append("</a> ]");
		}
		builder.append("</div><hr /><div id=\"delform\" data-thread-uri=\"").append(threadUri.toString())
				.append("\" data-posts=\"").append(postsCount).append("\" data-files=\"")
				.append(filesCount).append("\">");
	}
	
	private boolean mOriginalPost = true;
	private String mNumber;
	private String mSubject;
	private String mName;
	private String mIdentifier;
	private String mTripcode;
	private String mCapcode;
	private String mEmail;
	private boolean mSage;
	private boolean mOriginalPoster;
	private long mTimestamp;
	private boolean mDeleted;
	private boolean mUseDefaultName;
	private String mComment;
	private final ArrayList<Pair<Uri, String>> mIconItems = new ArrayList<>();
	private final ArrayList<FileItem> mFileItems = new ArrayList<>();
	
	private static class FileItem
	{
		public final String imageFile;
		public final String thumbnailFile;
		public final String displayName;
		public final String originalName;
		public final int size;
		public final int width;
		public final int height;
		
		public FileItem(String imageFile, String thumbnailFile, String displayName, String originalName,
				int size, int width, int height)
		{
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
			boolean useDefaultName, String comment)
	{
		closePost();
		mNumber = number;
		mSubject = subject;
		mName = name;
		mIdentifier = identifier;
		mTripcode = tripcode;
		mCapcode = capcode;
		mEmail = email;
		mSage = sage;
		mOriginalPoster = originalPoster;
		mTimestamp = timestamp;
		mDeleted = deleted;
		mUseDefaultName = useDefaultName;
		mComment = comment;
	}
	
	public void addIcon(Uri uri, String title)
	{
		if (uri != null)
		{
			if ("chan".equals(uri.getScheme()) && StringUtils.isEmpty(uri.getAuthority()))
			{
				uri = uri.buildUpon().authority(mChanName).build();
			}
			mIconItems.add(new Pair<>(uri, title));
		}
	}
	
	public void addFile(String imageFile, String thumbnailFile, String originalName, int size, int width, int height)
	{
		int index = imageFile.lastIndexOf('/');
		String displayName = index >= 0 ? imageFile.substring(index + 1) : imageFile;
		index = displayName.lastIndexOf('.');
		String extension = null;
		if (index >= 0)
		{
			extension = displayName.substring(index);
			displayName = displayName.substring(0, index);
		}
		int maxLength = 25;
		if (displayName.length() > maxLength)
		{
			displayName = displayName.substring(0, maxLength - 3) + "…" + displayName
					.substring(displayName.length() - 3, displayName.length());
		}
		if (extension != null) displayName += extension;
		mFileItems.add(new FileItem(imageFile, thumbnailFile, displayName, originalName, size, width, height));
	}
	
	private void closePost()
	{
		String number = mNumber;
		if (number != null)
		{
			boolean withImage = mFileItems.size() > 0;
			StringBuilder builder = mBuilder;
			builder.append("<span data-number=\"").append(number).append("\"></span>");
			if (mOriginalPost)
			{
				mOriginalPost = false;
				appendFiles();
				appendHeader(true);
				appendComment(withImage);
			}
			else
			{
				builder.append("<table><tbody><tr><td class=\"doubledash\">&gt;&gt;</td>")
						.append("<td class=\"reply\" id=\"reply").append(number).append("\">");
				appendHeader(false);
				appendFiles();
				appendComment(withImage);
				builder.append("</td></tr></tbody></table>");
			}
		}
		mNumber = null;
		mIconItems.clear();
		mFileItems.clear();
	}
	
	private static String escapeHtml(String string)
	{
		return string != null ? string.replace("&", "&amp;").replace("\"", "&quot;")
				.replace("<", "&lt;").replace(">", "&gt;") : "";
	}
	
	private void appendHeader(boolean originalPost)
	{
		String number = mNumber;
		String subject = mSubject;
		String name = mName;
		String identifier = mIdentifier;
		String tripcode = mTripcode;
		String capcode = mCapcode;
		String email = mEmail;
		long timestamp = mTimestamp;
		StringBuilder builder = mBuilder;
		builder.append("<div");
		if (!originalPost) builder.append(" class=\"replyheader\"");
		builder.append("><a name=\"").append(number).append("\"></a><input type=\"checkbox\" value=\"")
				.append(number).append("\" disabled />");
		for (Pair<Uri, String> icon : mIconItems)
		{
			builder.append("<img data-icon=\"true\" class=\"postericon\" src=\"").append(icon.first).append("\"");
			if (icon.second != null) builder.append(" title=\"").append(escapeHtml(icon.second)).append("\"");
			builder.append(" />");
		}
		if (!StringUtils.isEmpty(subject))
		{
			builder.append("<span class=\"replytitle\" data-subject=\"true\">").append(subject).append("</span> ");
		}
		if (name == null) name = "";
		boolean hasIdentifier = !StringUtils.isEmpty(identifier);
		boolean hasEmail = !StringUtils.isEmpty(email);
		builder.append("<span class=\"postername\" data-name=\"").append(escapeHtml(name)).append("\"");
		if (hasIdentifier) builder.append(" data-identifier=\"").append(escapeHtml(identifier)).append("\"");
		if (hasEmail) builder.append(" data-email=\"").append(escapeHtml(email)).append("\"");
		if (mUseDefaultName) builder.append(" data-default-name=\"true\"");
		builder.append('>');
		if (hasEmail)
		{
			if (!email.startsWith("mailto:")) email = "mailto:" + email;
			builder.append("<a href=\"").append(email).append("\">");
		}
		builder.append(name);
		if (hasEmail) builder.append("</a>");
		if (hasIdentifier) builder.append(" ID: ").append(identifier);
		builder.append("</span> ");
		boolean hasTripcode = !StringUtils.isEmpty(tripcode);
		boolean hasCapcode = !StringUtils.isEmpty(capcode);
		boolean originalPoster = mOriginalPoster;
		if (hasTripcode || hasCapcode || originalPoster)
		{
			builder.append("<span class=\"postertrip\"");
			if (hasTripcode) builder.append(" data-tripcode=\"").append(escapeHtml(tripcode)).append("\"");
			if (hasCapcode) builder.append(" data-capcode=\"").append(escapeHtml(capcode)).append("\"");
			if (originalPoster) builder.append(" data-op=\"true\"");
			builder.append('>');
			if (hasTripcode) builder.append(tripcode);
			if (hasCapcode)
			{
				if (hasTripcode) builder.append(' ');
				builder.append("## ").append(capcode);
			}
			if (originalPoster)
			{
				if (hasTripcode || hasCapcode) builder.append(' ');
				builder.append("# OP");
			}
			builder.append("</span> ");
		}
		if (mSage) builder.append("<a href=\"mailto:sage\" data-sage=\"true\"></a>");
		builder.append("<span data-timestamp=\"").append(timestamp).append("\">").append(DATE_FORMAT.format(timestamp))
				.append("</span> ");
		builder.append("<span class=\"reflink\">No.").append(number);
		if (mDeleted) builder.append(" <span style=\"color: #f00\">DELETED</span>");
		builder.append("</span></div>");
	}
	
	private void appendComment(boolean withImage)
	{
		StringBuilder builder = mBuilder;
		builder.append("<blockquote data-comment=\"true\"");
		if (mFileItems.size() > 0) builder.append(" class=\"withimage\"");
		builder.append('>').append(mComment).append("</blockquote>");
	}
	
	private void appendFiles()
	{
		StringBuilder builder = mBuilder;
		ArrayList<FileItem> fileItems = mFileItems;
		boolean multiple = fileItems.size() > 1;
		for (FileItem fileItem : fileItems) appendFile(fileItem, multiple);
		if (multiple) builder.append("<br style=\"clear: left;\" />");
	}
	
	private void appendFile(FileItem fileItem, boolean multiple)
	{
		StringBuilder builder = mBuilder;
		if (multiple) builder.append("<div style=\"float: left;\">");
		builder.append("<span class=\"filesize\" data-file=\"").append(fileItem.imageFile)
				.append("\" data-thumbnail=\"").append(fileItem.thumbnailFile != null ? fileItem.thumbnailFile : "");
		if (fileItem.originalName != null)
		{
			builder.append("\" data-original-name=\"").append(escapeHtml(fileItem.originalName));
		}
		builder.append("\" data-size=\"").append(fileItem.size).append("\" data-width=\"").append(fileItem.width)
				.append("\" data-height=\"").append(fileItem.height).append("\">");
		builder.append("File: <a target=\"_blank\" href=\"")
				.append(fileItem.imageFile).append("\">").append(fileItem.displayName).append("</a>");
		String size = null;
		if (fileItem.size > 0)
		{
			float sizeFloat;
			String dim;
			if (fileItem.size >= 2 * 1024 * 1024)
			{
				sizeFloat = fileItem.size / 1024f / 1024f;
				dim = "MB";
			}
			else if (fileItem.size >= 2 * 1024)
			{
				sizeFloat = fileItem.size / 1024f;
				dim = "KB";
			}
			else
			{
				sizeFloat = fileItem.size;
				dim = "B";
			}
			size = String.format(Locale.US, "%.2f", sizeFloat) + ' ' + dim;
		}
		boolean hasFileInfo = size != null || fileItem.width > 0 && fileItem.height > 0
				|| fileItem.originalName != null;
		if (hasFileInfo)
		{
			if (multiple) builder.append("<br />"); else builder.append(' ');
			boolean hasTitleFileInfo = multiple && fileItem.originalName != null;
			builder.append("(<em");
			if (hasTitleFileInfo)
			{
				builder.append(" title=\"");
				appendFileInfo(size, fileItem, false);
				builder.append("\"");
			}
			builder.append('>');
			appendFileInfo(size, fileItem, multiple);
			builder.append("</em>)");
		}
		builder.append("</span><br />");
		if (fileItem.thumbnailFile != null)
		{
			builder.append("<a target=\"_blank\" href=\"").append(fileItem.imageFile).append("\"><img src=\"")
					.append(fileItem.thumbnailFile).append("\" class=\"thumb\"");
			if (!multiple) builder.append(" style=\"float: left;\"");
			builder.append(" /></a>");
		}
		else
		{
			builder.append("<div class=\"nothumb\"><a target=\"_blank\" href=\"").append(fileItem.imageFile)
					.append("\">No<br />thumbnail</a></div>");
		}
		if (multiple) builder.append("</div>");
	}
	
	private void appendFileInfo(String size, FileItem fileItem, boolean shortInfo)
	{
		StringBuilder builder = mBuilder;
		boolean divider = false;
		if (size != null)
		{
			divider = true;
			builder.append(size);
		}
		if (fileItem.width > 0 && fileItem.height > 0)
		{
			if (divider) builder.append(", "); else divider = true;
			builder.append(fileItem.width).append('x').append(fileItem.height);
		}
		if (fileItem.originalName != null)
		{
			if (divider) builder.append(", "); else divider = true;
			if (shortInfo) builder.append("…"); else builder.append(escapeHtml(fileItem.originalName));
		}
	}
	
	public String build()
	{
		closePost();
		return mBuilder.append("<br style=\"clear: left;\" /><hr /> </div>").append("<p class=\"footer\"> - <a href=\"")
				.append(CLIENT_URI).append("\">dashchan</a> + <a href=\"http://wakaba.c3.cx/\">wakaba</a> + ")
				.append("<a href=\"http://www.2chan.net/\">futaba</a> -</p></body></html>").toString();
	}
}