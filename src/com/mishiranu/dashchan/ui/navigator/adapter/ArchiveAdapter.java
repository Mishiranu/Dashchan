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
import java.util.Collections;
import java.util.Locale;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import chan.content.model.ThreadSummary;
import chan.util.StringUtils;

import com.mishiranu.dashchan.util.ResourceUtils;

public class ArchiveAdapter extends BaseAdapter {
	private final ArrayList<ThreadSummary> archiveItems = new ArrayList<>();
	private final ArrayList<ThreadSummary> filteredArchiveItems = new ArrayList<>();

	private boolean filterMode = false;
	private String filterText;

	// Returns true, if adapter isn't empty.
	public boolean applyFilter(String text) {
		filterText = text;
		filterMode = !StringUtils.isEmpty(text);
		filteredArchiveItems.clear();
		if (filterMode) {
			text = text.toLowerCase(Locale.getDefault());
			for (ThreadSummary threadSummary : archiveItems) {
				boolean add = false;
				String title = threadSummary.getDescription();
				if (title != null && title.toLowerCase(Locale.getDefault()).contains(text)) {
					add = true;
				}
				if (add) {
					filteredArchiveItems.add(threadSummary);
				}
			}
		}
		notifyDataSetChanged();
		return !filterMode || filteredArchiveItems.size() > 0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ThreadSummary threadSummary = getItem(position);
		if (convertView == null) {
			float density = ResourceUtils.obtainDensity(parent);
			TextView textView = (TextView) LayoutInflater.from(parent.getContext())
					.inflate(android.R.layout.simple_list_item_1, parent, false);
			textView.setPadding((int) (16f * density), 0, (int) (16f * density), 0);
			textView.setEllipsize(TextUtils.TruncateAt.END);
			textView.setSingleLine(true);
			convertView = textView;
		}
		((TextView) convertView).setText(threadSummary.getDescription());
		return convertView;
	}

	@Override
	public int getCount() {
		return (filterMode ? filteredArchiveItems : archiveItems).size();
	}

	@Override
	public ThreadSummary getItem(int position) {
		return (filterMode ? filteredArchiveItems : archiveItems).get(position);
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	public void setItems(ThreadSummary[] threadSummaries) {
		archiveItems.clear();
		if (threadSummaries != null) {
			Collections.addAll(archiveItems, threadSummaries);
		}
		notifyDataSetChanged();
		if (filterMode) {
			applyFilter(filterText);
		}
	}
}