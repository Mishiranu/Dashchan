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

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Pair;
import android.webkit.MimeTypeMap;

import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.content.model.Posts;
import chan.util.StringUtils;

import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.LruCache;

public class CacheManager implements Runnable
{
	private static final int MAX_THUMBNAILS_PART = 30;
	private static final int MAX_MEDIA_PART = 60;
	private static final int MAX_PAGES_PART = 30;

	private static final long OLD_THREADS_THRESHOLD = 7 * 24 * 60 * 60 * 1000; // One week

	private static final float TRIM_FACTOR = 0.3f;

	private static final String TEMP_PAGE_FILE_PREFIX = "temp_";

	private static final CacheManager INSTANCE = new CacheManager();

	public static CacheManager getInstance()
	{
		return INSTANCE;
	}

	private CacheManager()
	{
		handleGalleryShareFiles();
		syncCache();
		BroadcastReceiver mediaMountedReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				syncCache();
			}
		};
		IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
		intentFilter.addDataScheme("file");
		MainApplication.getInstance().registerReceiver(mediaMountedReceiver, intentFilter);
		new Thread(this, "CacheManagerWorker").start();
	}

	private final LinkedBlockingQueue<CacheItem> mCacheItemsToDelete = new LinkedBlockingQueue<>();

	@Override
	public void run()
	{
		while (true)
		{
			CacheItem cacheItem;
			try
			{
				cacheItem = mCacheItemsToDelete.take();
			}
			catch (InterruptedException e)
			{
				return;
			}
			File file = null;
			switch (cacheItem.type)
			{
				case CacheItem.TYPE_THUMBNAILS:
				{
					file = new File(getThumbnailsDirectory(), cacheItem.name);
					break;
				}
				case CacheItem.TYPE_MEDIA:
				{
					file = new File(getMediaDirectory(), cacheItem.name);
					break;
				}
				case CacheItem.TYPE_PAGES:
				{
					file = new File(getPagesDirectory(), cacheItem.name);
					break;
				}
			}
			file.delete();
		}
	}

	private volatile CountDownLatch mCacheBuildingLatch;

	private class CacheItem
	{
		public static final int TYPE_THUMBNAILS = 0;
		public static final int TYPE_MEDIA = 1;
		public static final int TYPE_PAGES = 2;

		public final String name;
		public final String nameLc;
		public final long length;
		public long lastModified;
		public final int type;

		public CacheItem(File file, int type)
		{
			name = file.getName();
			nameLc = name.toLowerCase(Locale.US);
			length = file.length();
			lastModified = file.lastModified();
			this.type = type;
		}

		@Override
		public String toString()
		{
			return "CacheItem [\"" + name + "\", " + length + "]";
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == this) return true;
			if (o instanceof CacheItem)
			{
				CacheItem co = (CacheItem) o;
				return type == co.type && nameLc.equals(co.nameLc);
			}
			return false;
		}

		@Override
		public int hashCode()
		{
			int prime = 31;
			int result = 1;
			result = prime * result + type;
			result = prime * result + nameLc.hashCode();
			return result;
		}
	}

	private static final Comparator<CacheItem> SORT_BY_DATE_COMPARATOR = (lhs, rhs) ->
	{
		return ((Long) lhs.lastModified).compareTo(rhs.lastModified);
	};

	private final LinkedHashMap<String, CacheItem> mThumbnailsCache = new LinkedHashMap<>();
	private final LinkedHashMap<String, CacheItem> mMediaCache = new LinkedHashMap<>();
	private final LinkedHashMap<String, CacheItem> mPagesCache = new LinkedHashMap<>();

	private long mThumbnailsCacheSize;
	private long mMediaCacheSize;
	private long mPagesCacheSize;

	private long fillCache(LinkedHashMap<String, CacheItem> cacheItems, File directory, int type)
	{
		cacheItems.clear();
		if (directory == null) return 0L;
		ArrayList<CacheItem> cacheItemsList = new ArrayList<>();
		File[] files = directory.listFiles();
		if (files != null)
		{
			for (File file : files)
			{
				CacheItem cacheItem = new CacheItem(file, type);
				if (type == CacheItem.TYPE_PAGES)
				{
					if (cacheItem.name.startsWith(TEMP_PAGE_FILE_PREFIX))
					{
						synchronized (mTempSerializeFileLock)
						{
							file.delete();
						}
						continue;
					}
				}
				cacheItemsList.add(cacheItem);
			}
		}
		Collections.sort(cacheItemsList, SORT_BY_DATE_COMPARATOR);
		long size = 0L;
		for (CacheItem cacheItem : cacheItemsList)
		{
			cacheItems.put(cacheItem.nameLc, cacheItem);
			size += cacheItem.length;
		}
		return size;
	}

	private void syncCache()
	{
		final CountDownLatch latch = new CountDownLatch(1);
		mCacheBuildingLatch = latch;
		new Thread(() ->
		{
			try
			{
				synchronized (mThumbnailsCache)
				{
					mThumbnailsCacheSize = fillCache(mThumbnailsCache, getThumbnailsDirectory(),
							CacheItem.TYPE_THUMBNAILS);
				}
				synchronized (mMediaCache)
				{
					mMediaCacheSize = fillCache(mMediaCache, getMediaDirectory(), CacheItem.TYPE_MEDIA);
				}
				synchronized (mPagesCache)
				{
					mPagesCacheSize = fillCache(mPagesCache, getPagesDirectory(), CacheItem.TYPE_PAGES);
				}
				cleanupAsync(true, true, true);
			}
			finally
			{
				latch.countDown();
			}

		}).start();
	}

	private void cleanupAsync(boolean thumbnails, boolean media, boolean pages)
	{
		int maxCache = MAX_THUMBNAILS_PART + MAX_MEDIA_PART + MAX_PAGES_PART;
		int maxCacheSizeMb = Preferences.getCacheSize();
		ArrayList<CacheItem> cleanupCacheItems = null;
		if (thumbnails)
		{
			synchronized (mThumbnailsCache)
			{
				long maxSize = MAX_THUMBNAILS_PART * maxCacheSizeMb * 1024L * 1024L / maxCache;
				if (mThumbnailsCacheSize > maxSize)
				{
					if (cleanupCacheItems == null) cleanupCacheItems = new ArrayList<>();
					mThumbnailsCacheSize = obtainCacheItemsToCleanup(cleanupCacheItems, mThumbnailsCache,
							mThumbnailsCacheSize, maxSize, null);
				}
			}
		}
		if (media)
		{
			synchronized (mMediaCache)
			{
				long maxSize = MAX_MEDIA_PART * maxCacheSizeMb * 1024L * 1024L / maxCache;
				if (mMediaCacheSize > maxSize)
				{
					if (cleanupCacheItems == null) cleanupCacheItems = new ArrayList<>();
					mMediaCacheSize = obtainCacheItemsToCleanup(cleanupCacheItems, mMediaCache,
							mMediaCacheSize, maxSize, null);
				}
			}
		}
		if (pages)
		{
			synchronized (mPagesCache)
			{
				long maxSize = MAX_PAGES_PART * maxCacheSizeMb * 1024L * 1024L / maxCache;
				if (mPagesCacheSize > maxSize)
				{
					if (cleanupCacheItems == null) cleanupCacheItems = new ArrayList<>();
					mPagesCacheSize = obtainCacheItemsToCleanup(cleanupCacheItems, mPagesCache,
							mPagesCacheSize, maxSize, new PagesCacheDeleteCondition());
				}
			}
		}
		if (cleanupCacheItems != null && cleanupCacheItems.size() > 0)
		{
			// Start handling
			mCacheItemsToDelete.addAll(cleanupCacheItems);
		}
	}

	private long obtainCacheItemsToCleanup(ArrayList<CacheItem> cleanupCacheItems,
			LinkedHashMap<String, CacheItem> cacheItems, long size, long maxSize, DeleteCondition deleteCondition)
	{
		long trimAmount = (long) (TRIM_FACTOR * maxSize);
		long deleteAmount = size - maxSize + trimAmount;
		Iterator<CacheItem> iterator = cacheItems.values().iterator();
		while (iterator.hasNext() && deleteAmount > 0)
		{
			CacheItem cacheItem = iterator.next();
			if (deleteCondition == null || deleteCondition.allowDeleteCacheItem(cacheItem))
			{
				deleteAmount -= cacheItem.length;
				size -= cacheItem.length;
				iterator.remove();
				cleanupCacheItems.add(cacheItem);
			}
		}
		return size;
	}

	private interface DeleteCondition
	{
		public boolean allowDeleteCacheItem(CacheItem cacheItem);
	}

	private class PagesCacheDeleteCondition implements DeleteCondition
	{
		private final long mOlderThan = System.currentTimeMillis() - OLD_THREADS_THRESHOLD;
		private final ArrayList<String> mFavoriteFiles = new ArrayList<>();

		public PagesCacheDeleteCondition()
		{
			Collection<String> chanNames = ChanManager.getInstance().getAllChanNames();
			for (FavoritesStorage.FavoriteItem favoriteItem : FavoritesStorage.getInstance().getThreads(null))
			{
				if (chanNames.contains(favoriteItem.chanName))
				{
					mFavoriteFiles.add(getPostsFileName(favoriteItem.chanName, favoriteItem.boardName,
							favoriteItem.threadNumber).toLowerCase(Locale.US));
				}
			}
		}

		@Override
		public boolean allowDeleteCacheItem(CacheItem cacheItem)
		{
			return cacheItem.lastModified < mOlderThan && !mFavoriteFiles.contains(cacheItem.nameLc);
		}
	}

	private boolean waitCacheSync()
	{
		CountDownLatch latch = mCacheBuildingLatch;
		if (latch != null)
		{
			try
			{
				latch.await();
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return true;
			}
		}
		return false;
	}

	private LinkedHashMap<String, CacheItem> getCacheItems(int type)
	{
		switch (type)
		{
			case CacheItem.TYPE_THUMBNAILS: return mThumbnailsCache;
			case CacheItem.TYPE_MEDIA: return mMediaCache;
			case CacheItem.TYPE_PAGES: return mPagesCache;
		}
		throw new RuntimeException("Unknown cache type");
	}

	private void modifyCacheSize(int type, long lengthDelta)
	{
		switch (type)
		{
			case CacheItem.TYPE_THUMBNAILS: mThumbnailsCacheSize += lengthDelta; break;
			case CacheItem.TYPE_MEDIA: mMediaCacheSize += lengthDelta; break;
			case CacheItem.TYPE_PAGES: mPagesCacheSize += lengthDelta; break;
		}
	}

	private boolean isFileExistsInCache(File file, String fileName, int type)
	{
		if (waitCacheSync()) return false;
		LinkedHashMap<String, CacheItem> cacheItems = getCacheItems(type);
		synchronized (cacheItems)
		{
			CacheItem cacheItem = cacheItems.get(fileName.toLowerCase(Locale.US));
			if (cacheItem != null && !file.exists())
			{
				cacheItems.remove(cacheItem.nameLc);
				modifyCacheSize(type, -cacheItem.length);
				cacheItem = null;
			}
			return cacheItem != null;
		}
	}

	private void updateCachedFileLastModified(File file, String fileName, int type)
	{
		if (waitCacheSync()) return;
		LinkedHashMap<String, CacheItem> cacheItems = getCacheItems(type);
		synchronized (cacheItems)
		{
			String fileNameLc = fileName.toLowerCase(Locale.US);
			CacheItem cacheItem = cacheItems.remove(fileNameLc);
			if (cacheItem != null)
			{
				if (file.exists())
				{
					long lastModified = System.currentTimeMillis();
					file.setLastModified(lastModified);
					cacheItem.lastModified = lastModified;
					cacheItems.put(fileNameLc, cacheItem);
				}
				else modifyCacheSize(type, -cacheItem.length);
			}
		}
	}

	private void validateNewCachedFile(File file, String fileName, int type, boolean success)
	{
		if (waitCacheSync()) return;
		LinkedHashMap<String, CacheItem> cacheItems = getCacheItems(type);
		synchronized (cacheItems)
		{
			long lengthDelta = 0L;
			CacheItem cacheItem = cacheItems.remove(fileName.toLowerCase(Locale.US));
			if (cacheItem != null) lengthDelta = -cacheItem.length;
			if (success)
			{
				cacheItem = new CacheItem(file, type);
				cacheItems.put(cacheItem.nameLc, cacheItem);
				lengthDelta += cacheItem.length;
			}
			modifyCacheSize(type, lengthDelta);
			if (success)
			{
				cleanupAsync(type == CacheItem.TYPE_THUMBNAILS, type == CacheItem.TYPE_MEDIA,
						type == CacheItem.TYPE_PAGES);
			}
		}
	}

	public boolean isCacheAvailable()
	{
		return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
	}

	public long getCacheSize()
	{
		if (waitCacheSync()) return 0L;
		return mThumbnailsCacheSize + mMediaCacheSize + mPagesCacheSize;
	}

	private File getThumbnailsDirectory()
	{
		return getCacheDirectory("thumbnails");
	}

	private File getMediaDirectory()
	{
		return getCacheDirectory("media");
	}

	private File getPagesDirectory()
	{
		return getCacheDirectory("pages");
	}

	private File getCacheDirectory(String subFolder)
	{
		File directory = getExternalCacheDirectory();
		if (directory == null) return null;
		File file = new File(directory, subFolder);
		if (isCacheAvailable() && !file.exists()) file.mkdirs();
		return file;
	}

	private File getMediaFile(String fileName, boolean touch)
	{
		File directory = getMediaDirectory();
		if (directory == null) return null;
		File file = new File(directory, fileName);
		if (touch) updateCachedFileLastModified(file, fileName, CacheItem.TYPE_MEDIA);
		return file;
	}

	public File getMediaFile(Uri uri, boolean touch)
	{
		return getMediaFile(getCachedFileKey(uri), touch);
	}

	private long eraseCache(LinkedHashMap<String, CacheItem> cacheItems, File directory,
			DeleteCondition deleteCondition) throws InterruptedException
	{
		if (directory == null) return 0L;
		long deleted = 0L;
		Iterator<CacheItem> iterator = cacheItems.values().iterator();
		while (iterator.hasNext())
		{
			if (Thread.interrupted()) throw new InterruptedException();
			CacheItem cacheItem = iterator.next();
			if (deleteCondition == null || deleteCondition.allowDeleteCacheItem(cacheItem))
			{
				deleted += cacheItem.length;
				new File(directory, cacheItem.name).delete();
				iterator.remove();
			}
		}
		return deleted;
	}

	public void eraseThumbnailsCache() throws InterruptedException
	{
		synchronized (mThumbnailsCache)
		{
			eraseCache(mThumbnailsCache, getThumbnailsDirectory(), null);
			mThumbnailsCacheSize = 0L;
		}
	}

	public void eraseMediaCache() throws InterruptedException
	{
		synchronized (mMediaCache)
		{
			eraseCache(mMediaCache, getMediaDirectory(), null);
			mMediaCacheSize = 0L;
		}
	}

	public void erasePagesCache(boolean onlyOld) throws InterruptedException
	{
		synchronized (mPagesCache)
		{
			if (onlyOld)
			{
				long deleted = eraseCache(mPagesCache, getPagesDirectory(), new PagesCacheDeleteCondition());
				mPagesCacheSize -= deleted;
			}
			else
			{
				eraseCache(mPagesCache, getPagesDirectory(), null);
				mPagesCacheSize = 0L;
			}
		}
	}

	private final LruCache<String, String> mCachedFileKeys = new LruCache<>(1000);

	public String getCachedFileKey(Uri uri)
	{
		if (uri != null)
		{
			String data;
			String scheme = uri.getScheme();
			if ("data".equals(scheme))
			{
				String uriString = uri.toString();
				int index = uriString.indexOf("base64,");
				if (index >= 0) data = uriString.substring(index + 7);
				else data = uriString;
			}
			else if ("chan".equals(scheme)) data = uri.toString(); else
			{
				String chanName = ChanManager.getInstance().getChanNameByHost(uri.getHost());
				String path = uri.getPath();
				String query = uri.getQuery();
				StringBuilder dataBuilder = new StringBuilder();
				if (chanName != null) dataBuilder.append(chanName);
				dataBuilder.append(path);
				if (query != null) dataBuilder.append('?').append(query);
				data = dataBuilder.toString();
			}
			LruCache<String, String> cachedFileKeys = mCachedFileKeys;
			synchronized (cachedFileKeys)
			{
				String hash = cachedFileKeys.get(data);
				if (hash != null) return hash;
			}
			String hash = StringUtils.calculateSha256(data);
			synchronized (cachedFileKeys)
			{
				cachedFileKeys.put(data, hash);
			}
			return hash;
		}
		return null;
	}

	public boolean cancelCachedMediaBusy(File file)
	{
		if (mCacheItemsToDelete.remove(new CacheItem(file, CacheItem.TYPE_MEDIA)))
		{
			file.delete();
			return true;
		}
		return false;
	}

	public void handleDownloadedFile(File file, boolean success)
	{
		File directory = file.getParentFile();
		if (directory != null)
		{
			int type = -1;
			if (directory.equals(getThumbnailsDirectory())) type = CacheItem.TYPE_THUMBNAILS;
			else if (directory.equals(getMediaDirectory())) type = CacheItem.TYPE_MEDIA;
			else if (directory.equals(getPagesDirectory())) type = CacheItem.TYPE_PAGES;
			if (type != -1) validateNewCachedFile(file, file.getName(), type, success);
		}
	}

	private final LruCache<String, Bitmap> mBitmapCache = new LruCache<>(MainApplication.getInstance().isLowRam()
			? 50 : 200);

	public boolean isThumbnailCachedMemory(String thumbnailKey)
	{
		synchronized (mBitmapCache)
		{
			return mBitmapCache.get(thumbnailKey) != null;
		}
	}

	public Bitmap loadThumbnailMemory(String thumbnailKey)
	{
		synchronized (mBitmapCache)
		{
			return mBitmapCache.get(thumbnailKey);
		}
	}

	public void storeThumbnailMemory(String thumbnailKey, Bitmap data)
	{
		synchronized (mBitmapCache)
		{
			if (isThumbnailCachedMemory(thumbnailKey)) return;
			mBitmapCache.put(thumbnailKey, data);
		}
	}

	public File getThumbnailFile(String thumbnailKey)
	{
		File directory = getThumbnailsDirectory();
		if (directory == null) return null;
		return new File(directory, thumbnailKey);
	}

	public Bitmap loadThumbnailExternal(String thumbnailKey)
	{
		if (!isCacheAvailable()) return null;
		File file = getThumbnailFile(thumbnailKey);
		if (file == null) return null;
		if (!isFileExistsInCache(file, thumbnailKey, CacheItem.TYPE_THUMBNAILS)) return null;
		Bitmap bitmap;
		FileInputStream fis = null;
		try
		{
			fis = new FileInputStream(file);
			bitmap = BitmapFactory.decodeStream(fis);
			if (bitmap == null)
			{
				file.delete();
				return null;
			}
			updateCachedFileLastModified(file, thumbnailKey, CacheItem.TYPE_THUMBNAILS);
			return bitmap;
		}
		catch (IOException e)
		{
			return null;
		}
		finally
		{
			IOUtils.close(fis);
		}
	}

	public void storeThumbnailExternal(String thumbnailKey, Bitmap data)
	{
		if (!isCacheAvailable()) return;
		File directory = getThumbnailsDirectory();
		if (directory == null) return;
		boolean success = false;
		OutputStream outputStream = null;
		File file = new File(directory, thumbnailKey);
		try
		{
			outputStream = new FileOutputStream(file);
			data.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
			success = true;
		}
		catch (FileNotFoundException e)
		{

		}
		finally
		{
			IOUtils.close(outputStream);
			validateNewCachedFile(file, thumbnailKey, CacheItem.TYPE_THUMBNAILS, success);
		}
	}

	private final Object mSerializationQueueLock = new Object();
	private int mSerializationQueueSize = 0;

	public void waitSerializationFinished() throws InterruptedException
	{
		synchronized (mSerializationQueueLock)
		{
			while (mSerializationQueueSize > 0)
			{
				mSerializationQueueLock.wait();
			}
		}
	}

	private void handleSerializationQueue(boolean release)
	{
		synchronized (mSerializationQueueLock)
		{
			if (release) mSerializationQueueSize--; else mSerializationQueueSize++;
			mSerializationQueueLock.notifyAll();
		}
	}

	public static class SerializationHolder
	{
		private boolean cancelled = false;
		private Closeable closeable;

		private void setCloseable(Closeable closeable) throws IOException
		{
			synchronized (this)
			{
				if (cancelled) throw new IOException();
				this.closeable = closeable;
			}
		}

		public void cancel()
		{
			synchronized (this)
			{
				cancelled = true;
				IOUtils.close(closeable);
			}
		}
	}

	private final HashMap<File, Pair<FutureTask<Void>, SerializePageCallback>> mSerializeTasks = new HashMap<>();

	private void serializePage(String fileName, Object object)
	{
		if (!isCacheAvailable()) return;
		File file = getPagesFile(fileName);
		if (file == null) return;
		File tempFile = getPagesFile(TEMP_PAGE_FILE_PREFIX + System.currentTimeMillis() + "_" + fileName);
		Pair<FutureTask<Void>, SerializePageCallback> pair;
		synchronized (mSerializeTasks)
		{
			pair = mSerializeTasks.get(file);
		}
		if (pair != null)
		{
			pair.first.cancel(true);
			pair.second.onCancel();
		}
		SerializePageCallback callback = new SerializePageCallback(file, tempFile, fileName, object);
		FutureTask<Void> task = new FutureTask<>(callback);
		synchronized (mSerializeTasks)
		{
			mSerializeTasks.put(file, new Pair<>(task, callback));
		}
		AsyncTask.THREAD_POOL_EXECUTOR.execute(task);
		handleSerializationQueue(false);
	}

	@SuppressWarnings("unchecked")
	private <T> T deserializePage(String fileName, SerializationHolder holder) throws ClassCastException
	{
		if (!isCacheAvailable()) return null;
		File file = getPagesFile(fileName);
		if (file == null) return null;
		if (!isFileExistsInCache(file, fileName, CacheItem.TYPE_PAGES)) return null;
		updateCachedFileLastModified(file, fileName, CacheItem.TYPE_PAGES);
		synchronized (mSerializeTasks)
		{
			Pair<FutureTask<Void>, SerializePageCallback> pair = mSerializeTasks.get(file);
			if (pair != null) return (T) pair.second.mObject;
		}
		ObjectInputStream objectInputStream = null;
		try
		{
			FileInputStream fileInputStream = new FileInputStream(file);
			holder.setCloseable(fileInputStream);
			objectInputStream = new ObjectInputStream(fileInputStream);
			return (T) objectInputStream.readObject();
		}
		catch (Exception e)
		{
			synchronized (holder)
			{
				if (!holder.cancelled) Log.persistent().stack(e);
			}
		}
		finally
		{
			IOUtils.close(objectInputStream);
		}
		return null;
	}

	private final Object mTempSerializeFileLock = new Object();

	private class SerializePageCallback implements Callable<Void>
	{
		private final File mFile;
		private final File mTempFile;
		private final String mFileName;
		private final Object mObject;
		private final SerializationHolder mHolder = new SerializationHolder();

		public SerializePageCallback(File file, File tempFile, String fileName, Object object)
		{
			mFile = file;
			mTempFile = tempFile;
			mFileName = fileName;
			mObject = object;
		}

		@Override
		public Void call() throws Exception
		{
			boolean success = false;
			OutputStream outputStream = null;
			try
			{
				outputStream = new FileOutputStream(mTempFile != null ? mTempFile : mFile);
				ObjectOutputStream objectOutputStream = new ObjectOutputStream(new BufferedOutputStream(outputStream));
				outputStream = objectOutputStream;
				mHolder.setCloseable(outputStream);
				objectOutputStream.writeObject(mObject);
				objectOutputStream.flush();
				IOUtils.close(outputStream);
				outputStream = null;
				synchronized (mTempSerializeFileLock)
				{
					if (mTempFile.exists())
					{
						mFile.delete();
						mTempFile.renameTo(mFile);
						success = true;
					}
				}
			}
			catch (Exception e)
			{

			}
			finally
			{
				IOUtils.close(outputStream);
				if (success) validateNewCachedFile(mFile, mFileName, CacheItem.TYPE_PAGES, true);
				else mTempFile.delete();
				onFinished();
			}
			return null;
		}

		public void onCancel()
		{
			mHolder.cancel();
			onFinished();
		}

		private boolean mFinished = false;

		public void onFinished()
		{
			boolean release = false;
			synchronized (this)
			{
				if (!mFinished)
				{
					mFinished = true;
					release = true;
				}
			}
			if (release)
			{
				synchronized (mSerializeTasks)
				{
					Pair<FutureTask<Void>, SerializePageCallback> pair = mSerializeTasks.get(mFile);
					if (pair != null && pair.second == this) mSerializeTasks.remove(mFile);
				}
				handleSerializationQueue(true);
			}
		}
	}

	public boolean allowPagesCache(String chanName)
	{
		return !ChanConfiguration.get(chanName).getOption(ChanConfiguration.OPTION_HIDDEN_DISABLE_SERIALIZATION);
	}

	public void serializePosts(String chanName, String boardName, String threadNumber, Object posts)
	{
		if (allowPagesCache(chanName)) serializePage(getPostsFileName(chanName, boardName, threadNumber), posts);
	}

	public Posts deserializePosts(String chanName, String boardName, String threadNumber, SerializationHolder holder)
	{
		if (allowPagesCache(chanName))
		{
			try
			{
				return deserializePage(getPostsFileName(chanName, boardName, threadNumber), holder);
			}
			catch (ClassCastException e)
			{
				return null;
			}
		}
		else return null;
	}

	private String getPostsFileName(String chanName, String boardName, String threadNumber)
	{
		return "posts_" + chanName + "_" + boardName + "_" + threadNumber;
	}

	private File getPagesFile(String fileName)
	{
		File directory = getPagesDirectory();
		if (directory == null) return null;
		if (!directory.exists()) directory.mkdirs();
		return new File(directory, fileName);
	}

	private final Object mDirectoryLocker = new Object();

	private volatile File mExternalCacheDirectory;
	private volatile File mExternalTempDirectory;

	private File getExternalCacheDirectory()
	{
		if (mExternalCacheDirectory == null)
		{
			synchronized (mDirectoryLocker)
			{
				if (mExternalCacheDirectory == null)
				{
					mExternalCacheDirectory = MainApplication.getInstance().getExternalCacheDir();
				}
			}
		}
		return mExternalCacheDirectory;
	}

	private File getExternalTempDirectory()
	{
		if (mExternalTempDirectory == null)
		{
			synchronized (mDirectoryLocker)
			{
				if (mExternalTempDirectory == null)
				{
					mExternalTempDirectory = MainApplication.getInstance().getExternalCacheDir();
				}
			}
		}
		return mExternalTempDirectory;
	}

	public File getInternalCacheFile(String fileName)
	{
		File cacheDirectory = MainApplication.getInstance().getCacheDir();
		if (cacheDirectory != null) return new File(cacheDirectory, fileName);
		return null;
	}

	private static final String GALLERY_SHARE_FILE_NAME_START = "gallery-share-";

	private void handleGalleryShareFiles()
	{
		File tempDirectory = getExternalTempDirectory();
		if (tempDirectory == null) return;
		long time = System.currentTimeMillis();
		File[] files = tempDirectory.listFiles();
		if (files != null)
		{
			for (File tempFile : files)
			{
				if (tempFile.getName().startsWith(GALLERY_SHARE_FILE_NAME_START))
				{
					boolean delete = tempFile.lastModified() + 60 * 60 * 1000 < time; // 1 hour
					if (delete) tempFile.delete();
				}
			}
		}
	}

	public Pair<File, String> prepareFileForShare(File file, Uri sourceUri)
	{
		File tempDirectory = getExternalTempDirectory();
		if (tempDirectory == null) return null;
		String extension = StringUtils.getFileExtension(sourceUri != null ? sourceUri.getPath()
				: file.getAbsolutePath());
		String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
		if (mimeType == null)
		{
			mimeType = "image/jpeg";
			extension = "jpg";
		}
		handleGalleryShareFiles();
		String fileName = GALLERY_SHARE_FILE_NAME_START + System.currentTimeMillis() + "." + extension;
		File shareFile = new File(tempDirectory, fileName);
		IOUtils.copyInternalFile(file, shareFile);
		return new Pair<>(shareFile, mimeType);
	}
}