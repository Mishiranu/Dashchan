package com.mishiranu.dashchan.content.model;

import android.net.Uri;
import chan.content.Chan;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.util.NavigationUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

public class GalleryItem {
	private final String fileUriString;
	private final String thumbnailUriString;

	public final String boardName;
	public final String threadNumber;
	public final PostNumber postNumber;

	public final String originalName;

	public final int width;
	public final int height;
	public final int size;

	private transient Uri fileUri;
	private transient Uri thumbnailUri;

	public GalleryItem(Uri fileUri, Uri thumbnailUri, String boardName, String threadNumber, PostNumber postNumber,
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

	public boolean isImage(Chan chan) {
		return chan.locator.isImageExtension(getFileName(chan));
	}

	public boolean isVideo(Chan chan) {
		return chan.locator.isVideoExtension(getFileName(chan));
	}

	public boolean isOpenableVideo(Chan chan) {
		return NavigationUtils.isOpenableVideoPath(getFileName(chan));
	}

	public Uri getFileUri(Chan chan) {
		if (fileUri == null && fileUriString != null) {
			fileUri = chan.locator.convert(Uri.parse(fileUriString));
		}
		return fileUri;
	}

	public Uri getThumbnailUri(Chan chan) {
		if (thumbnailUri == null && thumbnailUriString != null) {
			thumbnailUri = chan.locator.convert(Uri.parse(thumbnailUriString));
		}
		return thumbnailUri;
	}

	public Uri getDisplayImageUri(Chan chan) {
		return isImage(chan) ? getFileUri(chan) : getThumbnailUri(chan);
	}

	public String getFileName(Chan chan) {
		Uri fileUri = getFileUri(chan);
		return chan.locator.createAttachmentFileName(fileUri);
	}

	public void downloadStorage(DownloadService.Binder binder, Chan chan, String threadTitle) {
		binder.downloadStorage(getFileUri(chan), getFileName(chan), originalName,
				chan.name, boardName, threadNumber, threadTitle);
	}

	public interface Provider {
		GalleryItem.Set getGallerySet(PostItem postItem);
	}

	public static class Set implements Provider {
		private final boolean navigatePostSupported;
		private final TreeMap<PostNumber, List<GalleryItem>> galleryItems = new TreeMap<>();

		private String threadTitle;

		public Set(boolean navigatePostSupported) {
			this.navigatePostSupported = navigatePostSupported;
		}

		public void setThreadTitle(String threadTitle) {
			this.threadTitle = threadTitle;
		}

		public String getThreadTitle() {
			return threadTitle;
		}

		public void put(PostNumber postNumber, Collection<AttachmentItem> attachmentItems) {
			if (attachmentItems != null) {
				ArrayList<GalleryItem> galleryItems = new ArrayList<>();
				for (AttachmentItem attachmentItem : attachmentItems) {
					if (attachmentItem.isShowInGallery() && attachmentItem.canDownloadToStorage()) {
						galleryItems.add(attachmentItem.createGalleryItem());
					}
				}
				if (!galleryItems.isEmpty()) {
					this.galleryItems.put(postNumber, galleryItems);
				}
			}
		}

		public void remove(PostNumber postNumber) {
			galleryItems.remove(postNumber);
		}

		public void clear() {
			galleryItems.clear();
		}

		public int findIndex(PostItem postItem) {
			if (postItem.hasAttachments()) {
				int index = 0;
				PostNumber postNumber = postItem.getPostNumber();
				for (TreeMap.Entry<PostNumber, List<GalleryItem>> entry : galleryItems.entrySet()) {
					if (postNumber.equals(entry.getKey())) {
						return index;
					}
					index += entry.getValue().size();
				}
			}
			return -1;
		}

		public List<GalleryItem> createList() {
			ArrayList<GalleryItem> galleryItems = new ArrayList<>();
			for (List<GalleryItem> list : this.galleryItems.values()) {
				galleryItems.addAll(list);
			}
			return galleryItems;
		}

		public boolean isNavigatePostSupported() {
			return navigatePostSupported;
		}

		@Override
		public Set getGallerySet(PostItem postItem) {
			return this;
		}
	}
}
