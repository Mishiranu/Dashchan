package com.mishiranu.dashchan.util;

import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import androidx.annotation.NonNull;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ConcurrentUtils {
	public static final Handler HANDLER = new Handler(Looper.getMainLooper());

	public static final Executor SEPARATE_EXECUTOR = command -> new Thread(command).start();
	public static final Executor PARALLEL_EXECUTOR = newThreadPool(1, 20, 3000, "ParallelExecutor", null);

	// 60 frames per second -> frame time is 1000 / 60 -> divide by 2
	public static final int HALF_FRAME_TIME_MS = 1000 / 60 / 2;

	public static ExecutorService newSingleThreadPool(int lifeTimeMs, String componentName, String componentPart) {
		return newThreadPool(lifeTimeMs > 0 ? 0 : 1, 1, lifeTimeMs, componentName, componentPart);
	}

	public static ExecutorService newThreadPool(int from, int to, long lifeTimeMs,
			String componentName, String componentPart) {
		if (to > from && to >= 2) {
			ThreadLocal<Boolean> executeState = new ThreadLocal<Boolean>() {
				@Override
				protected Boolean initialValue() {
					return false;
				}
			};
			LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>() {
				@Override
				public boolean offer(Runnable runnable) {
					return !executeState.get() && super.offer(runnable);
				}
			};
			return new ThreadPoolExecutor(from, to, lifeTimeMs, TimeUnit.MILLISECONDS, queue,
					new ComponentThreadFactory(componentName, componentPart)) {
				/* init */ {
					super.setRejectedExecutionHandler((runnable, executor) -> {
						executeState.set(false);
						if (!queue.offer(runnable)) {
							throw new RuntimeException();
						}
					});
				}

				@Override
				public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
					throw new UnsupportedOperationException();
				}

				@Override
				public void execute(Runnable command) {
					try {
						executeState.set(true);
						super.execute(command);
					} finally {
						executeState.set(false);
					}
				}
			};
		} else {
			return new ThreadPoolExecutor(from, to, lifeTimeMs, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
					new ComponentThreadFactory(componentName, componentPart));
		}
	}

	private static class ComponentThreadFactory implements ThreadFactory {
		private final String componentName;
		private final String componentPart;

		public ComponentThreadFactory(String componentName, String componentPart) {
			this.componentName = componentName;
			this.componentPart = componentPart;
		}

		@Override
		public Thread newThread(@NonNull Runnable r) {
			Thread thread = new Thread(() -> {
				Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
				r.run();
			});
			if (componentName != null) {
				thread.setName(componentName + (componentPart != null ? " #" + componentPart : ""));
			}
			return thread;
		}
	}

	public static boolean isMain() {
		return Looper.myLooper() == Looper.getMainLooper();
	}

	@SuppressWarnings("unchecked")
	public static <T> T mainGet(Callable<T> callable) {
		if (callable == null) {
			return null;
		}
		CountDownLatch latch = new CountDownLatch(1);
		Object[] result = new Object[2];
		Runnable runnable = () -> {
			try {
				result[0] = callable.call();
			} catch (Throwable t) {
				result[1] = t;
			}
			latch.countDown();
		};
		if (isMain()) {
			runnable.run();
		} else {
			HANDLER.post(runnable);
			boolean interrupted = false;
			while (true) {
				try {
					latch.await();
					if (interrupted) {
						Thread.currentThread().interrupt();
					}
					break;
				} catch (InterruptedException e) {
					interrupted = true;
				}
			}
		}
		if (result[1] != null) {
			if (result[1] instanceof RuntimeException) {
				throw ((RuntimeException) result[1]);
			}
			if (result[1] instanceof Error) {
				throw ((Error) result[1]);
			}
			throw new RuntimeException((Throwable) result[1]);
		}
		return (T) result[0];
	}
}
