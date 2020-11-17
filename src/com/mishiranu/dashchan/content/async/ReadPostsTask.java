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

public class ReadPostsTask extends HttpHolderTask<Void, ReadPostsTask.Result> {
	private final Callback callback;
	private final Chan chan;
	private final String boardName;
	private final String threadNumber;
	private final boolean loadFullThread;
	private final HashSet<PendingUserPost> pendingUserPosts;

	public interface Callback {
		void onPendingUserPostsConsumed(Set<PendingUserPost> pendingUserPosts);
		void onReadPostsSuccess(PagesDatabase.Cache.State cacheState,
				List<PagesDatabase.InsertResult.Reply> replies, Integer newCount);
		void onReadPostsRedirect(RedirectException.Target target);
		void onReadPostsFail(ErrorItem errorItem);
	}

	public interface Result {
		class Success implements Result {
			public final PagesDatabase.Cache.State cacheState;
			public final Set<PendingUserPost> removedPendingUserPosts;
			public final List<PagesDatabase.InsertResult.Reply> replies;
			public final Integer newCount;

			public Success(PagesDatabase.Cache.State cacheState, Set<PendingUserPost> removedPendingUserPosts,
					List<PagesDatabase.InsertResult.Reply> replies, Integer newCount) {
				this.cacheState = cacheState;
				this.removedPendingUserPosts = removedPendingUserPosts;
				this.replies = replies;
				this.newCount = newCount;
			}
		}

		class Redirect implements Result {
			public final RedirectException.Target target;

			public Redirect(RedirectException.Target target) {
				this.target = target;
			}
		}

		class Fail implements Result {
			public final ErrorItem errorItem;

			public Fail(ErrorItem errorItem) {
				this.errorItem = errorItem;
			}
		}
	}

	private static class UpdateMeta {
		public final boolean deleted;
		public final boolean error;

		private UpdateMeta(boolean deleted, boolean error) {
			this.deleted = deleted;
			this.error = error;
		}

		public boolean isChanged(PagesDatabase.Meta meta) {
			return meta != null && (meta.deleted != deleted || meta.error != error);
		}
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

	@Override
	protected Result run(HttpHolder holder) {
		boolean temporary = chan.configuration.getOption(ChanConfiguration.OPTION_LOCAL_MODE);
		PagesDatabase.ThreadKey threadKey = new PagesDatabase.ThreadKey(chan.name, boardName, threadNumber);
		PagesDatabase.Meta meta = PagesDatabase.getInstance().getMeta(threadKey, temporary);
		UpdateMeta updateMeta = null;
		PostNumber originalPostNumber;
		PostNumber lastExistingPostNumber;
		boolean allowPartialThreadLoading;
		if (loadFullThread) {
			originalPostNumber = null;
			lastExistingPostNumber = null;
			allowPartialThreadLoading = false;
		} else {
			PagesDatabase.ThreadSummary threadSummary = PagesDatabase.getInstance().getThreadSummary(threadKey);
			originalPostNumber = threadSummary.originalPostNumber;
			lastExistingPostNumber = threadSummary.lastExistingPostNumber;
			allowPartialThreadLoading = !threadSummary.cyclical ||
					Preferences.getCyclicalRefreshMode() == Preferences.CyclicalRefreshMode.DEFAULT;
		}
		boolean partial = !loadFullThread && allowPartialThreadLoading && Preferences.isPartialThreadLoading(chan);
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
				updateMeta = new UpdateMeta(true, false);
				return new Result.Redirect(target);
			} catch (RedirectException e) {
				RedirectException.Target target = e.obtainTarget(chan.name);
				if (target == null) {
					throw HttpException.createNotFoundException();
				}
				if (!chan.name.equals(target.chanName) || target.threadNumber == null) {
					Log.persistent().write(Log.TYPE_ERROR, Log.DISABLE_QUOTES,
							"Only local thread redirects allowed");
					updateMeta = new UpdateMeta(false, true);
					return new Result.Fail(new ErrorItem(ErrorItem.Type.INVALID_DATA_FORMAT));
				} else if (CommonUtils.equals(boardName, target.boardName) &&
						threadNumber.equals(target.threadNumber)) {
					throw HttpException.createNotFoundException();
				} else {
					updateMeta = new UpdateMeta(true, false);
					return new Result.Redirect(target);
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
					updateMeta = new UpdateMeta(false, false);
					return new Result.Success(PagesDatabase.getInstance().getCacheState(threadKey),
							null, Collections.emptyList(), null);
				} else {
					updateMeta = new UpdateMeta(false, true);
					return new Result.Fail(new ErrorItem(ErrorItem.Type.EMPTY_RESPONSE));
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

			PagesDatabase.InsertResult insertResult;
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
				meta = new PagesDatabase.Meta(validator, archivedThreadUri, uniquePosters, false, false);
				insertResult = PagesDatabase.getInstance().insertNewPosts(threadKey,
						posts, meta, temporary, newThread, partial);
			} catch (IOException e) {
				updateMeta = new UpdateMeta(false, true);
				return new Result.Fail(new ErrorItem(ErrorItem.Type.NO_ACCESS_TO_MEMORY));
			}
			return new Result.Success(insertResult.cacheState, removedPendingUserPosts,
					insertResult.replies, insertResult.newCount);
		} catch (HttpException e) {
			int responseCode = e.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
				updateMeta = new UpdateMeta(false, false);
				return new Result.Success(PagesDatabase.getInstance().getCacheState(threadKey),
						null, Collections.emptyList(), null);
			}
			if (responseCode == HttpURLConnection.HTTP_NOT_FOUND ||
					responseCode == HttpURLConnection.HTTP_GONE) {
				PagesDatabase.getInstance().setMetaFlags(threadKey, true, false);
				if (chan.configuration.getOption(ChanConfiguration.OPTION_READ_SINGLE_POST)) {
					try {
						// Check the post belongs to another thread
						ChanPerformer.ReadSinglePostResult result = chan.performer.safe().onReadSinglePost
								(new ChanPerformer.ReadSinglePostData(boardName, threadNumber, holder));
						SinglePost post = result != null ? result.post : null;
						if (post != null) {
							String threadNumber = post.threadNumber;
							if (!this.threadNumber.equals(threadNumber)) {
								RedirectException.Target target = RedirectException.toThread(boardName,
										threadNumber, post.post.number).obtainTarget(chan.name);
								updateMeta = new UpdateMeta(true, false);
								return new Result.Redirect(target);
							}
						}
					} catch (ExtensionException | HttpException | InvalidResponseException e2) {
						e2.getErrorItemAndHandle();
					}
				}
				updateMeta = new UpdateMeta(true, false);
				return new Result.Fail(new ErrorItem(ErrorItem.Type.THREAD_NOT_EXISTS));
			} else {
				updateMeta = new UpdateMeta(false, true);
				return new Result.Fail(e.getErrorItemAndHandle());
			}
		} catch (ExtensionException | InvalidResponseException e) {
			updateMeta = new UpdateMeta(false, true);
			return new Result.Fail(e.getErrorItemAndHandle());
		} finally {
			if (updateMeta != null && updateMeta.isChanged(meta)) {
				PagesDatabase.getInstance().setMetaFlags(threadKey, updateMeta.deleted, updateMeta.error);
			}
			chan.configuration.commit();
		}
	}

	@Override
	protected void onCancel(Result result) {
		if (result instanceof Result.Success) {
			Result.Success success = (Result.Success) result;
			if (success.removedPendingUserPosts != null) {
				callback.onPendingUserPostsConsumed(success.removedPendingUserPosts);
			}
		}
	}

	@Override
	protected void onComplete(Result result) {
		if (result instanceof Result.Success) {
			Result.Success success = (Result.Success) result;
			if (success.removedPendingUserPosts != null) {
				callback.onPendingUserPostsConsumed(success.removedPendingUserPosts);
			}
			callback.onReadPostsSuccess(success.cacheState, success.replies, success.newCount);
		} else if (result instanceof Result.Redirect) {
			Result.Redirect redirect = (Result.Redirect) result;
			callback.onReadPostsRedirect(redirect.target);
		} else if (result instanceof Result.Fail) {
			Result.Fail fail = (Result.Fail) result;
			callback.onReadPostsFail(fail.errorItem);
		} else {
			throw new IllegalStateException();
		}
	}
}
