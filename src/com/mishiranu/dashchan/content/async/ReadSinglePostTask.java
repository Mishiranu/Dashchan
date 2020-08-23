package com.mishiranu.dashchan.content.async;

import android.os.SystemClock;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.Post;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.util.CommonUtils;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import java.net.HttpURLConnection;

public class ReadSinglePostTask extends HttpHolderTask<Void, Void, PostItem> {
	private final Callback callback;
	private final String boardName;
	private final String chanName;
	private final String postNumber;

	private ErrorItem errorItem;

	public interface Callback {
		public void onReadSinglePostSuccess(PostItem postItem);
		public void onReadSinglePostFail(ErrorItem errorItem);
	}

	public ReadSinglePostTask(Callback callback, String chanName, String boardName, String postNumber) {
		this.callback = callback;
		this.boardName = boardName;
		this.chanName = chanName;
		this.postNumber = postNumber;
	}

	@Override
	protected PostItem doInBackground(HttpHolder holder, Void... params) {
		long startTime = SystemClock.elapsedRealtime();
		try {
			ChanPerformer performer = ChanPerformer.get(chanName);
			ChanPerformer.ReadSinglePostResult result = performer.safe().onReadSinglePost(new ChanPerformer
					.ReadSinglePostData(boardName, postNumber, holder));
			Post post = result != null ? result.post : null;
			startTime = 0L;
			return new PostItem(post, chanName, boardName);
		} catch (HttpException e) {
			errorItem = e.getErrorItemAndHandle();
			if (errorItem.httpResponseCode == HttpURLConnection.HTTP_NOT_FOUND ||
					errorItem.httpResponseCode == HttpURLConnection.HTTP_GONE) {
				errorItem = new ErrorItem(ErrorItem.Type.POST_NOT_FOUND);
			}
		} catch (ExtensionException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
		} finally {
			ChanConfiguration.get(chanName).commit();
			CommonUtils.sleepMaxRealtime(startTime, 500);
		}
		return null;
	}

	@Override
	protected void onPostExecute(PostItem result) {
		if (result != null) {
			callback.onReadSinglePostSuccess(result);
		} else {
			callback.onReadSinglePostFail(errorItem);
		}
	}
}
