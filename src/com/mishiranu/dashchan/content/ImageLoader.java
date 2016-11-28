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

package com.mishiranu.dashchan.content;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Process;
import android.util.Base64;

import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.util.StringUtils;

import com.mishiranu.dashchan.content.async.HttpHolderTask;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.WeakObservable;

public class ImageLoader {
	private static final ImageLoader INSTANCE = new ImageLoader();

	public static ImageLoader getInstance() {
		return INSTANCE;
	}

	private ImageLoader() {}

	private final CacheManager cacheManager = CacheManager.getInstance();
	private final WeakObservable<Observer> observable = new WeakObservable<>();

	private final HashMap<String, LoaderTask> loaderTasks = new HashMap<>();
	private final HashMap<String, Long> notFoundMap = new HashMap<>();

	private static final HashMap<String, ThreadPoolExecutor> EXECUTORS = new HashMap<>();

	static {
		EXECUTORS.put(null, ConcurrentUtils.newThreadPool(3, 3, 0, "ImageLoader", "client",
				Process.THREAD_PRIORITY_DEFAULT));
		for (String chanName : ChanManager.getInstance().getAllChanNames()) {
			EXECUTORS.put(chanName, ConcurrentUtils.newThreadPool(3, 3, 0, "ImageLoader", chanName,
					Process.THREAD_PRIORITY_DEFAULT));
		}
	}

	public interface Observer {
		public void onImageLoadComplete(String key, Bitmap bitmap);
	}

	public WeakObservable<Observer> observable() {
		return observable;
	}

	public void clearTasks(String chanName) {
		Iterator<HashMap.Entry<String, LoaderTask>> iterator = loaderTasks.entrySet().iterator();
		while (iterator.hasNext()) {
			HashMap.Entry<String, LoaderTask> entry = iterator.next();
			if (StringUtils.equals(chanName, entry.getKey())) {
				iterator.remove();
				entry.getValue().cancel();
			}
		}
	}

	private class LoaderTask extends HttpHolderTask<Void, Void, Bitmap> {
		private final Uri uri;
		private final String chanName;
		private final String key;

		public boolean fromCacheOnly;
		public boolean fromCacheOnlyChecked;

		private boolean notFound;

		public LoaderTask(Uri uri, String chanName, String key, boolean fromCacheOnly) {
			this.uri = uri;
			this.chanName = chanName;
			this.key = key;
			this.fromCacheOnly = fromCacheOnly;
		}

		@Override
		protected Bitmap doInBackground(HttpHolder holder, Void... params) {
			Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
			String scheme = uri.getScheme();
			boolean chanScheme = "chan".equals(scheme);
			boolean dataScheme = "data".equals(scheme);
			boolean storeExternal = !chanScheme && !dataScheme;
			Bitmap bitmap = null;
			try {
				bitmap = storeExternal ? cacheManager.loadThumbnailExternal(key) : null;
				synchronized (this) {
					fromCacheOnlyChecked = true;
				}
				if (isCancelled()) {
					return null;
				}
				if (bitmap != null) {
					cacheManager.storeThumbnailMemory(key, bitmap);
				} else if (!fromCacheOnly) {
					if (chanScheme) {
						String chanName = uri.getAuthority();
						if (StringUtils.isEmpty(chanName)) {
							chanName = this.chanName;
						}
						Resources resources = ChanConfiguration.get(chanName).getResources();
						if (resources != null) {
							String packageName = null;
							for (ChanManager.ExtensionItem chanItem : ChanManager.getInstance().getChanItems()) {
								if (chanName.equals(chanItem.extensionName)) {
									packageName = chanItem.packageInfo.packageName;
									break;
								}
							}
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
					cacheManager.storeThumbnailMemory(key, bitmap);
					if (storeExternal) {
						cacheManager.storeThumbnailExternal(key, bitmap);
					}
				}
			} catch (HttpException e) {
				if (e.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
					notFound = true;
				}
			} catch (Exception | OutOfMemoryError e) {
				// Ignore exception
			}
			return bitmap;
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			loaderTasks.remove(key);
			if (notFound) {
				notFoundMap.put(key, System.currentTimeMillis());
			}
			for (Observer observer : observable) {
				observer.onImageLoadComplete(key, result);
			}
		}
	}

	public interface KeySetter {
		public void setKey(String key);
	}

	public Bitmap loadImage(Uri uri, String chanName, String key, KeySetter keySetter, boolean fromCacheOnly) {
		if (key == null) {
			key = cacheManager.getCachedFileKey(uri);
			if (keySetter != null) {
				keySetter.setKey(key);
			}
		}
		LoaderTask loaderTask = loaderTasks.get(key);
		if (loaderTask != null) {
			boolean restart = loaderTask.fromCacheOnly && !fromCacheOnly;
			if (restart) {
				synchronized (loaderTask) {
					if (!loaderTask.fromCacheOnlyChecked) {
						loaderTask.fromCacheOnly = false;
						restart = false;
					}
				}
			}
			if (restart) {
				loaderTasks.remove(key);
				loaderTask.cancel();
			} else {
				return null;
			}
		}
		Bitmap bitmap = cacheManager.loadThumbnailMemory(key);
		if (bitmap != null) {
			return bitmap;
		}
		// Check "not found" images once per 5 minutes
		Long value = notFoundMap.get(key);
		if (value != null && System.currentTimeMillis() - value < 5 * 60 * 1000) {
			return null;
		}
		loaderTask = new LoaderTask(uri, chanName, key, fromCacheOnly);
		loaderTasks.put(key, loaderTask);
		loaderTask.executeOnExecutor(EXECUTORS.get(chanName));
		return null;
	}
}