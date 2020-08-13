package com.mishiranu.dashchan.ui.preference;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import com.mishiranu.dashchan.ui.FragmentHandler;

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
		dialog.show(fragment.getChildFragmentManager(), TAG);
	}

	public static void dismissIfOpen(Fragment fragment) {
		MessageDialog dialog = (MessageDialog) fragment.getChildFragmentManager()
				.findFragmentByTag(MessageDialog.TAG);
		if (dialog != null) {
			dialog.dismissAllowingStateLoss();
		}
	}

	@NonNull
	@Override
	public AlertDialog onCreateDialog(Bundle savedInstanceState) {
		return new AlertDialog.Builder(requireContext())
				.setMessage(requireArguments().getCharSequence(EXTRA_MESSAGE))
				.setPositiveButton(android.R.string.ok, this).create();
	}

	private void checkFinish() {
		if (requireArguments().getBoolean(EXTRA_FINISH_ACTIVITY)) {
			((FragmentHandler) requireActivity()).removeFragment();
		}
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		checkFinish();
	}

	@Override
	public void onCancel(@NonNull DialogInterface dialog) {
		checkFinish();
	}
}
