package com.mishiranu.dashchan.content.async;

import chan.content.model.Post;
import chan.content.model.Posts;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.model.PostItem;
import java.util.ArrayList;

public class DeserializePostsTask extends CancellableTask<Void, Void, Boolean> {
	private final Callback callback;
	private final String chanName;
	private final String boardName;
	private final String threadNumber;
	private final Posts cachedPosts;

	private final CacheManager.SerializationHolder holder = new CacheManager.SerializationHolder();

	private Posts posts;
	private ArrayList<PostItem> postItems;

	public interface Callback {
		public void onDeserializePostsComplete(boolean success, Posts posts, ArrayList<PostItem> postItems);
	}

	public DeserializePostsTask(Callback callback, String chanName, String boardName, String threadNumber,
			Posts cachedPosts) {
		this.callback = callback;
		this.chanName = chanName;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.cachedPosts = cachedPosts;
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		if (cachedPosts != null) {
			posts = cachedPosts;
		} else {
			posts = CacheManager.getInstance().deserializePosts(chanName, boardName, threadNumber, holder);
		}
		if (posts == null) {
			return false;
		}
		Post[] posts = this.posts.getPosts();
		if (posts == null || posts.length == 0) {
			return false;
		}
		postItems = new ArrayList<>(posts.length);
		for (Post post : posts) {
			postItems.add(PostItem.createPost(post, chanName, boardName));
		}
		return true;
	}

	@Override
	public void onPostExecute(Boolean success) {
		callback.onDeserializePostsComplete(success, posts, postItems);
	}

	@Override
	public void cancel() {
		cancel(true);
		holder.cancel();
	}
}
