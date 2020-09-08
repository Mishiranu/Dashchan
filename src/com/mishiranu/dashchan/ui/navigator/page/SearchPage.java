package com.mishiranu.dashchan.ui.navigator.page;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.ReadSearchTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.ui.navigator.Page;
import com.mishiranu.dashchan.ui.navigator.adapter.SearchAdapter;
import com.mishiranu.dashchan.ui.navigator.manager.DialogUnit;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.PullableRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import java.util.ArrayList;
import java.util.HashSet;

public class SearchPage extends ListPage implements SearchAdapter.Callback, ReadSearchTask.Callback {
	private static class RetainExtra {
		public static final ExtraFactory<RetainExtra> FACTORY = RetainExtra::new;

		public final ArrayList<PostItem> postItems = new ArrayList<>();
		public int pageNumber;

		public DialogUnit.StackInstance.State dialogsState;
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

	private ReadSearchTask readTask;
	private boolean showScaleOnSuccess;

	private SearchAdapter getAdapter() {
		return (SearchAdapter) getRecyclerView().getAdapter();
	}

	@Override
	protected void onCreate() {
		PullableRecyclerView recyclerView = getRecyclerView();
		recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
		UiManager uiManager = getUiManager();
		float density = ResourceUtils.obtainDensity(getResources());
		int dividerPadding = (int) (12f * density);
		SearchAdapter adapter = new SearchAdapter(this, uiManager, getPage().searchQuery);
		recyclerView.setAdapter(adapter);
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
				(c, position) -> adapter.configureDivider(c, position).horizontal(dividerPadding, dividerPadding)));
		recyclerView.getWrapper().setPullSides(PullableWrapper.Side.BOTH);
		InitRequest initRequest = getInitRequest();
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		if (initRequest.shouldLoad) {
			retainExtra.postItems.clear();
			retainExtra.pageNumber = 0;
			parcelableExtra.groupMode = false;
			showScaleOnSuccess = true;
			refreshSearch(false, false);
		} else {
			adapter.setGroupMode(parcelableExtra.groupMode);
			if (!retainExtra.postItems.isEmpty()) {
				adapter.setItems(retainExtra.postItems);
				restoreListPosition();
				if (retainExtra.dialogsState != null) {
					uiManager.dialog().restoreState(adapter.getConfigurationSet(), retainExtra.dialogsState);
					retainExtra.dialogsState = null;
				}
			} else {
				showScaleOnSuccess = true;
				refreshSearch(false, false);
			}
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
	protected void onNotifyAllAdaptersChanged() {
		getUiManager().dialog().notifyDataSetChangedToAll(getAdapter().getConfigurationSet().stackInstance);
	}

	@Override
	protected void onRequestStoreExtra(boolean saveToStack) {
		SearchAdapter adapter = getAdapter();
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		retainExtra.dialogsState = adapter.getConfigurationSet().stackInstance.collectState();
	}

	@Override
	public String obtainTitle() {
		return getPage().searchQuery;
	}

	@Override
	public void onItemClick(PostItem postItem) {
		if (postItem != null) {
			Page page = getPage();
			getUiManager().navigator().navigatePosts(page.chanName, page.boardName,
					postItem.getThreadNumber(), postItem.getPostNumber(), null, 0);
		}
	}

	@Override
	public boolean onItemLongClick(PostItem postItem) {
		return postItem != null && getUiManager().interaction()
				.handlePostContextMenu(postItem, null, false, false, false);
	}

	private static final int OPTIONS_MENU_REFRESH = 0;
	private static final int OPTIONS_MENU_GROUP = 1;

	@Override
	public void onCreateOptionsMenu(Menu menu) {
		menu.add(0, OPTIONS_MENU_SEARCH, 0, R.string.action_search)
				.setIcon(getActionBarIcon(R.attr.iconActionSearch))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, OPTIONS_MENU_REFRESH, 0, R.string.action_refresh);
		menu.add(0, OPTIONS_MENU_GROUP, 0, R.string.action_group).setCheckable(true);
		menu.addSubMenu(0, OPTIONS_MENU_APPEARANCE, 0, R.string.action_appearance);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		menu.findItem(OPTIONS_MENU_GROUP).setChecked(getAdapter().isGroupMode());
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case OPTIONS_MENU_REFRESH: {
				refreshSearch(getAdapter().getItemCount() > 0, false);
				return true;
			}
			case OPTIONS_MENU_GROUP: {
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
			case APPEARANCE_MENU_SPOILERS:
			case APPEARANCE_MENU_SFW_MODE: {
				notifyAllAdaptersChanged();
				break;
			}
		}
	}

	@Override
	public boolean onSearchSubmit(String query) {
		Page page = getPage();
		getUiManager().navigator().navigateSearch(page.chanName, page.boardName, query, 0);
		return true;
	}

	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side) {
		refreshSearch(true, side == PullableWrapper.Side.BOTTOM);
	}

	private void refreshSearch(boolean showPull, boolean nextPage) {
		Page page = getPage();
		if (readTask != null) {
			readTask.cancel();
		}
		int pageNumber = 0;
		if (nextPage) {
			RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
			if (!retainExtra.postItems.isEmpty()) {
				pageNumber = retainExtra.pageNumber + 1;
			}
		}
		readTask = new ReadSearchTask(this, page.chanName, page.boardName, page.searchQuery, pageNumber);
		readTask.executeOnExecutor(ReadSearchTask.THREAD_POOL_EXECUTOR);
		if (showPull) {
			getRecyclerView().getWrapper().startBusyState(PullableWrapper.Side.TOP);
			switchView(ViewType.LIST, null);
		} else {
			getRecyclerView().getWrapper().startBusyState(PullableWrapper.Side.BOTH);
			switchView(ViewType.PROGRESS, null);
		}
	}

	@Override
	public void onReadSearchSuccess(ArrayList<PostItem> postItems, int pageNumber) {
		readTask = null;
		PullableRecyclerView recyclerView = getRecyclerView();
		recyclerView.getWrapper().cancelBusyState();
		SearchAdapter adapter = getAdapter();
		boolean showScale = showScaleOnSuccess;
		showScaleOnSuccess = false;
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		if (pageNumber == 0 && (postItems == null || postItems.isEmpty())) {
			switchView(ViewType.ERROR, R.string.message_not_found);
			adapter.setItems(null);
			retainExtra.postItems.clear();
		} else {
			switchView(ViewType.LIST, null);
			if (pageNumber == 0) {
				adapter.setItems(postItems);
				retainExtra.postItems.clear();
				retainExtra.postItems.addAll(postItems);
				retainExtra.pageNumber = 0;
				ListViewUtils.cancelListFling(recyclerView);
				recyclerView.scrollToPosition(0);
				if (showScale) {
					showScaleAnimation();
				}
			} else {
				HashSet<String> existingPostNumbers = new HashSet<>();
				for (PostItem postItem : retainExtra.postItems) {
					existingPostNumbers.add(postItem.getPostNumber());
				}
				if (postItems != null) {
					for (PostItem postItem : postItems) {
						if (!existingPostNumbers.contains(postItem.getPostNumber())) {
							retainExtra.postItems.add(postItem);
						}
					}
				}
				if (retainExtra.postItems.size() > existingPostNumbers.size()) {
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
					adapter.setItems(retainExtra.postItems);
					retainExtra.pageNumber = pageNumber;
					if (!groupMode && needScroll) {
						ListViewUtils.smoothScrollToPosition(recyclerView, oldCount);
					}
				} else {
					ClickableToast.show(getContext(), R.string.message_search_completed);
				}
			}
		}
	}

	@Override
	public void onReadSearchFail(ErrorItem errorItem) {
		readTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		if (getAdapter().getItemCount() == 0) {
			switchView(ViewType.ERROR, errorItem.toString());
		} else {
			ClickableToast.show(getContext(), errorItem.toString());
		}
	}
}
