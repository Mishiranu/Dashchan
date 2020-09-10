package com.mishiranu.dashchan.ui.posting.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import chan.content.ApiException;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.PostDateFormatter;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.StringBlockBuilder;
import com.mishiranu.dashchan.util.ViewUtils;

public class SendPostFailDetailsDialog extends DialogFragment {
	public static final String TAG = SendPostFailDetailsDialog.class.getName();

	private static final String EXTRA_EXTRA = "extra";

	public SendPostFailDetailsDialog() {}

	public SendPostFailDetailsDialog(ApiException.Extra extra) {
		Bundle args = new Bundle();
		args.putParcelable(EXTRA_EXTRA, extra);
		setArguments(args);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		String message = null;
		ApiException.Extra extra = requireArguments().getParcelable(EXTRA_EXTRA);
		if (extra instanceof ApiException.BanExtra) {
			StringBlockBuilder builder = new StringBlockBuilder();
			PostDateFormatter formatter = new PostDateFormatter(requireContext());
			ApiException.BanExtra banExtra = (ApiException.BanExtra) extra;
			if (!StringUtils.isEmpty(banExtra.id)) {
				builder.appendLine(ResourceUtils.getColonString(getResources(), R.string.ban_id, banExtra.id));
			}
			if (banExtra.startDate > 0L) {
				builder.appendLine(ResourceUtils.getColonString(getResources(),
						R.string.filed_on, formatter.format(banExtra.startDate)));
			}
			if (banExtra.expireDate > 0L) {
				builder.appendLine(ResourceUtils.getColonString(getResources(),
						R.string.expires, banExtra.expireDate == Long.MAX_VALUE
								? getString(R.string.never) : formatter.format(banExtra.expireDate)));
			}
			if (!StringUtils.isEmpty(banExtra.message)) {
				builder.appendLine(ResourceUtils.getColonString(getResources(),
						R.string.reason, banExtra.message));
			}
			message = builder.toString();
		} else if (extra instanceof ApiException.WordsExtra) {
			ApiException.WordsExtra words = (ApiException.WordsExtra) extra;
			message = "";
			boolean first = true;
			for (String word : words.words) {
				if (first) {
					first = false;
					message = word;
				} else {
					message = getString(R.string.__enumeration_format, message, word);
				}
			}
			message = ResourceUtils.getColonString(getResources(), R.string.rejected_words, message);
		}
		AlertDialog dialog = new AlertDialog.Builder(requireContext())
				.setTitle(R.string.details)
				.setMessage(message)
				.setPositiveButton(android.R.string.ok, null)
				.create();
		dialog.setOnShowListener(ViewUtils.ALERT_DIALOG_MESSAGE_SELECTABLE);
		return dialog;
	}
}
