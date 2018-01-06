/*
 * Copyright 2014-2018 Fukurou Mishiranu
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Pair;

import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.content.model.Posts;
import chan.util.StringUtils;

import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.AndroidUtils;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.LruCache;
import com.mishiranu.dashchan.util.MimeTypes;

public class CacheManager implements Runnable {
	private static final int MAX_THUMBNAILS_PART = 30;
	private static final int MAX_MEDIA_PART = 60;
	private static final int MAX_PAGES_PART = 30;

	private static final long OLD_THREADS_THRESHOLD = 7 * 24 * 60 * 60 * 1000; // One week

	private static final float TRIM_FACTOR = 0.3f;

	private static final String TEMP_PAGE_FILE_PREFIX = "temp_";

	private static final CacheManager INSTANCE = new CacheManager();

	public static CacheManager getInstance() {
		return INSTANCE;
	}

	private CacheManager() {
		handleGalleryShareFiles();
		syncCache();
		IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
		intentFilter.addDataScheme("file");
		MainApplication.getInstance()
				.registerReceiver(AndroidUtils.createReceiver((r, c, i) -> syncCache()), intentFilter);
		new Thread(this, "CacheManagerWorker").start();
	}

	private final LinkedBlockingQueue<CacheItem> cacheItemsToDelete = new LinkedBlockingQueue<>();

	@Override
	public void run() {
		while (true) {
			CacheItem cacheItem;
			try {
				cacheItem = cacheItemsToDelete.take();
			} catch (InterruptedException e) {
				return;
			}
			File file = null;
			switch (cacheItem.type) {
				case CacheItem.TYPE_THUMBNAILS: {
					file = new File(getThumbnailsDirectory(), cacheItem.name);
					break;
				}
				case CacheItem.TYPE_MEDIA: {
					file = new File(getMediaDirectory(), cacheItem.name);
					break;
				}
				case CacheItem.TYPE_PAGES: {
					file = new File(getPagesDirectory(), cacheItem.name);
					break;
				}
			}
			file.delete();
		}
	}

	private volatile CountDownLatch cacheBuildingLatch;

	private class CacheItem {
		public static final int TYPE_THUMBNAILS = 0;
		public static final int TYPE_MEDIA = 1;
		public static final int TYPE_PAGES = 2;

		public final String name;
		public final String nameLc;
		public final long length;
		public long lastModified;
		public final int type;

		public CacheItem(File file, int type) {
			name = file.getName();
			nameLc = name.toLowerCase(Locale.US);
			length = file.length();
			lastModified = file.lastModified();
			this.type = type;
		}

		@Override
		public String toString() {
			return "CacheItem [\"" + name + "\", " + length + "]";
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof CacheItem) {
				CacheItem co = (CacheItem) o;
				return type == co.type && nameLc.equals(co.nameLc);
			}
			return false;
		}

		@Override
		public int hashCode() {
			int prime = 31;
			int result = 1;
			result = prime * result + type;
			result = prime * result + nameLc.hashCode();
			return result;
		}
	}

	private static final Comparator<CacheItem> SORT_BY_DATE_COMPARATOR =
			(lhs, rhs) -> ((Long) lhs.lastModified).compareTo(rhs.lastModified);

	private final LinkedHashMap<String, CacheItem> thumbnailsCache = new LinkedHashMap<>();
	private final LinkedHashMap<String, CacheItem> mediaCache = new LinkedHashMap<>();
	private final LinkedHashMap<String, CacheItem> pagesCache = new LinkedHashMap<>();

	private long thumbnailsCacheSize;
	private long mediaCacheSize;
	private long pagesCacheSize;

	private long fillCache(LinkedHashMap<String, CacheItem> cacheItems, File directory, int type) {
		cacheItems.clear();
		if (directory == null) {
			return 0L;
		}
		ArrayList<CacheItem> cacheItemsList = new ArrayList<>();
		File[] files = directory.listFiles();
		if (files != null) {
			for (File file : files) {
				CacheItem cacheItem = new CacheItem(file, type);
				if (type == CacheItem.TYPE_PAGES && cacheItem.name.startsWith(TEMP_PAGE_FILE_PREFIX)) {
					if (cacheItem.lastModified < System.currentTimeMillis() - OLD_THREADS_THRESHOLD) {
						file.delete();
					}
					continue;
				}
				cacheItemsList.add(cacheItem);
			}
		}
		Collections.sort(cacheItemsList, SORT_BY_DATE_COMPARATOR);
		long size = 0L;
		for (CacheItem cacheItem : cacheItemsList) {
			cacheItems.put(cacheItem.nameLc, cacheItem);
			size += cacheItem.length;
		}
		return size;
	}

	private void syncCache() {
		final CountDownLatch latch = new CountDownLatch(1);
		cacheBuildingLatch = latch;
		new Thread(() -> {
			try {
				synchronized (thumbnailsCache) {
					thumbnailsCacheSize = fillCache(thumbnailsCache, getThumbnailsDirectory(),
							CacheItem.TYPE_THUMBNAILS);
				}
				synchronized (mediaCache) {
					mediaCacheSize = fillCache(mediaCache, getMediaDirectory(), CacheItem.TYPE_MEDIA);
				}
				synchronized (pagesCache) {
					pagesCacheSize = fillCache(pagesCache, getPagesDirectory(), CacheItem.TYPE_PAGES);
				}
				cleanupAsync(true, true, true);
			} finally {
				latch.countDown();
			}
		}).start();
	}

	private void cleanupAsync(boolean thumbnails, boolean media, boolean pages) {
		int maxCache = MAX_THUMBNAILS_PART + MAX_MEDIA_PART + MAX_PAGES_PART;
		int maxCacheSizeMb = Preferences.getCacheSize();
		ArrayList<CacheItem> cleanupCacheItems = null;
		if (thumbnails) {
			synchronized (thumbnailsCache) {
				long maxSize = MAX_THUMBNAILS_PART * maxCacheSizeMb * 1024L * 1024L / maxCache;
				if (thumbnailsCacheSize > maxSize) {
					if (cleanupCacheItems == null) {
						cleanupCacheItems = new ArrayList<>();
					}
					thumbnailsCacheSize = obtainCacheItemsToCleanup(cleanupCacheItems, thumbnailsCache,
							thumbnailsCacheSize, maxSize, null);
				}
			}
		}
		if (media) {
			synchronized (mediaCache) {
				long maxSize = MAX_MEDIA_PART * maxCacheSizeMb * 1024L * 1024L / maxCache;
				if (mediaCacheSize > maxSize) {
					if (cleanupCacheItems == null) {
						cleanupCacheItems = new ArrayList<>();
					}
					mediaCacheSize = obtainCacheItemsToCleanup(cleanupCacheItems, mediaCache,
							mediaCacheSize, maxSize, null);
				}
			}
		}
		if (pages) {
			synchronized (pagesCache) {
				long maxSize = MAX_PAGES_PART * maxCacheSizeMb * 1024L * 1024L / maxCache;
				if (pagesCacheSize > maxSize) {
					if (cleanupCacheItems == null) {
						cleanupCacheItems = new ArrayList<>();
					}
					pagesCacheSize = obtainCacheItemsToCleanup(cleanupCacheItems, pagesCache,
							pagesCacheSize, maxSize, new PagesCacheDeleteCondition());
				}
			}
		}
		if (cleanupCacheItems != null && cleanupCacheItems.size() > 0) {
			// Start handling
			cacheItemsToDelete.addAll(cleanupCacheItems);
		}
	}

	private long obtainCacheItemsToCleanup(ArrayList<CacheItem> cleanupCacheItems,
			LinkedHashMap<String, CacheItem> cacheItems, long size, long maxSize, DeleteCondition deleteCondition) {
		long trimAmount = (long) (TRIM_FACTOR * maxSize);
		long deleteAmount = size - maxSize + trimAmount;
		Iterator<CacheItem> iterator = cacheItems.values().iterator();
		while (iterator.hasNext() && deleteAmount > 0) {
			CacheItem cacheItem = iterator.next();
			if (deleteCondition == null || deleteCondition.allowDeleteCacheItem(cacheItem)) {
				deleteAmount -= cacheItem.length;
				size -= cacheItem.length;
				iterator.remove();
				cleanupCacheItems.add(cacheItem);
			}
		}
		return size;
	}

	private interface DeleteCondition {
		public boolean allowDeleteCacheItem(CacheItem cacheItem);
	}

	private class PagesCacheDeleteCondition implements DeleteCondition {
		private final long olderThan = System.currentTimeMillis() - OLD_THREADS_THRESHOLD;
		private final ArrayList<String> favoriteFiles = new ArrayList<>();

		public PagesCacheDeleteCondition() {
			Collection<String> chanNames = ChanManager.getInstance().getAllChanNames();
			for (FavoritesStorage.FavoriteItem favoriteItem : ConcurrentUtils
					.mainGet(() -> FavoritesStorage.getInstance().getThreads(null))) {
				if (chanNames.contains(favoriteItem.chanName)) {
					favoriteFiles.add(getPostsFileName(favoriteItem.chanName, favoriteItem.boardName,
							favoriteItem.threadNumber).toLowerCase(Locale.US));
				}
			}
		}

		@Override
		public boolean allowDeleteCacheItem(CacheItem cacheItem) {
			return cacheItem.lastModified < olderThan && !favoriteFiles.contains(cacheItem.nameLc);
		}
	}

	private boolean waitCacheSync() {
		CountDownLatch latch = cacheBuildingLatch;
		if (latch != null) {
			try {
				latch.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return true;
			}
		}
		return false;
	}

	private LinkedHashMap<String, CacheItem> getCacheItems(int type) {
		switch (type) {
			case CacheItem.TYPE_THUMBNAILS: {
				return thumbnailsCache;
			}
			case CacheItem.TYPE_MEDIA: {
				return mediaCache;
			}
			case CacheItem.TYPE_PAGES: {
				return pagesCache;
			}
		}
		throw new RuntimeException("Unknown cache type");
	}

	private void modifyCacheSize(int type, long lengthDelta) {
		switch (type) {
			case CacheItem.TYPE_THUMBNAILS: {
				thumbnailsCacheSize += lengthDelta;
				break;
			}
			case CacheItem.TYPE_MEDIA: {
				mediaCacheSize += lengthDelta;
				break;
			}
			case CacheItem.TYPE_PAGES: {
				pagesCacheSize += lengthDelta;
				break;
			}
		}
	}

	private boolean isFileExistsInCache(File file, String fileName, int type) {
		if (waitCacheSync()) {
			return false;
		}
		LinkedHashMap<String, CacheItem> cacheItems = getCacheItems(type);
		synchronized (cacheItems) {
			CacheItem cacheItem = cacheItems.get(fileName.toLowerCase(Locale.US));
			if (cacheItem != null && !file.exists()) {
				cacheItems.remove(cacheItem.nameLc);
				modifyCacheSize(type, -cacheItem.length);
				cacheItem = null;
			}
			return cacheItem != null;
		}
	}

	private void updateCachedFileLastModified(File file, String fileName, int type) {
		if (waitCacheSync()) {
			return;
		}
		LinkedHashMap<String, CacheItem> cacheItems = getCacheItems(type);
		synchronized (cacheItems) {
			String fileNameLc = fileName.toLowerCase(Locale.US);
			CacheItem cacheItem = cacheItems.remove(fileNameLc);
			if (cacheItem != null) {
				if (file.exists()) {
					long lastModified = System.currentTimeMillis();
					file.setLastModified(lastModified);
					cacheItem.lastModified = lastModified;
					cacheItems.put(fileNameLc, cacheItem);
				} else {
					modifyCacheSize(type, -cacheItem.length);
				}
			}
		}
	}

	private void validateNewCachedFile(File file, String fileName, int type, boolean success) {
		if (waitCacheSync()) {
			return;
		}
		LinkedHashMap<String, CacheItem> cacheItems = getCacheItems(type);
		synchronized (cacheItems) {
			long lengthDelta = 0L;
			CacheItem cacheItem = cacheItems.remove(fileName.toLowerCase(Locale.US));
			if (cacheItem != null) {
				lengthDelta = -cacheItem.length;
			}
			if (success) {
				cacheItem = new CacheItem(file, type);
				cacheItems.put(cacheItem.nameLc, cacheItem);
				lengthDelta += cacheItem.length;
			}
			modifyCacheSize(type, lengthDelta);
			if (success) {
				cleanupAsync(type == CacheItem.TYPE_THUMBNAILS, type == CacheItem.TYPE_MEDIA,
						type == CacheItem.TYPE_PAGES);
			}
		}
	}

	public boolean isCacheAvailable() {
		return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
	}

	public long getCacheSize() {
		if (waitCacheSync()) {
			return 0L;
		}
		return thumbnailsCacheSize + mediaCacheSize + pagesCacheSize;
	}

	private File getThumbnailsDirectory() {
		return getCacheDirectory("thumbnails");
	}

	private File getMediaDirectory() {
		return getCacheDirectory("media");
	}

	private File getPagesDirectory() {
		return getCacheDirectory("pages");
	}

	private File getCacheDirectory(String subFolder) {
		File directory = getExternalCacheDirectory();
		if (directory == null) {
			return null;
		}
		File file = new File(directory, subFolder);
		if (isCacheAvailable() && !file.exists()) {
			file.mkdirs();
		}
		return file;
	}

	private File getMediaFile(String fileName, boolean touch) {
		File directory = getMediaDirectory();
		if (directory == null) {
			return null;
		}
		File file = new File(directory, fileName);
		if (touch) {
			updateCachedFileLastModified(file, fileName, CacheItem.TYPE_MEDIA);
		}
		return file;
	}

	public File getMediaFile(Uri uri, boolean touch) {
		return getMediaFile(getCachedFileKey(uri), touch);
	}

	private long eraseCache(LinkedHashMap<String, CacheItem> cacheItems, File directory,
			DeleteCondition deleteCondition) throws InterruptedException {
		if (directory == null) {
			return 0L;
		}
		long deleted = 0L;
		Iterator<CacheItem> iterator = cacheItems.values().iterator();
		while (iterator.hasNext()) {
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}
			CacheItem cacheItem = iterator.next();
			if (deleteCondition == null || deleteCondition.allowDeleteCacheItem(cacheItem)) {
				deleted += cacheItem.length;
				new File(directory, cacheItem.name).delete();
				iterator.remove();
			}
		}
		return deleted;
	}

	public void eraseThumbnailsCache() throws InterruptedException {
		synchronized (thumbnailsCache) {
			eraseCache(thumbnailsCache, getThumbnailsDirectory(), null);
			thumbnailsCacheSize = 0L;
		}
	}

	public void eraseMediaCache() throws InterruptedException {
		synchronized (mediaCache) {
			eraseCache(mediaCache, getMediaDirectory(), null);
			mediaCacheSize = 0L;
		}
	}

	public void erasePagesCache(boolean onlyOld) throws InterruptedException {
		synchronized (pagesCache) {
			if (onlyOld) {
				long deleted = eraseCache(pagesCache, getPagesDirectory(), new PagesCacheDeleteCondition());
				pagesCacheSize -= deleted;
			} else {
				eraseCache(pagesCache, getPagesDirectory(), null);
				pagesCacheSize = 0L;
			}
		}
	}

	private final LruCache<String, String> cachedFileKeys = new LruCache<>(1000);

	public String getCachedFileKey(Uri uri) {
		if (uri != null) {
			String data;
			String scheme = uri.getScheme();
			if ("data".equals(scheme)) {
				String uriString = uri.toString();
				int index = uriString.indexOf("base64,");
				if (index >= 0) {
					data = uriString.substring(index + 7);
				} else {
					data = uriString;
				}
			} else if ("chan".equals(scheme)) {
				data = uri.toString();
			} else {
				String chanName = ChanManager.getInstance().getChanNameByHost(uri.getHost());
				String path = uri.getPath();
				String query = uri.getQuery();
				StringBuilder dataBuilder = new StringBuilder();
				if (chanName != null) {
					dataBuilder.append(chanName);
				}
				dataBuilder.append(path);
				if (query != null) {
					dataBuilder.append('?').append(query);
				}
				data = dataBuilder.toString();
			}
			LruCache<String, String> cachedFileKeys = this.cachedFileKeys;
			synchronized (cachedFileKeys) {
				String hash = cachedFileKeys.get(data);
				if (hash != null) {
					return hash;
				}
			}
			String hash = IOUtils.calculateSha256(data);
			synchronized (cachedFileKeys) {
				cachedFileKeys.put(data, hash);
			}
			return hash;
		}
		return null;
	}

	public boolean cancelCachedMediaBusy(File file) {
		if (cacheItemsToDelete.remove(new CacheItem(file, CacheItem.TYPE_MEDIA))) {
			file.delete();
			return true;
		}
		return false;
	}

	public void handleDownloadedFile(File file, boolean success) {
		File directory = file.getParentFile();
		if (directory != null) {
			int type = -1;
			if (directory.equals(getThumbnailsDirectory())) {
				type = CacheItem.TYPE_THUMBNAILS;
			} else if (directory.equals(getMediaDirectory())) {
				type = CacheItem.TYPE_MEDIA;
			} else if (directory.equals(getPagesDirectory())) {
				type = CacheItem.TYPE_PAGES;
			}
			if (type != -1) {
				validateNewCachedFile(file, file.getName(), type, success);
			}
		}
	}

	private final LruCache<String, Bitmap> bitmapCache = new LruCache<>(MainApplication.getInstance().isLowRam()
			? 50 : 200);

	public boolean isThumbnailCachedMemory(String thumbnailKey) {
		synchronized (bitmapCache) {
			return bitmapCache.get(thumbnailKey) != null;
		}
	}

	public Bitmap loadThumbnailMemory(String thumbnailKey) {
		synchronized (bitmapCache) {
			return bitmapCache.get(thumbnailKey);
		}
	}

	public void storeThumbnailMemory(String thumbnailKey, Bitmap data) {
		synchronized (bitmapCache) {
			if (isThumbnailCachedMemory(thumbnailKey)) {
				return;
			}
			bitmapCache.put(thumbnailKey, data);
		}
	}

	public File getThumbnailFile(String thumbnailKey) {
		File directory = getThumbnailsDirectory();
		if (directory == null) {
			return null;
		}
		return new File(directory, thumbnailKey);
	}

	public Bitmap loadThumbnailExternal(String thumbnailKey) {
		if (!isCacheAvailable()) {
			return null;
		}
		File file = getThumbnailFile(thumbnailKey);
		if (file == null) {
			return null;
		}
		if (!isFileExistsInCache(file, thumbnailKey, CacheItem.TYPE_THUMBNAILS)) {
			return null;
		}
		Bitmap bitmap;
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			bitmap = BitmapFactory.decodeStream(fis);
			if (bitmap == null) {
				file.delete();
				return null;
			}
			updateCachedFileLastModified(file, thumbnailKey, CacheItem.TYPE_THUMBNAILS);
			return bitmap;
		} catch (IOException e) {
			return null;
		} finally {
			IOUtils.close(fis);
		}
	}

	public void storeThumbnailExternal(String thumbnailKey, Bitmap data) {
		if (!isCacheAvailable()) {
			return;
		}
		File directory = getThumbnailsDirectory();
		if (directory == null) {
			return;
		}
		boolean success = false;
		OutputStream outputStream = null;
		File file = new File(directory, thumbnailKey);
		try {
			outputStream = new FileOutputStream(file);
			data.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
			success = true;
		} catch (IOException e) {
			// Ignore exception
		} finally {
			IOUtils.close(outputStream);
			validateNewCachedFile(file, thumbnailKey, CacheItem.TYPE_THUMBNAILS, success);
		}
	}

	private final Object serializationQueueLock = new Object();
	private int serializationQueueSize = 0;

	public void waitSerializationFinished() throws InterruptedException {
		synchronized (serializationQueueLock) {
			while (serializationQueueSize > 0) {
				serializationQueueLock.wait();
			}
		}
	}

	private void handleSerializationQueue(boolean release) {
		synchronized (serializationQueueLock) {
			if (release) {
				serializationQueueSize--;
			} else {
				serializationQueueSize++;
			}
			serializationQueueLock.notifyAll();
		}
	}

	public static class SerializationHolder {
		private boolean cancelled = false;
		private Closeable closeable;

		private void setCloseable(Closeable closeable) throws IOException {
			synchronized (this) {
				if (cancelled) {
					throw new IOException();
				}
				this.closeable = closeable;
			}
		}

		public void cancel() {
			synchronized (this) {
				cancelled = true;
				IOUtils.close(closeable);
			}
		}
	}

	private final HashMap<String, SerializePageCallback> serializePageCallbacks = new HashMap<>();

	private void serializePage(String fileName, Object object) {
		if (!isCacheAvailable()) {
			return;
		}
		File file = getPagesFile(fileName);
		if (file == null) {
			return;
		}
		File tempFile = getPagesFile(TEMP_PAGE_FILE_PREFIX + fileName);
		SerializePageCallback callback;
		synchronized (serializePageCallbacks) {
			callback = serializePageCallbacks.get(fileName);
		}
		if (callback != null) {
			callback.cancel();
		}
		callback = new SerializePageCallback(file, tempFile, fileName, object);
		synchronized (serializePageCallbacks) {
			serializePageCallbacks.put(fileName, callback);
		}
		AsyncTask.THREAD_POOL_EXECUTOR.execute(callback);
		handleSerializationQueue(false);
	}

	private Object deserializePage(String fileName, SerializationHolder holder) {
		if (!isCacheAvailable()) {
			return null;
		}
		File file = getPagesFile(fileName);
		if (file == null) {
			return null;
		}
		synchronized (serializePageCallbacks) {
			SerializePageCallback callback = serializePageCallbacks.get(fileName);
			if (callback != null) {
				return callback.object;
			}
		}
		synchronized (obtainPageFileLock(fileName)) {
			File tempFile = getPagesFile(TEMP_PAGE_FILE_PREFIX + fileName);
			if (tempFile.exists()) {
				if ((!file.exists() || !file.delete()) && !tempFile.renameTo(file)) {
					Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES,
							"Can't restore backup file", tempFile.getName());
				} else {
					validateNewCachedFile(file, fileName, CacheItem.TYPE_PAGES, true);
				}
			}
			ObjectInputStream objectInputStream = null;
			try {
				FileInputStream fileInputStream = new FileInputStream(file);
				holder.setCloseable(fileInputStream);
				objectInputStream = new ObjectInputStream(fileInputStream);
				Object result = objectInputStream.readObject();
				updateCachedFileLastModified(file, fileName, CacheItem.TYPE_PAGES);
				return result;
			} catch (FileNotFoundException e) {
				// File not exist, ignore exception
			} catch (Exception e) {
				if (!holder.cancelled) {
					Log.persistent().stack(e);
				}
			} finally {
				IOUtils.close(objectInputStream);
			}
			return null;
		}
	}

	private final HashMap<String, Object> pageFileLocks = new HashMap<>();

	private Object obtainPageFileLock(String fileName) {
		synchronized (pageFileLocks) {
			Object object = pageFileLocks.get(fileName);
			if (object == null) {
				object = new Object();
				pageFileLocks.put(fileName, object);
			}
			return object;
		}
	}

	private class SerializePageCallback implements Runnable {
		private final File file;
		private final File tempFile;
		private final String fileName;
		private final Object object;
		private final SerializationHolder holder = new SerializationHolder();

		public SerializePageCallback(File file, File tempFile, String fileName, Object object) {
			this.file = file;
			this.tempFile = tempFile;
			this.fileName = fileName;
			this.object = object;
		}

		@Override
		public void run() {
			synchronized (obtainPageFileLock(fileName)) {
				if (holder.cancelled) {
					return;
				}
				if (file.exists() && (!tempFile.exists() || !tempFile.delete()) && !file.renameTo(tempFile)) {
					Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES,
							"Can't create backup of", file.getName());
					return;
				}
				boolean success = false;
				FileOutputStream outputStream = null;
				try {
					outputStream = new FileOutputStream(file);
					ObjectOutputStream objectOutputStream = new ObjectOutputStream
							(new BufferedOutputStream(outputStream));
					holder.setCloseable(objectOutputStream);
					objectOutputStream.writeObject(object);
					objectOutputStream.flush();
					outputStream.getFD().sync();
					success = true;
				} catch (IOException e) {
					Log.persistent().write(e);
				} finally {
					success &= IOUtils.close(outputStream);
					if (success) {
						if (tempFile.exists() && !tempFile.delete()) {
							Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES,
									"Can't delete temp file", tempFile.getName());
						}
						validateNewCachedFile(file, fileName, CacheItem.TYPE_PAGES, true);
					} else {
						file.delete();
						tempFile.renameTo(file);
					}
					synchronized (serializePageCallbacks) {
						serializePageCallbacks.remove(fileName);
					}
					handleSerializationQueue(true);
				}
			}
		}

		public void cancel() {
			holder.cancel();
			synchronized (obtainPageFileLock(fileName)) {
				// Wait until run() over
			}
		}
	}

	public void movePostsPage(String chanName, String fromBoardName, String fromThreadNumber,
			String toBoardName, String toThreadNumber) {
		String fromFileName = getPostsFileName(chanName, fromBoardName, fromThreadNumber);
		String toFileName = getPostsFileName(chanName, toBoardName, toThreadNumber);
		File fromFile = getPagesFile(fromFileName);
		File toFile = getPagesFile(toFileName);
		File fromTempFile = getPagesFile(TEMP_PAGE_FILE_PREFIX + fromFileName);
		File toTempFile = getPagesFile(TEMP_PAGE_FILE_PREFIX + toFileName);
		if (fromFile == null || toFile == null || fromTempFile == null || toFileName == null) {
			return;
		}
		synchronized (obtainPageFileLock(fromFile.getName())) {
			synchronized (obtainPageFileLock(toFile.getName())) {
				if (fromFile.exists() && !toFile.exists()) {
					toTempFile.delete();
					fromFile.renameTo(toFile);
					fromTempFile.renameTo(toTempFile);
					validateNewCachedFile(fromFile, fromFile.getName(), CacheItem.TYPE_PAGES, false);
					validateNewCachedFile(toFile, toFile.getName(), CacheItem.TYPE_PAGES, true);
				}
			}
		}
	}

	public boolean allowPagesCache(String chanName) {
		return !ChanConfiguration.get(chanName).getOption(ChanConfiguration.OPTION_HIDDEN_DISABLE_SERIALIZATION);
	}

	public void serializePosts(String chanName, String boardName, String threadNumber, Object posts) {
		if (allowPagesCache(chanName)) {
			serializePage(getPostsFileName(chanName, boardName, threadNumber), posts);
		}
	}

	public Posts deserializePosts(String chanName, String boardName, String threadNumber, SerializationHolder holder) {
		if (allowPagesCache(chanName)) {
			try {
				return (Posts) deserializePage(getPostsFileName(chanName, boardName, threadNumber), holder);
			} catch (ClassCastException e) {
				return null;
			}
		} else {
			return null;
		}
	}

	private String getPostsFileName(String chanName, String boardName, String threadNumber) {
		return "posts_" + chanName + "_" + boardName + "_" + threadNumber;
	}

	private File getPagesFile(String fileName) {
		File directory = getPagesDirectory();
		if (directory == null) {
			return null;
		}
		if (!directory.exists()) {
			directory.mkdirs();
		}
		return new File(directory, fileName);
	}

	private final Object directoryLocker = new Object();

	private volatile File externalCacheDirectory;
	private volatile File externalTempDirectory;

	private File getExternalCacheDirectory() {
		if (externalCacheDirectory == null) {
			synchronized (directoryLocker) {
				if (externalCacheDirectory == null) {
					externalCacheDirectory = MainApplication.getInstance().getExternalCacheDir();
				}
			}
		}
		return externalCacheDirectory;
	}

	private File getExternalTempDirectory() {
		if (externalTempDirectory == null) {
			synchronized (directoryLocker) {
				if (externalTempDirectory == null) {
					externalTempDirectory = MainApplication.getInstance().getExternalCacheDir();
				}
			}
		}
		return externalTempDirectory;
	}

	public File getInternalCacheFile(String fileName) {
		File cacheDirectory = MainApplication.getInstance().getCacheDir();
		if (cacheDirectory != null) {
			return new File(cacheDirectory, fileName);
		}
		return null;
	}

	private static final String GALLERY_SHARE_FILE_NAME_START = "gallery-share-";

	private void handleGalleryShareFiles() {
		File tempDirectory = getExternalTempDirectory();
		if (tempDirectory == null) {
			return;
		}
		long time = System.currentTimeMillis();
		File[] files = tempDirectory.listFiles();
		if (files != null) {
			for (File tempFile : files) {
				if (tempFile.getName().startsWith(GALLERY_SHARE_FILE_NAME_START)) {
					boolean delete = tempFile.lastModified() + 60 * 60 * 1000 < time; // 1 hour
					if (delete) {
						tempFile.delete();
					}
				}
			}
		}
	}

	public Pair<Uri, String> prepareFileForShare(File file, String fileName) {
		File tempDirectory = getExternalTempDirectory();
		if (tempDirectory == null) {
			return null;
		}
		String extension = StringUtils.getFileExtension(fileName);
		String mimeType = MimeTypes.forExtension(extension);
		if (mimeType == null) {
			mimeType = "image/jpeg";
			extension = "jpg";
		}
		handleGalleryShareFiles();
		fileName = GALLERY_SHARE_FILE_NAME_START + System.currentTimeMillis() + "." + extension;
		File shareFile = new File(tempDirectory, fileName);
		IOUtils.copyInternalFile(file, shareFile);
		Uri uri = FileProvider.convertShareFile(tempDirectory, shareFile, mimeType);
		return new Pair<>(uri, mimeType);
	}
}
