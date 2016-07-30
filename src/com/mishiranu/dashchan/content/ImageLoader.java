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
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.util.ArrayList;
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
import android.view.View;
import android.widget.ImageView;

import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.util.StringUtils;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.app.MainApplication;
import com.mishiranu.dashchan.async.CancellableTask;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.widget.AttachmentView;

public class ImageLoader
{
	private static final ImageLoader INSTANCE = new ImageLoader();
	
	public static ImageLoader getInstance()
	{
		return INSTANCE;
	}
	
	private ImageLoader()
	{
		
	}
	
	private final CacheManager mCacheManager = CacheManager.getInstance();
	
	private final HashMap<String, LoaderTask> mLoaderTasks = new HashMap<>();
	private final HashMap<String, Long> mNotFoundMap = new HashMap<>();
	
	private static final HashMap<String, ThreadPoolExecutor> EXECUTORS = new HashMap<>();
	
	static
	{
		EXECUTORS.put(null, ConcurrentUtils.newThreadPool(3, 3, 0, "ImageLoader", "client",
				Process.THREAD_PRIORITY_DEFAULT));
		for (String chanName : ChanManager.getInstance().getAllChanNames())
		{
			EXECUTORS.put(chanName, ConcurrentUtils.newThreadPool(3, 3, 0, "ImageLoader", chanName,
					Process.THREAD_PRIORITY_DEFAULT));
		}
	}
	
	public void clearTasks(String chanName)
	{
		Iterator<HashMap.Entry<String, LoaderTask>> iterator = mLoaderTasks.entrySet().iterator();
		while (iterator.hasNext())
		{
			HashMap.Entry<String, LoaderTask> entry = iterator.next();
			if (StringUtils.equals(chanName, entry.getKey()))
			{
				iterator.remove();
				entry.getValue().cancel();
			}
		}
	}
	
	public static abstract class Callback<V extends View>
	{
		private final WeakReference<V> mView;
		
		private String mKey;
		private final int mViewHashCode;
		
		public Callback(V view)
		{
			mView = new WeakReference<>(view);
			mViewHashCode = mView.hashCode();
		}
		
		Callback<V> setKey(String key)
		{
			mKey = key;
			mView.get().setTag(R.id.thumbnail, key);
			return this;
		}
		
		public final V getView()
		{
			return mView.get();
		}
		
		public final void onResult(Bitmap bitmap)
		{
			if (checkKeys())
			{
				if (bitmap != null) onSuccess(bitmap); else onError();
			}
		}
		
		public void onPrepare()
		{
			
		}
		
		public abstract void onSuccess(Bitmap bitmap);
		public abstract void onError();
		
		public final boolean checkKeys()
		{
			V view = getView();
			return view != null && mKey.equals(view.getTag(R.id.thumbnail));
		}
		
		@Override
		public final boolean equals(Object o)
		{
			if (o == this) return true;
			if (o instanceof Callback)
			{
				Callback<?> co = (Callback<?>) o;
				if (!mKey.equals(co.mKey)) return false;
				View v1 = mView.get();
				View v2 = co.mView.get();
				return v1 == v2;
			}
			return false;
		}
		
		@Override
		public final int hashCode()
		{
			int prime = 31;
			int result = 1;
			result = prime * result + mKey.hashCode();
			result = prime * result + mViewHashCode;
			return result;
		}
	}
	
	private class LoaderTask extends CancellableTask<Void, Void, Bitmap>
	{
		private final HttpHolder mHolder = new HttpHolder();
		
		private final Uri mUri;
		private final String mChanName;
		private final String mKey;

		public final ArrayList<Callback<?>> callbacks;
		public final boolean fromCacheOnly;
		public boolean fromCacheOnlyChecked;
		
		private boolean mNotFound;
		
		public LoaderTask(Uri uri, String chanName, String key, ArrayList<Callback<?>> callbacks, boolean fromCacheOnly)
		{
			mUri = uri;
			mChanName = chanName;
			mKey = key;
			this.callbacks = callbacks;
			this.fromCacheOnly = fromCacheOnly;
		}
		
		@Override
		protected Bitmap doInBackground(Void... params)
		{
			Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
			String scheme = mUri.getScheme();
			boolean chanScheme = "chan".equals(scheme);
			boolean dataScheme = "data".equals(scheme);
			boolean storeExternal = !chanScheme && !dataScheme;
			Bitmap bitmap = null;
			try
			{
				bitmap = storeExternal ? mCacheManager.loadThumbnailExternal(mKey) : null;
				synchronized (this)
				{
					fromCacheOnlyChecked = true;
				}
				if (isCancelled()) return null;
				if (bitmap != null)
				{
					mCacheManager.storeThumbnailMemory(mKey, bitmap);
				}
				else if (!fromCacheOnly)
				{
					if (chanScheme)
					{
						String chanName = mUri.getAuthority();
						if (StringUtils.isEmpty(chanName)) chanName = mChanName;
						Resources resources = ChanConfiguration.get(chanName).getResources();
						if (resources != null)
						{
							String packageName = null;
							for (ChanManager.ExtensionItem chanItem : ChanManager.getInstance().getChanItems())
							{
								if (chanName.equals(chanItem.extensionName))
								{
									packageName = chanItem.packageInfo.packageName;
									break;
								}
							}
							List<String> pathSegments = mUri.getPathSegments();
							if (pathSegments != null && pathSegments.size() == 3)
							{
								String entity = pathSegments.get(0);
								if ("res".equals(entity))
								{
									String type = pathSegments.get(1);
									String name = pathSegments.get(2);
									int id = resources.getIdentifier(name, type, packageName);
									if (id != 0)
									{
										ByteArrayOutputStream output = new ByteArrayOutputStream();
										InputStream input = null;
										try
										{
											input = resources.openRawResource(id);
											IOUtils.copyStream(resources.openRawResource(id), output);
										}
										finally
										{
											IOUtils.close(input);
										}
										byte[] bytes = output.toByteArray();
										bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
									}
								}
							}
						}
					}
					else if (dataScheme)
					{
						String data = mUri.toString();
						int index = data.indexOf("base64,");
						if (index >= 0)
						{
							data = data.substring(index + 7);
							byte[] bytes = Base64.decode(data, Base64.DEFAULT);
							if (bytes != null) bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
						}
					}
					else
					{
						String chanName = ChanManager.getInstance().getChanNameByHost(mUri.getAuthority());
						if (chanName == null) chanName = mChanName;
						int connectTimeout = 10000;
						int readTimeout = 5000;
						if (chanName != null)
						{
							ChanPerformer performer = ChanPerformer.get(chanName);
							try
							{
								ChanPerformer.ReadContentResult result = performer.safe()
										.onReadContent(new ChanPerformer.ReadContentData(mUri, connectTimeout,
										readTimeout, mHolder, null, null));
								bitmap = result != null && result.response != null ? result.response.getBitmap() : null;
							}
							catch (ExtensionException e)
							{
								e.getErrorItemAndHandle();
								return null;
							}
						}
						else
						{
							bitmap = new HttpRequest(mUri, mHolder).setTimeouts(connectTimeout, readTimeout)
									.read().getBitmap();
						}
					}
					if (isCancelled()) return null;
					bitmap = GraphicsUtils.reduceThumbnailSize(MainApplication.getInstance().getResources(), bitmap);
					mCacheManager.storeThumbnailMemory(mKey, bitmap);
					if (storeExternal) mCacheManager.storeThumbnailExternal(mKey, bitmap);
				}
			}
			catch (HttpException e)
			{
				if (e.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) mNotFound = true;
			}
			catch (Exception | OutOfMemoryError e)
			{
				
			}
			finally
			{
				mHolder.disconnect();
			}
			return bitmap;
		}
		
		@Override
		protected void onPostExecute(Bitmap result)
		{
			mLoaderTasks.remove(mKey);
			if (mNotFound) mNotFoundMap.put(mKey, System.currentTimeMillis());
			for (Callback<?> callback : callbacks) callback.onResult(result);
		}
		
		@Override
		public void cancel()
		{
			cancel(true);
			mHolder.interrupt();
		}
	}
	
	private class AttachmentCallback extends Callback<AttachmentView>
	{
		private final int mSuccessAttrId;
		private final int mErrorAttrId;
		
		public AttachmentCallback(AttachmentView view, int successAttrId, int errorAttrId)
		{
			super(view);
			mSuccessAttrId = successAttrId;
			mErrorAttrId = errorAttrId;
		}
		
		@Override
		public void onPrepare()
		{
			AttachmentView view = getView();
			view.setImage(null);
			view.enqueueTransition();
		}
		
		@Override
		public void onSuccess(Bitmap bitmap)
		{
			AttachmentView view = getView();
			view.setAdditionalOverlay(mSuccessAttrId, false);
			view.setImage(bitmap);
		}
		
		@Override
		public void onError()
		{
			if (mErrorAttrId != 0) getView().setAdditionalOverlay(mErrorAttrId, true);
		}
	}
	
	private class SimpleCallback extends Callback<ImageView>
	{
		public SimpleCallback(ImageView view)
		{
			super(view);
		}
		
		@Override
		public void onPrepare()
		{
			getView().setImageDrawable(null);
		}
		
		@Override
		public void onSuccess(Bitmap bitmap)
		{
			getView().setImageBitmap(bitmap);
		}
		
		@Override
		public void onError()
		{
			
		}
	}
	
	public void unbind(View view)
	{
		view.setTag(R.id.thumbnail, null);
	}
	
	private void loadImage(Uri uri, String chanName, String key, ArrayList<Callback<?>> callbacks,
						   Callback<?> newCallback, boolean fromCacheOnly)
	{
		Bitmap bitmap = mCacheManager.loadThumbnailMemory(key);
		if (bitmap != null)
		{
			for (Callback<?> callback : callbacks) callback.onSuccess(bitmap);
			return;
		}
		// Check "not found" images once per 5 minutes
		Long value = mNotFoundMap.get(key);
		if (value != null && System.currentTimeMillis() - value < 5 * 60 * 1000)
		{
			for (Callback<?> callback : callbacks) callback.onError();
			return;
		}
		if (newCallback != null) newCallback.onPrepare();
		LoaderTask loaderTask = new LoaderTask(uri, chanName, key, callbacks, fromCacheOnly);
		mLoaderTasks.put(key, loaderTask);
		loaderTask.executeOnExecutor(EXECUTORS.get(chanName));
	}
	
	public <V extends View> void loadImage(Uri uri, String chanName, String key, Callback<V> callback,
			boolean fromCacheOnly)
	{
		if (key == null) key = mCacheManager.getCachedFileKey(uri);
		callback.setKey(key);
		LoaderTask loaderTask = mLoaderTasks.get(key);
		if (loaderTask != null)
		{
			int index = loaderTask.callbacks.indexOf(callback);
			if (index >= 0)
			{
				if (loaderTask.callbacks.get(index).checkKeys()) return;
				loaderTask.callbacks.remove(index);
			}
			boolean restart;
			synchronized (loaderTask)
			{
				restart = loaderTask.fromCacheOnly && !fromCacheOnly && loaderTask.fromCacheOnlyChecked;
			}
			if (restart)
			{
				mLoaderTasks.remove(loaderTask);
				loaderTask.cancel();
				ArrayList<Callback<?>> callbacks = loaderTask.callbacks;
				callbacks.add(callback);
				loadImage(uri, chanName, key, callbacks, callback, false);
			}
			else
			{
				callback.onPrepare();
				loaderTask.callbacks.add(callback);
			}
		}
		else
		{
			ArrayList<Callback<?>> callbacks = new ArrayList<>(1);
			callbacks.add(callback);
			loadImage(uri, chanName, key, callbacks, callback, fromCacheOnly);
		}
	}
	
	public void loadImage(Uri uri, String chanName, String key, boolean fromCacheOnly, AttachmentView attachmentView,
			int successAttrId, int errorAttrId)
	{
		loadImage(uri, chanName, key, new AttachmentCallback(attachmentView, successAttrId, errorAttrId),
				fromCacheOnly);
	}
	
	public void loadImage(Uri uri, String chanName, String key, ImageView imageView, boolean fromCacheOnly)
	{
		loadImage(uri, chanName, key, new SimpleCallback(imageView), fromCacheOnly);
	}
}