package com.mishiranu.dashchan.content.storage;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.SparseArray;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.LinkedBlockingQueue;
import org.json.JSONException;
import org.json.JSONObject;

public class StorageManager implements Handler.Callback, Runnable {
	private static final StorageManager INSTANCE = new StorageManager();

	public static StorageManager getInstance() {
		return INSTANCE;
	}

	private StorageManager() {
		new Thread(this, "StorageManagerWorker").start();
	}

	private final Handler handler = new Handler(Looper.getMainLooper(), this);
	private final LinkedBlockingQueue<Enqueued<?>> queue = new LinkedBlockingQueue<>();

	private int nextIdentifier = 1;

	@Override
	public void run() {
		while (true) {
			Enqueued<?> enqueued;
			try {
				enqueued = queue.take();
			} catch (InterruptedException e) {
				return;
			}
			performSerialize(enqueued);
		}
	}

	private <Data> void performSerialize(Enqueued<Data> enqueued) {
		synchronized (enqueued.storage.lock) {
			File file = getFile(enqueued.storage);
			File backupFile = getBackupFile(enqueued.storage);
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
				enqueued.storage.onWrite(enqueued.data, output);
				output.flush();
				output.getFD().sync();
				success = true;
			} catch (IOException e) {
				Log.persistent().stack(e);
			} finally {
				success &= IOUtils.close(output);
				if (success) {
					backupFile.delete();
				} else if (file.exists() && !file.delete()) {
					Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES,
							"Can't delete partially written", file);
				}
			}
		}
	}

	private static class Enqueued<Data> {
		public final Storage<Data> storage;
		public final Data data;

		public Enqueued(Storage<Data> storage) {
			this.storage = storage;
			data = storage.onClone();
		}
	}

	public static abstract class Storage<Data> {
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

		protected void startRead() {
			try (InputStream input = INSTANCE.open(this)) {
				onRead(input);
			} catch (FileNotFoundException e) {
				// Ignore exception
			} catch (IOException e) {
				Log.persistent().stack(e);
			}
		}

		public final File getFile() {
			return INSTANCE.getFile(this);
		}

		public final void serialize() {
			INSTANCE.serialize(this);
		}

		public final void await(boolean async) {
			INSTANCE.await(this, async);
		}

		public abstract Data onClone();
		public abstract void onRead(InputStream input) throws IOException;
		public abstract void onWrite(Data data, OutputStream output) throws IOException;
	}

	public static abstract class JsonOrgStorage<Data> extends Storage<Data> {
		@SuppressWarnings("CharsetObjectCanBeUsed")
		private static final Charset CHARSET = Charset.forName("UTF-8");

		public JsonOrgStorage(String name, int timeout, int maxTimeout) {
			super(name, timeout, maxTimeout);
		}

		@Override
		public final void onRead(InputStream input) throws IOException {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			IOUtils.copyStream(input, output);
			try {
				onDeserialize(new JSONObject(new String(output.toByteArray(), CHARSET)));
			} catch (JSONException e) {
				// Ignore exception
			}
		}

		@Override
		public final void onWrite(Data data, OutputStream output) throws IOException {
			JSONObject jsonObject;
			try {
				jsonObject = onSerialize(data);
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
			if (jsonObject != null) {
				output.write(jsonObject.toString().getBytes(CHARSET));
			}
		}

		public abstract void onDeserialize(JSONObject jsonObject) throws JSONException;
		public abstract JSONObject onSerialize(Data data) throws JSONException;
	}

	private File getDirectory() {
		File file = new File(MainApplication.getInstance().getFilesDir(), "storage");
		file.mkdirs();
		return file;
	}

	private File getBackupFile(Storage<?> storage) {
		return new File(storage.getFile().getAbsolutePath() + "-backup");
	}

	private File getFile(String name) {
		return new File(getDirectory(), name + ".json");
	}

	private File getFile(Storage<?> storage) {
		return getFile(storage.name);
	}

	private InputStream open(Storage<?> storage) throws IOException {
		File file = getFile(storage);
		File backupFile = getBackupFile(storage);
		if (backupFile.exists()) {
			file.delete();
			backupFile.renameTo(file);
		}
		return new FileInputStream(file);
	}

	private final SparseArray<Long> serializeTimes = new SparseArray<>();

	private void serialize(Storage<?> storage) {
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

	public void await(Storage<?> storage, boolean async) {
		if (handler.hasMessages(storage.identifier)) {
			serializeTimes.remove(storage.identifier);
			handler.removeMessages(storage.identifier);
			if (async) {
				enqueueSerialize(storage);
			} else {
				performSerialize(new Enqueued<>(storage));
			}
		}
	}

	private void enqueueSerialize(Storage<?> storage) {
		queue.add(new Enqueued<>(storage));
	}

	@Override
	public boolean handleMessage(Message msg) {
		Storage<?> storage = (Storage<?>) msg.obj;
		serializeTimes.remove(storage.identifier);
		enqueueSerialize(storage);
		return true;
	}
}
