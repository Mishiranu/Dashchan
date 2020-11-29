package com.mishiranu.dashchan.ui.navigator.page;

import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import chan.content.Chan;
import chan.content.ChanPerformer;
import chan.content.model.ThreadSummary;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.ReadThreadSummariesTask;
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.DialogMenu;
import com.mishiranu.dashchan.ui.InstanceDialog;
import com.mishiranu.dashchan.ui.navigator.Page;
import com.mishiranu.dashchan.ui.navigator.adapter.ArchiveAdapter;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.PaddedRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import java.util.List;

public class ArchivePage extends ListPage implements ArchiveAdapter.Callback,
		ReadThreadSummariesTask.Callback {
	private static class RetainableExtra implements Retainable {
		public static final ExtraFactory<RetainableExtra> FACTORY = RetainableExtra::new;

		public List<ThreadSummary> threadSummaries;
		public int pageNumber;
	}

	public static class ReadViewModel extends TaskViewModel.Proxy<ReadThreadSummariesTask,
			ReadThreadSummariesTask.Callback> {}

	private ArchiveAdapter getAdapter() {
		return (ArchiveAdapter) getRecyclerView().getAdapter();
	}

	@Override
	protected void onCreate() {
		PaddedRecyclerView recyclerView = getRecyclerView();
		recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
		if (!C.API_LOLLIPOP) {
			float density = ResourceUtils.obtainDensity(recyclerView);
			ViewUtils.setNewPadding(recyclerView, (int) (16f * density), null, (int) (16f * density), null);
		}
		ArchiveAdapter adapter = new ArchiveAdapter(this);
		recyclerView.setAdapter(adapter);
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
				adapter::configureDivider));
		recyclerView.getPullable().setPullSides(PullableWrapper.Side.BOTH);
		adapter.applyFilter(getInitSearch().currentQuery);

		InitRequest initRequest = getInitRequest();
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		ReadViewModel readViewModel = getViewModel(ReadViewModel.class);
		if (initRequest.errorItem != null) {
			switchError(initRequest.errorItem);
		} else {
			boolean load = true;
			if (retainableExtra.threadSummaries != null) {
				load = false;
				adapter.setItems(retainableExtra.threadSummaries);
				restoreListPosition();
			}
			if (readViewModel.hasTaskOrValue()) {
				if (adapter.isRealEmpty()) {
					recyclerView.getPullable().startBusyState(PullableWrapper.Side.BOTH);
					switchProgress();
				} else {
					ReadThreadSummariesTask task = readViewModel.getTask();
					boolean bottom = task != null && task.getPageNumber() > 0;
					recyclerView.getPullable().startBusyState(bottom
							? PullableWrapper.Side.BOTTOM : PullableWrapper.Side.TOP);
				}
			} else if (load) {
				refreshThreads(false, false);
			}
		}
		readViewModel.observe(this, this);
	}

	@Override
	public String obtainTitle() {
		Page page = getPage();
		return getString(R.string.archive) + ": " +
				StringUtils.formatBoardTitle(page.chanName, page.boardName, null);
	}

	@Override
	public void onItemClick(String threadNumber) {
		if (threadNumber != null) {
			Page page = getPage();
			getUiManager().navigator().navigatePosts(page.chanName, page.boardName, threadNumber, null, null);
		}
	}

	@Override
	public boolean onItemLongClick(String threadNumber) {
		Page page = getPage();
		showItemPopupMenu(getFragmentManager(), page.chanName, page.boardName, threadNumber);
		return true;
	}

	private static void showItemPopupMenu(FragmentManager fragmentManager,
			String chanName, String boardName, String threadNumber) {
		new InstanceDialog(fragmentManager, null, provider -> {
			DialogMenu dialogMenu = new DialogMenu(provider.getContext());
			dialogMenu.add(R.string.copy_link, () -> {
				Chan chan = Chan.get(chanName);
				Uri uri = chan.locator.safe(true).createThreadUri(boardName, threadNumber);
				if (uri != null) {
					StringUtils.copyToClipboard(provider.getContext(), uri.toString());
				}
			});
			if (!FavoritesStorage.getInstance().hasFavorite(chanName, boardName, threadNumber)) {
				dialogMenu.add(R.string.add_to_favorites, () -> FavoritesStorage.getInstance()
						.add(chanName, boardName, threadNumber, null));
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
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_refresh: {
				refreshThreads(!getAdapter().isRealEmpty(), false);
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
		Page page = getPage();
		int pageNumber = 0;
		if (nextPage) {
			RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
			if (retainableExtra.threadSummaries != null) {
				pageNumber = retainableExtra.pageNumber + 1;
			}
		}
		ReadViewModel readViewModel = getViewModel(ReadViewModel.class);
		ReadThreadSummariesTask task = new ReadThreadSummariesTask(readViewModel.callback,
				getChan(), page.boardName, pageNumber, ChanPerformer.ReadThreadSummariesData.TYPE_ARCHIVED_THREADS);
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
	public void onReadThreadSummariesSuccess(List<ThreadSummary> threadSummaries, int pageNumber) {
		PaddedRecyclerView recyclerView = getRecyclerView();
		recyclerView.getPullable().cancelBusyState();
		ArchiveAdapter adapter = getAdapter();
		if (pageNumber == 0 && threadSummaries == null) {
			if (adapter.isRealEmpty()) {
				switchError(R.string.empty_response);
			} else {
				ClickableToast.show(R.string.empty_response);
			}
		} else {
			switchList();
			RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
			if (pageNumber == 0) {
				boolean showScale = adapter.isRealEmpty();
				adapter.setItems(threadSummaries);
				retainableExtra.threadSummaries = threadSummaries;
				retainableExtra.pageNumber = 0;
				recyclerView.scrollToPosition(0);
				if (showScale) {
					showScaleAnimation();
				}
			} else {
				threadSummaries = ReadThreadSummariesTask
						.concatenate(retainableExtra.threadSummaries, threadSummaries);
				int oldCount = retainableExtra.threadSummaries.size();
				if (threadSummaries.size() > oldCount) {
					boolean needScroll = false;
					int childCount = recyclerView.getChildCount();
					if (childCount > 0) {
						View child = recyclerView.getChildAt(childCount - 1);
						int position = recyclerView.getChildViewHolder(child).getAdapterPosition();
						needScroll = position + 1 == oldCount &&
								recyclerView.getHeight() - recyclerView.getPaddingBottom() - child.getBottom() >= 0;
					}
					adapter.setItems(threadSummaries);
					retainableExtra.threadSummaries = threadSummaries;
					retainableExtra.pageNumber = pageNumber;
					if (needScroll) {
						ListViewUtils.smoothScrollToPosition(recyclerView, oldCount);
					}
				}
			}
		}
	}

	@Override
	public void onReadThreadSummariesFail(ErrorItem errorItem) {
		getRecyclerView().getPullable().cancelBusyState();
		if (getAdapter().isRealEmpty()) {
			switchError(errorItem);
		} else {
			ClickableToast.show(errorItem);
		}
	}
}
