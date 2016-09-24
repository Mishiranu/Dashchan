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

package com.mishiranu.dashchan.widget;

import android.annotation.TargetApi;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.TextView;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ResourceUtils;

public class ViewFactory
{
	@TargetApi(Build.VERSION_CODES.M)
	@SuppressWarnings("deprecation")
	public static TextView makeListTextHeader(ViewGroup parent, boolean tiny)
	{
		if (C.API_LOLLIPOP)
		{
			TextView textView = new TextView(parent.getContext());
			if (C.API_MARSHMALLOW) textView.setTextAppearance(R.style.Widget_CategoryHeader);
			else textView.setTextAppearance(parent.getContext(), R.style.Widget_CategoryHeader);
			float density = ResourceUtils.obtainDensity(parent);
			textView.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT,
					AbsListView.LayoutParams.WRAP_CONTENT));
			textView.setGravity(Gravity.CENTER_VERTICAL);
			textView.setPadding((int) (16f * density), (int) (8f * density), (int) (16f * density),
					tiny ? 0 : (int) (8f * density));
			return textView;
		}
		else
		{
			TextView textView = (TextView) LayoutInflater.from(parent.getContext())
					.inflate(android.R.layout.preference_category, parent, false);
			float density = ResourceUtils.obtainDensity(parent);
			textView.setPadding((int) (12f * density), textView.getPaddingTop(), (int) (12f * density),
					textView.getPaddingBottom());
			return textView;
		}
	}

	public static class TwoLinesViewHolder
	{
		public TextView text1, text2;
	}

	public static View makeTwoLinesListItem(ViewGroup parent, boolean singleLine)
	{
		ViewGroup view = (ViewGroup) LayoutInflater.from(parent.getContext())
				.inflate(ResourceUtils.getResourceId(parent.getContext(), android.R.attr.preferenceStyle,
				android.R.attr.layout, android.R.layout.simple_list_item_2), parent, false);
		TwoLinesViewHolder holder = new TwoLinesViewHolder();
		if (view.findViewById(android.R.id.icon) != null)
		{
			holder.text1 = (TextView) view.findViewById(android.R.id.title);
			holder.text2 = (TextView) view.findViewById(android.R.id.summary);
			view.removeViewAt(view.getChildCount() - 1);
			view.removeViewAt(0);
		}
		else
		{
			holder.text1 = (TextView) view.findViewById(android.R.id.text1);
			holder.text2 = (TextView) view.findViewById(android.R.id.text2);
		}
		holder.text1.setSingleLine(true);
		if (singleLine) holder.text2.setSingleLine(true);
		holder.text1.setEllipsize(TextUtils.TruncateAt.END);
		holder.text2.setEllipsize(TextUtils.TruncateAt.END);
		view.setTag(holder);
		return view;
	}
}