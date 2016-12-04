package com.mishiranu.dashchan.ui.posting.text;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import chan.content.ChanConfiguration;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ErrorEditTextSetter;

public class CommentEditWatcher implements TextWatcher {
	private ChanConfiguration.Posting postingConfiguration;
	private final EditText commentView;
	private final TextView remainingCharacters;

	private final Runnable layoutCallback;
	private final Runnable storeDraftCallback;

	public CommentEditWatcher(ChanConfiguration.Posting posting, EditText commentView, TextView remainingCharacters,
			Runnable layoutCallback, Runnable storeDraftCallback) {
		this.postingConfiguration = posting;
		this.commentView = commentView;
		this.remainingCharacters = remainingCharacters;
		this.layoutCallback = layoutCallback;
		this.storeDraftCallback = storeDraftCallback;
	}

	public void updateConfiguration(ChanConfiguration.Posting posting) {
		this.postingConfiguration = posting;
	}

	private boolean show = false;
	private boolean error = false;

	private ErrorEditTextSetter errorSetter;

	private CharsetEncoder encoder;
	private boolean encoderReady = false;
	private ByteBuffer byteBuffer = null;

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		int length = 0;
		int maxCommentLength = 0;
		boolean show = false;
		if (postingConfiguration != null) {
			maxCommentLength = postingConfiguration.maxCommentLength;
			int threshold = Math.min(maxCommentLength / 2, 1000);
			length = s.length();
			if (!encoderReady) {
				encoderReady = true;
				String encoding = postingConfiguration.maxCommentLengthEncoding;
				if (encoding != null) {
					try {
						encoder = Charset.forName(encoding).newEncoder();
					} catch (Exception e) {
						// Ignore encoding exceptions
					}
				}
			}
			if (encoder != null) {
				int capacity = (int) (Math.max(100, length) * encoder.maxBytesPerChar());
				if (byteBuffer == null || byteBuffer.capacity() < capacity) {
					byteBuffer = ByteBuffer.allocate(4 * capacity);
				} else {
					byteBuffer.rewind();
				}
				encoder.reset();
				encoder.encode(CharBuffer.wrap(s), byteBuffer, true);
				length = byteBuffer.position();
			}
			show = threshold > 0 && length >= threshold;
		}
		boolean error = show && length > maxCommentLength;
		if (this.show != show) {
			remainingCharacters.setVisibility(show ? View.VISIBLE : View.GONE);
			layoutCallback.run();
			this.show = show;
		}
		if (this.error != error || remainingCharacters.getText().length() == 0) {
			int color = ResourceUtils.getColor(commentView.getContext(), error ? R.attr.colorTextError
					: android.R.attr.textColorSecondary);
			remainingCharacters.setTextColor(color);
			if (C.API_LOLLIPOP) {
				if (errorSetter == null) {
					errorSetter = new ErrorEditTextSetter(commentView);
				}
				errorSetter.setError(error);
			}
			this.error = error;
		}
		if (show) {
			remainingCharacters.setText(length + " / " + maxCommentLength);
		}
		if (before == 0 && count == 1) {
			char c = s.charAt(start);
			if (c == '\n' || c == '.') {
				storeDraftCallback.run();
			}
		}
	}

	@Override
	public void afterTextChanged(Editable s) {}
}