package chan.content.model;

import chan.annotation.Public;

@Public
public final class Board implements Comparable<Board> {
	private final String boardName;
	private final String title;
	private final String description;

	@Public
	public String getBoardName() {
		return boardName;
	}

	@Public
	public String getTitle() {
		return title;
	}

	@Public
	public String getDescription() {
		return description;
	}

	@Public
	public Board(String boardName, String title) {
		this(boardName, title, null);
	}

	@Public
	public Board(String boardName, String title, String description) {
		this.boardName = boardName;
		this.title = title;
		this.description = description;
	}

	@Public
	@Override
	public int compareTo(Board another) {
		return boardName.compareTo(another.boardName);
	}
}
