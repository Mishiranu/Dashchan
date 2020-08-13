package com.mishiranu.dashchan.ui;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import androidx.annotation.NonNull;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;

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
		navigateMainActivity();
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
