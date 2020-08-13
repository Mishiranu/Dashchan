package com.mishiranu.dashchan.ui.posting.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.content.storage.DraftsStorage;
import com.mishiranu.dashchan.media.JpegData;
import com.mishiranu.dashchan.ui.posting.AttachmentHolder;
import com.mishiranu.dashchan.ui.posting.PostingDialogCallback;

public class AttachmentWarningDialog extends DialogFragment {
	public static final String TAG = AttachmentWarningDialog.class.getName();

	private static final String EXTRA_ATTACHMENT_INDEX = "attachmentIndex";

	public AttachmentWarningDialog() {}

	public AttachmentWarningDialog(int attachmentIndex) {
		Bundle args = new Bundle();
		args.putInt(EXTRA_ATTACHMENT_INDEX, attachmentIndex);
		setArguments(args);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Activity activity = getActivity();
		AttachmentHolder holder = ((PostingDialogCallback) getParentFragment())
				.getAttachmentHolder(requireArguments().getInt(EXTRA_ATTACHMENT_INDEX));
		FileHolder fileHolder = holder != null ? DraftsStorage.getInstance()
				.getAttachmentDraftFileHolder(holder.hash) : null;
		if (holder == null || fileHolder == null) {
			dismiss();
			return new Dialog(activity);
		}
		JpegData jpegData = fileHolder.getJpegData();
		boolean hasExif = jpegData != null && jpegData.hasExif;
		int rotation = fileHolder.getRotation();
		String geolocation = jpegData != null ? jpegData.getGeolocation(false) : null;
		StringBuilder builder = new StringBuilder();
		if (hasExif) {
			if (builder.length() > 0) {
				builder.append(", ");
			}
			builder.append(getString(R.string.message_image_warning_exif));
		}
		if (rotation != 0) {
			if (builder.length() > 0) {
				builder.append(", ");
			}
			builder.append(getString(R.string.message_image_warning_orientation));
		}
		if (geolocation != null) {
			if (builder.length() > 0) {
				builder.append(", ");
			}
			builder.append(getString(R.string.message_image_warning_geolocation));
		}
		return new AlertDialog.Builder(activity).setTitle(R.string.text_warning)
				.setMessage(getString(R.string.message_image_warning, builder.toString()))
				.setPositiveButton(android.R.string.ok, null).create();
	}
}
