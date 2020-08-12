package com.mishiranu.dashchan.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import androidx.annotation.RequiresApi;
import com.mishiranu.dashchan.C;

public class AudioFocus {
	public enum Change {LOSS, LOSS_TRANSIENT, GAIN}

	public interface Callback {
		void onChange(Change change);
	}

	private final AudioManager audioManager;
	private final AudioManager.OnAudioFocusChangeListener listener;
	private final Implementation implementation;

	public AudioFocus(Context context, Callback callback) {
		audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		listener = focusChange -> {
			if (acquired) {
				Change change = null;
				switch (focusChange) {
					case AudioManager.AUDIOFOCUS_LOSS: {
						release();
						change = Change.LOSS;
						break;
					}
					case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: {
						change = Change.LOSS_TRANSIENT;
						break;
					}
					case AudioManager.AUDIOFOCUS_GAIN: {
						change = Change.GAIN;
						break;
					}
				}
				if (change != null) {
					callback.onChange(change);
				}
			}
		};
		if (C.API_OREO) {
			implementation = new Implementation26();
		} else {
			implementation = new Implementation8();
		}
	}

	private boolean acquired = false;

	public boolean acquire() {
		if (!acquired && implementation.acquire()) {
			acquired = true;
			return true;
		}
		return acquired;
	}

	public void release() {
		if (acquired) {
			acquired = false;
			implementation.release();
		}
	}

	private interface Implementation {
		boolean acquire();
		void release();
	}

	@RequiresApi(Build.VERSION_CODES.O)
	private class Implementation26 implements Implementation {
		private final AudioFocusRequest request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
				.setAudioAttributes(new AudioAttributes.Builder()
						.setLegacyStreamType(AudioManager.STREAM_MUSIC).build())
				.setOnAudioFocusChangeListener(listener).build();

		@Override
		public boolean acquire() {
			return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
		}

		@Override
		public void release() {
			audioManager.abandonAudioFocusRequest(request);
		}
	}

	@SuppressWarnings("deprecation")
	private class Implementation8 implements Implementation {
		@Override
		public boolean acquire() {
			return audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
					== AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
		}

		@Override
		public void release() {
			audioManager.abandonAudioFocus(listener);
		}
	}
}
