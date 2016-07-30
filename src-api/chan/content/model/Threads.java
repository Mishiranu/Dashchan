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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

import chan.annotation.Public;
import chan.util.CommonUtils;

@Public
public final class Threads implements Serializable
{
	private static final long serialVersionUID = 1L;
	
	private Posts[][] mThreads;
	private int mStartPage;
	private int mBoardSpeed;
	
	public Posts[][] getThreads()
	{
		return mThreads;
	}
	
	public Threads setThreads(Posts[][] threads)
	{
		mThreads = threads;
		return this;
	}
	
	public Threads setThreads(Posts[] threads)
	{
		return setThreads(new Posts[][] {threads});
	}
	
	@Public
	public int getBoardSpeed()
	{
		return mBoardSpeed;
	}
	
	@Public
	public Threads setBoardSpeed(int boardSpeed)
	{
		mBoardSpeed = boardSpeed;
		return this;
	}
	
	public boolean hasThreadsOnStart()
	{
		return mThreads != null && mThreads[0] != null && mThreads[0].length > 0;
	}
	
	public int getStartPage()
	{
		return mStartPage;
	}
	
	public Threads setStartPage(int startPage)
	{
		this.mStartPage = startPage;
		return this;
	}
	
	public int getLastPage()
	{
		return mStartPage + (mThreads != null ? mThreads.length - 1 : 0);
	}
	
	public void addNextPage(Threads threads)
	{
		Posts[][] threadsArray = mThreads;
		Posts[] lastPage = threads.mThreads != null ? threads.mThreads[0] : null;
		Posts[][] newThreads = new Posts[threadsArray.length + 1][];
		System.arraycopy(threadsArray, 0, newThreads, 0, threadsArray.length);
		newThreads[threadsArray.length] = lastPage;
		setThreads(newThreads);
	}
	
	public void removeEmpty()
	{
		Posts[] lastPage = mThreads != null ? mThreads[0] : null;
		if (lastPage != null)
		{
			boolean rebuild = false;
			for (int i = 0; i < lastPage.length; i++)
			{
				if (lastPage[i] == null || lastPage[i].length() == 0)
				{
					rebuild = true;
					lastPage[i] = null;
				}
			}
			if (rebuild)
			{
				ArrayList<Posts> newLastPage = new ArrayList<>(mThreads.length);
				for (Posts posts : lastPage)
				{
					if (posts != null) newLastPage.add(posts);
				}
				mThreads[0] = CommonUtils.toArray(newLastPage, Posts.class);
			}
		}
	}
	
	public void removeRepeats(Threads allThreads)
	{
		if (allThreads == null || mThreads == null) return;
		Posts[] lastPage = mThreads[0];
		if (lastPage != null)
		{
			boolean rebuild = false;
			for (Posts[] threadsArray : allThreads.mThreads)
			{
				if (threadsArray != null)
				{
					for (Posts posts : threadsArray)
					{
						for (int i = 0; i < lastPage.length; i++)
						{
							if (lastPage[i] != null && lastPage[i].getThreadNumber().equals(posts.getThreadNumber()))
							{
								lastPage[i] = null;
								rebuild = true;
								break;
							}
						}
					}
				}
			}
			if (rebuild)
			{
				ArrayList<Posts> newLastPage = new ArrayList<>(mThreads.length);
				for (Posts posts : lastPage)
				{
					if (posts != null) newLastPage.add(posts);
				}
				mThreads[0] = CommonUtils.toArray(newLastPage, Posts.class);
			}
		}
	}
	
	@Public
	public Threads(Posts... threads)
	{
		setThreads(CommonUtils.removeNullItems(threads, Posts.class));
	}
	
	@Public
	public Threads(Collection<? extends Posts> threads)
	{
		this(CommonUtils.toArray(threads, Posts.class));
	}
}