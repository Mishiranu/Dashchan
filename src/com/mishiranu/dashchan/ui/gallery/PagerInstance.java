package com.mishiranu.dashchan.ui.gallery;

import android.view.View;
import android.widget.FrameLayout;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.graphics.DecoderDrawable;
import com.mishiranu.dashchan.graphics.SimpleBitmapDrawable;
import com.mishiranu.dashchan.media.AnimatedPngDecoder;
import com.mishiranu.dashchan.media.GifDecoder;
import com.mishiranu.dashchan.media.JpegData;
import com.mishiranu.dashchan.widget.CircularProgressBar;
import com.mishiranu.dashchan.widget.PhotoView;
import com.mishiranu.dashchan.widget.ViewFactory;

public class PagerInstance {
	public enum LoadState {PREVIEW_OR_LOADING, COMPLETE, ERROR}

	public static class MediaSummary {
		public int width;
		public int height;
		public long size;

		public MediaSummary(int width, int height, long size) {
			this.width = width;
			this.height = height;
			this.size = size;
		}

		public MediaSummary(GalleryItem galleryItem) {
			this(galleryItem.width, galleryItem.height, galleryItem.size);
		}

		public boolean updateDimensions(int width, int height) {
			if (width > 0 && height > 0 && (this.width != width || this.height != height)) {
				this.width = width;
				this.height = height;
				return true;
			}
			return false;
		}

		public boolean updateSize(long size) {
			if (size > 0 && this.size != size) {
				this.size = size;
				return true;
			}
			return false;
		}
	}

	public final GalleryInstance galleryInstance;
	public final Callback callback;

	public boolean scrollingLeft;

	public ViewHolder leftHolder;
	public ViewHolder currentHolder;
	public ViewHolder rightHolder;

	public PagerInstance(GalleryInstance galleryInstance, Callback callback) {
		this.galleryInstance = galleryInstance;
		this.callback = callback;
	}

	public static class ViewHolder {
		public GalleryItem galleryItem;
		public MediaSummary mediaSummary;
		public PhotoView photoView;
		public FrameLayout surfaceParent;
		public CircularProgressBar progressBar;
		public View playButton;
		public ViewFactory.ErrorHolder errorHolder;

		public SimpleBitmapDrawable simpleBitmapDrawable;
		public DecoderDrawable decoderDrawable;
		public AnimatedPngDecoder animatedPngDecoder;
		public GifDecoder gifDecoder;
		public JpegData jpegData;
		public boolean photoViewThumbnail;
		public ImageLoader.Target thumbnailTarget;

		public LoadState loadState = LoadState.PREVIEW_OR_LOADING;
		public Object decodeBitmapTask;

		public void recyclePhotoView() {
			photoView.recycle();
			if (simpleBitmapDrawable != null) {
				simpleBitmapDrawable.recycle();
				simpleBitmapDrawable = null;
			}
			if (decoderDrawable != null) {
				decoderDrawable.recycle();
				decoderDrawable = null;
			}
			if (animatedPngDecoder != null) {
				animatedPngDecoder.recycle();
				animatedPngDecoder = null;
			}
			if (gifDecoder != null) {
				gifDecoder.recycle();
				gifDecoder = null;
			}
			jpegData = null;
			photoViewThumbnail = false;
		}
	}

	public interface Callback {
		void showError(ViewHolder holder, String message);
	}
}
