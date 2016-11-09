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

import android.os.Parcel;
import android.os.Parcelable;

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.RedirectException;
import chan.content.ThreadRedirectException;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpValidator;
import chan.util.CommonUtils;
import chan.util.StringUtils;

import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.net.YouTubeTitlesReader;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.text.SimilarTextEstimator;
import com.mishiranu.dashchan.util.Log;

public class ReadPostsTask extends HttpHolderTask<Void, Void, Boolean> {
	private final Callback callback;
	private final String chanName;
	private final String boardName;
	private final String threadNumber;
	private final Posts cachedPosts;
	private final HttpValidator validator;
	private final boolean forceLoadFullThread;
	private final String lastPostNumber;
	private final ArrayList<UserPostPending> userPostPendings;

	private Result result;
	private boolean fullThread = false;

	private ArrayList<UserPostPending> removedUserPostPendings;
	private RedirectException.Target target;
	private ErrorItem errorItem;

	public interface Callback {
		public void onRequestPreloadPosts(ArrayList<Patch> patches, int oldCount);
		public void onReadPostsSuccess(Result result, boolean fullThread,
				ArrayList<UserPostPending> removedUserPostPendings);
		public void onReadPostsEmpty();
		public void onReadPostsRedirect(RedirectException.Target target);
		public void onReadPostsFail(ErrorItem errorItem);
	}

	public ReadPostsTask(Callback callback, String chanName, String boardName, String threadNumber, Posts cachedPosts,
			boolean useValidator, boolean forceLoadFullThread, String lastPostNumber,
			ArrayList<UserPostPending> userPostPendings) {
		this.callback = callback;
		this.chanName = chanName;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.cachedPosts = cachedPosts;
		this.validator = useValidator && cachedPosts != null ? cachedPosts.getValidator() : null;
		this.forceLoadFullThread = forceLoadFullThread;
		this.lastPostNumber = lastPostNumber;
		this.userPostPendings = userPostPendings.size() > 0 ? userPostPendings : null;
	}

	@Override
	protected Boolean doInBackground(HttpHolder holder, Void... params) {
		String lastPostNumber = forceLoadFullThread ? null : this.lastPostNumber;
		boolean partialThreadLoading = Preferences.isPartialThreadLoading(chanName) && !forceLoadFullThread;
		ChanPerformer performer = ChanPerformer.get(chanName);
		try {
			ChanPerformer.ReadPostsResult result;
			try {
				result = performer.safe().onReadPosts(new ChanPerformer.ReadPostsData(boardName, threadNumber,
						lastPostNumber, partialThreadLoading, cachedPosts, holder, validator));
			} catch (ThreadRedirectException e) {
				RedirectException.Target target = e.obtainTarget(chanName, boardName);
				if (target == null) {
					throw HttpException.createNotFoundException();
				}
				this.target = target;
				return true;
			} catch (RedirectException e) {
				RedirectException.Target target = e.obtainTarget(chanName);
				if (target == null) {
					throw HttpException.createNotFoundException();
				}
				if (!chanName.equals(target.chanName) || target.threadNumber == null) {
					Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES,
							"Only local thread redirects available there");
					errorItem = new ErrorItem(ErrorItem.TYPE_INVALID_DATA_FORMAT);
					return false;
				} else if (StringUtils.equals(boardName, target.boardName) &&
						threadNumber.equals(target.threadNumber)) {
					throw HttpException.createNotFoundException();
				} else {
					this.target = target;
					return true;
				}
			}
			Posts readPosts = result != null ? result.posts : null;
			HttpValidator validator = result != null ? result.validator : null;
			if (result != null && result.fullThread) {
				partialThreadLoading = false;
			}

			if (readPosts != null && readPosts.length() > 0) {
				// Remove repeatings and sort
				Post[] posts = readPosts.getPosts();
				LinkedHashMap<String, Post> postsMap = new LinkedHashMap<>();
				for (Post post : readPosts.getPosts()) {
					String postNumber = post.getPostNumber();
					postsMap.put(postNumber, post);
				}
				if (postsMap.size() != posts.length) {
					posts = CommonUtils.toArray(postsMap.values(), Post.class);
				}
				Arrays.sort(posts);
				readPosts.setPosts(posts);

				// Validate model data format
				Post firstPost = cachedPosts != null ? cachedPosts.getPosts()[0] : posts[0];
				String firstPostNumber = firstPost.getPostNumber();
				if (firstPostNumber == null) {
					Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES, "The getPostNumber() method of",
							"original post returned null.");
					errorItem = new ErrorItem(ErrorItem.TYPE_INVALID_DATA_FORMAT);
					return false;
				}
				if (firstPost.getParentPostNumberOrNull() != null) {
					Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES, "The getParentPostNumber() method of",
							"original post must return null, \"0\" or post number.");
					errorItem = new ErrorItem(ErrorItem.TYPE_INVALID_DATA_FORMAT);
					return false;
				}
				boolean resultWithOriginalPost = posts[0].getPostNumber().equals(firstPostNumber);
				for (int i = 0; i < posts.length; i++) {
					Post post = posts[i];
					String postNumber = post.getPostNumber();
					if (postNumber == null) {
						Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES, "The getPostNumber() method of", i,
								"post returned null.");
						errorItem = new ErrorItem(ErrorItem.TYPE_INVALID_DATA_FORMAT);
						return false;
					}
					if (!threadNumber.equals(post.getThreadNumberOrOriginalPostNumber())) {
						Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES, "The number of requested thread and",
								"number of thread in post", postNumber, "are not equal.");
						errorItem = new ErrorItem(ErrorItem.TYPE_INVALID_DATA_FORMAT);
						return false;
					}
					if (!resultWithOriginalPost || i > 0) {
						if (!firstPostNumber.equals(post.getParentPostNumberOrNull())) {
							Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES, "The getParentPostNumber()",
									"method of post", postNumber, "is not equal to original post's number.");
							errorItem = new ErrorItem(ErrorItem.TYPE_INVALID_DATA_FORMAT);
							return false;
						}
					}
				}
			}

			Result handleResult = null;
			boolean fullThread = false;
			if (cachedPosts != null) {
				boolean partial = partialThreadLoading && lastPostNumber != null;
				handleResult = mergePosts(cachedPosts, readPosts, partial);
			} else if (readPosts != null && readPosts.length() > 0) {
				Post[] readPostsArray = readPosts.getPosts();
				ArrayList<Patch> patches = new ArrayList<>();
				for (int i = 0; i < readPostsArray.length; i++) {
					patches.add(new Patch(readPostsArray[i], null, i, false, true));
				}
				handleResult = new Result(readPostsArray.length, 0, false, readPosts, patches, false);
				fullThread = true;
			}
			if (handleResult != null) {
				if (!handleResult.patches.isEmpty()) {
					if (userPostPendings != null) {
						ArrayList<UserPostPending> workUserPostPendings = new ArrayList<>(userPostPendings);
						OUTER: for (int i = handleResult.patches.size() - 1; i >= 0; i--) {
							Post post = handleResult.patches.get(i).newPost;
							for (int j = workUserPostPendings.size() - 1; j >= 0; j--) {
								UserPostPending userPostPending = workUserPostPendings.get(j);
								if (userPostPending.isUserPost(post)) {
									post.setUserPost(true);
									workUserPostPendings.remove(j);
									if (removedUserPostPendings == null) {
										removedUserPostPendings = new ArrayList<>();
									}
									removedUserPostPendings.add(userPostPending);
									if (workUserPostPendings.isEmpty()) {
										break OUTER;
									}
								}
							}
						}
					}
					ArrayList<Post> handlePosts = new ArrayList<>();
					for (Patch patch : handleResult.patches) {
						handlePosts.add(patch.newPost);
					}
					YouTubeTitlesReader.getInstance().readAndApplyIfNecessary(handlePosts, holder);
				}
				for (Patch patch : handleResult.patches) {
					patch.postItem = new PostItem(patch.newPost, chanName, boardName);
				}
				callback.onRequestPreloadPosts(handleResult.patches, cachedPosts != null ? cachedPosts.length() : 0);
				if (validator == null) {
					validator = holder.getValidator();
				}
				if (validator == null && cachedPosts != null) {
					validator = cachedPosts.getValidator();
				}
				handleResult.posts.setValidator(validator);
				this.result = handleResult;
				this.fullThread = fullThread;
			}
			return true;
		} catch (HttpException e) {
			int responseCode = e.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
				return true;
			}
			if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
				if (ChanConfiguration.get(chanName).getOption(ChanConfiguration.OPTION_READ_SINGLE_POST)) {
					try {
						ChanPerformer.ReadSinglePostResult result = performer.safe().onReadSinglePost
								(new ChanPerformer.ReadSinglePostData(boardName, threadNumber, holder));
						Post post = result != null ? result.post : null;
						String threadNumber = post.getThreadNumberOrOriginalPostNumber();
						if (threadNumber != null && !threadNumber.equals(this.threadNumber)) {
							// noinspection ThrowableResultOfMethodCallIgnored
							target = RedirectException.toThread(boardName, threadNumber, post.getPostNumber())
									.obtainTarget(chanName);
							return true;
						}
					} catch (ExtensionException | HttpException | InvalidResponseException e2) {
						// Ignore exception
					}
				}
				errorItem = new ErrorItem(ErrorItem.TYPE_THREAD_NOT_EXISTS);
			} else {
				errorItem = e.getErrorItemAndHandle();
			}
			return false;
		} catch (ExtensionException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return false;
		} finally {
			ChanConfiguration.get(chanName).commit();
		}
	}

	@Override
	public void onPostExecute(Boolean success) {
		if (success) {
			if (result != null) {
				callback.onReadPostsSuccess(result, fullThread, removedUserPostPendings);
			} else if (target != null) {
				callback.onReadPostsRedirect(target);
			} else {
				callback.onReadPostsEmpty();
			}
		} else {
			callback.onReadPostsFail(errorItem);
		}
	}

	public static class Patch {
		public final Post newPost;
		public final Post oldPost;
		public PostItem postItem;

		public final int index;
		public final boolean replaceAtIndex;
		public final boolean newPostAddedToEnd;

		public Patch(Post newPost, Post oldPost, int index, boolean replaceAtIndex, boolean newPostAddedToEnd) {
			this.newPost = newPost;
			this.oldPost = oldPost;
			this.index = index;
			this.replaceAtIndex = replaceAtIndex;
			this.newPostAddedToEnd = newPostAddedToEnd;
		}

		public Patch(PostItem postItem, int index) {
			newPost = postItem.getPost();
			oldPost = null;
			this.postItem = postItem;
			this.index = index;
			replaceAtIndex = false;
			newPostAddedToEnd = true;
		}
	}

	public static class Result {
		public final int newCount, deletedCount;
		public final boolean hasEdited;

		public final Posts posts;
		public final ArrayList<Patch> patches;
		public final boolean fieldsUpdated;

		public Result(int newCount, int deletedCount, boolean hasEdited, Posts posts,
				ArrayList<Patch> patches, boolean fieldsUpdated) {
			this.newCount = newCount;
			this.deletedCount = deletedCount;
			this.hasEdited = hasEdited;
			this.posts = posts;
			this.patches = patches;
			this.fieldsUpdated = fieldsUpdated;
		}
	}

	private static Result mergePosts(Posts cachedPosts, Posts loadedPosts, boolean partial) {
		int newCount = 0;
		int deletedCount = 0;
		boolean hasEdited = false;
		ArrayList<Patch> patches = new ArrayList<>();
		if (loadedPosts != null && loadedPosts.length() > 0) {
			Post[] cachedPostsArray = cachedPosts.getPosts();
			Post[] loadedPostsArray = loadedPosts.getPosts();
			int resultSize = 0;
			int i = 0, j = 0;
			int ic = cachedPostsArray.length, jc = loadedPostsArray.length;
			while (i < ic || j < jc) {
				Post oldPost = i < ic ? cachedPostsArray[i] : null;
				Post newPost = j < jc ? loadedPostsArray[j] : null;
				int result;
				if (oldPost == null) {
					result = 1;
				} else if (newPost == null) {
					result = -1;
				} else {
					result = oldPost.compareTo(newPost);
				}
				if (result < 0) {
					// Number of new post is greater
					// So add old post to array and mark it as deleted, if downloading was not partial
					if (!partial && !oldPost.isDeleted()) {
						Post postBeforeCopy = oldPost;
						// Copying will reset client internal flags in model
						oldPost = oldPost.copy().setDeleted(true);
						deletedCount++;
						patches.add(new Patch(oldPost, postBeforeCopy, resultSize, true, false));
						hasEdited = true;
					}
					resultSize++;
					i++;
				} else if (result > 0) {
					// Number of old post is greater
					// It's a new post. May be it will be inserted in center of list.
					boolean addToEnd = oldPost == null;
					patches.add(new Patch(newPost, null, resultSize, false, addToEnd));
					resultSize++;
					if (addToEnd) {
						newCount++;
					} else {
						hasEdited = true;
					}
					j++;
				} else {
					// Post numbers are equal
					if (!oldPost.contentEquals(newPost) || oldPost.isDeleted()) {
						hasEdited = true;
						patches.add(new Patch(newPost, oldPost, resultSize, true, false));
						resultSize++;
					} else {
						// Keep old model if no changes, because PostItem bound to old PostModel
						resultSize++;
					}
					i++;
					j++;
				}
			}
		}
		Post[] postsArray = cachedPosts.getPosts();
		if (!patches.isEmpty()) {
			ArrayList<Post> newPosts = new ArrayList<>();
			if (postsArray != null) {
				Collections.addAll(newPosts, postsArray);
			}
			for (int i = 0; i < patches.size(); i++) {
				Patch patch = patches.get(i);
				if (patch.replaceAtIndex) {
					newPosts.set(patch.index, patch.newPost);
				} else {
					newPosts.add(patch.index, patch.newPost);
				}
			}
			postsArray = CommonUtils.toArray(newPosts, Post.class);
		}
		Posts resultPosts = new Posts(postsArray);
		resultPosts.setArchivedThreadUriString(cachedPosts.getArchivedThreadUriString());
		resultPosts.setUniquePosters(cachedPosts.getUniquePosters());
		// The rest model fields must be updated in main thread
		boolean fieldsUpdated = false;
		if (loadedPosts != null) {
			String archivedThreadUriString = loadedPosts.getArchivedThreadUriString();
			if (archivedThreadUriString != null && !archivedThreadUriString
					.equals(resultPosts.getArchivedThreadUriString())) {
				resultPosts.setArchivedThreadUriString(archivedThreadUriString);
				fieldsUpdated = true;
			}
			int uniquePosters = loadedPosts.getUniquePosters();
			if (uniquePosters > 0 && uniquePosters != resultPosts.getUniquePosters()) {
				resultPosts.setUniquePosters(uniquePosters);
				fieldsUpdated = true;
			}
		}
		if (patches.isEmpty() && !fieldsUpdated) {
			return null;
		}
		return new Result(newCount, deletedCount, hasEdited, resultPosts, patches, fieldsUpdated);
	}

	public interface UserPostPending extends Parcelable {
		public boolean isUserPost(Post post);
	}

	public static class PostNumberUserPostPending implements UserPostPending {
		private final String postNumber;

		public PostNumberUserPostPending(String postNumber) {
			this.postNumber = postNumber;
		}

		@Override
		public boolean isUserPost(Post post) {
			return postNumber.equals(post.getPostNumber());
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof PostNumberUserPostPending) {
				return ((PostNumberUserPostPending) o).postNumber.equals(postNumber);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return postNumber.hashCode();
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(postNumber);
		}

		public static final Creator<PostNumberUserPostPending> CREATOR = new Creator<PostNumberUserPostPending>() {
			@Override
			public PostNumberUserPostPending[] newArray(int size) {
				return new PostNumberUserPostPending[size];
			}

			@Override
			public PostNumberUserPostPending createFromParcel(Parcel source) {
				String postNumber = source.readString();
				return new PostNumberUserPostPending(postNumber);
			}
		};
	}

	public static class NewThreadUserPostPending implements UserPostPending {
		@Override
		public boolean isUserPost(Post post) {
			return post.getParentPostNumberOrNull() == null;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof NewThreadUserPostPending) {
				return true;
			}
			return false;
		}

		@Override
		public int hashCode() {
			return 1;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {}

		public static final Creator<NewThreadUserPostPending> CREATOR = new Creator<NewThreadUserPostPending>() {
			@Override
			public NewThreadUserPostPending[] newArray(int size) {
				return new NewThreadUserPostPending[size];
			}

			@Override
			public NewThreadUserPostPending createFromParcel(Parcel source) {
				return new NewThreadUserPostPending();
			}
		};
	}

	public static class CommentUserPostPending implements UserPostPending {
		private static final SimilarTextEstimator ESTIMATOR = new SimilarTextEstimator(Integer.MAX_VALUE, true);

		private final SimilarTextEstimator.WordsData wordsData;

		public CommentUserPostPending(String comment) {
			this(ESTIMATOR.getWords(comment));
		}

		private CommentUserPostPending(SimilarTextEstimator.WordsData wordsData) {
			this.wordsData = wordsData;
		}

		@Override
		public boolean isUserPost(Post post) {
			String comment = HtmlParser.clear(post.getComment());
			SimilarTextEstimator.WordsData wordsData = ESTIMATOR.getWords(comment);
			return ESTIMATOR.checkSimiliar(this.wordsData, wordsData) || this.wordsData == null && wordsData == null;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o instanceof CommentUserPostPending) {
				CommentUserPostPending co = (CommentUserPostPending) o;
				return co.wordsData == wordsData || co.wordsData != null && wordsData != null
						&& co.wordsData.count == wordsData.count && co.wordsData.words.equals(wordsData.words);
			}
			return false;
		}

		@Override
		public int hashCode() {
			int prime = 31;
			int result = 1;
			if (wordsData != null) {
				result = prime * result + wordsData.words.hashCode();
				result = prime * result + wordsData.count;
			}
			return result;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeSerializable(wordsData != null ? wordsData.words : null);
			dest.writeInt(wordsData != null ? wordsData.count : 0);
		}

		public static final Creator<CommentUserPostPending> CREATOR = new Creator<CommentUserPostPending>() {
			@Override
			public CommentUserPostPending[] newArray(int size) {
				return new CommentUserPostPending[size];
			}

			@Override
			public CommentUserPostPending createFromParcel(Parcel source) {
				@SuppressWarnings("unchecked")
				HashSet<String> words = (HashSet<String>) source.readSerializable();
				int count = source.readInt();
				return new CommentUserPostPending(words != null && count > 0
						? new SimilarTextEstimator.WordsData(words, count) : null);
			}
		};
	}
}