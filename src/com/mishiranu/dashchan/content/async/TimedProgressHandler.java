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

import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.MultipartEntity;

public class TimedProgressHandler implements HttpHolder.InputListener, HttpRequest.OutputListener,
		MultipartEntity.OpenableOutputListener
{
	private final long[] mLastProgressUpdate = new long[3];

	private boolean checkNeedToUpdate(int index, long progress, long progressMax)
	{
		long time = System.currentTimeMillis();
		if (time - mLastProgressUpdate[index] >= 200 || progress == 0 || progress == progressMax)
		{
			mLastProgressUpdate[index] = time;
			return true;
		}
		return false;
	}

	@Override
	public final void onInputProgressChange(long progress, long progressMax)
	{
		if (checkNeedToUpdate(0, progress, progressMax))
		{
			onProgressChange(progress, progressMax);
		}
	}

	@Override
	public final void onOutputProgressChange(long progress, long progressMax)
	{
		if (checkNeedToUpdate(1, progress, progressMax))
		{
			onProgressChange(progress, progressMax);
		}
	}

	@Override
	public final void onOutputProgressChange(MultipartEntity.Openable openable, long progress, long progressMax)
	{
		if (checkNeedToUpdate(2, progress, progressMax))
		{
			onProgressChange(openable, progress, progressMax);
		}
	}

	public void onProgressChange(long progress, long progressMax)
	{

	}

	public void onProgressChange(MultipartEntity.Openable openable, long progress, long progressMax)
	{

	}
}