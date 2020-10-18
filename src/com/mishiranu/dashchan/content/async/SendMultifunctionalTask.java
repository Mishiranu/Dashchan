package com.mishiranu.dashchan.content.async;

import android.net.Uri;
import android.util.Pair;
import chan.content.ApiException;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import java.util.ArrayList;
import java.util.List;

public class SendMultifunctionalTask extends HttpHolderTask<Void, Void, Boolean> {
	private final State state;
	private final String type;
	private final String text;
	private final ArrayList<String> options;
	private final Callback callback;

	private String archiveBoardName;
	private String archiveThreadNumber;
	private ErrorItem errorItem;

	public enum Operation {DELETE, REPORT, ARCHIVE}

	public interface Callback {
		void onSendSuccess(State state, String archiveBoardName, String archiveThreadNumber);
		void onSendFail(State state, String type, String text, ArrayList<String> options, ErrorItem errorItem);
	}

	public static final String OPTION_FILES_ONLY = "filesOnly";

	public static class State {
		public Operation operation;
		public String chanName;
		public String boardName;
		public String threadNumber;

		public List<Pair<String, String>> types;
		public List<Pair<String, String>> options;

		public boolean commentField;

		public List<PostNumber> postNumbers;
		public String archiveThreadTitle;
		public String archiveChanName;

		public State(Operation operation, String chanName, String boardName, String threadNumber,
				List<Pair<String, String>> types, List<Pair<String, String>> options, boolean commentField) {
			this.operation = operation;
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.types = types;
			this.options = options;
			this.commentField = commentField;
		}
	}

	public SendMultifunctionalTask(State state, String type, String text, ArrayList<String> options,
			Callback callback) {
		this.state = state;
		this.type = type;
		this.text = text;
		this.options = options;
		this.callback = callback;
	}

	private static List<String> createPostNumberList(List<PostNumber> numbers) {
		ArrayList<String> postNumbers = new ArrayList<>(numbers.size());
		for (PostNumber number : numbers) {
			postNumbers.add(number.toString());
		}
		return postNumbers;
	}

	@Override
	protected Boolean doInBackground(HttpHolder holder, Void... params) {
		try {
			switch (state.operation) {
				case DELETE: {
					ChanPerformer.get(state.chanName).safe().onSendDeletePosts(new ChanPerformer
							.SendDeletePostsData(state.boardName, state.threadNumber,
							createPostNumberList(state.postNumbers), text,
							options != null && options.contains(OPTION_FILES_ONLY), holder));
					break;
				}
				case REPORT: {
					ChanPerformer.get(state.chanName).safe().onSendReportPosts(new ChanPerformer
							.SendReportPostsData(state.boardName, state.threadNumber,
							createPostNumberList(state.postNumbers), type, options, text, holder));
					break;
				}
				case ARCHIVE: {
					Uri uri = ChanLocator.get(state.chanName).safe(false)
							.createThreadUri(state.boardName, state.threadNumber);
					if (uri == null) {
						errorItem = new ErrorItem(ErrorItem.Type.UNKNOWN);
						return false;
					}
					ChanPerformer.SendAddToArchiveResult result = ChanPerformer.get(state.archiveChanName).safe()
							.onSendAddToArchive(new ChanPerformer.SendAddToArchiveData(uri, state.boardName,
									state.threadNumber, options, holder));
					if (result != null && result.threadNumber != null) {
						archiveBoardName = result.boardName;
						archiveThreadNumber = result.threadNumber;
					}
					break;
				}
			}
			return true;
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return false;
		} catch (ApiException e) {
			errorItem = e.getErrorItem();
			return false;
		} finally {
			ChanConfiguration.get(state.chanName).commit();
		}
	}

	@Override
	protected void onPostExecute(Boolean result) {
		if (result) {
			callback.onSendSuccess(state, archiveBoardName, archiveThreadNumber);
		} else {
			callback.onSendFail(state, type, text, options, errorItem);
		}
	}
}
