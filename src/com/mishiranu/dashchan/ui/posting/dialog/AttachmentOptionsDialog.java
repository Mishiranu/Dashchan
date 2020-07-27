package com.mishiranu.dashchan.ui.posting.dialog;

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
import androidx.annotation.NonNull;
import chan.content.ChanConfiguration;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.content.storage.DraftsStorage;
import com.mishiranu.dashchan.graphics.TransparentTileDrawable;
import com.mishiranu.dashchan.ui.posting.AttachmentHolder;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import java.util.ArrayList;

public class AttachmentOptionsDialog extends PostingDialog implements AdapterView.OnItemClickListener {
	public static final String TAG = AttachmentOptionsDialog.class.getName();

	private static final String EXTRA_ATTACHMENT_INDEX = "attachmentIndex";

	private static final int OPTION_TYPE_UNIQUE_HASH = 0;
	private static final int OPTION_TYPE_REMOVE_METADATA = 1;
	private static final int OPTION_TYPE_REENCODE_IMAGE = 2;
	private static final int OPTION_TYPE_REMOVE_FILE_NAME = 3;
	private static final int OPTION_TYPE_SPOILER = 4;

	private static class OptionItem {
		public final String title;
		public final int type;
		public final boolean checked;

		public OptionItem(String title, int type, boolean checked) {
			this.title = title;
			this.type = type;
			this.checked = checked;
		}
	}

	private final ArrayList<OptionItem> optionItems = new ArrayList<>();
	private final SparseIntArray optionIndexes = new SparseIntArray();

	private ListView listView;
	private AttachmentHolder holder;

	public AttachmentOptionsDialog() {}

	public AttachmentOptionsDialog(int attachmentIndex) {
		Bundle args = new Bundle();
		args.putInt(EXTRA_ATTACHMENT_INDEX, attachmentIndex);
		setArguments(args);
	}

	private static class ItemsAdapter extends ArrayAdapter<String> {
		private final SparseBooleanArray enabledItems = new SparseBooleanArray();

		public ItemsAdapter(Context context, int resId, ArrayList<String> items) {
			super(context, resId, android.R.id.text1, items);
		}

		@NonNull
		@Override
		public View getView(int position, View convertView, @NonNull ViewGroup parent) {
			View view = super.getView(position, convertView, parent);
			view.setEnabled(isEnabled(position));
			return view;
		}

		@Override
		public boolean isEnabled(int position) {
			return enabledItems.get(position, true);
		}

		public void setEnabled(int index, boolean enabled) {
			enabledItems.put(index, enabled);
		}
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Activity activity = getActivity();
		holder = getAttachmentHolder(EXTRA_ATTACHMENT_INDEX);
		FileHolder fileHolder = holder != null ? DraftsStorage.getInstance()
				.getAttachmentDraftFileHolder(holder.hash) : null;
		if (holder == null || fileHolder == null) {
			dismiss();
			return new Dialog(activity);
		}
		ChanConfiguration.Posting postingConfiguration = getPostingConfiguration();
		int index = 0;
		optionItems.clear();
		optionIndexes.clear();
		optionItems.add(new OptionItem(getString(R.string.text_unique_hash), OPTION_TYPE_UNIQUE_HASH,
				holder.optionUniqueHash));
		optionIndexes.append(OPTION_TYPE_UNIQUE_HASH, index++);
		if (GraphicsUtils.canRemoveMetadata(fileHolder)) {
			optionItems.add(new OptionItem(getString(R.string.text_remove_metadata), OPTION_TYPE_REMOVE_METADATA,
					holder.optionRemoveMetadata));
			optionIndexes.append(OPTION_TYPE_REMOVE_METADATA, index++);
		}
		if (fileHolder.isImage()) {
			optionItems.add(new OptionItem(getString(R.string.text_reencode_image), OPTION_TYPE_REENCODE_IMAGE,
					holder.reencoding != null));
			optionIndexes.append(OPTION_TYPE_REENCODE_IMAGE, index++);
		}
		optionItems.add(new OptionItem(getString(R.string.text_remove_file_name), OPTION_TYPE_REMOVE_FILE_NAME,
				holder.optionRemoveFileName));
		optionIndexes.append(OPTION_TYPE_REMOVE_FILE_NAME, index++);
		if (postingConfiguration.attachmentSpoiler) {
			optionItems.add(new OptionItem(getString(R.string.text_spoiler), OPTION_TYPE_SPOILER,
					holder.optionSpoiler));
			// noinspection UnusedAssignment
			optionIndexes.append(OPTION_TYPE_SPOILER, index++);
		}
		ArrayList<String> items = new ArrayList<>();
		for (OptionItem optionItem : optionItems) {
			items.add(optionItem.title);
		}
		LinearLayout linearLayout = new LinearLayout(activity);
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		ImageView imageView = new ImageView(activity);
		imageView.setBackground(new TransparentTileDrawable(activity, true));
		imageView.setImageDrawable(holder.imageView.getDrawable());
		imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
		linearLayout.addView(imageView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
		listView = new ListView(activity);
		linearLayout.addView(listView, LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		int resId = ResourceUtils.obtainAlertDialogLayoutResId(activity, ResourceUtils.DIALOG_LAYOUT_MULTI_CHOICE);
		if (C.API_LOLLIPOP) {
			listView.setDividerHeight(0);
		}
		ItemsAdapter adapter = new ItemsAdapter(activity, resId, items);
		listView.setAdapter(adapter);
		for (int i = 0; i < optionItems.size(); i++) {
			listView.setItemChecked(i, optionItems.get(i).checked);
		}
		listView.setOnItemClickListener(this);
		updateItemsEnabled(adapter, holder);
		AlertDialog dialog = new AlertDialog.Builder(activity).setView(linearLayout).create();
		dialog.setCanceledOnTouchOutside(true);
		return dialog;
	}

	private void updateItemsEnabled(ItemsAdapter adapter, AttachmentHolder holder) {
		int reencodeIndex = optionIndexes.get(OPTION_TYPE_REENCODE_IMAGE, -1);
		boolean allowRemoveMetadata = reencodeIndex == -1 || holder.reencoding == null;
		int removeMetadataIndex = optionIndexes.get(OPTION_TYPE_REMOVE_METADATA, -1);
		if (removeMetadataIndex >= 0) {
			adapter.setEnabled(removeMetadataIndex, allowRemoveMetadata);
			adapter.notifyDataSetChanged();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		int type = optionItems.get(position).type;
		boolean checked = listView.isItemChecked(position);
		switch (type) {
			case OPTION_TYPE_UNIQUE_HASH: {
				holder.optionUniqueHash = checked;
				break;
			}
			case OPTION_TYPE_REMOVE_METADATA: {
				holder.optionRemoveMetadata = checked;
				break;
			}
			case OPTION_TYPE_REENCODE_IMAGE: {
				if (checked) {
					listView.setItemChecked(position, false);
					ReencodingDialog dialog = new ReencodingDialog();
					dialog.bindCallback(this).show(getParentFragmentManager(), ReencodingDialog.TAG);
				} else {
					holder.reencoding = null;
				}
				break;
			}
			case OPTION_TYPE_REMOVE_FILE_NAME: {
				holder.optionRemoveFileName = checked;
				break;
			}
			case OPTION_TYPE_SPOILER: {
				holder.optionSpoiler = checked;
				break;
			}
		}
		updateItemsEnabled((ItemsAdapter) listView.getAdapter(), holder);
	}

	public void setReencoding(GraphicsUtils.Reencoding reencoding) {
		int reencodeIndex = optionIndexes.get(OPTION_TYPE_REENCODE_IMAGE, -1);
		if (reencodeIndex >= 0) {
			holder.reencoding = reencoding;
			listView.setItemChecked(reencodeIndex, reencoding != null);
			updateItemsEnabled((ItemsAdapter) listView.getAdapter(), holder);
		}
	}
}
