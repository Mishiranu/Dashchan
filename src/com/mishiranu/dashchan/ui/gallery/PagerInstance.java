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

package com.mishiranu.dashchan.ui.gallery;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.graphics.DecoderDrawable;
import com.mishiranu.dashchan.graphics.SimpleBitmapDrawable;
import com.mishiranu.dashchan.media.AnimatedPngDecoder;
import com.mishiranu.dashchan.media.GifDecoder;
import com.mishiranu.dashchan.media.JpegData;
import com.mishiranu.dashchan.widget.CircularProgressBar;
import com.mishiranu.dashchan.widget.PhotoView;

public class PagerInstance {
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
		public PhotoView photoView;
		public FrameLayout surfaceParent;
		public CircularProgressBar progressBar;
		public View playButton;

		public View errorView;
		public TextView errorText;

		public SimpleBitmapDrawable simpleBitmapDrawable;
		public DecoderDrawable decoderDrawable;
		public AnimatedPngDecoder animatedPngDecoder;
		public GifDecoder gifDecoder;
		public JpegData jpegData;
		public boolean photoViewThumbnail;

		public boolean fullLoaded;
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
		public void showError(ViewHolder holder, String message);
	}
}