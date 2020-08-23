package com.mishiranu.dashchan.content.storage;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Pair;
import android.util.SparseArray;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.LinkedBlockingQueue;
import org.json.JSONException;
import org.json.JSONObject;

public class StorageManager implements Handler.Callback, Runnable {
	private static final StorageManager INSTANCE = new StorageManager();
	@SuppressWarnings("CharsetObjectCanBeUsed")
	private static final Charset CHARSET = Charset.forName("UTF-8");

	public static StorageManager getInstance() {
		return INSTANCE;
	}

	private StorageManager() {
		new Thread(this, "StorageManagerWorker").start();
	}

	private final Handler handler = new Handler(Looper.getMainLooper(), this);
	private final LinkedBlockingQueue<Pair<Storage, Object>> queue = new LinkedBlockingQueue<>();

	private int nextIdentifier = 1;

	@Override
	public void run() {
		while (true) {
			Pair<Storage, Object> pair;
			try {
				pair = queue.take();
			} catch (InterruptedException e) {
				return;
			}
			performSerialize(pair.first, pair.second);
		}
	}

	private void performSerialize(Storage storage, Object data) {
		synchronized (storage.lock) {
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
		private final String name;
		private final int timeout;
		private final int maxTimeout;

		private int identifier = 0;
		private final Object lock = new Object();

		public Storage(String name, int timeout, int maxTimeout) {
			this.name = name;
			this.timeout = timeout;
			this.maxTimeout = maxTimeout;
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
		return getFile(storage.name);
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
			// Ignore exception
		} finally {
			IOUtils.close(input);
		}
		if (bytes != null) {
			try {
				return new JSONObject(new String(bytes, CHARSET));
			} catch (JSONException e) {
				// Invalid JSON object, ignore exception
			}
		}
		return null;
	}

	private final SparseArray<Long> serializeTimes = new SparseArray<>();

	private void serialize(Storage storage) {
		if (storage.identifier == 0) {
			storage.identifier = nextIdentifier++;
		}
		Long timeObject = serializeTimes.get(storage.identifier);
		long timeout;
		if (timeObject == null) {
			serializeTimes.put(storage.identifier, SystemClock.elapsedRealtime());
			timeout = storage.timeout;
		} else {
			timeout = Math.min(storage.timeout, timeObject + storage.maxTimeout - SystemClock.elapsedRealtime());
		}
		handler.removeMessages(storage.identifier);
		if (timeout <= 0) {
			enqueueSerialize(storage);
			serializeTimes.remove(storage.identifier);
		} else {
			handler.sendMessageDelayed(handler.obtainMessage(storage.identifier, storage), timeout);
		}
	}

	public void await(Storage storage, boolean async) {
		if (handler.hasMessages(storage.identifier)) {
			serializeTimes.remove(storage.identifier);
			handler.removeMessages(storage.identifier);
			if (async) {
				enqueueSerialize(storage);
			} else {
				performSerialize(storage, storage.onClone());
			}
		}
	}

	private void enqueueSerialize(Storage storage) {
		queue.add(new Pair<>(storage, storage.onClone()));
	}

	@Override
	public boolean handleMessage(Message msg) {
		Storage storage = (Storage) msg.obj;
		serializeTimes.remove(storage.identifier);
		enqueueSerialize(storage);
		return true;
	}
}
