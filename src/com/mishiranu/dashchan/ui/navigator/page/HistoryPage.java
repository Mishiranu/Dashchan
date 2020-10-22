package com.mishiranu.dashchan.ui.navigator.page;

import android.app.AlertDialog;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import androidx.recyclerview.widget.LinearLayoutManager;
import chan.content.Chan;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.GetHistoryTask;
import com.mishiranu.dashchan.content.database.CommonDatabase;
import com.mishiranu.dashchan.content.database.HistoryDatabase;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.navigator.adapter.HistoryAdapter;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.PullableRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class HistoryPage extends ListPage implements HistoryAdapter.Callback, GetHistoryTask.Callback {
	private String chanName;
	private String searchQuery;

	private GetHistoryTask task;
	private boolean firstLoad = true;

	private HistoryAdapter getAdapter() {
		return (HistoryAdapter) getRecyclerView().getAdapter();
	}

	@Override
	protected void onCreate() {
		PullableRecyclerView recyclerView = getRecyclerView();
		recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
		if (!C.API_LOLLIPOP) {
			float density = ResourceUtils.obtainDensity(recyclerView);
			ViewUtils.setNewPadding(recyclerView, (int) (16f * density), null, (int) (16f * density), null);
		}
		chanName = Preferences.isMergeChans() ? null : getPage().chanName;
		searchQuery = getInitSearch().currentQuery;
		CommonDatabase.getInstance().getHistory().registerObserver(updateHistoryRunnable);
		HistoryAdapter adapter = new HistoryAdapter(this, chanName);
		recyclerView.setAdapter(adapter);
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
				adapter::configureDivider));
		recyclerView.addItemDecoration(adapter.headerItemDecoration);
		recyclerView.setItemAnimator(null);
		recyclerView.getWrapper().setPullSides(PullableWrapper.Side.NONE);
		switchView(ViewType.PROGRESS, null);
		updateHistory();
	}

	@Override
	protected void onDestroy() {
		CommonDatabase.getInstance().getHistory().unregisterObserver(updateHistoryRunnable);
		getAdapter().setCursor(null);
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	@Override
	public String obtainTitle() {
		return getString(R.string.history);
	}

	@Override
	public void onItemClick(HistoryDatabase.HistoryItem historyItem) {
		if (historyItem != null) {
			getUiManager().navigator().navigatePosts(historyItem.chanName, historyItem.boardName,
					historyItem.threadNumber, null, null);
		}
	}

	@Override
	public boolean onItemLongClick(HistoryDatabase.HistoryItem historyItem) {
		if (historyItem != null) {
			DialogMenu dialogMenu = new DialogMenu(getContext());
			dialogMenu.add(R.string.copy_link, () -> {
				Uri uri = Chan.get(historyItem.chanName).locator.safe(true)
						.createThreadUri(historyItem.boardName, historyItem.threadNumber);
				if (uri != null) {
					StringUtils.copyToClipboard(getContext(), uri.toString());
				}
			});
			if (!FavoritesStorage.getInstance().hasFavorite(historyItem.chanName,
					historyItem.boardName, historyItem.threadNumber)) {
				dialogMenu.add(R.string.add_to_favorites, () -> FavoritesStorage.getInstance()
						.add(historyItem.chanName, historyItem.boardName, historyItem.threadNumber,
								historyItem.title, 0));
			}
			dialogMenu.add(R.string.remove_from_history, () -> CommonDatabase.getInstance().getHistory()
					.remove(historyItem.chanName, historyItem.boardName, historyItem.threadNumber));
			dialogMenu.show(getUiManager().getConfigurationLock());
			return true;
		}
		return false;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu) {
		menu.add(0, R.id.menu_search, 0, R.string.filter)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, R.id.menu_clear, 0, R.string.clear_history);
		menu.addSubMenu(0, R.id.menu_appearance, 0, R.string.appearance);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_clear: {
				AlertDialog dialog = new AlertDialog.Builder(getContext())
						.setMessage(R.string.clear_history__sentence)
						.setNegativeButton(android.R.string.cancel, null)
						.setPositiveButton(android.R.string.ok, (d, w) -> CommonDatabase
								.getInstance().getHistory().clearHistory(chanName))
						.show();
				getUiManager().getConfigurationLock().lockConfiguration(dialog);
				return true;
			}
		}
		return false;
	}

	@Override
	public void onSearchQueryChange(String query) {
		searchQuery = query;
		updateHistory();
	}

	private final Runnable updateHistoryRunnable = this::updateHistory;

	private void updateHistory() {
		if (task != null) {
			task.cancel();
		}
		task = new GetHistoryTask(this, chanName, searchQuery);
		task.executeOnExecutor(GetHistoryTask.THREAD_POOL_EXECUTOR);
	}

	@Override
	public void onGetHistoryResult(HistoryDatabase.HistoryCursor cursor) {
		task = null;
		boolean firstLoad = this.firstLoad;
		this.firstLoad = false;
		getAdapter().setCursor(cursor);
		if (cursor.hasItems) {
			switchView(ViewType.LIST, null);
			if (firstLoad) {
				restoreListPosition();
			}
		} else {
			switchView(ViewType.ERROR, R.string.history_is_empty);
		}
	}
}
