/*
 * Copyright 2014-2016 Fukurou Mishiranu
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chan.content.model;

import java.util.Collection;
import java.util.Iterator;

public final class BoardCategory implements Iterable<Board>
{
	private final String mTitle;
	private final Board[] mBoards;
	
	public String getTitle()
	{
		return mTitle;
	}
	
	public Board[] getBoards()
	{
		return mBoards;
	}
	
	public BoardCategory(String title, Board[] boards)
	{
		mTitle = title;
		mBoards = boards;
	}
	
	public BoardCategory(String title, Collection<Board> boards)
	{
		this(title, boards.toArray(new Board[boards.size()]));
	}
	
	public int getCount()
	{
		return mBoards.length;
	}
	
	@Override
	public Iterator<Board> iterator()
	{
		return new BoardIterator();
	}
	
	private class BoardIterator implements Iterator<Board>
	{
		private int mIndex = 0;
		
		@Override
		public boolean hasNext()
		{
			return mIndex < mBoards.length;
		}
		
		@Override
		public Board next()
		{
			return mBoards[mIndex++];
		}
		
		@Override
		public void remove()
		{
			throw new UnsupportedOperationException();
		}
	}
}