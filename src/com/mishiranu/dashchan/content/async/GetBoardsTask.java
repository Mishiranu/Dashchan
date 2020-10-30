package com.mishiranu.dashchan.content.async;

import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import chan.content.Chan;
import com.mishiranu.dashchan.content.database.ChanDatabase;
import java.util.List;

public class GetBoardsTask extends ExecutorTask<Void, ChanDatabase.BoardCursor> {
	public interface Callback {
		void onGetBoardsResult(ChanDatabase.BoardCursor cursor);
	}

	private final Callback callback;
	private final Chan chan;
	private final List<String> userBoardNames;
	private final String searchQuery;
	private final CancellationSignal signal = new CancellationSignal();

	public GetBoardsTask(Callback callback, Chan chan, List<String> userBoardNames, String searchQuery) {
		this.callback = callback;
		this.chan = chan;
		this.userBoardNames = userBoardNames;
		this.searchQuery = searchQuery;
	}

	@Override
	protected ChanDatabase.BoardCursor run() {
		try {
			if (userBoardNames != null) {
				return chan.configuration.getUserBoards(userBoardNames, searchQuery, signal);
			} else {
				return chan.configuration.getBoards(searchQuery, signal);
			}
		} catch (OperationCanceledException e) {
			return null;
		}
	}

	@Override
	public void cancel() {
		super.cancel();
		try {
			signal.cancel();
		} catch (Exception e) {
			// Ignore
		}
	}

	@Override
	protected void onCancel(ChanDatabase.BoardCursor cursor) {
		if (cursor != null) {
			cursor.close();
		}
	}

	@Override
	protected void onComplete(ChanDatabase.BoardCursor cursor) {
		callback.onGetBoardsResult(cursor);
	}
}
