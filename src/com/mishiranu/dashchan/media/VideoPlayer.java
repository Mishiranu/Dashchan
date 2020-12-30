package com.mishiranu.dashchan.media;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.util.Pair;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import chan.content.ChanManager;
import chan.util.StringUtils;
import dalvik.system.PathClassLoader;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class VideoPlayer {
	private static boolean loaded = false;
	private static HolderInterface holder;

	public static Pair<Boolean, String> loadLibraries(Context context) {
		synchronized (VideoPlayer.class) {
			if (loaded) {
				return new Pair<>(true, null);
			}
			ChanManager.ExtensionItem extensionItem = ChanManager.getInstance()
					.getLibraryExtension(ChanManager.EXTENSION_NAME_LIB_WEBM);
			if (extensionItem != null) {
				String dir = extensionItem.getNativeLibraryDir();
				if (dir != null) {
					// System.loadLibrary uses a path from ClassLoader, so I must create one
					// containing all paths to native libraries (client + webm libraries package).
					// Holder class is loaded from this class loader, so all libraries will load correctly.
					ApplicationInfo applicationInfo = context.getApplicationInfo();
					PathClassLoader classLoader = new PathClassLoader(applicationInfo.sourceDir, applicationInfo
							.nativeLibraryDir + File.pathSeparatorChar + dir, Context.class.getClassLoader());
					try {
						// Initialize class (invoke static block)
						Class<?> holderClass = Class.forName(Holder.class.getName(), true, classLoader);
						Field instanceField = holderClass.getDeclaredField("INSTANCE");
						instanceField.setAccessible(true);
						InvocationHandler handler = (InvocationHandler) instanceField.get(null);
						holder = (HolderInterface) Proxy.newProxyInstance(VideoPlayer.class.getClassLoader(),
								new Class[] { HolderInterface.class }, handler);
						loaded = true;
						return new Pair<>(true, null);
					} catch (Exception | LinkageError e) {
						e.printStackTrace();
						String message = StringUtils.emptyIfNull(e.getMessage());
						message = shortenMessagePath(context.getPackageCodePath(), message);
						message = shortenMessagePath(dir, message);
						if (message.endsWith("...")) {
							message = message.substring(0, message.length() - 3);
						} else if (message.endsWith(".")) {
							message = message.substring(0, message.length() - 1);
						}
						return new Pair<>(false, message);
					}
				}
			}
			return new Pair<>(false, null);
		}
	}

	private static String shortenMessagePath(String path, String message) {
		if (path != null) {
			File file = new File(path);
			if (!file.isDirectory()) {
				file = file.getParentFile();
			}
			return message.replace(file.getPath() + "/", "");
		} else {
			return message;
		}
	}

	public static boolean isLoaded() {
		synchronized (VideoPlayer.class) {
			return loaded;
		}
	}

	public interface Listener {
		void onComplete(VideoPlayer player);
		void onBusyStateChange(VideoPlayer player, boolean busy);
		void onDimensionChange(VideoPlayer player);
	}

	public interface RangeCallback {
		void requestPartFromPosition(long start);
	}

	private static class SessionData {
		public final long pointer;
		public final RangeCallback rangeCallback;

		public long request;
		public long size;
		public long total = -1;
		public long partStart;
		public long partEnd;

		public SessionData(long pointer, RangeCallback rangeCallback) {
			this.pointer = pointer;
			this.rangeCallback = rangeCallback;
		}
	}

	private final Listener listener;
	private final boolean seekAnyFrame;

	private SessionData sessionData;
	private SessionData initData;

	private boolean consumed = false;
	private boolean playing = false;

	private boolean lastSeeking = false;
	private volatile boolean lastBuffering = false;

	public VideoPlayer(Listener listener, boolean seekAnyFrame) {
		this.listener = listener;
		this.seekAnyFrame = seekAnyFrame;
	}

	public void init(File file, RangeCallback rangeCallback) throws IOException {
		int priority = Process.getThreadPriority(Process.myTid());
		Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
		try {
			SessionData initData = null;
			synchronized (this) {
				if (sessionData == null && initData == null && !consumed) {
					long initPointer;
					try (ParcelFileDescriptor descriptor = ParcelFileDescriptor
							.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) {
						int fd = descriptor.detachFd();
						initPointer = holder.preInit(fd);
					}
					if (rangeCallback == null) {
						long length = file.length();
						holder.setRange(initPointer, 0, length, length);
					}
					initData = new SessionData(initPointer, rangeCallback);
					this.initData = initData;
				}
			}
			if (initData != null) {
				holder.init(initData.pointer, new NativeBridge(this), seekAnyFrame);
				synchronized (this) {
					this.initData = null;
					if (consumed) {
						holder.destroy(initData.pointer, false);
					} else {
						int errorCode = holder.getErrorCode(initData.pointer);
						if (errorCode != 0) {
							destroyInternal(initData);
							throw new InitializationException(errorCode);
						}
						sessionData = initData;
						seekerThread.start();
					}
				}
			}
		} finally {
			Process.setThreadPriority(priority);
		}
	}

	public static class InitializationException extends IOException {
		private InitializationException(int errorCode) {
			super("Can't initialize player: CODE=" + errorCode);
		}
	}

	private SessionData getInitOrSessionDataLocked() {
		return sessionData != null ? sessionData : initData;
	}

	public void setDownloadRange(long size, long total) {
		synchronized (this) {
			SessionData sessionData = getInitOrSessionDataLocked();
			if (sessionData != null) {
				sessionData.size = size;
				sessionData.total = total;
				if (size > sessionData.request || sessionData.request <= 0) {
					if (sessionData.request > 0) {
						sessionData.request = 0;
						sessionData.rangeCallback.requestPartFromPosition(0);
					}
					setRangeLocked(0, size, total);
				}
			}
		}
	}

	public void setPartRange(long start, long end) {
		synchronized (this) {
			SessionData sessionData = getInitOrSessionDataLocked();
			if (sessionData != null && sessionData.rangeCallback != null &&
					sessionData.request >= start && sessionData.request <= end) {
				sessionData.partStart = start;
				sessionData.partEnd = end;
				// Shift request position for higher priority
				sessionData.request = end;
				setRangeLocked(start, end, sessionData.total);
			}
		}
	}

	private void requestRange(long start) {
		synchronized (this) {
			SessionData sessionData = getInitOrSessionDataLocked();
			if (!consumed && sessionData != null && sessionData.rangeCallback != null) {
				int awaitLength = 200000;
				boolean request = true;
				if (start > 0 && sessionData.request > 0 && (start == sessionData.request ||
						start >= sessionData.partStart && start <= sessionData.partEnd + awaitLength)) {
					// Use current part range request
					request = false;
				}
				if (request && start <= sessionData.size + awaitLength) {
					// Use download range
					request = false;
					if (sessionData.request > 0) {
						// Cancel part range request
						sessionData.request = 0;
						sessionData.rangeCallback.requestPartFromPosition(0);
					}
					setRangeLocked(0, sessionData.size, sessionData.total);
				}
				if (request) {
					sessionData.request = start;
					sessionData.partStart = 0;
					sessionData.partEnd = 0;
					sessionData.rangeCallback.requestPartFromPosition(start);
				}
			}
		}
	}

	private void setRangeLocked(long start, long end, long total) {
		if (!consumed) {
			SessionData sessionData = getInitOrSessionDataLocked();
			if (sessionData != null) {
				holder.setRange(sessionData.pointer, start, end, total);
			}
		}
	}

	private boolean isInitialized() {
		return !consumed && sessionData != null;
	}

	private final int[] summaryOutput = new int[3];

	private boolean obtainSummary() {
		if (isInitialized()) {
			holder.getSummary(sessionData.pointer, summaryOutput);
			return true;
		}
		return false;
	}

	public Point getDimensions() {
		return obtainSummary() ? new Point(summaryOutput[0], summaryOutput[1]) : null;
	}

	public boolean isAudioPresent() {
		return obtainSummary() ? summaryOutput[2] != 0 : null;
	}

	public long getDuration() {
		return isInitialized() ? holder.getDuration(sessionData.pointer) : 0;
	}

	public long getPosition() {
		SeekToPosition seekToPosition = this.seekToPosition;
		return isInitialized() ? seekToPosition != null ? seekToPosition.position
				: holder.getPosition(sessionData.pointer) : 0;
	}

	private void cancelSetPositionLocked() {
		boolean locked = seekerMutex.tryAcquire();
		if (!locked) {
			holder.setCancelSeek(sessionData.pointer, true);
			seekerMutex.acquireUninterruptibly();
			holder.setCancelSeek(sessionData.pointer, false);
		}
		seekerMutex.release();
	}

	public void setPosition(long position) {
		synchronized (this) {
			if (isInitialized()) {
				if (playing) {
					cancelSetPositionLocked();
				}
				synchronized (seekerThread) {
					seekToPosition = new SeekToPosition(position, playing);
					seekerThread.notifyAll();
				}
			}
		}
	}

	private void onComplete() {
		if (listener != null && !consumed) {
			listener.onComplete(this);
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
			if (isInitialized()) {
				boolean playing = isPlaying();
				if (playing) {
					setPlaying(false);
				}
				holder.setSurface(sessionData.pointer, surface);
				if (playing) {
					setPlaying(true);
				}
			}
		}
	}

	public void setPlaying(boolean playing) {
		synchronized (this) {
			if (isInitialized() && this.playing != playing) {
				SeekToPosition seekToPosition = this.seekToPosition;
				cancelSetPositionLocked();
				this.playing = playing;
				if (playing) {
					holder.setPlaying(sessionData.pointer, true);
					if (seekToPosition != null) {
						setPosition(seekToPosition.position);
					}
				} else {
					holder.setPlaying(sessionData.pointer, false);
					// Restore value after cancelSetPosition
					this.seekToPosition = seekToPosition != null
							? new SeekToPosition(seekToPosition.position, false) : null;
				}
			}
		}
	}

	public boolean isPlaying() {
		synchronized (this) {
			return isInitialized() && playing;
		}
	}

	private final int[] dimensionsOutput = new int[2];

	public Bitmap getCurrentFrame() {
		synchronized (this) {
			if (isInitialized()) {
				int[] dimensions = dimensionsOutput;
				int[] frame = holder.getCurrentFrame(sessionData.pointer, dimensions);
				if (frame != null) {
					try {
						return Bitmap.createBitmap(frame, dimensions[0], dimensions[1], Bitmap.Config.ARGB_8888);
					} catch (Exception e) {
						// Ignore exception
					}
				}
			}
			return null;
		}
	}

	public Map<String, String> getMetadata() {
		synchronized (this) {
			if (isInitialized()) {
				String[] array = holder.getMetadata(sessionData.pointer);
				if (array != null) {
					HashMap<String, String> result = new HashMap<>();
					for (int i = 0; i < array.length; i += 2) {
						String key = array[i];
						String value = array[i + 1];
						if (key != null && value != null) {
							result.put(key, value);
						}
					}
					return result;
				}
			}
			return Collections.emptyMap();
		}
	}

	public void destroy() {
		destroyInternal(null);
	}

	private void destroyInternal(SessionData preInitSessionData) {
		synchronized (this) {
			if (!consumed) {
				consumed = true;
				if (initData != null) {
					holder.destroy(initData.pointer, true);
				}
				SessionData sessionData = preInitSessionData != null ? preInitSessionData : this.sessionData;
				if (sessionData != null) {
					cancelSetPositionLocked();
					synchronized (seekerThread) {
						seekerThread.notifyAll();
					}
					holder.destroy(sessionData.pointer, false);
				}
			}
		}
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			destroy();
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

	private enum Message {PLAYBACK_COMPLETE, SIZE_CHANGED, START_SEEKING, END_SEEKING, START_BUFFERING, END_BUFFERING,
		RETRY_SET_POSITION, REQUEST_RANGE}

	private final Handler handler = new Handler(Looper.getMainLooper(), msg -> {
		switch (Message.values()[msg.what]) {
			case PLAYBACK_COMPLETE: {
				onComplete();
				return true;
			}
			case SIZE_CHANGED: {
				onDimensionChange();
				return true;
			}
			case START_SEEKING:
			case END_SEEKING: {
				boolean seeking = msg.what == Message.START_SEEKING.ordinal();
				if (lastSeeking != seeking) {
					lastSeeking = seeking;
					onSeekingBufferingStateChange(true, false);
				}
				return true;
			}
			case START_BUFFERING:
			case END_BUFFERING: {
				boolean buffering = msg.what == Message.START_BUFFERING.ordinal();
				if (lastBuffering != buffering) {
					lastBuffering = buffering;
					onSeekingBufferingStateChange(false, true);
				}
				return true;
			}
			case RETRY_SET_POSITION: {
				long position = (long) msg.obj;
				// Sometimes player hangs during setPosition (ffmpeg seek too far away from real position)
				// I can do nothing better than repeat seeking
				setPosition(position);
				return true;
			}
			case REQUEST_RANGE: {
				long position = (long) msg.obj;
				requestRange(position);
				return true;
			}
		}
		return false;
	});

	private static class SeekToPosition {
		public final long position;
		public final boolean allow;

		public SeekToPosition(long position, boolean allow) {
			this.position = position;
			this.allow = allow;
		}
	}

	private SeekToPosition seekToPosition = null;
	private final Semaphore seekerMutex = new Semaphore(1);
	private final Thread seekerThread = new Thread(this::seekerThread);

	private void seekerThread() {
		Thread seekerThread = Thread.currentThread();
		while (true) {
			long position;
			synchronized (seekerThread) {
				SeekToPosition seekToPosition;
				while (true) {
					seekToPosition = this.seekToPosition;
					if ((seekToPosition == null || !seekToPosition.allow) && !consumed) {
						try {
							seekerThread.wait();
						} catch (InterruptedException e) {
							// Uninterruptible wait
						}
					} else {
						break;
					}
				}
				if (consumed) {
					return;
				}
				if (sessionData != null) {
					position = seekToPosition.position;
					seekerMutex.acquireUninterruptibly();
				} else {
					continue;
				}
			}
			try {
				handler.removeMessages(Message.END_BUFFERING.ordinal());
				handler.sendEmptyMessageDelayed(Message.START_BUFFERING.ordinal(), 200);
				handler.sendMessageDelayed(handler.obtainMessage
						(Message.RETRY_SET_POSITION.ordinal(), position), 1000);
				holder.setPosition(sessionData.pointer, position);
				handler.removeMessages(Message.RETRY_SET_POSITION.ordinal());
				handler.removeMessages(Message.START_BUFFERING.ordinal());
				handler.sendEmptyMessageDelayed(Message.END_BUFFERING.ordinal(), 100);
			} finally {
				seekToPosition = null;
				seekerMutex.release();
			}
		}
	}

	@SuppressWarnings("unused")
	private static class NativeBridge {
		private static final int BRIDGE_MESSAGE_PLAYBACK_COMPLETE = 1;
		private static final int BRIDGE_MESSAGE_SIZE_CHANGED = 2;
		private static final int BRIDGE_MESSAGE_START_SEEKING = 3;
		private static final int BRIDGE_MESSAGE_END_SEEKING = 4;

		private final WeakReference<VideoPlayer> player;

		public NativeBridge(VideoPlayer player) {
			this.player = new WeakReference<>(player);
		}

		public void onSeek(long position) {
			VideoPlayer player = this.player.get();
			if (player != null) {
				player.handler.removeMessages(Message.REQUEST_RANGE.ordinal());
				player.handler.obtainMessage(Message.REQUEST_RANGE.ordinal(), position).sendToTarget();
			}
		}

		public void onMessage(int what) {
			VideoPlayer player = this.player.get();
			if (player != null) {
				switch (what) {
					case BRIDGE_MESSAGE_PLAYBACK_COMPLETE: {
						player.handler.sendEmptyMessageDelayed(Message.PLAYBACK_COMPLETE.ordinal(), 200);
						break;
					}
					case BRIDGE_MESSAGE_SIZE_CHANGED: {
						player.handler.sendEmptyMessage(Message.SIZE_CHANGED.ordinal());
						break;
					}
					case BRIDGE_MESSAGE_START_SEEKING: {
						player.handler.removeMessages(Message.END_SEEKING.ordinal());
						if (player.lastBuffering) {
							player.handler.sendEmptyMessage(Message.START_SEEKING.ordinal());
						} else {
							player.handler.sendEmptyMessageDelayed(Message.START_SEEKING.ordinal(), 500);
						}
						break;
					}
					case BRIDGE_MESSAGE_END_SEEKING: {
						player.handler.removeMessages(Message.START_SEEKING.ordinal());
						player.handler.sendEmptyMessage(Message.END_SEEKING.ordinal());
						break;
					}
				}
			}
		}
	}

	private interface HolderInterface {
		long preInit(int fd);
		void init(long pointer, Object nativeBridge, boolean seekAnyFrame);
		void destroy(long pointer, boolean initOnly);

		int getErrorCode(long pointer);
		void getSummary(long pointer, int[] output);

		long getDuration(long pointer);
		long getPosition(long pointer);
		void setPosition(long pointer, long position);

		void setRange(long pointer, long start, long end, long total);
		void setCancelSeek(long pointer, boolean cancelSeek);

		void setSurface(long pointer, Surface surface);
		void setPlaying(long pointer, boolean playing);

		int[] getCurrentFrame(long pointer, int[] dimensions);
		String[] getMetadata(long pointer);
	}

	private static class Holder implements HolderInterface, InvocationHandler {
		// Extracted via reflection
		@SuppressWarnings("unused")
		private static final Holder INSTANCE = new Holder();

		private final Map<String, Method> methods;

		private Holder() {
			HashMap<String, Method> methods = new HashMap<>();
			// Collect all native methods declared in interface for faster access
			for (Method method : HolderInterface.class.getMethods()) {
				// Assume there are no overloaded methods
				methods.put(method.getName(), method);
			}
			this.methods = Collections.unmodifiableMap(methods);
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
			// Class loader bridge
			return methods.get(method.getName()).invoke(this, args);
		}

		@Override public native long preInit(int fd);
		@Override public native void init(long pointer, Object nativeBridge, boolean seekAnyFrame);
		@Override public native void destroy(long pointer, boolean initOnly);

		@Override public native int getErrorCode(long pointer);
		@Override public native void getSummary(long pointer, int[] output);

		@Override public native long getDuration(long pointer);
		@Override public native long getPosition(long pointer);
		@Override public native void setPosition(long pointer, long position);

		@Override public native void setRange(long pointer, long start, long end, long total);
		@Override public native void setCancelSeek(long pointer, boolean busy);

		@Override public native void setSurface(long pointer, Surface surface);
		@Override public native void setPlaying(long pointer, boolean playing);

		@Override public native int[] getCurrentFrame(long pointer, int[] dimensions);
		@Override public native String[] getMetadata(long pointer);

		static {
			System.loadLibrary("player");
		}
	}
}
