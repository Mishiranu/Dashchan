package com.mishiranu.dashchan.ui.navigator.page;

import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import androidx.recyclerview.widget.LinearLayoutManager;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ReadBoardsTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.navigator.adapter.BoardsAdapter;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.PullableRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BoardsPage extends ListPage implements BoardsAdapter.Callback, ReadBoardsTask.Callback {
	private ReadBoardsTask readTask;

	private BoardsAdapter getAdapter() {
		return (BoardsAdapter) getRecyclerView().getAdapter();
	}

	@Override
	protected void onCreate() {
		PullableRecyclerView recyclerView = getRecyclerView();
		recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
		if (!C.API_LOLLIPOP) {
			float density = ResourceUtils.obtainDensity(recyclerView);
			ViewUtils.setNewPadding(recyclerView, (int) (16f * density), null, (int) (16f * density), null);
		}
		BoardsAdapter adapter = new BoardsAdapter(this, getPage().chanName);
		recyclerView.setAdapter(adapter);
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
				adapter::configureDivider));
		recyclerView.getWrapper().setPullSides(PullableWrapper.Side.TOP);
		adapter.update();
		adapter.applyFilter(getInitSearch().currentQuery);
		if (adapter.isRealEmpty()) {
			refreshBoards(false);
		} else {
			restoreListPosition();
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
		boolean hasUserBoards = getChan().configuration.getOption(ChanConfiguration.OPTION_READ_USER_BOARDS);
		return getString(hasUserBoards ? R.string.general_boards : R.string.boards);
	}

	@Override
	public void onItemClick(String boardName) {
		if (boardName != null) {
			getUiManager().navigator().navigateBoardsOrThreads(getPage().chanName, boardName);
		}
	}

	@Override
	public boolean onItemLongClick(String boardName) {
		if (boardName != null) {
			DialogMenu dialogMenu = new DialogMenu(getContext());
			dialogMenu.add(R.string.copy_link, () -> {
				Uri uri = getChan().locator.safe(true).createBoardUri(boardName, 0);
				if (uri != null) {
					StringUtils.copyToClipboard(getContext(), uri.toString());
				}
			});
			if (!FavoritesStorage.getInstance().hasFavorite(getPage().chanName, boardName, null)) {
				dialogMenu.add(R.string.add_to_favorites, () -> FavoritesStorage.getInstance()
						.add(getPage().chanName, boardName));
			}
			dialogMenu.show(getUiManager().getConfigurationLock());
			return true;
		}
		return false;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu) {
		menu.add(0, R.id.menu_search, 0, R.string.filter)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, R.id.menu_refresh, 0, R.string.refresh)
				.setIcon(getActionBarIcon(R.attr.iconActionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.addSubMenu(0, R.id.menu_appearance, 0, R.string.appearance);
		menu.add(0, R.id.menu_make_home_page, 0, R.string.make_home_page)
				.setVisible(Preferences.getDefaultBoardName(getChan()) != null);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_refresh: {
				refreshBoards(!getAdapter().isRealEmpty());
				return true;
			}
			case R.id.menu_make_home_page: {
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
		readTask = new ReadBoardsTask(this, getChan());
		readTask.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
		if (showPull) {
			getRecyclerView().getWrapper().startBusyState(PullableWrapper.Side.TOP);
			switchView(ViewType.LIST, null);
		} else {
			getRecyclerView().getWrapper().startBusyState(PullableWrapper.Side.BOTH);
			switchView(ViewType.PROGRESS, null);
		}
	}

	@Override
	public void onReadBoardsSuccess(BoardCategory[] boardCategories) {
		readTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
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
		Chan chan = getChan();
		chan.configuration.storeBoards(jsonArray);
		chan.configuration.commit();
		getAdapter().update();
		getRecyclerView().scrollToPosition(0);
	}

	@Override
	public void onReadBoardsFail(ErrorItem errorItem) {
		readTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		if (getAdapter().isRealEmpty()) {
			switchView(ViewType.ERROR, errorItem.toString());
		} else {
			ClickableToast.show(getContext(), errorItem.toString());
		}
	}
}
