package com.mishiranu.dashchan.ui.navigator.adapter;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.ChanConfiguration;
import chan.content.model.Board;
import chan.util.StringUtils;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.util.ArrayList;
import java.util.Locale;

public class UserBoardsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	public interface Callback {
		void onItemClick(String boardName);
		boolean onItemLongClick(String boardName);
	}

	private final Callback callback;
	private final String chanName;

	private final ArrayList<ListItem> listItems = new ArrayList<>();
	private final ArrayList<ListItem> filteredListItems = new ArrayList<>();

	private boolean filterMode = false;
	private String filterText;

	public UserBoardsAdapter(Callback callback, String chanName) {
		this.callback = callback;
		this.chanName = chanName;
	}

	public void applyFilter(String text) {
		filterText = text;
		filterMode = !StringUtils.isEmpty(text);
		filteredListItems.clear();
		if (filterMode) {
			text = text.toLowerCase(Locale.getDefault());
			for (ListItem listItem : listItems) {
				boolean add = false;
				if (listItem.boardName.toLowerCase(Locale.US).contains(text)) {
					add = true;
				} else if (listItem.title != null && listItem.title.toLowerCase(Locale.getDefault()).contains(text)) {
					add = true;
				} else if (listItem.description != null && listItem.description.toLowerCase(Locale.getDefault())
						.contains(text)) {
					add = true;
				}
				if (add){
					filteredListItems.add(listItem);
				}
			}
		}
		notifyDataSetChanged();
	}

	public boolean isRealEmpty() {
		return listItems.size() == 0;
	}

	@Override
	public int getItemCount() {
		return (filterMode ? filteredListItems : listItems).size();
	}

	private ListItem getItem(int position) {
		return (filterMode ? filteredListItems : listItems).get(position);
	}

	public void setItems(Board[] boards) {
		listItems.clear();
		ChanConfiguration configuration = ChanConfiguration.get(chanName);
		if (boards != null) {
			for (Board board : boards) {
				String boardName = board.getBoardName();
				String title = configuration.getBoardTitle(boardName);
				String description = configuration.getBoardDescription(boardName);
				listItems.add(new ListItem(boardName, StringUtils.formatBoardTitle(chanName, boardName, title),
						description));
			}
		}
		notifyDataSetChanged();
		if (filterMode) {
			applyFilter(filterText);
		}
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new RecyclerView.ViewHolder(ViewFactory.makeTwoLinesListItem(parent)) {{
			itemView.setOnClickListener(v -> callback
					.onItemClick(getItem(getAdapterPosition()).boardName));
			itemView.setOnLongClickListener(v -> callback
					.onItemLongClick(getItem(getAdapterPosition()).boardName));
		}};
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		ListItem listItem = getItem(position);
		ViewFactory.TwoLinesViewHolder viewHolder = (ViewFactory.TwoLinesViewHolder) holder.itemView.getTag();
		viewHolder.text1.setText(listItem.title);
		if (!StringUtils.isEmpty(listItem.description)) {
			viewHolder.text2.setVisibility(View.VISIBLE);
			viewHolder.text2.setText(listItem.description);
		} else {
			viewHolder.text2.setVisibility(View.GONE);
		}
	}

	public static class ListItem {
		public final String boardName, title, description;

		public ListItem(String boardName, String title, String description) {
			this.boardName = boardName;
			this.title = title;
			this.description = description;
		}
	}
}
