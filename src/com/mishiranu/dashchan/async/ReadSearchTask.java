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

package com.mishiranu.dashchan.async;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpHolder;

import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.net.YouTubeTitlesReader;
import com.mishiranu.dashchan.text.HtmlParser;

public class ReadSearchTask extends CancellableTask<Void, Void, PostItem[]> implements Comparator<Post>
{
	private final Callback mCallback;
	private final String mChanName;
	private final String mBoardName;
	private final String mSearchQuery;
	
	private final HttpHolder mHolder = new HttpHolder();
	
	private ErrorItem mErrorItem;
	
	public static interface Callback
	{
		public void onReadSearchSuccess(PostItem[] postItems);
		public void onReadSearchFail(ErrorItem errorItem);
	}
	
	public ReadSearchTask(Callback callback, String chanName, String boardName, String searchQuery)
	{
		mCallback = callback;
		mChanName = chanName;
		mBoardName = boardName;
		mSearchQuery = searchQuery;
	}
	
	@Override
	public int compare(Post lhs, Post rhs)
	{
		return ((Long) rhs.getTimestamp()).compareTo(lhs.getTimestamp());
	}
	
	@Override
	protected PostItem[] doInBackground(Void... params)
	{
		try
		{
			ChanPerformer performer = ChanPerformer.get(mChanName);
			ChanConfiguration configuration = ChanConfiguration.get(mChanName);
			ChanConfiguration.Board board = configuration.safe().obtainBoard(mBoardName);
			ArrayList<Post> posts = new ArrayList<>();
			HashSet<String> postNumbers = null;
			if (board.allowSearch)
			{
				try
				{
					ChanPerformer.ReadSearchPostsResult result = performer.onReadSearchPosts(new ChanPerformer
							.ReadSearchPostsData(mBoardName, mSearchQuery, mHolder));
					Post[] readPosts = result != null ? result.posts : null;
					if (readPosts != null && readPosts.length > 0)
					{
						Collections.addAll(posts, readPosts);
						postNumbers = new HashSet<>();
						for (Post post : readPosts) postNumbers.add(post.getPostNumber());
					}
				}
				catch (LinkageError | RuntimeException e)
				{
					mErrorItem = ExtensionException.obtainErrorItemAndLogException(e);
					return null;
				}
			}
			if (board.allowCatalog && board.allowCatalogSearch)
			{
				ChanPerformer.ReadThreadsResult result = performer.onReadThreads(new ChanPerformer.ReadThreadsData
						(mBoardName, ChanPerformer.ReadThreadsData.PAGE_NUMBER_CATALOG, mHolder, null));
				Posts[] threads = result != null && result.threads != null ? result.threads.getThreads()[0] : null;
				ArrayList<Post> matched = new ArrayList<>();
				Locale locale = Locale.getDefault();
				String searchQuery = mSearchQuery.toUpperCase(locale);
				for (Posts thread : threads)
				{
					for (Post post : thread.getPosts())
					{
						if (postNumbers == null || !postNumbers.contains(post.getPostNumber()))
						{
							String comment = post.getComment();
							String subject = post.getSubject();
							if (comment != null && HtmlParser.clear(comment).toUpperCase(locale).contains(searchQuery)
									|| subject != null && subject.toUpperCase(locale).contains(searchQuery))
							{
								matched.add(post);
							}
						}
					}
				}
				posts.addAll(matched);
			}
			if (posts.size() > 0)
			{
				Collections.sort(posts, this);
				YouTubeTitlesReader.getInstance().readAndApplyIfNecessary(posts, mHolder);
			}
			PostItem[] postItems = ReadPostsTask.wrapPosts(posts, mChanName, mBoardName);
			if (postItems != null)
			{
				for (int i = 0; i < postItems.length; i++)
				{
					PostItem postItem = postItems[i];
					postItem.setOrdinalIndex(i);
					postItem.preload();
				}
			}
			else postItems = new PostItem[0];
			return postItems;
		}
		catch (HttpException | InvalidResponseException e)
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
	public void onPostExecute(PostItem[] postItems)
	{
		if (postItems != null)
		{
			if (postItems.length > 0) mCallback.onReadSearchSuccess(postItems);
			else mCallback.onReadSearchFail(new ErrorItem(ErrorItem.TYPE_NOT_FOUND));
		}
		else mCallback.onReadSearchFail(mErrorItem);
	}
	
	@Override
	public void cancel()
	{
		cancel(true);
		mHolder.interrupt();
	}
}