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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;

public class MessageDialog extends DialogFragment implements DialogInterface.OnClickListener {
	private static final String TAG = MessageDialog.class.getName();

	private static final String EXTRA_MESSAGE = "message";
	private static final String EXTRA_FINISH_ACTIVITY = "finishActivity";

	public MessageDialog() {}

	public static void create(Fragment fragment, CharSequence message, boolean finishActivity) {
		dismissIfOpen(fragment);
		MessageDialog dialog = new MessageDialog();
		Bundle args = new Bundle();
		args.putCharSequence(EXTRA_MESSAGE, message);
		args.putBoolean(EXTRA_FINISH_ACTIVITY, finishActivity);
		dialog.setArguments(args);
		dialog.show(fragment.getFragmentManager(), TAG);
	}

	public static void dismissIfOpen(Fragment fragment) {
		MessageDialog dialog = (MessageDialog) fragment.getFragmentManager().findFragmentByTag(MessageDialog.TAG);
		if (dialog != null) {
			dialog.dismissAllowingStateLoss();
		}
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		return new AlertDialog.Builder(getActivity()).setMessage(getArguments().getCharSequence(EXTRA_MESSAGE))
				.setPositiveButton(android.R.string.ok, this).create();
	}

	private void checkFinish() {
		if (getArguments().getBoolean(EXTRA_FINISH_ACTIVITY)) {
			getActivity().finish();
		}
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		checkFinish();
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		checkFinish();
	}
}