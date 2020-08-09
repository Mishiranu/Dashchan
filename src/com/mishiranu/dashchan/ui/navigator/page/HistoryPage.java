package com.mishiranu.dashchan.ui.navigator.page;

import android.app.AlertDialog;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.content.storage.HistoryDatabase;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.navigator.adapter.HistoryAdapter;
import com.mishiranu.dashchan.ui.navigator.entity.Page;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class HistoryPage extends ListPage<HistoryAdapter> {
	private String chanName;
	private boolean mergeChans;

	@Override
	protected void onCreate() {
		PullableListView listView = getListView();
		HistoryAdapter adapter = new HistoryAdapter();
		initAdapter(adapter, null);
		listView.getWrapper().setPullSides(PullableWrapper.Side.NONE);
		if (updateConfiguration(true)) {
			restoreListPosition(null);
		}
	}

	@Override
	protected void onResume() {
		updateConfiguration(false);
	}

	private boolean updateConfiguration(boolean create) {
		Page page = getPage();
		HistoryAdapter adapter = getAdapter();
		String chanName = page.chanName;
		boolean mergeChans = Preferences.isMergeChans();
		boolean update = !mergeChans && !chanName.equals(this.chanName) || mergeChans != this.mergeChans;
		if (create || update) {
			adapter.updateConfiguration(mergeChans ? null : chanName);
		}
		if (update) {
			this.chanName = chanName;
			this.mergeChans = mergeChans;
		}
		if (adapter.isEmpty()) {
			switchView(ViewType.ERROR, R.string.message_empty_history);
			return false;
		} else {
			switchView(ViewType.LIST, null);
			return true;
		}
	}

	@Override
	public String obtainTitle() {
		return getString(R.string.action_history);
	}

	@Override
	public void onItemClick(View view, int position) {
		HistoryDatabase.HistoryItem historyItem = getAdapter().getHistoryItem(position);
		if (historyItem != null) {
			getUiManager().navigator().navigatePosts(historyItem.chanName, historyItem.boardName,
					historyItem.threadNumber, null, null, 0);
		}
	}

	private static final int CONTEXT_MENU_COPY_LINK = 0;
	private static final int CONTEXT_MENU_ADD_FAVORITES = 1;
	private static final int CONTEXT_MENU_REMOVE_FROM_HISTORY = 2;

	@Override
	public boolean onItemLongClick(View view, int position) {
		HistoryDatabase.HistoryItem historyItem = getAdapter().getHistoryItem(position);
		if (historyItem != null) {
			DialogMenu dialogMenu = new DialogMenu(getContext(), (context, id, extra) -> {
				switch (id) {
					case CONTEXT_MENU_COPY_LINK: {
						Uri uri = getChanLocator().safe(true).createThreadUri(historyItem.boardName,
								historyItem.threadNumber);
						if (uri != null) {
							StringUtils.copyToClipboard(getContext(), uri.toString());
						}
						break;
					}
					case CONTEXT_MENU_ADD_FAVORITES: {
						FavoritesStorage.getInstance().add(historyItem.chanName, historyItem.boardName,
								historyItem.threadNumber, historyItem.title, 0);
						break;
					}
					case CONTEXT_MENU_REMOVE_FROM_HISTORY: {
						if (HistoryDatabase.getInstance().remove(historyItem.chanName, historyItem.boardName,
								historyItem.threadNumber)) {
							getAdapter().remove(historyItem);
						}
						break;
					}
				}
			});
			dialogMenu.addItem(CONTEXT_MENU_COPY_LINK, R.string.action_copy_link);
			if (!FavoritesStorage.getInstance().hasFavorite(historyItem.chanName,
					historyItem.boardName, historyItem.threadNumber)) {
				dialogMenu.addItem(CONTEXT_MENU_ADD_FAVORITES, R.string.action_add_to_favorites);
			}
			dialogMenu.addItem(CONTEXT_MENU_REMOVE_FROM_HISTORY, R.string.action_remove_from_history);
			dialogMenu.show();
			return true;
		}
		return false;
	}

	private static final int OPTIONS_MENU_CLEAR_HISTORY = 0;

	@Override
	public void onCreateOptionsMenu(Menu menu) {
		menu.add(0, OPTIONS_MENU_SEARCH, 0, R.string.action_filter)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, OPTIONS_MENU_CLEAR_HISTORY, 0, R.string.action_clear_history);
		menu.addSubMenu(0, OPTIONS_MENU_APPEARANCE, 0, R.string.action_appearance);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case OPTIONS_MENU_CLEAR_HISTORY: {
				new AlertDialog.Builder(getContext()).setMessage(R.string.message_clear_history_confirm)
						.setNegativeButton(android.R.string.cancel, null)
						.setPositiveButton(android.R.string.ok, (dialog, which1) -> {
					HistoryDatabase.getInstance().clearAllHistory(mergeChans ? null : chanName);
					getAdapter().clear();
					switchView(ViewType.ERROR, R.string.message_empty_history);
				}).show();
				return true;
			}
		}
		return false;
	}

	@Override
	public void onSearchQueryChange(String query) {
		getAdapter().applyFilter(query);
	}
}
