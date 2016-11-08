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

import java.net.HttpURLConnection;
import java.util.Collections;

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.Post;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.util.CommonUtils;

import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.net.YouTubeTitlesReader;

public class ReadSinglePostTask extends HttpHolderTask<Void, Void, PostItem> {
	private final Callback mCallback;
	private final String mBoardName;
	private final String mChanName;
	private final String mPostNumber;

	private ErrorItem mErrorItem;

	public interface Callback {
		public void onReadSinglePostSuccess(PostItem postItem);
		public void onReadSinglePostFail(ErrorItem errorItem);
	}

	public ReadSinglePostTask(Callback callback, String chanName, String boardName, String postNumber) {
		mCallback = callback;
		mBoardName = boardName;
		mChanName = chanName;
		mPostNumber = postNumber;
	}

	@Override
	protected PostItem doInBackground(HttpHolder holder, Void... params) {
		long startTime = System.currentTimeMillis();
		try {
			ChanPerformer performer = ChanPerformer.get(mChanName);
			ChanPerformer.ReadSinglePostResult result = performer.safe().onReadSinglePost(new ChanPerformer
					.ReadSinglePostData(mBoardName, mPostNumber, holder));
			Post post = result != null ? result.post : null;
			YouTubeTitlesReader.getInstance().readAndApplyIfNecessary(Collections.singletonList(post), holder);
			startTime = 0L;
			return new PostItem(post, mChanName, mBoardName);
		} catch (HttpException e) {
			mErrorItem = e.getErrorItemAndHandle();
			if (mErrorItem.httpResponseCode == HttpURLConnection.HTTP_NOT_FOUND) {
				mErrorItem = new ErrorItem(ErrorItem.TYPE_POST_NOT_FOUND);
			}
		} catch (ExtensionException | InvalidResponseException e) {
			mErrorItem = e.getErrorItemAndHandle();
		} finally {
			ChanConfiguration.get(mChanName).commit();
			CommonUtils.sleepMaxTime(startTime, 500);
		}
		return null;
	}

	@Override
	protected void onPostExecute(PostItem result) {
		if (result != null) {
			mCallback.onReadSinglePostSuccess(result);
		} else {
			mCallback.onReadSinglePostFail(mErrorItem);
		}
	}
}