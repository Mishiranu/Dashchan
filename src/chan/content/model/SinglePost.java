package chan.content.model;

import com.mishiranu.dashchan.content.model.PostNumber;

public final class SinglePost {
	public final com.mishiranu.dashchan.content.model.Post post;
	public final String threadNumber;
	public final PostNumber originalPostNumber;

	public SinglePost(com.mishiranu.dashchan.content.model.Post post,
			String threadNumber, PostNumber originalPostNumber) {
		this.post = post;
		this.threadNumber = threadNumber;
		this.originalPostNumber = originalPostNumber;
	}

	public SinglePost(Post post) {
		String threadNumber = post.getThreadNumberOrOriginalPostNumber();
		String originalPostNumberString = post.getOriginalPostNumber();
		PostNumber originalPostNumber = originalPostNumberString != null
				? PostNumber.parseOrThrow(originalPostNumberString) : null;
		this.post = post.build();
		this.threadNumber = threadNumber;
		this.originalPostNumber = originalPostNumber;
	}
}
