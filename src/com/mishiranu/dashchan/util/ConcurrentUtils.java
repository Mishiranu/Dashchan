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

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.os.Process;

public class ConcurrentUtils
{
	public static final Executor SEPARATE_EXECUTOR = command -> new Thread(command).start();
	
	public static ThreadPoolExecutor newSingleThreadPool(int lifeTimeMs, String componentName, String componentPart,
			int threadPriority)
	{
		return newThreadPool(lifeTimeMs > 0 ? 0 : 1, 1, lifeTimeMs, componentName, componentPart, threadPriority);
	}
	
	public static ThreadPoolExecutor newThreadPool(int from, int to, long lifeTimeMs,
			String componentName, String componentPart, int threadPriority)
	{
		return new ThreadPoolExecutor(from, to, lifeTimeMs, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
				new ComponentThreadFactory(componentName, componentPart, threadPriority));
	}
	
	private static class ComponentThreadFactory implements ThreadFactory
	{
		private final String mComponentName;
		private final String mComponentPart;
		private final int mThreadPriority;
		
		public ComponentThreadFactory(String componentName, String componentPart, int threadPriority)
		{
			mComponentName = componentName;
			mComponentPart = componentPart;
			mThreadPriority = threadPriority;
		}
		
		@Override
		public Thread newThread(Runnable r)
		{
			Thread thread = new Thread(r)
			{
				@Override
				public void run()
				{
					Process.setThreadPriority(mThreadPriority);
					super.run();
				}
			};
			if (mComponentName != null)
			{
				thread.setName(mComponentName + (mComponentPart != null ? " #" + mComponentPart : ""));
			}
			return thread;
		}
	}
}