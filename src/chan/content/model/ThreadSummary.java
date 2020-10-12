package chan.content.model;

import chan.annotation.Public;
import com.mishiranu.dashchan.content.model.PostNumber;

@Public
public final class ThreadSummary {
	private final String boardName;
	private final String threadNumber;
	private final String description;

	private int postsCount = -1;

	@Public
	public ThreadSummary(String boardName, String threadNumber, String description) {
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.description = description;
		PostNumber.validateThreadNumber(threadNumber, false);
	}

	@Public
	public String getBoardName() {
		return boardName;
	}

	@Public
	public String getThreadNumber() {
		return threadNumber;
	}

	@Public
	public String getDescription() {
		return description;
	}

	@Public
	public int getPostsCount() {
		return postsCount;
	}

	@Public
	public ThreadSummary setPostsCount(int postsCount) {
		this.postsCount = postsCount;
		return this;
	}
}
