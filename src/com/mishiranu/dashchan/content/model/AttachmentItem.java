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

package com.mishiranu.dashchan.content.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import android.graphics.Bitmap;
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
import com.mishiranu.dashchan.content.net.EmbeddedManager;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.widget.AttachmentView;

public abstract class AttachmentItem {
	private final Binder binder;

	public static final int TYPE_IMAGE = 0;
	public static final int TYPE_VIDEO = 1;
	public static final int TYPE_AUDIO = 2;
	public static final int TYPE_FILE = 3;

	public static final int GENERAL_TYPE_FILE = 0;
	public static final int GENERAL_TYPE_EMBEDDED = 1;
	public static final int GENERAL_TYPE_LINK = 2;

	public abstract Uri getFileUri();
	public abstract Uri getThumbnailUri();
	public abstract String getThumbnailKey();
	public abstract String getDialogTitle();
	public abstract int getSize();
	public abstract int getType();
	public abstract int getGeneralType();
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
		private int type = TYPE_IMAGE;

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
		public int getType() {
			return type;
		}

		@Override
		public int getGeneralType() {
			return GENERAL_TYPE_FILE;
		}

		@Override
		public boolean isShowInGallery() {
			return type == TYPE_IMAGE || type == TYPE_VIDEO;
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
						builder.append(width).append('x').append(height);
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
						builder.append('\n').append(width).append('x').append(height);
					}
					break;
				}
			}
			return builder.toString();
		}

		public void setDisplayedExtension(String displayedExtension) {
			this.displayedExtension = displayedExtension;
			if (C.IMAGE_EXTENSIONS.contains(displayedExtension)) {
				type = TYPE_IMAGE;
			} else if (C.VIDEO_EXTENSIONS.contains(displayedExtension)) {
				type = TYPE_VIDEO;
			} else if (C.AUDIO_EXTENSIONS.contains(displayedExtension)) {
				type = TYPE_AUDIO;
			} else {
				type = TYPE_FILE;
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
		public int getType() {
			return isAudio ? TYPE_AUDIO : isVideo ? TYPE_VIDEO : TYPE_FILE;
		}

		@Override
		public int getGeneralType() {
			return fromComment ? GENERAL_TYPE_LINK : GENERAL_TYPE_FILE;
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
		addCommentAttachmentItems(attachmentItems, postItem, locator, comment, URI_TYPE_YOUTUBE);
		addCommentAttachmentItems(attachmentItems, postItem, locator, comment, URI_TYPE_VIMEO);
		addCommentAttachmentItems(attachmentItems, postItem, locator, comment, URI_TYPE_VOCAROO);
		addCommentAttachmentItems(attachmentItems, postItem, locator, comment, URI_TYPE_SOUNDCLOUD);
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

	private static final int URI_TYPE_YOUTUBE = 0;
	private static final int URI_TYPE_VIMEO = 1;
	private static final int URI_TYPE_VOCAROO = 2;
	private static final int URI_TYPE_SOUNDCLOUD = 3;

	private static EmbeddedAttachmentItem obtainCommentAttachmentItem(Binder binder, ChanLocator locator,
			String embeddedCode, int uriType) {
		EmbeddedAttachmentItem attachmentItem;
		switch (uriType) {
			case URI_TYPE_YOUTUBE: {
				attachmentItem = obtainEmbeddedAttachmentItem(binder, locator,
						EmbeddedManager.getInstance().obtainYouTubeAttachment(locator, embeddedCode));
				break;
			}
			case URI_TYPE_VIMEO: {
				attachmentItem = obtainEmbeddedAttachmentItem(binder, locator,
						EmbeddedManager.getInstance().obtainVimeoAttachment(locator, embeddedCode));
				break;
			}
			case URI_TYPE_VOCAROO: {
				attachmentItem = obtainEmbeddedAttachmentItem(binder, locator,
						EmbeddedManager.getInstance().obtainVocarooAttachment(locator, embeddedCode));
				break;
			}
			case URI_TYPE_SOUNDCLOUD: {
				attachmentItem = obtainEmbeddedAttachmentItem(binder, locator,
						EmbeddedManager.getInstance().obtainSoundCloudAttachment(locator, embeddedCode));
				break;
			}
			default: {
				throw new RuntimeException();
			}
		}
		attachmentItem.fromComment = true;
		return attachmentItem;
	}

	private static void addCommentAttachmentItems(ArrayList<AttachmentItem> attachmentItems, Binder binder,
			ChanLocator locator, String comment, int uriType) {
		ArrayList<String> embeddedCodes;
		switch (uriType) {
			case URI_TYPE_YOUTUBE: {
				embeddedCodes = getAllCodes(locator.getYouTubeEmbeddedCodes(comment));
				break;
			}
			case URI_TYPE_VIMEO: {
				embeddedCodes = getAllCodes(locator.getVimeoEmbeddedCodes(comment));
				break;
			}
			case URI_TYPE_VOCAROO: {
				embeddedCodes = getAllCodes(locator.getVocarooEmbeddedCodes(comment));
				break;
			}
			case URI_TYPE_SOUNDCLOUD: {
				embeddedCodes = getAllCodes(locator.getSoundCloudEmbeddedCodes(comment));
				break;
			}
			default: {
				throw new RuntimeException();
			}
		}
		if (embeddedCodes != null && embeddedCodes.size() > 0) {
			for (String embeddedCode : embeddedCodes) {
				attachmentItems.add(obtainCommentAttachmentItem(binder, locator, embeddedCode, uriType));
			}
		}
	}

	private boolean forceLoadThumbnail = false;

	public void setForceLoadThumbnail() {
		forceLoadThumbnail = true;
	}

	public void configureAndLoad(AttachmentView view, boolean needShowSeveralIcon, boolean isBusy, boolean force) {
		view.setCropEnabled(Preferences.isCutThumbnails());
		int type = getType();
		String key = getThumbnailKey();
		AttachmentView.Overlay overlay = AttachmentView.Overlay.NONE;
		if (needShowSeveralIcon) {
			overlay = AttachmentView.Overlay.SEVERAL;
		} else {
			switch (type) {
				case TYPE_IMAGE: {
					if (StringUtils.isEmpty(key)) {
						overlay = AttachmentView.Overlay.WARNING;
					}
					break;
				}
				case TYPE_VIDEO: {
					overlay = AttachmentView.Overlay.VIDEO;
					break;
				}
				case TYPE_AUDIO: {
					overlay = AttachmentView.Overlay.AUDIO;
					break;
				}
				case TYPE_FILE: {
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
		startLoad(view, key, isBusy, force);
	}

	public void startLoad(AttachmentView view, boolean isBusy, boolean force) {
		startLoad(view, getThumbnailKey(), isBusy, force);
	}

	private void startLoad(AttachmentView view, String key, boolean isBusy, boolean force) {
		if (key != null) {
			Uri uri = getThumbnailUri();
			boolean loadThumbnails = Preferences.isLoadThumbnails();
			boolean isCachedMemory = CacheManager.getInstance().isThumbnailCachedMemory(key);
			// Load image if cached in RAM or list isn't scrolling (for better performance)
			if (!isBusy || isCachedMemory) {
				boolean fromCacheOnly = isCachedMemory || !(loadThumbnails || force || forceLoadThumbnail);
				ImageLoader.BitmapResult result = ImageLoader.getInstance().loadImage(uri, getChanName(),
						key, null, fromCacheOnly);
				if (result != null) {
					view.handleLoadedImage(key, result.bitmap, true);
				}
			}
		}
	}

	public boolean isThumbnailReady() {
		String thumbnailKey = getThumbnailKey();
		return thumbnailKey == null || CacheManager.getInstance().isThumbnailCachedMemory(thumbnailKey);
	}

	public boolean canLoadThumbnailManually() {
		return !isThumbnailReady() && !forceLoadThumbnail && !Preferences.isLoadThumbnails();
	}
}