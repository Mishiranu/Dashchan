package com.mishiranu.dashchan.ui.navigator.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.ui.navigator.manager.HidePerformer;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.SimpleViewHolder;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

public class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	public interface Callback extends ListViewUtils.SimpleCallback<PostItem> {}

	private enum ViewType {VIEW, HEADER}

	private static class ListItem {
		public final PostItem postItem;
		public final String title;

		private ListItem(PostItem postItem, String title) {
			this.postItem = postItem;
			this.title = title;
		}
	}

	private final Context context;
	private final Callback callback;
	private final UiManager uiManager;
	private final UiManager.DemandSet demandSet = new UiManager.DemandSet();
	private final UiManager.ConfigurationSet configurationSet;

	private final ArrayList<ListItem> postItems = new ArrayList<>();
	private final ArrayList<ListItem> groupItems = new ArrayList<>();

	private boolean groupMode = false;

	public SearchAdapter(Context context, Callback callback, UiManager uiManager, String searchQuery) {
		this.context = context;
		this.callback = callback;
		this.uiManager = uiManager;
		configurationSet = new UiManager.ConfigurationSet(null, null, new HidePerformer(context),
				new GalleryItem.GallerySet(false), uiManager.dialog().createStackInstance(), null, null,
				true, false, false, false, false, null);
		demandSet.highlightText = Collections.singleton(searchQuery);
	}

	@Override
	public int getItemCount() {
		return groupMode ? groupItems.size() : postItems.size();
	}

	@Override
	public int getItemViewType(int position) {
		ListItem listItem = getItem(position);
		return (listItem.postItem != null ? ViewType.VIEW : ViewType.HEADER).ordinal();
	}

	private ListItem getItem(int position) {
		return groupMode ? groupItems.get(position) : postItems.get(position);
	}

	private PostItem getPostItem(int position) {
		return getItem(position).postItem;
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		switch (ViewType.values()[viewType]) {
			case VIEW: {
				RecyclerView.ViewHolder holder = uiManager.view().createPostView(parent, configurationSet);
				ListViewUtils.bind(holder, true, this::getPostItem, callback);
				return holder;
			}
			case HEADER: {
				View view = ViewFactory.makeListTextHeader(parent);
				float density = ResourceUtils.obtainDensity(parent);
				ViewUtils.setNewPadding(view, (int) (12f * density), null, (int) (12f * density), null);
				return new SimpleViewHolder(view);
			}
			default: {
				throw new IllegalStateException();
			}
		}
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		switch (ViewType.values()[holder.getItemViewType()]) {
			case VIEW: {
				uiManager.view().bindPostView(holder, getItem(position).postItem, demandSet);
				break;
			}
			case HEADER: {
				((TextView) holder.itemView).setText(getItem(position).title);
				break;
			}
		}
	}

	public UiManager.ConfigurationSet getConfigurationSet() {
		return configurationSet;
	}

	public void setItems(ArrayList<PostItem> postItems) {
		this.postItems.clear();
		if (postItems != null) {
			for (PostItem postItem : postItems) {
				this.postItems.add(new ListItem(postItem, null));
			}
		}
		handleItems();
	}

	public void setGroupMode(boolean groupMode) {
		if (this.groupMode != groupMode) {
			this.groupMode = groupMode;
			handleItems();
		}
	}

	public boolean isGroupMode() {
		return groupMode;
	}

	private void handleItems() {
		groupItems.clear();
		configurationSet.gallerySet.clear();
		if (postItems.size() > 0) {
			if (groupMode) {
				LinkedHashMap<String, ArrayList<ListItem>> map = new LinkedHashMap<>();
				for (ListItem listItem : postItems) {
					String threadNumber = listItem.postItem.getThreadNumber();
					ArrayList<ListItem> postItems = map.get(threadNumber);
					if (postItems == null) {
						postItems = new ArrayList<>();
						map.put(threadNumber, postItems);
					}
					postItems.add(listItem);
				}
				for (LinkedHashMap.Entry<String, ArrayList<ListItem>> entry : map.entrySet()) {
					String threadNumber = entry.getKey();
					boolean number;
					try {
						Integer.parseInt(threadNumber);
						number = true;
					} catch (NumberFormatException e) {
						number = false;
					}
					groupItems.add(new ListItem(null, context.getString(R.string.in_thread_number__format,
							number ? "#" + threadNumber : threadNumber)));
					int ordinalIndex = 0;
					for (ListItem listItem : entry.getValue()) {
						groupItems.add(listItem);
						listItem.postItem.setOrdinalIndex(ordinalIndex++);
					}
				}
			} else {
				for (int i = 0; i < postItems.size(); i++) {
					postItems.get(i).postItem.setOrdinalIndex(i);
				}
			}
		}
		for (int i = 0, count = getItemCount(); i < count; i++) {
			ListItem listItem = getItem(i);
			if (listItem.postItem != null) {
				configurationSet.gallerySet.add(listItem.postItem.getAttachmentItems());
			}
		}
		notifyDataSetChanged();
	}

	public DividerItemDecoration.Configuration configureDivider
			(DividerItemDecoration.Configuration configuration, int position) {
		ListItem current = getItem(position);
		ListItem next = position + 1 < getItemCount() ? getItem(position + 1) : null;
		if (C.API_LOLLIPOP) {
			return configuration.need(current.postItem != null || next == null || next.postItem == null);
		} else {
			return configuration.need(current.postItem != null && (next == null || next.postItem != null));
		}
	}
}
