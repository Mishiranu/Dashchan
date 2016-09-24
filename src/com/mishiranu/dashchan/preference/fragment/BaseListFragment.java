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

package com.mishiranu.dashchan.preference.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ResourceUtils;

public abstract class BaseListFragment extends Fragment implements AdapterView.OnItemClickListener
{
	private ListView mListView;
	private View mEmptyView;
	private TextView mEmptyText;

	@Override
	public final View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.activity_common, container, false);
		ListView listView = (ListView) view.findViewById(android.R.id.list);
		mEmptyView = view.findViewById(R.id.error);
		mEmptyText = (TextView) view.findViewById(R.id.error_text);
		mEmptyView.setVisibility(View.GONE);
		listView.setOnItemClickListener(this);
		registerForContextMenu(listView);
		ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
		try
		{
			layoutParams.getClass().getDeclaredField("removeBorders").set(layoutParams, true);
			if (!C.API_MARSHMALLOW)
			{
				float density = ResourceUtils.obtainDensity(inflater.getContext());
				int padding = (int) ((C.API_LOLLIPOP ? 8f : 16f) * density);
				listView.setPadding(padding, 0, padding, 0);
			}
			listView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);
		}
		catch (Exception e)
		{

		}
		mListView = listView;
		return view;
	}

	public ListView getListView()
	{
		return mListView;
	}

	public void setListAdapter(ListAdapter adapter)
	{
		mListView.setAdapter(adapter);
	}

	public void setEmptyText(CharSequence text)
	{
		mEmptyText.setText(text);
		if (StringUtils.isEmpty(text))
		{
			mListView.setEmptyView(null);
			mEmptyView.setVisibility(View.GONE);
		}
		else
		{
			mEmptyView.setVisibility(View.VISIBLE);
			mListView.setEmptyView(mEmptyView);
		}
	}

	public void setEmptyText(int resId)
	{
		setEmptyText(getString(resId));
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{

	}
}