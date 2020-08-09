package com.mishiranu.dashchan.ui.navigator.page;

import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import chan.content.model.Board;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.ReadUserBoardsTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.navigator.adapter.UserBoardsAdapter;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class UserBoardsPage extends ListPage<UserBoardsAdapter> implements ReadUserBoardsTask.Callback {
	private static class RetainExtra {
		public static final ExtraFactory<RetainExtra> FACTORY = RetainExtra::new;

		public Board[] boards;
	}

	private ReadUserBoardsTask readTask;

	@Override
	protected void onCreate() {
		PullableListView listView = getListView();
		UserBoardsAdapter adapter = new UserBoardsAdapter(getPage().chanName);
		initAdapter(adapter, null);
		listView.getWrapper().setPullSides(PullableWrapper.Side.TOP);
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		if (retainExtra.boards != null) {
			adapter.setItems(retainExtra.boards);
			restoreListPosition(null);
		} else {
			refreshBoards(false);
		}
	}

	@Override
	protected void onDestroy() {
		if (readTask != null) {
			readTask.cancel();
			readTask = null;
		}
	}

	@Override
	public String obtainTitle() {
		return getString(R.string.action_user_boards);
	}

	@Override
	public void onItemClick(View view, int position) {
		String boardName = getAdapter().getItem(position).boardName;
		if (boardName != null) {
			getUiManager().navigator().navigateBoardsOrThreads(getPage().chanName, boardName, 0);
		}
	}

	private static final int CONTEXT_MENU_COPY_LINK = 0;
	private static final int CONTEXT_MENU_ADD_FAVORITES = 1;

	@Override
	public boolean onItemLongClick(View view, int position) {
		String boardName = getAdapter().getItem(position).boardName;
		if (boardName != null) {
			DialogMenu dialogMenu = new DialogMenu(getContext(), (context, id, extra) -> {
				switch (id) {
					case CONTEXT_MENU_COPY_LINK: {
						Uri uri = getChanLocator().safe(true).createBoardUri(boardName, 0);
						if (uri != null) {
							StringUtils.copyToClipboard(getContext(), uri.toString());
						}
						break;
					}
					case CONTEXT_MENU_ADD_FAVORITES: {
						FavoritesStorage.getInstance().add(getPage().chanName, boardName);
						break;
					}
				}
			});
			dialogMenu.addItem(CONTEXT_MENU_COPY_LINK, R.string.action_copy_link);
			if (!FavoritesStorage.getInstance().hasFavorite(getPage().chanName, boardName, null)) {
				dialogMenu.addItem(CONTEXT_MENU_ADD_FAVORITES, R.string.action_add_to_favorites);
			}
			dialogMenu.show();
			return true;
		}
		return false;
	}

	private static final int OPTIONS_MENU_REFRESH = 0;

	@Override
	public void onCreateOptionsMenu(Menu menu) {
		menu.add(0, OPTIONS_MENU_SEARCH, 0, R.string.action_filter)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, OPTIONS_MENU_REFRESH, 0, R.string.action_refresh).setIcon(obtainIcon(R.attr.actionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.addSubMenu(0, OPTIONS_MENU_APPEARANCE, 0, R.string.action_appearance);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case OPTIONS_MENU_REFRESH: {
				refreshBoards(!getAdapter().isEmpty());
				return true;
			}
		}
		return false;
	}

	@Override
	public void onSearchQueryChange(String query) {
		getAdapter().applyFilter(query);
	}

	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side) {
		refreshBoards(true);
	}

	private void refreshBoards(boolean showPull) {
		if (readTask != null) {
			readTask.cancel();
		}
		readTask = new ReadUserBoardsTask(getPage().chanName, this);
		readTask.executeOnExecutor(ReadUserBoardsTask.THREAD_POOL_EXECUTOR);
		if (showPull) {
			getListView().getWrapper().startBusyState(PullableWrapper.Side.TOP);
			switchView(ViewType.LIST, null);
		} else {
			getListView().getWrapper().startBusyState(PullableWrapper.Side.BOTH);
			switchView(ViewType.PROGRESS, null);
		}
	}

	@Override
	public void onReadUserBoardsSuccess(Board[] boards) {
		readTask = null;
		getListView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		getRetainExtra(RetainExtra.FACTORY).boards = boards;
		getAdapter().setItems(boards);
		getListView().setSelection(0);
	}

	@Override
	public void onReadUserBoardsFail(ErrorItem errorItem) {
		readTask = null;
		getListView().getWrapper().cancelBusyState();
		if (getAdapter().isEmpty()) {
			switchView(ViewType.ERROR, errorItem.toString());
		} else {
			ClickableToast.show(getContext(), errorItem.toString());
		}
	}
}
