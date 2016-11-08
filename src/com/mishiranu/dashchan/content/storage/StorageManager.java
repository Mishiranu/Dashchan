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

package com.mishiranu.dashchan.content.storage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.LinkedBlockingQueue;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;
import android.util.SparseArray;

import chan.util.StringUtils;

import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;

public class StorageManager implements Handler.Callback, Runnable {
	private static final StorageManager INSTANCE = new StorageManager();
	private static final Charset CHARSET = Charset.forName("UTF-8");

	public static StorageManager getInstance() {
		return INSTANCE;
	}

	private StorageManager() {
		new Thread(this, "StorageManagerWorker").start();
	}

	private final Handler mHandler = new Handler(Looper.getMainLooper(), this);
	private final LinkedBlockingQueue<Pair<Storage, Object>> mQueue = new LinkedBlockingQueue<>();

	private int mNextIdentifier = 1;

	@Override
	public void run() {
		while (true) {
			Pair<Storage, Object> pair;
			try {
				pair = mQueue.take();
			} catch (InterruptedException e) {
				return;
			}
			performSerialize(pair.first, pair.second);
		}
	}

	private void performSerialize(Storage storage, Object data) {
		synchronized (storage.mLock) {
			JSONObject jsonObject;
			try {
				jsonObject = storage.onSerialize(data);
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
			File file = getFile(storage);
			File backupFile = getBackupFile(storage);
			if (jsonObject != null) {
				if (file.exists()) {
					if (!backupFile.exists()) {
						if (!file.renameTo(backupFile)) {
							Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES, "Can't create backup of", file);
							return;
						}
					} else {
						file.delete();
					}
				}
				boolean success = false;
				FileOutputStream output = null;
				try {
					output = new FileOutputStream(file);
					output.write(jsonObject.toString().getBytes(CHARSET));
					output.flush();
					output.getFD().sync();
					success = true;
				} catch (IOException e) {
					Log.persistent().write(e);
				} finally {
					success &= IOUtils.close(output);
					if (success) {
						backupFile.delete();
					} else if (file.exists() && !file.delete()) {
						Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES,
								"Can't delete partially written", file);
					}
				}
			} else {
				file.delete();
				backupFile.delete();
			}
		}
	}

	public static abstract class Storage {
		private final String mName;
		private final int mTimeout;
		private final int mMaxTimeout;

		private int mIdentifier = 0;
		private final Object mLock = new Object();

		public Storage(String name, int timeout, int maxTimeout) {
			mName = name;
			mTimeout = timeout;
			mMaxTimeout = maxTimeout;
		}

		public final File getFile() {
			return INSTANCE.getFile(this);
		}

		public final JSONObject read() {
			return INSTANCE.read(this);
		}

		public final void serialize() {
			INSTANCE.serialize(this);
		}

		public final void await(boolean async) {
			INSTANCE.await(this, async);
		}

		public abstract Object onClone();
		public abstract JSONObject onSerialize(Object data) throws JSONException;

		public static void putJson(JSONObject jsonObject, String name, String value) throws JSONException {
			if (!StringUtils.isEmpty(value)) {
				jsonObject.put(name, value);
			}
		}

		public static void putJson(JSONObject jsonObject, String name, boolean value) throws JSONException {
			if (value) {
				jsonObject.put(name, true);
			}
		}

		public static void putJson(JSONObject jsonObject, String name, int value) throws JSONException {
			if (value != 0) {
				jsonObject.put(name, value);
			}
		}

		public static void putJson(JSONObject jsonObject, String name, long value) throws JSONException {
			if (value != 0L) {
				jsonObject.put(name, value);
			}
		}
	}

	private File getDirectory() {
		File file = new File(MainApplication.getInstance().getFilesDir(), "storage");
		file.mkdirs();
		return file;
	}

	private File getBackupFile(Storage storage) {
		return new File(storage.getFile().getAbsolutePath() + "-backup");
	}

	private File getFile(String name) {
		return new File(getDirectory(), name + ".json");
	}

	private File getFile(Storage storage) {
		return getFile(storage.mName);
	}

	private JSONObject read(Storage storage) {
		File file = getFile(storage);
		File backupFile = getBackupFile(storage);
		if (backupFile.exists()) {
			file.delete();
			backupFile.renameTo(file);
		}
		FileInputStream input = null;
		byte[] bytes = null;
		try {
			input = new FileInputStream(file);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			IOUtils.copyStream(input, output);
			bytes = output.toByteArray();
		} catch (IOException e) {
			// Ignore
		} finally {
			IOUtils.close(input);
		}
		if (bytes != null) {
			try {
				return new JSONObject(new String(bytes, CHARSET));
			} catch (JSONException e) {
				// Ignore
			}
		}
		return null;
	}

	private final SparseArray<Long> mSerializeTimes = new SparseArray<>();

	private void serialize(Storage storage) {
		if (storage.mIdentifier == 0) {
			storage.mIdentifier = mNextIdentifier++;
		}
		Long timeObject = mSerializeTimes.get(storage.mIdentifier);
		long timeout;
		if (timeObject == null) {
			mSerializeTimes.put(storage.mIdentifier, System.currentTimeMillis());
			timeout = storage.mTimeout;
		} else {
			timeout = Math.min(storage.mTimeout, timeObject + storage.mMaxTimeout - System.currentTimeMillis());
		}
		mHandler.removeMessages(storage.mIdentifier);
		if (timeout <= 0) {
			enqueueSerialize(storage);
			mSerializeTimes.remove(storage.mIdentifier);
		} else {
			mHandler.sendMessageDelayed(mHandler.obtainMessage(storage.mIdentifier, storage), timeout);
		}
	}

	public void await(Storage storage, boolean async) {
		if (mHandler.hasMessages(storage.mIdentifier)) {
			mSerializeTimes.remove(storage.mIdentifier);
			mHandler.removeMessages(storage.mIdentifier);
			if (async) {
				enqueueSerialize(storage);
			} else {
				performSerialize(storage, storage.onClone());
			}
		}
	}

	private void enqueueSerialize(Storage storage) {
		mQueue.add(new Pair<>(storage, storage.onClone()));
	}

	@Override
	public boolean handleMessage(Message msg) {
		Storage storage = (Storage) msg.obj;
		mSerializeTimes.remove(storage.mIdentifier);
		enqueueSerialize(storage);
		return true;
	}
}