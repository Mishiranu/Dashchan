/*
 * Copyright 2016-2017 Fukurou Mishiranu
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

package com.mishiranu.dashchan.media;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import dalvik.system.PathClassLoader;

import chan.content.ChanManager;

import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;

public class VideoPlayer {
	private static final int MESSAGE_PLAYBACK_COMPLETE = 1;
	private static final int MESSAGE_SIZE_CHANGED = 2;
	private static final int MESSAGE_START_SEEKING = 3;
	private static final int MESSAGE_END_SEEKING = 4;
	private static final int MESSAGE_START_BUFFERING = 5;
	private static final int MESSAGE_END_BUFFERING = 6;
	private static final int MESSAGE_RETRY_SET_POSITION = 7;

	private static final Handler HANDLER = new Handler(Looper.getMainLooper(), msg -> {
		switch (msg.what) {
			case MESSAGE_PLAYBACK_COMPLETE: {
				VideoPlayer player = (VideoPlayer) msg.obj;
				if (player.listener != null && !player.consumed) {
					player.listener.onComplete(player);
				}
				return true;
			}
			case MESSAGE_SIZE_CHANGED: {
				VideoPlayer player = (VideoPlayer) msg.obj;
				player.onDimensionChange();
				return true;
			}
			case MESSAGE_START_SEEKING:
			case MESSAGE_END_SEEKING: {
				boolean seeking = msg.what == MESSAGE_START_SEEKING;
				VideoPlayer player = (VideoPlayer) msg.obj;
				if (player.lastSeeking != seeking) {
					player.lastSeeking = seeking;
					player.onSeekingBufferingStateChange(true, false);
				}
				return true;
			}
			case MESSAGE_START_BUFFERING:
			case MESSAGE_END_BUFFERING: {
				boolean buffering = msg.what == MESSAGE_START_BUFFERING;
				VideoPlayer player = (VideoPlayer) msg.obj;
				if (player.lastBuffering != buffering) {
					player.lastBuffering = buffering;
					player.onSeekingBufferingStateChange(false, true);
				}
				return true;
			}
			case MESSAGE_RETRY_SET_POSITION: {
				Object[] data = (Object[]) msg.obj;
				VideoPlayer player = (VideoPlayer) data[0];
				long position = (long) data[1];
				// Sometimes player hangs during setPosition (ffmpeg seek too far away from real position)
				// I can do nothing better than repeat seeking
				player.setPosition(position);
				return true;
			}
		}
		return false;
	});

	private static final HashMap<String, Method> METHODS = new HashMap<>();
	private static boolean loaded = false;
	private static Class<?> holderClass;

	public static boolean loadLibraries(Context context) {
		synchronized (VideoPlayer.class) {
			if (loaded) {
				return true;
			}
			ChanManager.ExtensionItem extensionItem = ChanManager.getInstance()
					.getLibExtension(ChanManager.EXTENSION_NAME_LIB_WEBM);
			if (extensionItem != null) {
				String dir = extensionItem.applicationInfo.nativeLibraryDir;
				if (dir != null) {
					// System.loadLibrary uses a path from ClassLoader, so I must create one
					// containing all paths to native libraries (client + webm libraries package).
					// Holder class is loaded from this class loader, so all libraries will load correctly.
					ApplicationInfo applicationInfo = context.getApplicationInfo();
					PathClassLoader classLoader = new PathClassLoader(applicationInfo.sourceDir, applicationInfo
							.nativeLibraryDir + File.pathSeparatorChar + dir, Context.class.getClassLoader());
					try {
						// Initialize class (invoke static block)
						holderClass = Class.forName(Holder.class.getName(), true, classLoader);
						loaded = true;
						return true;
					} catch (Exception | LinkageError e) {
						Log.persistent().stack(e);
					}
				}
			}
			return false;
		}
	}

	public static boolean isLoaded() {
		synchronized (VideoPlayer.class) {
			return loaded;
		}
	}

	private static Object invoke(String methodName, Object... args) {
		Method method;
		synchronized (METHODS) {
			method = METHODS.get(methodName);
			if (method == null) {
				Method[] methods = holderClass.getMethods();
				for (int i = 0; i < methods.length; i++) {
					if (methodName.equals(methods[i].getName())) {
						method = methods[i];
						METHODS.put(methodName, method);
						break;
					}
				}
			}
		}
		try {
			return method.invoke(null, args);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static long init(Object nativeBridge, boolean seekAnyFrame) {
		return (long) invoke("init", nativeBridge, seekAnyFrame);
	}

	private static void destroy(long pointer) {
		invoke("destroy", pointer);
	}

	private static int getErrorCode(long pointer) {
		return (int) invoke("getErrorCode", pointer);
	}

	private static void getSummary(long pointer, int[] output) {
		invoke("getSummary", pointer, output);
	}

	private static long getDuration(long pointer) {
		return (long) invoke("getDuration", pointer);
	}

	private static long getPosition(long pointer) {
		return (long) invoke("getPosition", pointer);
	}

	private static void setPosition(long pointer, long position) {
		invoke("setPosition", pointer, position);
	}

	private static void setSurface(long pointer, Surface surface) {
		invoke("setSurface", pointer, surface);
	}

	private static void setPlaying(long pointer, boolean playing) {
		invoke("setPlaying", pointer, playing);
	}

	private static int[] getCurrentFrame(long pointer) {
		return (int[]) invoke("getCurrentFrame", pointer);
	}

	private static String[] getTechnicalInfo(long pointer) {
		return (String[]) invoke("getTechnicalInfo", pointer);
	}

	private final Object inputLock = new Object();
	private final byte[] buffer;
	private InputHolder inputHolder;

	private long pointer;
	private boolean consumed = false;
	private boolean playing = false;
	private final boolean seekAnyFrame;

	private boolean initialized = false;
	private final int[] summaryOutput = new int[3];

	public interface Listener {
		public void onComplete(VideoPlayer player);
		public void onBusyStateChange(VideoPlayer player, boolean busy);
		public void onDimensionChange(VideoPlayer player);
	}

	private Listener listener;
	private boolean lastSeeking = false;
	private volatile boolean lastBuffering = false;

	private interface InputHolder {
		public int read(byte[] buffer, int count) throws IOException;
		public int seek(int position, CachingInputStream.Whence whence) throws IOException;
		public void setAllowReadBeyondBuffer(boolean allow);
		public int getPosition() throws IOException;
		public int getSize();
		public void close();
	}

	private static class FileInputHolder implements InputHolder {
		private final int size;
		private final FileInputStream inputStream;

		public FileInputHolder(File file, FileInputStream inputStream) {
			size = (int) file.length();
			this.inputStream = inputStream;
		}

		@Override
		public int read(byte[] buffer, int count) throws IOException {
			return inputStream.read(buffer, 0, count);
		}

		@Override
		public int seek(int position, CachingInputStream.Whence whence) throws IOException {
			switch (whence) {
				case START: {
					inputStream.getChannel().position(position);
					break;
				}
				case RELATIVE: {
					inputStream.getChannel().position(getPosition() + position);
					break;
				}
				case END: {
					inputStream.getChannel().position(size + position);
					break;
				}
			}
			return getPosition();
		}

		@Override
		public void setAllowReadBeyondBuffer(boolean allow) {}

		@Override
		public int getPosition() throws IOException {
			return (int) inputStream.getChannel().position();
		}

		@Override
		public int getSize() {
			return size;
		}

		@Override
		public void close() {
			IOUtils.close(inputStream);
		}
	}

	private static class CachingInputHolder implements InputHolder {
		private final CachingInputStream inputStream;

		public CachingInputHolder(CachingInputStream inputStream) {
			this.inputStream = inputStream;
		}

		@Override
		public int read(byte[] buffer, int count) throws IOException {
			return inputStream.read(buffer, 0, count);
		}

		@Override
		public int seek(int position, CachingInputStream.Whence whence) {
			return inputStream.seek(position, whence);
		}

		@Override
		public void setAllowReadBeyondBuffer(boolean allow) {
			inputStream.setAllowReadBeyondBuffer(allow);
		}

		@Override
		public int getPosition() {
			return inputStream.getPosition();
		}

		@Override
		public int getSize() {
			return inputStream.getTotalCount();
		}

		@Override
		public void close() {
			IOUtils.close(inputStream);
		}
	}

	public VideoPlayer(boolean seekAnyFrame) {
		buffer = new byte[8192];
		this.seekAnyFrame = seekAnyFrame;
	}

	public void init(CachingInputStream inputStream) throws IOException {
		init(new CachingInputHolder(inputStream));
	}

	public void init(File file) throws IOException {
		init(new FileInputHolder(file, new FileInputStream(file)));
	}

	private void init(InputHolder inputHolder) throws IOException {
		synchronized (inputLock) {
			if (pointer == 0 && !consumed) {
				int priority = Process.getThreadPriority(Process.myTid());
				Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
				try {
					this.inputHolder = inputHolder;
					pointer = init(new NativeBridge(this), seekAnyFrame);
					int errorCode = getErrorCode(pointer);
					if (errorCode != 0) {
						free();
						throw new InitializationException(errorCode);
					}
					initialized = true;
					seekerThread.start();
				} finally {
					Process.setThreadPriority(priority);
				}
			}
		}
	}

	public void replaceStream(File file) throws IOException {
		if (consumed) {
			return;
		}
		replaceStream(new FileInputHolder(file, new FileInputStream(file)));
	}

	private void replaceStream(InputHolder inputHolder) throws IOException {
		synchronized (inputLock) {
			if (!consumed && initialized) {
				int position = this.inputHolder.getPosition();
				inputHolder.seek(position, CachingInputStream.Whence.START);
				this.inputHolder.close();
				this.inputHolder = inputHolder;
			} else {
				inputHolder.close();
			}
		}
	}

	public static class InitializationException extends IOException {
		private InitializationException(int errorCode) {
			super("Can't initialize player: CODE=" + errorCode);
		}
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	private boolean obtainSummary() {
		if (consumed) {
			return false;
		}
		getSummary(pointer, summaryOutput);
		return true;
	}

	public Point getDimensions() {
		if (!obtainSummary()) {
			return null;
		}
		return new Point(summaryOutput[0], summaryOutput[1]);
	}

	public boolean isAudioPresent() {
		if (!obtainSummary()) {
			return false;
		}
		return summaryOutput[2] != 0;
	}

	public long getDuration() {
		if (consumed) {
			return -1L;
		}
		return getDuration(pointer);
	}

	public long getPosition() {
		if (consumed) {
			return -1L;
		}
		long seekToPosition = this.seekToPosition;
		return seekToPosition >= 0L ? seekToPosition : getPosition(pointer);
	}

	private volatile boolean cancelNativeRequests = false;

	private static final long SEEK_TO_WAIT = -1L;

	private long seekToPosition = SEEK_TO_WAIT;
	private final Semaphore seekerMutex = new Semaphore(1);
	private final Thread seekerThread = new Thread(() -> {
		Thread seekerThread = Thread.currentThread();
		while (true) {
			long seekToPosition = SEEK_TO_WAIT;
			synchronized (seekerThread) {
				while (this.seekToPosition == SEEK_TO_WAIT && !consumed) {
					try {
						seekerThread.wait();
					} catch (InterruptedException e) {
						// Uninterruptible wait, ignore exception
					}
				}
				if (consumed) {
					return;
				}
				if (!consumed && initialized && this.seekToPosition >= 0L) {
					seekToPosition = this.seekToPosition;
					seekerMutex.acquireUninterruptibly();
				}
			}
			if (seekToPosition >= 0L) {
				try {
					HANDLER.removeMessages(MESSAGE_END_BUFFERING);
					HANDLER.sendMessageDelayed(HANDLER.obtainMessage(MESSAGE_START_BUFFERING,
							VideoPlayer.this), 200);
					HANDLER.sendMessageDelayed(HANDLER.obtainMessage(MESSAGE_RETRY_SET_POSITION,
							new Object[] {VideoPlayer.this, seekToPosition}), 1000);
					setPosition(pointer, seekToPosition);
					HANDLER.removeMessages(MESSAGE_RETRY_SET_POSITION);
					HANDLER.removeMessages(MESSAGE_START_BUFFERING);
					HANDLER.sendMessageDelayed(HANDLER.obtainMessage(MESSAGE_END_BUFFERING,
							VideoPlayer.this), 100);
				} finally {
					this.seekToPosition = SEEK_TO_WAIT;
					seekerMutex.release();
				}
			}
		}
	});

	private void cancelSetPosition() {
		boolean locked = seekerMutex.tryAcquire();
		if (!locked) {
			cancelNativeRequests = true;
			inputHolder.setAllowReadBeyondBuffer(false);
			seekerMutex.acquireUninterruptibly();
			cancelNativeRequests = false;
			inputHolder.setAllowReadBeyondBuffer(true);
		}
		seekerMutex.release();
	}

	public void setPosition(long position) {
		synchronized (this) {
			if (!consumed && initialized) {
				if (playing) {
					cancelSetPosition();
					seekToPosition = position;
					synchronized (seekerThread) {
						seekerThread.notifyAll();
					}
				} else {
					seekToPosition = position;
				}
			}
		}
	}

	private void onDimensionChange() {
		if (videoView != null) {
			videoView.requestLayout();
		}
		if (listener != null) {
			listener.onDimensionChange(this);
		}
	}

	@SuppressLint("ViewConstructor")
	private static class PlayerTextureView extends TextureView implements TextureView.SurfaceTextureListener {
		private final WeakReference<VideoPlayer> player;

		public PlayerTextureView(Context context, VideoPlayer player) {
			super(context);
			this.player = new WeakReference<>(player);
			setSurfaceTextureListener(this);
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			VideoPlayer player = this.player.get();
			if (player == null) {
				return;
			}
			Point dimensions = player.getDimensions();
			if (dimensions != null && dimensions.x > 0 && dimensions.y > 0) {
				int width = getMeasuredWidth();
				int height = getMeasuredHeight();
				if (dimensions.x * height > dimensions.y * width) {
					height = dimensions.y * width / dimensions.x;
				} else {
					width = dimensions.x * height / dimensions.y;
				}
				setMeasuredDimension(width, height);
			}
		}

		@Override
		public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
			VideoPlayer player = this.player.get();
			if (player == null) {
				return;
			}
			player.setSurface(new Surface(surface));
		}

		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
			VideoPlayer player = this.player.get();
			if (player == null) {
				return true;
			}
			player.setSurface(null);
			return true;
		}

		@Override
		public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

		@Override
		public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
	}

	private View videoView;

	public View getVideoView(Context context) {
		if (videoView == null) {
			videoView = new PlayerTextureView(context, this);
		}
		return videoView;
	}

	private void setSurface(Surface surface) {
		synchronized (this) {
			if (!consumed && initialized) {
				boolean playing = isPlaying();
				if (playing) {
					setPlaying(false);
				}
				setSurface(pointer, surface);
				if (playing) {
					setPlaying(true);
				}
			}
		}
	}

	public void setPlaying(boolean playing) {
		synchronized (this) {
			if (!consumed && initialized && this.playing != playing) {
				long seekToPosition = this.seekToPosition;
				cancelSetPosition();
				this.playing = playing;
				if (playing) {
					setPlaying(pointer, true);
					if (seekToPosition >= 0L) {
						setPosition(seekToPosition);
					}
				} else {
					setPlaying(pointer, false);
					this.seekToPosition = seekToPosition; // Will be reset in cancelSetPosition
				}
			}
		}
	}

	public boolean isPlaying() {
		synchronized (this) {
			return !consumed && initialized && playing;
		}
	}

	public Bitmap getCurrentFrame() {
		synchronized (this) {
			int[] frame = consumed ? null : getCurrentFrame(pointer);
			if (frame != null) {
				Point dimensions = getDimensions();
				try {
					return Bitmap.createBitmap(frame, dimensions.x, dimensions.y, Bitmap.Config.ARGB_8888);
				} catch (Exception e) {
					// Ignore exception
				}
			}
			return null;
		}
	}

	public HashMap<String, String> getTechnicalInfo() {
		synchronized (this) {
			HashMap<String, String> result = new HashMap<>();
			if (!consumed && initialized) {
				String[] array = getTechnicalInfo(pointer);
				if (array != null) {
					for (int i = 0; i < array.length; i += 2) {
						String key = array[i];
						String value = array[i + 1];
						if (key != null && value != null) {
							result.put(key, value);
						}
					}
				}
			}
			return result;
		}
	}

	public void free() {
		synchronized (this) {
			if (!consumed && pointer != 0) {
				consumed = true;
				if (inputHolder != null) {
					inputHolder.close();
				}
				cancelSetPosition();
				synchronized (seekerThread) {
					seekerThread.notifyAll();
				}
				destroy(pointer);
			}
		}
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			free();
		} finally {
			super.finalize();
		}
	}

	private void onSeekingBufferingStateChange(boolean seekingChange, boolean bufferingChange) {
		if (seekingChange && lastBuffering) {
			return;
		}
		if (bufferingChange && lastSeeking) {
			return;
		}
		if (listener != null && !consumed) {
			listener.onBusyStateChange(this, lastSeeking || lastBuffering);
		}
	}

	@SuppressWarnings("unused")
	private static class NativeBridge {
		private final WeakReference<VideoPlayer> player;

		public NativeBridge(VideoPlayer player) {
			this.player = new WeakReference<>(player);
		}

		public byte[] getBuffer() {
			VideoPlayer player = this.player.get();
			return player != null ? player.buffer : null;
		}

		public int onRead(int size) {
			VideoPlayer player = this.player.get();
			if (player == null) {
				return -1;
			}
			synchronized (player.inputLock) {
				if (player.inputHolder == null || player.cancelNativeRequests) {
					return -1;
				}
				size = Math.min(size, player.buffer.length);
				try {
					return player.inputHolder.read(player.buffer, size);
				} catch (IOException e) {
					return -1;
				}
			}
		}

		public long onSeek(long position, int whence) {
			VideoPlayer player = this.player.get();
			if (player == null) {
				return -1;
			}
			synchronized (player.inputLock) {
				if (player.inputHolder == null || player.cancelNativeRequests) {
					return -1;
				}
				if (whence == 0x10000) {
					// AVSEEK_SIZE == 0x10000
					return player.inputHolder.getSize();
				}
				try {
					int seekPosition = position > Integer.MAX_VALUE ? Integer.MAX_VALUE
							: position < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) position;
					CachingInputStream.Whence seekWhence = whence == 2 ? CachingInputStream.Whence.END : whence == 1
							? CachingInputStream.Whence.RELATIVE : CachingInputStream.Whence.START;
					return player.inputHolder.seek(seekPosition, seekWhence);
				} catch (IOException e) {
					return -1;
				}
			}
		}

		public void onMessage(int what) {
			VideoPlayer player = this.player.get();
			if (player != null) {
				switch (what) {
					case 1: {
						// BRIDGE_MESSAGE_PLAYBACK_COMPLETE
						HANDLER.sendMessageDelayed(HANDLER.obtainMessage(MESSAGE_PLAYBACK_COMPLETE, player), 200);
						break;
					}
					case 2: {
						// BRIDGE_MESSAGE_SIZE_CHANGED
						HANDLER.obtainMessage(MESSAGE_SIZE_CHANGED, player).sendToTarget();
						break;
					}
					case 3: {
						// BRIDGE_MESSAGE_START_SEEKING
						HANDLER.removeMessages(MESSAGE_END_SEEKING);
						if (player.lastBuffering) {
							HANDLER.obtainMessage(MESSAGE_START_SEEKING, player).sendToTarget();
						} else {
							HANDLER.sendMessageDelayed(HANDLER.obtainMessage(MESSAGE_START_SEEKING, player), 500);
						}
						break;
					}
					case 4: {
						// BRIDGE_MESSAGE_END_SEEKING
						HANDLER.removeMessages(MESSAGE_START_SEEKING);
						HANDLER.obtainMessage(MESSAGE_END_SEEKING, player).sendToTarget();
						break;
					}
				}
			}
		}
	}

	@SuppressWarnings("unused")
	private static class Holder {
		public static native long init(Object nativeBridge, boolean seekAnyFrame);
		public static native void destroy(long pointer);

		public static native int getErrorCode(long pointer);
		public static native void getSummary(long pointer, int[] output);

		public static native long getDuration(long pointer);
		public static native long getPosition(long pointer);
		public static native void setPosition(long pointer, long position);

		public static native void setSurface(long pointer, Surface surface);
		public static native void setPlaying(long pointer, boolean playing);

		public static native int[] getCurrentFrame(long pointer);
		public static native String[] getTechnicalInfo(long pointer);

		static {
			System.loadLibrary("avutil");
			System.loadLibrary("swscale");
			System.loadLibrary("swresample");
			System.loadLibrary("avcodec");
			System.loadLibrary("avformat");
			System.loadLibrary("yuv");
			System.loadLibrary("player");
		}
	}
}
