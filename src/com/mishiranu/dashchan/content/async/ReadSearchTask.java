package com.mishiranu.dashchan.content.async;

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.RedirectException;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpHolder;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.text.HtmlParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;

public class ReadSearchTask extends HttpHolderTask<Void, Void, ArrayList<PostItem>> implements Comparator<Post> {
	private final Callback callback;
	private final String chanName;
	private final String boardName;
	private final String searchQuery;
	private final int pageNumber;

	private ErrorItem errorItem;

	public interface Callback {
		public void onReadSearchSuccess(ArrayList<PostItem> postItems, int pageNumber);
		public void onReadSearchFail(ErrorItem errorItem);
	}

	public ReadSearchTask(Callback callback, String chanName, String boardName, String searchQuery, int pageNumber) {
		this.callback = callback;
		this.chanName = chanName;
		this.boardName = boardName;
		this.searchQuery = searchQuery;
		this.pageNumber = pageNumber;
	}

	@Override
	public int compare(Post lhs, Post rhs) {
		return ((Long) rhs.getTimestamp()).compareTo(lhs.getTimestamp());
	}

	@Override
	protected ArrayList<PostItem> doInBackground(HttpHolder holder, Void... params) {
		try {
			ChanPerformer performer = ChanPerformer.get(chanName);
			ChanConfiguration configuration = ChanConfiguration.get(chanName);
			ChanConfiguration.Board board = configuration.safe().obtainBoard(boardName);
			ArrayList<Post> posts = new ArrayList<>();
			HashSet<String> postNumbers = null;
			if (board.allowSearch) {
				ChanPerformer.ReadSearchPostsResult result = performer.safe().onReadSearchPosts(new ChanPerformer
						.ReadSearchPostsData(boardName, searchQuery, pageNumber, holder));
				Post[] readPosts = result != null ? result.posts : null;
				if (readPosts != null && readPosts.length > 0) {
					Collections.addAll(posts, readPosts);
					postNumbers = new HashSet<>();
					for (Post post : readPosts) {
						postNumbers.add(post.getPostNumber());
					}
				}
			}
			if (board.allowCatalog && board.allowCatalogSearch && pageNumber == 0) {
				ChanPerformer.ReadThreadsResult result;
				try {
					result = performer.safe().onReadThreads(new ChanPerformer.ReadThreadsData(boardName,
							ChanPerformer.ReadThreadsData.PAGE_NUMBER_CATALOG, holder, null));
				} catch (RedirectException e) {
					result = null;
				}
				Posts[] threads = result != null ? result.threads : null;
				ArrayList<Post> matched = new ArrayList<>();
				Locale locale = Locale.getDefault();
				String searchQuery = this.searchQuery.toUpperCase(locale);
				for (Posts thread : threads) {
					for (Post post : thread.getPosts()) {
						if (postNumbers == null || !postNumbers.contains(post.getPostNumber())) {
							String comment = post.getComment();
							String subject = post.getSubject();
							if (comment != null && HtmlParser.clear(comment).toUpperCase(locale).contains(searchQuery)
									|| subject != null && subject.toUpperCase(locale).contains(searchQuery)) {
								matched.add(post);
							}
						}
					}
				}
				posts.addAll(matched);
			}
			if (posts.size() > 0) {
				Collections.sort(posts, this);
				ArrayList<PostItem> postItems = new ArrayList<>(posts.size());
				for (int i = 0; i < posts.size() && !Thread.interrupted(); i++) {
					PostItem postItem = new PostItem(posts.get(i), chanName, boardName);
					postItem.setOrdinalIndex(i);
					postItem.preload();
					postItems.add(postItem);
				}
				return postItems;
			}
			return null;
		} catch (ExtensionException | HttpException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
			return null;
		} finally {
			ChanConfiguration.get(chanName).commit();
		}
	}

	@Override
	public void onPostExecute(ArrayList<PostItem> postItems) {
		if (errorItem == null) {
			callback.onReadSearchSuccess(postItems, pageNumber);
		} else {
			callback.onReadSearchFail(errorItem);
		}
	}
}
