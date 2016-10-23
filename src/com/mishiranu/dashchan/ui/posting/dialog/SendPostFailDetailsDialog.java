package com.mishiranu.dashchan.ui.posting.dialog;

import java.io.Serializable;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import chan.content.ApiException;
import chan.util.StringUtils;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.PostDateFormatter;
import com.mishiranu.dashchan.util.StringBlockBuilder;
import com.mishiranu.dashchan.util.ViewUtils;

public class SendPostFailDetailsDialog extends PostingDialog
{
	public static final String TAG = SendPostFailDetailsDialog.class.getName();

	private static final String EXTRA_EXTRA = "extra";

	public SendPostFailDetailsDialog()
	{

	}

	public SendPostFailDetailsDialog(Serializable extra)
	{
		Bundle args = new Bundle();
		args.putSerializable(EXTRA_EXTRA, extra);
		setArguments(args);
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		String message = null;
		Object extra = getArguments().getSerializable(EXTRA_EXTRA);
		if (extra instanceof ApiException.BanExtra)
		{
			StringBlockBuilder builder = new StringBlockBuilder();
			PostDateFormatter formatter = new PostDateFormatter(getActivity());
			ApiException.BanExtra banExtra = (ApiException.BanExtra) extra;
			if (!StringUtils.isEmpty(banExtra.id))
			{
				builder.appendLine(getString(R.string.text_ban_id_format, banExtra.id));
			}
			if (banExtra.startDate > 0L)
			{
				builder.appendLine(getString(R.string.text_ban_start_date_format,
						formatter.format(banExtra.startDate)));
			}
			if (banExtra.expireDate > 0L)
			{
				builder.appendLine(getString(R.string.text_ban_expires_format,
						banExtra.expireDate == Long.MAX_VALUE ? getString(R.string.text_ban_expires_never)
								: formatter.format(banExtra.expireDate)));
			}
			if (!StringUtils.isEmpty(banExtra.message))
			{
				builder.appendLine(getString(R.string.text_ban_reason_format, banExtra.message));
			}
			message = builder.toString();
		}
		else if (extra instanceof ApiException.WordsExtra)
		{
			StringBuilder builder = new StringBuilder();
			ApiException.WordsExtra words = (ApiException.WordsExtra) extra;
			builder.append(getString(R.string.text_rejected_words)).append(": ");
			boolean first = true;
			for (String word : words.words)
			{
				if (first) first = false; else builder.append(", ");
				builder.append(word);
			}
			message = builder.toString();
		}
		AlertDialog dialog = new AlertDialog.Builder(getActivity()).setTitle(R.string.action_details)
				.setMessage(message).setPositiveButton(android.R.string.ok, null).create();
		dialog.setOnShowListener(ViewUtils.ALERT_DIALOG_MESSAGE_SELECTABLE);
		return dialog;
	}
}