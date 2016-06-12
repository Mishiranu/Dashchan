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
import java.util.Collections;
import java.util.Locale;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import chan.content.model.ThreadSummary;
import chan.util.StringUtils;

import com.mishiranu.dashchan.util.ResourceUtils;

public class ArchiveAdapter extends BaseAdapter
{
	private final Context mContext;
	private final LayoutInflater mInflater;
	
	private final ArrayList<ThreadSummary> mArchiveItems = new ArrayList<>();
	private final ArrayList<ThreadSummary> mFilteredArchiveItems = new ArrayList<>();
	
	private boolean mFilterMode = false;
	private String mFilterText;
	
	public ArchiveAdapter(Context context)
	{
		mContext = context;
		mInflater = LayoutInflater.from(context);
	}
	
	/*
	 * Returns true, if adapter isn't empty.
	 */
	public boolean applyFilter(String text)
	{
		mFilterText = text;
		mFilterMode = !StringUtils.isEmpty(text);
		mFilteredArchiveItems.clear();
		if (mFilterMode)
		{
			text = text.toLowerCase(Locale.getDefault());
			for (ThreadSummary threadSummary : mArchiveItems)
			{
				boolean add = false;
				String title = threadSummary.getDescription();
				if (title != null && title.toLowerCase(Locale.getDefault()).contains(text))
				{
					add = true;
				}
				if (add) mFilteredArchiveItems.add(threadSummary);
			}
		}
		notifyDataSetChanged();
		return !mFilterMode || mFilteredArchiveItems.size() > 0;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		ThreadSummary threadSummary = getItem(position);
		if (convertView == null)
		{
			float density = ResourceUtils.obtainDensity(mContext);
			TextView textView = (TextView) mInflater.inflate(android.R.layout.simple_list_item_1, parent, false);
			textView.setPadding((int) (16f * density), 0, (int) (16f * density), 0);
			textView.setEllipsize(TextUtils.TruncateAt.END);
			textView.setSingleLine(true);
			convertView = textView;
		}
		((TextView) convertView).setText(threadSummary.getDescription());
		return convertView;
	}
	
	@Override
	public int getCount()
	{
		return (mFilterMode ? mFilteredArchiveItems : mArchiveItems).size();
	}
	
	@Override
	public ThreadSummary getItem(int position)
	{
		return (mFilterMode ? mFilteredArchiveItems : mArchiveItems).get(position);
	}
	
	@Override
	public long getItemId(int position)
	{
		return 0;
	}
	
	public void setItems(ThreadSummary[] threadSummaries)
	{
		mArchiveItems.clear();
		if (threadSummaries != null) Collections.addAll(mArchiveItems, threadSummaries);
		notifyDataSetChanged();
		if (mFilterMode) applyFilter(mFilterText);
	}
}