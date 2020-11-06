package com.mishiranu.dashchan.content.async;

import chan.content.Chan;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.ThreadSummary;
import chan.http.HttpException;
import chan.http.HttpHolder;
import com.mishiranu.dashchan.content.model.ErrorItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class ReadThreadSummariesTask extends HttpHolderTask<Void, List<ThreadSummary>> {
	private final Callback callback;
	private final Chan chan;
	private final String boardName;
	private final int pageNumber;
	private final int type;

	private ErrorItem errorItem;

	public interface Callback {
		void onReadThreadSummariesSuccess(List<ThreadSummary> threadSummaries, int pageNumber);
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

	public int getPageNumber() {
		return pageNumber;
	}

	@Override
	protected List<ThreadSummary> run(HttpHolder holder) {
		try {
			ChanPerformer.ReadThreadSummariesResult result = chan.performer.safe()
					.onReadThreadSummaries(new ChanPerformer
							.ReadThreadSummariesData(boardName, pageNumber, type, holder));
			ThreadSummary[] threadSummaries = result != null ? result.threadSummaries : null;
			return threadSummaries != null && threadSummaries.length > 0
					? Arrays.asList(threadSummaries) : Collections.emptyList();
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return null;
		} finally {
			chan.configuration.commit();
		}
	}

	@Override
	public void onComplete(List<ThreadSummary> threadSummaries) {
		if (threadSummaries != null) {
			callback.onReadThreadSummariesSuccess(threadSummaries, pageNumber);
		} else {
			callback.onReadThreadSummariesFail(errorItem);
		}
	}

	public static List<ThreadSummary> concatenate(List<ThreadSummary> threadSummaries1,
			List<ThreadSummary> threadSummaries2) {
		if (threadSummaries1 == null) {
			return threadSummaries2 != null ? threadSummaries2 : Collections.emptyList();
		} else if (threadSummaries2 == null) {
			return threadSummaries1;
		}
		ArrayList<ThreadSummary> threadSummaries = new ArrayList<>(threadSummaries1);
		HashSet<String> identifiers = new HashSet<>();
		for (ThreadSummary threadSummary : threadSummaries) {
			identifiers.add(threadSummary.getBoardName() + '/' + threadSummary.getThreadNumber());
		}
		for (ThreadSummary threadSummary : threadSummaries2) {
			if (!identifiers.contains(threadSummary.getBoardName() + '/' + threadSummary.getThreadNumber())) {
				threadSummaries.add(threadSummary);
			}
		}
		return threadSummaries;
	}
}
