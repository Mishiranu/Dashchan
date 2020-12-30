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
import com.mishiranu.dashchan.media.ExifData;
import com.mishiranu.dashchan.media.JpegData;
import com.mishiranu.dashchan.media.PngData;
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
		PngData pngData = fileHolder.getPngData();
		boolean hasMetadata = pngData != null && pngData.hasMetadata;
		boolean hasExif = jpegData != null && jpegData.exifData != null;
		int rotation = fileHolder.getImageRotation();
		ExifData exifData = jpegData != null ? jpegData.exifData : null;
		String geolocation = exifData != null ? exifData.getGeolocation(false) : null;
		String message = "";
		if (hasMetadata) {
			message = appendMessage(message, getString(R.string.metadata));
		}
		if (hasExif) {
			message = appendMessage(message, getString(R.string.exif_metadata));
		}
		if (rotation != 0) {
			message = appendMessage(message, getString(R.string.orientation));
		}
		if (geolocation != null) {
			message = appendMessage(message, getString(R.string.geolocation));
		}
		return new AlertDialog.Builder(activity).setTitle(R.string.warning)
				.setMessage(getString(R.string.file_contains_data__format_sentence, message))
				.setPositiveButton(android.R.string.ok, null).create();
	}

	private String appendMessage(String message, String append) {
		if (message.isEmpty()) {
			return append;
		} else {
			return getString(R.string.__enumeration_format, message, append);
		}
	}
}
