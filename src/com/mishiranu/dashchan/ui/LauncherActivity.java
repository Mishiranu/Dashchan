package com.mishiranu.dashchan.ui;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.view.ContextThemeWrapper;
import androidx.annotation.NonNull;
import chan.content.ChanManager;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import java.util.Collection;

/*
 * MainActivity can't be both singleTask and launcher activity, so I use this launcher activity.
 */
public class LauncherActivity extends StateActivity {
	private static final int STATE_START = 0;
	private static final int STATE_PERMISSION_REQUEST = 1;

	private static final String EXTRA_STATE = "state";

	private int state;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getIntent().setPackage(null);
		state = STATE_START;
		if (savedInstanceState != null) {
			state = savedInstanceState.getInt(EXTRA_STATE, state);
		}
		switch (state) {
			case STATE_START: {
				navigatePermissionRequest();
				break;
			}
			case STATE_PERMISSION_REQUEST: {
				break;
			}
		}
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(EXTRA_STATE, state);
	}

	@TargetApi(Build.VERSION_CODES.M)
	private void navigatePermissionRequest() {
		if (C.API_MARSHMALLOW) {
			if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
					!= PackageManager.PERMISSION_GRANTED) {
				Context context = new ContextThemeWrapper(this, Preferences.getThemeResource());
				new AlertDialog.Builder(context).setMessage(R.string.message_memory_access_permission)
						.setPositiveButton(android.R.string.ok, (dialog, which) -> {
					state = STATE_PERMISSION_REQUEST;
					requestPermissions(new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
				}).setCancelable(false).show();
				return;
			}
		}
		navigateExtensionsTrust();
	}

	private void navigateExtensionsTrust() {
		navigateExtensionsTrust(ChanManager.getInstance().getUntrustedExtensionItems());
	}

	private void navigateExtensionsTrust(final Collection<ChanManager.ExtensionItem> extensionItems) {
		if (!extensionItems.isEmpty()) {
			final ChanManager.ExtensionItem extensionItem = extensionItems.iterator().next();
			DialogInterface.OnClickListener onClickListener = (dialog, which) -> {
				switch (which) {
					case AlertDialog.BUTTON_POSITIVE:
					case AlertDialog.BUTTON_NEGATIVE: {
						ChanManager.getInstance().changeUntrustedExtensionState(extensionItem.extensionName,
								which == AlertDialog.BUTTON_POSITIVE);
						extensionItems.remove(extensionItem);
						break;
					}
					case AlertDialog.BUTTON_NEUTRAL: {
						startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
								.setData(Uri.parse("package:" + extensionItem.packageInfo.packageName)));
						break;
					}
				}
				navigateExtensionsTrust(extensionItems);

			};
			Context context = new ContextThemeWrapper(this, Preferences.getThemeResource());
			String packageName = extensionItem.packageInfo.packageName;
			SpannableStringBuilder message = new SpannableStringBuilder();
			message.append(getString(R.string.message_extension_trust_request));
			message.append("\n\n");
			StringUtils.appendSpan(message, packageName, new RelativeSizeSpan(0.8f));
			new AlertDialog.Builder(context).setTitle(extensionItem.extensionName).setMessage(message)
					.setPositiveButton(android.R.string.ok, onClickListener)
					.setNegativeButton(android.R.string.cancel, onClickListener)
					.setNeutralButton(R.string.action_details, onClickListener).setCancelable(false).show();
		} else {
			navigateMainActivity();
		}
	}

	private void navigateMainActivity() {
		int flags = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY & getIntent().getFlags();
		startActivity(new Intent(this, MainActivity.class).setFlags(flags));
		finish();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, int[] grantResults) {
		if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			navigateMainActivity();
		} else {
			finish();
		}
	}
}
