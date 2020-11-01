package com.mishiranu.dashchan.content.async;

import chan.content.ApiException;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.RedirectException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.MultipartEntity;
import chan.text.CommentEditor;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.Post;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.text.SimilarTextEstimator;
import java.util.ArrayList;
import java.util.List;

public class SendPostTask<Key> extends ExecutorTask<long[], Boolean> {
	private final HttpHolder chanHolder;
	private final HttpHolder fallbackHolder = new HttpHolder(Chan.getFallback());

	private final Key key;
	private final Callback<Key> callback;
	private final Chan chan;
	private final ChanPerformer.SendPostData data;

	private final boolean progressMode;

	private ChanPerformer.SendPostResult result;
	private ErrorItem errorItem;
	private ApiException.Extra extra;
	private boolean captchaError = false;
	private boolean keepCaptcha = false;

	private final TimedProgressHandler progressHandler = new TimedProgressHandler() {
		@Override
		public void onProgressChange(long progress, long progressMax) {
			if (!progressMode) {
				notifyProgress(new long[] {0L, progress, progressMax});
			}
		}

		private final ArrayList<MultipartEntity.Openable> completeOpenables = new ArrayList<>();
		private MultipartEntity.Openable lastOpenable = null;

		@Override
		public void onProgressChange(MultipartEntity.Openable openable, long progress, long progressMax) {
			if (progressMode) {
				if (lastOpenable != openable) {
					if (lastOpenable != null) {
						completeOpenables.add(lastOpenable);
						if (completeOpenables.size() == data.attachments.length) {
							completeOpenables.clear();
						}
					}
					lastOpenable = openable;
				}
				notifyProgress(new long[] {completeOpenables.size(), progress, progressMax});
			}
		}
	};

	public interface Callback<Key> {
		void onSendPostChangeProgressState(Key key, ProgressState progressState,
				int attachmentIndex, int attachmentsCount);
		void onSendPostChangeProgressValue(Key key, long progress, long progressMax);
		void onSendPostSuccess(Key key, ChanPerformer.SendPostData data,
				String chanName, String threadNumber, PostNumber postNumber);
		void onSendPostFail(Key key, ChanPerformer.SendPostData data, String chanName, ErrorItem errorItem,
				ApiException.Extra extra, boolean captchaError, boolean keepCaptcha);
	}

	public SendPostTask(Key key, Callback<Key> callback, Chan chan, ChanPerformer.SendPostData data) {
		chanHolder = new HttpHolder(chan);
		this.key = key;
		this.callback = callback;
		this.chan = chan;
		this.data = data;
		progressMode = data.attachments != null;
		if (progressMode) {
			for (ChanPerformer.SendPostData.Attachment attachment : data.attachments) {
				attachment.listener = progressHandler;
			}
		}
	}

	public boolean isProgressMode() {
		return progressMode;
	}

	public enum ProgressState {CONNECTING, SENDING, PROCESSING}

	private ProgressState lastProgressState = ProgressState.CONNECTING;

	private void switchProgressState(ProgressState progressState, int attachmentIndex, boolean force) {
		if (lastProgressState != progressState || force) {
			lastProgressState = progressState;
			if (callback != null) {
				callback.onSendPostChangeProgressState(key, progressState, attachmentIndex, progressMode
						? data.attachments.length : 0);
			}
		}
	}

	private void updateProgressValue(int index, long progress, long progressMax) {
		if (progress == 0) {
			switchProgressState(ProgressState.SENDING, index, true);
		} else if (progress == progressMax) {
			switchProgressState(ProgressState.PROCESSING, 0, false);
		}
		if (progressMode && callback != null) {
			callback.onSendPostChangeProgressValue(key, progress, progressMax);
		}
	}

	@Override
	protected Boolean run() {
		try (HttpHolder.Use ignore1 = chanHolder.use()) {
			ChanPerformer.SendPostData data = this.data;
			if (data.captchaNeedLoad) {
				boolean success = false;
				if (data.captchaData != null) {
					try (HttpHolder.Use ignore2 = fallbackHolder.use()) {
						String response = ReadCaptchaTask.readForegroundCaptcha(fallbackHolder,
							chan.name, data.captchaData, data.captchaType);
						if (response != null) {
							data.captchaData.put(ChanPerformer.CaptchaData.INPUT, response);
							success = true;
						}
					} catch (InterruptedException e) {
						errorItem = new ErrorItem(ErrorItem.Type.UNKNOWN);
						return false;
					}
				}
				if (!success) {
					// Don't switch captchaError
					errorItem = new ErrorItem(ErrorItem.Type.API, ApiException.SEND_ERROR_CAPTCHA);
					return false;
				}
			} else if (data.captchaData != null &&
					(ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2.equals(data.captchaType) ||
							ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE.equals(data.captchaType) ||
							ChanConfiguration.CAPTCHA_TYPE_HCAPTCHA.equals(data.captchaType))) {
				data.captchaData.put(ChanPerformer.CaptchaData.INPUT,
						data.captchaData.get(ReadCaptchaTask.RECAPTCHA_SKIP_RESPONSE));
			}
			if (isCancelled()) {
				return false;
			}
			data.holder = chanHolder;
			data.listener = progressHandler;
			ChanPerformer.SendPostResult result = chan.performer.safe().onSendPost(data);
			if (data.threadNumber == null && (result == null || result.threadNumber == null)) {
				// New thread created with undefined number
				ChanPerformer.ReadThreadsResult readThreadsResult;
				try {
					readThreadsResult = chan.performer.safe().onReadThreads(new ChanPerformer
							.ReadThreadsData(data.boardName, 0, data.holder, null));
				} catch (RedirectException e) {
					readThreadsResult = null;
				}
				List<ChanPerformer.ReadThreadsResult.Thread> threads = readThreadsResult != null
						? readThreadsResult.threads : null;
				if (threads != null && !threads.isEmpty()) {
					String postComment = data.comment;
					CommentEditor commentEditor = chan.markup.safe().obtainCommentEditor(data.boardName);
					if (commentEditor != null && postComment != null) {
						postComment = commentEditor.removeTags(postComment);
					}
					SimilarTextEstimator estimator = new SimilarTextEstimator(Integer.MAX_VALUE, true);
					SimilarTextEstimator.WordsData<Void> wordsData1 = estimator.getWords(postComment);
					for (ChanPerformer.ReadThreadsResult.Thread thread : threads) {
						Post post = thread.posts.get(0);
						String comment = HtmlParser.clear(post.comment);
						SimilarTextEstimator.WordsData<Void> wordsData2 = estimator.getWords(comment);
						if (estimator.checkSimiliar(wordsData1, wordsData2)
								|| wordsData1 == null && wordsData2 == null) {
							result = new ChanPerformer.SendPostResult(thread.threadNumber, null);
							break;
						}
					}
				}
			}
			this.result = result;
			return true;
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return false;
		} catch (ApiException e) {
			errorItem = e.getErrorItem();
			extra = e.getExtra();
			int errorType = e.getErrorType();
			captchaError = errorType == ApiException.SEND_ERROR_CAPTCHA;
			keepCaptcha = !captchaError && e.checkFlag(ApiException.FLAG_KEEP_CAPTCHA);
			return false;
		} finally {
			chan.configuration.commit();
		}
	}

	@Override
	public void cancel() {
		super.cancel();
		chanHolder.interrupt();
		fallbackHolder.interrupt();
	}

	@Override
	protected void onProgress(long[] values) {
		int index = (int) values[0];
		long progress = values[1];
		long progressMax = values[2];
		updateProgressValue(index, progress, progressMax);
	}

	@Override
	protected void onComplete(Boolean success) {
		if (callback != null) {
			if (success) {
				callback.onSendPostSuccess(key, data, chan.name,
						this.result != null ? this.result.threadNumber : null,
						this.result != null ? this.result.postNumber : null);
			} else {
				callback.onSendPostFail(key, data, chan.name, errorItem, extra, captchaError, keepCaptcha);
			}
		}
	}
}
