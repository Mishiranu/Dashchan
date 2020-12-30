package com.mishiranu.dashchan.content.async;

import chan.content.Chan;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.SinglePost;
import chan.http.HttpException;
import chan.http.HttpHolder;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ReadSearchTask extends HttpHolderTask<Void, List<PostItem>> {
	private final Callback callback;
	private final Chan chan;
	private final String boardName;
	private final String searchQuery;
	private final int pageNumber;

	private ErrorItem errorItem;

	public interface Callback {
		void onReadSearchSuccess(List<PostItem> postItems, int pageNumber);
		void onReadSearchFail(ErrorItem errorItem);
	}

	public ReadSearchTask(Callback callback, Chan chan, String boardName, String searchQuery, int pageNumber) {
		super(chan);
		this.callback = callback;
		this.chan = chan;
		this.boardName = boardName;
		this.searchQuery = searchQuery;
		this.pageNumber = pageNumber;
	}

	public int getPageNumber() {
		return pageNumber;
	}

	private static final Comparator<SinglePost> TIME_COMPARATOR =
			(lhs, rhs) -> Long.compare(rhs.post.timestamp, lhs.post.timestamp);

	@Override
	protected ArrayList<PostItem> run(HttpHolder holder) {
		try {
			ChanPerformer.ReadSearchPostsResult result = chan.performer.safe().onReadSearchPosts(new ChanPerformer
					.ReadSearchPostsData(boardName, searchQuery, pageNumber, holder));
			ArrayList<SinglePost> posts = new ArrayList<>();
			if (result != null) {
				posts.addAll(result.posts);
			}
			if (!posts.isEmpty()) {
				Collections.sort(posts, TIME_COMPARATOR);
				ArrayList<PostItem> postItems = new ArrayList<>(posts.size());
				for (int i = 0; i < posts.size() && !Thread.interrupted(); i++) {
					SinglePost post = posts.get(i);
					PostItem postItem = PostItem.createPost(post.post, chan,
							boardName, post.threadNumber, post.originalPostNumber);
					postItem.setOrdinalIndex(i);
					// Preload
					ConcurrentUtils.mainGet(() -> postItem.getComment(chan));
					postItems.add(postItem);
				}
				return postItems;
			}
			return null;
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return null;
		} finally {
			chan.configuration.commit();
		}
	}

	@Override
	public void onComplete(List<PostItem> postItems) {
		if (errorItem == null) {
			callback.onReadSearchSuccess(postItems, pageNumber);
		} else {
			callback.onReadSearchFail(errorItem);
		}
	}
}
