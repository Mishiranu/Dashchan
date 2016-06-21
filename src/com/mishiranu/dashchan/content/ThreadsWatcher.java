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

package com.mishiranu.dashchan.content;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

import android.os.Handler;
import android.os.Message;
import android.os.Process;

import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpValidator;
import chan.util.StringUtils;

import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.ConcurrentUtils;

public class ThreadsWatcher implements FavoritesStorage.Observer, Handler.Callback
{
	private static final HashMap<String, ExecutorService> EXECUTORS = new HashMap<>();
	
	static
	{
		for (String chanName : ChanManager.getInstance().getAvailableChanNames())
		{
			ChanConfiguration configuration = ChanConfiguration.get(chanName);
			if (configuration.getOption(ChanConfiguration.OPTION_READ_POSTS_COUNT))
			{
				EXECUTORS.put(chanName, ConcurrentUtils.newSingleThreadPool(60000, "ThreadsWatcher", chanName,
						Process.THREAD_PRIORITY_BACKGROUND));
			}
		}
	}
	
	private static final int MESSAGE_STOP = 0;
	private static final int MESSAGE_UPDATE = 1;
	private static final int MESSAGE_RESULT = 2;

	private final Handler mHandler = new Handler(this);
	private final LinkedHashMap<String, WatcherItem> mWatching = new LinkedHashMap<>();
	private final HashMap<String, WatcherTask> mTasks = new HashMap<>();
	
	private boolean mMergeChans = false;
	private Callback mCallback;
	private String mChanName;
	
	private boolean mStarted = false;
	private int mInterval;
	private boolean mRefreshPeriodically = true;
	
	public static int NEW_POSTS_COUNT_DELETED = -1;
	public static int POSTS_COUNT_DIFFERENCE_DELETED = Integer.MIN_VALUE;
	
	public static enum State {DISABLED, UNAVAILABLE, ENABLED, BUSY};
	
	public static class WatcherItem
	{
		public final String chanName;
		public final String boardName;
		public final String threadNumber;
		
		private final String mKey;
		
		private int mPostsCount;
		private int mNewPostsCount;
		private boolean mHasNewPosts;
		private boolean mError;
		private HttpValidator mValidator;

		private long mLastUpdateTime;
		private boolean mLastWasAvailable;
		private State mLastState = State.DISABLED;
		
		public WatcherItem(FavoritesStorage.FavoriteItem favoriteItem)
		{
			this(favoriteItem.chanName, favoriteItem.boardName, favoriteItem.threadNumber, favoriteItem.postsCount,
					favoriteItem.newPostsCount, favoriteItem.hasNewPosts, favoriteItem.watcherValidator);
		}
		
		public WatcherItem(String chanName, String boardName, String threadNumber, int postsCount, int newPostsCount,
				boolean hasNewPosts, HttpValidator validator)
		{
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			mKey = makeKey(chanName, boardName, threadNumber);
			mPostsCount = postsCount;
			mNewPostsCount = newPostsCount;
			mHasNewPosts = hasNewPosts;
			mValidator = validator;
		}
		
		public boolean compare(String chanName, String boardName, String threadNumber)
		{
			return this.chanName.equals(chanName) && StringUtils.equals(this.boardName, boardName) &&
					this.threadNumber.equals(threadNumber);
		}
		
		public int getPostsCountDifference()
		{
			return calculatePostsCountDifference(mNewPostsCount, mPostsCount);
		}
		
		public boolean hasNewPosts()
		{
			return mHasNewPosts;
		}
		
		public boolean isError()
		{
			return mError;
		}
		
		public State getLastState()
		{
			return mLastState;
		}
	}
	
	private static int calculatePostsCountDifference(int newPostsCount, int postsCount)
	{
		if (newPostsCount == NEW_POSTS_COUNT_DELETED) return POSTS_COUNT_DIFFERENCE_DELETED;
		return newPostsCount - postsCount;
	}
	
	public ThreadsWatcher()
	{
		ArrayList<FavoritesStorage.FavoriteItem> favoriteItems = FavoritesStorage.getInstance().getThreads(null);
		boolean available = isAvailable();
		for (FavoritesStorage.FavoriteItem favoriteItem : favoriteItems)
		{
			if (favoriteItem.watcherEnabled && EXECUTORS.containsKey(favoriteItem.chanName))
			{
				WatcherItem watcherItem = new WatcherItem(favoriteItem);
				watcherItem.mLastState = available ? State.ENABLED : State.UNAVAILABLE;
				watcherItem.mLastWasAvailable = available;
				mWatching.put(watcherItem.mKey, watcherItem);
			}
		}
		FavoritesStorage.getInstance().getObservable().register(this);
	}
	
	public void setCallback(Callback callback)
	{
		mCallback = callback;
	}
	
	public static interface Callback
	{
		public void onWatcherUpdate(WatcherItem watcherItem, State state);
	}
	
	private void notifyUpdate(WatcherItem watcherItem, State state)
	{
		watcherItem.mLastState = state;
		if (mCallback != null) mCallback.onWatcherUpdate(watcherItem, state);
	}
	
	private static String makeKey(String chanName, String boardName, String threadNumber)
	{
		return chanName + "/" + boardName + "/" + threadNumber;
	}
	
	public WatcherItem getItem(String chanName, String boardName, String threadNumber)
	{
		return mWatching.get(makeKey(chanName, boardName, threadNumber));
	}
	
	private WatcherItem getItem(FavoritesStorage.FavoriteItem favoriteItem)
	{
		return getItem(favoriteItem.chanName, favoriteItem.boardName, favoriteItem.threadNumber);
	}
	
	@Override
	public void onFavoritesUpdate(FavoritesStorage.FavoriteItem favoriteItem, int action)
	{
		switch (action)
		{
			case FavoritesStorage.ACTION_WATCHER_ENABLE:
			{
				if (favoriteItem.threadNumber == null) throw new IllegalArgumentException();
				if (EXECUTORS.containsKey(favoriteItem.chanName))
				{
					WatcherItem watcherItem = new WatcherItem(favoriteItem);
					mWatching.put(watcherItem.mKey, watcherItem);
					if (mMergeChans || favoriteItem.chanName.equals(mChanName)) enqueue(watcherItem, true);
				}
				break;
			}
			case FavoritesStorage.ACTION_WATCHER_DISABLE:
			{
				WatcherItem watcherItem = getItem(favoriteItem);
				if (watcherItem != null)
				{
					mWatching.remove(watcherItem.mKey);
					WatcherTask task = mTasks.remove(watcherItem.mKey);
					if (task != null) task.cancel(false);
					mHandler.removeMessages(MESSAGE_UPDATE, watcherItem);
					if (mMergeChans || favoriteItem.chanName.equals(mChanName))
					{
						notifyUpdate(watcherItem, State.DISABLED);
					}
				}
				break;
			}
			case FavoritesStorage.ACTION_WATCHER_SYNC:
			{
				WatcherItem watcherItem = getItem(favoriteItem);
				if (watcherItem != null)
				{
					watcherItem.mPostsCount = favoriteItem.postsCount;
					watcherItem.mNewPostsCount = favoriteItem.newPostsCount;
					watcherItem.mHasNewPosts = false;
				}
				break;
			}
		}
	}
	
	public void updateConfiguration(String chanName)
	{
		if (!StringUtils.equals(mChanName, chanName))
		{
			cancelAll();
			mChanName = chanName;
			updateAllSinceNow();
		}
	}
	
	public void start()
	{
		mHandler.removeMessages(MESSAGE_STOP);
		if (!mStarted)
		{
			mStarted = true;
			mInterval = Preferences.getWatcherRefreshInterval() * 1000;
			mRefreshPeriodically = Preferences.isWatcherRefreshPeriodically();
			mMergeChans = Preferences.isMergeChans();
			if (mRefreshPeriodically) updateAllSinceNow(); else
			{
				boolean mergeChans = mMergeChans;
				for (WatcherItem watcherItem : mWatching.values())
				{
					if (mergeChans || watcherItem.chanName.equals(mChanName)) notifyUpdate(watcherItem, State.ENABLED);
				}
			}
		}
	}
	
	public void stop()
	{
		if (mStarted)
		{
			mHandler.removeMessages(MESSAGE_STOP);
			mHandler.sendEmptyMessageDelayed(MESSAGE_STOP, 1000);
		}
	}
	
	public void update()
	{
		if (mStarted)
		{
			updateAll();
		}
	}
	
	public void cleanup()
	{
		stop();
		mWatching.clear();
		FavoritesStorage.getInstance().getObservable().unregister(this);
	}
	
	public static class TemporalCountData
	{
		public int postsCountDifference;
		public boolean hasNewPosts;
		public boolean isError;
		
		private void set(int postsCountDifference, boolean hasNewPosts, boolean isError)
		{
			this.postsCountDifference = postsCountDifference;
			this.hasNewPosts = hasNewPosts;
			this.isError = isError;
		}
	}
	
	private final TemporalCountData mTemporalCountData = new TemporalCountData();
	
	public TemporalCountData countNewPosts(FavoritesStorage.FavoriteItem favoriteItem)
	{
		TemporalCountData temporalCountData = mTemporalCountData;
		// Watcher may have newer dirty values
		WatcherItem watcherItem = getItem(favoriteItem);
		if (watcherItem != null)
		{
			temporalCountData.set(watcherItem.getPostsCountDifference(), watcherItem.mHasNewPosts,
					watcherItem.isError());
		}
		else
		{
			temporalCountData.set(calculatePostsCountDifference(favoriteItem.newPostsCount, favoriteItem.postsCount),
					favoriteItem.hasNewPosts, false);
		}
		return temporalCountData;
	}
	
	private long mLastAvailableCheck;
	private boolean mLastAvailableValue;
	
	private boolean isAvailable()
	{
		long time = System.currentTimeMillis();
		if (time - mLastAvailableCheck >= 1000)
		{
			mLastAvailableCheck = time;
			mLastAvailableValue = !mRefreshPeriodically || !Preferences.isWatcherWifiOnly()
					|| NetworkObserver.getInstance().isWifiConnected();
		}
		return mLastAvailableValue;
	}
	
	private void updateAll()
	{
		mHandler.removeMessages(MESSAGE_UPDATE);
		boolean mergeChans = mMergeChans;
		for (WatcherItem watcherItem : mWatching.values())
		{
			if (mergeChans || watcherItem.chanName.equals(mChanName)) enqueue(watcherItem, true);
		}
	}
	
	private void updateAllSinceNow()
	{
		mHandler.removeMessages(MESSAGE_UPDATE);
		long time = System.currentTimeMillis();
		boolean available = isAvailable();
		boolean mergeChans = mMergeChans;
		for (WatcherItem watcherItem : mWatching.values())
		{
			if (mergeChans || watcherItem.chanName.equals(mChanName))
			{
				long dt = time - watcherItem.mLastUpdateTime;
				if (dt >= mInterval) enqueue(watcherItem, available);
				else mHandler.sendMessageDelayed(mHandler.obtainMessage(MESSAGE_UPDATE, watcherItem), mInterval - dt);
			}
		}
	}
	
	private void enqueue(WatcherItem watcherItem, boolean available)
	{
		if (!mTasks.containsKey(watcherItem.mKey))
		{
			if (available)
			{
				WatcherTask task = new WatcherTask(watcherItem);
				mTasks.put(watcherItem.mKey, task);
				EXECUTORS.get(watcherItem.chanName).execute(task);
				watcherItem.mLastWasAvailable = true;
				notifyUpdate(watcherItem, State.BUSY);
			}
			else
			{
				enqueueDelayed(watcherItem);
				if (watcherItem.mLastWasAvailable)
				{
					watcherItem.mLastWasAvailable = false;
					notifyUpdate(watcherItem, State.UNAVAILABLE);
				}
			}
		}
	}
	
	private void enqueueDelayed(WatcherItem watcherItem)
	{
		if (mRefreshPeriodically)
		{
			mHandler.sendMessageDelayed(mHandler.obtainMessage(MESSAGE_UPDATE, watcherItem), mInterval);
		}
	}
	
	private void cancelAll()
	{
		boolean available = isAvailable();
		for (WatcherTask task : mTasks.values())
		{
			task.cancel(false);
			notifyUpdate(task.watcherItem, available ? State.ENABLED : State.UNAVAILABLE);
		}
		mTasks.clear();
	}
	
	private static class Result
	{
		public final WatcherItem watcherItem;
		public int newPostsCount = NEW_POSTS_COUNT_DELETED - 1;
		public HttpValidator validator;
		public boolean error = false;
		public boolean interrupt = false;
		public boolean notModified = false;
		
		public Result(WatcherItem watcherItem)
		{
			this.watcherItem = watcherItem;
		}
	}
	
	private class WatcherRunnable implements Callable<Result>
	{
		private final HttpHolder mHolder = new HttpHolder();
		private final Result mResult;
		
		public WatcherRunnable(WatcherItem watcherItem)
		{
			mResult = new Result(watcherItem);
		}
		
		@Override
		public Result call()
		{
			WatcherItem watcherItem = mResult.watcherItem;
			try
			{
				ChanPerformer performer = ChanPerformer.get(watcherItem.chanName);
				ChanPerformer.ReadPostsCountResult result = performer.safe()
						.onReadPostsCount(new ChanPerformer.ReadPostsCountData(watcherItem.boardName,
						watcherItem.threadNumber, 5000, 5000, mHolder, watcherItem.mValidator));
				mResult.newPostsCount = result != null ? result.postsCount : 0;
				HttpValidator validator = result != null ? result.validator : null;
				if (validator == null) validator = mHolder.getValidator();
				mResult.validator = validator;
			}
			catch (HttpException e)
			{
				int responseCode = e.getResponseCode();
				if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED)
				{
					mResult.notModified = true;
				}
				else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND)
				{
					mResult.newPostsCount = NEW_POSTS_COUNT_DELETED;
					mResult.validator = null;
				}
				else
				{
					// Interrupt on client or server error (most likely chan is down)
					if (responseCode >= HttpURLConnection.HTTP_INTERNAL_ERROR) mResult.interrupt = true;
					mResult.error = true;
				}
			}
			catch (ExtensionException | InvalidResponseException e)
			{
				e.getErrorItemAndHandle();
				mResult.error = true;
			}
			catch (Exception e)
			{
				mResult.error = true;
			}
			return mResult;
		}
	}
	
	private class WatcherTask extends FutureTask<Result>
	{
		public final WatcherItem watcherItem;
		
		public WatcherTask(WatcherItem watcherItem)
		{
			super(new WatcherRunnable(watcherItem));
			this.watcherItem = watcherItem;
		}
		
		@Override
		protected void done()
		{
			try
			{
				mHandler.obtainMessage(MESSAGE_RESULT, get()).sendToTarget();
			}
			catch (Exception e)
			{
				// Task cancelled
			}
		}
	}
	
	@Override
	public boolean handleMessage(Message msg)
	{
		switch (msg.what)
		{
			case MESSAGE_STOP:
			{
				mStarted = false;
				mHandler.removeMessages(MESSAGE_UPDATE);
				cancelAll();
				return true;
			}
			case MESSAGE_UPDATE:
			{
				WatcherItem watcherItem = (WatcherItem) msg.obj;
				enqueue(watcherItem, isAvailable());
				return true;
			}
			case MESSAGE_RESULT:
			{
				Result result = (Result) msg.obj;
				WatcherItem watcherItem = result.watcherItem;
				mTasks.remove(watcherItem.mKey);
				long time = System.currentTimeMillis();
				boolean available = isAvailable();
				watcherItem.mLastUpdateTime = time;
				watcherItem.mLastWasAvailable = available;
				if (result.interrupt)
				{
					String chanName = result.watcherItem.chanName;
					watcherItem.mError = true;
					Iterator<WatcherTask> iterator = mTasks.values().iterator();
					while (iterator.hasNext())
					{
						WatcherTask task = iterator.next();
						WatcherItem cancelItem = task.watcherItem;
						if (cancelItem.chanName.equals(chanName))
						{
							task.cancel(false);
							cancelItem.mError = true;
							cancelItem.mLastUpdateTime = time;
							cancelItem.mLastWasAvailable = available;
							enqueueDelayed(cancelItem);
							notifyUpdate(cancelItem, available ? State.ENABLED : State.UNAVAILABLE);
							iterator.remove();
						}
					}
				}
				else 
				{
					watcherItem.mError = result.error;
					if (!result.notModified)
					{
						int newPostsCount = result.newPostsCount;
						if (newPostsCount >= NEW_POSTS_COUNT_DELETED)
						{
							if (newPostsCount > watcherItem.mNewPostsCount && watcherItem.mNewPostsCount > 1
									|| newPostsCount > watcherItem.mPostsCount)
							{
								watcherItem.mHasNewPosts = true;
							}
							watcherItem.mNewPostsCount = newPostsCount;
							watcherItem.mValidator = result.validator;
							FavoritesStorage.getInstance().modifyWatcherData(watcherItem.chanName,
									watcherItem.boardName, watcherItem.threadNumber, watcherItem.mNewPostsCount,
									watcherItem.mHasNewPosts, watcherItem.mValidator);
						}
					}
				}
				enqueueDelayed(watcherItem);
				notifyUpdate(watcherItem, available ? State.ENABLED : State.UNAVAILABLE);
				return true;
			}
		}
		return false;
	}
}