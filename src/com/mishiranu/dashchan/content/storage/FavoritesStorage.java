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

package com.mishiranu.dashchan.content.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import chan.content.ChanManager;
import chan.http.HttpValidator;
import chan.util.StringUtils;

import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.WeakObservable;

public class FavoritesStorage extends StorageManager.Storage
{
	private static final String KEY_DATA = "data";
	private static final String KEY_CHAN_NAME = "chanName";
	private static final String KEY_BOARD_NAME = "boardName";
	private static final String KEY_THREAD_NUMBER = "threadNumber";
	private static final String KEY_TITLE = "title";
	private static final String KEY_MODIFIED_TITLE = "modifiedTitle";
	private static final String KEY_WATCHER_ENABLED = "watcherEnabled";
	private static final String KEY_POSTS_COUNT = "postsCount";
	private static final String KEY_NEW_POSTS_COUNT = "newPostsCount";
	private static final String KEY_HAS_NEW_POSTS = "hasNewPosts";
	private static final String KEY_WATCHER_VALIDATOR = "watcherValidator";

	private static final FavoritesStorage INSTANCE = new FavoritesStorage();

	public static FavoritesStorage getInstance()
	{
		return INSTANCE;
	}

	private final HashMap<String, FavoriteItem> mFavoriteItemsMap = new HashMap<>();
	private final ArrayList<FavoriteItem> mFavoriteItemsList = new ArrayList<>();

	private FavoritesStorage()
	{
		super("favorites", 2000, 10000);
		JSONObject jsonObject = read();
		if (jsonObject != null)
		{
			JSONArray jsonArray = jsonObject.optJSONArray(KEY_DATA);
			if (jsonArray != null)
			{
				for (int i = 0; i < jsonArray.length(); i++)
				{
					jsonObject = jsonArray.optJSONObject(i);
					if (jsonObject != null)
					{
						try
						{
							String chanName = jsonObject.getString(KEY_CHAN_NAME);
							String boardName = jsonObject.optString(KEY_BOARD_NAME, null);
							String threadNumber = jsonObject.optString(KEY_THREAD_NUMBER, null);
							String title = jsonObject.optString(KEY_TITLE, null);
							boolean modifiedTitle = jsonObject.optBoolean(KEY_MODIFIED_TITLE);
							boolean watcherEnabled = jsonObject.optBoolean(KEY_WATCHER_ENABLED);
							int postsCount = jsonObject.optInt(KEY_POSTS_COUNT);
							int newPostsCount = jsonObject.optInt(KEY_NEW_POSTS_COUNT);
							boolean hasNewPosts = jsonObject.optBoolean(KEY_HAS_NEW_POSTS);
							HttpValidator watcherValidator = HttpValidator.fromString(jsonObject
									.optString(KEY_WATCHER_VALIDATOR, null));
							FavoriteItem favoriteItem = new FavoriteItem(chanName, boardName, threadNumber,
									title, modifiedTitle, watcherEnabled, postsCount, newPostsCount,
									hasNewPosts, watcherValidator);
							mFavoriteItemsMap.put(makeKey(chanName, boardName, threadNumber), favoriteItem);
							mFavoriteItemsList.add(favoriteItem);
						}
						catch (JSONException e)
						{
							throw new RuntimeException(e);
						}
					}
				}
			}
		}
	}

	@Override
	public Object onClone()
	{
		ArrayList<FavoriteItem> favoriteItems = new ArrayList<>(mFavoriteItemsList.size());
		for (FavoriteItem favoriteItem : mFavoriteItemsList) favoriteItems.add(new FavoriteItem(favoriteItem));
		return favoriteItems;
	}

	@Override
	public JSONObject onSerialize(Object data) throws JSONException
	{
		@SuppressWarnings("unchecked")
		ArrayList<FavoriteItem> favoriteItems = (ArrayList<FavoriteItem>) data;
		if (favoriteItems.size() > 0)
		{
			JSONArray jsonArray = new JSONArray();
			for (FavoriteItem favoriteItem : favoriteItems)
			{
				JSONObject jsonObject = new JSONObject();
				jsonObject.put(KEY_CHAN_NAME, favoriteItem.chanName);
				putJson(jsonObject, KEY_BOARD_NAME, favoriteItem.boardName);
				putJson(jsonObject, KEY_THREAD_NUMBER, favoriteItem.threadNumber);
				putJson(jsonObject, KEY_TITLE, favoriteItem.title);
				putJson(jsonObject, KEY_MODIFIED_TITLE, favoriteItem.modifiedTitle);
				putJson(jsonObject, KEY_WATCHER_ENABLED, favoriteItem.watcherEnabled);
				putJson(jsonObject, KEY_POSTS_COUNT, favoriteItem.postsCount);
				putJson(jsonObject, KEY_NEW_POSTS_COUNT, favoriteItem.newPostsCount);
				putJson(jsonObject, KEY_HAS_NEW_POSTS, favoriteItem.hasNewPosts);
				if (favoriteItem.watcherValidator != null)
				{
					putJson(jsonObject, KEY_WATCHER_VALIDATOR, favoriteItem.watcherValidator.toString());
				}
				jsonArray.put(jsonObject);
			}
			JSONObject jsonObject = new JSONObject();
			jsonObject.put(KEY_DATA, jsonArray);
			return jsonObject;
		}
		return null;
	}

	private final WeakObservable<Observer> mObservable = new WeakObservable<>();

	public static final int ACTION_ADD = 0;
	public static final int ACTION_REMOVE = 1;
	public static final int ACTION_MODIFY_TITLE = 2;
	public static final int ACTION_WATCHER_ENABLE = 3;
	public static final int ACTION_WATCHER_DISABLE = 4;
	public static final int ACTION_WATCHER_SYNCHRONIZE = 5;

	public interface Observer
	{
		public void onFavoritesUpdate(FavoriteItem favoriteItem, int action);
	}

	public WeakObservable<Observer> getObservable()
	{
		return mObservable;
	}

	public boolean canSortManually()
	{
		return Preferences.getFavoritesOrder() != Preferences.FAVORITES_ORDER_BY_TITLE;
	}

	private void notifyFavoritesUpdate(FavoriteItem favoriteItem, int action)
	{
		for (Observer observer : mObservable) observer.onFavoritesUpdate(favoriteItem, action);
	}

	private static String makeKey(String chanName, String boardName, String threadNumber)
	{
		return chanName + "/" + boardName + "/" + threadNumber;
	}

	private static String makeKey(FavoriteItem favoriteItem)
	{
		return makeKey(favoriteItem.chanName, favoriteItem.boardName, favoriteItem.threadNumber);
	}

	public FavoriteItem getFavorite(String chanName, String boardName, String threadNumber)
	{
		return mFavoriteItemsMap.get(makeKey(chanName, boardName, threadNumber));
	}

	public boolean hasFavorite(String chanName, String boardName, String threadNumber)
	{
		return getFavorite(chanName, boardName, threadNumber) != null;
	}

	private boolean sortIfNeededInternal()
	{
		if (!canSortManually())
		{
			switch (Preferences.getFavoritesOrder())
			{
				case Preferences.FAVORITES_ORDER_BY_TITLE:
				{
					Collections.sort(mFavoriteItemsList, mTitlesComparator);
					return true;
				}
			}
		}
		return false;
	}

	public void sortIfNeeded()
	{
		if (sortIfNeededInternal()) serialize();
	}

	public void add(FavoriteItem favoriteItem)
	{
		if (!hasFavorite(favoriteItem.chanName, favoriteItem.boardName, favoriteItem.threadNumber))
		{
			mFavoriteItemsMap.put(makeKey(favoriteItem), favoriteItem);
			int order = Preferences.getFavoritesOrder();
			if (order == Preferences.FAVORITES_ORDER_ADD_TO_THE_TOP) mFavoriteItemsList.add(0, favoriteItem);
			else mFavoriteItemsList.add(favoriteItem);
			sortIfNeededInternal();
			notifyFavoritesUpdate(favoriteItem, ACTION_ADD);
			if (favoriteItem.threadNumber != null && Preferences.isWatcherWatchInitially())
			{
				favoriteItem.watcherEnabled = true;
				notifyFavoritesUpdate(favoriteItem, ACTION_WATCHER_ENABLE);
			}
			serialize();
		}
	}

	public void add(String chanName, String boardName, String threadNumber, String title, int postsCount)
	{
		FavoriteItem favoriteItem = new FavoriteItem();
		favoriteItem.chanName = chanName;
		favoriteItem.boardName = boardName;
		favoriteItem.threadNumber = threadNumber;
		favoriteItem.title = title;
		favoriteItem.postsCount = postsCount;
		favoriteItem.newPostsCount = postsCount;
		add(favoriteItem);
	}

	public void add(String chanName, String boardName)
	{
		add(chanName, boardName, null, null, 0);
	}

	public void moveAfter(FavoriteItem favoriteItem, FavoriteItem afterFavoriteItem)
	{
		if (canSortManually() && mFavoriteItemsList.remove(favoriteItem))
		{
			int index = mFavoriteItemsList.indexOf(afterFavoriteItem) + 1;
			mFavoriteItemsList.add(index, favoriteItem);
			serialize();
		}
	}

	public void modifyTitle(String chanName, String boardName, String threadNumber, String title, boolean fromUser)
	{
		boolean empty = StringUtils.isEmpty(title);
		if (!empty || fromUser)
		{
			if (empty) title = null;
			FavoriteItem favoriteItem = getFavorite(chanName, boardName, threadNumber);
			if (favoriteItem != null && (fromUser || !favoriteItem.modifiedTitle))
			{
				boolean titleChanged = !StringUtils.equals(favoriteItem.title, title);
				boolean stateChanged = false;
				if (titleChanged)
				{
					favoriteItem.title = title;
					stateChanged = true;
				}
				if (fromUser)
				{
					boolean mofidiedTitle = !empty;
					stateChanged = favoriteItem.modifiedTitle != mofidiedTitle;
					favoriteItem.modifiedTitle = mofidiedTitle;
				}
				if (stateChanged)
				{
					if (titleChanged) sortIfNeededInternal();
					notifyFavoritesUpdate(favoriteItem, ACTION_MODIFY_TITLE);
					serialize();
				}
			}
		}
	}

	public void modifyPostsCount(String chanName, String boardName, String threadNumber, int postsCount)
	{
		FavoriteItem favoriteItem = getFavorite(chanName, boardName, threadNumber);
		if (favoriteItem != null)
		{
			favoriteItem.postsCount = postsCount;
			favoriteItem.newPostsCount = postsCount;
			favoriteItem.hasNewPosts = false;
			notifyFavoritesUpdate(favoriteItem, ACTION_WATCHER_SYNCHRONIZE);
			serialize();
		}
	}

	public void modifyWatcherData(String chanName, String boardName, String threadNumber,
			int newPostsCount, boolean hasNewPosts, HttpValidator watcherValidator)
	{
		FavoriteItem favoriteItem = getFavorite(chanName, boardName, threadNumber);
		if (favoriteItem != null)
		{
			favoriteItem.newPostsCount = newPostsCount;
			favoriteItem.hasNewPosts = hasNewPosts;
			favoriteItem.watcherValidator = watcherValidator;
			serialize();
		}
	}

	public void toggleWatcher(String chanName, String boardName, String threadNumber)
	{
		FavoriteItem favoriteItem = getFavorite(chanName, boardName, threadNumber);
		if (favoriteItem != null)
		{
			favoriteItem.watcherEnabled = !favoriteItem.watcherEnabled;
			notifyFavoritesUpdate(favoriteItem, favoriteItem.watcherEnabled
					? ACTION_WATCHER_ENABLE : ACTION_WATCHER_DISABLE);
			serialize();
		}
	}

	public void remove(String chanName, String boardName, String threadNumber)
	{
		FavoriteItem favoriteItem = mFavoriteItemsMap.remove(makeKey(chanName, boardName, threadNumber));
		if (favoriteItem != null)
		{
			mFavoriteItemsList.remove(favoriteItem);
			if (favoriteItem.watcherEnabled)
			{
				favoriteItem.watcherEnabled = false;
				notifyFavoritesUpdate(favoriteItem, ACTION_WATCHER_DISABLE);
			}
			notifyFavoritesUpdate(favoriteItem, ACTION_REMOVE);
			serialize();
		}
	}

	public ArrayList<FavoriteItem> getThreads(String chanName)
	{
		return getFavorites(chanName, true, false, false);
	}

	public ArrayList<FavoriteItem> getBoards(String chanName)
	{
		return getFavorites(chanName, false, true, true);
	}

	private ArrayList<FavoriteItem> getFavorites(String chanName, boolean threads, boolean boards,
			boolean orderByBoardName)
	{
		ArrayList<FavoriteItem> favoriteItems = new ArrayList<>();
		for (FavoriteItem favoriteItem : mFavoriteItemsList)
		{
			if ((chanName == null || favoriteItem.chanName.equals(chanName)) && (threads && boards
					|| favoriteItem.threadNumber != null && threads || favoriteItem.threadNumber == null && boards))
			{
				favoriteItems.add(favoriteItem);
			}
		}
		Comparator<FavoriteItem> comparator = orderByBoardName ? mIdentifiersComparator
				: mChanNameIndexAscendingComparator;
		Collections.sort(favoriteItems, comparator);
		return favoriteItems;
	}

	private static int compareChanNames(FavoriteItem lhs, FavoriteItem rhs)
	{
		return ChanManager.getInstance().compareChanNames(lhs.chanName, rhs.chanName);
	}

	private static int compareBoardNames(FavoriteItem lhs, FavoriteItem rhs)
	{
		return StringUtils.compare(lhs.boardName, rhs.boardName, false);
	}

	private static int compareThreadNumbers(FavoriteItem lhs, FavoriteItem rhs)
	{
		return StringUtils.compare(lhs.threadNumber, rhs.threadNumber, false);
	}

	private final Comparator<FavoriteItem> mChanNameIndexAscendingComparator = (lhs, rhs) ->
	{
		int result = compareChanNames(lhs, rhs);
		if (result != 0) return result;
		return mFavoriteItemsList.indexOf(lhs) - mFavoriteItemsList.indexOf(rhs);
	};

	private final Comparator<FavoriteItem> mIdentifiersComparator = (lhs, rhs) ->
	{
		int result = compareChanNames(lhs, rhs);
		if (result != 0) return result;
		result = compareBoardNames(lhs, rhs);
		if (result != 0) return result;
		result = compareThreadNumbers(lhs, rhs);
		if (result != 0) return result;
		return mFavoriteItemsList.indexOf(lhs) - mFavoriteItemsList.indexOf(rhs);
	};

	private final Comparator<FavoriteItem> mTitlesComparator = (lhs, rhs) ->
	{
		int result = compareChanNames(lhs, rhs);
		if (result != 0) return result;
		result = StringUtils.compare(lhs.title, rhs.title, true);
		if (result != 0) return result;
		result = compareBoardNames(lhs, rhs);
		if (result != 0) return result;
		return compareThreadNumbers(lhs, rhs);
	};

	public static class FavoriteItem
	{
		public String chanName;
		public String boardName;
		public String threadNumber;
		public String title;

		public boolean modifiedTitle;
		public boolean watcherEnabled;

		public int postsCount;
		public int newPostsCount;
		public boolean hasNewPosts;
		public HttpValidator watcherValidator;

		public FavoriteItem()
		{

		}

		public FavoriteItem(FavoriteItem favoriteItem)
		{
			this(favoriteItem.chanName, favoriteItem.boardName, favoriteItem.threadNumber, favoriteItem.title,
					favoriteItem.modifiedTitle, favoriteItem.watcherEnabled, favoriteItem.postsCount,
					favoriteItem.newPostsCount, favoriteItem.hasNewPosts, favoriteItem.watcherValidator);
		}

		public FavoriteItem(String chanName, String boardName, String threadNumber, String title, boolean modifiedTitle,
				boolean watcherEnabled, int postsCount, int newPostsCount, boolean hasNewPosts,
				HttpValidator watcherValidator)
		{
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.title = title;
			this.modifiedTitle = modifiedTitle;
			this.watcherEnabled = watcherEnabled;
			this.postsCount = postsCount;
			this.newPostsCount = newPostsCount;
			this.hasNewPosts = hasNewPosts;
			this.watcherValidator = watcherValidator;
		}

		public boolean equals(String chanName, String boardName, String threadNumber)
		{
			return this.chanName.equals(chanName) && StringUtils.equals(this.boardName, boardName)
					&& StringUtils.equals(this.threadNumber, threadNumber);
		}
	}
}