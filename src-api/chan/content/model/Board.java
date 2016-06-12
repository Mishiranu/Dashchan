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

public final class Board implements Comparable<Board>
{
	private final String mBoardName;
	private final String mTitle;
	private final String mDescription;
	
	public String getBoardName()
	{
		return mBoardName;
	}
	
	public String getTitle()
	{
		return mTitle;
	}
	
	public String getDescription()
	{
		return mDescription;
	}
	
	public Board(String boardName, String title)
	{
		this(boardName, title, null);
	}
	
	public Board(String boardName, String title, String description)
	{
		mBoardName = boardName;
		mTitle = title;
		mDescription = description;
	}
	
	@Override
	public int compareTo(Board another)
	{
		return mBoardName.compareTo(another.mBoardName);
	}
}