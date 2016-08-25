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
import java.util.ArrayList;
import java.util.Collections;

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpValidator;

import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.net.YouTubeTitlesReader;

public class ReadThreadsTask extends HttpHolderTask<Void, Void, Boolean>
{
	private final Callback mCallback;
	private final String mChanName;
	private final String mBoardName;
	private final int mPageNumber;
	private final HttpValidator mValidator;
	private final boolean mAppend;
	
	private ArrayList<PostItem> mPostItems;
	private int mBoardSpeed = 0;
	private HttpValidator mResultValidator;
	private ErrorItem mErrorItem;
	
	public interface Callback
	{
		public void onReadThreadsSuccess(ArrayList<PostItem> postItems, int pageNumber,
				int boardSpeed, boolean append, boolean checkModified, HttpValidator validator);
		public void onReadThreadsFail(ErrorItem errorItem, int pageNumber);
	}
	
	public ReadThreadsTask(Callback callback, String chanName, String boardName, int pageNumber,
			HttpValidator validator, boolean append)
	{
		mCallback = callback;
		mChanName = chanName;
		mBoardName = boardName;
		mPageNumber = pageNumber;
		mValidator = validator;
		mAppend = append;
	}
	
	private boolean isFirstPage()
	{
		return mPageNumber == 0 || mPageNumber == ChanPerformer.ReadThreadsData.PAGE_NUMBER_CATALOG;
	}
	
	@Override
	protected Boolean doInBackground(Void... params)
	{
		try
		{
			ChanPerformer performer = ChanPerformer.get(mChanName);
			ChanPerformer.ReadThreadsResult result = performer.safe()
					.onReadThreads(new ChanPerformer.ReadThreadsData(mBoardName, mPageNumber, getHolder(), mValidator));
			Posts[] threadsArray = result != null ? result.threads : null;
			ArrayList<PostItem> postItems = null;
			int boardSpeed = result != null ? result.boardSpeed : 0;
			HttpValidator validator = result != null ? result.validator : null;
			if (threadsArray != null && threadsArray.length > 0)
			{
				ArrayList<Post> posts = new ArrayList<>();
				for (Posts thread : threadsArray)
				{
					Post[] postsArray = thread.getPosts();
					if (postsArray != null) Collections.addAll(posts, postsArray);
				}
				YouTubeTitlesReader.getInstance().readAndApplyIfNecessary(posts, getHolder());
				for (Posts thread : threadsArray)
				{
					Post[] postsArray = thread.getPosts();
					if (postsArray != null && postsArray.length > 0)
					{
						if (postItems == null) postItems = new ArrayList<>();
						postItems.add(new PostItem(thread, mChanName, mBoardName));
					}
				}
			}
			if (validator == null) validator = getHolder().getValidator();
			mPostItems = postItems;
			mBoardSpeed = boardSpeed;
			mResultValidator = validator;
			return true;
		}
		catch (HttpException e)
		{
			if (e.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) return true;
			if (isFirstPage() && e.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND)
			{
				mErrorItem = new ErrorItem(ErrorItem.TYPE_BOARD_NOT_EXISTS);
			}
			else mErrorItem = e.getErrorItemAndHandle();
			return false;
		}
		catch (ExtensionException | InvalidResponseException e)
		{
			mErrorItem = e.getErrorItemAndHandle();
			return false;
		}
		finally
		{
			ChanConfiguration.get(mChanName).commit();
		}
	}
	
	@Override
	public void onPostExecute(Boolean success)
	{
		if (success)
		{
			mCallback.onReadThreadsSuccess(mPostItems, mPageNumber, mBoardSpeed, mAppend,
					mValidator != null, mResultValidator);
		}
		else mCallback.onReadThreadsFail(mErrorItem, mPageNumber);
	}
}