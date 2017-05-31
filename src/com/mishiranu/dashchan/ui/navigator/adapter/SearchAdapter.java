/*
 * Copyright 2014-2017 Fukurou Mishiranu
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
import java.util.LinkedHashMap;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.ui.navigator.manager.HidePerformer;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ViewFactory;
import com.mishiranu.dashchan.widget.callback.BusyScrollListener;

public class SearchAdapter extends BaseAdapter implements BusyScrollListener.Callback {
	private static final int TYPE_VIEW = 0;
	private static final int TYPE_HEADER = 1;

	private final UiManager uiManager;
	private final UiManager.DemandSet demandSet = new UiManager.DemandSet();
	private final UiManager.ConfigurationSet configurationSet;

	private final ArrayList<PostItem> postItems = new ArrayList<>();
	private final ArrayList<Object> groupItems = new ArrayList<>();

	private boolean groupMode = false;

	public SearchAdapter(UiManager uiManager) {
		this.uiManager = uiManager;
		configurationSet = new UiManager.ConfigurationSet(null, null, new HidePerformer(),
				new GalleryItem.GallerySet(false), null, null, true, false, false, false, false, null);
	}

	@Override
	public int getViewTypeCount() {
		return 2;
	}

	@Override
	public int getItemViewType(int position) {
		Object item = getItem(position);
		return item instanceof PostItem ? TYPE_VIEW : TYPE_HEADER;
	}

	@Override
	public boolean isEnabled(int position) {
		Object item = getItem(position);
		return item instanceof PostItem;
	}

	@Override
	public boolean areAllItemsEnabled() {
		return false;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		Object item = getItem(position);
		if (item instanceof PostItem) {
			return uiManager.view().getPostView((PostItem) item, convertView, parent, demandSet, configurationSet);
		} else {
			if (convertView == null) {
				convertView = ViewFactory.makeListTextHeader(parent, false);
				if (C.API_LOLLIPOP) {
					float density = ResourceUtils.obtainDensity(parent);
					convertView.setPadding((int) (12f * density), convertView.getPaddingTop() + (int) (12f * density),
							(int) (12f * density), convertView.getPaddingBottom());
				}
			}
			((TextView) convertView).setText((String) item);
			return convertView;
		}
	}

	@Override
	public int getCount() {
		return groupMode ? groupItems.size() : postItems.size();
	}

	@Override
	public Object getItem(int position) {
		return groupMode ? groupItems.get(position) : postItems.get(position);
	}

	public PostItem getPostItem(int position) {
		Object item = getItem(position);
		if (item instanceof PostItem) {
			return (PostItem) item;
		}
		return null;
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	@Override
	public void setListViewBusy(boolean isBusy, AbsListView listView) {
		uiManager.view().handleListViewBusyStateChange(isBusy, listView, demandSet);
	}

	public void setItems(ArrayList<PostItem> postItems) {
		this.postItems.clear();
		if (postItems != null) {
			this.postItems.addAll(postItems);
		}
		handleItems();
	}

	public void setGroupMode(boolean groupMode) {
		if (this.groupMode != groupMode) {
			this.groupMode = groupMode;
			handleItems();
		}
	}

	public boolean isGroupMode() {
		return groupMode;
	}

	private void handleItems() {
		groupItems.clear();
		configurationSet.gallerySet.clear();
		if (postItems.size() > 0) {
			if (groupMode) {
				LinkedHashMap<String, ArrayList<PostItem>> map = new LinkedHashMap<>();
				for (PostItem postItem : postItems) {
					String threadNumber = postItem.getThreadNumber();
					ArrayList<PostItem> postItems = map.get(threadNumber);
					if (postItems == null) {
						postItems = new ArrayList<>();
						map.put(threadNumber, postItems);
					}
					postItems.add(postItem);
				}
				for (LinkedHashMap.Entry<String, ArrayList<PostItem>> entry : map.entrySet()) {
					String threadNumber = entry.getKey();
					boolean number;
					try {
						Integer.parseInt(threadNumber);
						number = true;
					} catch (NumberFormatException e) {
						number = false;
					}
					groupItems.add(MainApplication.getInstance().getString(R.string.text_in_thread_format_format,
							number ? "#" + threadNumber : threadNumber));
					int ordinalIndex = 0;
					for (PostItem postItem : entry.getValue()) {
						groupItems.add(postItem);
						postItem.setOrdinalIndex(ordinalIndex++);
					}
				}
			} else {
				for (int i = 0; i < postItems.size(); i++) {
					postItems.get(i).setOrdinalIndex(i);
				}
			}
		}
		for (int i = 0, count = getCount(); i < count; i++) {
			PostItem postItem = getPostItem(i);
			if (postItem != null) {
				configurationSet.gallerySet.add(postItem.getAttachmentItems());
			}
		}
		notifyDataSetChanged();
	}
}
