package com.mishiranu.dashchan.ui.posting.text;

import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ErrorEditTextSetter;

public class NameEditWatcher implements TextWatcher
{
	private final boolean mWatchTripcodeWarning;
	private final EditText mNameView;
	private final TextView mTripcodeWarning;

	private final Runnable mLayoutCallback;

	public NameEditWatcher(boolean watchTripcodeWarning, EditText nameView, TextView tripcodeWarning,
			Runnable layoutCallback)
	{
		mWatchTripcodeWarning = watchTripcodeWarning;
		mNameView = nameView;
		mTripcodeWarning = tripcodeWarning;
		mLayoutCallback = layoutCallback;
	}

	private boolean mError = false;

	private ForegroundColorSpan mTripcodeSpan;
	private ErrorEditTextSetter mErrorSetter;

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after)
	{

	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count)
	{

	}

	@Override
	public void afterTextChanged(Editable s)
	{
		int index = s.toString().indexOf('#');
		if (mWatchTripcodeWarning)
		{
			boolean error = index >= 0;
			if (mError != error)
			{
				if (C.API_LOLLIPOP)
				{
					if (mErrorSetter == null) mErrorSetter = new ErrorEditTextSetter(mNameView);
					mErrorSetter.setError(error);
				}
				mTripcodeWarning.setVisibility(error ? View.VISIBLE : View.GONE);
				mLayoutCallback.run();
				mError = error;
			}
		}
		if (mTripcodeSpan != null) s.removeSpan(mTripcodeSpan);
		if (index >= 0)
		{
			if (mTripcodeSpan == null)
			{
				mTripcodeSpan = new ForegroundColorSpan(ResourceUtils.getColor(mNameView.getContext(),
						mWatchTripcodeWarning ? R.attr.colorTextError : R.attr.colorTextTripcode));
			}
			s.setSpan(mTripcodeSpan, index, s.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
	}
}