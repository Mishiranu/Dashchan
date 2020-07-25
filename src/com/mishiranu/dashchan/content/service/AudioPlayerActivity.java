/*
 * Copyright 2014-2017, 2020 Fukurou Mishiranu
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

package com.mishiranu.dashchan.content.service;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.StateActivity;
import com.mishiranu.dashchan.util.AndroidUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

public class AudioPlayerActivity extends StateActivity implements Runnable, SeekBar.OnSeekBarChangeListener,
		DialogInterface.OnCancelListener, DialogInterface.OnClickListener, View.OnClickListener, ServiceConnection {
	private Context context;
	private TextView textView;
	private SeekBar seekBar;
	private ImageButton button;
	private AlertDialog alertDialog;

	private boolean tracking = false;

	private AudioPlayerService.Binder audioPlayerBinder;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		context = new ContextThemeWrapper(this, Preferences.getThemeResource());
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		float density = ResourceUtils.obtainDensity(this);
		LinearLayout linearLayout = new LinearLayout(context);
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		int padding = getResources().getDimensionPixelSize(R.dimen.dialog_padding_view);
		linearLayout.setPadding(padding, padding, padding, C.API_LOLLIPOP ? (int) (8f * density) : padding);
		textView = new TextView(context, null, android.R.attr.textAppearanceListItem);
		linearLayout.addView(textView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		textView.setPadding(0, 0, 0, 0);
		textView.setEllipsize(TextUtils.TruncateAt.END);
		textView.setSingleLine(true);
		LinearLayout horizontal = new LinearLayout(context);
		horizontal.setOrientation(LinearLayout.HORIZONTAL);
		horizontal.setGravity(Gravity.CENTER_VERTICAL);
		horizontal.setPadding(0, (int) (16f * density), 0, 0);
		linearLayout.addView(horizontal, LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		seekBar = new SeekBar(context);
		horizontal.addView(seekBar, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
		seekBar.setPadding((int) (8f * density), 0, (int) (16f * density), 0);
		seekBar.setOnSeekBarChangeListener(this);
		button = new ImageButton(context);
		horizontal.addView(button, (int) (48f * density), (int) (48f * density));
		button.setBackgroundResource(ResourceUtils.getResourceId(context,
				android.R.attr.listChoiceBackgroundIndicator, 0));
		setPlayState(false);
		button.setOnClickListener(this);
		alertDialog = new AlertDialog.Builder(context).setView(linearLayout).setOnCancelListener(this)
				.setPositiveButton(R.string.action_stop, this).show();
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(AudioPlayerService.ACTION_TOGGLE);
		intentFilter.addAction(AudioPlayerService.ACTION_CANCEL);
		LocalBroadcastManager.getInstance(this).registerReceiver(audioPlayerReceiver, intentFilter);
		bindService(new Intent(this, AudioPlayerService.class), this, 0);
	}

	private final BroadcastReceiver audioPlayerReceiver = AndroidUtils.createReceiver((receiver, context, intent) -> {
		String action = intent.getAction();
		if (AudioPlayerService.ACTION_TOGGLE.equals(action)) {
			setPlayState(audioPlayerBinder.isPlaying());
		} else if (AudioPlayerService.ACTION_CANCEL.equals(action)) {
			seekBar.removeCallbacks(AudioPlayerActivity.this);
			finish();
		}
	});

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		audioPlayerBinder = (AudioPlayerService.Binder) service;
		seekBar.removeCallbacks(this);
		textView.setText(audioPlayerBinder.getFileName());
		seekBar.setMax(audioPlayerBinder.getDuration());
		setPlayState(audioPlayerBinder.isPlaying());
		run();
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		audioPlayerBinder = null;
		finish();
	}

	@Override
	public void onClick(View v) {
		audioPlayerBinder.togglePlayback();
	}

	private void setPlayState(boolean playing) {
		button.setImageResource(ResourceUtils.getResourceId(context, playing ? R.attr.buttonPause
				: R.attr.buttonPlay, 0));
	}

	@Override
	protected void onStart() {
		super.onStart();
		alertDialog.show();
		seekBar.removeCallbacks(this);
		run();
	}

	@Override
	protected void onStop() {
		super.onStop();
		alertDialog.dismiss();
		seekBar.removeCallbacks(this);
	}

	@Override
	protected void onFinish() {
		super.onFinish();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(audioPlayerReceiver);
		if (audioPlayerBinder != null) {
			unbindService(this);
		}
	}

	@Override
	public void run() {
		if (audioPlayerBinder != null) {
			if (!tracking) {
				seekBar.setProgress(audioPlayerBinder.getPosition());
			}
			seekBar.postDelayed(this, 500);
		}
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		seekBar.removeCallbacks(this);
		finish();
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		seekBar.removeCallbacks(this);
		audioPlayerBinder.stop();
		unbindService(this);
		audioPlayerBinder = null;
		finish();
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if (fromUser) {
			audioPlayerBinder.seekTo(progress);
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		tracking = true;
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		tracking = false;
	}
}
