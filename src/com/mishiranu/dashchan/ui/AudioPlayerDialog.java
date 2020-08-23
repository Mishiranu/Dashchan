package com.mishiranu.dashchan.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.service.AudioPlayerService;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ThemeEngine;

public class AudioPlayerDialog extends DialogFragment {
	private TextView textView;
	private SeekBar seekBar;
	private ImageButton button;

	private boolean tracking = false;
	private boolean shouldCancel = false;

	private final AudioPlayerService.Callback callback = new AudioPlayerService.Callback() {
		@Override
		public void onTogglePlayback() {
			updatePlayState();
		}

		@Override
		public void onCancel() {
			handleCancel();
		}
	};

	private AudioPlayerService.Binder audioPlayerBinder;
	private final ServiceConnection audioPlayerConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName componentName, IBinder binder) {
			audioPlayerBinder = (AudioPlayerService.Binder) binder;
			audioPlayerBinder.registerCallback(callback);
			if (audioPlayerBinder.isRunning()) {
				seekBar.removeCallbacks(seekBarUpdate);
				textView.setText(audioPlayerBinder.getFileName());
				seekBar.setMax(audioPlayerBinder.getDuration());
				updatePlayState();
				seekBarUpdate.run();
			} else {
				handleCancel();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			if (audioPlayerBinder != null) {
				audioPlayerBinder.unregisterCallback(callback);
				audioPlayerBinder = null;
			}
		}
	};

	private final Runnable seekBarUpdate = new Runnable() {
		@Override
		public void run() {
			if (audioPlayerBinder != null) {
				if (!tracking) {
					seekBar.setProgress(audioPlayerBinder.getPosition());
				}
				seekBar.postDelayed(this, 500);
			}
		}
	};

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Context context = requireContext();
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
		tracking = false;
		seekBar = new SeekBar(context);
		ThemeEngine.applyStyle(seekBar);
		horizontal.addView(seekBar, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
		seekBar.setPadding((int) (8f * density), 0, (int) (16f * density), 0);
		seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (audioPlayerBinder != null && fromUser) {
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
		});
		button = new ImageButton(context);
		horizontal.addView(button, (int) (48f * density), (int) (48f * density));
		if (C.API_LOLLIPOP) {
			button.setImageTintList(ResourceUtils.getColorStateList(button.getContext(),
					android.R.attr.textColorPrimary));
		}
		button.setBackgroundResource(ResourceUtils.getResourceId(context,
				android.R.attr.listChoiceBackgroundIndicator, 0));
		updatePlayState();
		button.setOnClickListener(v -> {
			if (audioPlayerBinder != null) {
				audioPlayerBinder.togglePlayback();
			}
		});
		AlertDialog dialog = new AlertDialog.Builder(context)
				.setView(linearLayout)
				.setPositiveButton(R.string.action_stop, (d, w) -> {
					if (audioPlayerBinder != null) {
						audioPlayerBinder.stop();
					}
				})
				.create();
		dialog.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		requireContext().bindService(new Intent(requireContext(), AudioPlayerService.class),
				audioPlayerConnection, Context.BIND_AUTO_CREATE);
		return dialog;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		if (audioPlayerBinder != null) {
			audioPlayerBinder.unregisterCallback(callback);
			audioPlayerBinder = null;
			requireContext().unbindService(audioPlayerConnection);
		}
		if (seekBar != null) {
			seekBar.removeCallbacks(seekBarUpdate);
		}
		textView = null;
		seekBar = null;
		button = null;
	}

	@Override
	public void onResume() {
		super.onResume();

		if (shouldCancel) {
			shouldCancel = false;
			handleCancel();
		}
	}

	private void handleCancel() {
		if (isResumed()) {
			dismiss();
		} else {
			shouldCancel = false;
		}
	}

	private void updatePlayState() {
		boolean playing = audioPlayerBinder != null && audioPlayerBinder.isPlaying();
		button.setImageResource(ResourceUtils.getResourceId(requireContext(),
				playing ? R.attr.iconButtonPause : R.attr.iconButtonPlay, 0));
	}
}
