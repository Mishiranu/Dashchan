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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import chan.content.ChanConfiguration;
import chan.util.CommonUtils;
import chan.util.StringUtils;

import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ViewFactory;

public class BoardsAdapter extends BaseAdapter
{
	public static final String KEY_TITLE = "title";
	public static final String KEY_BOARDS = "boards";

	private static final int TYPE_VIEW = 0;
	private static final int TYPE_HEADER = 1;

	private final String mChanName;

	private final ArrayList<ListItem> mListItems = new ArrayList<>();
	private final ArrayList<ListItem> mFilteredListItems = new ArrayList<>();

	private boolean mFilterMode = false;
	private String mFilterText;

	public BoardsAdapter(String chanName)
	{
		mChanName = chanName;
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
				if (listItem.boardName != null)
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
					if (add) mFilteredListItems.add(listItem);
				}
			}
		}
		notifyDataSetChanged();
		return !mFilterMode || mFilteredListItems.size() > 0;
	}

	public void update()
	{
		mListItems.clear();
		ChanConfiguration configuration = ChanConfiguration.get(mChanName);
		JSONArray jsonArray = configuration.getBoards();
		if (jsonArray != null)
		{
			try
			{
				for (int i = 0, length = jsonArray.length(); i < length; i++)
				{
					JSONObject jsonObject = jsonArray.getJSONObject(i);
					String title = CommonUtils.getJsonString(jsonObject, KEY_TITLE);
					if (length > 1) mListItems.add(new ListItem(null, title));
					JSONArray boardsArray = jsonObject.getJSONArray(KEY_BOARDS);
					for (int j = 0; j < boardsArray.length(); j++)
					{
						String boardName = boardsArray.isNull(j) ? null : boardsArray.getString(j);
						if (!StringUtils.isEmpty(boardName))
						{
							title = configuration.getBoardTitle(boardName);
							mListItems.add(new ListItem(boardName, StringUtils.formatBoardTitle(mChanName,
									boardName, title)));
						}
					}
				}
			}
			catch (JSONException e)
			{

			}
		}
		notifyDataSetChanged();
		if (mFilterMode) applyFilter(mFilterText);
	}

	@Override
	public int getViewTypeCount()
	{
		return 2;
	}

	@Override
	public int getItemViewType(int position)
	{
		return getItem(position).boardName == null ? TYPE_HEADER : TYPE_VIEW;
	}

	@Override
	public boolean isEnabled(int position)
	{
		return getItem(position).boardName != null;
	}

	@Override
	public boolean areAllItemsEnabled()
	{
		return false;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		ListItem listItem = getItem(position);
		if (convertView == null)
		{
			if (listItem.boardName != null)
			{
				float density = ResourceUtils.obtainDensity(parent);
				TextView textView = (TextView) LayoutInflater.from(parent.getContext())
						.inflate(android.R.layout.simple_list_item_1, parent, false);
				textView.setPadding((int) (16f * density), 0, (int) (16f * density), 0);
				textView.setEllipsize(TextUtils.TruncateAt.END);
				textView.setSingleLine(true);
				convertView = textView;
			}
			else convertView = ViewFactory.makeListTextHeader(parent, false);
		}
		((TextView) convertView).setText(listItem.title);
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

	public static class ListItem
	{
		public final String boardName, title;

		public ListItem(String boardName, String title)
		{
			this.boardName = boardName;
			this.title = title;
		}
	}
}