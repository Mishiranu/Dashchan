package com.mishiranu.dashchan.ui.navigator.page;

import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import chan.content.ChanConfiguration;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.ReadBoardsTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.navigator.adapter.BoardsAdapter;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BoardsPage extends ListPage<BoardsAdapter> implements ReadBoardsTask.Callback {
	private ReadBoardsTask readTask;

	@Override
	protected void onCreate() {
		PullableListView listView = getListView();
		if (C.API_LOLLIPOP) {
			listView.setDivider(null);
		}
		BoardsAdapter adapter = new BoardsAdapter(getPage().chanName);
		initAdapter(adapter, null);
		adapter.update();
		listView.getWrapper().setPullSides(PullableWrapper.Side.TOP);
		if (!adapter.isEmpty()) {
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
		boolean hasUserBoards = getChanConfiguration().getOption(ChanConfiguration.OPTION_READ_USER_BOARDS);
		return getString(hasUserBoards ? R.string.action_general_boards : R.string.action_boards);
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
			DialogMenu dialogMenu = new DialogMenu(getContext(), id -> {
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
			dialogMenu.show(getUiManager().getConfigurationLock());
			return true;
		}
		return false;
	}

	private static final int OPTIONS_MENU_REFRESH = 0;
	private static final int OPTIONS_MENU_MAKE_HOME_PAGE = 1;

	@Override
	public void onCreateOptionsMenu(Menu menu) {
		menu.add(0, OPTIONS_MENU_SEARCH, 0, R.string.action_filter)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, OPTIONS_MENU_REFRESH, 0, R.string.action_refresh).setIcon(obtainIcon(R.attr.actionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.addSubMenu(0, OPTIONS_MENU_APPEARANCE, 0, R.string.action_appearance);
		menu.add(0, OPTIONS_MENU_MAKE_HOME_PAGE, 0, R.string.action_make_home_page);
		menu.findItem(OPTIONS_MENU_MAKE_HOME_PAGE).setVisible(Preferences
				.getDefaultBoardName(getPage().chanName) != null);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case OPTIONS_MENU_REFRESH: {
				refreshBoards(!getAdapter().isEmpty());
				return true;
			}
			case OPTIONS_MENU_MAKE_HOME_PAGE: {
				Preferences.setDefaultBoardName(getPage().chanName, null);
				item.setVisible(false);
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
		readTask = new ReadBoardsTask(getPage().chanName, this);
		readTask.executeOnExecutor(ReadBoardsTask.THREAD_POOL_EXECUTOR);
		if (showPull) {
			getListView().getWrapper().startBusyState(PullableWrapper.Side.TOP);
			switchView(ViewType.LIST, null);
		} else {
			getListView().getWrapper().startBusyState(PullableWrapper.Side.BOTH);
			switchView(ViewType.PROGRESS, null);
		}
	}

	@Override
	public void onReadBoardsSuccess(BoardCategory[] boardCategories) {
		readTask = null;
		getListView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		JSONArray jsonArray = null;
		if (boardCategories != null && boardCategories.length > 0) {
			try {
				for (BoardCategory boardCategory : boardCategories) {
					Board[] boards = boardCategory.getBoards();
					if (boards != null && boards.length > 0) {
						JSONObject jsonObject = new JSONObject();
						jsonObject.put(BoardsAdapter.KEY_TITLE, StringUtils.emptyIfNull(boardCategory.getTitle()));
						JSONArray boardsArray = new JSONArray();
						for (Board board : boards) {
							boardsArray.put(board.getBoardName());
						}
						jsonObject.put(BoardsAdapter.KEY_BOARDS, boardsArray);
						if (jsonArray == null) {
							jsonArray = new JSONArray();
						}
						jsonArray.put(jsonObject);
					}
				}
			} catch (JSONException e) {
				// Invalid data, ignore exception
			}
		}
		ChanConfiguration configuration = getChanConfiguration();
		configuration.storeBoards(jsonArray);
		configuration.commit();
		getAdapter().update();
		getListView().setSelection(0);
	}

	@Override
	public void onReadBoardsFail(ErrorItem errorItem) {
		readTask = null;
		getListView().getWrapper().cancelBusyState();
		if (getAdapter().isEmpty()) {
			switchView(ViewType.ERROR, errorItem.toString());
		} else {
			ClickableToast.show(getContext(), errorItem.toString());
		}
	}
}
