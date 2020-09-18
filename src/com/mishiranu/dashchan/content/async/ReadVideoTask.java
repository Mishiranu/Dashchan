package com.mishiranu.dashchan.content.async;

import android.net.Uri;
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
import java.io.IOException;
import java.io.OutputStream;

public class ReadVideoTask extends HttpHolderTask<Void, Long, Boolean> {
	private static final int CONNECT_TIMEOUT = 15000;
	private static final int READ_TIMEOUT = 15000;

	private final String chanName;
	private final Uri uri;
	private final CachingInputStream inputStream;
	private final Callback callback;

	private ErrorItem errorItem;

	public interface Callback {
		public void onReadVideoProgressUpdate(long progress, long progressMax);
		public void onReadVideoSuccess(CachingInputStream inputStream);
		public void onReadVideoFail(ErrorItem errorItem);
	}

	private final TimedProgressHandler progressHandler = new TimedProgressHandler() {
		@Override
		public void onProgressChange(long progress, long progressMax) {
			publishProgress(progress, progressMax);
		}
	};

	public ReadVideoTask(String chanName, Uri uri, CachingInputStream inputStream, Callback callback) {
		this.chanName = chanName;
		this.uri = uri;
		this.inputStream = inputStream;
		this.callback = callback;
	}

	@Override
	protected Boolean doInBackground(HttpHolder holder, Void... params) {
		try {
			ChanPerformer.ReadContentResult result = ChanPerformer.get(chanName).safe()
					.onReadContent(new ChanPerformer.ReadContentData(uri, CONNECT_TIMEOUT, READ_TIMEOUT, holder,
					progressHandler, inputStream.getOutputStream()));
			HttpResponse response = result != null ? result.response : null;
			if (response != null) {
				byte[] data = response.getBytes();
				if (data == null) {
					errorItem = new ErrorItem(ErrorItem.Type.UNKNOWN);
					return false;
				}
				OutputStream output = inputStream.getOutputStream();
				try {
					output.write(data);
				} catch (IOException e) {
					// Ignore exception
				} finally {
					IOUtils.close(output);
				}
			}
			return true;
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return false;
		} finally {
			if (chanName != null) {
				ChanConfiguration.get(chanName).commit();
			}
		}
	}

	@Override
	protected void onProgressUpdate(Long... values) {
		callback.onReadVideoProgressUpdate(values[0], values[1]);
	}

	@Override
	public void onPostExecute(Boolean success) {
		if (success) {
			callback.onReadVideoSuccess(inputStream);
		} else {
			callback.onReadVideoFail(errorItem);
		}
	}

	public boolean isError() {
		return errorItem != null;
	}
}
