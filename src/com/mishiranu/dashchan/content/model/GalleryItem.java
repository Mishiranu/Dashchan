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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

import android.content.Context;
import android.net.Uri;

import chan.content.ChanLocator;

import com.mishiranu.dashchan.content.DownloadManager;
import com.mishiranu.dashchan.util.NavigationUtils;

public class GalleryItem implements Serializable {
	private static final long serialVersionUID = 1L;

	private final String mFileUriString;
	private final String mThumbnailUriString;

	public final String boardName;
	public final String threadNumber;
	public final String postNumber;

	public final String originalName;

	public final int width;
	public final int height;

	public int size;

	private transient Uri mFileUri;
	private transient Uri mThumbnailUri;

	public GalleryItem(Uri fileUri, Uri thumbnailUri, String boardName, String threadNumber, String postNumber,
			String originalName, int width, int height, int size) {
		mFileUriString = fileUri != null ? fileUri.toString() : null;
		mThumbnailUriString = thumbnailUri != null ? thumbnailUri.toString() : null;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.postNumber = postNumber;
		this.originalName = originalName;
		this.width = width;
		this.height = height;
		this.size = size;
	}

	public GalleryItem(Uri fileUri, String boardName, String threadNumber) {
		mFileUriString = null;
		mThumbnailUriString = null;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		postNumber = null;
		originalName = null;
		width = 0;
		height = 0;
		size = 0;
		mFileUri = fileUri;
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
		if (mFileUri == null && mFileUriString != null) {
			mFileUri = locator.convert(Uri.parse(mFileUriString));
		}
		return mFileUri;
	}

	public Uri getThumbnailUri(ChanLocator locator) {
		if (mThumbnailUri == null && mThumbnailUriString != null) {
			mThumbnailUri = locator.convert(Uri.parse(mThumbnailUriString));
		}
		return mThumbnailUri;
	}

	public Uri getDisplayImageUri(ChanLocator locator) {
		return isImage(locator) ? getFileUri(locator) : getThumbnailUri(locator);
	}

	public String getFileName(ChanLocator locator) {
		Uri fileUri = getFileUri(locator);
		return locator.createAttachmentFileName(fileUri);
	}

	public void downloadStorage(Context context, ChanLocator locator, String threadTitle) {
		DownloadManager.getInstance().downloadStorage(context, getFileUri(locator), getFileName(locator), originalName,
				locator.getChanName(), boardName, threadNumber, threadTitle);
	}

	public void cleanup() {
		if (mFileUriString != null) {
			mFileUri = null;
		}
		if (mThumbnailUriString != null) {
			mThumbnailUri = null;
		}
	}

	public static class GallerySet {
		private final boolean mAllowGoToPost;
		private final ArrayList<GalleryItem> mGalleryItems = new ArrayList<>();

		private String mThreadTitle;

		public GallerySet(boolean allowGoToPost) {
			mAllowGoToPost = allowGoToPost;
		}

		public void setThreadTitle(String threadTitle) {
			mThreadTitle = threadTitle;
		}

		public String getThreadTitle() {
			return mThreadTitle;
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
				mGalleryItems.add(galleryItem);
			}
		}

		public void cleanup() {
			for (GalleryItem galleryItem : mGalleryItems) {
				galleryItem.cleanup();
			}
		}

		public void clear() {
			mGalleryItems.clear();
		}

		public ArrayList<GalleryItem> getItems() {
			return mGalleryItems.size() > 0 ? mGalleryItems : null;
		}

		public boolean isAllowGoToPost() {
			return mAllowGoToPost;
		}
	}
}