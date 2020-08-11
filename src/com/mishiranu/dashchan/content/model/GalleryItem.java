package com.mishiranu.dashchan.content.model;

import android.content.Context;
import android.net.Uri;
import chan.content.ChanLocator;
import com.mishiranu.dashchan.content.DownloadManager;
import com.mishiranu.dashchan.util.ConfigurationLock;
import com.mishiranu.dashchan.util.NavigationUtils;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

public class GalleryItem implements Serializable {
	private static final long serialVersionUID = 1L;

	private final String fileUriString;
	private final String thumbnailUriString;

	public final String boardName;
	public final String threadNumber;
	public final String postNumber;

	public final String originalName;

	public final int width;
	public final int height;

	public int size;

	private transient Uri fileUri;
	private transient Uri thumbnailUri;

	public GalleryItem(Uri fileUri, Uri thumbnailUri, String boardName, String threadNumber, String postNumber,
			String originalName, int width, int height, int size) {
		fileUriString = fileUri != null ? fileUri.toString() : null;
		thumbnailUriString = thumbnailUri != null ? thumbnailUri.toString() : null;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.postNumber = postNumber;
		this.originalName = originalName;
		this.width = width;
		this.height = height;
		this.size = size;
	}

	public GalleryItem(Uri fileUri, String boardName, String threadNumber) {
		fileUriString = null;
		thumbnailUriString = null;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		postNumber = null;
		originalName = null;
		width = 0;
		height = 0;
		size = 0;
		this.fileUri = fileUri;
	}

	public boolean isImage(ChanLocator locator) {
		return locator.isImageExtension(getFileName(locator));
	}

	public boolean isVideo(ChanLocator locator) {
		return locator.isVideoExtension(getFileName(locator));
	}

	public boolean isOpenableVideo(ChanLocator locator) {
		return NavigationUtils.isOpenableVideoPath(getFileName(locator));
	}

	public Uri getFileUri(ChanLocator locator) {
		if (fileUri == null && fileUriString != null) {
			fileUri = locator.convert(Uri.parse(fileUriString));
		}
		return fileUri;
	}

	public Uri getThumbnailUri(ChanLocator locator) {
		if (thumbnailUri == null && thumbnailUriString != null) {
			thumbnailUri = locator.convert(Uri.parse(thumbnailUriString));
		}
		return thumbnailUri;
	}

	public Uri getDisplayImageUri(ChanLocator locator) {
		return isImage(locator) ? getFileUri(locator) : getThumbnailUri(locator);
	}

	public String getFileName(ChanLocator locator) {
		Uri fileUri = getFileUri(locator);
		return locator.createAttachmentFileName(fileUri);
	}

	public void downloadStorage(Context context, ConfigurationLock configurationLock,
			ChanLocator locator, String threadTitle) {
		DownloadManager.getInstance().downloadStorage(context, configurationLock,
				getFileUri(locator), getFileName(locator), originalName,
				locator.getChanName(), boardName, threadNumber, threadTitle);
	}

	public void cleanup() {
		if (fileUriString != null) {
			fileUri = null;
		}
		if (thumbnailUriString != null) {
			thumbnailUri = null;
		}
	}

	public static class GallerySet {
		private final boolean navigatePostSupported;
		private final ArrayList<GalleryItem> galleryItems = new ArrayList<>();

		private String threadTitle;

		public GallerySet(boolean navigatePostSupported) {
			this.navigatePostSupported = navigatePostSupported;
		}

		public void setThreadTitle(String threadTitle) {
			this.threadTitle = threadTitle;
		}

		public String getThreadTitle() {
			return threadTitle;
		}

		public void add(Collection<AttachmentItem> attachmentItems) {
			if (attachmentItems != null) {
				for (AttachmentItem attachmentItem : attachmentItems) {
					if (attachmentItem.isShowInGallery() && attachmentItem.canDownloadToStorage()) {
						add(attachmentItem.createGalleryItem());
					}
				}
			}
		}

		public void add(GalleryItem galleryItem) {
			if (galleryItem != null) {
				galleryItems.add(galleryItem);
			}
		}

		public void cleanup() {
			for (GalleryItem galleryItem : galleryItems) {
				galleryItem.cleanup();
			}
		}

		public void clear() {
			galleryItems.clear();
		}

		public ArrayList<GalleryItem> getItems() {
			return galleryItems.size() > 0 ? galleryItems : null;
		}

		public boolean isNavigatePostSupported() {
			return navigatePostSupported;
		}
	}
}
