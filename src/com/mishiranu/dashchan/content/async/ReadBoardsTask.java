package com.mishiranu.dashchan.content.async;

import chan.content.Chan;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.BoardCategory;
import chan.http.HttpException;
import chan.http.HttpHolder;
import com.mishiranu.dashchan.content.database.ChanDatabase;
import com.mishiranu.dashchan.content.model.ErrorItem;

public class ReadBoardsTask extends HttpHolderTask<Void, Boolean> {
	private final Callback callback;
	private final Chan chan;

	private ErrorItem errorItem;

	public interface Callback {
		void onReadBoardsSuccess();
		void onReadBoardsFail(ErrorItem errorItem);
	}

	public ReadBoardsTask(Callback callback, Chan chan) {
		super(chan);
		this.callback = callback;
		this.chan = chan;
	}

	@Override
	protected Boolean run(HttpHolder holder) {
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
			if (!ChanDatabase.getInstance().setBoards(chan.name, boardCategories)) {
				errorItem = new ErrorItem(ErrorItem.Type.EMPTY_RESPONSE);
				return false;
			}
			return true;
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return false;
		} finally {
			chan.configuration.commit();
		}
	}

	@Override
	protected void onComplete(Boolean success) {
		if (success) {
			callback.onReadBoardsSuccess();
		} else {
			callback.onReadBoardsFail(errorItem);
		}
	}
}
