package com.mishiranu.dashchan.content;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Process;
import android.os.SystemClock;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import androidx.core.view.ViewCompat;
import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.HttpHolderTask;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.LruCache;
import com.mishiranu.dashchan.widget.AttachmentView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

public class ImageLoader {
	private static final ImageLoader INSTANCE = new ImageLoader();

	public static ImageLoader getInstance() {
		return INSTANCE;
	}

	private ImageLoader() {}

	private final HashMap<String, LoaderTask> loaderTasks = new HashMap<>();
	private final HashMap<String, Long> notFoundMap = new HashMap<>();

	private final HashMap<String, ThreadPoolExecutor> executors = new HashMap<>();

	private ThreadPoolExecutor getExecutor(String chanName) {
		ThreadPoolExecutor executor = executors.get(chanName);
		if (executor == null) {
			executor = ConcurrentUtils.newThreadPool(3, 3, 0, "ImageLoader", chanName,
					Process.THREAD_PRIORITY_DEFAULT);
			executors.put(chanName, executor);
		}
		return executor;
	}

	private interface TaskCallback {
		void onTaskFinished(String key, Bitmap bitmap, boolean error);
	}

	private class LoaderTask extends HttpHolderTask<Void, Void, Bitmap> {
		public final Uri uri;
		public final String chanName;
		public final String key;
		public boolean fromCacheOnly;

		public final HashSet<TaskCallback> callbacks = new HashSet<>();
		private final long created = SystemClock.elapsedRealtime();

		private boolean notFound;
		private boolean finished;

		public LoaderTask(Uri uri, String chanName, String key, boolean fromCacheOnly) {
			this.uri = uri;
			this.chanName = chanName;
			this.key = key;
			this.fromCacheOnly = fromCacheOnly;
		}

		@Override
		protected Bitmap doInBackground(HttpHolder holder, Void... params) {
			Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
			// Debounce image requests, taking into account that
			// a task can be executed much later than created.
			long sleep = 500 + created - SystemClock.elapsedRealtime();
			if (sleep > 0) {
				try {
					Thread.sleep(sleep);
				} catch (InterruptedException e) {
					return null;
				}
			}
			String scheme = uri.getScheme();
			boolean chanScheme = "chan".equals(scheme);
			boolean dataScheme = "data".equals(scheme);
			boolean storeExternal = !chanScheme && !dataScheme;
			Bitmap bitmap = null;
			try {
				bitmap = storeExternal ? CacheManager.getInstance().loadThumbnailExternal(key) : null;
				if (isCancelled()) {
					return null;
				}
				if (bitmap == null && !fromCacheOnly) {
					if (chanScheme) {
						String chanName = uri.getAuthority();
						if (StringUtils.isEmpty(chanName)) {
							chanName = this.chanName;
						}
						Resources resources = ChanConfiguration.get(chanName).getResources();
						if (resources != null) {
							String packageName = ChanManager.getInstance().getExtensionPackageName(chanName);
							List<String> pathSegments = uri.getPathSegments();
							if (pathSegments != null && pathSegments.size() == 3) {
								String entity = pathSegments.get(0);
								if ("res".equals(entity)) {
									String type = pathSegments.get(1);
									String name = pathSegments.get(2);
									int id = resources.getIdentifier(name, type, packageName);
									if (id != 0) {
										ByteArrayOutputStream output = new ByteArrayOutputStream();
										InputStream input = null;
										try {
											input = resources.openRawResource(id);
											IOUtils.copyStream(resources.openRawResource(id), output);
										} finally {
											IOUtils.close(input);
										}
										byte[] bytes = output.toByteArray();
										bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
									}
								}
							}
						}
					} else if (dataScheme) {
						String data = uri.toString();
						int index = data.indexOf("base64,");
						if (index >= 0) {
							data = data.substring(index + 7);
							byte[] bytes = Base64.decode(data, Base64.DEFAULT);
							if (bytes != null) {
								bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
							}
						}
					} else {
						String chanName = ChanManager.getInstance().getChanNameByHost(uri.getAuthority());
						if (chanName == null) {
							chanName = this.chanName;
						}
						int connectTimeout = 10000;
						int readTimeout = 5000;
						if (chanName != null) {
							ChanPerformer performer = ChanPerformer.get(chanName);
							try {
								ChanPerformer.ReadContentResult result = performer.safe()
										.onReadContent(new ChanPerformer.ReadContentData(uri, connectTimeout,
										readTimeout, holder, null, null));
								bitmap = result != null && result.response != null ? result.response.getBitmap() : null;
							} catch (ExtensionException e) {
								e.getErrorItemAndHandle();
								return null;
							}
						} else {
							bitmap = new HttpRequest(uri, holder).setTimeouts(connectTimeout, readTimeout)
									.read().getBitmap();
						}
					}
					if (isCancelled()) {
						return null;
					}
					bitmap = GraphicsUtils.reduceThumbnailSize(MainApplication.getInstance().getResources(), bitmap);
					if (storeExternal) {
						CacheManager.getInstance().storeThumbnailExternal(key, bitmap);
					}
				}
			} catch (HttpException e) {
				int responseCode = e.getResponseCode();
				if (responseCode == HttpURLConnection.HTTP_NOT_FOUND ||
						responseCode == HttpURLConnection.HTTP_GONE) {
					notFound = true;
				}
			} catch (Exception | OutOfMemoryError e) {
				Log.persistent().stack(e);
			}
			return bitmap;
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			// Don't remove task but instead mark it as finished,
			// so targets could be extracted later.
			finished = true;
			if (notFound) {
				notFoundMap.put(key, SystemClock.elapsedRealtime());
			}
			if (result != null) {
				bitmapCache.put(key, result);
			}
			for (TaskCallback callback : callbacks) {
				callback.onTaskFinished(key, result, !fromCacheOnly);
			}
		}
	}

	private final LruCache<String, Bitmap> bitmapCache =
			new LruCache<>(MainApplication.getInstance().isLowRam() ? 50 : 200);

	public static abstract class Target {
		public String currentKey;

		private final TaskCallback taskCallback = (key, bitmap, error) -> {
			if (key.equals(currentKey)) {
				onResult(key, bitmap, error, false);
			}
		};

		public void onStart() {}
		public abstract void onResult(String key, Bitmap bitmap, boolean error, boolean instantly);
	}

	private interface WrapperCallback<T> {
		void onResult(T target, String key, Bitmap bitmap, boolean error, boolean instantly);
	}

	private static final WrapperCallback<ImageView> WRAPPER_CALLBACK_IMAGE_VIEW =
			(target, key, bitmap, error, instantly) -> {
		if (bitmap != null) {
			target.setImageBitmap(bitmap);
		} else {
			target.setImageDrawable(null);
		}
	};

	private static final WrapperCallback<AttachmentView> WRAPPER_CALLBACK_ATTACHMENT_VIEW =
			AttachmentView::handleLoadedImage;

	private static class WrapperTarget<T> extends Target {
		public final T target;
		public final WrapperCallback<T> callback;

		private WrapperTarget(T target, WrapperCallback<T> callback) {
			this.target = target;
			this.callback = callback;
		}

		@Override
		public void onResult(String key, Bitmap bitmap, boolean error, boolean instantly) {
			callback.onResult(target, key, bitmap, error, instantly);
		}
	}

	private interface DetachCallback {
		void onDetach(View view);
	}

	private static class ViewTarget<T extends View> extends WrapperTarget<T>
			implements Runnable, View.OnAttachStateChangeListener {
		private final DetachCallback detachCallback;

		private ViewTarget(T target, WrapperCallback<T> wrapperCallback, DetachCallback detachCallback) {
			super(target, wrapperCallback);

			this.detachCallback = detachCallback;
			target.addOnAttachStateChangeListener(this);
		}

		@Override
		public void onStart() {
			if (!ViewCompat.isAttachedToWindow(target)) {
				onViewDetachedFromWindow(target);
			}
		}

		@Override
		public void run() {
			if (detachCallback != null) {
				detachCallback.onDetach(target);
			}
		}

		@Override
		public void onViewAttachedToWindow(View v) {
			ConcurrentUtils.HANDLER.removeCallbacks(this);
		}

		@Override
		public void onViewDetachedFromWindow(View v) {
			ConcurrentUtils.HANDLER.removeCallbacks(this);
			ConcurrentUtils.HANDLER.postDelayed(this, 2000L);
		}
	}

	private final DetachCallback detachCallback = this::cancel;

	private <T extends View> WrapperTarget<T> getWrapperTarget(T view, WrapperCallback<T> wrapperCallback) {
		@SuppressWarnings("unchecked")
		WrapperTarget<T> wrapperTarget = (WrapperTarget<T>) view.getTag(R.id.tag_image_loader);
		if (wrapperTarget == null && wrapperCallback != null) {
			ViewTarget<T> viewTarget = new ViewTarget<>(view, wrapperCallback, detachCallback);
			view.setTag(R.id.tag_image_loader, viewTarget);
			return viewTarget;
		}
		return wrapperTarget;
	}

	public boolean hasRunningTask(View view) {
		WrapperTarget<?> wrapperTarget = getWrapperTarget(view, null);
		if (wrapperTarget != null && wrapperTarget.currentKey != null) {
			LoaderTask loaderTask = loaderTasks.get(wrapperTarget.currentKey);
			return loaderTask != null && !loaderTask.finished;
		}
		return false;
	}

	public void cancel(Target target) {
		String key = target.currentKey;
		target.currentKey = null;
		if (key != null) {
			LoaderTask loaderTask = loaderTasks.get(key);
			if (loaderTask != null) {
				loaderTask.callbacks.remove(target.taskCallback);
				if (loaderTask.callbacks.isEmpty()) {
					loaderTask.cancel(true);
					loaderTasks.remove(key);
				}
			}
		}
	}

	public void cancel(View view) {
		WrapperTarget<?> wrapperTarget = getWrapperTarget(view, null);
		if (wrapperTarget != null) {
			cancel(wrapperTarget);
		}
	}

	public void loadImage(String chanName, Uri uri, boolean fromCacheOnly, ImageView target) {
		WrapperTarget<ImageView> wrapperTarget = getWrapperTarget(target, WRAPPER_CALLBACK_IMAGE_VIEW);
		loadImage(chanName, uri, null, fromCacheOnly, wrapperTarget);
	}

	public void loadImage(String chanName, Uri uri, String key, boolean fromCacheOnly, AttachmentView target) {
		WrapperTarget<AttachmentView> wrapperTarget = getWrapperTarget(target, WRAPPER_CALLBACK_ATTACHMENT_VIEW);
		loadImage(chanName, uri, key, fromCacheOnly, wrapperTarget);
	}

	public boolean loadImage(String chanName, Uri uri, String key, boolean fromCacheOnly, Target target) {
		if (key == null) {
			key = CacheManager.getInstance().getCachedFileKey(uri);
		}
		if (key == null) {
			return false;
		}
		boolean mainThread = ConcurrentUtils.isMain();
		if (mainThread) {
			cancel(target);
		}
		Bitmap memoryCachedBitmap;
		if (mainThread) {
			memoryCachedBitmap = bitmapCache.get(key);
		} else {
			String finalKey = key;
			memoryCachedBitmap = ConcurrentUtils.mainGet(() -> bitmapCache.get(finalKey));
		}
		if (memoryCachedBitmap != null) {
			target.onResult(key, memoryCachedBitmap, false, true);
			return true;
		} else if (!mainThread) {
			// Don't enqueue tasks requested from non-main thread
			target.onResult(key, null, false, true);
			return false;
		}
		// Check "not found" images once per 5 minutes
		Long value = notFoundMap.get(key);
		if (value != null && SystemClock.elapsedRealtime() - value < 5 * 60 * 1000) {
			target.onResult(key, null, !fromCacheOnly, true);
			return false;
		}
		target.currentKey = key;
		target.onStart();
		LoaderTask currentLoaderTask = loaderTasks.get(key);
		boolean startTask = currentLoaderTask == null || currentLoaderTask.finished ||
				currentLoaderTask.fromCacheOnly && !fromCacheOnly;
		LoaderTask registerLoaderTask = currentLoaderTask;
		if (startTask) {
			LoaderTask loaderTask = new LoaderTask(uri, chanName, key, fromCacheOnly);
			registerLoaderTask = loaderTask;
			if (currentLoaderTask != null) {
				currentLoaderTask.cancel(true);
				loaderTask.callbacks.addAll(currentLoaderTask.callbacks);
			}
			loaderTasks.put(key, loaderTask);
			loaderTask.executeOnExecutor(getExecutor(chanName));
		}
		registerLoaderTask.callbacks.add(target.taskCallback);
		return false;
	}
}
