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

package com.mishiranu.dashchan.content.async;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import android.os.Handler;
import android.os.Looper;

public class MainLooperGetter<Data>
{
	private static final Handler HANDLER = new Handler(Looper.getMainLooper());

	private volatile Data mData;
	private volatile Throwable mThrowable;
	private volatile CountDownLatch mLatch;

	public Data get(Callable<Data> callable)
	{
		if (callable == null) return null;
		synchronized (this)
		{
			try
			{
				mLatch = new CountDownLatch(1);
				HANDLER.post(() ->
				{
					try
					{
						mData = callable.call();
					}
					catch (Throwable t)
					{
						mThrowable = t;
					}
					mLatch.countDown();
				});
				boolean interrupted = false;
				while (true)
				{
					try
					{
						mLatch.await();
						if (interrupted) Thread.currentThread().interrupt();
						break;
					}
					catch (InterruptedException e)
					{
						interrupted = true;
					}
				}
				if (mThrowable != null)
				{
					if (mThrowable instanceof RuntimeException) throw ((RuntimeException) mThrowable);
					if (mThrowable instanceof Error) throw ((Error) mThrowable);
					throw new RuntimeException(mThrowable);
				}
				return mData;
			}
			finally
			{

				mData = null;
				mThrowable = null;
				mLatch = null;
			}
		}
	}
}