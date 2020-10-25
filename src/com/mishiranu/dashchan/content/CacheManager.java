package com.mishiranu.dashchan.content;

import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.util.Pair;
import androidx.annotation.NonNull;
import chan.content.Chan;
import chan.util.StringUtils;
import com.mishiranu.dashchan.util.AndroidUtils;
import com.mishiranu.dashchan.util.Hasher;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.LruCache;
import com.mishiranu.dashchan.util.MimeTypes;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

public class CacheManager implements Runnable {
	private static final int MAX_THUMBNAILS_PART = 1;
	private static final int MAX_MEDIA_PART = 2;

	private static final float TRIM_FACTOR = 0.3f;

	private static final CacheManager INSTANCE = new CacheManager();

	public static CacheManager getInstance() {
		return INSTANCE;
	}

	private CacheManager() {
		if (MainApplication.getInstance().isMainProcess()) {
			handleGalleryShareFiles();
			syncCache();
			IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
			intentFilter.addDataScheme("file");
			MainApplication.getInstance()
					.registerReceiver(AndroidUtils.createReceiver((r, c, i) -> syncCache()), intentFilter);
			new Thread(this, "CacheManagerWorker").start();
		}
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
				case THUMBNAILS: {
					file = new File(getThumbnailsDirectory(), cacheItem.name);
					break;
				}
				case MEDIA: {
					file = new File(getMediaDirectory(), cacheItem.name);
					break;
				}
			}
			file.delete();
		}
	}

	private volatile CountDownLatch cacheBuildingLatch;

	private static class CacheItem {
		public enum Type {THUMBNAILS, MEDIA}

		public final String name;
		public final String nameLc;
		public final long length;
		public long lastModified;
		public final Type type;

		public CacheItem(File file, Type type) {
			name = file.getName();
			nameLc = name.toLowerCase(Locale.US);
			length = file.length();
			lastModified = file.lastModified();
			this.type = type;
		}

		@NonNull
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
			result = prime * result + type.hashCode();
			result = prime * result + nameLc.hashCode();
			return result;
		}
	}

	private static final Comparator<CacheItem> SORT_BY_DATE_COMPARATOR =
			(lhs, rhs) -> ((Long) lhs.lastModified).compareTo(rhs.lastModified);

	private final LinkedHashMap<String, CacheItem> thumbnailsCache = new LinkedHashMap<>();
	private final LinkedHashMap<String, CacheItem> mediaCache = new LinkedHashMap<>();

	private long thumbnailsCacheSize;
	private long mediaCacheSize;

	private long fillCache(LinkedHashMap<String, CacheItem> cacheItems, File directory, CacheItem.Type type) {
		cacheItems.clear();
		if (directory == null) {
			return 0L;
		}
		ArrayList<CacheItem> cacheItemsList = new ArrayList<>();
		File[] files = directory.listFiles();
		if (files != null) {
			for (File file : files) {
				cacheItemsList.add(new CacheItem(file, type));
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
							CacheItem.Type.THUMBNAILS);
				}
				synchronized (mediaCache) {
					mediaCacheSize = fillCache(mediaCache, getMediaDirectory(), CacheItem.Type.MEDIA);
				}
				cleanupAsync(true, true);
			} finally {
				latch.countDown();
			}
		}).start();
	}

	private void cleanupAsync(boolean thumbnails, boolean media) {
		int maxCache = MAX_THUMBNAILS_PART + MAX_MEDIA_PART;
		long maxCacheSize = Preferences.getCacheSize() * 1000L * 1000L;
		ArrayList<CacheItem> cleanupCacheItems = null;
		if (thumbnails) {
			synchronized (thumbnailsCache) {
				long maxSize = MAX_THUMBNAILS_PART * maxCacheSize / maxCache;
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
				long maxSize = MAX_MEDIA_PART * maxCacheSize / maxCache;
				if (mediaCacheSize > maxSize) {
					if (cleanupCacheItems == null) {
						cleanupCacheItems = new ArrayList<>();
					}
					mediaCacheSize = obtainCacheItemsToCleanup(cleanupCacheItems, mediaCache,
							mediaCacheSize, maxSize, null);
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
		boolean allowDeleteCacheItem(CacheItem cacheItem);
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

	private LinkedHashMap<String, CacheItem> getCacheItems(CacheItem.Type type) {
		switch (type) {
			case THUMBNAILS: {
				return thumbnailsCache;
			}
			case MEDIA: {
				return mediaCache;
			}
		}
		throw new RuntimeException("Unknown cache type");
	}

	private void modifyCacheSize(CacheItem.Type type, long lengthDelta) {
		switch (type) {
			case THUMBNAILS: {
				thumbnailsCacheSize += lengthDelta;
				break;
			}
			case MEDIA: {
				mediaCacheSize += lengthDelta;
				break;
			}
		}
	}

	private boolean isFileExistsInCache(File file, String fileName, CacheItem.Type type) {
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

	private void updateCachedFileLastModified(File file, String fileName, CacheItem.Type type) {
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

	private void validateNewCachedFile(File file, String fileName, CacheItem.Type type, boolean success) {
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
				cleanupAsync(type == CacheItem.Type.THUMBNAILS, type == CacheItem.Type.MEDIA);
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
		return thumbnailsCacheSize + mediaCacheSize;
	}

	private File getThumbnailsDirectory() {
		return getCacheDirectory("thumbnails");
	}

	public File getMediaDirectory() {
		return getCacheDirectory("media");
	}

	private File getCacheDirectory(String name) {
		File directory = getExternalCacheDirectory();
		if (directory == null) {
			return null;
		}
		File file = new File(directory, name);
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
			updateCachedFileLastModified(file, fileName, CacheItem.Type.MEDIA);
		}
		return file;
	}

	public File getMediaFile(Uri uri, boolean touch) {
		return getMediaFile(getCachedFileKey(uri), touch);
	}

	public File getPartialMediaFile(Uri uri) {
		return getMediaFile(getCachedFileKey(uri) + ".part", false);
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
				Chan chan = Chan.getPreferred(null, uri);
				String path = uri.getPath();
				String query = uri.getQuery();
				StringBuilder dataBuilder = new StringBuilder();
				if (chan.name != null) {
					dataBuilder.append(chan.name);
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
			String hash = StringUtils.formatHex(Hasher.getInstanceSha256().calculate(data));
			synchronized (cachedFileKeys) {
				cachedFileKeys.put(data, hash);
			}
			return hash;
		}
		return null;
	}

	public boolean cancelCachedMediaBusy(File file) {
		if (cacheItemsToDelete.remove(new CacheItem(file, CacheItem.Type.MEDIA))) {
			file.delete();
			return true;
		}
		return false;
	}

	public void handleDownloadedFile(File file, boolean success) {
		File directory = file.getParentFile();
		if (directory != null) {
			CacheItem.Type type = null;
			if (directory.equals(getThumbnailsDirectory())) {
				type = CacheItem.Type.THUMBNAILS;
			} else if (directory.equals(getMediaDirectory())) {
				type = CacheItem.Type.MEDIA;
			}
			if (type != null) {
				validateNewCachedFile(file, file.getName(), type, success);
			}
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
		if (!isFileExistsInCache(file, thumbnailKey, CacheItem.Type.THUMBNAILS)) {
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
			updateCachedFileLastModified(file, thumbnailKey, CacheItem.Type.THUMBNAILS);
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
			validateNewCachedFile(file, thumbnailKey, CacheItem.Type.THUMBNAILS, success);
		}
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
