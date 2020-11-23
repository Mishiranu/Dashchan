package com.mishiranu.dashchan.content.async;

import android.net.Uri;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.style.StrikethroughSpan;
import android.text.style.UnderlineSpan;
import android.util.Base64;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanMarkup;
import chan.util.DataFile;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.model.Post;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.text.WakabaLikeHtmlBuilder;
import com.mishiranu.dashchan.text.style.GainedColorSpan;
import com.mishiranu.dashchan.text.style.HeadingSpan;
import com.mishiranu.dashchan.text.style.ItalicSpan;
import com.mishiranu.dashchan.text.style.LinkSpan;
import com.mishiranu.dashchan.text.style.MediumSpan;
import com.mishiranu.dashchan.text.style.MonospaceSpan;
import com.mishiranu.dashchan.text.style.OverlineSpan;
import com.mishiranu.dashchan.text.style.QuoteSpan;
import com.mishiranu.dashchan.text.style.ScriptSpan;
import com.mishiranu.dashchan.text.style.SpoilerSpan;
import com.mishiranu.dashchan.util.Hasher;
import com.mishiranu.dashchan.util.MimeTypes;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class SendLocalArchiveTask extends ExecutorTask<Integer, SendLocalArchiveTask.Result>
		implements ChanMarkup.MarkupExtra {
	private static final String DIRECTORY_ARCHIVE = "Archive";
	private static final String DIRECTORY_FILES = "src";
	private static final String DIRECTORY_THUMBNAILS = "thumb";

	private final Callback callback;
	private final Chan chan;
	private final String boardName;
	private final String threadNumber;
	private final Collection<Post> posts;
	private final boolean saveThumbnails;
	private final boolean saveFiles;

	public interface DownloadResult {
		void run(DownloadService.Binder binder);
	}

	public interface Callback {
		void onLocalArchivationProgressUpdate(int handledPostsCount);
		void onLocalArchivationComplete(DownloadResult result);
	}

	public SendLocalArchiveTask(Callback callback, Chan chan, String boardName, String threadNumber,
			Collection<Post> posts, boolean saveThumbnails, boolean saveFiles) {
		this.callback = callback;
		this.chan = chan;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.posts = posts;
		this.saveThumbnails = saveThumbnails;
		this.saveFiles = saveFiles;
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
	protected Result run() {
		Chan chan = this.chan;
		String boardName = this.boardName;
		String threadNumber = this.threadNumber;
		Collection<Post> posts = this.posts;
		Object[] decodeTo = new Object[2];
		ArrayList<SpanItem> spanItems = new ArrayList<>();
		String archiveName = chan.name + '-' + boardName + '-' + threadNumber;
		ArrayList<String> existFilesLc = new ArrayList<>();
		ArrayList<String> existThumbnailsLc = new ArrayList<>();
		HashMap<String, String> iconNames = new HashMap<>();
		Hasher hasher = Hasher.getInstanceSha256();
		int totalFilesCount = 0;
		for (Post post : posts) {
			totalFilesCount += post.attachments.size();
		}
		String defaultName = chan.configuration.getDefaultName(boardName);
		WakabaLikeHtmlBuilder htmlBuilder = new WakabaLikeHtmlBuilder(posts.iterator().next().subject,
				boardName, chan.configuration.getBoardTitle(boardName), chan.configuration.getTitle(),
				chan.locator.safe(false).createThreadUri(boardName, threadNumber), posts.size(), totalFilesCount);
		ArrayList<DownloadService.DownloadItem> filesToDownload = new ArrayList<>();
		ArrayList<DownloadService.DownloadItem> thumbnailsToDownload = new ArrayList<>();
		for (Post post : posts) {
			PostNumber number = post.number;
			String name = StringUtils.emptyIfNull(post.name).trim();
			String identifier = post.identifier;
			String tripcode = post.tripcode;
			String capcode = post.capcode;
			String email = post.email;
			String subject = post.subject;
			String comment = post.comment;
			long timestamp = post.timestamp;
			boolean sage = post.isSage();
			boolean originalPoster = post.isOriginalPoster();
			boolean deleted = post.deleted;
			boolean useDefaultName = name.equals(defaultName) || name.isEmpty();
			if (name.isEmpty()) {
				name = defaultName;
			}
			CharSequence charSequence = HtmlParser.spanify(comment, chan.markup.getMarkup(), null, null, this);
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
							Uri uri = chan.locator.validateClickedUriString((String) decodeTo[1],
									boardName, threadNumber);
							if (threadNumber.equals(chan.locator.safe(false).getThreadNumber(uri))) {
								PostNumber postNumber = chan.locator.safe(false).getPostNumber(uri);
								String postNumberString = postNumber == null ? threadNumber : postNumber.toString();
								extra = "#" + postNumberString;
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
			htmlBuilder.addPost(number.toString(), subject, name, identifier, tripcode, capcode, email,
					sage, originalPoster, timestamp, deleted, useDefaultName, comment);
			for (Post.Icon icon : post.icons) {
				if (icon.uri != null && !StringUtils.isEmpty(icon.title)) {
					Uri iconUri = chan.locator.convert(icon.uri);
					String simpleUri = iconUri.buildUpon().scheme(null).authority(null).build().toString();
					String pathHash = Base64.encodeToString(hasher.calculate(simpleUri), 0, 12,
							Base64.NO_WRAP | Base64.URL_SAFE);
					String iconNameWithoutExtension = "icon-" + pathHash;
					String iconName = iconNames.get(iconNameWithoutExtension);
					boolean downloadIcon = false;
					if (iconName == null) {
						String extension = null;
						if (ChanConfiguration.SCHEME_CHAN.equals(iconUri.getScheme())) {
							ByteArrayOutputStream output = new ByteArrayOutputStream();
							try {
								if (chan.configuration.readResourceUri(iconUri, output)) {
									byte[] bytes = output.toByteArray();
									String contentType = URLConnection
											.guessContentTypeFromStream(new ByteArrayInputStream(bytes));
									if (contentType != null) {
										extension = MimeTypes.toExtension(contentType);
									}
								}
							} catch (IOException e) {
								// Ignore
							}
						} else {
							extension = StringUtils.getFileExtension(iconUri.getPath());
						}
						iconName = iconNameWithoutExtension;
						if (!StringUtils.isEmpty(extension)) {
							iconName += "." + extension;
						}
						iconNames.put(iconNameWithoutExtension, iconName);
						downloadIcon = true;
					}
					String iconPath = archiveName + "/" + DIRECTORY_THUMBNAILS + "/" + iconName;
					htmlBuilder.addIcon(iconPath, icon.title);
					if (downloadIcon && saveThumbnails) {
						thumbnailsToDownload.add(new DownloadService.DownloadItem(chan.name, iconUri, iconName, null));
					}
				}
			}
			for (Post.Attachment attachment : post.attachments) {
				if (attachment instanceof Post.Attachment.File) {
					Post.Attachment.File file = (Post.Attachment.File) attachment;
					Uri fileUri = chan.locator.convert(chan.locator.fixRelativeFileUri(file.fileUri));
					Uri thumbnailUri = chan.locator.convert(chan.locator.fixRelativeFileUri(file.thumbnailUri));
					if (fileUri == null) {
						fileUri = thumbnailUri;
					}
					if (fileUri != null) {
						String fileName = chan.locator.createAttachmentFileName(fileUri);
						fileName = chooseFileName(existFilesLc, fileName);
						String filePath = archiveName + "/" + DIRECTORY_FILES + "/" + fileName;
						String thumbnailName = null;
						String thumbnailPath = null;
						if (thumbnailUri != null) {
							thumbnailName = chan.locator.createAttachmentFileName(thumbnailUri);
							thumbnailName = chooseFileName(existThumbnailsLc, thumbnailName);
							thumbnailPath = archiveName + "/" + DIRECTORY_THUMBNAILS + "/" + thumbnailName;
						}
						String originalName = StringUtils.getNormalizedOriginalName(file.originalName, fileName);
						htmlBuilder.addFile(filePath, thumbnailPath, originalName, file.size,
								file.width, file.height);
						if (saveFiles) {
							filesToDownload.add(new DownloadService.DownloadItem(chan.name,
									fileUri, fileName, null));
						}
						if (saveThumbnails && thumbnailUri != null) {
							thumbnailsToDownload.add(new DownloadService.DownloadItem(chan.name,
									thumbnailUri, thumbnailName, null));
						}
					}
				}
			}
			if (isCancelled()) {
				return null;
			}
			notifyIncrement();
		}
		String html = htmlBuilder.build();
		return new Result(html, archiveName, filesToDownload, thumbnailsToDownload);
	}

	@Override
	protected void onProgress(Integer value) {
		callback.onLocalArchivationProgressUpdate(value);
	}

	@SuppressWarnings("CharsetObjectCanBeUsed")
	@Override
	protected void onComplete(Result result) {
		DownloadResult downloadResult = null;
		if (result != null) {
			byte[] htmlBytes;
			try {
				htmlBytes = result.html.getBytes("UTF-8");
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
			ArrayList<DownloadResult> results = new ArrayList<>();
			results.add(createDownload(".nomedia", new ByteArrayInputStream(new byte[0])));
			results.add(createDownload(result.archiveName + ".html", new ByteArrayInputStream(htmlBytes)));
			results.add(createDownload(result.archiveName + "/" + DIRECTORY_THUMBNAILS, result.thumbnailsToDownload));
			results.add(createDownload(result.archiveName + "/" + DIRECTORY_FILES, result.filesToDownload));
			downloadResult = binder -> {
				try (DownloadService.Accumulate ignored = binder.accumulate()) {
					for (DownloadResult innerDownloadResult : results) {
						innerDownloadResult.run(binder);
					}
				}
			};
		}
		callback.onLocalArchivationComplete(downloadResult);
	}

	private long lastNotifyIncrement = 0L;
	private int progress = 0;

	public void notifyIncrement() {
		progress++;
		long t = SystemClock.elapsedRealtime();
		if (t - lastNotifyIncrement >= 100) {
			lastNotifyIncrement = t;
			notifyProgress(progress);
		}
	}

	private static DownloadResult createDownload(String name, InputStream input) {
		return binder -> binder.downloadDirect(DataFile.Target.DOWNLOADS, DIRECTORY_ARCHIVE, name, input);
	}

	private static DownloadResult createDownload(String path, List<DownloadService.DownloadItem> downloadItems) {
		return binder -> {
			if (path != null && downloadItems.size() > 0) {
				binder.downloadDirect(DataFile.Target.DOWNLOADS, DIRECTORY_ARCHIVE + "/" + path, false, downloadItems);
			}
		};
	}

	public static class Result {
		public final String html;
		public final String archiveName;
		public final List<DownloadService.DownloadItem> filesToDownload;
		public final List<DownloadService.DownloadItem> thumbnailsToDownload;

		private Result(String html, String archiveName,
				List<DownloadService.DownloadItem> filesToDownload,
				List<DownloadService.DownloadItem> thumbnailsToDownload) {
			this.html = html;
			this.archiveName = archiveName;
			this.filesToDownload = filesToDownload;
			this.thumbnailsToDownload = thumbnailsToDownload;
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
			result[1] = ((LinkSpan) span).uriString;
		} else if (span instanceof SpoilerSpan) {
			result[0] = ChanMarkup.TAG_SPOILER;
		} else if (span instanceof QuoteSpan) {
			result[0] = ChanMarkup.TAG_QUOTE;
		} else if (span instanceof ScriptSpan) {
			result[0] = ((ScriptSpan) span).isSuperscript() ? ChanMarkup.TAG_SUPERSCRIPT : ChanMarkup.TAG_SUBSCRIPT;
		} else if (span instanceof MediumSpan) {
			result[0] = ChanMarkup.TAG_BOLD;
		} else if (span instanceof ItalicSpan) {
			result[0] = ChanMarkup.TAG_ITALIC;
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
