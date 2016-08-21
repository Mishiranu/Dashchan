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
import java.util.List;

import android.net.Uri;
import android.util.Pair;

import chan.content.ApiException;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpException;

import com.mishiranu.dashchan.content.model.ErrorItem;

public class SendMultifunctionalTask extends HttpHolderTask<Void, Void, Boolean>
{
	private final State mState;
	private final String mType;
	private final String mText;
	private final ArrayList<String> mOptions;
	private final Callback mCallback;

	private String mArchiveBoardName;
	private String mArchiveThreadNumber;
	private ErrorItem mErrorItem;
	
	public enum Operation {DELETE, REPORT, ARCHIVE}
	
	public interface Callback
	{
		public void onSendSuccess(State state, String archiveBoardName, String archiveThreadNumber);
		public void onSendFail(State state, String type, String text, ArrayList<String> options, ErrorItem errorItem);
	}
	
	public static final String OPTION_FILES_ONLY = "filesOnly";
	
	public static class State
	{
		public Operation operation;
		public String chanName;
		public String boardName;
		public String threadNumber;
		
		public List<Pair<String, String>> types;
		public List<Pair<String, String>> options;
		
		public boolean commentField;
		
		public List<String> postNumbers;
		public String archiveThreadTitle;
		public String archiveChanName;
		
		public State(Operation operation, String chanName, String boardName, String threadNumber,
				List<Pair<String, String>> types, List<Pair<String, String>> options, boolean commentField)
		{
			this.operation = operation;
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.types = types;
			this.options = options;
			this.commentField = commentField;
		}
	}
	
	public SendMultifunctionalTask(State state, String type, String text, ArrayList<String> options, Callback callback)
	{
		mState = state;
		mType = type;
		mText = text;
		mOptions = options;
		mCallback = callback;
	}
	
	@Override
	protected Boolean doInBackground(Void... params)
	{
		try
		{
			switch (mState.operation)
			{
				case DELETE:
				{
					ChanPerformer.get(mState.chanName).safe().onSendDeletePosts(new ChanPerformer.SendDeletePostsData
							(mState.boardName, mState.threadNumber, Collections.unmodifiableList(mState.postNumbers),
							mText, mOptions != null && mOptions.contains(OPTION_FILES_ONLY), getHolder()));
					break;
				}
				case REPORT:
				{
					ChanPerformer.get(mState.chanName).safe().onSendReportPosts(new ChanPerformer.SendReportPostsData
							(mState.boardName, mState.threadNumber, Collections.unmodifiableList(mState.postNumbers),
							mType, mOptions, mText, getHolder()));
					break;
				}
				case ARCHIVE:
				{
					Uri uri = ChanLocator.get(mState.chanName).safe(false).createThreadUri(mState.boardName,
							mState.threadNumber);
					if (uri == null)
					{
						mErrorItem = new ErrorItem(ErrorItem.TYPE_UNKNOWN);
						return false;
					}
					ChanPerformer.SendAddToArchiveResult result = ChanPerformer.get(mState.archiveChanName).safe()
							.onSendAddToArchive(new ChanPerformer.SendAddToArchiveData(uri, mState.boardName,
							mState.threadNumber, mOptions, getHolder()));
					if (result != null && result.threadNumber != null)
					{
						mArchiveBoardName = result.boardName;
						mArchiveThreadNumber = result.threadNumber;
					}
					break;
				}
			}
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
			return false;
		}
		finally
		{
			ChanConfiguration.get(mState.chanName).commit();
		}
	}
	
	@Override
	protected void onPostExecute(Boolean result)
	{
		if (result) mCallback.onSendSuccess(mState, mArchiveBoardName, mArchiveThreadNumber);
		else mCallback.onSendFail(mState, mType, mText, mOptions, mErrorItem);
	}
}