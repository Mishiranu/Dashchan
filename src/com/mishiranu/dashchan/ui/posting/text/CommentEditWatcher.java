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

public class CommentEditWatcher implements TextWatcher
{
	private final ChanConfiguration.Posting mPostingConfiguration;
	private final EditText mCommentView;
	private final TextView mRemainingCharacters;

	private final Runnable mLayoutCallback;
	private final Runnable mStoreDraftCallback;

	public CommentEditWatcher(ChanConfiguration.Posting posting, EditText commentView, TextView remainingCharacters,
			Runnable layoutCallback, Runnable storeDraftCallback)
	{
		mPostingConfiguration = posting;
		mCommentView = commentView;
		mRemainingCharacters = remainingCharacters;
		mLayoutCallback = layoutCallback;
		mStoreDraftCallback = storeDraftCallback;
	}

	private boolean mShow = false;
	private boolean mError = false;

	private ErrorEditTextSetter mErrorSetter;

	private CharsetEncoder mEncoder;
	private boolean mEncoderReady = false;
	private ByteBuffer mByteBuffer = null;

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after)
	{

	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count)
	{
		int length = 0;
		int maxCommentLength = 0;
		boolean show = false;
		if (mPostingConfiguration != null)
		{
			maxCommentLength = mPostingConfiguration.maxCommentLength;
			int threshold = Math.min(maxCommentLength / 2, 1000);
			length = s.length();
			if (!mEncoderReady)
			{
				mEncoderReady = true;
				String encoding = mPostingConfiguration.maxCommentLengthEncoding;
				if (encoding != null)
				{
					try
					{
						mEncoder = Charset.forName(encoding).newEncoder();
					}
					catch (Exception e)
					{

					}
				}
			}
			if (mEncoder != null)
			{
				int capacity = (int) (Math.max(100, length) * mEncoder.maxBytesPerChar());
				if (mByteBuffer == null || mByteBuffer.capacity() < capacity)
				{
					mByteBuffer = ByteBuffer.allocate(4 * capacity);
				}
				else mByteBuffer.rewind();
				mEncoder.reset();
				mEncoder.encode(CharBuffer.wrap(s), mByteBuffer, true);
				length = mByteBuffer.position();
			}
			show = threshold > 0 && length >= threshold;
		}
		boolean error = show && length > maxCommentLength;
		if (mShow != show)
		{
			mRemainingCharacters.setVisibility(show ? View.VISIBLE : View.GONE);
			mLayoutCallback.run();
			mShow = show;
		}
		if (mError != error || mRemainingCharacters.getText().length() == 0)
		{
			int color = ResourceUtils.getColor(mCommentView.getContext(), error ? R.attr.colorTextError
					: android.R.attr.textColorSecondary);
			mRemainingCharacters.setTextColor(color);
			if (C.API_LOLLIPOP)
			{
				if (mErrorSetter == null) mErrorSetter = new ErrorEditTextSetter(mCommentView);
				mErrorSetter.setError(error);
			}
			mError = error;
		}
		if (show) mRemainingCharacters.setText(length + " / " + maxCommentLength);
		if (before == 0 && count == 1)
		{
			char c = s.charAt(start);
			if (c == '\n' || c == '.') mStoreDraftCallback.run();
		}
	}

	@Override
	public void afterTextChanged(Editable s)
	{

	}
}