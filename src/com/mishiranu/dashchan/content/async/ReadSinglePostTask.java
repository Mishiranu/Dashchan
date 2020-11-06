package com.mishiranu.dashchan.content.async;

import android.util.Pair;
import chan.content.Chan;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import java.net.HttpURLConnection;

public class ReadSinglePostTask extends HttpHolderTask<Void, Pair<ErrorItem, PostItem>> {
	private final Callback callback;
	private final Chan chan;
	private final String boardName;
	private final String threadNumber;
	private final PostNumber postNumber;

	public interface Callback {
		void onReadSinglePostSuccess(PostItem postItem);
		void onReadSinglePostFail(ErrorItem errorItem);
	}

	public ReadSinglePostTask(Callback callback, Chan chan,
			String boardName, String threadNumber, PostNumber postNumber) {
		super(chan);
		this.callback = callback;
		this.chan = chan;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.postNumber = postNumber;
	}

	@Override
	protected Pair<ErrorItem, PostItem> run(HttpHolder holder) {
		try {
			String postNumber;
			if (this.postNumber != null) {
				postNumber = this.postNumber.toString();
			} else {
				postNumber = threadNumber;
			}
			ChanPerformer.ReadSinglePostResult result = chan.performer.safe().onReadSinglePost(new ChanPerformer
					.ReadSinglePostData(boardName, postNumber, holder));
			if (result == null || result.post == null) {
				throw HttpException.createNotFoundException();
			}
			return new Pair<>(null, PostItem.createPost(result.post.post, chan,
					boardName, result.post.threadNumber, result.post.originalPostNumber));
		} catch (HttpException e) {
			ErrorItem errorItem = e.getErrorItemAndHandle();
			if (errorItem.httpResponseCode == HttpURLConnection.HTTP_NOT_FOUND ||
					errorItem.httpResponseCode == HttpURLConnection.HTTP_GONE) {
				errorItem = new ErrorItem(ErrorItem.Type.POST_NOT_FOUND);
			}
			return new Pair<>(errorItem, null);
		} catch (ExtensionException | InvalidResponseException e) {
			return new Pair<>(e.getErrorItemAndHandle(), null);
		} finally {
			chan.configuration.commit();
		}
	}

	@Override
	protected void onComplete(Pair<ErrorItem, PostItem> result) {
		if (result.second != null) {
			callback.onReadSinglePostSuccess(result.second);
		} else {
			callback.onReadSinglePostFail(result.first);
		}
	}
}
