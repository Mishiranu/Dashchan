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

package com.mishiranu.dashchan.ui.posting.dialog;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;

import chan.content.ChanConfiguration;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.graphics.TransparentTileDrawable;
import com.mishiranu.dashchan.ui.posting.AttachmentHolder;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

public class AttachmentOptionsDialog extends PostingDialog implements AdapterView.OnItemClickListener
{
	public static final String TAG = AttachmentOptionsDialog.class.getName();

	private static final String EXTRA_ATTACHMENT_INDEX = "attachmentIndex";

	private static final int OPTION_TYPE_UNIQUE_HASH = 0;
	private static final int OPTION_TYPE_REMOVE_METADATA = 1;
	private static final int OPTION_TYPE_REENCODE_IMAGE = 2;
	private static final int OPTION_TYPE_REMOVE_FILE_NAME = 3;
	private static final int OPTION_TYPE_SPOILER = 4;

	private static class OptionItem
	{
		public final String title;
		public final int type;
		public final boolean checked;

		public OptionItem(String title, int type, boolean checked)
		{
			this.title = title;
			this.type = type;
			this.checked = checked;
		}
	}

	private final ArrayList<OptionItem> mOptionItems = new ArrayList<>();
	private final SparseIntArray mOptionIndexes = new SparseIntArray();

	private ListView mListView;

	public AttachmentOptionsDialog()
	{

	}

	public AttachmentOptionsDialog(int attachmentIndex)
	{
		Bundle args = new Bundle();
		args.putInt(EXTRA_ATTACHMENT_INDEX, attachmentIndex);
		setArguments(args);
	}

	private static class ItemsAdapter extends ArrayAdapter<String>
	{
		private final SparseBooleanArray mEnabledItems = new SparseBooleanArray();

		public ItemsAdapter(Context context, int resId, ArrayList<String> items)
		{
			super(context, resId, android.R.id.text1, items);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			View view = super.getView(position, convertView, parent);
			view.setEnabled(isEnabled(position));
			return view;
		}

		@Override
		public boolean isEnabled(int position)
		{
			return mEnabledItems.get(position, true);
		}

		public void setEnabled(int index, boolean enabled)
		{
			mEnabledItems.put(index, enabled);
		}
	}

	@SuppressWarnings("UnusedAssignment")
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		Activity activity = getActivity();
		AttachmentHolder holder = getAttachmentHolder(EXTRA_ATTACHMENT_INDEX);
		ChanConfiguration.Posting postingConfiguration = getPostingConfiguration();
		int index = 0;
		mOptionItems.clear();
		mOptionIndexes.clear();
		mOptionItems.add(new OptionItem(getString(R.string.text_unique_hash), OPTION_TYPE_UNIQUE_HASH,
				holder.optionUniqueHash));
		mOptionIndexes.append(OPTION_TYPE_UNIQUE_HASH, index++);
		if (GraphicsUtils.canRemoveMetadata(holder.fileHolder))
		{
			mOptionItems.add(new OptionItem(getString(R.string.text_remove_metadata), OPTION_TYPE_REMOVE_METADATA,
					holder.optionRemoveMetadata));
			mOptionIndexes.append(OPTION_TYPE_REMOVE_METADATA, index++);
		}
		if (holder.fileHolder.isImage())
		{
			mOptionItems.add(new OptionItem(getString(R.string.text_reencode_image), OPTION_TYPE_REENCODE_IMAGE,
					holder.reencoding != null));
			mOptionIndexes.append(OPTION_TYPE_REENCODE_IMAGE, index++);
		}
		mOptionItems.add(new OptionItem(getString(R.string.text_remove_file_name), OPTION_TYPE_REMOVE_FILE_NAME,
				holder.optionRemoveFileName));
		mOptionIndexes.append(OPTION_TYPE_REMOVE_FILE_NAME, index++);
		if (postingConfiguration.attachmentSpoiler)
		{
			mOptionItems.add(new OptionItem(getString(R.string.text_spoiler), OPTION_TYPE_SPOILER,
					holder.optionSpoiler));
			mOptionIndexes.append(OPTION_TYPE_SPOILER, index++);
		}
		ArrayList<String> items = new ArrayList<>();
		for (OptionItem optionItem : mOptionItems) items.add(optionItem.title);
		LinearLayout linearLayout = new LinearLayout(activity);
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		ImageView imageView = new ImageView(activity);
		imageView.setBackground(new TransparentTileDrawable(activity, true));
		imageView.setImageDrawable(holder.imageView.getDrawable());
		imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
		linearLayout.addView(imageView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				0, 1));
		mListView = new ListView(activity);
		linearLayout.addView(mListView, LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		int resId = ResourceUtils.obtainAlertDialogLayoutResId(activity, ResourceUtils.DIALOG_LAYOUT_MULTI_CHOICE);
		if (C.API_LOLLIPOP) mListView.setDividerHeight(0);
		ItemsAdapter adapter = new ItemsAdapter(activity, resId, items);
		mListView.setAdapter(adapter);
		for (int i = 0; i < mOptionItems.size(); i++) mListView.setItemChecked(i, mOptionItems.get(i).checked);
		mListView.setOnItemClickListener(this);
		updateItemsEnabled(adapter, holder);
		AlertDialog dialog = new AlertDialog.Builder(activity).setView(linearLayout).create();
		dialog.setCanceledOnTouchOutside(true);
		return dialog;
	}

	private void updateItemsEnabled(ItemsAdapter adapter, AttachmentHolder holder)
	{
		int reencodeIndex = mOptionIndexes.get(OPTION_TYPE_REENCODE_IMAGE, -1);
		boolean allowRemoveMetadata = reencodeIndex == -1 || holder.reencoding == null;
		int removeMetadataIndex = mOptionIndexes.get(OPTION_TYPE_REMOVE_METADATA, -1);
		if (removeMetadataIndex >= 0)
		{
			adapter.setEnabled(removeMetadataIndex, allowRemoveMetadata);
			adapter.notifyDataSetChanged();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		AttachmentHolder holder = getAttachmentHolder(EXTRA_ATTACHMENT_INDEX);
		int type = mOptionItems.get(position).type;
		boolean checked = mListView.isItemChecked(position);
		switch (type)
		{
			case OPTION_TYPE_UNIQUE_HASH: holder.optionUniqueHash = checked; break;
			case OPTION_TYPE_REMOVE_METADATA: holder.optionRemoveMetadata = checked; break;
			case OPTION_TYPE_REENCODE_IMAGE:
			{
				if (checked)
				{
					mListView.setItemChecked(position, false);
					ReencodingDialog dialog = new ReencodingDialog();
					dialog.bindCallback(this).show(getFragmentManager(), ReencodingDialog.TAG);
				}
				else holder.reencoding = null;
				break;
			}
			case OPTION_TYPE_REMOVE_FILE_NAME: holder.optionRemoveFileName = checked; break;
			case OPTION_TYPE_SPOILER: holder.optionSpoiler = checked; break;
		}
		updateItemsEnabled((ItemsAdapter) mListView.getAdapter(), holder);
	}

	public void setReencoding(GraphicsUtils.Reencoding reencoding)
	{
		int reencodeIndex = mOptionIndexes.get(OPTION_TYPE_REENCODE_IMAGE, -1);
		if (reencodeIndex >= 0)
		{
			AttachmentHolder holder = getAttachmentHolder(EXTRA_ATTACHMENT_INDEX);
			holder.reencoding = reencoding;
			mListView.setItemChecked(reencodeIndex, reencoding != null);
			updateItemsEnabled((ItemsAdapter) mListView.getAdapter(), holder);
		}
	}
}