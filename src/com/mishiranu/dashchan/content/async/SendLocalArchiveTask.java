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

package com.mishiranu.dashchan.content.async;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;

import android.graphics.Typeface;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanMarkup;
import chan.content.model.Attachment;
import chan.content.model.FileAttachment;
import chan.content.model.Icon;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.util.StringUtils;

import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.text.WakabaLikeHtmlBuilder;
import com.mishiranu.dashchan.text.style.GainedColorSpan;
import com.mishiranu.dashchan.text.style.HeadingSpan;
import com.mishiranu.dashchan.text.style.LinkSpan;
import com.mishiranu.dashchan.text.style.MonospaceSpan;
import com.mishiranu.dashchan.text.style.OverlineSpan;
import com.mishiranu.dashchan.text.style.QuoteSpan;
import com.mishiranu.dashchan.text.style.ScriptSpan;
import com.mishiranu.dashchan.text.style.SpoilerSpan;
import com.mishiranu.dashchan.util.IOUtils;

public class SendLocalArchiveTask extends CancellableTask<Void, Integer, Object> implements ChanMarkup.MarkupExtra {
	private final String chanName;
	private final String boardName;
	private final String threadNumber;
	private final Posts posts;
	private final boolean saveThumbnails;
	private final boolean saveFiles;
	private final Callback callback;

	public interface Callback {
		public void onLocalArchivationProgressUpdate(int handledPostsCount);
		public void onLocalArchivationComplete(boolean success, boolean showSuccess);
	}

	public SendLocalArchiveTask(String chanName, String boardName, String threadNumber,
			Posts posts, boolean saveThumbnails, boolean saveFiles, Callback callback) {
		this.chanName = chanName;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.posts = posts;
		this.saveThumbnails = saveThumbnails;
		this.saveFiles = saveFiles;
		this.callback = callback;
	}

	@Override
	public String getBoardName() {
		return boardName;
	}

	@Override
	public String getThreadNumber() {
		return threadNumber;
	}

	private static class SpanItem {
		public int start;
		public int end;

		public final String openTag;
		public final String closeTag;

		public SpanItem(String openTag, String closeTag) {
			this.openTag = openTag;
			this.closeTag = closeTag;
		}
	}

	@Override
	protected Result doInBackground(Void... params) {
		String chanName = this.chanName;
		String boardName = this.boardName;
		String threadNumber = this.threadNumber;
		Post[] posts = this.posts.getPosts();
		Object[] decodeTo = new Object[2];
		ArrayList<SpanItem> spanItems = new ArrayList<>();
		ChanConfiguration configuration = ChanConfiguration.get(chanName);
		ChanLocator locator = ChanLocator.get(configuration);
		ChanMarkup markup = ChanMarkup.get(configuration);
		File directory = getLocalDownloadDirectory(true);
		String archiveDirectoryName = chanName + '-' + boardName + '-' + threadNumber;
		File filesDirectory = saveFiles ? new File(directory, archiveDirectoryName + "/src") : null;
		File thumbnailsDirectory = saveThumbnails ? new File(directory, archiveDirectoryName + "/thumb") : null;
		if (filesDirectory != null) {
			filesDirectory.mkdirs();
		}
		if (thumbnailsDirectory != null) {
			thumbnailsDirectory.mkdirs();
		}
		int totalFilesCount = 0;
		ArrayList<String> existFilesLc = new ArrayList<>();
		ArrayList<String> existThumbnailsLc = new ArrayList<>();
		for (Post post : posts) {
			totalFilesCount += post.getAttachmentsCount();
		}
		String defaultName = configuration.getDefaultName(boardName);
		WakabaLikeHtmlBuilder htmlBuilder = new WakabaLikeHtmlBuilder(posts[0].getSubject(), chanName, boardName,
				configuration .getBoardTitle(boardName), configuration.getTitle(),
				locator.safe(false).createThreadUri(boardName, threadNumber), posts.length, totalFilesCount);
		Result result = new Result(thumbnailsDirectory, filesDirectory);
		for (Post post : posts) {
			String number = post.getPostNumber();
			String name = StringUtils.emptyIfNull(post.getName()).trim();
			String identifier = post.getIdentifier();
			String tripcode = post.getTripcode();
			String capcode = post.getCapcode();
			String email = post.getEmail();
			String subject = post.getSubject();
			String comment = post.getWorkComment();
			long timestamp = post.getTimestamp();
			boolean sage = post.isSage();
			boolean originalPoster = post.isOriginalPoster();
			boolean deleted = post.isDeleted();
			boolean useDefaultName = name.equals(defaultName) || name.isEmpty();
			if (name.isEmpty()) {
				name = defaultName;
			}
			int attachmentsCount = post.getAttachmentsCount();
			int iconsCount = post.getIconsCount();
			CharSequence charSequence = HtmlParser.spanify(comment, markup, null, this);
			spanItems.clear();
			SpannableStringBuilder spannable = new SpannableStringBuilder(charSequence);
			replaceSpannable(spannable, '<', "&lt;");
			replaceSpannable(spannable, '>', "&gt;");
			Object[] spans = spannable.getSpans(0, spannable.length(), Object.class);
			for (Object span : spans) {
				getSpanType(span, decodeTo);
				int what = (int) decodeTo[0];
				if (what != 0) {
					int start = spannable.getSpanStart(span);
					int end = spannable.getSpanEnd(span);
					Object extra = decodeTo[1];
					if (what == ChanMarkup.TAG_SPECIAL_LINK) {
						String text = spannable.subSequence(start, end).toString();
						if (text.startsWith("&gt;&gt;")) {
							Uri uri = locator.validateClickedUriString((String) decodeTo[1], boardName, threadNumber);
							if (threadNumber.equals(locator.safe(false).getThreadNumber(uri))) {
								String postNumber = locator.safe(false).getPostNumber(uri);
								if (postNumber == null) {
									postNumber = threadNumber;
								}
								extra = "#" + postNumber;
							} else {
								extra = uri.toString();
							}
						}
					}
					SpanItem spanItem = makeSpanItem(what, extra);
					if (spanItem != null) {
						spanItem.start = start;
						spanItem.end = end;
						spanItems.add(spanItem);
					}
				}
			}
			StringBuilder builder = new StringBuilder(spannable.toString());
			for (int i = 0; i < spanItems.size(); i++) {
				SpanItem spanItem = spanItems.get(i);
				int openLength = spanItem.openTag.length();
				int closeLength = spanItem.closeTag.length();
				builder.insert(spanItem.start, spanItem.openTag).insert(spanItem.end + openLength, spanItem.closeTag);
				for (int j = i + 1; j < spanItems.size(); j++) {
					SpanItem editingItem = spanItems.get(j);
					if (editingItem.start >= spanItem.start) {
						if (editingItem.start >= spanItem.end) {
							editingItem.start += openLength + closeLength;
						} else {
							editingItem.start += openLength;
						}
					}
					if (editingItem.end > spanItem.start) {
						if (editingItem.end > spanItem.end) {
							editingItem.end += openLength + closeLength;
						} else {
							editingItem.end += openLength;
						}
					}
				}
			}
			comment = builder.toString().replaceAll("\r", "").replaceAll("\n", "<br />");
			htmlBuilder.addPost(number, subject, name, identifier, tripcode, capcode, email,
					sage, originalPoster, timestamp, deleted, useDefaultName, comment);
			if (iconsCount > 0) {
				for (int i = 0; i < iconsCount; i++) {
					Icon icon = post.getIconAt(i);
					Uri uri = icon.getUri(locator);
					if (uri != null) {
						htmlBuilder.addIcon(locator.convert(uri), icon.getTitle());
					}
				}
			}
			for (int i = 0; i < attachmentsCount; i++) {
				Attachment attachment = post.getAttachmentAt(i);
				if (attachment instanceof FileAttachment) {
					FileAttachment fileAttachment = (FileAttachment) attachment;
					Uri fileUri = fileAttachment.getFileUri(locator);
					Uri thumbnailUri = fileAttachment.getThumbnailUri(locator);
					if (fileUri == null) {
						fileUri = thumbnailUri;
					}
					if (fileUri != null) {
						String fileName = locator.createAttachmentFileName(fileUri);
						fileName = chooseFileName(existFilesLc, fileName);
						String filePath = archiveDirectoryName + "/src/" + fileName;
						String thumbnailName = null;
						String thumbnailPath = null;
						if (thumbnailUri != null) {
							thumbnailName = locator.createAttachmentFileName(thumbnailUri);
							thumbnailName = chooseFileName(existThumbnailsLc, thumbnailName);
							thumbnailPath = archiveDirectoryName + "/thumb/" + thumbnailName;
						}
						String originalName = fileAttachment.getNormalizedOriginalName(fileName);
						htmlBuilder.addFile(filePath, thumbnailPath, originalName, fileAttachment.getSize(),
								fileAttachment.getWidth(), fileAttachment.getHeight());
						if (saveFiles) {
							if (!new File(filesDirectory, fileName).exists()) {
								result.filesToDownload.add(new DownloadService.DownloadItem(chanName,
										fileUri, fileName));
							}
						}
						if (saveThumbnails && thumbnailUri != null) {
							if (!new File(thumbnailsDirectory, thumbnailName).exists()) {
								result.thumbnailsToDownload.add(new DownloadService.DownloadItem(chanName,
										thumbnailUri, thumbnailName));
							}
						}
					}
				}
			}
			if (isCancelled()) {
				return null;
			}
			notifyIncrement();
		}
		String postHtml = htmlBuilder.build();
		String fileName = chanName + "-" + boardName + "-" + threadNumber + ".html";
		OutputStream outputStream = null;
		try {
			outputStream = IOUtils.openOutputStream(MainApplication.getInstance(), new File(directory, fileName));
			outputStream.write(postHtml.getBytes());
		} catch (IOException e) {
			return null;
		} finally {
			IOUtils.close(outputStream);
		}
		return result;
	}

	@Override
	protected void onProgressUpdate(Integer... values) {
		callback.onLocalArchivationProgressUpdate(values[0]);
	}

	@Override
	protected void onPostExecute(Object resultObject) {
		Result result = (Result) resultObject;
		boolean showSuccess = false;
		if (result != null) {
			boolean willDownload = false;
			willDownload |= performDownload(result.thumbnailsDirectory, result.thumbnailsToDownload);
			willDownload |= performDownload(result.filesDirectory, result.filesToDownload);
			showSuccess = !willDownload;
		}
		callback.onLocalArchivationComplete(result != null, showSuccess);
	}

	@Override
	public void cancel() {
		cancel(true);
	}

	private long lastNotifyIncrement = 0L;
	private int progress = 0;

	public void notifyIncrement() {
		progress++;
		long t = System.currentTimeMillis();
		if (t - lastNotifyIncrement >= 100) {
			lastNotifyIncrement = t;
			publishProgress(progress);
		}
	}

	private boolean performDownload(File directory, ArrayList<DownloadService.DownloadItem> downloadItems) {
		if (directory != null && downloadItems.size() > 0) {
			DownloadService.downloadDirect(MainApplication.getInstance(), directory, downloadItems);
			return true;
		}
		return false;
	}

	private static class Result {
		public final File filesDirectory;
		public final File thumbnailsDirectory;

		public final ArrayList<DownloadService.DownloadItem> filesToDownload = new ArrayList<>();
		public final ArrayList<DownloadService.DownloadItem> thumbnailsToDownload = new ArrayList<>();

		public Result(File thumbnailsDirectory, File filesDirectory) {
			this.thumbnailsDirectory = thumbnailsDirectory;
			this.filesDirectory = filesDirectory;
		}
	}

	private void replaceSpannable(SpannableStringBuilder spannable, char what, String with) {
		for (int i = 0; i < spannable.length(); i++) {
			if (spannable.charAt(i) == what) {
				spannable.replace(i, i + 1, with);
			}
		}
	}

	private SpanItem makeSpanItem(int what, Object extra) {
		String openTag;
		String closeTag;
		switch (what) {
			case ChanMarkup.TAG_BOLD: {
				openTag = "<b>";
				closeTag = "</b>";
				break;
			}
			case ChanMarkup.TAG_ITALIC: {
				openTag = "<i>";
				closeTag = "</i>";
				break;
			}
			case ChanMarkup.TAG_UNDERLINE: {
				openTag = "<span class=\"underline\">";
				closeTag = "</span>";
				break;
			}
			case ChanMarkup.TAG_OVERLINE: {
				openTag = "<span class=\"overline\">";
				closeTag = "</span>";
				break;
			}
			case ChanMarkup.TAG_STRIKE: {
				openTag = "<span class=\"strike\">";
				closeTag = "</span>";
				break;
			}
			case ChanMarkup.TAG_SUBSCRIPT: {
				openTag = "<sub>";
				closeTag = "</sub>";
				break;
			}
			case ChanMarkup.TAG_SUPERSCRIPT: {
				openTag = "<sup>";
				closeTag = "</sup>";
				break;
			}
			case ChanMarkup.TAG_SPOILER: {
				openTag = "<span class=\"spoiler\">";
				closeTag = "</span>";
				break;
			}
			case ChanMarkup.TAG_QUOTE: {
				openTag = "<span class=\"unkfunc\">";
				closeTag = "</span>";
				break;
			}
			case ChanMarkup.TAG_CODE: {
				openTag = "<span class=\"code\">";
				closeTag = "</span>";
				break;
			}
			case ChanMarkup.TAG_ASCII_ART: {
				openTag = "<span class=\"aa\">";
				closeTag = "</span>";
				break;
			}
			case ChanMarkup.TAG_HEADING: {
				openTag = "<span class=\"heading\">";
				closeTag = "</span>";
				break;
			}
			case ChanMarkup.TAG_SPECIAL_LINK: {
				openTag = "<a href=\"" + extra + "\">";
				closeTag = "</a>";
				break;
			}
			case ChanMarkup.TAG_SPECIAL_COLOR: {
				openTag = "<span style=\"color: #" + String.format("%06x", 0x00ffffff & (int) extra) + "\">";
				closeTag = "</span>";
				break;
			}
			default: {
				return null;
			}
		}
		return new SpanItem(openTag, closeTag);
	}

	private Object[] getSpanType(Object span, Object[] result) {
		result[0] = 0;
		result[1] = null;
		if (span instanceof LinkSpan) {
			result[0] = ChanMarkup.TAG_SPECIAL_LINK;
			result[1] = ((LinkSpan) span).getUriString();
		} else if (span instanceof SpoilerSpan) {
			result[0] = ChanMarkup.TAG_SPOILER;
		} else if (span instanceof QuoteSpan) {
			result[0] = ChanMarkup.TAG_QUOTE;
		} else if (span instanceof ScriptSpan) {
			result[0] = ((ScriptSpan) span).isSuperscript() ? ChanMarkup.TAG_SUPERSCRIPT : ChanMarkup.TAG_SUBSCRIPT;
		} else if (span instanceof StyleSpan) {
			int style = ((StyleSpan) span).getStyle();
			if (style == Typeface.BOLD) {
				result[0] = ChanMarkup.TAG_BOLD;
			} else {
				if (style == Typeface.ITALIC) {
					result[0] = ChanMarkup.TAG_ITALIC;
				}
			}
		} else if (span instanceof UnderlineSpan) {
			result[0] = ChanMarkup.TAG_UNDERLINE;
		} else if (span instanceof OverlineSpan) {
			result[0] = ChanMarkup.TAG_OVERLINE;
		} else if (span instanceof StrikethroughSpan) {
			result[0] = ChanMarkup.TAG_STRIKE;
		} else if (span instanceof GainedColorSpan) {
			result[0] = ChanMarkup.TAG_SPECIAL_COLOR;
			result[1] = ((GainedColorSpan) span).getForegroundColor();
		} else if (span instanceof MonospaceSpan) {
			result[0] = ((MonospaceSpan) span).isAsciiArt() ? ChanMarkup.TAG_ASCII_ART : ChanMarkup.TAG_CODE;
		} else if (span instanceof HeadingSpan) {
			result[0] = ChanMarkup.TAG_HEADING;
		}
		return result;
	}

	public static File getLocalDownloadDirectory(boolean prepare) {
		File directory = new File(Preferences.getDownloadDirectory(), "Archive");
		if (prepare) {
			directory.mkdirs();
			File nomedia = new File(directory, ".nomedia");
			if (!nomedia.exists()) {
				try {
					nomedia.createNewFile();
				} catch (IOException e) {
					// Ignore exception
				}
			}
		}
		return directory;
	}

	private String chooseFileName(ArrayList<String> fileNamesLc, String fileName) {
		if (fileName != null) {
			Locale locale = Locale.getDefault();
			String fileNameLc = fileName.toLowerCase(locale);
			if (fileNamesLc.contains(fileNameLc)) {
				String extension = StringUtils.getFileExtension(fileName);
				if (extension != null) {
					fileName = fileName.substring(0, fileName.length() - extension.length() - 1);
				}
				String newFileName;
				String newFileNameLc;
				int i = 0;
				do {
					newFileName = fileName + "-" + ++i + (extension != null ? "." + extension : "");
					newFileNameLc = newFileName.toLowerCase(locale);
				} while (fileNamesLc.contains(newFileNameLc));
				fileNamesLc.add(newFileNameLc);
				fileName = newFileName;
			} else {
				fileNamesLc.add(fileNameLc);
			}
		}
		return fileName;
	}
}