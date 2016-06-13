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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;
import android.net.Uri;

import chan.content.ChanManager;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpClient;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;

import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.net.EmbeddedManager;
import com.mishiranu.dashchan.util.IOUtils;

public class ReadFileTask extends CancellableTask<String, Long, Boolean>
{
	public static interface Callback
	{
		public void onFileExists(Uri uri, File file);
		public void onStartDownloading(Uri uri, File file);
		public void onFinishDownloading(boolean success, Uri uri, File file, ErrorItem errorItem);
		public void onUpdateProgress(long progress, long progressMax);
	}
	
	public static interface CancelCallback
	{
		public void onCancelDownloading(Uri uri, File file);
	}
	
	public static interface AsyncFinishCallback
	{
		public void onFinishDownloadingInThread();
	}
	
	private final Context mContext;
	private final Callback mCallback;
	private final HttpHolder mHolder = new HttpHolder();
	
	private final String mChanName;
	private final Uri mFromUri;
	private final File mToFile;
	private final File mCachedMediaFile;
	private final boolean mOverwrite;
	
	private ErrorItem mErrorItem;
	
	private boolean mLoadingStarted;
	
	private final TimedProgressHandler mProgressHandler = new TimedProgressHandler()
	{
		@Override
		public void onProgressChange(long progress, long progressMax)
		{
			publishProgress(progress, progressMax);
		}
	};
	
	public ReadFileTask(Context context, String chanName, Uri from, File to, boolean overwrite, Callback callback)
	{
		mContext = context.getApplicationContext();
		mChanName = chanName;
		mCallback = callback;
		mFromUri = from;
		mToFile = to;
		File cachedMediaFile = CacheManager.getInstance().getMediaFile(from, true);
		if (cachedMediaFile == null || !cachedMediaFile.exists() || CacheManager.getInstance()
				.cancelCachedMediaBusy(cachedMediaFile) || cachedMediaFile.equals(to))
		{
			cachedMediaFile = null;
		}
		mCachedMediaFile = cachedMediaFile;
		mOverwrite = overwrite;
	}
	
	@Override
	public void onPreExecute()
	{
		if (!mOverwrite && mToFile.exists())
		{
			cancel(false);
			mCallback.onFileExists(mFromUri, mToFile);
		}
		else mCallback.onStartDownloading(mFromUri, mToFile);
	}
	
	@Override
	protected Boolean doInBackground(String... params)
	{
		try
		{
			mLoadingStarted = true;
			if (mCachedMediaFile != null)
			{
				InputStream input = null;
				OutputStream output = null;
				try
				{
					input = HttpClient.wrapWithProgressListener(new FileInputStream(mCachedMediaFile),
							mProgressHandler, mCachedMediaFile.length());
					output = IOUtils.openOutputStream(mContext, mToFile);
					IOUtils.copyStream(input, output);
				}
				finally
				{
					IOUtils.close(input);
					IOUtils.close(output);
				}
			}
			else
			{
				Uri uri = mFromUri;
				String chanName = mChanName;
				if (chanName == null) chanName = ChanManager.getInstance().getChanNameByHost(uri.getAuthority());
				HttpHolder holder = mHolder;
				uri = EmbeddedManager.getInstance().doReadRealUri(chanName, uri, holder);
				final int connectTimeout = 15000, readTimeout = 15000;
				byte[] response;
				if (chanName != null)
				{
					try
					{
						ChanPerformer.ReadContentResult result = ChanPerformer.get(chanName).onReadContent
								(new ChanPerformer.ReadContentData (uri, connectTimeout, readTimeout,
								holder, mProgressHandler, null));
						response = result.response.getBytes();
					}
					catch (LinkageError | RuntimeException e)
					{
						throw new ExtensionException(e);
					}
				}
				else
				{
					response = new HttpRequest(uri, holder).setTimeouts(15000, 15000)
							.setInputListener(mProgressHandler).read().getBytes();
				}
				ByteArrayInputStream input = new ByteArrayInputStream(response);
				OutputStream output = null;
				boolean success = false;
				try
				{
					output = IOUtils.openOutputStream(mContext, mToFile);
					IOUtils.copyStream(input, output);
					success = true;
				}
				finally
				{
					IOUtils.close(output);
					CacheManager.getInstance().handleDownloadedFile(mToFile, success);
				}
			}
			return true;
		}
		catch (HttpException | InvalidResponseException | ExtensionException e)
		{
			mErrorItem = e.getErrorItemAndHandle();
			return false;
		}
		catch (FileNotFoundException e)
		{
			mErrorItem = new ErrorItem(ErrorItem.TYPE_NO_ACCESS_TO_MEMORY);
			return false;
		}
		catch (IOException e)
		{
			String message = e.getMessage();
			if (message != null && message.contains("ENOSPC"))
			{
				mToFile.delete();
				CacheManager.getInstance().handleDownloadedFile(mToFile, false);
				mErrorItem = new ErrorItem(ErrorItem.TYPE_INSUFFICIENT_SPACE);
			}
			else mErrorItem = new ErrorItem(ErrorItem.TYPE_UNKNOWN);
			return false;
		}
		finally
		{
			if (mCallback instanceof AsyncFinishCallback)
			{
				((AsyncFinishCallback) mCallback).onFinishDownloadingInThread();
			}
		}
	}
	
	@Override
	public void onPostExecute(Boolean success)
	{
		mCallback.onFinishDownloading(success, mFromUri, mToFile, mErrorItem);
	}
	
	@Override
	protected void onProgressUpdate(Long... values)
	{
		mCallback.onUpdateProgress(values[0], values[1]);
	}
	
	public boolean isDownloadingFromCache()
	{
		return mCachedMediaFile != null;
	}
	
	public String getFileName()
	{
		return mToFile.getName();
	}
	
	@Override
	public void cancel()
	{
		cancel(true);
		mHolder.interrupt();
		if (mLoadingStarted)
		{
			mToFile.delete();
			CacheManager.getInstance().handleDownloadedFile(mToFile, false);
		}
		if (mCallback instanceof CancelCallback)
		{
			((CancelCallback) mCallback).onCancelDownloading(mFromUri, mToFile);
		}
	}
}