package com.mishiranu.dashchan.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.InputFilter;
import android.text.InputType;
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
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;

public class CaptchaForm implements View.OnClickListener, View.OnLongClickListener,
		TextView.OnEditorActionListener {
	public enum CaptchaViewType {LOADING, IMAGE, SKIP, ERROR}

	private final Callback callback;
	private final boolean applyHeight;
	private final View blockParentView;
	private final View blockView;
	private final View skipBlockView;
	private final TextView skipTextView;
	private final View loadingView;
	private final ImageView imageView;
	private final View inputParentView;
	private final EditText inputView;
	private final ImageView loadButton;
	private final View refreshButton;

	private ChanConfiguration.Captcha.Input captchaInput;

	public interface Callback {
		public void onRefreshCaptcha(boolean forceRefresh);
		public void onConfirmCaptcha();
	}

	public CaptchaForm(Callback callback, View container, View inputParentView, EditText inputView,
			boolean applyHeight, ChanConfiguration.Captcha captcha) {
		this.callback = callback;
		this.applyHeight = applyHeight;
		blockParentView = container.findViewById(R.id.captcha_block_parent);
		blockView = container.findViewById(R.id.captcha_block);
		imageView = container.findViewById(R.id.captcha_image);
		loadingView = container.findViewById(R.id.captcha_loading);
		skipBlockView = container.findViewById(R.id.captcha_skip_block);
		skipTextView = container.findViewById(R.id.captcha_skip_text);
		this.inputParentView = inputParentView;
		this.inputView = inputView;
		loadButton = container.findViewById(R.id.captcha_load_button);
		refreshButton = container.findViewById(R.id.refresh_button);
		if (C.API_LOLLIPOP) {
			loadButton.setImageTintList(ResourceUtils.getColorStateList(loadButton.getContext(),
					android.R.attr.textColorPrimary));
			skipTextView.setAllCaps(true);
			skipTextView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
			ViewUtils.setTextSizeScaled(skipTextView, 12);
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
			callback.onRefreshCaptcha(true);
		} else if (v == blockParentView || v == refreshButton) {
			callback.onRefreshCaptcha(false);
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
			callback.onRefreshCaptcha(true);
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
				skipTextView.setText(needLoad ? R.string.load_captcha : R.string.captcha_is_not_required);
				loadButton.setVisibility(View.GONE);
				switchToCaptchaView(CaptchaViewType.SKIP, null, false);
				break;
			}
			case PASS: {
				skipTextView.setText(R.string.captcha_pass_is_allowed);
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
