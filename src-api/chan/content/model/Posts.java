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
import java.util.Collection;

import android.net.Uri;
import android.util.Pair;

import chan.annotation.Public;
import chan.http.HttpValidator;
import chan.util.CommonUtils;

@Public
public final class Posts implements Serializable {
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
	public Post[] getPosts() {
		return mPosts;
	}

	@Public
	public Posts setPosts(Post... posts) {
		mPosts = CommonUtils.removeNullItems(posts, Post.class);
		return this;
	}

	@Public
	public Posts setPosts(Collection<? extends Post> posts) {
		return setPosts(CommonUtils.toArray(posts, Post.class));
	}

	public String getThreadNumber() {
		return mPosts[0].getThreadNumberOrOriginalPostNumber();
	}

	@Public
	public Uri getArchivedThreadUri() {
		return mArchivedThreadUriString != null ? Uri.parse(mArchivedThreadUriString) : null;
	}

	@Public
	public Posts setArchivedThreadUri(Uri uri) {
		mArchivedThreadUriString = uri != null ? uri.toString() : null;
		return this;
	}

	public String getArchivedThreadUriString() {
		return mArchivedThreadUriString;
	}

	public Posts setArchivedThreadUriString(String uriString) {
		mArchivedThreadUriString = uriString;
		return this;
	}

	@Public
	public int getUniquePosters() {
		return mUniquePosters;
	}

	@Public
	public Posts setUniquePosters(int uniquePosters) {
		if (uniquePosters > 0) {
			mUniquePosters = uniquePosters;
		}
		return this;
	}

	@Public
	public int getPostsCount() {
		return mPostsCount;
	}

	@Public
	public Posts addPostsCount(int postsCount) {
		if (postsCount > 0) {
			if (mPostsCount == -1) {
				postsCount++;
			}
			mPostsCount += postsCount;
		}
		return this;
	}

	@Public
	public int getFilesCount() {
		return mFilesCount;
	}

	@Public
	public Posts addFilesCount(int filesCount) {
		if (filesCount > 0) {
			if (mFilesCount == -1) {
				filesCount++;
			}
			mFilesCount += filesCount;
		}
		return this;
	}

	@Public
	public int getPostsWithFilesCount() {
		return mPostsWithFilesCount;
	}

	@Public
	public Posts addPostsWithFilesCount(int postsWithFilesCount) {
		if (postsWithFilesCount > 0) {
			if (mPostsWithFilesCount == -1) {
				postsWithFilesCount++;
			}
			mPostsWithFilesCount += postsWithFilesCount;
		}
		return this;
	}

	public HttpValidator getValidator() {
		return mHttpValidator;
	}

	public Posts setValidator(HttpValidator validator) {
		mHttpValidator = validator;
		return this;
	}

	public String[][] getLocalAutohide() {
		return mLocalAutohide;
	}

	public Posts setLocalAutohide(String[][] localAutohide) {
		mLocalAutohide = localAutohide;
		return this;
	}

	public Pair<Boolean, Integer> getAutoRefreshData() {
		return new Pair<>(mAutoRefreshEnabled, mAutoRefreshInterval);
	}

	public boolean setAutoRefreshData(boolean enabled, int interval) {
		if (mAutoRefreshEnabled != enabled || mAutoRefreshInterval != interval) {
			mAutoRefreshEnabled = enabled;
			mAutoRefreshInterval = interval;
			return true;
		}
		return false;
	}

	public int length() {
		return mPosts != null ? mPosts.length : 0;
	}

	public int append(Posts posts) {
		if (posts != null && posts.length() > 0) {
			if (length() > 0) {
				int newCount = posts.length();
				if (newCount > 0) {
					Post[] oldPosts = mPosts;
					Post[] newPosts = new Post[oldPosts.length + newCount];
					System.arraycopy(oldPosts, 0, newPosts, 0, oldPosts.length);
					System.arraycopy(posts.mPosts, 0, newPosts, oldPosts.length, newCount);
					setPosts(newPosts);
				}
				return newCount;
			} else {
				setPosts(posts.mPosts);
				return length();
			}
		}
		return 0;
	}

	public void clearDeletedPosts() {
		if (length() > 0) {
			ArrayList<Post> posts = new ArrayList<>(mPosts.length);
			for (Post post : mPosts) {
				if (!post.isDeleted()) {
					posts.add(post);
				}
			}
			mPosts = CommonUtils.toArray(posts, Post.class);
		}
	}

	@Public
	public Posts() {}

	@Public
	public Posts(Post... posts) {
		setPosts(posts);
	}

	@Public
	public Posts(Collection<? extends Post> posts) {
		setPosts(posts);
	}
}