package com.mishiranu.dashchan.ui.navigator.page;

import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import chan.content.ChanPerformer;
import chan.content.model.ThreadSummary;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.ReadThreadSummariesTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.navigator.Page;
import com.mishiranu.dashchan.ui.navigator.adapter.ArchiveAdapter;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.ListScroller;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class ArchivePage extends ListPage<ArchiveAdapter> implements ReadThreadSummariesTask.Callback {
	private static class RetainExtra {
		public static final ExtraFactory<RetainExtra> FACTORY = RetainExtra::new;

		public ThreadSummary[] threadSummaries;
		public int pageNumber;
	}

	private ReadThreadSummariesTask readTask;
	private boolean showScaleOnSuccess;

	@Override
	protected void onCreate() {
		PullableListView listView = getListView();
		if (C.API_LOLLIPOP) {
			listView.setDivider(null);
		}
		ArchiveAdapter adapter = new ArchiveAdapter();
		initAdapter(adapter, null);
		listView.getWrapper().setPullSides(PullableWrapper.Side.BOTH);
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		if (retainExtra.threadSummaries != null) {
			adapter.setItems(retainExtra.threadSummaries);
			restoreListPosition(null);
		} else {
			showScaleOnSuccess = true;
			refreshThreads(false, false);
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
		Page page = getPage();
		return getString(R.string.action_archive_view) + ": " +
				StringUtils.formatBoardTitle(page.chanName, page.boardName, null);
	}

	@Override
	public void onItemClick(View view, int position) {
		String threadNumber = getAdapter().getItem(position).getThreadNumber();
		if (threadNumber != null) {
			Page page = getPage();
			getUiManager().navigator().navigatePosts(page.chanName, page.boardName, threadNumber, null, null, 0);
		}
	}

	private static final int CONTEXT_MENU_COPY_LINK = 0;
	private static final int CONTEXT_MENU_ADD_FAVORITES = 1;

	@Override
	public boolean onItemLongClick(View view, int position) {
		Page page = getPage();
		String threadNumber = getAdapter().getItem(position).getThreadNumber();
		DialogMenu dialogMenu = new DialogMenu(getContext(), id -> {
			switch (id) {
				case CONTEXT_MENU_COPY_LINK: {
					Uri uri = getChanLocator().safe(true).createThreadUri(page.boardName, threadNumber);
					if (uri != null) {
						StringUtils.copyToClipboard(getContext(), uri.toString());
					}
					break;
				}
				case CONTEXT_MENU_ADD_FAVORITES: {
					FavoritesStorage.getInstance().add(page.chanName, page.boardName, threadNumber, null, 0);
					break;
				}
			}
		});
		dialogMenu.addItem(CONTEXT_MENU_COPY_LINK, R.string.action_copy_link);
		if (!FavoritesStorage.getInstance().hasFavorite(page.chanName, page.boardName, threadNumber)) {
			dialogMenu.addItem(CONTEXT_MENU_ADD_FAVORITES, R.string.action_add_to_favorites);
		}
		dialogMenu.show(getUiManager().getConfigurationLock());
		return true;
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
				refreshThreads(!getAdapter().isEmpty(), false);
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
		refreshThreads(true, side == PullableWrapper.Side.BOTTOM);
	}

	private void refreshThreads(boolean showPull, boolean nextPage) {
		if (readTask != null) {
			readTask.cancel();
		}
		Page page = getPage();
		int pageNumber = 0;
		if (nextPage) {
			RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
			if (retainExtra.threadSummaries != null) {
				pageNumber = retainExtra.pageNumber + 1;
			}
		}
		readTask = new ReadThreadSummariesTask(page.chanName, page.boardName, pageNumber,
				ChanPerformer.ReadThreadSummariesData.TYPE_ARCHIVED_THREADS, this);
		readTask.executeOnExecutor(ReadThreadSummariesTask.THREAD_POOL_EXECUTOR);
		if (showPull) {
			getListView().getWrapper().startBusyState(PullableWrapper.Side.TOP);
			switchView(ViewType.LIST, null);
		} else {
			getListView().getWrapper().startBusyState(PullableWrapper.Side.BOTH);
			switchView(ViewType.PROGRESS, null);
		}
	}

	@Override
	public void onReadThreadSummariesSuccess(ThreadSummary[] threadSummaries, int pageNumber) {
		readTask = null;
		PullableListView listView = getListView();
		listView.getWrapper().cancelBusyState();
		boolean showScale = showScaleOnSuccess;
		showScaleOnSuccess = false;
		if (pageNumber == 0 && threadSummaries == null) {
			if (getAdapter().isEmpty()) {
				switchView(ViewType.ERROR, R.string.message_empty_response);
			} else {
				ClickableToast.show(getContext(), R.string.message_empty_response);
			}
		} else {
			switchView(ViewType.LIST, null);
			RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
			if (pageNumber == 0) {
				getAdapter().setItems(threadSummaries);
				retainExtra.threadSummaries = threadSummaries;
				retainExtra.pageNumber = 0;
				ListViewUtils.cancelListFling(listView);
				listView.setSelection(0);
				if (showScale) {
					showScaleAnimation();
				}
			} else {
				threadSummaries = ReadThreadSummariesTask.concatenate(retainExtra.threadSummaries, threadSummaries);
				int oldCount = retainExtra.threadSummaries.length;
				if (threadSummaries.length > oldCount) {
					getAdapter().setItems(threadSummaries);
					retainExtra.threadSummaries = threadSummaries;
					retainExtra.pageNumber = pageNumber;
					if (listView.getLastVisiblePosition() + 1 == oldCount) {
						View view = listView.getChildAt(listView.getChildCount() - 1);
						if (listView.getHeight() - listView.getPaddingBottom() - view.getBottom() >= 0) {
							ListScroller.scrollTo(getListView(), oldCount);
						}
					}
				}
			}
		}
	}

	@Override
	public void onReadThreadSummariesFail(ErrorItem errorItem) {
		readTask = null;
		getListView().getWrapper().cancelBusyState();
		if (getAdapter().isEmpty()) {
			switchView(ViewType.ERROR, errorItem.toString());
		} else {
			ClickableToast.show(getContext(), errorItem.toString());
		}
	}
}
