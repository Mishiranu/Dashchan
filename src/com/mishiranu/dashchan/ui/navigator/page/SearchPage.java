package com.mishiranu.dashchan.ui.navigator.page;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.recyclerview.widget.LinearLayoutManager;
import chan.content.ChanConfiguration;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.ReadSearchTask;
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.ui.navigator.Page;
import com.mishiranu.dashchan.ui.navigator.adapter.SearchAdapter;
import com.mishiranu.dashchan.ui.navigator.manager.DialogUnit;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.HeaderItemDecoration;
import com.mishiranu.dashchan.widget.PaddedRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class SearchPage extends ListPage implements SearchAdapter.Callback,
		UiManager.Observer, ReadSearchTask.Callback {
	private static class RetainableExtra implements Retainable {
		public static final ExtraFactory<RetainableExtra> FACTORY = RetainableExtra::new;

		public final ArrayList<PostItem> postItems = new ArrayList<>();
		public int pageNumber;

		public DialogUnit.StackInstance.State dialogsState;

		@Override
		public void clear() {
			if (dialogsState != null) {
				dialogsState.dropState();
				dialogsState = null;
			}
		}
	}

	private static class ParcelableExtra implements Parcelable {
		public static final ExtraFactory<ParcelableExtra> FACTORY = ParcelableExtra::new;

		public boolean groupMode = false;

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeByte((byte) (groupMode ? 1 : 0));
		}

		public static final Creator<ParcelableExtra> CREATOR = new Creator<ParcelableExtra>() {
			@Override
			public ParcelableExtra createFromParcel(Parcel in) {
				ParcelableExtra parcelableExtra = new ParcelableExtra();
				parcelableExtra.groupMode = in.readByte() != 0;
				return parcelableExtra;
			}

			@Override
			public ParcelableExtra[] newArray(int size) {
				return new ParcelableExtra[size];
			}
		};
	}

	public static class ReadViewModel extends TaskViewModel.Proxy<ReadSearchTask, ReadSearchTask.Callback> {}

	private SearchAdapter getAdapter() {
		return (SearchAdapter) getRecyclerView().getAdapter();
	}

	@Override
	protected void onCreate() {
		PaddedRecyclerView recyclerView = getRecyclerView();
		recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
		Page page = getPage();
		UiManager uiManager = getUiManager();
		uiManager.view().bindThreadsPostRecyclerView(recyclerView);
		float density = ResourceUtils.obtainDensity(getResources());
		int dividerPadding = (int) (12f * density);
		SearchAdapter adapter = new SearchAdapter(getContext(), this, page.chanName,
				uiManager, getFragmentManager(), page.searchQuery);
		recyclerView.setAdapter(adapter);
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
				(c, position) -> adapter.configureDivider(c, position).horizontal(dividerPadding, dividerPadding)));
		recyclerView.addItemDecoration(new HeaderItemDecoration(adapter::configureItemHeader,
				(c, position) -> adapter.getItemHeader(position)));
		recyclerView.getPullable().setPullSides(PullableWrapper.Side.BOTH);
		uiManager.observable().register(this);

		InitRequest initRequest = getInitRequest();
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		ReadViewModel readViewModel = getViewModel(ReadViewModel.class);
		if (initRequest.shouldLoad) {
			parcelableExtra.groupMode = false;
		}
		adapter.setGroupMode(parcelableExtra.groupMode);
		if (initRequest.errorItem != null) {
			switchError(initRequest.errorItem);
		} else {
			boolean load = true;
			if (!initRequest.shouldLoad && !retainableExtra.postItems.isEmpty()) {
				load = false;
				adapter.setItems(retainableExtra.postItems);
				restoreListPosition();
				if (retainableExtra.dialogsState != null) {
					uiManager.dialog().restoreState(adapter.getConfigurationSet(), retainableExtra.dialogsState);
					retainableExtra.dialogsState.dropState();
					retainableExtra.dialogsState = null;
				}
			}
			if (readViewModel.hasTaskOrValue()) {
				if (adapter.getItemCount() == 0) {
					recyclerView.getPullable().startBusyState(PullableWrapper.Side.BOTH);
					switchProgress();
				} else {
					ReadSearchTask task = readViewModel.getTask();
					boolean bottom = task != null && task.getPageNumber() > 0;
					recyclerView.getPullable().startBusyState(bottom
							? PullableWrapper.Side.BOTTOM : PullableWrapper.Side.TOP);
				}
			} else if (load) {
				retainableExtra.postItems.clear();
				retainableExtra.pageNumber = 0;
				refreshSearch(false, false);
			}
		}
		readViewModel.observe(this, this);
	}

	@Override
	protected void onResume() {
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		if (retainableExtra.dialogsState != null) {
			retainableExtra.dialogsState.dropState();
			retainableExtra.dialogsState = null;
		}
	}

	@Override
	protected void onDestroy() {
		getUiManager().observable().unregister(this);
	}

	@Override
	protected void onNotifyAllAdaptersChanged() {
		getUiManager().dialog().notifyDataSetChangedToAll(getAdapter().getConfigurationSet().stackInstance);
	}

	@Override
	protected void onRequestStoreExtra(boolean saveToStack) {
		SearchAdapter adapter = getAdapter();
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		if (retainableExtra.dialogsState != null) {
			retainableExtra.dialogsState.dropState();
		}
		retainableExtra.dialogsState = adapter.getConfigurationSet().stackInstance.collectState();
	}

	@Override
	public String obtainTitle() {
		return getPage().searchQuery;
	}

	@Override
	public void onItemClick(PostItem postItem) {
		Page page = getPage();
		getUiManager().navigator().navigatePosts(page.chanName, page.boardName,
				postItem.getThreadNumber(), postItem.getPostNumber(), null);
	}

	@Override
	public boolean onItemLongClick(PostItem postItem) {
		getUiManager().interaction().handlePostContextMenu(getAdapter().getConfigurationSet(), postItem);
		return true;
	}

	private boolean allowSearch = false;

	@Override
	public void onCreateOptionsMenu(Menu menu) {
		menu.add(0, R.id.menu_search, 0, R.string.search)
				.setIcon(getActionBarIcon(R.attr.iconActionSearch))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, R.id.menu_refresh, 0, R.string.refresh);
		menu.add(0, R.id.menu_group, 0, R.string.group).setCheckable(true);
		menu.addSubMenu(0, R.id.menu_appearance, 0, R.string.appearance);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		ChanConfiguration.Board board = getChan().configuration.safe().obtainBoard(getPage().boardName);
		boolean search = board.allowSearch;
		boolean catalog = board.allowCatalog;
		boolean catalogSearch = catalog && board.allowCatalogSearch;
		boolean allowSearch = search || catalogSearch;
		this.allowSearch = allowSearch;
		menu.findItem(R.id.menu_search).setVisible(allowSearch);
		menu.findItem(R.id.menu_refresh).setVisible(allowSearch);
		menu.findItem(R.id.menu_group).setChecked(getAdapter().isGroupMode());
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_refresh: {
				refreshSearch(getAdapter().getItemCount() > 0, false);
				return true;
			}
			case R.id.menu_group: {
				SearchAdapter adapter = getAdapter();
				boolean groupMode = !adapter.isGroupMode();
				adapter.setGroupMode(groupMode);
				getParcelableExtra(ParcelableExtra.FACTORY).groupMode = groupMode;
				return true;
			}
		}
		return false;
	}

	@Override
	public void onAppearanceOptionChanged(int what) {
		switch (what) {
			case R.id.menu_spoilers:
			case R.id.menu_sfw_mode: {
				notifyAllAdaptersChanged();
				break;
			}
		}
	}

	@Override
	public boolean onSearchSubmit(String query) {
		if (allowSearch) {
			// Collapse search view
			getRecyclerView().post(() -> {
				Page page = getPage();
				getUiManager().navigator().navigateSearch(page.chanName, page.boardName, query);
			});
			return true;
		}
		return false;
	}

	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side) {
		refreshSearch(true, side == PullableWrapper.Side.BOTTOM);
	}

	private void refreshSearch(boolean showPull, boolean nextPage) {
		Page page = getPage();
		int pageNumber = 0;
		if (nextPage) {
			RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
			if (!retainableExtra.postItems.isEmpty()) {
				pageNumber = retainableExtra.pageNumber + 1;
			}
		}
		ReadViewModel readViewModel = getViewModel(ReadViewModel.class);
		ReadSearchTask task = new ReadSearchTask(readViewModel.callback,
				getChan(), page.boardName, page.searchQuery, pageNumber);
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
	public void onReadSearchSuccess(List<PostItem> postItems, int pageNumber) {
		PaddedRecyclerView recyclerView = getRecyclerView();
		recyclerView.getPullable().cancelBusyState();
		SearchAdapter adapter = getAdapter();
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		if (pageNumber == 0 && (postItems == null || postItems.isEmpty())) {
			switchError(R.string.not_found);
			adapter.setItems(null);
			retainableExtra.postItems.clear();
		} else {
			switchList();
			if (pageNumber == 0) {
				boolean showScale = adapter.getItemCount() == 0;
				adapter.setItems(postItems);
				retainableExtra.postItems.clear();
				retainableExtra.postItems.addAll(postItems);
				retainableExtra.pageNumber = 0;
				recyclerView.scrollToPosition(0);
				if (showScale) {
					showScaleAnimation();
				}
			} else {
				HashSet<PostNumber> existingPostNumbers = new HashSet<>();
				for (PostItem postItem : retainableExtra.postItems) {
					existingPostNumbers.add(postItem.getPostNumber());
				}
				if (postItems != null) {
					for (PostItem postItem : postItems) {
						if (!existingPostNumbers.contains(postItem.getPostNumber())) {
							retainableExtra.postItems.add(postItem);
						}
					}
				}
				if (retainableExtra.postItems.size() > existingPostNumbers.size()) {
					int oldCount = adapter.getItemCount();
					boolean groupMode = adapter.isGroupMode();
					boolean needScroll = false;
					int childCount = recyclerView.getChildCount();
					if (childCount > 0) {
						View child = recyclerView.getChildAt(childCount - 1);
						int position = recyclerView.getChildViewHolder(child).getAdapterPosition();
						needScroll = position + 1 == oldCount &&
								recyclerView.getHeight() - recyclerView.getPaddingBottom() - child.getBottom() >= 0;
					}
					adapter.setItems(retainableExtra.postItems);
					retainableExtra.pageNumber = pageNumber;
					if (!groupMode && needScroll) {
						ListViewUtils.smoothScrollToPosition(recyclerView, oldCount);
					}
				} else {
					ClickableToast.show(R.string.search_completed);
				}
			}
		}
	}

	@Override
	public void onReadSearchFail(ErrorItem errorItem) {
		getRecyclerView().getPullable().cancelBusyState();
		if (getAdapter().getItemCount() == 0) {
			switchError(errorItem);
		} else {
			ClickableToast.show(errorItem);
		}
	}

	@Override
	public void onReloadAttachmentItem(AttachmentItem attachmentItem) {
		getAdapter().reloadAttachment(attachmentItem);
	}
}
