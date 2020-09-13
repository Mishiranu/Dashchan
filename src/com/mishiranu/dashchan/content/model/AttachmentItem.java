package com.mishiranu.dashchan.content.model;

import android.net.Uri;
import chan.content.ChanLocator;
import chan.content.model.Attachment;
import chan.content.model.EmbeddedAttachment;
import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.net.EmbeddedType;
import com.mishiranu.dashchan.widget.AttachmentView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public abstract class AttachmentItem {
	public enum Type {IMAGE, VIDEO, AUDIO, FILE}
	public enum GeneralType {FILE, EMBEDDED, LINK}

	private final Binder binder;

	public abstract Uri getFileUri();
	public abstract Uri getThumbnailUri();
	public abstract String getThumbnailKey();
	public abstract String getDialogTitle();
	public abstract int getSize();
	public abstract Type getType();
	public abstract GeneralType getGeneralType();
	public abstract boolean isShowInGallery();
	public abstract boolean canDownloadToStorage();
	public abstract GalleryItem createGalleryItem();
	public abstract String getExtension();
	public abstract String getFileName();
	public abstract String getOriginalName();
	public abstract String getDescription(AttachmentItem.FormatMode formatMode);

	public String getChanName() {
		return binder.getChanName();
	}

	public String getBoardName() {
		return binder.getBoardName();
	}

	public String getThreadNumber() {
		return binder.getThreadNumber();
	}

	public String getPostNumber() {
		return binder.getPostNumber();
	}

	private static class FileAttachmentItem extends AttachmentItem {
		public Uri fileUri;
		public Uri thumbnailUri;

		public String originalName;
		public String displayedExtension;
		public int size;
		public int width;
		public int height;

		private String thumbnailKey;
		private Type type = Type.IMAGE;

		public FileAttachmentItem(Binder binder) {
			super(binder);
		}

		private ChanLocator getLocator() {
			return ChanLocator.get(getChanName());
		}

		@Override
		public Uri getFileUri() {
			return getLocator().convert(fileUri);
		}

		@Override
		public Uri getThumbnailUri() {
			return getLocator().convert(thumbnailUri);
		}

		@Override
		public String getThumbnailKey() {
			if (thumbnailKey == null && thumbnailUri != null) {
				thumbnailKey = CacheManager.getInstance().getCachedFileKey(getThumbnailUri());
			}
			return thumbnailKey;
		}

		@Override
		public String getDialogTitle() {
			return originalName != null ? originalName : getFileName();
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
		public String getFileName() {
			return getLocator().createAttachmentFileName(getFileUri());
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
						builder.append(formatSize(size));
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
						builder.append(formatSize(size));
					}
					if (width > 0 && height > 0) {
						builder.append('\n').append(width).append('×').append(height);
					}
					break;
				}
			}
			return builder.toString();
		}

		public void setDisplayedExtension(String displayedExtension) {
			this.displayedExtension = displayedExtension;
			if (C.IMAGE_EXTENSIONS.contains(displayedExtension)) {
				type = Type.IMAGE;
			} else if (C.VIDEO_EXTENSIONS.contains(displayedExtension)) {
				type = Type.VIDEO;
			} else if (C.AUDIO_EXTENSIONS.contains(displayedExtension)) {
				type = Type.AUDIO;
			} else {
				type = Type.FILE;
			}
		}
	}

	private static class EmbeddedAttachmentItem extends AttachmentItem {
		public boolean isAudio;
		public boolean isVideo;

		public String embeddedType;
		public boolean fromComment;

		public Uri fileUri;
		public Uri thumbnailUri;
		public boolean canDownload;
		public String fileName;
		public String title;

		private String thumbnailKey;

		public EmbeddedAttachmentItem(Binder binder) {
			super(binder);
		}

		@Override
		public Uri getFileUri() {
			return fileUri;
		}

		@Override
		public Uri getThumbnailUri() {
			return thumbnailUri;
		}

		@Override
		public String getThumbnailKey() {
			if (thumbnailKey == null && thumbnailUri != null) {
				thumbnailKey = CacheManager.getInstance().getCachedFileKey(getThumbnailUri());
			}
			return thumbnailKey;
		}

		@Override
		public String getDialogTitle() {
			return title != null ? embeddedType + ": " + title : embeddedType;
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
		public String getFileName() {
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

	public interface Binder {
		public String getChanName();
		public String getBoardName();
		public String getThreadNumber();
		public String getPostNumber();
	}

	protected AttachmentItem(Binder binder) {
		this.binder = binder;
	}

	public static ArrayList<AttachmentItem> obtain(PostItem postItem) {
		ArrayList<AttachmentItem> attachmentItems = new ArrayList<>();
		ChanLocator locator = ChanLocator.get(postItem.getChanName());
		Post post = postItem.getPost();
		for (int i = 0, count = post.getAttachmentsCount(); i < count; i++) {
			AttachmentItem attachmentItem = null;
			Attachment attachment = post.getAttachmentAt(i);
			if (attachment instanceof FileAttachment) {
				attachmentItem = obtainFileAttachmentItem(postItem, locator, (FileAttachment) attachment);
			} else if (attachment instanceof EmbeddedAttachment) {
				attachmentItem = obtainEmbeddedAttachmentItem(postItem, locator, (EmbeddedAttachment) attachment);
			}
			if (attachmentItem != null) {
				attachmentItems.add(attachmentItem);
			}
		}
		String comment = postItem.getRawComment();
		for (EmbeddedType embeddedType : EmbeddedType.values()) {
			addCommentAttachmentItems(attachmentItems, postItem, locator, comment, embeddedType);
		}
		if (attachmentItems.size() > 0) {
			attachmentItems.trimToSize();
			return attachmentItems;
		}
		return null;
	}

	private static ArrayList<String> getAllCodes(String... codes) {
		if (codes != null && codes.length > 0) {
			ArrayList<String> list = new ArrayList<>(codes.length);
			Collections.addAll(list, codes);
			return list;
		}
		return null;
	}

	public static String formatSize(int size) {
		size /= 1024;
		return size >= 1024 ? String.format(Locale.US, "%.1f", size / 1024f) + " MB" : size + " KB";
	}

	public enum FormatMode {LONG, SIMPLE, TWO_LINES, THREE_LINES}

	private static FileAttachmentItem obtainFileAttachmentItem(Binder binder, ChanLocator locator,
			FileAttachment attachment) {
		if (attachment == null) {
			return null;
		}
		FileAttachmentItem attachmentItem = new FileAttachmentItem(binder);
		attachmentItem.size = attachment.getSize();
		attachmentItem.width = attachment.getWidth();
		attachmentItem.height = attachment.getHeight();
		Uri fileUri = attachment.getRelativeFileUri();
		Uri thumbnailUri = attachment.getRelativeThumbnailUri();
		if (fileUri != null || thumbnailUri != null) {
			if (fileUri == null) {
				fileUri = thumbnailUri;
			}
			String fileName = locator.createAttachmentFileName(fileUri);
			String extension = StringUtils.getFileExtension(fileName);
			attachmentItem.fileUri = fileUri;
			if (C.IMAGE_EXTENSIONS.contains(extension) || C.VIDEO_EXTENSIONS.contains(extension)) {
				attachmentItem.thumbnailUri = thumbnailUri;
			}
			attachmentItem.setDisplayedExtension(FileAttachment.getNormalizedExtension(extension));
			attachmentItem.originalName = attachment.getNormalizedOriginalName(fileName, extension);
			return attachmentItem;
		}
		return null;
	}

	private static EmbeddedAttachmentItem obtainEmbeddedAttachmentItem(Binder binder, ChanLocator locator,
			EmbeddedAttachment attachment) {
		if (attachment == null) {
			return null;
		}
		Uri fileUri = attachment.getFileUri();
		if (fileUri == null) {
			return null;
		}
		EmbeddedAttachmentItem attachmentItem = new EmbeddedAttachmentItem(binder);
		attachmentItem.fileUri = fileUri;
		attachmentItem.thumbnailUri = attachment.getThumbnailUri();
		attachmentItem.embeddedType = attachment.getEmbeddedType();
		EmbeddedAttachment.ContentType contentType = attachment.getContentType();
		attachmentItem.isAudio = contentType == EmbeddedAttachment.ContentType.AUDIO;
		attachmentItem.isVideo = contentType == EmbeddedAttachment.ContentType.VIDEO;
		attachmentItem.canDownload = attachment.isCanDownload();
		attachmentItem.fileName = attachmentItem.canDownload ? locator.createAttachmentFileName(fileUri,
				attachment.getNormalizedForcedName()) : null;
		attachmentItem.title = attachment.getTitle();
		return attachmentItem;
	}

	private static EmbeddedAttachmentItem obtainCommentAttachmentItem(Binder binder, ChanLocator locator,
			EmbeddedType embeddedType, String embeddedCode) {
		EmbeddedAttachmentItem attachmentItem = obtainEmbeddedAttachmentItem(binder, locator,
				embeddedType.obtainAttachment(locator, embeddedCode));
		attachmentItem.fromComment = true;
		return attachmentItem;
	}

	private static void addCommentAttachmentItems(ArrayList<AttachmentItem> attachmentItems, Binder binder,
			ChanLocator locator, String comment, EmbeddedType embeddedType) {
		ArrayList<String> embeddedCodes = getAllCodes(embeddedType.getAll(locator, comment));
		if (embeddedCodes != null && embeddedCodes.size() > 0) {
			for (String embeddedCode : embeddedCodes) {
				attachmentItems.add(obtainCommentAttachmentItem(binder, locator, embeddedType, embeddedCode));
			}
		}
	}

	public void configureAndLoad(AttachmentView view, boolean needShowMultipleIcon, boolean force) {
		view.setCropEnabled(Preferences.isCutThumbnails());
		Type type = getType();
		String key = getThumbnailKey();
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
		startLoad(view, key, force);
	}

	public void startLoad(AttachmentView view, boolean force) {
		startLoad(view, getThumbnailKey(), force);
	}

	private void startLoad(AttachmentView view, String key, boolean force) {
		if (key != null) {
			Uri uri = getThumbnailUri();
			boolean loadThumbnails = Preferences.isLoadThumbnails();
			boolean allowDownload = loadThumbnails || force;
			ImageLoader.getInstance().loadImage(getChanName(), uri, key, !allowDownload, view);
		} else {
			ImageLoader.getInstance().cancel(view);
		}
	}

	public boolean canLoadThumbnailManually(AttachmentView attachmentView) {
		return getThumbnailKey() != null && !attachmentView.hasImage() &&
				!ImageLoader.getInstance().hasRunningTask(attachmentView);
	}
}
