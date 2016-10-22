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
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;

import com.mishiranu.dashchan.R;

public class MessageDialog extends DialogFragment implements DialogInterface.OnClickListener
{
	private static final String TAG = MessageDialog.class.getName();

	public static final int TYPE_LOADING = 0;
	public static final int TYPE_UPDATE_REMINDER = 1;
	public static final int TYPE_UNINSTALL_REMINDER = 2;

	private static final String EXTRA_TYPE = "type";

	public MessageDialog()
	{

	}

	public static void create(int type, Fragment fragment, boolean setTarget)
	{
		dismissIfOpen(fragment);
		MessageDialog dialog = new MessageDialog();
		Bundle args = new Bundle();
		args.putInt(EXTRA_TYPE, type);
		dialog.setArguments(args);
		if (setTarget) dialog.setTargetFragment(fragment, 0);
		dialog.show(fragment.getFragmentManager(), TAG);
	}

	public static void dismissIfOpen(Fragment fragment)
	{
		MessageDialog dialog = (MessageDialog) fragment.getFragmentManager().findFragmentByTag(MessageDialog.TAG);
		if (dialog != null) dialog.dismissAllowingStateLoss();
	}

	private int getType()
	{
		return getArguments().getInt(EXTRA_TYPE);
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		switch (getType())
		{
			case TYPE_LOADING:
			{
				ProgressDialog dialog = new ProgressDialog(getActivity());
				dialog.setMessage(getString(R.string.message_loading));
				dialog.setCanceledOnTouchOutside(false);
				return dialog;
			}
			case TYPE_UPDATE_REMINDER:
			{
				return new AlertDialog.Builder(getActivity()).setMessage(R.string.message_update_reminder)
						.setPositiveButton(android.R.string.ok, this).create();
			}
			case TYPE_UNINSTALL_REMINDER:
			{
				return new AlertDialog.Builder(getActivity()).setMessage(R.string.message_uninstall_reminder)
						.setPositiveButton(android.R.string.ok, this).create();
			}
		}
		throw new RuntimeException();
	}

	@Override
	public void onClick(DialogInterface dialog, int which)
	{
		switch (getType())
		{
			case TYPE_UPDATE_REMINDER:
			case TYPE_UNINSTALL_REMINDER:
			{
				getActivity().finish();
				break;
			}
		}
	}

	@Override
	public void onCancel(DialogInterface dialog)
	{
		super.onCancel(dialog);
		switch (getType())
		{
			case TYPE_LOADING:
			case TYPE_UPDATE_REMINDER:
			case TYPE_UNINSTALL_REMINDER:
			{
				getActivity().finish();
				break;
			}
		}
	}
}