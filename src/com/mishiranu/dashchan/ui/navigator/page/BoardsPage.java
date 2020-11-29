package com.mishiranu.dashchan.ui.navigator.page;

import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.GetBoardsTask;
import com.mishiranu.dashchan.content.async.ReadBoardsTask;
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.content.database.ChanDatabase;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.DialogMenu;
import com.mishiranu.dashchan.ui.InstanceDialog;
import com.mishiranu.dashchan.ui.navigator.adapter.BoardsAdapter;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.HeaderItemDecoration;
import com.mishiranu.dashchan.widget.PaddedRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class BoardsPage extends ListPage implements BoardsAdapter.Callback,
		GetBoardsTask.Callback, ReadBoardsTask.Callback {
	private static class RetainableExtra implements Retainable {
		public static final ExtraFactory<RetainableExtra> FACTORY = RetainableExtra::new;

		public boolean firstLoad = true;
	}

	public static class ReadViewModel extends TaskViewModel.Proxy<ReadBoardsTask, ReadBoardsTask.Callback> {}

	private String searchQuery;

	private GetBoardsTask getTask;

	private BoardsAdapter getAdapter() {
		return (BoardsAdapter) getRecyclerView().getAdapter();
	}

	@Override
	protected void onCreate() {
		PaddedRecyclerView recyclerView = getRecyclerView();
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

		InitRequest initRequest = getInitRequest();
		recyclerView.getPullable().setPullSides(PullableWrapper.Side.TOP);
 		ReadViewModel readViewModel = getViewModel(ReadViewModel.class);
		if (initRequest.errorItem != null) {
			switchError(initRequest.errorItem);
		} else {
			recyclerView.getPullable().startBusyState(PullableWrapper.Side.BOTH);
			switchProgress();
			updateBoards();
		}
		readViewModel.observe(this, this);
	}

	@Override
	protected void onDestroy() {
		getAdapter().setCursor(null);
		if (getTask != null) {
			getTask.cancel();
			getTask = null;
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
		showItemPopupMenu(getFragmentManager(), getPage().chanName, boardItem);
		return true;
	}

	private static void showItemPopupMenu(FragmentManager fragmentManager,
			String chanName, ChanDatabase.BoardItem boardItem) {
		new InstanceDialog(fragmentManager, null, provider -> {
			DialogMenu dialogMenu = new DialogMenu(provider.getContext());
			dialogMenu.add(R.string.copy_link, () -> {
				Chan chan = Chan.get(chanName);
				Uri uri = chan.locator.safe(true).createBoardUri(boardItem.boardName, 0);
				if (uri != null) {
					StringUtils.copyToClipboard(provider.getContext(), uri.toString());
				}
			});
			if (!FavoritesStorage.getInstance().hasFavorite(chanName, boardItem.boardName, null)) {
				dialogMenu.add(R.string.add_to_favorites, () -> FavoritesStorage.getInstance()
						.add(chanName, boardItem.boardName));
			}
			return dialogMenu.create();
		});
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
				RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
				if (!retainableExtra.firstLoad) {
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
		ReadViewModel readViewModel = getViewModel(ReadViewModel.class);
		ReadBoardsTask task = new ReadBoardsTask(readViewModel.callback, getChan());
		task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
		readViewModel.attach(task);
		PaddedRecyclerView recyclerView = getRecyclerView();
		if (showPull) {
			recyclerView.getPullable().startBusyState(PullableWrapper.Side.TOP);
			switchList();
		} else {
			recyclerView.getPullable().startBusyState(PullableWrapper.Side.BOTH);
			switchProgress();
		}
	}

	@Override
	public void onGetBoardsResult(ChanDatabase.BoardCursor cursor) {
		getTask = null;
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		boolean firstLoad = retainableExtra.firstLoad;
		retainableExtra.firstLoad = false;
		ReadViewModel readViewModel = getViewModel(ReadViewModel.class);
		if (cursor == null || !cursor.hasItems) {
			if (cursor != null) {
				cursor.close();
			}
			getAdapter().setCursor(null);
			if (!readViewModel.hasTaskOrValue()) {
				if (firstLoad) {
					refreshBoards(false);
				} else {
					onReadBoardsFail(new ErrorItem(ErrorItem.Type.EMPTY_RESPONSE));
				}
			}
		} else {
			switchList();
			getAdapter().setCursor(cursor);
			restoreListPosition();
			if (readViewModel.hasTaskOrValue()) {
				getRecyclerView().getPullable().startBusyState(PullableWrapper.Side.TOP);
			}
		}
	}

	@Override
	public void onReadBoardsSuccess() {
		PaddedRecyclerView recyclerView = getRecyclerView();
		recyclerView.getPullable().cancelBusyState();
		updateBoards();
		recyclerView.scrollToPosition(0);
	}

	@Override
	public void onReadBoardsFail(ErrorItem errorItem) {
		getRecyclerView().getPullable().cancelBusyState();
		if (getAdapter().isRealEmpty()) {
			switchError(errorItem);
		} else {
			ClickableToast.show(errorItem);
		}
	}
}
