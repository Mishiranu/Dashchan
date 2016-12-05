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

package com.mishiranu.dashchan.util;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.os.Handler;
import android.os.Looper;
import android.os.Process;

public class ConcurrentUtils {
	private static final Handler HANDLER = new Handler(Looper.getMainLooper());

	public static final Executor SEPARATE_EXECUTOR = command -> new Thread(command).start();

	public static ThreadPoolExecutor newSingleThreadPool(int lifeTimeMs, String componentName, String componentPart,
			int threadPriority) {
		return newThreadPool(lifeTimeMs > 0 ? 0 : 1, 1, lifeTimeMs, componentName, componentPart, threadPriority);
	}

	public static ThreadPoolExecutor newThreadPool(int from, int to, long lifeTimeMs,
			String componentName, String componentPart, int threadPriority) {
		return new ThreadPoolExecutor(from, to, lifeTimeMs, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
				new ComponentThreadFactory(componentName, componentPart, threadPriority));
	}

	private static class ComponentThreadFactory implements ThreadFactory {
		private final String componentName;
		private final String componentPart;
		private final int threadPriority;

		public ComponentThreadFactory(String componentName, String componentPart, int threadPriority) {
			this.componentName = componentName;
			this.componentPart = componentPart;
			this.threadPriority = threadPriority;
		}

		@Override
		public Thread newThread(Runnable r) {
			Thread thread = new Thread(() -> {
				Process.setThreadPriority(threadPriority);
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