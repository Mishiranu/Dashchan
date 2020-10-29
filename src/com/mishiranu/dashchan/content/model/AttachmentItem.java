package com.mishiranu.dashchan.content.model;

import android.net.Uri;
import chan.content.Chan;
import chan.content.ChanLocator;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.NetworkObserver;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.net.EmbeddedType;
import com.mishiranu.dashchan.widget.AttachmentView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class AttachmentItem {
	public enum Type {IMAGE, VIDEO, AUDIO, FILE}
	public enum GeneralType {FILE, EMBEDDED, LINK}

	public interface Master {
		String getBoardName();
		String getThreadNumber();
		PostNumber getPostNumber();
	}

	private final Master master;

	public abstract Uri getFileUri(Chan chan);
	public abstract Uri getThumbnailUri(Chan chan);
	public abstract String getThumbnailKey(Chan chan);
	public abstract String getDialogTitle(Chan chan);
	public abstract int getSize();
	public abstract Type getType();
	public abstract GeneralType getGeneralType();
	public abstract boolean isShowInGallery();
	public abstract boolean canDownloadToStorage();
	public abstract GalleryItem createGalleryItem();
	public abstract String getExtension();
	public abstract String getFileName(Chan chan);
	public abstract String getOriginalName();
	public abstract String getDescription(AttachmentItem.FormatMode formatMode);

	public String getBoardName() {
		return master.getBoardName();
	}

	public String getThreadNumber() {
		return master.getThreadNumber();
	}

	public PostNumber getPostNumber() {
		return master.getPostNumber();
	}

	private static class FileAttachmentItem extends AttachmentItem {
		public final Uri fileUri;
		public final Uri thumbnailUri;

		public final String originalName;
		public final String displayedExtension;
		public final Type type;
		public final int size;
		public final int width;
		public final int height;

		private String thumbnailKey;

		public FileAttachmentItem(Master master, ChanLocator locator, Uri fileUri, Uri thumbnailUri,
				String originalName, int size, int width, int height) {
			super(master);
			if (fileUri == null) {
				fileUri = thumbnailUri;
			}
			String fileName = locator.createAttachmentFileName(fileUri);
			String extension = StringUtils.getFileExtension(fileName);
			this.fileUri = fileUri;
			this.thumbnailUri = C.IMAGE_EXTENSIONS.contains(extension) ||
					C.VIDEO_EXTENSIONS.contains(extension) ? thumbnailUri : null;
			this.originalName = StringUtils.getNormalizedOriginalName(originalName, fileName);
			displayedExtension = StringUtils.getNormalizedExtension(extension);
			if (C.IMAGE_EXTENSIONS.contains(displayedExtension)) {
				type = Type.IMAGE;
			} else if (C.VIDEO_EXTENSIONS.contains(displayedExtension)) {
				type = Type.VIDEO;
			} else if (C.AUDIO_EXTENSIONS.contains(displayedExtension)) {
				type = Type.AUDIO;
			} else {
				type = Type.FILE;
			}
			this.size = size;
			this.width = width;
			this.height = height;
		}

		@Override
		public Uri getFileUri(Chan chan) {
			return chan.locator.convert(fileUri);
		}

		@Override
		public Uri getThumbnailUri(Chan chan) {
			return chan.locator.convert(thumbnailUri);
		}

		@Override
		public String getThumbnailKey(Chan chan) {
			if (thumbnailKey == null && thumbnailUri != null) {
				thumbnailKey = CacheManager.getInstance().getCachedFileKey(getThumbnailUri(chan));
			}
			return thumbnailKey;
		}

		@Override
		public String getDialogTitle(Chan chan) {
			return !StringUtils.isEmpty(originalName) ? originalName : getFileName(chan);
		}

		@Override
		public int getSize() {
			return size;
		}

		@Override
		public Type getType() {
			return type;
		}

		@Override
		public GeneralType getGeneralType() {
			return GeneralType.FILE;
		}

		@Override
		public boolean isShowInGallery() {
			return type == Type.IMAGE || type == Type.VIDEO;
		}

		@Override
		public boolean canDownloadToStorage() {
			return true;
		}

		@Override
		public GalleryItem createGalleryItem() {
			return new GalleryItem(fileUri, thumbnailUri, getBoardName(), getThreadNumber(),
					getPostNumber(), originalName, width, height, size);
		}

		@Override
		public String getExtension() {
			return displayedExtension;
		}

		@Override
		public String getFileName(Chan chan) {
			return chan.locator.createAttachmentFileName(getFileUri(chan));
		}

		@Override
		public String getOriginalName() {
			return originalName;
		}

		@Override
		public String getDescription(AttachmentItem.FormatMode formatMode) {
			StringBuilder builder = new StringBuilder();
			switch (formatMode) {
				case LONG: {
					if (displayedExtension != null) {
						builder.append(displayedExtension.toUpperCase(Locale.US));
					}
				}
				case SIMPLE: {
					if (width > 0 && height > 0) {
						if (builder.length() > 0) {
							builder.append(' ');
						}
						builder.append(width).append('×').append(height);
					}
					if (size > 0) {
						if (builder.length() > 0) {
							builder.append(' ');
						}
						builder.append(StringUtils.formatFileSize(size, true));
					}
					break;
				}
				case TWO_LINES:
				case THREE_LINES: {
					if (displayedExtension != null) {
						builder.append(displayedExtension.toUpperCase(Locale.US));
					}
					if (size > 0) {
						builder.append(formatMode == FormatMode.THREE_LINES ? '\n' : ' ');
						builder.append(StringUtils.formatFileSize(size, true));
					}
					if (width > 0 && height > 0) {
						builder.append('\n').append(width).append('×').append(height);
					}
					break;
				}
			}
			return builder.toString();
		}
	}

	private static class EmbeddedAttachmentItem extends AttachmentItem {
		public final boolean isAudio;
		public final boolean isVideo;

		public final String embeddedType;
		public final boolean fromComment;

		public final Uri fileUri;
		public final Uri thumbnailUri;
		public final boolean canDownload;
		public final String fileName;

		private String thumbnailKey;

		public EmbeddedAttachmentItem(Master master, Uri fileUri, Uri thumbnailUri,
				String embeddedType, Post.Attachment.Embedded.ContentType contentType,
				boolean canDownload, String fileName, boolean fromComment) {
			super(master);
			this.fileUri = fileUri;
			this.thumbnailUri = thumbnailUri;
			this.embeddedType = embeddedType;
			isAudio = contentType == Post.Attachment.Embedded.ContentType.AUDIO;
			isVideo = contentType == Post.Attachment.Embedded.ContentType.VIDEO;
			this.canDownload = canDownload;
			this.fileName = fileName;
			this.fromComment = fromComment;
		}

		@Override
		public Uri getFileUri(Chan chan) {
			return fileUri;
		}

		@Override
		public Uri getThumbnailUri(Chan chan) {
			return thumbnailUri;
		}

		@Override
		public String getThumbnailKey(Chan chan) {
			if (thumbnailKey == null && thumbnailUri != null) {
				thumbnailKey = CacheManager.getInstance().getCachedFileKey(getThumbnailUri(chan));
			}
			return thumbnailKey;
		}

		@Override
		public String getDialogTitle(Chan chan) {
			return embeddedType;
		}

		@Override
		public int getSize() {
			return 0;
		}

		@Override
		public Type getType() {
			return isAudio ? Type.AUDIO : isVideo ? Type.VIDEO : Type.FILE;
		}

		@Override
		public GeneralType getGeneralType() {
			return fromComment ? GeneralType.LINK : GeneralType.FILE;
		}

		@Override
		public boolean isShowInGallery() {
			return false;
		}

		@Override
		public boolean canDownloadToStorage() {
			return canDownload;
		}

		@Override
		public GalleryItem createGalleryItem() {
			return null;
		}

		@Override
		public String getExtension() {
			return null;
		}

		@Override
		public String getFileName(Chan chan) {
			return fileName;
		}

		@Override
		public String getOriginalName() {
			return null;
		}

		@Override
		public String getDescription(FormatMode formatMode) {
			StringBuilder builder = new StringBuilder();
			if (formatMode == FormatMode.LONG || formatMode == FormatMode.TWO_LINES
					|| formatMode == FormatMode.THREE_LINES) {
				builder.append(fromComment ? "URL" : "Embedded");
				builder.append(formatMode == FormatMode.TWO_LINES || formatMode == FormatMode.THREE_LINES
						? '\n' : ' ');
			}
			builder.append(embeddedType);
			return builder.toString();
		}
	}

	protected AttachmentItem(Master master) {
		this.master = master;
	}

	public static ArrayList<AttachmentItem> obtain(Master master, Post post, ChanLocator locator) {
		ArrayList<AttachmentItem> attachmentItems = new ArrayList<>();
		for (Post.Attachment attachment : post.attachments) {
			AttachmentItem attachmentItem = null;
			if (attachment instanceof Post.Attachment.File) {
				attachmentItem = obtainFileAttachmentItem(master, locator, (Post.Attachment.File) attachment);
			} else if (attachment instanceof Post.Attachment.Embedded) {
				attachmentItem = obtainEmbeddedAttachmentItem(master, locator,
						(Post.Attachment.Embedded) attachment, false);
			}
			if (attachmentItem != null) {
				attachmentItems.add(attachmentItem);
			}
		}
		for (EmbeddedType embeddedType : EmbeddedType.values()) {
			addCommentAttachmentItems(attachmentItems, master, locator, post.comment, embeddedType);
		}
		if (attachmentItems.size() > 0) {
			attachmentItems.trimToSize();
			return attachmentItems;
		}
		return null;
	}

	public enum FormatMode {LONG, SIMPLE, TWO_LINES, THREE_LINES}

	private static FileAttachmentItem obtainFileAttachmentItem(Master master, ChanLocator locator,
			Post.Attachment.File file) {
		if (file == null) {
			return null;
		}
		Uri fileUri = locator.convert(locator.fixRelativeFileUri(file.fileUri));
		Uri thumbnailUri = locator.convert(locator.fixRelativeFileUri(file.thumbnailUri));
		if (fileUri == null && thumbnailUri == null) {
			return null;
		}
		return new FileAttachmentItem(master, locator, fileUri, thumbnailUri, file.originalName,
				file.size, file.width, file.height);
	}

	private static EmbeddedAttachmentItem obtainEmbeddedAttachmentItem(Master master,
			ChanLocator locator, Post.Attachment.Embedded embedded, boolean fromComment) {
		if (embedded == null) {
			return null;
		}
		Uri fileUri = embedded.fileUri;
		if (fileUri == null) {
			return null;
		}
		boolean canDownload = embedded.canDownload;
		String fileName = "";
		if (canDownload) {
			String forcedName = StringUtils.escapeFile(embedded.forcedName, false);
			fileName = locator.createAttachmentFileName(fileUri, forcedName);
		}
		return new EmbeddedAttachmentItem(master, fileUri, embedded.thumbnailUri, embedded.embeddedType,
				embedded.contentType, canDownload, fileName, fromComment);
	}

	private static void addCommentAttachmentItems(List<AttachmentItem> attachmentItems, Master master,
			ChanLocator locator, String comment, EmbeddedType embeddedType) {
		String[] embeddedCodes = embeddedType.getAll(locator, comment);
		if (embeddedCodes != null && embeddedCodes.length > 0) {
			for (String embeddedCode : embeddedCodes) {
				AttachmentItem attachmentItem = obtainEmbeddedAttachmentItem(master, locator,
						embeddedType.obtainAttachment(locator, embeddedCode), true);
				if (attachmentItem != null) {
					attachmentItems.add(attachmentItem);
				}
			}
		}
	}

	public void configureAndLoad(AttachmentView view, Chan chan, boolean needShowMultipleIcon, boolean force) {
		view.setCropEnabled(Preferences.isCutThumbnails());
		Type type = getType();
		String key = getThumbnailKey(chan);
		AttachmentView.Overlay overlay = AttachmentView.Overlay.NONE;
		if (needShowMultipleIcon) {
			overlay = AttachmentView.Overlay.MULTIPLE;
		} else {
			switch (type) {
				case IMAGE: {
					if (StringUtils.isEmpty(key)) {
						overlay = AttachmentView.Overlay.WARNING;
					}
					break;
				}
				case VIDEO: {
					overlay = AttachmentView.Overlay.VIDEO;
					break;
				}
				case AUDIO: {
					overlay = AttachmentView.Overlay.AUDIO;
					break;
				}
				case FILE: {
					overlay = AttachmentView.Overlay.FILE;
					break;
				}
				default: {
					overlay = AttachmentView.Overlay.WARNING;
					break;
				}
			}
		}
		view.resetImage(key, overlay);
		startLoad(view, chan, key, force);
	}

	public void startLoad(AttachmentView view, Chan chan, boolean force) {
		startLoad(view, chan, getThumbnailKey(chan), force);
	}

	private void startLoad(AttachmentView view, Chan chan, String key, boolean force) {
		if (key != null) {
			Uri uri = getThumbnailUri(chan);
			boolean loadThumbnails = Preferences.getLoadThumbnails()
					.isNetworkAvailable(NetworkObserver.getInstance());
			boolean allowDownload = loadThumbnails || force;
			ImageLoader.getInstance().loadImage(chan, uri, key, !allowDownload, view);
		} else {
			ImageLoader.getInstance().cancel(view);
		}
	}

	public boolean canLoadThumbnailManually(AttachmentView attachmentView, Chan chan) {
		return getThumbnailKey(chan) != null && !attachmentView.hasImage() &&
				!ImageLoader.getInstance().hasRunningTask(attachmentView);
	}
}
