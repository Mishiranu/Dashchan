package com.mishiranu.dashchan.ui.navigator.adapter;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.ChanConfiguration;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.storage.HistoryDatabase;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.SimpleViewHolder;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	public interface Callback {
		void onItemClick(HistoryDatabase.HistoryItem historyItem);
		boolean onItemLongClick(HistoryDatabase.HistoryItem historyItem);
	}

	private enum ViewType {VIEW, HEADER}

	private enum Header {
		TODAY(R.string.today),
		YESTERDAY(R.string.yesterday),
		WEEK(R.string.this_week),
		OLD(R.string.older_than_seven_days);

		public final int titleResId;

		Header(int titleResId) {
			this.titleResId = titleResId;
		}
	}

	private static class ListItem {
		public final HistoryDatabase.HistoryItem historyItem;
		public final String headerTitle;

		private ListItem(String headerTitle, HistoryDatabase.HistoryItem historyItem) {
			this.headerTitle = headerTitle;
			this.historyItem = historyItem;
		}
	}

	private final Callback callback;

	private final ArrayList<ListItem> listItems = new ArrayList<>();
	private final ArrayList<ListItem> filteredListItems = new ArrayList<>();

	private String chanName;
	private String filterText;

	public HistoryAdapter(Callback callback) {
		this.callback = callback;
	}

	public void updateConfiguration(Context context, String chanName) {
		this.chanName = chanName;
		listItems.clear();
		ArrayList<HistoryDatabase.HistoryItem> historyItems = HistoryDatabase.getInstance().getAllHistory(chanName);
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		long thisDay = calendar.getTimeInMillis();
		long yesterday = thisDay - 24 * 60 * 60 * 1000;
		long thisWeek = thisDay - 7 * 24 * 60 * 60 * 1000;
		Header header = null;
		for (HistoryDatabase.HistoryItem historyItem : historyItems) {
			Header targetHeader = Header.TODAY;
			if (historyItem.time < thisDay) {
				if (historyItem.time < yesterday) {
					if (historyItem.time < thisWeek) {
						targetHeader = Header.OLD;
					} else {
						targetHeader = Header.WEEK;
					}
				} else {
					targetHeader = Header.YESTERDAY;
				}
			}
			if (header == null || targetHeader.compareTo(header) > 0) {
				header = targetHeader;
				listItems.add(new ListItem(context.getString(header.titleResId), null));
			}
			listItems.add(new ListItem(null, historyItem));
		}
		applyCurrentFilter();
		notifyDataSetChanged();
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
		filteredListItems.clear();
		if (!StringUtils.isEmpty(text)) {
			text = text.toLowerCase(Locale.getDefault());
			for (ListItem listItem : listItems) {
				if (listItem.historyItem != null) {
					if (listItem.historyItem.title != null &&
							listItem.historyItem.title.toLowerCase(Locale.getDefault()).contains(text)) {
						filteredListItems.add(listItem);
					}
				}
			}
		}
	}

	public void remove(HistoryDatabase.HistoryItem historyItem) {
		int index = -1;
		for (int i = 0; i < listItems.size(); i++) {
			ListItem listItem = listItems.get(i);
			if (listItem.historyItem == historyItem) {
				index = i;
				break;
			}
		}
		if (index >= 0) {
			listItems.remove(index);
			if (index > 0 && (index == listItems.size() ||
					getItemViewType(index) == ViewType.HEADER.ordinal()) &&
					getItemViewType(index - 1) == ViewType.HEADER.ordinal()) {
				listItems.remove(index - 1);
			}
			applyCurrentFilter();
			notifyDataSetChanged();
		}
	}

	public void clear() {
		listItems.clear();
		filteredListItems.clear();
		notifyDataSetChanged();
	}

	public boolean isRealEmpty() {
		return listItems.size() == 0;
	}

	@Override
	public int getItemCount() {
		return (filterText != null ? filteredListItems : listItems).size();
	}

	@Override
	public int getItemViewType(int position) {
		ListItem listItem = getItem(position);
		if (listItem.headerTitle != null) {
			return ViewType.HEADER.ordinal();
		} else if (listItem.historyItem != null) {
			return ViewType.VIEW.ordinal();
		} else {
			throw new IllegalStateException();
		}
	}

	private ListItem getItem(int position) {
		return (filterText != null ? filteredListItems : listItems).get(position);
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		switch (ViewType.values()[viewType]) {
			case VIEW: {
				return new RecyclerView.ViewHolder(ViewFactory.makeTwoLinesListItem(parent)) {{
					((ViewFactory.TwoLinesViewHolder) itemView.getTag()).text2.setSingleLine(true);
					itemView.setOnClickListener(v -> callback
							.onItemClick(getItem(getAdapterPosition()).historyItem));
					itemView.setOnLongClickListener(v -> callback
							.onItemLongClick(getItem(getAdapterPosition()).historyItem));
				}};
			}
			case HEADER: {
				return new SimpleViewHolder(ViewFactory.makeListTextHeader(parent));
			}
			default: {
				throw new IllegalStateException();
			}
		}
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		ListItem listItem = getItem(position);
		switch (ViewType.values()[holder.getItemViewType()]) {
			case VIEW: {
				ViewFactory.TwoLinesViewHolder viewHolder = (ViewFactory.TwoLinesViewHolder) holder.itemView.getTag();
				viewHolder.text1.setText(StringUtils.isEmpty(listItem.historyItem.title)
						? StringUtils.formatThreadTitle(listItem.historyItem.chanName, listItem.historyItem.boardName,
						listItem.historyItem.threadNumber) : listItem.historyItem.title);
				ChanConfiguration configuration = ChanConfiguration.get(listItem.historyItem.chanName);
				String title = configuration.getBoardTitle(listItem.historyItem.boardName);
				title = StringUtils.isEmpty(listItem.historyItem.boardName) ? title
						: StringUtils.formatBoardTitle(listItem.historyItem.chanName,
						listItem.historyItem.boardName, title);
				if (chanName == null) {
					title = configuration.getTitle() + " — " + title;
				}
				viewHolder.text2.setText(title);
				break;
			}
			case HEADER: {
				((TextView) holder.itemView).setText(listItem.headerTitle);
				break;
			}
		}
	}

	public DividerItemDecoration.Configuration configureDivider
			(DividerItemDecoration.Configuration configuration, int position) {
		ListItem current = getItem(position);
		ListItem next = position + 1 < getItemCount() ? getItem(position + 1) : null;
		if (C.API_LOLLIPOP) {
			return configuration.need(current.historyItem != null || next == null || next.headerTitle != null);
		} else {
			return configuration.need(current.historyItem != null && (next == null || next.historyItem != null));
		}
	}
}
