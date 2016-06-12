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

package com.mishiranu.dashchan.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

public class CaptchaController implements View.OnClickListener, View.OnLongClickListener,
		TextView.OnEditorActionListener
{
	public static enum CaptchaViewType {LOADING, IMAGE, SKIP, ERROR};
	
	private final Callback mCallback;
	
	private boolean mApplyHeight;
	private View mBlockParentView;
	private View mBlockView;
	private View mSkipBlockView;
	private TextView mSkipTextView;
	private View mLoadingView;
	private ImageView mImageView;
	private View mInputParentView;
	private EditText mInputView;
	private View mLoadButton;
	private View mRefreshButton;
	
	private ChanConfiguration.Captcha.Input mCaptchaInput;
	
	public static interface Callback
	{
		public void onRefreshCapctha(boolean forceRefresh);
		public void onConfirmCaptcha();
	}
	
	public CaptchaController(Callback callback)
	{
		mCallback = callback;
	}
	
	public void setupViews(View container, View inputParentView, EditText inputView, boolean applyHeight,
			ChanConfiguration.Captcha captcha)
	{
		mApplyHeight = applyHeight;
		mBlockParentView = container.findViewById(R.id.captcha_block_parent);
		mBlockView = container.findViewById(R.id.captcha_block);
		mImageView = (ImageView) container.findViewById(R.id.captcha_image);
		mLoadingView = container.findViewById(R.id.captcha_loading);
		mSkipBlockView = container.findViewById(R.id.captcha_skip_block);
		mSkipTextView = (TextView) container.findViewById(R.id.captcha_skip_text);
		mInputParentView = inputParentView;
		mInputView = inputView;
		mLoadButton = container.findViewById(R.id.captcha_load_button);
		mRefreshButton = container.findViewById(R.id.refresh_button);
		if (C.API_LOLLIPOP)
		{
			mSkipTextView.setAllCaps(true);
			mSkipTextView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
			mSkipTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
		}
		updateCaptchaHeight(false);
		mCaptchaInput = captcha.input;
		if (mCaptchaInput == null) mCaptchaInput = ChanConfiguration.Captcha.Input.ALL;
		updateCaptchaInput(mCaptchaInput);
		mInputView.setFilters(new InputFilter[] {new InputFilter.LengthFilter(50)});
		mInputView.setOnEditorActionListener(this);
		mLoadButton.setOnClickListener(this);
		mBlockParentView.setOnClickListener(this);
		mBlockParentView.setOnLongClickListener(this);
		mRefreshButton.setOnClickListener(this);
		mRefreshButton.setOnLongClickListener(this);
		if (mInputParentView != null) mInputParentView.setOnClickListener(this);
	}
	
	private void updateCaptchaInput(ChanConfiguration.Captcha.Input input)
	{
		switch (input)
		{
			case ALL:
			{
				mInputView.setInputType(InputType.TYPE_CLASS_TEXT);
				break;
			}
			case LATIN:
			{
				mInputView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
				break;
			}
			case NUMERIC:
			{
				mInputView.setInputType(InputType.TYPE_CLASS_NUMBER);
				break;
			}
		}
	}
	
	private void updateCaptchaHeight(boolean large)
	{
		if (mApplyHeight)
		{
			float density = ResourceUtils.obtainDensity(mInputView);
			int height = (int) ((Preferences.isHugeCaptcha() || large ? 96f : 48f) * density);
			ViewGroup.LayoutParams layoutParams = mBlockView.getLayoutParams();
			if (height != layoutParams.height)
			{
				layoutParams.height = height;
				mBlockView.requestLayout();
			}
		}
	}
	
	@Override
	public void onClick(View v)
	{
		if (v == mLoadButton)
		{
			mCallback.onRefreshCapctha(true);
		}
		else if (v == mBlockParentView || v == mRefreshButton)
		{
			mCallback.onRefreshCapctha(false);
		}
		else if (mInputParentView != null && v == mInputParentView)
		{
			mInputView.requestFocus();
			InputMethodManager inputMethodManager = (InputMethodManager) v.getContext()
					.getSystemService(Context.INPUT_METHOD_SERVICE);
			if (inputMethodManager != null)
			{
				inputMethodManager.showSoftInput(mInputView, InputMethodManager.SHOW_IMPLICIT);
			}
		}
	}
	
	@Override
	public boolean onLongClick(View v)
	{
		if (v == mBlockParentView || v == mRefreshButton)
		{
			mCallback.onRefreshCapctha(true);
			return true;
		}
		return false;
	}
	
	@Override
	public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
	{
		mCallback.onConfirmCaptcha();
		return true;
	}
	
	public boolean showCaptcha(ChanPerformer.CaptchaState captchaState, ChanConfiguration.Captcha.Input input,
			Bitmap image, boolean large, boolean blackAndWhite, boolean invertColors)
	{
		switch (captchaState)
		{
			case CAPTCHA:
			{
				mImageView.setImageBitmap(image);
				mImageView.setColorFilter(invertColors ? GraphicsUtils.INVERT_FILTER : null);
				switchToCaptchaView(CaptchaViewType.IMAGE, input, large);
				return true;
			}
			case SKIP:
			case NEED_LOAD:
			{
				boolean needLoad = captchaState == ChanPerformer.CaptchaState.NEED_LOAD;
				mSkipTextView.setText(needLoad ? R.string.action_load_captcha
						: R.string.message_can_skip_captcha);
				mLoadButton.setVisibility(View.GONE);
				switchToCaptchaView(CaptchaViewType.SKIP, null, false);
				return !needLoad;
			}
			case PASS:
			{
				mSkipTextView.setText(R.string.message_captcha_pass_allowed);
				mLoadButton.setVisibility(View.VISIBLE);
				switchToCaptchaView(CaptchaViewType.SKIP, null, false);
				return true;
			}
		}
		return true;
	}
	
	public void showError()
	{
		mImageView.setImageResource(android.R.color.transparent);
		switchToCaptchaView(CaptchaViewType.ERROR, null, false);
	}
	
	public void showLoading()
	{
		mInputView.setText(null);
		switchToCaptchaView(CaptchaViewType.LOADING, null, false);
	}
	
	private void switchToCaptchaView(CaptchaViewType captchaViewType, ChanConfiguration.Captcha.Input input,
			boolean large)
	{
		switch (captchaViewType)
		{
			case LOADING:
			{
				mBlockView.setVisibility(View.VISIBLE);
				mImageView.setVisibility(View.GONE);
				mImageView.setImageResource(android.R.color.transparent);
				mLoadingView.setVisibility(View.VISIBLE);
				mSkipBlockView.setVisibility(View.GONE);
				mInputView.setEnabled(true);
				updateCaptchaHeight(false);
				break;
			}
			case ERROR:
			{
				mBlockView.setVisibility(View.VISIBLE);
				mImageView.setVisibility(View.VISIBLE);
				mLoadingView.setVisibility(View.GONE);
				mSkipBlockView.setVisibility(View.GONE);
				mInputView.setEnabled(true);
				updateCaptchaHeight(false);
				break;
			}
			case IMAGE:
			{
				mBlockView.setVisibility(View.VISIBLE);
				mImageView.setVisibility(View.VISIBLE);
				mLoadingView.setVisibility(View.GONE);
				mSkipBlockView.setVisibility(View.GONE);
				mInputView.setEnabled(true);
				updateCaptchaInput(input != null ? input : mCaptchaInput);
				updateCaptchaHeight(large);
				break;
			}
			case SKIP:
			{
				mBlockView.setVisibility(View.INVISIBLE);
				mImageView.setVisibility(View.VISIBLE);
				mImageView.setImageResource(android.R.color.transparent);
				mLoadingView.setVisibility(View.GONE);
				mSkipBlockView.setVisibility(View.VISIBLE);
				mInputView.setEnabled(false);
				updateCaptchaHeight(false);
				break;
			}
		}
	}
	
	public void setText(String text)
	{
		mInputView.setText(text);
	}
	
	public String getInput()
	{
		return mInputView.getText().toString();
	}
}