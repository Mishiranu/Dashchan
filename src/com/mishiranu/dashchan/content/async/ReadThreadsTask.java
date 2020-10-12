package com.mishiranu.dashchan.content.async;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
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

public class ReadThreadsTask extends HttpHolderTask<Void, Void, Boolean> {
	private final Callback callback;
	private final String chanName;
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
		void onReadThreadsSuccess(ArrayList<PostItem> postItems, int pageNumber,
				int boardSpeed, boolean append, boolean checkModified, HttpValidator validator,
				PostItem.HideState.Map<String> hiddenThreads);
		void onReadThreadsRedirect(RedirectException.Target target);
		void onReadThreadsFail(ErrorItem errorItem, int pageNumber);
	}

	public ReadThreadsTask(Callback callback, String chanName, String boardName, int pageNumber,
			HttpValidator validator, boolean append) {
		this.callback = callback;
		this.chanName = chanName;
		this.boardName = boardName;
		this.pageNumber = pageNumber;
		this.validator = validator;
		this.append = append;
	}

	@Override
	protected Boolean doInBackground(HttpHolder holder, Void... params) {
		try {
			ChanPerformer.ReadThreadsResult result;
			try {
				result = ChanPerformer.get(chanName).safe().onReadThreads(new ChanPerformer.ReadThreadsData(boardName,
						pageNumber, holder, validator));
			} catch (RedirectException e) {
				RedirectException.Target target = e.obtainTarget(chanName);
				if (target == null) {
					throw HttpException.createNotFoundException();
				}
				if (target.threadNumber != null) {
					Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES, "Only board redirects available there");
					errorItem = new ErrorItem(ErrorItem.Type.INVALID_DATA_FORMAT);
					return false;
				} else if (chanName.equals(target.chanName) && CommonUtils.equals(boardName, target.boardName)) {
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
						thread.postsWithFilesCount, ChanLocator.get(chanName),
						chanName, boardName, thread.threadNumber));
				threadNumbers.add(thread.threadNumber);
			}
			this.postItems = postItems;
			this.boardSpeed = result.boardSpeed;
			this.resultValidator = result.validator != null ? result.validator : holder.getValidator();
			hiddenThreads = CommonDatabase.getInstance().getThreads()
					.getFlags(chanName, boardName, threadNumbers);
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
			ChanConfiguration.get(chanName).commit();
		}
	}

	@Override
	public void onPostExecute(Boolean success) {
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
