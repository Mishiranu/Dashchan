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

package com.mishiranu.dashchan.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

public class DialogMenu implements DialogInterface.OnClickListener
{
	private final Context mContext;
	private final AlertDialog.Builder mDialog;

	private final Callback mCallback;

	private final ArrayList<ListItem> mListItems = new ArrayList<>();
	private boolean mLongTitle;

	private static class ListItem
	{
		public final int id;
		public final String title;
		public final boolean checkable;
		public final boolean checked;

		public ListItem(int id, String title, boolean checkable, boolean checked)
		{
			this.id = id;
			this.title = title;
			this.checkable = checkable;
			this.checked = checked;
		}
	}

	private HashMap<String, Object> mExtra;
	private boolean mConsumed = false;

	public DialogMenu(Context context, Callback callback)
	{
		mContext = context;
		mDialog = new AlertDialog.Builder(context);
		mCallback = callback;
	}

	public DialogMenu setTitle(String title, boolean longTitle)
	{
		checkConsumed();
		mDialog.setTitle(title);
		mLongTitle = longTitle;
		return this;
	}

	private DialogMenu addItem(int id, String title, boolean checkable, boolean checked)
	{
		checkConsumed();
		mListItems.add(new ListItem(id, title, checkable, checked));
		return this;
	}

	public DialogMenu addItem(int id, String title)
	{
		return addItem(id, title, false, false);
	}

	public DialogMenu addItem(int id, int titleRes)
	{
		return addItem(id, mContext.getString(titleRes));
	}

	public DialogMenu addCheckableItem(int id, String title, boolean checked)
	{
		return addItem(id, title, true, checked);
	}

	public DialogMenu addCheckableItem(int id, int titleRes, boolean checked)
	{
		return addCheckableItem(id, mContext.getString(titleRes), checked);
	}

	public DialogMenu putExtra(String key, Object value)
	{
		if (mExtra == null) mExtra = new HashMap<>();
		mExtra.put(key, value);
		return this;
	}

	public void show()
	{
		checkConsumed();
		if (mListItems.size() > 0)
		{
			AlertDialog dialog = mDialog.setAdapter(new DialogAdapter(), this).create();
			if (mLongTitle) dialog.setOnShowListener(ViewUtils.ALERT_DIALOG_LONGER_TITLE);
			dialog.show();
		}
		mConsumed = true;
	}

	private void checkConsumed()
	{
		if (mConsumed) throw new RuntimeException("DialogMenu is already consumed.");
	}

	@Override
	public void onClick(DialogInterface dialog, int which)
	{
		mCallback.onItemClick(mContext, mListItems.get(which).id, mExtra);
	}

	public interface Callback
	{
		public void onItemClick(Context context, int id, Map<String, Object> extra);
	}

	private class DialogAdapter extends BaseAdapter
	{
		private static final int TYPE_SIMPLE = 0;
		private static final int TYPE_CHECKABLE = 1;

		private final int mLayoutResId;

		public DialogAdapter()
		{
			mLayoutResId = ResourceUtils.obtainAlertDialogLayoutResId(mContext, ResourceUtils.DIALOG_LAYOUT_SIMPLE);
		}

		@Override
		public int getViewTypeCount()
		{
			return 2;
		}

		@Override
		public int getItemViewType(int position)
		{
			return getItem(position).checkable ? TYPE_CHECKABLE : TYPE_SIMPLE;
		}

		@Override
		public int getCount()
		{
			return mListItems.size();
		}

		@Override
		public ListItem getItem(int position)
		{
			return mListItems.get(position);
		}

		@Override
		public long getItemId(int position)
		{
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			ListItem listItem = getItem(position);
			ViewHolder viewHolder;
			if (convertView == null)
			{
				viewHolder = new ViewHolder();
				View view = LayoutInflater.from(parent.getContext()).inflate(mLayoutResId, parent, false);
				if (listItem.checkable)
				{
					LinearLayout linearLayout = new LinearLayout(parent.getContext());
					linearLayout.setOrientation(LinearLayout.HORIZONTAL);
					linearLayout.setGravity(Gravity.CENTER_VERTICAL);
					linearLayout.addView(view, new LinearLayout.LayoutParams(0,
							LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
					CheckBox checkBox = new CheckBox(parent.getContext());
					checkBox.setClickable(false);
					checkBox.setFocusable(false);
					int paddingRight = view.getPaddingRight() - (int) (4f * ResourceUtils.obtainDensity(view));
					checkBox.setPadding(checkBox.getPaddingLeft(), checkBox.getPaddingTop(),
							Math.max(checkBox.getPaddingRight(), paddingRight), checkBox.getPaddingBottom());
					linearLayout.addView(checkBox, LinearLayout.LayoutParams.WRAP_CONTENT,
							LinearLayout.LayoutParams.WRAP_CONTENT);
					viewHolder.checkBox = checkBox;
					view = linearLayout;
				}
				viewHolder.textView = (TextView) view.findViewById(android.R.id.text1);
				view.setTag(viewHolder);
				convertView = view;
			}
			else viewHolder = (ViewHolder) convertView.getTag();
			viewHolder.textView.setText(listItem.title);
			if (listItem.checkable) viewHolder.checkBox.setChecked(listItem.checked);
			return convertView;
		}
	}

	private static class ViewHolder
	{
		public TextView textView;
		public CheckBox checkBox;
	}
}