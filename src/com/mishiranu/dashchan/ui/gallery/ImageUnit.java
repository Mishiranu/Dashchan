package com.mishiranu.dashchan.ui.gallery;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Pair;
import androidx.fragment.app.FragmentManager;
import chan.content.Chan;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.NetworkObserver;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ExecutorTask;
import com.mishiranu.dashchan.content.async.ReadFileTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.graphics.DecoderDrawable;
import com.mishiranu.dashchan.graphics.SimpleBitmapDrawable;
import com.mishiranu.dashchan.media.AnimatedPngDecoder;
import com.mishiranu.dashchan.media.ExifData;
import com.mishiranu.dashchan.media.GifDecoder;
import com.mishiranu.dashchan.media.JpegData;
import com.mishiranu.dashchan.ui.InstanceDialog;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.widget.PhotoView;
import com.mishiranu.dashchan.widget.SummaryLayout;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;

public class ImageUnit {
	private final PagerInstance instance;

	private ReadFileTask readFileTask;
	private ReadBitmapCallback readBitmapCallback;

	public ImageUnit(PagerInstance instance) {
		this.instance = instance;
	}

	public void interrupt(boolean force) {
		if (force && readFileTask != null) {
			readFileTask.cancel();
			readFileTask = null;
			readBitmapCallback = null;
		}
		interruptHolder(instance.leftHolder);
		interruptHolder(instance.currentHolder);
		interruptHolder(instance.rightHolder);
	}

	private void interruptHolder(PagerInstance.ViewHolder holder) {
		if (holder != null) {
			if (holder.decodeBitmapTask != null) {
				((DecodeBitmapTask) holder.decodeBitmapTask).cancel();
				holder.progressBar.setVisible(false, true);
				holder.decodeBitmapTask = null;
			}
		}
	}

	public void applyImage(Uri uri, File file, boolean reload) {
		if (!reload && file.exists()) {
			applyImageFromFile(file);
		} else {
			loadImage(uri, file, instance.currentHolder);
		}
	}

	private static final Executor EXECUTOR = ConcurrentUtils.newSingleThreadPool(20000, "DecodeBitmapTask", null);

	private void applyImageFromFile(File file) {
		PagerInstance.ViewHolder holder = instance.currentHolder;
		if (attachReadBitmapCallback(holder)) {
			return;
		}
		if (holder.mediaSummary.updateSize(file.length())) {
			instance.galleryInstance.callback.updateTitle();
		}
		FileHolder fileHolder = FileHolder.obtain(file);
		if (holder.decodeBitmapTask != null) {
			((DecodeBitmapTask) holder.decodeBitmapTask).cancel();
			holder.progressBar.setVisible(false, true);
		}
		DecodeBitmapTask decodeBitmapTask = new DecodeBitmapTask(file, fileHolder);
		decodeBitmapTask.execute(EXECUTOR);
		holder.decodeBitmapTask = decodeBitmapTask;
		PagerInstance.ViewHolder nextHolder = instance.scrollingLeft ? instance.leftHolder : instance.rightHolder;
		if (nextHolder != null && Preferences.getLoadNearestImage()
				.isNetworkAvailable(NetworkObserver.getInstance())) {
			GalleryItem nextGalleryItem = nextHolder.galleryItem;
			Chan chan = Chan.get(instance.galleryInstance.chanName);
			if (nextGalleryItem.isImage(chan)) {
				Uri nextUri = nextGalleryItem.getFileUri(chan);
				File nextCachedFile = CacheManager.getInstance().getMediaFile(nextUri, true);
				if (nextCachedFile != null && !nextCachedFile.exists()) {
					loadImage(nextUri, nextCachedFile, nextHolder);
				}
			}
		}
	}

	private void loadImage(Uri uri, File cachedFile, PagerInstance.ViewHolder holder) {
		if (attachReadBitmapCallback(holder)) {
			return;
		}
		if (readFileTask != null) {
			readFileTask.cancel();
		}
		readBitmapCallback = new ReadBitmapCallback(holder.galleryItem);
		Chan chan = Chan.getPreferred(instance.galleryInstance.chanName, uri);
		readFileTask = ReadFileTask.createCachedMediaFile(readBitmapCallback, chan, uri, cachedFile);
		readFileTask.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
	}

	private boolean attachReadBitmapCallback(PagerInstance.ViewHolder holder) {
		if (readBitmapCallback != null && readBitmapCallback.isHolder(holder)) {
			readBitmapCallback.attachDownloading();
			return true;
		}
		return false;
	}

	private class ReadBitmapCallback implements ReadFileTask.FileCallback {
		private final GalleryItem galleryItem;

		public ReadBitmapCallback(GalleryItem galleryItem) {
			this.galleryItem = galleryItem;
		}

		private boolean isCurrentHolder() {
			return isHolder(instance.currentHolder);
		}

		public boolean isHolder(PagerInstance.ViewHolder holder) {
			return holder != null && holder.galleryItem == galleryItem;
		}

		@Override
		public void onStartDownloading() {
			if (isCurrentHolder()) {
				instance.currentHolder.progressBar.setVisible(true, false);
				instance.currentHolder.progressBar.setIndeterminate(true);
			}
		}

		private int pendingProgress;
		private int pendingProgressMax;

		public void attachDownloading() {
			if (isCurrentHolder()) {
				instance.currentHolder.progressBar.setVisible(true, false);
				instance.currentHolder.progressBar.setIndeterminate(pendingProgressMax <= 0);
				if (pendingProgressMax > 0) {
					instance.currentHolder.progressBar.setProgress(pendingProgress, pendingProgressMax, true);
				}
			}
		}

		@Override
		public void onFinishDownloading(boolean success, Uri uri, File file, ErrorItem errorItem) {
			readFileTask = null;
			readBitmapCallback = null;
			if (isCurrentHolder()) {
				instance.currentHolder.progressBar.setVisible(false, false);
				if (success) {
					applyImageFromFile(file);
				} else {
					instance.callback.showError(instance.currentHolder, errorItem.toString());
				}
			}
		}

		@Override
		public void onCancelDownloading() {
			if (isCurrentHolder()) {
				instance.currentHolder.progressBar.setVisible(false, true);
			}
		}

		@Override
		public void onUpdateProgress(long progress, long progressMax) {
			if (isCurrentHolder()) {
				instance.currentHolder.progressBar.setIndeterminate(false);
				instance.currentHolder.progressBar.setProgress((int) progress, (int) progressMax, progress == 0);
			} else {
				pendingProgress = (int) progress;
				pendingProgressMax = (int) progressMax;
			}
		}
	}

	public boolean hasMetadata() {
		JpegData jpegData = instance.currentHolder.jpegData;
		return jpegData != null && jpegData.exifData != null && !jpegData.exifData.getUserMetadata().isEmpty();
	}

	public void viewMetadata() {
		String fileName = instance.currentHolder.galleryItem
				.getFileName(Chan.get(instance.galleryInstance.chanName));
		showMetadata(instance.galleryInstance.callback.getChildFragmentManager(),
				instance.currentHolder.jpegData, fileName);
	}

	private static void showMetadata(FragmentManager fragmentManager, JpegData jpegData, String fileName) {
		new InstanceDialog(fragmentManager, null, provider -> {
			Context context = GalleryInstance.getCallback(provider).getWindow().getContext();
			AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context)
					.setTitle(R.string.metadata)
					.setPositiveButton(android.R.string.ok, null);
			ExifData exifData = jpegData != null ? jpegData.exifData : null;
			String geolocation = exifData != null ? exifData.getGeolocation(false) : null;
			if (geolocation != null) {
				Uri uri = new Uri.Builder().scheme("geo").appendQueryParameter("q",
						geolocation + "(" + fileName + ")").build();
				Intent intent = new Intent(Intent.ACTION_VIEW).setData(uri);
				if (!context.getPackageManager().queryIntentActivities(intent,
						PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) {
					dialogBuilder.setNeutralButton(R.string.show_on_map, (d, w) -> context.startActivity(intent));
				}
			}
			AlertDialog dialog = dialogBuilder.create();
			SummaryLayout layout = new SummaryLayout(dialog);
			if (exifData != null) {
				for (Pair<String, String> pair : exifData.getUserMetadata()) {
					if (pair != null) {
						layout.add(pair.first, pair.second);
					} else {
						layout.addDivider();
					}
				}
				layout.addDivider();
			}
			return dialog;
		});
	}

	private class DecodeBitmapTask extends ExecutorTask<Void, Void> {
		private final File file;
		private final FileHolder fileHolder;
		private final PhotoView photoView;

		private Bitmap bitmap;
		private DecoderDrawable decoderDrawable;
		private AnimatedPngDecoder animatedPngDecoder;
		private GifDecoder gifDecoder;
		private int errorMessageId;

		public DecodeBitmapTask(File file, FileHolder fileHolder) {
			this.file = file;
			this.fileHolder = fileHolder;
			photoView = instance.currentHolder.photoView;
			if (fileHolder.getImageWidth() >= 2048 && fileHolder.getImageHeight() >= 2048
					|| fileHolder.getImageType() == FileHolder.ImageType.IMAGE_SVG) {
				instance.currentHolder.progressBar.setVisible(true, false);
				instance.currentHolder.progressBar.setIndeterminate(true);
			}
		}

		@Override
		protected Void run() {
			if (!fileHolder.isImage()) {
				errorMessageId = R.string.image_is_corrupted;
				return null;
			}
			if (fileHolder.getImageType() == FileHolder.ImageType.IMAGE_PNG) {
				try {
					animatedPngDecoder = new AnimatedPngDecoder(fileHolder);
					return null;
				} catch (IOException e) {
					// Ignore exception
				}
			} else if (fileHolder.getImageType() == FileHolder.ImageType.IMAGE_GIF) {
				try {
					gifDecoder = new GifDecoder(file);
					return null;
				} catch (IOException e) {
					// Ignore exception
				}
			}
			try {
				int maxSize = photoView.getMaximumImageSizeAsync();
				bitmap = fileHolder.readImageBitmap(maxSize, true, true);
				if (bitmap == null) {
					errorMessageId = R.string.image_is_corrupted;
				} else {
					if (bitmap.getWidth() < fileHolder.getImageWidth() ||
							bitmap.getHeight() < fileHolder.getImageHeight()) {
						try {
							decoderDrawable = new DecoderDrawable(bitmap, fileHolder);
							bitmap = null;
						} catch (OutOfMemoryError | IOException e) {
							// Ignore exception
						}
					}
				}
			} catch (OutOfMemoryError e) {
				errorMessageId = R.string.no_enough_memory_to_handle_image;
			} catch (InterruptedException e) {
				errorMessageId = R.string.unknown_error;
			} catch (Exception e) {
				e.printStackTrace();
				errorMessageId = R.string.image_is_corrupted;
			}
			return null;
		}

		@Override
		protected void onComplete(Void result) {
			PagerInstance.ViewHolder holder = instance.currentHolder;
			holder.decodeBitmapTask = null;
			holder.progressBar.setVisible(false, false);
			if (bitmap != null || decoderDrawable != null || animatedPngDecoder != null || gifDecoder != null) {
				int width;
				int height;
				if (animatedPngDecoder != null) {
					holder.animatedPngDecoder = animatedPngDecoder;
					Drawable drawable = animatedPngDecoder.getDrawable();
					width = drawable.getIntrinsicWidth();
					height = drawable.getIntrinsicHeight();
					setPhotoViewImage(holder, drawable, true);
				} else if (gifDecoder != null) {
					holder.gifDecoder = gifDecoder;
					Drawable drawable = gifDecoder.getDrawable();
					width = drawable.getIntrinsicWidth();
					height = drawable.getIntrinsicHeight();
					setPhotoViewImage(holder, drawable, true);
				} else if (decoderDrawable != null) {
					holder.decoderDrawable = decoderDrawable;
					width = decoderDrawable.getIntrinsicWidth();
					height = decoderDrawable.getIntrinsicHeight();
					setPhotoViewImage(holder, decoderDrawable, decoderDrawable.hasAlpha());
				} else {
					holder.simpleBitmapDrawable = new SimpleBitmapDrawable(bitmap, true);
					width = bitmap.getWidth();
					height = bitmap.getHeight();
					setPhotoViewImage(holder, holder.simpleBitmapDrawable, bitmap.hasAlpha());
				}
				if (holder.mediaSummary.updateDimensions(width, height)) {
					instance.galleryInstance.callback.updateTitle();
				}
				holder.loadState = PagerInstance.LoadState.COMPLETE;
				instance.galleryInstance.callback.invalidateOptionsMenu();
			} else {
				instance.callback.showError(holder, instance.galleryInstance.context.getString(errorMessageId));
			}
		}

		private void setPhotoViewImage(PagerInstance.ViewHolder holder, Drawable drawable, boolean hasAlpha) {
			holder.photoView.setImage(drawable, hasAlpha, false, holder.photoViewThumbnail);
			holder.jpegData = fileHolder.getJpegData();
			holder.photoViewThumbnail = false;
		}
	}
}
