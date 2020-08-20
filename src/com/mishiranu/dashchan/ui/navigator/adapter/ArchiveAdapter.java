package com.mishiranu.dashchan.ui.navigator.adapter;

import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.model.ThreadSummary;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class ArchiveAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	public interface Callback {
		void onItemClick(String threadNumber);
		boolean onItemLongClick(String threadNumber);
	}

	private final Callback callback;

	private final ArrayList<ThreadSummary> archiveItems = new ArrayList<>();
	private final ArrayList<ThreadSummary> filteredArchiveItems = new ArrayList<>();

	private boolean filterMode = false;
	private String filterText;

	public ArchiveAdapter(Callback callback) {
		this.callback = callback;
	}

	// Returns true, if adapter isn't empty.
	public boolean applyFilter(String text) {
		filterText = text;
		filterMode = !StringUtils.isEmpty(text);
		filteredArchiveItems.clear();
		if (filterMode) {
			text = text.toLowerCase(Locale.getDefault());
			for (ThreadSummary threadSummary : archiveItems) {
				boolean add = false;
				String title = threadSummary.getDescription();
				if (title != null && title.toLowerCase(Locale.getDefault()).contains(text)) {
					add = true;
				}
				if (add) {
					filteredArchiveItems.add(threadSummary);
				}
			}
		}
		notifyDataSetChanged();
		return !filterMode || filteredArchiveItems.size() > 0;
	}

	public boolean isRealEmpty() {
		return archiveItems.size() == 0;
	}

	@Override
	public int getItemCount() {
		return (filterMode ? filteredArchiveItems : archiveItems).size();
	}

	private ThreadSummary getItem(int position) {
		return (filterMode ? filteredArchiveItems : archiveItems).get(position);
	}

	public void setItems(ThreadSummary[] threadSummaries) {
		archiveItems.clear();
		if (threadSummaries != null) {
			Collections.addAll(archiveItems, threadSummaries);
		}
		notifyDataSetChanged();
		if (filterMode) {
			applyFilter(filterText);
		}
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new RecyclerView.ViewHolder(ViewFactory.makeSingleLineListItem(parent)) {{
			itemView.setOnClickListener(v -> callback
					.onItemClick(getItem(getAdapterPosition()).getThreadNumber()));
			itemView.setOnLongClickListener(v -> callback
					.onItemLongClick(getItem(getAdapterPosition()).getThreadNumber()));
		}};
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		ThreadSummary threadSummary = getItem(position);
		((TextView) holder.itemView).setText(threadSummary.getDescription());
	}

	public DividerItemDecoration.Configuration configureDivider(DividerItemDecoration.Configuration configuration,
			@SuppressWarnings("unused") int position) {
		return configuration.need(!C.API_LOLLIPOP);
	}
}
