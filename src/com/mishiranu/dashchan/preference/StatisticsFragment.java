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

package com.mishiranu.dashchan.preference;

import java.util.ArrayList;
import java.util.HashMap;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.StatisticsManager;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.widget.ViewFactory;

public class StatisticsFragment extends BaseListFragment
{
	private static class ListItem
	{
		public final String title;
		public final int views;
		public final int posts;
		public final int threads;
		
		public ListItem(String title, int views, int posts, int threads)
		{
			this.title = title;
			this.views = views;
			this.posts = posts;
			this.threads = threads;
		}
	}
	
	private final ArrayList<ListItem> mListItems = new ArrayList<>();
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		setHasOptionsMenu(true);
		getActivity().setTitle(R.string.preference_statistics);
		HashMap<String, StatisticsManager.StatisticsItem> statisticsItems = StatisticsManager.getInstance().getItems();
		int totalThreadsViewed = 0;
		int totalPostsSent = 0;
		int totalThreadsCreated = 0;
		for (StatisticsManager.StatisticsItem statisticsItem : statisticsItems.values())
		{
			if (statisticsItem.threadsViewed >= 0) totalThreadsViewed += statisticsItem.threadsViewed;
			if (statisticsItem.postsSent >= 0) totalPostsSent += statisticsItem.postsSent;
			if (statisticsItem.threadsCreated >= 0) totalThreadsCreated += statisticsItem.threadsCreated;
		}
		mListItems.add(new ListItem(getString(R.string.text_general), totalThreadsViewed, totalPostsSent,
				totalThreadsCreated));
		for (String chanName : ChanManager.getInstance().getAvailableChanNames())
		{
			StatisticsManager.StatisticsItem statisticsItem = statisticsItems.get(chanName);
			if (statisticsItem != null)
			{
				int threadsViewed = statisticsItem.threadsViewed;
				int postsSent = statisticsItem.postsSent;
				int threadsCreated = statisticsItem.threadsCreated;
				String title = ChanConfiguration.get(chanName).getTitle();
				if (StringUtils.isEmpty(title)) title = chanName;
				if (threadsViewed >= 0 || postsSent >= 0 || threadsCreated >= 0)
				{
					mListItems.add(new ListItem(title, threadsViewed, postsSent, threadsCreated));
				}
			}
		}
		setListAdapter(new BaseAdapter()
		{
			@Override
			public View getView(int position, View convertView, ViewGroup parent)
			{
				ViewFactory.TwoLinesViewHolder holder;
				if (convertView == null)
				{
					convertView = ViewFactory.makeTwoLinesListItem(parent, false);
					holder = (ViewFactory.TwoLinesViewHolder) convertView.getTag();
				}
				else holder = (ViewFactory.TwoLinesViewHolder) convertView.getTag();
				ListItem listItem = getItem(position);
				holder.text1.setText(listItem.title);
				SpannableStringBuilder spannable = new SpannableStringBuilder();
				appendSpannedLine(spannable, R.string.text_threads_viewed, listItem.views);
				appendSpannedLine(spannable, R.string.text_posts_sent, listItem.posts);
				appendSpannedLine(spannable, R.string.text_threads_created, listItem.threads);
				holder.text2.setText(spannable);
				return convertView;
			}
			
			private void appendSpannedLine(SpannableStringBuilder spannable, int resId, int value)
			{
				if (value >= 0)
				{
					if (spannable.length() > 0) spannable.append('\n');
					spannable.append(getString(resId)).append(": ");
					StringUtils.appendSpan(spannable, Integer.toString(value), getBoldSpan());
				}
			}
			
			private Object getBoldSpan()
			{
				return C.API_LOLLIPOP ? new TypefaceSpan("sans-serif-medium") : new StyleSpan(Typeface.BOLD);
			}
			
			@Override
			public long getItemId(int position)
			{
				return 0;
			}
			
			@Override
			public ListItem getItem(int position)
			{
				return mListItems.get(position);
			}
			
			@Override
			public int getCount()
			{
				return mListItems.size();
			}
			
			@Override
			public boolean areAllItemsEnabled()
			{
				return false;
			}
			
			@Override
			public boolean isEnabled(int position)
			{
				return false;
			}
		});
	}
	
	private static final int OPTIONS_MENU_CLEAR = 0;
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		ActionIconSet set = new ActionIconSet(getActivity());
		menu.add(0, OPTIONS_MENU_CLEAR, 0, R.string.action_clear).setIcon(set.getId(R.attr.actionDelete))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		super.onCreateOptionsMenu(menu, inflater);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case OPTIONS_MENU_CLEAR:
			{
				StatisticsManager.getInstance().clear();
				getActivity().finish();
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}
}