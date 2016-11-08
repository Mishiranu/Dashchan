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
import android.os.Message;
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

	private static final Handler HANDLER = new Handler(Looper.getMainLooper(), new Handler.Callback() {
		@Override
		public boolean handleMessage(Message msg) {
			switch (msg.what) {
				case MESSAGE_PLAYBACK_COMPLETE: {
					VideoPlayer player = (VideoPlayer) msg.obj;
					if (player.mListener != null && !player.mConsumed) {
						player.mListener.onComplete(player);
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
					if (player.mLastSeeking != seeking) {
						player.mLastSeeking = seeking;
						player.onSeekingBufferingStateChange(true, false);
					}
					return true;
				}
				case MESSAGE_START_BUFFERING:
				case MESSAGE_END_BUFFERING: {
					boolean buffering = msg.what == MESSAGE_START_BUFFERING;
					VideoPlayer player = (VideoPlayer) msg.obj;
					if (player.mLastBuffering != buffering) {
						player.mLastBuffering = buffering;
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
		}
	});

	private static final HashMap<String, Method> METHODS = new HashMap<>();
	private static boolean sLoaded = false;
	private static Class<?> sHolderClass;

	public static boolean loadLibraries(Context context) {
		synchronized (VideoPlayer.class) {
			if (sLoaded) {
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
						sHolderClass = Class.forName(Holder.class.getName(), true, classLoader);
						sLoaded = true;
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
			return sLoaded;
		}
	}

	private static Object invoke(String methodName, Object... args) {
		Method method;
		synchronized (METHODS) {
			method = METHODS.get(methodName);
			if (method == null) {
				Method[] methods = sHolderClass.getMethods();
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

	private final Object mInputLock = new Object();
	private final byte[] mBuffer;
	private InputHolder mInputHolder;

	private long mPointer;
	private boolean mConsumed = false;
	private boolean mPlaying = false;
	private final boolean mSeekAnyFrame;

	private boolean mInitialized = false;
	private final int[] mSummaryOutput = new int[3];

	public interface Listener {
		public void onComplete(VideoPlayer player);
		public void onBusyStateChange(VideoPlayer player, boolean busy);
		public void onDimensionChange(VideoPlayer player);
	}

	private Listener mListener;
	private boolean mLastSeeking = false;
	private volatile boolean mLastBuffering = false;

	private interface InputHolder {
		public int read(byte[] buffer, int count) throws IOException;
		public int seek(int position, CachingInputStream.Whence whence) throws IOException;
		public void setAllowReadBeyondBuffer(boolean allow);
		public int getPosition() throws IOException;
		public int getSize();
		public void close();
	}

	private static class FileInputHolder implements InputHolder {
		private final int mSize;
		private final FileInputStream mInputStream;

		public FileInputHolder(File file, FileInputStream inputStream) {
			mSize = (int) file.length();
			mInputStream = inputStream;
		}

		@Override
		public int read(byte[] buffer, int count) throws IOException {
			return mInputStream.read(buffer, 0, count);
		}

		@Override
		public int seek(int position, CachingInputStream.Whence whence) throws IOException {
			switch (whence) {
				case START: {
					mInputStream.getChannel().position(position);
					break;
				}
				case RELATIVE: {
					mInputStream.getChannel().position(getPosition() + position);
					break;
				}
				case END: {
					mInputStream.getChannel().position(mSize + position);
					break;
				}
			}
			return getPosition();
		}

		@Override
		public void setAllowReadBeyondBuffer(boolean allow) {}

		@Override
		public int getPosition() throws IOException {
			return (int) mInputStream.getChannel().position();
		}

		@Override
		public int getSize() {
			return mSize;
		}

		@Override
		public void close() {
			IOUtils.close(mInputStream);
		}
	}

	private static class CachingInputHolder implements InputHolder {
		private final CachingInputStream mInputStream;

		public CachingInputHolder(CachingInputStream inputStream) {
			mInputStream = inputStream;
		}

		@Override
		public int read(byte[] buffer, int count) throws IOException {
			return mInputStream.read(buffer, 0, count);
		}

		@Override
		public int seek(int position, CachingInputStream.Whence whence) {
			return mInputStream.seek(position, whence);
		}

		@Override
		public void setAllowReadBeyondBuffer(boolean allow) {
			mInputStream.setAllowReadBeyondBuffer(allow);
		}

		@Override
		public int getPosition() {
			return mInputStream.getPosition();
		}

		@Override
		public int getSize() {
			return mInputStream.getTotalCount();
		}

		@Override
		public void close() {
			IOUtils.close(mInputStream);
		}
	}

	public VideoPlayer(boolean seekAnyFrame) {
		mBuffer = new byte[8192];
		mSeekAnyFrame = seekAnyFrame;
	}

	public void init(CachingInputStream inputStream) throws IOException {
		init(new CachingInputHolder(inputStream));
	}

	public void init(File file) throws IOException {
		init(new FileInputHolder(file, new FileInputStream(file)));
	}

	private void init(InputHolder inputHolder) throws IOException {
		synchronized (mInputLock) {
			if (mPointer == 0 && !mConsumed) {
				int priority = Process.getThreadPriority(Process.myTid());
				Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
				try {
					mInputHolder = inputHolder;
					mPointer = init(new NativeBridge(this), mSeekAnyFrame);
					int errorCode = getErrorCode(mPointer);
					if (errorCode != 0) {
						free();
						throw new IOException("Can't initialize player: CODE=" + errorCode);
					}
					mInitialized = true;
					mSeekerThread.start();
				} finally {
					Process.setThreadPriority(priority);
				}
			}
		}
	}

	public void replaceStream(File file) throws IOException {
		if (mConsumed) {
			return;
		}
		replaceStream(new FileInputHolder(file, new FileInputStream(file)));
	}

	private void replaceStream(InputHolder inputHolder) throws IOException {
		synchronized (mInputLock) {
			if (!mConsumed && mInitialized) {
				int position = mInputHolder.getPosition();
				inputHolder.seek(position, CachingInputStream.Whence.START);
				mInputHolder.close();
				mInputHolder = inputHolder;
			} else {
				inputHolder.close();
			}
		}
	}

	public void setListener(Listener listener) {
		mListener = listener;
	}

	private boolean obtainSummary() {
		if (mConsumed) {
			return false;
		}
		getSummary(mPointer, mSummaryOutput);
		return true;
	}

	public Point getDimensions() {
		if (!obtainSummary()) {
			return null;
		}
		return new Point(mSummaryOutput[0], mSummaryOutput[1]);
	}

	public boolean isAudioPresent() {
		if (!obtainSummary()) {
			return false;
		}
		return mSummaryOutput[2] != 0;
	}

	public long getDuration() {
		if (mConsumed) {
			return -1L;
		}
		return getDuration(mPointer);
	}

	public long getPosition() {
		if (mConsumed) {
			return -1L;
		}
		long seekToPosition = mSeekToPosition;
		return seekToPosition >= 0L ? seekToPosition : getPosition(mPointer);
	}

	private volatile boolean mCancelNativeRequests = false;

	private static final long SEEK_TO_WAIT = -1L;

	private long mSeekToPosition = SEEK_TO_WAIT;
	private final Semaphore mSeekerMutex = new Semaphore(1);
	private final Thread mSeekerThread = new Thread(() -> {
		Thread seekerThread = Thread.currentThread();
		while (true) {
			long seekToPosition = SEEK_TO_WAIT;
			synchronized (seekerThread) {
				while (mSeekToPosition == SEEK_TO_WAIT && !mConsumed) {
					try {
						seekerThread.wait();
					} catch (InterruptedException e) {
						// Ignore
					}
				}
				if (mConsumed) {
					return;
				}
				if (!mConsumed && mInitialized && mSeekToPosition >= 0L) {
					seekToPosition = mSeekToPosition;
					mSeekerMutex.acquireUninterruptibly();
				}
			}
			if (seekToPosition >= 0L) {
				try {
					HANDLER.removeMessages(MESSAGE_END_BUFFERING);
					HANDLER.sendMessageDelayed(HANDLER.obtainMessage(MESSAGE_START_BUFFERING,
							VideoPlayer.this), 200);
					HANDLER.sendMessageDelayed(HANDLER.obtainMessage(MESSAGE_RETRY_SET_POSITION,
							new Object[] {VideoPlayer.this, seekToPosition}), 1000);
					setPosition(mPointer, seekToPosition);
					HANDLER.removeMessages(MESSAGE_RETRY_SET_POSITION);
					HANDLER.removeMessages(MESSAGE_START_BUFFERING);
					HANDLER.sendMessageDelayed(HANDLER.obtainMessage(MESSAGE_END_BUFFERING,
							VideoPlayer.this), 100);
				} finally {
					mSeekToPosition = SEEK_TO_WAIT;
					mSeekerMutex.release();
				}
			}
		}
	});

	private void cancelSetPosition() {
		boolean locked = mSeekerMutex.tryAcquire();
		if (!locked) {
			mCancelNativeRequests = true;
			mInputHolder.setAllowReadBeyondBuffer(false);
			mSeekerMutex.acquireUninterruptibly();
			mCancelNativeRequests = false;
			mInputHolder.setAllowReadBeyondBuffer(true);
		}
		mSeekerMutex.release();
	}

	public void setPosition(long position) {
		synchronized (this) {
			if (!mConsumed && mInitialized) {
				if (mPlaying) {
					cancelSetPosition();
					mSeekToPosition = position;
					synchronized (mSeekerThread) {
						mSeekerThread.notifyAll();
					}
				} else {
					mSeekToPosition = position;
				}
			}
		}
	}

	private void onDimensionChange() {
		if (mVideoView != null) {
			mVideoView.requestLayout();
		}
		if (mListener != null) {
			mListener.onDimensionChange(this);
		}
	}

	@SuppressLint("ViewConstructor")
	private static class PlayerTextureView extends TextureView implements TextureView.SurfaceTextureListener {
		private final WeakReference<VideoPlayer> mPlayer;

		public PlayerTextureView(Context context, VideoPlayer player) {
			super(context);
			mPlayer = new WeakReference<>(player);
			setSurfaceTextureListener(this);
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			VideoPlayer player = mPlayer.get();
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
			VideoPlayer player = mPlayer.get();
			if (player == null) {
				return;
			}
			player.setSurface(new Surface(surface));
		}

		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
			VideoPlayer player = mPlayer.get();
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

	private View mVideoView;

	public View getVideoView(Context context) {
		if (mVideoView == null) {
			mVideoView = new PlayerTextureView(context, this);
		}
		return mVideoView;
	}

	private void setSurface(Surface surface) {
		synchronized (this) {
			if (!mConsumed && mInitialized) {
				boolean playing = isPlaying();
				if (playing) {
					setPlaying(false);
				}
				setSurface(mPointer, surface);
				if (playing) {
					setPlaying(true);
				}
			}
		}
	}

	public void setPlaying(boolean playing) {
		synchronized (this) {
			if (!mConsumed && mInitialized && mPlaying != playing) {
				long seekToPosition = mSeekToPosition;
				cancelSetPosition();
				mPlaying = playing;
				if (playing) {
					setPlaying(mPointer, true);
					if (seekToPosition >= 0L) {
						setPosition(seekToPosition);
					}
				} else {
					setPlaying(mPointer, false);
					mSeekToPosition = seekToPosition; // Will be reset in cancelSetPosition
				}
			}
		}
	}

	public boolean isPlaying() {
		synchronized (this) {
			return !mConsumed && mInitialized && mPlaying;
		}
	}

	public Bitmap getCurrentFrame() {
		synchronized (this) {
			int[] frame = mConsumed ? null : getCurrentFrame(mPointer);
			if (frame != null) {
				Point dimensions = getDimensions();
				try {
					return Bitmap.createBitmap(frame, dimensions.x, dimensions.y, Bitmap.Config.ARGB_8888);
				} catch (Exception e) {
					// Ignore
				}
			}
			return null;
		}
	}

	public HashMap<String, String> getTechnicalInfo() {
		synchronized (this) {
			HashMap<String, String> result = new HashMap<>();
			if (!mConsumed && mInitialized) {
				String[] array = getTechnicalInfo(mPointer);
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
			if (!mConsumed && mPointer != 0) {
				mConsumed = true;
				if (mInputHolder != null) {
					mInputHolder.close();
				}
				cancelSetPosition();
				synchronized (mSeekerThread) {
					mSeekerThread.notifyAll();
				}
				destroy(mPointer);
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
		if (seekingChange && mLastBuffering) {
			return;
		}
		if (bufferingChange && mLastSeeking) {
			return;
		}
		if (mListener != null && !mConsumed) {
			mListener.onBusyStateChange(this, mLastSeeking || mLastBuffering);
		}
	}

	@SuppressWarnings("unused")
	private static class NativeBridge {
		private final WeakReference<VideoPlayer> mPlayer;

		public NativeBridge(VideoPlayer player) {
			mPlayer = new WeakReference<>(player);
		}

		public byte[] getBuffer() {
			VideoPlayer player = mPlayer.get();
			return player != null ? player.mBuffer : null;
		}

		public int onRead(int size) {
			VideoPlayer player = mPlayer.get();
			if (player == null) {
				return -1;
			}
			synchronized (player.mInputLock) {
				if (player.mInputHolder == null || player.mCancelNativeRequests) {
					return -1;
				}
				size = Math.min(size, player.mBuffer.length);
				try {
					return player.mInputHolder.read(player.mBuffer, size);
				} catch (IOException e) {
					return -1;
				}
			}
		}

		public long onSeek(long position, int whence) {
			VideoPlayer player = mPlayer.get();
			if (player == null) {
				return -1;
			}
			synchronized (player.mInputLock) {
				if (player.mInputHolder == null || player.mCancelNativeRequests) {
					return -1;
				}
				if (whence == 0x10000) {
					// AVSEEK_SIZE == 0x10000
					return player.mInputHolder.getSize();
				}
				try {
					return player.mInputHolder.seek((int) position, whence == 2 ? CachingInputStream.Whence.END
							: whence == 1 ? CachingInputStream.Whence.RELATIVE : CachingInputStream.Whence.START);
				} catch (IOException e) {
					return -1;
				}
			}
		}

		public void onMessage(int what) {
			VideoPlayer player = mPlayer.get();
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
						if (player.mLastBuffering) {
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