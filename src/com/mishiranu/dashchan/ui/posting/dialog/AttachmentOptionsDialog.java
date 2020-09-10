package com.mishiranu.dashchan.ui.posting.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import chan.content.ChanConfiguration;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.content.storage.DraftsStorage;
import com.mishiranu.dashchan.graphics.TransparentTileDrawable;
import com.mishiranu.dashchan.ui.posting.AttachmentHolder;
import com.mishiranu.dashchan.ui.posting.PostingDialogCallback;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import java.util.ArrayList;
import java.util.HashMap;

public class AttachmentOptionsDialog extends DialogFragment implements AdapterView.OnItemClickListener {
	public static final String TAG = AttachmentOptionsDialog.class.getName();

	private static final String EXTRA_ATTACHMENT_INDEX = "attachmentIndex";

	private enum Type {UNIQUE_HASH, REMOVE_METADATA, REENCODE_IMAGE, REMOVE_FILE_NAME, SPOILER}

	private static class OptionItem {
		public final String title;
		public final Type type;
		public final boolean checked;

		public OptionItem(String title, Type type, boolean checked) {
			this.title = title;
			this.type = type;
			this.checked = checked;
		}
	}

	private final ArrayList<OptionItem> optionItems = new ArrayList<>();
	private final HashMap<Type, Integer> optionIndices = new HashMap<>();

	private ListView listView;

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

	private AttachmentHolder getAttachmentHolder() {
		return ((PostingDialogCallback) getParentFragment())
				.getAttachmentHolder(requireArguments().getInt(EXTRA_ATTACHMENT_INDEX));
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Activity activity = getActivity();
		AttachmentHolder holder = getAttachmentHolder();
		FileHolder fileHolder = holder != null ? DraftsStorage.getInstance()
				.getAttachmentDraftFileHolder(holder.hash) : null;
		if (holder == null || fileHolder == null) {
			dismiss();
			return new Dialog(activity);
		}
		ChanConfiguration.Posting postingConfiguration = ((PostingDialogCallback) getParentFragment())
				.getPostingConfiguration();
		int index = 0;
		optionItems.clear();
		optionIndices.clear();
		optionItems.add(new OptionItem(getString(R.string.unique_hash), Type.UNIQUE_HASH,
				holder.optionUniqueHash));
		optionIndices.put(Type.UNIQUE_HASH, index++);
		if (GraphicsUtils.canRemoveMetadata(fileHolder)) {
			optionItems.add(new OptionItem(getString(R.string.remove_metadata), Type.REMOVE_METADATA,
					holder.optionRemoveMetadata));
			optionIndices.put(Type.REMOVE_METADATA, index++);
		}
		if (fileHolder.isImage()) {
			optionItems.add(new OptionItem(getString(R.string.reencode_image), Type.REENCODE_IMAGE,
					holder.reencoding != null));
			optionIndices.put(Type.REENCODE_IMAGE, index++);
		}
		optionItems.add(new OptionItem(getString(R.string.remove_file_name), Type.REMOVE_FILE_NAME,
				holder.optionRemoveFileName));
		optionIndices.put(Type.REMOVE_FILE_NAME, index++);
		if (postingConfiguration.attachmentSpoiler) {
			optionItems.add(new OptionItem(getString(R.string.spoiler), Type.SPOILER,
					holder.optionSpoiler));
			// noinspection UnusedAssignment
			optionIndices.put(Type.SPOILER, index++);
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
		int resId = ResourceUtils.obtainAlertDialogLayoutResId(activity, ResourceUtils.DialogLayout.MULTI_CHOICE);
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
		Integer reencodeIndex = optionIndices.get(Type.REENCODE_IMAGE);
		boolean allowRemoveMetadata = reencodeIndex == null || holder.reencoding == null;
		Integer removeMetadataIndex = optionIndices.get(Type.REMOVE_METADATA);
		if (removeMetadataIndex != null) {
			adapter.setEnabled(removeMetadataIndex, allowRemoveMetadata);
			adapter.notifyDataSetChanged();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		AttachmentHolder holder = getAttachmentHolder();
		Type type = optionItems.get(position).type;
		boolean checked = listView.isItemChecked(position);
		switch (type) {
			case UNIQUE_HASH: {
				holder.optionUniqueHash = checked;
				break;
			}
			case REMOVE_METADATA: {
				holder.optionRemoveMetadata = checked;
				break;
			}
			case REENCODE_IMAGE: {
				if (checked) {
					listView.setItemChecked(position, false);
					new ReencodingDialog().show(getChildFragmentManager(), ReencodingDialog.TAG);
				} else {
					holder.reencoding = null;
				}
				break;
			}
			case REMOVE_FILE_NAME: {
				holder.optionRemoveFileName = checked;
				break;
			}
			case SPOILER: {
				holder.optionSpoiler = checked;
				break;
			}
		}
		updateItemsEnabled((ItemsAdapter) listView.getAdapter(), holder);
	}

	public void setReencoding(GraphicsUtils.Reencoding reencoding) {
		AttachmentHolder holder = getAttachmentHolder();
		Integer reencodeIndex = optionIndices.get(Type.REENCODE_IMAGE);
		if (reencodeIndex != null) {
			holder.reencoding = reencoding;
			listView.setItemChecked(reencodeIndex, reencoding != null);
			updateItemsEnabled((ItemsAdapter) listView.getAdapter(), holder);
		}
	}
}
