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

package com.mishiranu.dashchan.ui.adapter;

import java.util.ArrayList;
import java.util.Locale;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import chan.content.ChanConfiguration;
import chan.content.model.Board;
import chan.util.StringUtils;

import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ViewFactory;

public class UserBoardsAdapter extends BaseAdapter
{
	private final Context mContext;
	private final String mChanName;
	private final LayoutInflater mInflater;
	
	private final ArrayList<ListItem> mListItems = new ArrayList<>();
	private final ArrayList<ListItem> mFilteredListItems = new ArrayList<>();

	private boolean mFilterMode = false;
	private String mFilterText;
	
	public UserBoardsAdapter(Context context, String chanName)
	{
		mContext = context;
		mChanName = chanName;
		mInflater = LayoutInflater.from(context);
	}
	
	/*
	 * Returns true, if adapter isn't empty.
	 */
	public boolean applyFilter(String text)
	{
		mFilterText = text;
		mFilterMode = !StringUtils.isEmpty(text);
		mFilteredListItems.clear();
		if (mFilterMode)
		{
			text = text.toLowerCase(Locale.getDefault());
			for (ListItem listItem : mListItems)
			{
				boolean add = false;
				if (listItem.boardName.toLowerCase(Locale.US).contains(text))
				{
					add = true;
				}
				else if (listItem.title != null && listItem.title.toLowerCase(Locale.getDefault()).contains(text))
				{
					add = true;
				}
				else if (listItem.description != null && listItem.description.toLowerCase(Locale.getDefault())
						.contains(text))
				{
					add = true;
				}
				if (add) mFilteredListItems.add(listItem);
			}
		}
		notifyDataSetChanged();
		return !mFilterMode || mFilteredListItems.size() > 0;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		ListItem listItem = getItem(position);
		ViewFactory.TwoLinesViewHolder holder;
		if (convertView == null)
		{
			convertView = ViewFactory.makeTwoLinesListItem(mInflater, parent, true);
			float density = ResourceUtils.obtainDensity(mContext);
			convertView.setPadding((int) (16f * density), convertView.getPaddingTop(),
					(int) (16f * density), convertView.getPaddingBottom());
			holder = (ViewFactory.TwoLinesViewHolder) convertView.getTag();
			holder.text2.setSingleLine(false);
		}
		else holder = (ViewFactory.TwoLinesViewHolder) convertView.getTag();
		holder.text1.setText(listItem.title);
		if (!StringUtils.isEmpty(listItem.description))
		{
			holder.text2.setVisibility(View.VISIBLE);
			holder.text2.setText(listItem.description);
		}
		else holder.text2.setVisibility(View.GONE);
		return convertView;
	}
	
	@Override
	public int getCount()
	{
		return (mFilterMode ? mFilteredListItems : mListItems).size();
	}
	
	@Override
	public ListItem getItem(int position)
	{
		return (mFilterMode ? mFilteredListItems : mListItems).get(position);
	}
	
	@Override
	public long getItemId(int position)
	{
		return 0;
	}
	
	public void setItems(Board[] boards)
	{
		mListItems.clear();
		ChanConfiguration configuration = ChanConfiguration.get(mChanName);
		if (boards != null)
		{
			for (Board board : boards)
			{
				String boardName = board.getBoardName();
				String title = configuration.getBoardTitle(boardName);
				String description = configuration.getBoardDescription(boardName);
				mListItems.add(new ListItem(boardName, StringUtils.formatBoardTitle(mChanName, boardName, title),
						description));
			}
		}
		notifyDataSetChanged();
		if (mFilterMode) applyFilter(mFilterText);
	}
	
	public static class ListItem
	{
		public final String boardName, title, description;
		
		public ListItem(String boardName, String title, String description)
		{
			this.boardName = boardName;
			this.title = title;
			this.description = description;
		}
	}
}