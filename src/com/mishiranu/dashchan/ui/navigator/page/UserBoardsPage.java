package com.mishiranu.dashchan.ui.navigator.page;

import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import androidx.recyclerview.widget.LinearLayoutManager;
import chan.content.model.Board;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.ReadUserBoardsTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.navigator.adapter.UserBoardsAdapter;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.PullableRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class UserBoardsPage extends ListPage implements UserBoardsAdapter.Callback,
		ReadUserBoardsTask.Callback {
	private static class RetainExtra {
		public static final ExtraFactory<RetainExtra> FACTORY = RetainExtra::new;

		public Board[] boards;
	}

	private ReadUserBoardsTask readTask;

	private UserBoardsAdapter getAdapter() {
		return (UserBoardsAdapter) getRecyclerView().getAdapter();
	}

	@Override
	protected void onCreate() {
		PullableRecyclerView recyclerView = getRecyclerView();
		recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
		if (!C.API_LOLLIPOP) {
			float density = ResourceUtils.obtainDensity(recyclerView);
			ViewUtils.setNewPadding(recyclerView, (int) (16f * density), null, (int) (16f * density), null);
		}
		UserBoardsAdapter adapter = new UserBoardsAdapter(this, getPage().chanName);
		recyclerView.setAdapter(adapter);
		recyclerView.getWrapper().setPullSides(PullableWrapper.Side.TOP);
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
				(c, position) -> c.need(true)));
		adapter.applyFilter(getInitSearch().currentQuery);
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		if (retainExtra.boards != null) {
			adapter.setItems(retainExtra.boards);
			restoreListPosition();
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
		return getString(R.string.user_boards);
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
				Uri uri = getChanLocator().safe(true).createBoardUri(boardName, 0);
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
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_refresh: {
				refreshBoards(!getAdapter().isRealEmpty());
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
			getRecyclerView().getWrapper().startBusyState(PullableWrapper.Side.TOP);
			switchView(ViewType.LIST, null);
		} else {
			getRecyclerView().getWrapper().startBusyState(PullableWrapper.Side.BOTH);
			switchView(ViewType.PROGRESS, null);
		}
	}

	@Override
	public void onReadUserBoardsSuccess(Board[] boards) {
		readTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		getRetainExtra(RetainExtra.FACTORY).boards = boards;
		getAdapter().setItems(boards);
		getRecyclerView().scrollToPosition(0);
	}

	@Override
	public void onReadUserBoardsFail(ErrorItem errorItem) {
		readTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		if (getAdapter().isRealEmpty()) {
			switchView(ViewType.ERROR, errorItem.toString());
		} else {
			ClickableToast.show(getContext(), errorItem.toString());
		}
	}
}
