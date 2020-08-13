package com.mishiranu.dashchan.ui.posting.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.ui.posting.AttachmentHolder;
import com.mishiranu.dashchan.ui.posting.PostingDialogCallback;
import java.util.List;

public class AttachmentRatingDialog extends DialogFragment implements DialogInterface.OnClickListener {
	public static final String TAG = AttachmentRatingDialog.class.getName();

	private static final String EXTRA_ATTACHMENT_INDEX = "attachmentIndex";

	public AttachmentRatingDialog() {}

	public AttachmentRatingDialog(int attachmentIndex) {
		Bundle args = new Bundle();
		args.putInt(EXTRA_ATTACHMENT_INDEX, attachmentIndex);
		setArguments(args);
	}

	private String[] ratings;

	private AttachmentHolder getAttachmentHolder() {
		return ((PostingDialogCallback) getParentFragment())
				.getAttachmentHolder(requireArguments().getInt(EXTRA_ATTACHMENT_INDEX));
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Activity activity = getActivity();
		AttachmentHolder holder = getAttachmentHolder();
		List<Pair<String, String>> attachmentRatingItems = ((PostingDialogCallback) getParentFragment())
				.getAttachmentRatingItems();
		if (holder == null || attachmentRatingItems == null) {
			dismiss();
			return new Dialog(activity);
		}
		String[] items = new String[attachmentRatingItems.size()];
		ratings = new String[attachmentRatingItems.size()];
		int checkedItem = 0;
		for (int i = 0; i < items.length; i++) {
			Pair<String, String> ratingItem = attachmentRatingItems.get(i);
			items[i] = ratingItem.second;
			ratings[i] = ratingItem.first;
			if (ratingItem.first.equals(holder.rating)) {
				checkedItem = i;
			}
		}
		return new AlertDialog.Builder(activity).setTitle(R.string.text_rating).setSingleChoiceItems(items,
				checkedItem, this).setNegativeButton(android.R.string.cancel, null).create();
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		AttachmentHolder holder = getAttachmentHolder();
		holder.rating = ratings[which];
		dismiss();
	}
}
