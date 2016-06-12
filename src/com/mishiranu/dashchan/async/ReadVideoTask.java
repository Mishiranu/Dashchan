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

package com.mishiranu.dashchan.async;

import java.io.IOException;
import java.io.OutputStream;

import android.net.Uri;
import android.os.AsyncTask;

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpResponse;

import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.media.CachingInputStream;
import com.mishiranu.dashchan.util.IOUtils;

public class ReadVideoTask extends AsyncTask<Void, Long, Boolean>
{
	private final String mChanName;
	private final Uri mUri;
	private final CachingInputStream mInputStream;
	private final Callback mCallback;
	private final HttpHolder mHolder = new HttpHolder();
	
	private byte[] mData;
	private ErrorItem mErrorItem;
	
	public static interface Callback
	{
		public void onReadVideoProgressUpdate(long progress, long progressMax);
		public void onReadVideoSuccess(CachingInputStream inputStream);
		public void onReadVideoFail(ErrorItem errorItem);
	}
	
	private final TimedProgressHandler mProgressHandler = new TimedProgressHandler()
	{
		@Override
		public void onProgressChange(long progress, long progressMax)
		{
			publishProgress(progress, progressMax);
		}
	};
	
	public ReadVideoTask(String chanName, Uri uri, CachingInputStream inputStream, Callback callback)
	{
		mChanName = chanName;
		mUri = uri;
		mInputStream = inputStream;
		mCallback = callback;
	}
	
	@Override
	protected Boolean doInBackground(Void... params)
	{
		try
		{
			int connectTimeout = 15000, readTimeout = 15000;
			HttpResponse response = null;
			try
			{
				ChanPerformer.ReadContentResult result = ChanPerformer.get(mChanName).onReadContent(new ChanPerformer
						.ReadContentData(mUri, connectTimeout, readTimeout, mHolder, mProgressHandler,
						mInputStream.getOutputStream()));
				if (result != null) response = result.response;
			}
			catch (LinkageError | RuntimeException e)
			{
				mErrorItem = ExtensionException.obtainErrorItemAndLogException(e);
				return false;
			}
			if (response != null)
			{
				mData = response.getBytes();
				if (mData == null)
				{
					mErrorItem = new ErrorItem(ErrorItem.TYPE_UNKNOWN);
					return false;
				}
				OutputStream output = mInputStream.getOutputStream();
				try
				{
					output.write(mData);
				}
				catch (IOException e)
				{
					
				}
				finally
				{
					IOUtils.close(output);
				}
			}
			return true;
		}
		catch (HttpException | InvalidResponseException e)
		{
			mErrorItem = e.getErrorItemAndHandle();
			return false;
		}
		finally
		{
			if (mChanName != null) ChanConfiguration.get(mChanName).commit();
		}
	}
	
	@Override
	protected void onProgressUpdate(Long... values)
	{
		mCallback.onReadVideoProgressUpdate(values[0], values[1]);
	}
	
	@Override
	public void onPostExecute(Boolean success)
	{
		if (success) mCallback.onReadVideoSuccess(mInputStream);
		else mCallback.onReadVideoFail(mErrorItem);
	}
	
	public boolean isError()
	{
		return mErrorItem != null;
	}
	
	public void cancel()
	{
		cancel(true);
		mHolder.interrupt();
	}
}