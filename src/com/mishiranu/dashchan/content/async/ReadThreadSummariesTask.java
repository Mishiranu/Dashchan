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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.ThreadSummary;
import chan.http.HttpException;
import chan.util.CommonUtils;

import com.mishiranu.dashchan.content.model.ErrorItem;

public class ReadThreadSummariesTask extends HttpHolderTask<Void, Void, ThreadSummary[]>
{
	private final String mChanName;
	private final String mBoardName;
	private final int mPageNumber;
	private final int mType;
	private final Callback mCallback;

	private ErrorItem mErrorItem;

	public interface Callback
	{
		public void onReadThreadSummariesSuccess(ThreadSummary[] threadSummaries, int pageNumber);
		public void onReadThreadSummariesFail(ErrorItem errorItem);
	}

	public ReadThreadSummariesTask(String chanName, String boardName, int pageNumber, int type, Callback callback)
	{
		mChanName = chanName;
		mBoardName = boardName;
		mPageNumber = pageNumber;
		mType = type;
		mCallback = callback;
	}

	@Override
	protected ThreadSummary[] doInBackground(Void... params)
	{
		try
		{
			ChanPerformer performer = ChanPerformer.get(mChanName);
			ChanPerformer.ReadThreadSummariesResult result = performer.safe().onReadThreadSummaries(new ChanPerformer
					.ReadThreadSummariesData(mBoardName, mPageNumber, mType, getHolder()));
			ThreadSummary[] threadSummaries = result != null ? result.threadSummaries : null;
			return threadSummaries != null && threadSummaries.length > 0 ? threadSummaries : null;
		}
		catch (ExtensionException | HttpException | InvalidResponseException e)
		{
			mErrorItem = e.getErrorItemAndHandle();
			return null;
		}
		finally
		{
			ChanConfiguration.get(mChanName).commit();
		}
	}

	@Override
	public void onPostExecute(ThreadSummary[] threadSummaries)
	{
		if (mErrorItem == null) mCallback.onReadThreadSummariesSuccess(threadSummaries, mPageNumber);
		else mCallback.onReadThreadSummariesFail(mErrorItem);
	}

	public static ThreadSummary[] concatenate(ThreadSummary[] threadSummaries1, ThreadSummary[] threadSummaries2)
	{
		ArrayList<ThreadSummary> threadSummaries = new ArrayList<>();
		if (threadSummaries1 != null) Collections.addAll(threadSummaries, threadSummaries1);
		HashSet<String> identifiers = new HashSet<>();
		for (ThreadSummary threadSummary : threadSummaries)
		{
			identifiers.add(threadSummary.getBoardName() + '/' + threadSummary.getThreadNumber());
		}
		if (threadSummaries2 != null)
		{
			for (ThreadSummary threadSummary : threadSummaries2)
			{
				if (!identifiers.contains(threadSummary.getBoardName() + '/' + threadSummary.getThreadNumber()))
				{
					threadSummaries.add(threadSummary);
				}
			}
		}
		return CommonUtils.toArray(threadSummaries, ThreadSummary.class);
	}
}