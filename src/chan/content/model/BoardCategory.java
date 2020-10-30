package chan.content.model;

import chan.annotation.Public;
import chan.util.CommonUtils;
import java.util.Collection;

@Public
public final class BoardCategory {
	private final String title;
	private final Board[] boards;

	@Public
	public String getTitle() {
		return title;
	}

	@Public
	public Board[] getBoards() {
		return boards;
	}

	@Public
	public BoardCategory(String title, Board[] boards) {
		this.title = title;
		this.boards = CommonUtils.removeNullItems(boards, Board.class);
	}

	@Public
	public BoardCategory(String title, Collection<Board> boards) {
		this(title, CommonUtils.toArray(boards, Board.class));
	}
}
