package com.mishiranu.dashchan.content.async;

import chan.content.Chan;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.Board;
import chan.http.HttpException;
import chan.http.HttpHolder;
import com.mishiranu.dashchan.content.model.ErrorItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ReadUserBoardsTask extends HttpHolderTask<Long, Boolean> {
	private final Callback callback;
	private final Chan chan;

	private List<String> boardNames;
	private ErrorItem errorItem;

	public interface Callback {
		void onReadUserBoardsSuccess(List<String> boardNames);
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
			HashSet<String> boardNamesSet = new HashSet<>();
			ArrayList<String> boardNamesList = new ArrayList<>();
			if (boards != null) {
				chan.configuration.updateFromBoards(boards);
				for (Board board : boards) {
					if (board != null) {
						String boardName = board.getBoardName();
						if (boardName != null && !boardNamesSet.contains(boardName)) {
							boardNamesSet.add(boardName);
							boardNamesList.add(boardName);
						}
					}
				}
			}
			if (boardNamesList.isEmpty()) {
				errorItem = new ErrorItem(ErrorItem.Type.EMPTY_RESPONSE);
				return false;
			}
			this.boardNames = boardNamesList;
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
			callback.onReadUserBoardsSuccess(boardNames);
		} else {
			callback.onReadUserBoardsFail(errorItem);
		}
	}
}
