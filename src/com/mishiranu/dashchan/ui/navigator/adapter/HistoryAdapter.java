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
import java.util.Calendar;
import java.util.Locale;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import chan.content.ChanConfiguration;
import chan.util.StringUtils;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.storage.HistoryDatabase;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ViewFactory;

public class HistoryAdapter extends BaseAdapter {
	private String chanName;

	private final ArrayList<Object> items = new ArrayList<>();
	private final ArrayList<Object> filteredItems = new ArrayList<>();

	private boolean filterMode = false;
	private String filterText;

	private static final int TYPE_HEADER = 0;
	private static final int TYPE_ITEM = 1;

	private static final int HEADER_NONE = 0;
	private static final int HEADER_TODAY = 1;
	private static final int HEADER_YESTERDAY = 2;
	private static final int HEADER_WEEK = 3;
	private static final int HEADER_OLD = 4;

	public void updateConfiguraion(String chanName) {
		this.chanName = chanName;
		items.clear();
		ArrayList<HistoryDatabase.HistoryItem> historyItems = HistoryDatabase.getInstance().getAllHistory(chanName);
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		long thisDay = calendar.getTimeInMillis();
		long yesterday = thisDay - 24 * 60 * 60 * 1000;
		long thisWeek = thisDay - 7 * 24 * 60 * 60 * 1000;
		int header = HEADER_NONE;
		for (HistoryDatabase.HistoryItem historyItem : historyItems) {
			int targetHeader = HEADER_TODAY;
			if (historyItem.time < thisDay) {
				if (historyItem.time < yesterday) {
					if (historyItem.time < thisWeek) {
						targetHeader = HEADER_OLD;
					} else {
						targetHeader = HEADER_WEEK;
					}
				} else {
					targetHeader = HEADER_YESTERDAY;
				}
			}
			if (targetHeader > header) {
				header = targetHeader;
				int resId = 0;
				switch (header) {
					case HEADER_TODAY: {
						resId = R.string.text_today;
						break;
					}
					case HEADER_YESTERDAY: {
						resId = R.string.text_yesterday;
						break;
					}
					case HEADER_WEEK: {
						resId = R.string.text_this_week;
						break;
					}
					case HEADER_OLD: {
						resId = R.string.text_older_7_days;
						break;
					}
				}
				items.add(MainApplication.getInstance().getString(resId));
			}
			items.add(historyItem);
		}
		if (filterMode) {
			applyFilter(filterText);
		} else {
			notifyDataSetChanged();
		}
	}

	// Returns true, if adapter isn't empty.
	public boolean applyFilter(String text) {
		filterText = text;
		filterMode = !StringUtils.isEmpty(text);
		filteredItems.clear();
		if (filterMode) {
			text = text.toLowerCase(Locale.getDefault());
			for (Object item : items) {
				if (item instanceof HistoryDatabase.HistoryItem) {
					HistoryDatabase.HistoryItem historyItem = (HistoryDatabase.HistoryItem) item;
					if (historyItem.title != null && historyItem.title.toLowerCase(Locale.getDefault())
							.contains(text)) {
						filteredItems.add(historyItem);
					}
				}
			}
		}
		notifyDataSetChanged();
		return !filterMode || filteredItems.size() > 0;
	}

	public void remove(HistoryDatabase.HistoryItem historyItem) {
		int index = items.indexOf(historyItem);
		if (index >= 0) {
			items.remove(index);
			if (index > 0 && (index == items.size() || getItemViewType(index) == TYPE_HEADER) &&
					getItemViewType(index - 1) == TYPE_HEADER) {
				items.remove(index - 1);
			}
			if (filterMode) {
				applyFilter(filterText);
			} else {
				notifyDataSetChanged();
			}
		}
	}

	public void clear() {
		items.clear();
		filteredItems.clear();
		notifyDataSetChanged();
	}

	@Override
	public int getCount() {
		return (filterMode ? filteredItems : items).size();
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	@Override
	public Object getItem(int position) {
		return (filterMode ? filteredItems : items).get(position);
	}

	public HistoryDatabase.HistoryItem getHistoryItem(int position) {
		Object item = getItem(position);
		if (item instanceof HistoryDatabase.HistoryItem) {
			return (HistoryDatabase.HistoryItem) item;
		}
		return null;
	}

	@Override
	public boolean areAllItemsEnabled() {
		return false;
	}

	@Override
	public boolean isEnabled(int position) {
		return getItem(position) instanceof HistoryDatabase.HistoryItem;
	}

	@Override
	public int getViewTypeCount() {
		return 2;
	}

	@Override
	public int getItemViewType(int position) {
		return getItem(position) instanceof HistoryDatabase.HistoryItem ? TYPE_ITEM : TYPE_HEADER;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		Object item = getItem(position);
		HistoryDatabase.HistoryItem historyItem = item instanceof HistoryDatabase.HistoryItem
				? (HistoryDatabase.HistoryItem) item : null;
		ViewFactory.TwoLinesViewHolder holder;
		if (convertView == null) {
			if (historyItem != null) {
				convertView = ViewFactory.makeTwoLinesListItem(parent, true);
				float density = ResourceUtils.obtainDensity(parent);
				convertView.setPadding((int) (16f * density), convertView.getPaddingTop(),
						(int) (16f * density), convertView.getPaddingBottom());
				holder = (ViewFactory.TwoLinesViewHolder) convertView.getTag();
			} else {
				convertView = ViewFactory.makeListTextHeader(parent, true);
				holder = null;
			}
		} else {
			if (historyItem != null) {
				holder = (ViewFactory.TwoLinesViewHolder) convertView.getTag();
			} else {
				holder = null;
			}
		}
		if (historyItem != null) {
			holder.text1.setText(historyItem.title);
			ChanConfiguration configuration = ChanConfiguration.get(historyItem.chanName);
			String title = configuration.getBoardTitle(historyItem.boardName);
			title = StringUtils.isEmpty(historyItem.boardName) ? title
					: StringUtils.formatBoardTitle(historyItem.chanName, historyItem.boardName, title);
			if (chanName == null) {
				title = configuration.getTitle() + " — " + title;
			}
			holder.text2.setText(title);
		} else {
			((TextView) convertView).setText((String) item);
		}
		return convertView;
	}
}