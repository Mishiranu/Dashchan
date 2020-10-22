package com.mishiranu.dashchan.content.async;

import chan.content.Chan;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.BoardCategory;
import chan.http.HttpException;
import chan.http.HttpHolder;
import com.mishiranu.dashchan.content.model.ErrorItem;

public class ReadBoardsTask extends HttpHolderTask<Void, Long, Boolean> {
	private final Callback callback;
	private final Chan chan;

	private BoardCategory[] boardCategories;
	private ErrorItem errorItem;

	public interface Callback {
		void onReadBoardsSuccess(BoardCategory[] boardCategories);
		void onReadBoardsFail(ErrorItem errorItem);
	}

	public ReadBoardsTask(Callback callback, Chan chan) {
		super(chan);
		this.callback = callback;
		this.chan = chan;
	}

	@Override
	protected Boolean doInBackground(HttpHolder holder, Void... params) {
		try {
			ChanPerformer.ReadBoardsResult result = chan.performer.safe()
					.onReadBoards(new ChanPerformer.ReadBoardsData(holder));
			BoardCategory[] boardCategories = result != null ? result.boardCategories : null;
			if (boardCategories != null && boardCategories.length == 0) {
				boardCategories = null;
			}
			if (boardCategories != null) {
				chan.configuration.updateFromBoards(boardCategories);
			}
			this.boardCategories = boardCategories;
			return true;
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return false;
		} finally {
			chan.configuration.commit();
		}
	}

	@Override
	public void onPostExecute(Boolean success) {
		if (success) {
			if (boardCategories != null) {
				callback.onReadBoardsSuccess(boardCategories);
			} else {
				callback.onReadBoardsFail(new ErrorItem(ErrorItem.Type.EMPTY_RESPONSE));
			}
		} else {
			callback.onReadBoardsFail(errorItem);
		}
	}
}
