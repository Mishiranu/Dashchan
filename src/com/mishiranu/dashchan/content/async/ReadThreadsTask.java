package com.mishiranu.dashchan.content.async;

import chan.content.Chan;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.RedirectException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpValidator;
import chan.util.CommonUtils;
import com.mishiranu.dashchan.content.database.CommonDatabase;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.util.Log;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

public class ReadThreadsTask extends HttpHolderTask<Void, Boolean> {
	private final Callback callback;
	private final Chan chan;
	private final String boardName;
	private final int pageNumber;
	private final HttpValidator validator;
	private final boolean append;

	private ArrayList<PostItem> postItems;
	private int boardSpeed = 0;
	private HttpValidator resultValidator;
	private PostItem.HideState.Map<String> hiddenThreads;

	private RedirectException.Target target;
	private ErrorItem errorItem;

	public interface Callback {
		void onReadThreadsSuccess(List<PostItem> postItems, int pageNumber,
				int boardSpeed, boolean append, boolean checkModified, HttpValidator validator,
				PostItem.HideState.Map<String> hiddenThreads);
		void onReadThreadsRedirect(RedirectException.Target target);
		void onReadThreadsFail(ErrorItem errorItem, int pageNumber);
	}

	public ReadThreadsTask(Callback callback, Chan chan, String boardName, int pageNumber,
			HttpValidator validator, boolean append) {
		super(chan);
		this.callback = callback;
		this.chan = chan;
		this.boardName = boardName;
		this.pageNumber = pageNumber;
		this.validator = validator;
		this.append = append;
	}

	public int getPageNumber() {
		return pageNumber;
	}

	@Override
	protected Boolean run(HttpHolder holder) {
		try {
			ChanPerformer.ReadThreadsResult result;
			try {
				result = chan.performer.safe().onReadThreads(new ChanPerformer.ReadThreadsData(boardName,
						pageNumber, holder, validator));
			} catch (RedirectException e) {
				RedirectException.Target target = e.obtainTarget(chan.name);
				if (target == null) {
					throw HttpException.createNotFoundException();
				}
				if (target.threadNumber != null) {
					Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES, "Only board redirects available there");
					errorItem = new ErrorItem(ErrorItem.Type.INVALID_DATA_FORMAT);
					return false;
				} else if (chan.name.equals(target.chanName) && CommonUtils.equals(boardName, target.boardName)) {
					throw HttpException.createNotFoundException();
				} else {
					this.target = target;
					return true;
				}
			}
			if (result == null) {
				throw HttpException.createNotFoundException();
			}
			ArrayList<PostItem> postItems = new ArrayList<>(result.threads.size());
			ArrayList<String> threadNumbers = new ArrayList<>(result.threads.size());
			for (ChanPerformer.ReadThreadsResult.Thread thread : result.threads) {
				postItems.add(PostItem.createThread(thread.posts, thread.postsCount, thread.filesCount,
						thread.postsWithFilesCount, chan, boardName, thread.threadNumber));
				threadNumbers.add(thread.threadNumber);
			}
			this.postItems = postItems;
			this.boardSpeed = result.boardSpeed;
			this.resultValidator = result.validator != null ? result.validator : holder.extractValidator();
			hiddenThreads = CommonDatabase.getInstance().getThreads()
					.getFlags(chan.name, boardName, threadNumbers);
			return true;
		} catch (HttpException e) {
			int responseCode = e.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
				return true;
			}
			if (responseCode == HttpURLConnection.HTTP_NOT_FOUND ||
					responseCode == HttpURLConnection.HTTP_GONE) {
				errorItem = new ErrorItem(ErrorItem.Type.BOARD_NOT_EXISTS);
			} else {
				errorItem = e.getErrorItemAndHandle();
			}
			return false;
		} catch (ExtensionException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return false;
		} finally {
			chan.configuration.commit();
		}
	}

	@Override
	public void onComplete(Boolean success) {
		if (success) {
			if (target != null) {
				callback.onReadThreadsRedirect(target);
			} else {
				callback.onReadThreadsSuccess(postItems, pageNumber, boardSpeed, append,
						validator != null, resultValidator, hiddenThreads);
			}
		} else {
			callback.onReadThreadsFail(errorItem, pageNumber);
		}
	}
}
