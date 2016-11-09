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

public class CaptchaForm implements View.OnClickListener, View.OnLongClickListener,
		TextView.OnEditorActionListener {
	public enum CaptchaViewType {LOADING, IMAGE, SKIP, ERROR}

	private final Callback callback;

	private boolean applyHeight;
	private View blockParentView;
	private View blockView;
	private View skipBlockView;
	private TextView skipTextView;
	private View loadingView;
	private ImageView imageView;
	private View inputParentView;
	private EditText inputView;
	private View loadButton;
	private View refreshButton;

	private ChanConfiguration.Captcha.Input captchaInput;

	public interface Callback {
		public void onRefreshCapctha(boolean forceRefresh);
		public void onConfirmCaptcha();
	}

	public CaptchaForm(Callback callback) {
		this.callback = callback;
	}

	public void setupViews(View container, View inputParentView, EditText inputView, boolean applyHeight,
			ChanConfiguration.Captcha captcha) {
		this.applyHeight = applyHeight;
		blockParentView = container.findViewById(R.id.captcha_block_parent);
		blockView = container.findViewById(R.id.captcha_block);
		imageView = (ImageView) container.findViewById(R.id.captcha_image);
		loadingView = container.findViewById(R.id.captcha_loading);
		skipBlockView = container.findViewById(R.id.captcha_skip_block);
		skipTextView = (TextView) container.findViewById(R.id.captcha_skip_text);
		this.inputParentView = inputParentView;
		this.inputView = inputView;
		loadButton = container.findViewById(R.id.captcha_load_button);
		refreshButton = container.findViewById(R.id.refresh_button);
		if (C.API_LOLLIPOP) {
			skipTextView.setAllCaps(true);
			skipTextView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
			skipTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
		}
		updateCaptchaHeight(false);
		captchaInput = captcha.input;
		if (captchaInput == null) {
			captchaInput = ChanConfiguration.Captcha.Input.ALL;
		}
		updateCaptchaInput(captchaInput);
		inputView.setFilters(new InputFilter[] {new InputFilter.LengthFilter(50)});
		inputView.setOnEditorActionListener(this);
		loadButton.setOnClickListener(this);
		blockParentView.setOnClickListener(this);
		blockParentView.setOnLongClickListener(this);
		refreshButton.setOnClickListener(this);
		refreshButton.setOnLongClickListener(this);
		if (inputParentView != null) {
			inputParentView.setOnClickListener(this);
		}
	}

	private void updateCaptchaInput(ChanConfiguration.Captcha.Input input) {
		switch (input) {
			case ALL: {
				inputView.setInputType(InputType.TYPE_CLASS_TEXT);
				break;
			}
			case LATIN: {
				inputView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
				break;
			}
			case NUMERIC: {
				inputView.setInputType(InputType.TYPE_CLASS_NUMBER);
				break;
			}
		}
	}

	private void updateCaptchaHeight(boolean large) {
		if (applyHeight) {
			float density = ResourceUtils.obtainDensity(inputView);
			int height = (int) ((Preferences.isHugeCaptcha() || large ? 96f : 48f) * density);
			ViewGroup.LayoutParams layoutParams = blockView.getLayoutParams();
			if (height != layoutParams.height) {
				layoutParams.height = height;
				blockView.requestLayout();
			}
		}
	}

	@Override
	public void onClick(View v) {
		if (v == loadButton) {
			callback.onRefreshCapctha(true);
		} else if (v == blockParentView || v == refreshButton) {
			callback.onRefreshCapctha(false);
		} else if (inputParentView != null && v == inputParentView) {
			inputView.requestFocus();
			InputMethodManager inputMethodManager = (InputMethodManager) v.getContext()
					.getSystemService(Context.INPUT_METHOD_SERVICE);
			if (inputMethodManager != null) {
				inputMethodManager.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT);
			}
		}
	}

	@Override
	public boolean onLongClick(View v) {
		if (v == blockParentView || v == refreshButton) {
			callback.onRefreshCapctha(true);
			return true;
		}
		return false;
	}

	@Override
	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		callback.onConfirmCaptcha();
		return true;
	}

	public void showCaptcha(ChanPerformer.CaptchaState captchaState, ChanConfiguration.Captcha.Input input,
			Bitmap image, boolean large, boolean invertColors) {
		switch (captchaState) {
			case CAPTCHA: {
				imageView.setImageBitmap(image);
				imageView.setColorFilter(invertColors ? GraphicsUtils.INVERT_FILTER : null);
				switchToCaptchaView(CaptchaViewType.IMAGE, input, large);
				break;
			}
			case SKIP:
			case NEED_LOAD: {
				boolean needLoad = captchaState == ChanPerformer.CaptchaState.NEED_LOAD;
				skipTextView.setText(needLoad ? R.string.action_load_captcha
						: R.string.message_can_skip_captcha);
				loadButton.setVisibility(View.GONE);
				switchToCaptchaView(CaptchaViewType.SKIP, null, false);
				break;
			}
			case PASS: {
				skipTextView.setText(R.string.message_captcha_pass_allowed);
				loadButton.setVisibility(View.VISIBLE);
				switchToCaptchaView(CaptchaViewType.SKIP, null, false);
				break;
			}
		}
	}

	public void showError() {
		imageView.setImageResource(android.R.color.transparent);
		switchToCaptchaView(CaptchaViewType.ERROR, null, false);
	}

	public void showLoading() {
		inputView.setText(null);
		switchToCaptchaView(CaptchaViewType.LOADING, null, false);
	}

	private void switchToCaptchaView(CaptchaViewType captchaViewType, ChanConfiguration.Captcha.Input input,
			boolean large) {
		switch (captchaViewType) {
			case LOADING: {
				blockView.setVisibility(View.VISIBLE);
				imageView.setVisibility(View.GONE);
				imageView.setImageResource(android.R.color.transparent);
				loadingView.setVisibility(View.VISIBLE);
				skipBlockView.setVisibility(View.GONE);
				inputView.setEnabled(true);
				updateCaptchaHeight(false);
				break;
			}
			case ERROR: {
				blockView.setVisibility(View.VISIBLE);
				imageView.setVisibility(View.VISIBLE);
				loadingView.setVisibility(View.GONE);
				skipBlockView.setVisibility(View.GONE);
				inputView.setEnabled(true);
				updateCaptchaHeight(false);
				break;
			}
			case IMAGE: {
				blockView.setVisibility(View.VISIBLE);
				imageView.setVisibility(View.VISIBLE);
				loadingView.setVisibility(View.GONE);
				skipBlockView.setVisibility(View.GONE);
				inputView.setEnabled(true);
				updateCaptchaInput(input != null ? input : captchaInput);
				updateCaptchaHeight(large);
				break;
			}
			case SKIP: {
				blockView.setVisibility(View.INVISIBLE);
				imageView.setVisibility(View.VISIBLE);
				imageView.setImageResource(android.R.color.transparent);
				loadingView.setVisibility(View.GONE);
				skipBlockView.setVisibility(View.VISIBLE);
				inputView.setEnabled(false);
				updateCaptchaHeight(false);
				break;
			}
		}
	}

	public void setText(String text) {
		inputView.setText(text);
	}

	public String getInput() {
		return inputView.getText().toString();
	}
}