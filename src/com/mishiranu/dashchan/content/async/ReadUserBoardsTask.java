package com.mishiranu.dashchan.content.async;

import chan.content.Chan;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.Board;
import chan.http.HttpException;
import chan.http.HttpHolder;
import com.mishiranu.dashchan.content.model.ErrorItem;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ReadUserBoardsTask extends HttpHolderTask<Long, Boolean> {
	private final Callback callback;
	private final Chan chan;

	private List<Board> boards;
	private ErrorItem errorItem;

	public interface Callback {
		void onReadUserBoardsSuccess(List<Board> boards);
		void onReadUserBoardsFail(ErrorItem errorItem);
	}

	public ReadUserBoardsTask(Callback callback, Chan chan) {
		super(chan);
		this.callback = callback;
		this.chan = chan;
	}

	@Override
	protected Boolean run(HttpHolder holder) {
		try {
			ChanPerformer.ReadUserBoardsResult result = chan.performer.safe()
					.onReadUserBoards(new ChanPerformer.ReadUserBoardsData(holder));
			Board[] boards = result != null ? result.boards : null;
			if (boards != null && boards.length == 0) {
				boards = null;
			}
			if (boards != null) {
				chan.configuration.updateFromBoards(boards);
			}
			this.boards = boards != null ? Arrays.asList(boards) : Collections.emptyList();
			return true;
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return false;
		} finally {
			chan.configuration.commit();
		}
	}

	@Override
	public void onComplete(Boolean success) {
		if (success) {
			if (boards != null && boards.size() > 0) {
				callback.onReadUserBoardsSuccess(boards);
			} else {
				callback.onReadUserBoardsFail(new ErrorItem(ErrorItem.Type.EMPTY_RESPONSE));
			}
		} else {
			callback.onReadUserBoardsFail(errorItem);
		}
	}
}
