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

package chan.content.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;

import android.net.Uri;
import android.util.Pair;

import chan.annotation.Public;
import chan.http.HttpValidator;
import chan.util.CommonUtils;

@Public
public final class Posts implements Serializable
{
	private static final long serialVersionUID = 1L;
	
	private Post[] mPosts;
	private HttpValidator mHttpValidator;
	
	private String mArchivedThreadUriString;
	private int mUniquePosters = 0;
	
	private int mPostsCount = -1;
	private int mFilesCount = -1;
	private int mPostsWithFilesCount = -1;
	
	private String[][] mLocalAutohide;
	private boolean mAutoRefreshEnabled;
	private int mAutoRefreshInterval;
	
	@Public
	public Post[] getPosts()
	{
		return mPosts;
	}
	
	@Public
	public Posts setPosts(Post... posts)
	{
		mPosts = CommonUtils.removeNullItems(posts, Post.class);
		return this;
	}
	
	@Public
	public Posts setPosts(Collection<? extends Post> posts)
	{
		return setPosts(CommonUtils.toArray(posts, Post.class));
	}
	
	public String getThreadNumber()
	{
		return mPosts[0].getThreadNumberOrOriginalPostNumber();
	}
	
	@Public
	public Uri getArchivedThreadUri()
	{
		return mArchivedThreadUriString != null ? Uri.parse(mArchivedThreadUriString) : null;
	}
	
	@Public
	public Posts setArchivedThreadUri(Uri uri)
	{
		mArchivedThreadUriString = uri != null ? uri.toString() : null;
		return this;
	}
	
	@Public
	public int getUniquePosters()
	{
		return mUniquePosters;
	}
	
	@Public
	public Posts setUniquePosters(int uniquePosters)
	{
		if (uniquePosters > 0) mUniquePosters = uniquePosters;
		return this;
	}
	
	@Public
	public int getPostsCount()
	{
		return mPostsCount;
	}
	
	@Public
	public Posts addPostsCount(int postsCount)
	{
		if (postsCount > 0)
		{
			if (mPostsCount == -1) postsCount++;
			mPostsCount += postsCount;
		}
		return this;
	}
	
	@Public
	public int getFilesCount()
	{
		return mFilesCount;
	}
	
	@Public
	public Posts addFilesCount(int filesCount)
	{
		if (filesCount > 0)
		{
			if (mFilesCount == -1) filesCount++;
			mFilesCount += filesCount;
		}
		return this;
	}
	
	@Public
	public int getPostsWithFilesCount()
	{
		return mPostsWithFilesCount;
	}
	
	@Public
	public Posts addPostsWithFilesCount(int postsWithFilesCount)
	{
		if (postsWithFilesCount > 0)
		{
			if (mPostsWithFilesCount == -1) postsWithFilesCount++;
			mPostsWithFilesCount += postsWithFilesCount;
		}
		return this;
	}
	
	public HttpValidator getValidator()
	{
		return mHttpValidator;
	}
	
	public Posts setValidator(HttpValidator validator)
	{
		mHttpValidator = validator;
		return this;
	}
	
	public String[][] getLocalAutohide()
	{
		return mLocalAutohide;
	}
	
	public Posts setLocalAutohide(String[][] localAutohide)
	{
		mLocalAutohide = localAutohide;
		return this;
	}
	
	public Pair<Boolean, Integer> getAutoRefreshDate()
	{
		return new Pair<>(mAutoRefreshEnabled, mAutoRefreshInterval);
	}
	
	public boolean setAutoRefreshData(boolean enabled, int interval)
	{
		if (mAutoRefreshEnabled != enabled || mAutoRefreshInterval != interval)
		{
			mAutoRefreshEnabled = enabled;
			mAutoRefreshInterval = interval;
			return true;
		}
		return false;
	}
	
	public int length()
	{
		return mPosts != null ? mPosts.length : 0;
	}
	
	public int append(Posts posts)
	{
		if (posts != null && posts.length() > 0)
		{
			if (length() > 0)
			{
				int newCount = posts.length();
				if (newCount > 0)
				{
					Post[] oldPosts = mPosts;
					Post[] newPosts = new Post[oldPosts.length + newCount];
					System.arraycopy(oldPosts, 0, newPosts, 0, oldPosts.length);
					System.arraycopy(posts.mPosts, 0, newPosts, oldPosts.length, newCount);
					setPosts(newPosts);
				}
				return newCount;
			}
			else
			{
				setPosts(posts.mPosts);
				return length();
			}
		}
		return 0;
	}
	
	public void removeRepeatingsAndSort()
	{
		if (length() > 0)
		{
			LinkedHashMap<String, Post> posts = new LinkedHashMap<>();
			for (Post post : mPosts)
			{
				String postNumber = post.getPostNumber();
				posts.put(postNumber, post);
			}
			if (posts.size() != mPosts.length) mPosts = CommonUtils.toArray(posts.values(), Post.class);
			Arrays.sort(mPosts);
		}
	}
	
	public void clearDeletedPosts()
	{
		synchronized (this)
		{
			if (length() > 0)
			{
				ArrayList<Post> posts = new ArrayList<>(mPosts.length);
				for (Post post : mPosts)
				{
					if (!post.isDeleted()) posts.add(post);
				}
				mPosts = CommonUtils.toArray(posts, Post.class);
			}
		}
	}
	
	public static class MergeAction
	{
		public final int index;
		public final boolean insert;
		public final boolean newPost;
		
		public MergeAction(int index, boolean insert, boolean newPost)
		{
			this.index = index;
			this.insert = insert;
			this.newPost = newPost;
		}
	}
	
	public static class MergeResult
	{
		public final int newCount, deletedCount;
		public final boolean hasEdited;
		public final Post[] changed;
		public final MergeAction[] actions;
		public final boolean fieldsUpdated;
		
		public MergeResult(int newCount, int deletedCount, boolean hasEdited, Post[] changed,
				MergeAction[] actions, boolean fieldsUpdated)
		{
			this.newCount = newCount;
			this.deletedCount = deletedCount;
			this.hasEdited = hasEdited;
			this.changed = changed;
			this.actions = actions;
			this.fieldsUpdated = fieldsUpdated;
		}
	}
	
	public MergeResult pendingMerge(Posts posts, boolean partial)
	{
		synchronized (this)
		{
			int newCount = 0;
			int deletedCount = 0;
			boolean hasEdited = false;
			boolean fieldsUpdated = false;
			// This list will contain only new or changed Posts
			ArrayList<Post> changedList = new ArrayList<>();
			ArrayList<MergeAction> actionsList = new ArrayList<>();
			if (length() > 0)
			{
				if (posts != null && posts.length() > 0)
				{
					int resultSize = 0;
					int i = 0, j = 0;
					int ic = mPosts.length, jc = posts.mPosts.length;
					while (i < ic || j < jc)
					{
						Post oldPost = i < ic ? mPosts[i] : null;
						Post newPost = j < jc ? posts.mPosts[j] : null;
						int result;
						if (oldPost == null) result = 1;
						else if (newPost == null) result = -1;
						else result = oldPost.compareTo(newPost);
						if (result < 0) // Number of new post is greater...
						{
							// So add old post to array and mark it as deleted, if downloading was not partial
							if (!partial && !oldPost.isDeleted())
							{
								oldPost.setDeleted(true);
								deletedCount++;
								changedList.add(oldPost);
								actionsList.add(new MergeAction(resultSize, false, false));
								hasEdited = true;
							}
							resultSize++;
							i++;
						}
						else if (result > 0) // Number of old post is greater
						{
							// It's a new post. May be it will be inserted in center of list.
							boolean addToEnd = oldPost == null;
							changedList.add(newPost);
							actionsList.add(new MergeAction(resultSize, true, addToEnd));
							resultSize++;
							if (addToEnd) newCount++; else hasEdited = true;
							j++;
						}
						else // Post numbers are equal
						{
							if (!oldPost.contentEquals(newPost) || oldPost.isDeleted())
							{
								// Must copy, because "hidden" flag has 3 states - shown, hidden, undefined
								if (oldPost.isHidden()) newPost.setHidden(true);
								else if (oldPost.isShown()) newPost.setHidden(false);
								if (oldPost.isUserPost()) newPost.setUserPost(true);
								hasEdited = true;
								changedList.add(newPost);
								actionsList.add(new MergeAction(resultSize, false, false));
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
			}
			else
			{
				Post[] postsArray = posts.mPosts;
				if (postsArray != null)
				{
					for (int i = 0; i < postsArray.length; i++)
					{
						changedList.add(postsArray[0]);
						actionsList.add(new MergeAction(i, true, true));
					}
					newCount = postsArray.length;
				}
			}
			Post[] changed = CommonUtils.toArray(changedList, Post.class);
			MergeAction[] actions = CommonUtils.toArray(actionsList, MergeAction.class);
			return new MergeResult(newCount, deletedCount, hasEdited, changed, actions, fieldsUpdated);
		}
	}
	
	public boolean merge(Posts posts, Post[] handlePosts, MergeAction[] actions)
	{
		synchronized (this)
		{
			boolean fieldsUpdated = false;
			// Copy attributes to new model
			if (posts != null)
			{
				String archivedThreadUriString = posts.mArchivedThreadUriString;
				if (archivedThreadUriString != null && !archivedThreadUriString.equals(mArchivedThreadUriString))
				{
					mArchivedThreadUriString = archivedThreadUriString;
					fieldsUpdated = true;
				}
				int uniquePosters = posts.mUniquePosters;
				if (uniquePosters > 0 && uniquePosters != mUniquePosters)
				{
					mUniquePosters = uniquePosters;
					fieldsUpdated = true;
				}
			}
			if (handlePosts != null && actions != null)
			{
				ArrayList<Post> newPosts = new ArrayList<>();
				if (mPosts != null) Collections.addAll(newPosts, mPosts);
				for (int i = 0; i < handlePosts.length; i++)
				{
					if (actions[i].insert) newPosts.add(actions[i].index, handlePosts[i]);
					else newPosts.set(actions[i].index, handlePosts[i]);
				}
				mPosts = CommonUtils.toArray(newPosts, Post.class);
			}
			return handlePosts != null || fieldsUpdated;
		}
	}
	
	@Public
	public Posts()
	{
		
	}
	
	@Public
	public Posts(Post... posts)
	{
		setPosts(posts);
	}
	
	@Public
	public Posts(Collection<? extends Post> posts)
	{
		setPosts(posts);
	}
}