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

package com.mishiranu.dashchan.app;

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
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.app.service.AudioPlayerService;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.ResourceUtils;

public class AudioPlayerActivity extends StateActivity implements Runnable, SeekBar.OnSeekBarChangeListener,
		DialogInterface.OnCancelListener, DialogInterface.OnClickListener, View.OnClickListener, ServiceConnection
{
	private Context mContext;
	private TextView mTextView;
	private SeekBar mSeekBar;
	private ImageButton mButton;
	private AlertDialog mAlertDialog;
	
	private boolean mTracking = false;
	
	private AudioPlayerService.AudioPlayerBinder mBinder;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		mContext = new ContextThemeWrapper(this, Preferences.getThemeResource());
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		float density = ResourceUtils.obtainDensity(this);
		LinearLayout linearLayout = new LinearLayout(mContext);
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		int padding = getResources().getDimensionPixelSize(R.dimen.dialog_padding_view);
		linearLayout.setPadding(padding, padding, padding, C.API_LOLLIPOP ? (int) (8f * density) : padding);
		mTextView = new TextView(mContext, null, android.R.attr.textAppearanceListItem);
		linearLayout.addView(mTextView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		mTextView.setPadding(0, 0, 0, 0);
		mTextView.setEllipsize(TextUtils.TruncateAt.END);
		mTextView.setSingleLine(true);
		LinearLayout horizontal = new LinearLayout(mContext);
		horizontal.setOrientation(LinearLayout.HORIZONTAL);
		horizontal.setGravity(Gravity.CENTER_VERTICAL);
		horizontal.setPadding(0, (int) (16f * density), 0, 0);
		linearLayout.addView(horizontal, LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		mSeekBar = new SeekBar(mContext);
		horizontal.addView(mSeekBar, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
		mSeekBar.setPadding((int) (8f * density), 0, (int) (16f * density), 0);
		mSeekBar.setOnSeekBarChangeListener(this);
		mButton = new ImageButton(mContext);
		horizontal.addView(mButton, (int) (48f * density), (int) (48f * density));
		mButton.setBackgroundResource(ResourceUtils.getResourceId(mContext,
				android.R.attr.listChoiceBackgroundIndicator, 0));
		setPlayState(false);
		mButton.setOnClickListener(this);
		mAlertDialog = new AlertDialog.Builder(mContext).setView(linearLayout).setOnCancelListener(this)
				.setPositiveButton(R.string.action_stop, this).show();
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(AudioPlayerService.ACTION_TOGGLE);
		intentFilter.addAction(AudioPlayerService.ACTION_CANCEL);
		LocalBroadcastManager.getInstance(this).registerReceiver(mAudioPlayerReceiver, intentFilter);
		bindService(new Intent(this, AudioPlayerService.class), this, 0);
	}
	
	private final BroadcastReceiver mAudioPlayerReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			if (AudioPlayerService.ACTION_TOGGLE.equals(action))
			{
				setPlayState(mBinder.isPlaying());
			}
			else if (AudioPlayerService.ACTION_CANCEL.equals(action))
			{
				mSeekBar.removeCallbacks(AudioPlayerActivity.this);
				finish();
			}
		}
	};
	
	@Override
	public void onServiceConnected(ComponentName name, IBinder service)
	{
		mBinder = (AudioPlayerService.AudioPlayerBinder) service;
		mSeekBar.removeCallbacks(this);
		mTextView.setText(mBinder.getFileName());
		mSeekBar.setMax(mBinder.getDuration());
		setPlayState(mBinder.isPlaying());
		run();
	}
	
	@Override
	public void onServiceDisconnected(ComponentName name)
	{
		mBinder = null;
		finish();
	}
	
	@Override
	public void onClick(View v)
	{
		mBinder.togglePlayback();
	}
	
	private void setPlayState(boolean playing)
	{
		mButton.setImageResource(ResourceUtils.getResourceId(mContext, playing ? R.attr.buttonPause
				: R.attr.buttonPlay, 0));
	}
	
	@Override
	protected void onStart()
	{
		super.onStart();
		mAlertDialog.show();
		mSeekBar.removeCallbacks(this);
		run();
	}
	
	@Override
	protected void onStop()
	{
		super.onStop();
		mAlertDialog.dismiss();
		mSeekBar.removeCallbacks(this);
	}
	
	@Override
	protected void onFinish()
	{
		super.onFinish();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mAudioPlayerReceiver);
		if (mBinder != null) unbindService(this);
	}
	
	@Override
	public void run()
	{
		if (mBinder != null)
		{
			if (!mTracking) mSeekBar.setProgress(mBinder.getPosition());
			mSeekBar.postDelayed(this, 500);
		}
	}

	@Override
	public void onCancel(DialogInterface dialog)
	{
		mSeekBar.removeCallbacks(this);
		finish();
	}
	
	@Override
	public void onClick(DialogInterface dialog, int which)
	{
		mSeekBar.removeCallbacks(this);
		mBinder.stop();
		unbindService(this);
		mBinder = null;
		finish();
	}
	
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		if (fromUser) mBinder.seekTo(progress);
	}
	
	@Override
	public void onStartTrackingTouch(SeekBar seekBar)
	{
		mTracking = true;
	}
	
	@Override
	public void onStopTrackingTouch(SeekBar seekBar)
	{
		mTracking = false;
	}
}