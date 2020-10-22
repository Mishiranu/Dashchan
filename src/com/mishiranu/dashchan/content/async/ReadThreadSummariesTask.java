package com.mishiranu.dashchan.content.async;

import chan.content.Chan;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.ThreadSummary;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.util.CommonUtils;
import com.mishiranu.dashchan.content.model.ErrorItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

public class ReadThreadSummariesTask extends HttpHolderTask<Void, Void, ThreadSummary[]> {
	private final Callback callback;
	private final Chan chan;
	private final String boardName;
	private final int pageNumber;
	private final int type;

	private ErrorItem errorItem;

	public interface Callback {
		void onReadThreadSummariesSuccess(ThreadSummary[] threadSummaries, int pageNumber);
		void onReadThreadSummariesFail(ErrorItem errorItem);
	}

	public ReadThreadSummariesTask(Callback callback, Chan chan, String boardName, int pageNumber, int type) {
		super(chan);
		this.callback = callback;
		this.chan = chan;
		this.boardName = boardName;
		this.pageNumber = pageNumber;
		this.type = type;
	}

	@Override
	protected ThreadSummary[] doInBackground(HttpHolder holder, Void... params) {
		try {
			ChanPerformer.ReadThreadSummariesResult result = chan.performer.safe()
					.onReadThreadSummaries(new ChanPerformer
							.ReadThreadSummariesData(boardName, pageNumber, type, holder));
			ThreadSummary[] threadSummaries = result != null ? result.threadSummaries : null;
			return threadSummaries != null && threadSummaries.length > 0 ? threadSummaries : null;
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return null;
		} finally {
			chan.configuration.commit();
		}
	}

	@Override
	public void onPostExecute(ThreadSummary[] threadSummaries) {
		if (errorItem == null) {
			callback.onReadThreadSummariesSuccess(threadSummaries, pageNumber);
		} else {
			callback.onReadThreadSummariesFail(errorItem);
		}
	}

	public static ThreadSummary[] concatenate(ThreadSummary[] threadSummaries1, ThreadSummary[] threadSummaries2) {
		ArrayList<ThreadSummary> threadSummaries = new ArrayList<>();
		if (threadSummaries1 != null) {
			Collections.addAll(threadSummaries, threadSummaries1);
		}
		HashSet<String> identifiers = new HashSet<>();
		for (ThreadSummary threadSummary : threadSummaries) {
			identifiers.add(threadSummary.getBoardName() + '/' + threadSummary.getThreadNumber());
		}
		if (threadSummaries2 != null) {
			for (ThreadSummary threadSummary : threadSummaries2) {
				if (!identifiers.contains(threadSummary.getBoardName() + '/' + threadSummary.getThreadNumber())) {
					threadSummaries.add(threadSummary);
				}
			}
		}
		return CommonUtils.toArray(threadSummaries, ThreadSummary.class);
	}
}
