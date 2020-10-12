package com.mishiranu.dashchan.content.async;

import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.text.ParseException;
import com.mishiranu.dashchan.content.database.CommonDatabase;
import com.mishiranu.dashchan.content.database.PagesDatabase;
import com.mishiranu.dashchan.content.database.PostsDatabase;
import com.mishiranu.dashchan.content.database.ThreadsDatabase;
import com.mishiranu.dashchan.content.model.Post;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.util.Log;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ExtractPostsTask extends CancellableTask<Void, Void, ExtractPostsTask.Result> {
	public interface Callback {
		void onExtractPostsComplete(Result result);
	}

	public static class Result {
		public final Set<PostNumber> newPosts;
		public final Set<PostNumber> deletedPosts;
		public final Set<PostNumber> editedPosts;
		public final int replyCount;

		public final PagesDatabase.Cache cache;
		public final boolean cacheChanged;
		public final Map<PostNumber, PostItem> postItems;
		public final Collection<PostNumber> removedPosts;

		public final PostsDatabase.Flags flags;
		public final ThreadsDatabase.StateExtra stateExtra;

		public final Uri archivedThreadUri;
		public final int uniquePosters;

		public final boolean initial;
		public final boolean newThread;

		public Result(Set<PostNumber> newPosts, Set<PostNumber> deletedPosts, Set<PostNumber> editedPosts,
				int replyCount, PagesDatabase.Cache cache, boolean cacheChanged, Map<PostNumber, PostItem> postItems,
				Collection<PostNumber> removedPosts, PostsDatabase.Flags flags, ThreadsDatabase.StateExtra stateExtra,
				Uri archivedThreadUri, int uniquePosters, boolean initial, boolean newThread) {
			this.newPosts = newPosts;
			this.deletedPosts = deletedPosts;
			this.editedPosts = editedPosts;
			this.replyCount = replyCount;
			this.cache = cache;
			this.cacheChanged = cacheChanged;
			this.postItems = postItems;
			this.removedPosts = removedPosts;
			this.flags = flags;
			this.stateExtra = stateExtra;
			this.archivedThreadUri = archivedThreadUri;
			this.uniquePosters = uniquePosters;
			this.initial = initial;
			this.newThread = newThread;
		}
	}

	private final Callback callback;
	private final PagesDatabase.Cache cache;
	private final String chanName;
	private final String boardName;
	private final String threadNumber;
	private final boolean initial;
	private final boolean newThread;
	private final boolean removeDeleted;
	private final CancellationSignal signal = new CancellationSignal();

	public ExtractPostsTask(Callback callback, PagesDatabase.Cache cache, String chanName, String boardName,
			String threadNumber, boolean initial, boolean newThread, boolean removeDeleted) {
		this.callback = callback;
		this.cache = cache;
		this.chanName = chanName;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.initial = initial;
		this.newThread = newThread;
		this.removeDeleted = removeDeleted;
	}

	@Override
	protected Result doInBackground(Void... params) {
		PagesDatabase.Diff diff;
		PagesDatabase.ThreadKey threadKey = new PagesDatabase.ThreadKey(chanName, boardName, threadNumber);
		try {
			diff = PagesDatabase.getInstance().collectDiffPosts(threadKey, cache, removeDeleted, signal);
		} catch (ParseException e) {
			Log.persistent().stack(e);
			return null;
		} catch (OperationCanceledException e) {
			return null;
		}
		PagesDatabase.Meta meta = null;
		PostsDatabase.Flags flags = null;
		ThreadsDatabase.StateExtra stateExtra = null;
		boolean cacheChanged = false;
		Map<PostNumber, PostItem> postItems = Collections.emptyMap();
		Collection<PostNumber> removedPosts = Collections.emptyList();
		if (initial) {
			stateExtra = CommonDatabase.getInstance().getThreads()
					.getStateExtra(chanName, boardName, threadNumber);
		}
		if (diff.cache.isChanged(cache)) {
			ChanConfiguration configuration = ChanConfiguration.get(chanName);
			boolean temporary = configuration.getOption(ChanConfiguration.OPTION_LOCAL_MODE);
			meta = PagesDatabase.getInstance().getMeta(threadKey, temporary);
			flags = CommonDatabase.getInstance().getPosts().getFlags(chanName, boardName, threadNumber);
			cacheChanged = true;
			postItems = new HashMap<>(diff.changed.size());
			removedPosts = diff.removed;
			PostNumber originalPostNumber = diff.cache.originalPostNumber;
			for (Post post : diff.changed) {
				postItems.put(post.number, PostItem.createPost(post, ChanLocator.get(chanName),
						chanName, boardName, threadNumber, originalPostNumber));
			}
		}
		return new Result(diff.newPosts, diff.deletedPosts, diff.editedPosts, diff.replyCount, diff.cache, cacheChanged,
				postItems, removedPosts, flags, stateExtra, meta != null ? meta.archivedThreadUri : null,
				meta != null ? meta.uniquePosters : 0, initial, newThread);
	}

	@Override
	public void onPostExecute(Result result) {
		callback.onExtractPostsComplete(result);
	}

	@Override
	public void cancel() {
		cancel(true);
		try {
			signal.cancel();
		} catch (Exception e) {
			// Ignore
		}
	}
}
