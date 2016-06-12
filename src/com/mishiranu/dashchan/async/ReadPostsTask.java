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

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.ThreadRedirectException;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpValidator;

import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.net.YouTubeTitlesReader;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.text.SimilarTextEstimator;
import com.mishiranu.dashchan.util.Log;

public class ReadPostsTask extends CancellableTask<Void, Void, Boolean>
{
	private final Callback mCallback;
	private final String mChanName;
	private final String mBoardName;
	private final String mThreadNumber;
	private final Posts mCachedPosts;
	private final HttpValidator mValidator;
	private final boolean mForceLoadFullThread;
	private final String mLastPostNumber;
	private final ArrayList<UserPostPending> mUserPostPendings;
	
	private final HttpHolder mHolder = new HttpHolder();
	
	private Posts mPostsModel;
	private PostItem[] mHandlePostItems;
	private Post[] mHandlePosts;
	private Posts.MergeAction[] mMergeActions;
	private Posts mReadPosts;
	private String mRedirectBoardName;
	private String mRedirectThreadNumber;
	private String mRedirectPostNumber;
	private ArrayList<UserPostPending> mRemovedUserPostPendings;
	private ErrorItem mErrorItem;
	
	private boolean mFullThread = false;
	private int mNewCount = 0;
	private int mDeletedCount = 0;
	private boolean mHasEdited = false;
	
	public static class ResultItems
	{
		public PostItem[] handlePostItems;
		public Post[] handlePosts;
		public Posts.MergeAction[] mergeActions;
		public Posts readPosts;
		public int newCount;
		public int deletedCount;
		public boolean hasEdited;
	}
	
	public static interface Callback
	{
		public void onRequestPreloadPosts(PostItem[] postItems);
		public void onReadPostsSuccess(Posts posts, ResultItems resultItems, boolean fullThread,
				ArrayList<UserPostPending> removedUserPostPendings);
		public void onReadPostsRedirect(String boardName, String threadNumber, String postNumber);
		public void onReadPostsFail(ErrorItem errorItem);
	}
	
	public ReadPostsTask(Callback callback, String chanName, String boardName, String threadNumber, Posts cachedPosts,
			boolean useValidator, boolean forceLoadFullThread, String lastPostNumber,
			ArrayList<UserPostPending> userPostPendings)
	{
		mCallback = callback;
		mChanName = chanName;
		mBoardName = boardName;
		mThreadNumber = threadNumber;
		mCachedPosts = cachedPosts;
		mValidator = useValidator && cachedPosts != null ? cachedPosts.getValidator() : null;
		mForceLoadFullThread = forceLoadFullThread;
		mLastPostNumber = lastPostNumber;
		mUserPostPendings = userPostPendings.size() > 0 ? userPostPendings : null;
	}
	
	@Override
	protected Boolean doInBackground(Void... params)
	{
		String lastPostNumber = mForceLoadFullThread ? null : mLastPostNumber;
		boolean partialThreadLoading = Preferences.isPartialThreadLoading(mChanName) && !mForceLoadFullThread;
		ChanPerformer performer = ChanPerformer.get(mChanName);
		try
		{
			Posts readPosts;
			HttpValidator validator;
			try
			{
				ChanPerformer.ReadPostsResult result = performer.onReadPosts(new ChanPerformer
						.ReadPostsData(mBoardName, mThreadNumber, lastPostNumber, partialThreadLoading,
						mCachedPosts, mHolder, mValidator));
				readPosts = result != null ? result.posts : null;
				validator = result != null ? result.validator : null;
				if (result != null && result.fullThread) partialThreadLoading = false;
			}
			catch (LinkageError | RuntimeException e)
			{
				mErrorItem = ExtensionException.obtainErrorItemAndLogException(e);
				return false;
			}
			Posts fullPosts = mCachedPosts;
			if (readPosts != null) readPosts.removeRepeatingsAndSort();
			
			if (readPosts != null && readPosts.length() > 0)
			{
				Post[] posts = readPosts.getPosts();
				Post firstPost = fullPosts != null ? fullPosts.getPosts()[0] : posts[0];
				String firstPostNumber = firstPost.getPostNumber();
				if (firstPostNumber == null)
				{
					Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES, "The getPostNumber() method of",
							"original post returned null.");
					mErrorItem = new ErrorItem(ErrorItem.TYPE_INVALID_DATA_FORMAT);
					return false;
				}
				if (firstPost.getParentPostNumberOrNull() != null)
				{
					Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES, "The getParentPostNumber() method of",
							"original post must return null, \"0\" or post number.");
					mErrorItem = new ErrorItem(ErrorItem.TYPE_INVALID_DATA_FORMAT);
					return false;
				}
				boolean resultWithOriginalPost = posts[0].getPostNumber().equals(firstPostNumber);
				for (int i = 0; i < posts.length; i++)
				{
					Post post = posts[i];
					String postNumber = post.getPostNumber();
					if (postNumber == null)
					{
						Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES, "The getPostNumber() method of", i,
								"post returned null.");
						mErrorItem = new ErrorItem(ErrorItem.TYPE_INVALID_DATA_FORMAT);
						return false;
					}
					if (!mThreadNumber.equals(post.getThreadNumberOrOriginalPostNumber()))
					{
						Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES, "The number of requested thread and",
								"number of thread in post", postNumber, "are not equal.");
						mErrorItem = new ErrorItem(ErrorItem.TYPE_INVALID_DATA_FORMAT);
						return false;
					}
					if (!resultWithOriginalPost || i > 0)
					{
						if (!firstPostNumber.equals(post.getParentPostNumberOrNull()))
						{
							Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES, "The getParentPostNumber()",
									"method of post", postNumber, "is not equal to original post's number.");
							mErrorItem = new ErrorItem(ErrorItem.TYPE_INVALID_DATA_FORMAT);
							return false;
						}
					}
				}
			}
			
			Post[] handlePosts = null;
			if (fullPosts != null)
			{
				boolean partial = partialThreadLoading && lastPostNumber != null;
				Posts.MergeResult mergeResult = fullPosts.pendingMerge(readPosts, partial);
				mNewCount = mergeResult.newCount;
				mDeletedCount = mergeResult.deletedCount;
				mHasEdited = mergeResult.hasEdited;
				handlePosts = mergeResult.changed;
				mMergeActions = mergeResult.actions;
			}
			else if (readPosts != null && readPosts.length() > 0)
			{
				fullPosts = readPosts;
				handlePosts = readPosts.getPosts();
				mNewCount = readPosts.length();
				mFullThread = true;
			}
			
			if (handlePosts != null && handlePosts.length > 0)
			{
				mHandlePosts = handlePosts;
				if (mUserPostPendings != null)
				{
					ArrayList<UserPostPending> workUserPostPendings = new ArrayList<>(mUserPostPendings);
					OUTER : for (int i = handlePosts.length - 1; i >= 0; i--)
					{
						Post post = handlePosts[i];
						for (int j = workUserPostPendings.size() - 1; j >= 0; j--)
						{
							UserPostPending userPostPending = workUserPostPendings.get(j);
							if (userPostPending.isUserPost(post))
							{
								post.setUserPost(true);
								workUserPostPendings.remove(j);
								if (mRemovedUserPostPendings == null) mRemovedUserPostPendings = new ArrayList<>();
								mRemovedUserPostPendings.add(userPostPending);
								if (workUserPostPendings.isEmpty()) break OUTER;
							}
						}
					}
				}
				YouTubeTitlesReader.getInstance().readAndApplyIfNecessary(handlePosts, mHolder);
			}
			PostItem[] handlePostItems = wrapPosts(handlePosts, mChanName, mBoardName);
			if (handlePostItems != null) mCallback.onRequestPreloadPosts(handlePostItems);
			if (fullPosts != null)
			{
				if (validator == null) validator = mHolder.getValidator();
				if (validator != null) fullPosts.setValidator(validator);
			}
			mPostsModel = fullPosts;
			mHandlePostItems = handlePostItems;
			mReadPosts = readPosts;
			return true;
		}
		catch (HttpException e)
		{
			int responseCode = e.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED)
			{
				mPostsModel = mCachedPosts;
				return true;
			}
			if (responseCode == HttpURLConnection.HTTP_NOT_FOUND)
			{
				if (ChanConfiguration.get(mChanName).getOption(ChanConfiguration.OPTION_READ_SINGLE_POST))
				{
					try
					{
						Post post;
						try
						{
							ChanPerformer.ReadSinglePostResult result = performer.onReadSinglePost
									(new ChanPerformer.ReadSinglePostData(mBoardName, mThreadNumber, mHolder));
							post = result != null ? result.post : null;
						}
						catch (LinkageError | RuntimeException e2)
						{
							mErrorItem = ExtensionException.obtainErrorItemAndLogException(e2);
							return false;
						}
						String threadNumber = post.getThreadNumberOrOriginalPostNumber();
						if (threadNumber != null && !threadNumber.equals(mThreadNumber))
						{
							mRedirectThreadNumber = threadNumber;
							mRedirectPostNumber = post.getPostNumber();
							return true;
						}
					}
					catch (HttpException | InvalidResponseException e2)
					{
						
					}
				}
				mErrorItem = new ErrorItem(ErrorItem.TYPE_THREAD_NOT_EXISTS);
			}
			else mErrorItem = e.getErrorItemAndHandle();
			return false;
		}
		catch (ThreadRedirectException e)
		{
			mRedirectBoardName = e.getBoardName();
			if (mRedirectBoardName == null) mRedirectBoardName = mBoardName;
			mRedirectThreadNumber = e.getThreadNumber();
			mRedirectPostNumber = e.getPostNumber();
			return true;
		}
		catch (InvalidResponseException e)
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
			if (mRedirectThreadNumber != null)
			{
				mCallback.onReadPostsRedirect(mRedirectBoardName != null ? mRedirectBoardName : mBoardName,
						mRedirectThreadNumber, mRedirectPostNumber);
			}
			else
			{
				ResultItems resultItems = new ResultItems();
				resultItems.handlePostItems = mHandlePostItems;
				resultItems.handlePosts = mHandlePosts;
				resultItems.mergeActions = mMergeActions;
				resultItems.readPosts = mReadPosts;
				resultItems.newCount = mNewCount;
				resultItems.deletedCount = mDeletedCount;
				resultItems.hasEdited = mHasEdited;
				mCallback.onReadPostsSuccess(mPostsModel, resultItems, mFullThread, mRemovedUserPostPendings);
			}
		}
		else
		{
			mCallback.onReadPostsFail(mErrorItem);
		}
	}
	
	@Override
	public void cancel()
	{
		cancel(true);
		mHolder.interrupt();
	}
	
	static PostItem[] wrapPosts(Posts posts, String chanName, String boardName)
	{
		return posts != null ? wrapPosts(posts.getPosts(), chanName, boardName) : null;
	}
	
	static PostItem[] wrapPosts(Post[] posts, String chanName, String boardName)
	{
		if (posts == null || posts.length == 0) return null;
		return wrapPosts(Arrays.asList(posts), chanName, boardName);
	}
	
	static PostItem[] wrapPosts(List<Post> posts, String chanName, String boardName)
	{
		if (posts == null || posts.size() == 0) return null;
		PostItem[] postItems = new PostItem[posts.size()];
		Thread thread = Thread.currentThread();
		for (int i = 0, length = posts.size(); i < length && !thread.isInterrupted(); i++)
		{
			postItems[i] = new PostItem(posts.get(i), chanName, boardName);
		}
		return postItems;
	}
	
	public static interface UserPostPending extends Parcelable
	{
		public boolean isUserPost(Post post);
	}
	
	public static class PostNumberUserPostPending implements UserPostPending
	{
		private final String mPostNumber;
		
		public PostNumberUserPostPending(String postNumber)
		{
			mPostNumber = postNumber;
		}
		
		@Override
		public boolean isUserPost(Post post)
		{
			return mPostNumber.equals(post.getPostNumber());
		}
		
		@Override
		public boolean equals(Object o)
		{
			if (o == this) return true;
			if (o instanceof PostNumberUserPostPending)
			{
				return ((PostNumberUserPostPending) o).mPostNumber.equals(mPostNumber);
			}
			return false;
		}
		
		@Override
		public int hashCode()
		{
			return mPostNumber.hashCode();
		}
		
		@Override
		public int describeContents()
		{
			return 0;
		}
		
		@Override
		public void writeToParcel(Parcel dest, int flags)
		{
			dest.writeString(mPostNumber);
		}
		
		public static final Creator<PostNumberUserPostPending> CREATOR = new Creator<PostNumberUserPostPending>()
		{
			@Override
			public PostNumberUserPostPending[] newArray(int size)
			{
				return new PostNumberUserPostPending[size];
			}
			
			@Override
			public PostNumberUserPostPending createFromParcel(Parcel source)
			{
				String postNumber = source.readString();
				return new PostNumberUserPostPending(postNumber);
			}
		};
	}
	
	public static class NewThreadUserPostPending implements UserPostPending
	{
		@Override
		public boolean isUserPost(Post post)
		{
			return post.getParentPostNumberOrNull() == null;
		}
		
		@Override
		public boolean equals(Object o)
		{
			if (o == this) return true;
			if (o instanceof NewThreadUserPostPending) return true;
			return false;
		}
		
		@Override
		public int hashCode()
		{
			return 1;
		}
		
		@Override
		public int describeContents()
		{
			return 0;
		}
		
		@Override
		public void writeToParcel(Parcel dest, int flags)
		{
			
		}
		
		public static final Creator<NewThreadUserPostPending> CREATOR = new Creator<NewThreadUserPostPending>()
		{
			@Override
			public NewThreadUserPostPending[] newArray(int size)
			{
				return new NewThreadUserPostPending[size];
			}
			
			@Override
			public NewThreadUserPostPending createFromParcel(Parcel source)
			{
				return new NewThreadUserPostPending();
			}
		};
	}
	
	public static class CommentUserPostPending implements UserPostPending
	{
		private static final SimilarTextEstimator ESTIMATOR = new SimilarTextEstimator(Integer.MAX_VALUE, true);
		
		private final SimilarTextEstimator.WordsData mWordsData;
		
		public CommentUserPostPending(String comment)
		{
			this(ESTIMATOR.getWords(comment));
		}
		
		private CommentUserPostPending(SimilarTextEstimator.WordsData wordsData)
		{
			mWordsData = wordsData;
		}
		
		@Override
		public boolean isUserPost(Post post)
		{
			String comment = HtmlParser.clear(post.getComment());
			SimilarTextEstimator.WordsData wordsData = ESTIMATOR.getWords(comment);
			return ESTIMATOR.checkSimiliar(mWordsData, wordsData) || mWordsData == null && wordsData == null;
		}
		
		@Override
		public boolean equals(Object o)
		{
			if (o == this) return true;
			if (o instanceof CommentUserPostPending)
			{
				CommentUserPostPending co = (CommentUserPostPending) o;
				return co.mWordsData == mWordsData || co.mWordsData != null && mWordsData != null
						&& co.mWordsData.count == mWordsData.count && co.mWordsData.words.equals(mWordsData.words);
			}
			return false;
		}
		
		@Override
		public int hashCode()
		{
			int prime = 31;
			int result = 1;
			if (mWordsData != null)
			{
				result = prime * result + mWordsData.words.hashCode();
				result = prime * result + mWordsData.count;
			}
			return result;
		}
		
		@Override
		public int describeContents()
		{
			return 0;
		}
		
		@Override
		public void writeToParcel(Parcel dest, int flags)
		{
			dest.writeSerializable(mWordsData != null ? mWordsData.words : null);
			dest.writeInt(mWordsData != null ? mWordsData.count : 0);
		}
		
		public static final Creator<CommentUserPostPending> CREATOR = new Creator<CommentUserPostPending>()
		{
			@Override
			public CommentUserPostPending[] newArray(int size)
			{
				return new CommentUserPostPending[size];
			}
			
			@Override
			public CommentUserPostPending createFromParcel(Parcel source)
			{
				@SuppressWarnings("unchecked")
				HashSet<String> words = (HashSet<String>) source.readSerializable();
				int count = source.readInt();
				return new CommentUserPostPending(words != null && count > 0
						? new SimilarTextEstimator.WordsData(words, count) : null);
			}
		};
	}
}