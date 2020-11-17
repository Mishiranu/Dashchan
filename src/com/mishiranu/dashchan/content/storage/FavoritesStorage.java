package com.mishiranu.dashchan.content.storage;

import chan.content.ChanManager;
import chan.text.JsonSerial;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.util.WeakObservable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class FavoritesStorage extends StorageManager.Storage<List<FavoritesStorage.FavoriteItem>> {
	private static final String KEY_DATA = "data";
	private static final String KEY_CHAN_NAME = "chanName";
	private static final String KEY_BOARD_NAME = "boardName";
	private static final String KEY_THREAD_NUMBER = "threadNumber";
	private static final String KEY_TITLE = "title";
	private static final String KEY_MODIFIED_TITLE = "modifiedTitle";
	private static final String KEY_WATCHER_ENABLED = "watcherEnabled";

	private static final FavoritesStorage INSTANCE = new FavoritesStorage();

	public static FavoritesStorage getInstance() {
		return INSTANCE;
	}

	private final HashMap<String, FavoriteItem> favoriteItemsMap = new HashMap<>();
	private final ArrayList<FavoriteItem> favoriteItemsList = new ArrayList<>();

	private FavoritesStorage() {
		super("favorites", 2000, 10000);
		startRead();
	}

	@Override
	public List<FavoriteItem> onClone() {
		ArrayList<FavoriteItem> favoriteItems = new ArrayList<>(favoriteItemsList.size());
		for (FavoriteItem favoriteItem : favoriteItemsList) {
			favoriteItems.add(new FavoriteItem(favoriteItem));
		}
		return favoriteItems;
	}

	@Override
	public void onRead(InputStream input) throws IOException {
		try {
			JsonSerial.Reader reader = JsonSerial.reader(input);
			reader.startObject();
			while (!reader.endStruct()) {
				switch (reader.nextName()) {
					case KEY_DATA: {
						reader.startArray();
						while (!reader.endStruct()) {
							String chanName = null;
							String boardName = null;
							String threadNumber = null;
							String title = null;
							boolean modifiedTitle = false;
							boolean watcherEnabled = false;
							reader.startObject();
							while (!reader.endStruct()) {
								switch (reader.nextName()) {
									case KEY_CHAN_NAME: {
										chanName = reader.nextString();
										break;
									}
									case KEY_BOARD_NAME: {
										boardName = reader.nextString();
										break;
									}
									case KEY_THREAD_NUMBER: {
										threadNumber = reader.nextString();
										break;
									}
									case KEY_TITLE: {
										title = reader.nextString();
										break;
									}
									case KEY_MODIFIED_TITLE: {
										modifiedTitle = reader.nextBoolean();
										break;
									}
									case KEY_WATCHER_ENABLED: {
										watcherEnabled = reader.nextBoolean();
										break;
									}
									default: {
										reader.skip();
										break;
									}
								}
							}
							FavoriteItem favoriteItem = new FavoriteItem(chanName, boardName, threadNumber,
									title, modifiedTitle, watcherEnabled);
							favoriteItemsMap.put(makeKey(chanName, boardName, threadNumber), favoriteItem);
							favoriteItemsList.add(favoriteItem);
						}
						break;
					}
					default: {
						reader.skip();
						break;
					}
				}
			}
		} catch (ParseException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void onWrite(List<FavoriteItem> favoriteItems, OutputStream output) throws IOException {
		JsonSerial.Writer writer = JsonSerial.writer(output);
		writer.startObject();
		writer.name(KEY_DATA);
		writer.startArray();
		for (FavoriteItem favoriteItem : favoriteItems) {
			writer.startObject();
			writer.name(KEY_CHAN_NAME);
			writer.value(favoriteItem.chanName);
			if (!StringUtils.isEmpty(favoriteItem.boardName)) {
				writer.name(KEY_BOARD_NAME);
				writer.value(favoriteItem.boardName);
			}
			if (!StringUtils.isEmpty(favoriteItem.threadNumber)) {
				writer.name(KEY_THREAD_NUMBER);
				writer.value(favoriteItem.threadNumber);
			}
			if (!StringUtils.isEmpty(favoriteItem.title)) {
				writer.name(KEY_TITLE);
				writer.value(favoriteItem.title);
			}
			writer.name(KEY_MODIFIED_TITLE);
			writer.value(favoriteItem.modifiedTitle);
			writer.name(KEY_WATCHER_ENABLED);
			writer.value(favoriteItem.watcherEnabled);
			writer.endObject();
		}
		writer.endArray();
		writer.endObject();
		writer.flush();
	}

	private final WeakObservable<Observer> observable = new WeakObservable<>();

	public enum Action {ADD, REMOVE, MODIFY_TITLE, WATCHER_ENABLE, WATCHER_DISABLE}

	public interface Observer {
		void onFavoritesUpdate(FavoriteItem favoriteItem, Action action);
	}

	public WeakObservable<Observer> getObservable() {
		return observable;
	}

	public boolean canSortManually() {
		return Preferences.getFavoritesOrder() != Preferences.FavoritesOrder.TITLE;
	}

	private void notifyFavoritesUpdate(FavoriteItem favoriteItem, Action action) {
		for (Observer observer : observable) {
			observer.onFavoritesUpdate(favoriteItem, action);
		}
	}

	private static String makeKey(String chanName, String boardName, String threadNumber) {
		return chanName + "/" + boardName + "/" + threadNumber;
	}

	private static String makeKey(FavoriteItem favoriteItem) {
		return makeKey(favoriteItem.chanName, favoriteItem.boardName, favoriteItem.threadNumber);
	}

	public FavoriteItem getFavorite(String chanName, String boardName, String threadNumber) {
		return favoriteItemsMap.get(makeKey(chanName, boardName, threadNumber));
	}

	public boolean hasFavorite(String chanName, String boardName, String threadNumber) {
		return getFavorite(chanName, boardName, threadNumber) != null;
	}

	private boolean sortIfNeededInternal() {
		if (!canSortManually()) {
			switch (Preferences.getFavoritesOrder()) {
				case TITLE: {
					Collections.sort(favoriteItemsList, titlesComparator);
					return true;
				}
			}
		}
		return false;
	}

	public void sortIfNeeded() {
		if (sortIfNeededInternal()) {
			serialize();
		}
	}

	public void add(FavoriteItem favoriteItem) {
		if (!hasFavorite(favoriteItem.chanName, favoriteItem.boardName, favoriteItem.threadNumber)) {
			favoriteItemsMap.put(makeKey(favoriteItem), favoriteItem);
			Preferences.FavoritesOrder order = Preferences.getFavoritesOrder();
			if (order == Preferences.FavoritesOrder.DATE_DESC) {
				favoriteItemsList.add(0, favoriteItem);
			} else {
				favoriteItemsList.add(favoriteItem);
			}
			sortIfNeededInternal();
			notifyFavoritesUpdate(favoriteItem, Action.ADD);
			if (favoriteItem.threadNumber != null && favoriteItem.watcherEnabled) {
				notifyFavoritesUpdate(favoriteItem, Action.WATCHER_ENABLE);
			}
			serialize();
		}
	}

	public void add(String chanName, String boardName, String threadNumber, String title) {
		FavoriteItem favoriteItem = new FavoriteItem(chanName, boardName, threadNumber);
		favoriteItem.title = title;
		favoriteItem.watcherEnabled = favoriteItem.threadNumber != null && Preferences.isWatcherWatchInitially();
		add(favoriteItem);
	}

	public void add(String chanName, String boardName) {
		add(new FavoriteItem(chanName, boardName, null));
	}

	public void moveAfter(FavoriteItem favoriteItem, FavoriteItem afterFavoriteItem) {
		if (canSortManually() && favoriteItemsList.remove(favoriteItem)) {
			int index = favoriteItemsList.indexOf(afterFavoriteItem) + 1;
			favoriteItemsList.add(index, favoriteItem);
			serialize();
		}
	}

	public void updateTitle(String chanName, String boardName, String threadNumber, String title, boolean fromUser) {
		boolean empty = StringUtils.isEmpty(title);
		if (!empty || fromUser) {
			if (empty) {
				title = null;
			}
			FavoriteItem favoriteItem = getFavorite(chanName, boardName, threadNumber);
			if (favoriteItem != null && (fromUser || !favoriteItem.modifiedTitle)) {
				boolean titleChanged = !CommonUtils.equals(favoriteItem.title, title);
				boolean stateChanged = false;
				if (titleChanged) {
					favoriteItem.title = title;
					stateChanged = true;
				}
				if (fromUser) {
					boolean mofidiedTitle = !empty;
					stateChanged = favoriteItem.modifiedTitle != mofidiedTitle;
					favoriteItem.modifiedTitle = mofidiedTitle;
				}
				if (stateChanged) {
					if (titleChanged) {
						sortIfNeededInternal();
					}
					notifyFavoritesUpdate(favoriteItem, Action.MODIFY_TITLE);
					serialize();
				}
			}
		}
	}

	public void setWatcherEnabled(String chanName, String boardName, String threadNumber, Boolean enabled) {
		FavoriteItem favoriteItem = getFavorite(chanName, boardName, threadNumber);
		if (favoriteItem != null) {
			boolean changed;
			if (enabled != null) {
				changed = favoriteItem.watcherEnabled != enabled;
				if (changed) {
					favoriteItem.watcherEnabled = enabled;
				}
			} else {
				favoriteItem.watcherEnabled = !favoriteItem.watcherEnabled;
				changed = true;
			}
			if (changed) {
				notifyFavoritesUpdate(favoriteItem, favoriteItem.watcherEnabled
						? Action.WATCHER_ENABLE : Action.WATCHER_DISABLE);
				serialize();
			}
		}
	}

	public void remove(String chanName, String boardName, String threadNumber) {
		FavoriteItem favoriteItem = favoriteItemsMap.remove(makeKey(chanName, boardName, threadNumber));
		if (favoriteItem != null) {
			favoriteItemsList.remove(favoriteItem);
			if (favoriteItem.watcherEnabled) {
				favoriteItem.watcherEnabled = false;
				notifyFavoritesUpdate(favoriteItem, Action.WATCHER_DISABLE);
			}
			notifyFavoritesUpdate(favoriteItem, Action.REMOVE);
			serialize();
		}
	}

	public ArrayList<FavoriteItem> getThreads(String chanName) {
		return getFavorites(chanName, true, false, false);
	}

	public ArrayList<FavoriteItem> getBoards(String chanName) {
		return getFavorites(chanName, false, true, true);
	}

	@SuppressWarnings("ConditionCoveredByFurtherCondition")
	private ArrayList<FavoriteItem> getFavorites(String chanName, boolean threads, boolean boards,
			boolean orderByBoardName) {
		ArrayList<FavoriteItem> favoriteItems = new ArrayList<>();
		for (FavoriteItem favoriteItem : favoriteItemsList) {
			if ((chanName == null || favoriteItem.chanName.equals(chanName)) && (threads && boards
					|| favoriteItem.threadNumber != null && threads || favoriteItem.threadNumber == null && boards)) {
				favoriteItems.add(favoriteItem);
			}
		}
		Comparator<FavoriteItem> comparator = orderByBoardName ? identifiersComparator
				: chanNameIndexAscendingComparator;
		Collections.sort(favoriteItems, comparator);
		return favoriteItems;
	}

	private static int compareChanNames(FavoriteItem lhs, FavoriteItem rhs) {
		return ChanManager.getInstance().compareChanNames(lhs.chanName, rhs.chanName);
	}

	private static int compareBoardNames(FavoriteItem lhs, FavoriteItem rhs) {
		return StringUtils.compare(lhs.boardName, rhs.boardName, false);
	}

	private static int compareThreadNumbers(FavoriteItem lhs, FavoriteItem rhs) {
		return StringUtils.compare(lhs.threadNumber, rhs.threadNumber, false);
	}

	private final Comparator<FavoriteItem> chanNameIndexAscendingComparator = (lhs, rhs) -> {
		int result = compareChanNames(lhs, rhs);
		if (result != 0) {
			return result;
		}
		return favoriteItemsList.indexOf(lhs) - favoriteItemsList.indexOf(rhs);
	};

	private final Comparator<FavoriteItem> identifiersComparator = (lhs, rhs) -> {
		int result = compareChanNames(lhs, rhs);
		if (result != 0) {
			return result;
		}
		result = compareBoardNames(lhs, rhs);
		if (result != 0) {
			return result;
		}
		result = compareThreadNumbers(lhs, rhs);
		if (result != 0) {
			return result;
		}
		return favoriteItemsList.indexOf(lhs) - favoriteItemsList.indexOf(rhs);
	};

	private final Comparator<FavoriteItem> titlesComparator = (lhs, rhs) -> {
		int result = compareChanNames(lhs, rhs);
		if (result != 0) {
			return result;
		}
		result = StringUtils.compare(lhs.title, rhs.title, true);
		if (result != 0) {
			return result;
		}
		result = compareBoardNames(lhs, rhs);
		if (result != 0) {
			return result;
		}
		return compareThreadNumbers(lhs, rhs);
	};

	public static class FavoriteItem {
		public final String chanName;
		public final String boardName;
		public final String threadNumber;
		public String title;

		public boolean modifiedTitle;
		public boolean watcherEnabled;

		public FavoriteItem(String chanName, String boardName, String threadNumber) {
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
		}

		public FavoriteItem(FavoriteItem favoriteItem) {
			this(favoriteItem.chanName, favoriteItem.boardName, favoriteItem.threadNumber, favoriteItem.title,
					favoriteItem.modifiedTitle, favoriteItem.watcherEnabled);
		}

		public FavoriteItem(String chanName, String boardName, String threadNumber,
				String title, boolean modifiedTitle, boolean watcherEnabled) {
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.title = title;
			this.modifiedTitle = modifiedTitle;
			this.watcherEnabled = watcherEnabled;
		}

		public boolean equals(String chanName, String boardName, String threadNumber) {
			return this.chanName.equals(chanName) &&
					CommonUtils.equals(this.boardName, boardName)
					&& CommonUtils.equals(this.threadNumber, threadNumber);
		}
	}
}
