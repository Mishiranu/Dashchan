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
import com.mishiranu.dashchan.widget.SummaryLayout;

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
		ApiException.Extra extra = requireArguments().getParcelable(EXTRA_EXTRA);
		AlertDialog dialog = new AlertDialog.Builder(requireContext())
				.setTitle(R.string.details)
				.setPositiveButton(android.R.string.ok, null)
				.create();
		if (extra instanceof ApiException.BanExtra) {
			SummaryLayout layout = new SummaryLayout(dialog);
			PostDateFormatter formatter = new PostDateFormatter(requireContext());
			ApiException.BanExtra banExtra = (ApiException.BanExtra) extra;
			if (!StringUtils.isEmpty(banExtra.id)) {
				layout.add(getString(R.string.ban_id), banExtra.id);
			}
			if (banExtra.startDate > 0L) {
				layout.add(getString(R.string.filed_on), formatter.formatDateTime(banExtra.startDate));
			}
			if (banExtra.expireDate > 0L) {
				layout.add(getString(R.string.expires), banExtra.expireDate == Long.MAX_VALUE
						? getString(R.string.never) : formatter.formatDateTime(banExtra.expireDate));
			}
			if (!StringUtils.isEmpty(banExtra.message)) {
				layout.add(getString(R.string.reason), banExtra.message);
			}
		} else if (extra instanceof ApiException.WordsExtra) {
			ApiException.WordsExtra words = (ApiException.WordsExtra) extra;
			String message = "";
			boolean first = true;
			for (String word : words.words) {
				if (first) {
					first = false;
					message = word;
				} else {
					message = getString(R.string.__enumeration_format, message, word);
				}
			}
			dialog.setMessage(ResourceUtils.getColonString(getResources(), R.string.rejected_words, message));
		}
		return dialog;
	}
}
