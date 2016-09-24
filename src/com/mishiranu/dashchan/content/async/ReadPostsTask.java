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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import chan.http.HttpValidator;
import chan.util.CommonUtils;

import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.net.YouTubeTitlesReader;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.text.SimilarTextEstimator;
import com.mishiranu.dashchan.util.Log;

public class ReadPostsTask extends HttpHolderTask<Void, Void, Boolean>
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

	private Result mResult;
	private boolean mFullThread = false;

	private String mRedirectBoardName;
	private String mRedirectThreadNumber;
	private String mRedirectPostNumber;

	private ArrayList<UserPostPending> mRemovedUserPostPendings;
	private ErrorItem mErrorItem;

	public interface Callback
	{
		public void onRequestPreloadPosts(PostItem[] postItems);
		public void onReadPostsSuccess(Result result, boolean fullThread,
				ArrayList<UserPostPending> removedUserPostPendings);
		public void onReadPostsEmpty();
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
			ChanPerformer.ReadPostsResult result = performer.safe()
					.onReadPosts(new ChanPerformer.ReadPostsData(mBoardName, mThreadNumber, lastPostNumber,
					partialThreadLoading, mCachedPosts, getHolder(), mValidator));
			Posts readPosts = result != null ? result.posts : null;
			HttpValidator validator = result != null ? result.validator : null;
			if (result != null && result.fullThread) partialThreadLoading = false;

			if (readPosts != null && readPosts.length() > 0)
			{
				// Remove repeatings and sort
				Post[] posts = readPosts.getPosts();
				LinkedHashMap<String, Post> postsMap = new LinkedHashMap<>();
				for (Post post : readPosts.getPosts())
				{
					String postNumber = post.getPostNumber();
					postsMap.put(postNumber, post);
				}
				if (postsMap.size() != posts.length) posts = CommonUtils.toArray(postsMap.values(), Post.class);
				Arrays.sort(posts);
				readPosts.setPosts(posts);

				// Validate model data format
				Post firstPost = mCachedPosts != null ? mCachedPosts.getPosts()[0] : posts[0];
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

			Result handleResult = null;
			boolean fullThread = false;
			if (mCachedPosts != null)
			{
				boolean partial = partialThreadLoading && lastPostNumber != null;
				handleResult = mergePosts(mCachedPosts, readPosts, partial);
			}
			else if (readPosts != null && readPosts.length() > 0)
			{
				Post[] readPostsArray = readPosts.getPosts();
				ArrayList<Patch> patches = new ArrayList<>();
				for (int i = 0; i < readPostsArray.length; i++)
				{
					patches.add(new Patch(readPostsArray[i], null, i, false, true));
				}
				handleResult = new Result(readPostsArray.length, 0, false, readPosts, patches, false);
				fullThread = true;
			}
			if (handleResult != null)
			{
				if (!handleResult.patches.isEmpty())
				{
					if (mUserPostPendings != null)
					{
						ArrayList<UserPostPending> workUserPostPendings = new ArrayList<>(mUserPostPendings);
						OUTER: for (int i = handleResult.patches.size() - 1; i >= 0; i--)
						{
							Post post = handleResult.patches.get(i).newPost;
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
					ArrayList<Post> handlePosts = new ArrayList<>();
					for (Patch patch : handleResult.patches) handlePosts.add(patch.newPost);
					YouTubeTitlesReader.getInstance().readAndApplyIfNecessary(handlePosts, getHolder());
				}
				PostItem[] handlePostItems = new PostItem[handleResult.patches.size()];
				for (int i = 0; i < handlePostItems.length; i++)
				{
					Patch patch = handleResult.patches.get(i);
					PostItem postItem = new PostItem(patch.newPost, mChanName, mBoardName);
					patch.postItem = postItem;
					handlePostItems[i] = postItem;
				}
				mCallback.onRequestPreloadPosts(handlePostItems);
				if (validator == null) validator = getHolder().getValidator();
				if (validator == null && mCachedPosts != null) validator = mCachedPosts.getValidator();
				handleResult.posts.setValidator(validator);
				mResult = handleResult;
				mFullThread = fullThread;
			}
			return true;
		}
		catch (HttpException e)
		{
			int responseCode = e.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) return true;
			if (responseCode == HttpURLConnection.HTTP_NOT_FOUND)
			{
				if (ChanConfiguration.get(mChanName).getOption(ChanConfiguration.OPTION_READ_SINGLE_POST))
				{
					try
					{
						ChanPerformer.ReadSinglePostResult result = performer.safe().onReadSinglePost
								(new ChanPerformer.ReadSinglePostData(mBoardName, mThreadNumber, getHolder()));
						Post post = result != null ? result.post : null;
						String threadNumber = post.getThreadNumberOrOriginalPostNumber();
						if (threadNumber != null && !threadNumber.equals(mThreadNumber))
						{
							mRedirectThreadNumber = threadNumber;
							mRedirectPostNumber = post.getPostNumber();
							return true;
						}
					}
					catch (ExtensionException | HttpException | InvalidResponseException e2)
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
			if (mRedirectThreadNumber != null)
			{
				mCallback.onReadPostsRedirect(mRedirectBoardName != null ? mRedirectBoardName : mBoardName,
						mRedirectThreadNumber, mRedirectPostNumber);
			}
			else if (mResult != null) mCallback.onReadPostsSuccess(mResult, mFullThread, mRemovedUserPostPendings);
			else mCallback.onReadPostsEmpty();
		}
		else
		{
			mCallback.onReadPostsFail(mErrorItem);
		}
	}

	public static class Patch
	{
		public final Post newPost;
		public final Post oldPost;
		public PostItem postItem;

		public final int index;
		public final boolean replaceAtIndex;
		public final boolean newPostAddedToEnd;

		public Patch(Post newPost, Post oldPost, int index, boolean replaceAtIndex, boolean newPostAddedToEnd)
		{
			this.newPost = newPost;
			this.oldPost = oldPost;
			this.index = index;
			this.replaceAtIndex = replaceAtIndex;
			this.newPostAddedToEnd = newPostAddedToEnd;
		}

		public Patch(PostItem postItem, int index)
		{
			newPost = postItem.getPost();
			oldPost = null;
			this.postItem = postItem;
			this.index = index;
			replaceAtIndex = false;
			newPostAddedToEnd = true;
		}
	}

	public static class Result
	{
		public final int newCount, deletedCount;
		public final boolean hasEdited;

		public final Posts posts;
		public final ArrayList<Patch> patches;
		public final boolean fieldsUpdated;

		public Result(int newCount, int deletedCount, boolean hasEdited, Posts posts,
				ArrayList<Patch> patches, boolean fieldsUpdated)
		{
			this.newCount = newCount;
			this.deletedCount = deletedCount;
			this.hasEdited = hasEdited;
			this.posts = posts;
			this.patches = patches;
			this.fieldsUpdated = fieldsUpdated;
		}
	}

	private static Result mergePosts(Posts cachedPosts, Posts loadedPosts, boolean partial)
	{
		int newCount = 0;
		int deletedCount = 0;
		boolean hasEdited = false;
		ArrayList<Patch> patches = new ArrayList<>();
		if (loadedPosts != null && loadedPosts.length() > 0)
		{
			Post[] cachedPostsArray = cachedPosts.getPosts();
			Post[] loadedPostsArray = loadedPosts.getPosts();
			int resultSize = 0;
			int i = 0, j = 0;
			int ic = cachedPostsArray.length, jc = loadedPostsArray.length;
			while (i < ic || j < jc)
			{
				Post oldPost = i < ic ? cachedPostsArray[i] : null;
				Post newPost = j < jc ? loadedPostsArray[j] : null;
				int result;
				if (oldPost == null) result = 1;
				else if (newPost == null) result = -1;
				else result = oldPost.compareTo(newPost);
				if (result < 0) // Number of new post is greater
				{
					// So add old post to array and mark it as deleted, if downloading was not partial
					if (!partial && !oldPost.isDeleted())
					{
						Post postBeforeCopy = oldPost;
						// Copying will reset client internal flags in model
						oldPost = oldPost.copy().setDeleted(true);
						deletedCount++;
						patches.add(new Patch(oldPost, postBeforeCopy, resultSize, true, false));
						hasEdited = true;
					}
					resultSize++;
					i++;
				}
				else if (result > 0) // Number of old post is greater
				{
					// It's a new post. May be it will be inserted in center of list.
					boolean addToEnd = oldPost == null;
					patches.add(new Patch(newPost, null, resultSize, false, addToEnd));
					resultSize++;
					if (addToEnd) newCount++; else hasEdited = true;
					j++;
				}
				else // Post numbers are equal
				{
					if (!oldPost.contentEquals(newPost) || oldPost.isDeleted())
					{
						hasEdited = true;
						patches.add(new Patch(newPost, oldPost, resultSize, true, false));
						resultSize++;
					}
					else
					{
						// Keep old model if no changes, because PostItem bound to old PostModel
						resultSize++;
					}
					i++;
					j++;
				}
			}
		}
		Post[] postsArray = cachedPosts.getPosts();
		if (!patches.isEmpty())
		{
			ArrayList<Post> newPosts = new ArrayList<>();
			if (postsArray != null) Collections.addAll(newPosts, postsArray);
			for (int i = 0; i < patches.size(); i++)
			{
				Patch patch = patches.get(i);
				if (patch.replaceAtIndex) newPosts.set(patch.index, patch.newPost);
				else newPosts.add(patch.index, patch.newPost);
			}
			postsArray = CommonUtils.toArray(newPosts, Post.class);
		}
		Posts resultPosts = new Posts(postsArray);
		resultPosts.setArchivedThreadUriString(cachedPosts.getArchivedThreadUriString());
		resultPosts.setUniquePosters(cachedPosts.getUniquePosters());
		// The rest model fields must be updated in main thread
		boolean fieldsUpdated = false;
		if (loadedPosts != null)
		{
			String archivedThreadUriString = loadedPosts.getArchivedThreadUriString();
			if (archivedThreadUriString != null && !archivedThreadUriString
					.equals(resultPosts.getArchivedThreadUriString()))
			{
				resultPosts.setArchivedThreadUriString(archivedThreadUriString);
				fieldsUpdated = true;
			}
			int uniquePosters = loadedPosts.getUniquePosters();
			if (uniquePosters > 0 && uniquePosters != resultPosts.getUniquePosters())
			{
				resultPosts.setUniquePosters(uniquePosters);
				fieldsUpdated = true;
			}
		}
		if (patches.isEmpty() && !fieldsUpdated) return null;
		return new Result(newCount, deletedCount, hasEdited, resultPosts, patches, fieldsUpdated);
	}

	public interface UserPostPending extends Parcelable
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