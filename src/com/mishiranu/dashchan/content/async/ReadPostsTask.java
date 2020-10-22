package com.mishiranu.dashchan.content.async;

import android.net.Uri;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.RedirectException;
import chan.content.ThreadRedirectException;
import chan.content.model.SinglePost;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpValidator;
import chan.util.CommonUtils;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.database.CommonDatabase;
import com.mishiranu.dashchan.content.database.PagesDatabase;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PendingUserPost;
import com.mishiranu.dashchan.content.model.Post;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.util.Log;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

public class ReadPostsTask extends HttpHolderTask<Void, Void, Boolean> {
	private final Callback callback;
	private final Chan chan;
	private final String boardName;
	private final String threadNumber;
	private final boolean loadFullThread;
	private final HashSet<PendingUserPost> pendingUserPosts;

	private boolean newThread;
	private boolean shouldExtract;
	private HashSet<PendingUserPost> removedPendingUserPosts;
	private RedirectException.Target target;
	private ErrorItem errorItem;

	public interface Callback {
		void onReadPostsSuccess(boolean newThread, boolean shouldExtract,
				Set<PendingUserPost> removedPendingUserPosts);
		void onReadPostsRedirect(RedirectException.Target target);
		void onReadPostsFail(ErrorItem errorItem);
	}

	public ReadPostsTask(Callback callback, Chan chan, String boardName, String threadNumber,
			boolean loadFullThread, Collection<PendingUserPost> pendingUserPosts) {
		super(chan);
		this.callback = callback;
		this.chan = chan;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.loadFullThread = loadFullThread;
		this.pendingUserPosts = pendingUserPosts != null ? new HashSet<>(pendingUserPosts) : null;
	}

	public boolean isLoadFullThread() {
		return loadFullThread;
	}

	protected Boolean doInBackground(HttpHolder holder, Void... params) {
		boolean temporary = chan.configuration.getOption(ChanConfiguration.OPTION_LOCAL_MODE);
		PagesDatabase.ThreadKey threadKey = new PagesDatabase.ThreadKey(chan.name, boardName, threadNumber);
		PagesDatabase.Meta meta = PagesDatabase.getInstance().getMeta(threadKey, temporary);
		PostNumber originalPostNumber;
		PostNumber lastExistingPostNumber;
		if (loadFullThread) {
			originalPostNumber = null;
			lastExistingPostNumber = null;
		} else {
			PagesDatabase.ThreadSummary threadSummary = PagesDatabase.getInstance().getThreadSummary(threadKey);
			originalPostNumber = threadSummary.originalPostNumber;
			lastExistingPostNumber = threadSummary.lastExistingPostNumber;
		}
		boolean partial = !loadFullThread && Preferences.isPartialThreadLoading(chan);
		HttpValidator useValidator = !loadFullThread && meta != null ? meta.validator : null;
		try {
			ChanPerformer.ReadPostsResult result;
			try {
				String lastPostNumber = lastExistingPostNumber != null ? lastExistingPostNumber.toString() : null;
				result = chan.performer.safe().onReadPosts(new ChanPerformer.ReadPostsData(chan.name, boardName,
						threadNumber, lastPostNumber, partial, lastPostNumber != null, holder, useValidator));
			} catch (ThreadRedirectException e) {
				RedirectException.Target target = e.obtainTarget(chan.name, boardName);
				if (target == null) {
					throw HttpException.createNotFoundException();
				}
				this.target = target;
				return true;
			} catch (RedirectException e) {
				RedirectException.Target target = e.obtainTarget(chan.name);
				if (target == null) {
					throw HttpException.createNotFoundException();
				}
				if (!chan.name.equals(target.chanName) || target.threadNumber == null) {
					Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES,
							"Only local thread redirects allowed");
					errorItem = new ErrorItem(ErrorItem.Type.INVALID_DATA_FORMAT);
					return false;
				} else if (CommonUtils.equals(boardName, target.boardName) &&
						threadNumber.equals(target.threadNumber)) {
					throw HttpException.createNotFoundException();
				} else {
					this.target = target;
					return true;
				}
			}
			HttpValidator validator = result != null ? result.validator : null;
			if (validator == null) {
				validator = holder.extractValidator();
			}
			if (validator == null) {
				validator = useValidator;
			}
			if (result != null && !result.posts.isEmpty() && result.fullThread) {
				partial = false;
			}

			List<Post> posts;
			if (result != null && !result.posts.isEmpty()) {
				// Remove repeats and sort
				TreeMap<PostNumber, Post> postsMap = new TreeMap<>();
				for (Post post : result.posts) {
					postsMap.put(post.number, post);
				}
				if (originalPostNumber == null && !postsMap.isEmpty()) {
					originalPostNumber = postsMap.firstKey();
				}
				posts = new ArrayList<>(postsMap.values());
			} else {
				posts = Collections.emptyList();
			}
			if (posts.isEmpty()) {
				if (partial) {
					return true;
				} else {
					errorItem = new ErrorItem(ErrorItem.Type.EMPTY_RESPONSE);
					return false;
				}
			}

			HashSet<PendingUserPost> pendingUserPosts = this.pendingUserPosts;
			HashSet<PendingUserPost> removedPendingUserPosts = null;
			if (pendingUserPosts != null && !pendingUserPosts.isEmpty()) {
				for (Post post : posts) {
					if (pendingUserPosts.isEmpty()) {
						break;
					}
					Iterator<PendingUserPost> iterator = pendingUserPosts.iterator();
					while (iterator.hasNext()) {
						PendingUserPost pendingUserPost = iterator.next();
						if (pendingUserPost.isUserPost(post, post.number.equals(originalPostNumber))) {
							iterator.remove();
							if (removedPendingUserPosts == null) {
								removedPendingUserPosts = new HashSet<>();
							}
							removedPendingUserPosts.add(pendingUserPost);
							CommonDatabase.getInstance().getPosts().setFlags(false, chan.name,
									boardName, threadNumber, post.number, PostItem.HideState.UNDEFINED, true);
							break;
						}
					}
				}
			}

			boolean newThread = meta == null;
			try {
				Uri archivedThreadUri = result.archivedThreadUri;
				if (archivedThreadUri == null && meta != null) {
					archivedThreadUri = meta.archivedThreadUri;
				}
				int uniquePosters = result.uniquePosters;
				if (uniquePosters <= 0 && meta != null) {
					uniquePosters = meta.uniquePosters;
				}
				meta = new PagesDatabase.Meta(validator, archivedThreadUri, uniquePosters);
				PagesDatabase.getInstance().insertNewPosts(threadKey, posts, meta, temporary, newThread, partial);
			} catch (IOException e) {
				errorItem = new ErrorItem(ErrorItem.Type.NO_ACCESS_TO_MEMORY);
				return false;
			}

			this.newThread = newThread;
			this.shouldExtract = true;
			this.removedPendingUserPosts = removedPendingUserPosts;
			return true;
		} catch (HttpException e) {
			int responseCode = e.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
				return true;
			}
			if (responseCode == HttpURLConnection.HTTP_NOT_FOUND ||
					responseCode == HttpURLConnection.HTTP_GONE) {
				if (chan.configuration.getOption(ChanConfiguration.OPTION_READ_SINGLE_POST)) {
					try {
						// Check the post belongs to another thread
						ChanPerformer.ReadSinglePostResult result = chan.performer.safe().onReadSinglePost
								(new ChanPerformer.ReadSinglePostData(boardName, threadNumber, holder));
						SinglePost post = result != null ? result.post : null;
						if (post != null) {
							String threadNumber = post.threadNumber;
							if (!this.threadNumber.equals(threadNumber)) {
								target = RedirectException.toThread(boardName, threadNumber, post.post.number)
										.obtainTarget(chan.name);
								return true;
							}
						}
					} catch (ExtensionException | HttpException | InvalidResponseException e2) {
						// Ignore exception
					}
				}
				errorItem = new ErrorItem(ErrorItem.Type.THREAD_NOT_EXISTS);
			} else {
				errorItem = e.getErrorItemAndHandle();
			}
			return false;
		} catch (ExtensionException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return false;
		} finally {
			chan.configuration.commit();
		}
	}

	@Override
	public void onPostExecute(Boolean success) {
		if (success) {
			if (target != null) {
				callback.onReadPostsRedirect(target);
			} else {
				callback.onReadPostsSuccess(newThread, shouldExtract, removedPendingUserPosts != null
						? removedPendingUserPosts : Collections.emptySet());
			}
		} else {
			callback.onReadPostsFail(errorItem);
		}
	}
}
