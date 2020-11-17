package com.mishiranu.dashchan.content.async;

import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import chan.content.Chan;
import chan.content.ChanConfiguration;
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

public class ExtractPostsTask extends ExecutorTask<Void, ExtractPostsTask.Result> {
	public interface Callback {
		void onExtractPostsComplete(Result result, boolean cancelled);
	}

	public static class Result {
		public final Set<PostNumber> newPosts;
		public final Set<PostNumber> deletedPosts;
		public final Set<PostNumber> editedPosts;
		public final Set<PostNumber> replyPosts;

		public final PagesDatabase.Cache cache;
		public final boolean cacheChanged;
		public final Map<PostNumber, PostItem> postItems;
		public final Collection<PostNumber> removedPosts;

		public final PostsDatabase.Flags flags;
		public final ThreadsDatabase.StateExtra stateExtra;

		public final Uri archivedThreadUri;
		public final int uniquePosters;

		public Result(Set<PostNumber> newPosts, Set<PostNumber> deletedPosts, Set<PostNumber> editedPosts,
				Set<PostNumber> replyPosts, PagesDatabase.Cache cache, boolean cacheChanged,
				Map<PostNumber, PostItem> postItems, Collection<PostNumber> removedPosts, PostsDatabase.Flags flags,
				ThreadsDatabase.StateExtra stateExtra, Uri archivedThreadUri, int uniquePosters) {
			this.newPosts = newPosts;
			this.deletedPosts = deletedPosts;
			this.editedPosts = editedPosts;
			this.replyPosts = replyPosts;
			this.cache = cache;
			this.cacheChanged = cacheChanged;
			this.postItems = postItems;
			this.removedPosts = removedPosts;
			this.flags = flags;
			this.stateExtra = stateExtra;
			this.archivedThreadUri = archivedThreadUri;
			this.uniquePosters = uniquePosters;
		}
	}

	private final Callback callback;
	private final PagesDatabase.Cache cache;
	private final Chan chan;
	private final String boardName;
	private final String threadNumber;
	private final boolean extractStateExtra;
	private final PagesDatabase.Cleanup cleanup;
	private final CancellationSignal signal = new CancellationSignal();

	public ExtractPostsTask(Callback callback, PagesDatabase.Cache cache, Chan chan,
			String boardName, String threadNumber, boolean extractStateExtra, PagesDatabase.Cleanup cleanup) {
		this.callback = callback;
		this.cache = cache;
		this.chan = chan;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.extractStateExtra = extractStateExtra;
		this.cleanup = cleanup;
	}

	@Override
	protected Result run() {
		PagesDatabase.Diff diff;
		PagesDatabase.ThreadKey threadKey = new PagesDatabase.ThreadKey(chan.name, boardName, threadNumber);
		try {
			diff = PagesDatabase.getInstance().collectDiffPosts(threadKey, cache, cleanup, signal);
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
		if (!isCancelled() && extractStateExtra) {
			stateExtra = CommonDatabase.getInstance().getThreads()
					.getStateExtra(chan.name, boardName, threadNumber);
		}
		if (!isCancelled() && diff.cache.isChanged(cache)) {
			boolean temporary = chan.configuration.getOption(ChanConfiguration.OPTION_LOCAL_MODE);
			meta = PagesDatabase.getInstance().getMeta(threadKey, temporary);
			flags = CommonDatabase.getInstance().getPosts().getFlags(chan.name, boardName, threadNumber);
			cacheChanged = true;
			postItems = new HashMap<>(diff.changed.size());
			removedPosts = diff.removed;
			PostNumber originalPostNumber = diff.cache.originalPostNumber;
			for (Post post : diff.changed) {
				postItems.put(post.number, PostItem.createPost(post, chan,
						boardName, threadNumber, originalPostNumber));
			}
		}
		return new Result(diff.newPosts, diff.deletedPosts, diff.editedPosts, diff.replyPosts,
				diff.cache, cacheChanged, postItems, removedPosts, flags, stateExtra,
				meta != null ? meta.archivedThreadUri : null, meta != null ? meta.uniquePosters : 0);
	}

	@Override
	protected void onCancel(Result result) {
		if (result != null) {
			callback.onExtractPostsComplete(result, true);
		}
	}

	@Override
	protected void onComplete(Result result) {
		callback.onExtractPostsComplete(result, false);
	}

	@Override
	public void cancel() {
		super.cancel();
		try {
			signal.cancel();
		} catch (Exception e) {
			// Ignore
		}
	}
}
