package com.mishiranu.dashchan.content.async;

import android.util.Pair;
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

public class ReadUserBoardsTask extends HttpHolderTask<Long, Pair<ErrorItem, List<String>>> {
	private final Callback callback;
	private final Chan chan;

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
	protected Pair<ErrorItem, List<String>> run(HttpHolder holder) {
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
				return new Pair<>(new ErrorItem(ErrorItem.Type.EMPTY_RESPONSE), null);
			}
			return new Pair<>(null, boardNamesList);
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			return new Pair<>(e.getErrorItemAndHandle(), null);
		} finally {
			chan.configuration.commit();
		}
	}

	@Override
	protected void onComplete(Pair<ErrorItem, List<String>> result) {
		if (result.second != null) {
			callback.onReadUserBoardsSuccess(result.second);
		} else {
			callback.onReadUserBoardsFail(result.first);
		}
	}
}
