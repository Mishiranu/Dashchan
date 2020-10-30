package com.mishiranu.dashchan.ui.navigator.page;

import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import androidx.recyclerview.widget.LinearLayoutManager;
import chan.content.ChanConfiguration;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.GetBoardsTask;
import com.mishiranu.dashchan.content.async.ReadBoardsTask;
import com.mishiranu.dashchan.content.database.ChanDatabase;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.navigator.adapter.BoardsAdapter;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.HeaderItemDecoration;
import com.mishiranu.dashchan.widget.PullableRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class BoardsPage extends ListPage implements BoardsAdapter.Callback,
		GetBoardsTask.Callback, ReadBoardsTask.Callback {
	private String searchQuery;

	private GetBoardsTask getTask;
	private ReadBoardsTask readTask;
	private boolean firstLoad = true;

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
		searchQuery = getInitSearch().currentQuery;
		BoardsAdapter adapter = new BoardsAdapter(this);
		recyclerView.setAdapter(adapter);
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
				adapter::configureDivider));
		recyclerView.addItemDecoration(new HeaderItemDecoration((c, position) -> adapter.getItemHeader(position)));
		recyclerView.setItemAnimator(null);
		recyclerView.getWrapper().setPullSides(PullableWrapper.Side.NONE);
		switchView(ViewType.PROGRESS, null);
		updateBoards();
	}

	@Override
	protected void onDestroy() {
		getAdapter().setCursor(null);
		if (getTask != null) {
			getTask.cancel();
			getTask = null;
		}
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
	public void onItemClick(ChanDatabase.BoardItem boardItem) {
		getUiManager().navigator().navigateBoardsOrThreads(getPage().chanName, boardItem.boardName);

	}

	@Override
	public boolean onItemLongClick(ChanDatabase.BoardItem boardItem) {
		DialogMenu dialogMenu = new DialogMenu(getContext());
		dialogMenu.add(R.string.copy_link, () -> {
			Uri uri = getChan().locator.safe(true).createBoardUri(boardItem.boardName, 0);
			if (uri != null) {
				StringUtils.copyToClipboard(getContext(), uri.toString());
			}
		});
		if (!FavoritesStorage.getInstance().hasFavorite(getPage().chanName, boardItem.boardName, null)) {
			dialogMenu.add(R.string.add_to_favorites, () -> FavoritesStorage.getInstance()
					.add(getPage().chanName, boardItem.boardName));
		}
		dialogMenu.show(getUiManager().getConfigurationLock());
		return true;
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
				if (!firstLoad) {
					refreshBoards(!getAdapter().isRealEmpty());
				}
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
		searchQuery = query;
		updateBoards();
	}

	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side) {
		refreshBoards(true);
	}

	private void updateBoards() {
		if (getTask != null) {
			getTask.cancel();
		}
		getTask = new GetBoardsTask(this, getChan(), null, searchQuery);
		getTask.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
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
	public void onGetBoardsResult(ChanDatabase.BoardCursor cursor) {
		getTask = null;
		boolean firstLoad = this.firstLoad;
		this.firstLoad = false;
		if (firstLoad) {
			getRecyclerView().getWrapper().setPullSides(PullableWrapper.Side.TOP);
		}
		if (cursor == null || !cursor.hasItems) {
			if (cursor != null) {
				cursor.close();
			}
			getAdapter().setCursor(null);
			if (firstLoad) {
				refreshBoards(false);
			} else {
				onReadBoardsFail(new ErrorItem(ErrorItem.Type.EMPTY_RESPONSE));
			}
		} else {
			switchView(ViewType.LIST, null);
			getAdapter().setCursor(cursor);
			if (firstLoad) {
				restoreListPosition();
			}
		}
	}

	@Override
	public void onReadBoardsSuccess() {
		readTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		updateBoards();
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
