/*
 * Copyright 2014-2016 Fukurou Mishiranu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.content.async;

import java.io.Serializable;
import java.util.ArrayList;

import chan.content.ApiException;
import chan.content.ChanConfiguration;
import chan.content.ChanMarkup;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.RedirectException;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.MultipartEntity;
import chan.text.CommentEditor;

import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.net.RecaptchaReader;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.text.SimilarTextEstimator;

public class SendPostTask extends HttpHolderTask<Void, Long, Boolean>
{
	private final String mKey;
	private final String mChanName;
	private final Callback mCallback;
	private final ChanPerformer.SendPostData mData;

	private final boolean mProgressMode;

	private ChanPerformer.SendPostResult mResult;
	private ErrorItem mErrorItem;
	private Serializable mExtra;
	private boolean mCaptchaError = false;
	private boolean mKeepCaptcha = false;

	private final TimedProgressHandler mProgressHandler = new TimedProgressHandler()
	{
		@Override
		public void onProgressChange(long progress, long progressMax)
		{
			if (!mProgressMode)
			{
				publishProgress(0L, progress, progressMax);
			}
		}

		private final ArrayList<MultipartEntity.Openable> mCompleteOpenables = new ArrayList<>();
		private MultipartEntity.Openable mLastOpenable = null;

		@Override
		public void onProgressChange(MultipartEntity.Openable openable, long progress, long progressMax)
		{
			if (mProgressMode)
			{
				if (mLastOpenable != openable)
				{
					if (mLastOpenable != null)
					{
						mCompleteOpenables.add(mLastOpenable);
						if (mCompleteOpenables.size() == mData.attachments.length) mCompleteOpenables.clear();
					}
					mLastOpenable = openable;
				}
				publishProgress((long) mCompleteOpenables.size(), progress, progressMax);
			}
		}
	};

	public interface Callback
	{
		public void onSendPostChangeProgressState(String key, ProgressState progressState,
				int attachmentIndex, int attachmentsCount);
		public void onSendPostChangeProgressValue(String key, int progress, int progressMax);
		public void onSendPostSuccess(String key, ChanPerformer.SendPostData data,
				String chanName, String threadNumber, String postNumber);
		public void onSendPostFail(String key, ChanPerformer.SendPostData data, String chanName, ErrorItem errorItem,
				Serializable extra, boolean captchaError, boolean keepCaptcha);
	}

	public SendPostTask(String key, String chanName, Callback callback, ChanPerformer.SendPostData data)
	{
		mKey = key;
		mChanName = chanName;
		mCallback = callback;
		mData = data;
		mProgressMode = data.attachments != null;
		if (mProgressMode)
		{
			for (ChanPerformer.SendPostData.Attachment attachment : data.attachments)
			{
				attachment.listener = mProgressHandler;
			}
		}
	}

	public boolean isProgressMode()
	{
		return mProgressMode;
	}

	public enum ProgressState {CONNECTING, SENDING, PROCESSING}

	private ProgressState mLastProgressState = ProgressState.CONNECTING;

	private void switchProgressState(ProgressState progressState, int attachmentIndex, boolean force)
	{
		if (mLastProgressState != progressState || force)
		{
			mLastProgressState = progressState;
			if (mCallback != null)
			{
				mCallback.onSendPostChangeProgressState(mKey, progressState, attachmentIndex, mProgressMode
						? mData.attachments.length : 0);
			}
		}
	}

	private void updateProgressValue(int index, long progress, long progressMax)
	{
		if (progress == 0) switchProgressState(ProgressState.SENDING, index, true);
		else if (progress == progressMax) switchProgressState(ProgressState.PROCESSING, 0, false);
		if (mProgressMode && mCallback != null)
		{
			mCallback.onSendPostChangeProgressValue(mKey, (int) (progress / 1024), (int) (progressMax / 1024));
		}
	}

	@Override
	protected Boolean doInBackground(HttpHolder holder, Void... params)
	{
		try
		{
			ChanPerformer.SendPostData data = mData;
			if (data.captchaData != null && ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2.equals(data.captchaType))
			{
				String apiKey = data.captchaData.get(ChanPerformer.CaptchaData.API_KEY);
				String recaptchaResponse = data.captchaData.get(ReadCaptchaTask.RECAPTCHA_SKIP_RESPONSE);
				if (apiKey != null && recaptchaResponse == null)
				{
					String challenge = data.captchaData.get(ChanPerformer.CaptchaData.CHALLENGE);
					String input = data.captchaData.get(ChanPerformer.CaptchaData.INPUT);
					recaptchaResponse = RecaptchaReader.getInstance().getResponseField2(holder, apiKey,
							challenge, input, Preferences.isRecaptchaJavascript());
					if (recaptchaResponse == null) throw new ApiException(ApiException.SEND_ERROR_CAPTCHA);
				}
				data.captchaData.put(ChanPerformer.CaptchaData.INPUT, recaptchaResponse);
			}
			if (isCancelled()) return false;
			data.holder = holder;
			data.listener = mProgressHandler;
			ChanPerformer performer = ChanPerformer.get(mChanName);
			ChanPerformer.SendPostResult result = performer.safe().onSendPost(data);
			if (data.threadNumber == null && (result == null || result.threadNumber == null))
			{
				// New thread created with undefined number
				ChanPerformer.ReadThreadsResult readThreadsResult;
				try
				{
					readThreadsResult = performer.safe().onReadThreads(new ChanPerformer
							.ReadThreadsData(data.boardName, 0, data.holder, null));
				}
				catch (RedirectException e)
				{
					readThreadsResult = null;
				}
				Posts[] threads = readThreadsResult != null ? readThreadsResult.threads : null;
				if (threads != null && threads.length > 0)
				{
					String postComment = data.comment;
					CommentEditor commentEditor = ChanMarkup.get(mChanName).safe().obtainCommentEditor(data.boardName);
					if (commentEditor != null && postComment != null)
					{
						postComment = commentEditor.removeTags(postComment);
					}
					SimilarTextEstimator estimator = new SimilarTextEstimator(Integer.MAX_VALUE, true);
					SimilarTextEstimator.WordsData wordsData1 = estimator.getWords(postComment);
					for (Posts thread : threads)
					{
						Post[] posts = thread.getPosts();
						if (posts != null && posts.length > 0)
						{
							Post post = posts[0];
							String comment = HtmlParser.clear(post.getComment());
							SimilarTextEstimator.WordsData wordsData2 = estimator.getWords(comment);
							if (estimator.checkSimiliar(wordsData1, wordsData2)
									|| wordsData1 == null && wordsData2 == null)
							{
								result = new ChanPerformer.SendPostResult(thread.getThreadNumber(), null);
								break;
							}
						}
					}
				}
			}
			mResult = result;
			return true;
		}
		catch (ExtensionException | HttpException | InvalidResponseException e)
		{
			mErrorItem = e.getErrorItemAndHandle();
			return false;
		}
		catch (ApiException e)
		{
			mErrorItem = e.getErrorItem();
			mExtra = e.getExtra();
			int errorType = e.getErrorType();
			mCaptchaError = errorType == ApiException.SEND_ERROR_CAPTCHA;
			mKeepCaptcha = !mCaptchaError && e.checkFlag(ApiException.FLAG_KEEP_CAPTCHA);
			return false;
		}
		finally
		{
			ChanConfiguration.get(mChanName).commit();
		}
	}

	@Override
	protected void onProgressUpdate(Long... values)
	{
		int index = values[0].intValue();
		long progress = values[1];
		long progressMax = values[2];
		updateProgressValue(index, progress, progressMax);
	}

	@Override
	protected void onPostExecute(final Boolean result)
	{
		if (mCallback != null)
		{
			if (result)
			{
				mCallback.onSendPostSuccess(mKey, mData, mChanName, mResult != null ? mResult.threadNumber : null,
						mResult != null ? mResult.postNumber : null);
			}
			else mCallback.onSendPostFail(mKey, mData, mChanName, mErrorItem, mExtra, mCaptchaError, mKeepCaptcha);
		}
	}
}