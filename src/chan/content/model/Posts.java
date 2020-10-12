package chan.content.model;

import android.net.Uri;
import chan.annotation.Public;
import chan.util.CommonUtils;
import com.mishiranu.dashchan.content.database.PagesDatabase;
import com.mishiranu.dashchan.content.model.PostNumber;
import java.util.Collection;
import java.util.List;

@Public
public final class Posts {
	private final ChanBuilder builder;
	private final LazyProvider provider;

	@Public
	public Posts() {
		builder = new ChanBuilder();
		provider = null;
	}

	@Public
	public Posts(Post... posts) {
		this();
		setPosts(posts);
	}

	@Public
	public Posts(Collection<? extends Post> posts) {
		this();
		setPosts(posts);
	}

	public Posts(String chanName, String boardName, String threadNumber) {
		builder = null;
		provider = new LazyProvider(new PagesDatabase.ThreadKey(chanName, boardName, threadNumber));
	}

	@Public
	public Post[] getPosts() {
		if (builder != null) {
			return builder.posts;
		} else if (provider != null) {
			return provider.getPosts();
		} else {
			return null;
		}
	}

	@Public
	public Posts setPosts(Post... posts) {
		builder.posts = CommonUtils.removeNullItems(posts, Post.class);
		return this;
	}

	@Public
	public Posts setPosts(Collection<? extends Post> posts) {
		return setPosts(CommonUtils.toArray(posts, Post.class));
	}

	public String getThreadNumber() {
		return builder.posts[0].getThreadNumberOrOriginalPostNumber();
	}

	@Public
	public Uri getArchivedThreadUri() {
		return builder != null ? builder.archivedThreadUri : null;
	}

	@Public
	public Posts setArchivedThreadUri(Uri uri) {
		if (builder != null) {
			builder.archivedThreadUri = uri;
		}
		return this;
	}

	@Public
	public int getUniquePosters() {
		return builder != null ? builder.uniquePosters : 0;
	}

	@Public
	public Posts setUniquePosters(int uniquePosters) {
		if (builder != null && uniquePosters > 0) {
			builder.uniquePosters = uniquePosters;
		}
		return this;
	}

	@Public
	public int getPostsCount() {
		return builder != null ? builder.postsCount : 0;
	}

	@Public
	public Posts addPostsCount(int postsCount) {
		if (builder != null && postsCount > 0) {
			if (builder.postsCount == -1) {
				postsCount++;
			}
			builder.postsCount += postsCount;
		}
		return this;
	}

	@Public
	public int getFilesCount() {
		return builder != null ? builder.filesCount : 0;
	}

	@Public
	public Posts addFilesCount(int filesCount) {
		if (builder != null && filesCount > 0) {
			if (builder.filesCount == -1) {
				filesCount++;
			}
			builder.filesCount += filesCount;
		}
		return this;
	}

	@Public
	public int getPostsWithFilesCount() {
		return builder != null ? builder.postsWithFilesCount : 0;
	}

	@Public
	public Posts addPostsWithFilesCount(int postsWithFilesCount) {
		if (builder != null && postsWithFilesCount > 0) {
			if (builder.postsWithFilesCount == -1) {
				postsWithFilesCount++;
			}
			builder.postsWithFilesCount += postsWithFilesCount;
		}
		return this;
	}

	public int length() {
		Post[] posts = getPosts();
		return posts != null ? posts.length : 0;
	}

	private static class ChanBuilder {
		public Post[] posts;

		public Uri archivedThreadUri;
		public int uniquePosters = 0;

		public int postsCount = -1;
		public int filesCount = -1;
		public int postsWithFilesCount = -1;
	}

	private static class LazyProvider {
		public final PagesDatabase.ThreadKey threadKey;

		public Post[] posts;

		LazyProvider(PagesDatabase.ThreadKey threadKey) {
			this.threadKey = threadKey;
		}

		public Post[] getPosts() {
			if (posts == null) {
				synchronized (this) {
					if (posts == null) {
						List<PostNumber> postNumbers = PagesDatabase.getInstance().getPostNumbers(threadKey);
						Post[] posts = new Post[postNumbers.size()];
						for (int i = 0; i < posts.length; i++) {
							posts[i] = new Post(postNumbers.get(i));
						}
						this.posts = posts;
					}
				}
			}
			return posts;
		}
	}
}
