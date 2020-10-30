package com.mishiranu.dashchan.ui.navigator.page;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuItem;
import androidx.recyclerview.widget.LinearLayoutManager;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.GetBoardsTask;
import com.mishiranu.dashchan.content.async.ReadUserBoardsTask;
import com.mishiranu.dashchan.content.database.ChanDatabase;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.navigator.adapter.UserBoardsAdapter;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.PullableRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import java.util.Collections;
import java.util.List;

public class UserBoardsPage extends ListPage implements UserBoardsAdapter.Callback,
		GetBoardsTask.Callback, ReadUserBoardsTask.Callback {
	private static class ParcelableExtra implements Parcelable {
		public static final ExtraFactory<ParcelableExtra> FACTORY = ParcelableExtra::new;

		public List<String> boardNames = Collections.emptyList();

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeStringList(boardNames);
		}

		public static final Creator<ParcelableExtra> CREATOR = new Creator<ParcelableExtra>() {
			@Override
			public ParcelableExtra createFromParcel(Parcel source) {
				ParcelableExtra parcelableExtra = new ParcelableExtra();
				parcelableExtra.boardNames = source.createStringArrayList();
				return parcelableExtra;
			}

			@Override
			public ParcelableExtra[] newArray(int size) {
				return new ParcelableExtra[size];
			}
		};
	}

	private String searchQuery;

	private GetBoardsTask getTask;
	private ReadUserBoardsTask readTask;
	private boolean firstLoad = true;

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
		searchQuery = getInitSearch().currentQuery;
		UserBoardsAdapter adapter = new UserBoardsAdapter(this);
		recyclerView.setAdapter(adapter);
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
				(c, position) -> c.need(true)));
		recyclerView.setItemAnimator(null);
		recyclerView.getWrapper().setPullSides(PullableWrapper.Side.TOP);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		if (parcelableExtra.boardNames.isEmpty()) {
			refreshBoards(false);
		} else {
			updateBoards();
		}
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
		return getString(R.string.user_boards);
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
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_refresh: {
				ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
				refreshBoards(!parcelableExtra.boardNames.isEmpty());
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
			getTask = null;
		}
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		if (parcelableExtra.boardNames.isEmpty()) {
			getAdapter().setCursor(null);
		} else {
			getTask = new GetBoardsTask(this, getChan(), parcelableExtra.boardNames, searchQuery);
			getTask.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
		}
	}

	private void refreshBoards(boolean showPull) {
		if (readTask != null) {
			readTask.cancel();
		}
		readTask = new ReadUserBoardsTask(this, getChan());
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
		getAdapter().setCursor(cursor);
		if (firstLoad) {
			restoreListPosition();
		}
	}

	@Override
	public void onReadUserBoardsSuccess(List<String> boardNames) {
		readTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		parcelableExtra.boardNames = boardNames;
		updateBoards();
		getRecyclerView().scrollToPosition(0);
	}

	@Override
	public void onReadUserBoardsFail(ErrorItem errorItem) {
		readTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		if (parcelableExtra.boardNames.isEmpty()) {
			switchView(ViewType.ERROR, errorItem.toString());
		} else {
			ClickableToast.show(getContext(), errorItem.toString());
		}
	}
}
