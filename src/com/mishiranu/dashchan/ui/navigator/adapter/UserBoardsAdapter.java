/*
 * Copyright 2014-2016 Fukurou Mishiranu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.ui.navigator.adapter;

import java.util.ArrayList;
import java.util.Locale;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import chan.content.ChanConfiguration;
import chan.content.model.Board;
import chan.util.StringUtils;

import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ViewFactory;

public class UserBoardsAdapter extends BaseAdapter {
	private final String chanName;

	private final ArrayList<ListItem> listItems = new ArrayList<>();
	private final ArrayList<ListItem> filteredListItems = new ArrayList<>();

	private boolean filterMode = false;
	private String filterText;

	public UserBoardsAdapter(String chanName) {
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
		return !filterMode || filteredListItems.size() > 0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ListItem listItem = getItem(position);
		ViewFactory.TwoLinesViewHolder holder;
		if (convertView == null) {
			convertView = ViewFactory.makeTwoLinesListItem(parent, true);
			float density = ResourceUtils.obtainDensity(parent);
			convertView.setPadding((int) (16f * density), convertView.getPaddingTop(),
					(int) (16f * density), convertView.getPaddingBottom());
			holder = (ViewFactory.TwoLinesViewHolder) convertView.getTag();
			holder.text2.setSingleLine(false);
		} else {
			holder = (ViewFactory.TwoLinesViewHolder) convertView.getTag();
		}
		holder.text1.setText(listItem.title);
		if (!StringUtils.isEmpty(listItem.description)) {
			holder.text2.setVisibility(View.VISIBLE);
			holder.text2.setText(listItem.description);
		} else {
			holder.text2.setVisibility(View.GONE);
		}
		return convertView;
	}

	@Override
	public int getCount() {
		return (filterMode ? filteredListItems : listItems).size();
	}

	@Override
	public ListItem getItem(int position) {
		return (filterMode ? filteredListItems : listItems).get(position);
	}

	@Override
	public long getItemId(int position) {
		return 0;
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

	public static class ListItem {
		public final String boardName, title, description;

		public ListItem(String boardName, String title, String description) {
			this.boardName = boardName;
			this.title = title;
			this.description = description;
		}
	}
}