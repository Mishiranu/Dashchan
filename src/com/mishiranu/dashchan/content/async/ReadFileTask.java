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
import com.mishiranu.dashchan.content.net.EmbeddedManager;
import com.mishiranu.dashchan.util.IOUtils;

public class ReadFileTask extends HttpHolderTask<String, Long, Boolean> {
	public interface Callback {
		public void onFileExists(Uri uri, File file);
		public void onStartDownloading(Uri uri, File file);
		public void onFinishDownloading(boolean success, Uri uri, File file, ErrorItem errorItem);
		public void onUpdateProgress(long progress, long progressMax);
	}

	public interface CancelCallback {
		public void onCancelDownloading(Uri uri, File file);
	}

	public interface AsyncFinishCallback {
		public void onFinishDownloadingInThread();
	}

	private final Context context;
	private final Callback callback;

	private final String chanName;
	private final Uri fromUri;
	private final File toFile;
	private final File cachedMediaFile;
	private final boolean overwrite;

	private ErrorItem errorItem;

	private boolean loadingStarted;

	private final TimedProgressHandler progressHandler = new TimedProgressHandler() {
		@Override
		public void onProgressChange(long progress, long progressMax) {
			publishProgress(progress, progressMax);
		}
	};

	public ReadFileTask(Context context, String chanName, Uri from, File to, boolean overwrite, Callback callback) {
		this.context = context.getApplicationContext();
		this.chanName = chanName;
		this.callback = callback;
		this.fromUri = from;
		this.toFile = to;
		File cachedMediaFile = CacheManager.getInstance().getMediaFile(from, true);
		if (cachedMediaFile == null || !cachedMediaFile.exists() || CacheManager.getInstance()
				.cancelCachedMediaBusy(cachedMediaFile) || cachedMediaFile.equals(to)) {
			cachedMediaFile = null;
		}
		this.cachedMediaFile = cachedMediaFile;
		this.overwrite = overwrite;
	}

	@Override
	public void onPreExecute() {
		if (!overwrite && toFile.exists()) {
			cancel(false);
			callback.onFileExists(fromUri, toFile);
		} else {
			callback.onStartDownloading(fromUri, toFile);
		}
	}

	@Override
	protected Boolean doInBackground(HttpHolder holder, String... params) {
		try {
			loadingStarted = true;
			if (cachedMediaFile != null) {
				InputStream input = null;
				OutputStream output = null;
				try {
					input = HttpClient.wrapWithProgressListener(new FileInputStream(cachedMediaFile),
							progressHandler, cachedMediaFile.length());
					output = IOUtils.openOutputStream(context, toFile);
					IOUtils.copyStream(input, output);
				} finally {
					IOUtils.close(input);
					IOUtils.close(output);
				}
			} else {
				Uri uri = fromUri;
				uri = EmbeddedManager.getInstance().doReadRealUri(uri, holder);
				final int connectTimeout = 15000, readTimeout = 15000;
				byte[] response;
				String chanName = this.chanName;
				if (chanName == null) {
					chanName = ChanManager.getInstance().getChanNameByHost(uri.getAuthority());
				}
				if (chanName != null) {
					ChanPerformer.ReadContentResult result = ChanPerformer.get(chanName).safe()
							.onReadContent(new ChanPerformer.ReadContentData (uri, connectTimeout, readTimeout,
							holder, progressHandler, null));
					response = result.response.getBytes();
				} else {
					response = new HttpRequest(uri, holder).setTimeouts(connectTimeout, readTimeout)
							.setInputListener(progressHandler).read().getBytes();
				}
				ByteArrayInputStream input = new ByteArrayInputStream(response);
				OutputStream output = null;
				boolean success = false;
				try {
					output = IOUtils.openOutputStream(context, toFile);
					IOUtils.copyStream(input, output);
					success = true;
				} finally {
					IOUtils.close(output);
					CacheManager.getInstance().handleDownloadedFile(toFile, success);
				}
			}
			return true;
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return false;
		} catch (FileNotFoundException e) {
			errorItem = new ErrorItem(ErrorItem.TYPE_NO_ACCESS_TO_MEMORY);
			return false;
		} catch (IOException e) {
			String message = e.getMessage();
			if (message != null && message.contains("ENOSPC")) {
				toFile.delete();
				CacheManager.getInstance().handleDownloadedFile(toFile, false);
				errorItem = new ErrorItem(ErrorItem.TYPE_INSUFFICIENT_SPACE);
			} else {
				errorItem = new ErrorItem(ErrorItem.TYPE_UNKNOWN);
			}
			return false;
		} finally {
			if (callback instanceof AsyncFinishCallback) {
				((AsyncFinishCallback) callback).onFinishDownloadingInThread();
			}
		}
	}

	@Override
	public void onPostExecute(Boolean success) {
		callback.onFinishDownloading(success, fromUri, toFile, errorItem);
	}

	@Override
	protected void onProgressUpdate(Long... values) {
		callback.onUpdateProgress(values[0], values[1]);
	}

	public boolean isDownloadingFromCache() {
		return cachedMediaFile != null;
	}

	public String getFileName() {
		return toFile.getName();
	}

	@Override
	public void cancel() {
		super.cancel();
		if (loadingStarted) {
			toFile.delete();
			CacheManager.getInstance().handleDownloadedFile(toFile, false);
		}
		if (callback instanceof CancelCallback) {
			((CancelCallback) callback).onCancelDownloading(fromUri, toFile);
		}
	}
}