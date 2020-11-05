package com.mishiranu.dashchan.ui.navigator.adapter;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;
import chan.util.CommonUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.ui.navigator.manager.ViewUnit;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	public interface Callback extends ListViewUtils.SimpleCallback<PostItem> {}

	private static class ListItem {
		public final PostItem postItem;
		public final String group;

		private ListItem(PostItem postItem, String group) {
			this.postItem = postItem;
			this.group = group;
		}
	}

	private final Context context;
	private final UiManager uiManager;
	private final UiManager.ConfigurationSet configurationSet;
	private final UiManager.DemandSet demandSet = new UiManager.DemandSet();
	private final GalleryItem.Set gallerySet = new GalleryItem.Set(false);

	private final ArrayList<PostItem> postItems = new ArrayList<>();
	private final ArrayList<ListItem> groupItems = new ArrayList<>();

	private boolean groupMode = false;

	public SearchAdapter(Context context, Callback callback, String chanName,
			UiManager uiManager, FragmentManager fragmentManager, String searchQuery) {
		this.context = context;
		this.uiManager = uiManager;
		configurationSet = new UiManager.ConfigurationSet(chanName, null, null, UiManager.PostStateProvider.DEFAULT,
				gallerySet, fragmentManager, uiManager.dialog().createStackInstance(), null, callback,
				true, false, false, false, false, null);
		demandSet.highlightText = Collections.singleton(searchQuery);
	}

	@Override
	public int getItemCount() {
		return groupMode ? groupItems.size() : postItems.size();
	}

	@Override
	public int getItemViewType(int position) {
		return ViewUnit.ViewType.POST.ordinal();
	}

	private PostItem getItem(int position) {
		return groupMode ? groupItems.get(position).postItem : postItems.get(position);
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return uiManager.view().createView(parent, ViewUnit.ViewType.POST);
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		onBindViewHolder(holder, position, Collections.emptyList());
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder,
			int position, @NonNull List<Object> payloads) {
		if (payloads.isEmpty()) {
			uiManager.view().bindPostView(holder, getItem(position), configurationSet, demandSet);
		} else {
			for (Object object : payloads) {
				if (object instanceof AttachmentItem) {
					uiManager.view().bindPostViewReloadAttachment(holder, (AttachmentItem) object);
				}
			}
		}
	}

	public UiManager.ConfigurationSet getConfigurationSet() {
		return configurationSet;
	}

	public void setItems(List<PostItem> postItems) {
		this.postItems.clear();
		if (postItems != null) {
			this.postItems.addAll(postItems);
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
		gallerySet.clear();
		if (postItems.size() > 0) {
			if (groupMode) {
				LinkedHashMap<String, ArrayList<PostItem>> map = new LinkedHashMap<>();
				for (PostItem postItem : postItems) {
					String threadNumber = postItem.getThreadNumber();
					ArrayList<PostItem> postItems = map.get(threadNumber);
					if (postItems == null) {
						postItems = new ArrayList<>();
						map.put(threadNumber, postItems);
					}
					postItems.add(postItem);
				}
				for (LinkedHashMap.Entry<String, ArrayList<PostItem>> entry : map.entrySet()) {
					String threadNumber = entry.getKey();
					boolean number;
					try {
						Integer.parseInt(threadNumber);
						number = true;
					} catch (NumberFormatException e) {
						number = false;
					}
					String group = context.getString(R.string.in_thread_number__format,
							number ? "#" + threadNumber : threadNumber);
					int ordinalIndex = 0;
					for (PostItem postItem : entry.getValue()) {
						groupItems.add(new ListItem(postItem, group));
						postItem.setOrdinalIndex(ordinalIndex++);
					}
				}
			} else {
				for (int i = 0; i < postItems.size(); i++) {
					postItems.get(i).setOrdinalIndex(i);
				}
			}
		}
		for (int i = 0, count = getItemCount(); i < count; i++) {
			PostItem postItem = getItem(i);
			gallerySet.put(postItem.getPostNumber(), postItem.getAttachmentItems());
		}
		notifyDataSetChanged();
	}

	public void reloadAttachment(AttachmentItem attachmentItem) {
		for (int i = 0; i < getItemCount(); i++) {
			PostItem postItem = getItem(i);
			if (postItem.getPostNumber().equals(attachmentItem.getPostNumber())) {
				notifyItemChanged(i, attachmentItem);
				break;
			}
		}
	}

	public DividerItemDecoration.Configuration configureDivider
			(DividerItemDecoration.Configuration configuration, int position) {
		if (C.API_LOLLIPOP) {
			return configuration.need(true);
		} else {
			String header = position + 1 < getItemCount() ? getItemHeader(position + 1) : null;
			return configuration.need(header == null);
		}
	}

	public void configureItemHeader(Context context, TextView headerView) {
		float density = ResourceUtils.obtainDensity(context);
		ViewUtils.setNewPadding(headerView, (int) (12f * density), null, (int) (12f * density), null);
	}

	public String getItemHeader(int position) {
		if (groupMode) {
			if (position == 0) {
				return groupItems.get(0).group;
			} else {
				String previous = groupItems.get(position - 1).group;
				String current = groupItems.get(position).group;
				return CommonUtils.equals(previous, current) ? null : current;
			}
		} else {
			return null;
		}
	}
}
