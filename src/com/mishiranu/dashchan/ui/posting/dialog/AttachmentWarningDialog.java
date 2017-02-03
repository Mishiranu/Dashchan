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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.content.storage.DraftsStorage;
import com.mishiranu.dashchan.media.JpegData;
import com.mishiranu.dashchan.ui.posting.AttachmentHolder;

public class AttachmentWarningDialog extends PostingDialog {
	public static final String TAG = AttachmentWarningDialog.class.getName();

	private static final String EXTRA_ATTACHMENT_INDEX = "attachmentIndex";

	public AttachmentWarningDialog() {}

	public AttachmentWarningDialog(int attachmentIndex) {
		Bundle args = new Bundle();
		args.putInt(EXTRA_ATTACHMENT_INDEX, attachmentIndex);
		setArguments(args);
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Activity activity = getActivity();
		AttachmentHolder holder = getAttachmentHolder(EXTRA_ATTACHMENT_INDEX);
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