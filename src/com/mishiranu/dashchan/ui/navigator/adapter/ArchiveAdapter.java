package com.mishiranu.dashchan.ui.navigator.adapter;

import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.model.ThreadSummary;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.SimpleViewHolder;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ArchiveAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	public interface Callback extends ListViewUtils.SimpleCallback<String> {}

	private final Callback callback;

	private final ArrayList<ThreadSummary> archiveItems = new ArrayList<>();
	private final ArrayList<ThreadSummary> filteredArchiveItems = new ArrayList<>();

	private String filterText;

	public ArchiveAdapter(Callback callback) {
		this.callback = callback;
	}

	public void applyFilter(String text) {
		if (!StringUtils.emptyIfNull(filterText).equals(StringUtils.emptyIfNull(text))) {
			filterText = StringUtils.nullIfEmpty(text);
			applyCurrentFilter();
			notifyDataSetChanged();
		}
	}

	private void applyCurrentFilter() {
		String text = filterText;
		filteredArchiveItems.clear();
		if (!StringUtils.isEmpty(text)) {
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
	}

	public boolean isRealEmpty() {
		return archiveItems.size() == 0;
	}

	@Override
	public int getItemCount() {
		return (filterText != null ? filteredArchiveItems : archiveItems).size();
	}

	private ThreadSummary getItem(int position) {
		return (filterText != null ? filteredArchiveItems : archiveItems).get(position);
	}

	private String getThreadNumber(int position) {
		return getItem(position).getThreadNumber();
	}

	public void setItems(List<ThreadSummary> threadSummaries) {
		archiveItems.clear();
		if (threadSummaries != null) {
			archiveItems.addAll(threadSummaries);
		}
		applyCurrentFilter();
		notifyDataSetChanged();
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return ListViewUtils.bind(new SimpleViewHolder(ViewFactory.makeSingleLineListItem(parent)),
				true, this::getThreadNumber, callback);
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
