package com.mishiranu.dashchan.ui.navigator.page;

import android.app.AlertDialog;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import androidx.recyclerview.widget.LinearLayoutManager;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.content.storage.HistoryDatabase;
import com.mishiranu.dashchan.ui.navigator.Page;
import com.mishiranu.dashchan.ui.navigator.adapter.HistoryAdapter;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.PullableRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class HistoryPage extends ListPage implements HistoryAdapter.Callback {
	private String chanName;
	private boolean mergeChans;

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
		HistoryAdapter adapter = new HistoryAdapter(this);
		recyclerView.setAdapter(adapter);
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
				adapter::configureDivider));
		recyclerView.getWrapper().setPullSides(PullableWrapper.Side.NONE);
		if (updateConfiguration(true)) {
			restoreListPosition();
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
			adapter.updateConfiguration(getContext(), mergeChans ? null : chanName);
		}
		if (update) {
			this.chanName = chanName;
			this.mergeChans = mergeChans;
		}
		if (adapter.isRealEmpty()) {
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
	public void onItemClick(HistoryDatabase.HistoryItem historyItem) {
		if (historyItem != null) {
			getUiManager().navigator().navigatePosts(historyItem.chanName, historyItem.boardName,
					historyItem.threadNumber, null, null, 0);
		}
	}

	@Override
	public boolean onItemLongClick(HistoryDatabase.HistoryItem historyItem) {
		if (historyItem != null) {
			DialogMenu dialogMenu = new DialogMenu(getContext());
			dialogMenu.add(R.string.action_copy_link, () -> {
				Uri uri = getChanLocator().safe(true).createThreadUri(historyItem.boardName, historyItem.threadNumber);
				if (uri != null) {
					StringUtils.copyToClipboard(getContext(), uri.toString());
				}
			});
			if (!FavoritesStorage.getInstance().hasFavorite(historyItem.chanName,
					historyItem.boardName, historyItem.threadNumber)) {
				dialogMenu.add(R.string.action_add_to_favorites, () -> FavoritesStorage.getInstance()
						.add(historyItem.chanName, historyItem.boardName, historyItem.threadNumber,
								historyItem.title, 0));
			}
			dialogMenu.add(R.string.action_remove_from_history, () -> {
				if (HistoryDatabase.getInstance().remove(historyItem.chanName, historyItem.boardName,
						historyItem.threadNumber)) {
					getAdapter().remove(historyItem);
				}
			});
			dialogMenu.show(getUiManager().getConfigurationLock());
			return true;
		}
		return false;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu) {
		menu.add(0, R.id.menu_search, 0, R.string.action_filter)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, R.id.menu_clear, 0, R.string.action_clear_history);
		menu.addSubMenu(0, R.id.menu_appearance, 0, R.string.action_appearance);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_clear: {
				AlertDialog dialog = new AlertDialog.Builder(getContext())
						.setMessage(R.string.message_clear_history_confirm)
						.setNegativeButton(android.R.string.cancel, null)
						.setPositiveButton(android.R.string.ok, (d, which1) -> {
							HistoryDatabase.getInstance().clearAllHistory(mergeChans ? null : chanName);
							getAdapter().clear();
							switchView(ViewType.ERROR, R.string.message_empty_history);
						}).show();
				getUiManager().getConfigurationLock().lockConfiguration(dialog);
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
