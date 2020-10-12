package chan.content.model;

import chan.annotation.Public;
import chan.util.CommonUtils;
import java.util.Collection;
import java.util.Iterator;

@Public
public final class BoardCategory implements Iterable<Board> {
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

	@Public
	@Override
	public Iterator<Board> iterator() {
		return new BoardIterator();
	}

	private class BoardIterator implements Iterator<Board> {
		private int index = 0;

		@Override
		public boolean hasNext() {
			return index < boards.length;
		}

		@Override
		public Board next() {
			return boards[index++];
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}
