package com.mishiranu.dashchan.ui.navigator.adapter;

import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.ChanConfiguration;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.SimpleViewHolder;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BoardsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	public interface Callback {
		void onItemClick(String boardName);
		boolean onItemLongClick(String boardName);
	}

	private enum ViewType {VIEW, HEADER}

	private static class ListItem {
		public final String boardName, title;

		public ListItem(String boardName, String title) {
			this.boardName = boardName;
			this.title = title;
		}
	}

	public static final String KEY_TITLE = "title";
	public static final String KEY_BOARDS = "boards";

	private final Callback callback;
	private final String chanName;

	private final ArrayList<ListItem> listItems = new ArrayList<>();
	private final ArrayList<ListItem> filteredListItems = new ArrayList<>();

	private boolean filterMode = false;
	private String filterText;

	public BoardsAdapter(Callback callback, String chanName) {
		this.callback = callback;
		this.chanName = chanName;
	}

	// Returns true, if adapter isn't empty.
	public boolean applyFilter(String text) {
		filterText = text;
		filterMode = !StringUtils.isEmpty(text);
		filteredListItems.clear();
		if (filterMode) {
			text = text.toLowerCase(Locale.getDefault());
			for (ListItem listItem : listItems) {
				if (listItem.boardName != null) {
					boolean add = false;
					if (listItem.boardName.toLowerCase(Locale.US).contains(text)) {
						add = true;
					} else if (listItem.title != null && listItem.title.toLowerCase(Locale.getDefault())
							.contains(text)) {
						add = true;
					}
					if (add) {
						filteredListItems.add(listItem);
					}
				}
			}
		}
		notifyDataSetChanged();
		return !filterMode || filteredListItems.size() > 0;
	}

	public void update() {
		listItems.clear();
		ChanConfiguration configuration = ChanConfiguration.get(chanName);
		JSONArray jsonArray = configuration.getBoards();
		if (jsonArray != null) {
			try {
				for (int i = 0, length = jsonArray.length(); i < length; i++) {
					JSONObject jsonObject = jsonArray.getJSONObject(i);
					String title = CommonUtils.getJsonString(jsonObject, KEY_TITLE);
					if (length > 1) {
						listItems.add(new ListItem(null, title));
					}
					JSONArray boardsArray = jsonObject.getJSONArray(KEY_BOARDS);
					for (int j = 0; j < boardsArray.length(); j++) {
						String boardName = boardsArray.isNull(j) ? null : boardsArray.getString(j);
						if (!StringUtils.isEmpty(boardName)) {
							title = configuration.getBoardTitle(boardName);
							listItems.add(new ListItem(boardName, StringUtils.formatBoardTitle(chanName,
									boardName, title)));
						}
					}
				}
			} catch (JSONException e) {
				// Invalid data, ignore exception
			}
		}
		notifyDataSetChanged();
		if (filterMode) {
			applyFilter(filterText);
		}
	}

	public boolean isRealEmpty() {
		return listItems.size() == 0;
	}

	@Override
	public int getItemCount() {
		return (filterMode ? filteredListItems : listItems).size();
	}

	@Override
	public int getItemViewType(int position) {
		return (getItem(position).boardName == null ? ViewType.HEADER : ViewType.VIEW).ordinal();
	}

	private ListItem getItem(int position) {
		return (filterMode ? filteredListItems : listItems).get(position);
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		switch (ViewType.values()[viewType]) {
			case VIEW: {
				return new RecyclerView.ViewHolder(ViewFactory.makeSingleLineListItem(parent)) {{
					itemView.setOnClickListener(v -> callback
							.onItemClick(getItem(getAdapterPosition()).boardName));
					itemView.setOnLongClickListener(v -> callback
							.onItemLongClick(getItem(getAdapterPosition()).boardName));
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
		((TextView) holder.itemView).setText(listItem.title);
	}

	public DividerItemDecoration.Configuration configureDivider
			(DividerItemDecoration.Configuration configuration, int position) {
		ListItem current = getItem(position);
		ListItem next = position + 1 < getItemCount() ? getItem(position + 1) : null;
		if (C.API_LOLLIPOP) {
			return configuration.need(next != null && next.boardName == null);
		} else {
			return configuration.need(current.boardName != null && (next == null || next.boardName != null));
		}
	}
}
