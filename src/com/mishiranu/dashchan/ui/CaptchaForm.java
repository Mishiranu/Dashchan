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
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ReadCaptchaTask;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;

public class CaptchaForm implements View.OnClickListener, View.OnLongClickListener,
		TextView.OnEditorActionListener {
	public enum CaptchaViewType {LOADING, IMAGE, SKIP, SKIP_LOCK, ERROR}

	private final Callback callback;
	private final boolean hideInput;
	private final boolean applyHeight;
	private final View blockParentView;
	private final View blockView;
	private final View skipBlockView;
	private final TextView skipTextView;
	private final View loadingView;
	private final ImageView imageView;
	private final View inputParentView;
	private final EditText inputView;
	private final View cancelView;

	private ChanConfiguration.Captcha.Input captchaInput;

	public interface Callback {
		void onRefreshCaptcha(boolean forceRefresh);
		void onConfirmCaptcha();
	}

	public CaptchaForm(Callback callback, boolean hideInput, boolean applyHeight,
			View container, View inputParentView, EditText inputView, ChanConfiguration.Captcha captcha) {
		this.callback = callback;
		this.hideInput = hideInput;
		this.applyHeight = applyHeight;
		blockParentView = container.findViewById(R.id.captcha_block_parent);
		blockView = container.findViewById(R.id.captcha_block);
		imageView = container.findViewById(R.id.captcha_image);
		loadingView = container.findViewById(R.id.captcha_loading);
		skipBlockView = container.findViewById(R.id.captcha_skip_block);
		skipTextView = container.findViewById(R.id.captcha_skip_text);
		this.inputParentView = inputParentView;
		this.inputView = inputView;
		if (hideInput) {
			inputView.setVisibility(View.GONE);
		}
		ImageView cancelView = container.findViewById(R.id.captcha_cancel);
		this.cancelView = cancelView;
		if (C.API_LOLLIPOP) {
			cancelView.setImageTintList(ResourceUtils.getColorStateList(cancelView.getContext(),
					android.R.attr.textColorPrimary));
			skipTextView.setAllCaps(true);
			skipTextView.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
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
		cancelView.setOnClickListener(this);
		blockParentView.setOnClickListener(this);
		blockParentView.setOnLongClickListener(this);
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
		if (v == cancelView) {
			callback.onRefreshCaptcha(true);
		} else if (v == blockParentView && v.isClickable()) {
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
		if (v == blockParentView) {
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

	public void showCaptcha(ReadCaptchaTask.CaptchaState captchaState, ChanConfiguration.Captcha.Input input,
			Bitmap image, boolean large, boolean invertColors) {
		switch (captchaState) {
			case CAPTCHA: {
				imageView.setImageBitmap(image);
				imageView.setColorFilter(invertColors ? GraphicsUtils.INVERT_FILTER : null);
				switchToCaptchaView(CaptchaViewType.IMAGE, input, large);
				break;
			}
			case NEED_LOAD:
			case MAY_LOAD:
			case MAY_LOAD_SOLVING: {
				skipTextView.setText(R.string.load_captcha);
				cancelView.setVisibility(captchaState == ReadCaptchaTask.CaptchaState.MAY_LOAD_SOLVING
						? View.VISIBLE : View.GONE);
				switchToCaptchaView(CaptchaViewType.SKIP, null, false);
				break;
			}
			case SKIP: {
				skipTextView.setText(R.string.captcha_is_not_required);
				cancelView.setVisibility(View.VISIBLE);
				switchToCaptchaView(CaptchaViewType.SKIP_LOCK, null, false);
				break;
			}
			case PASS: {
				skipTextView.setText(R.string.captcha_pass);
				cancelView.setVisibility(View.VISIBLE);
				switchToCaptchaView(CaptchaViewType.SKIP, null, false);
				break;
			}
		}
	}

	public void showError() {
		imageView.setImageResource(android.R.color.transparent);
		skipTextView.setText(R.string.load_captcha);
		cancelView.setVisibility(View.GONE);
		switchToCaptchaView(CaptchaViewType.ERROR, null, false);
	}

	public void showLoading() {
		inputView.setText(null);
		switchToCaptchaView(CaptchaViewType.LOADING, null, false);
	}

	private void setInputEnabled(boolean enabled, boolean switchVisibility) {
		inputView.setEnabled(enabled);
		if (hideInput && switchVisibility) {
			inputView.setVisibility(enabled ? View.VISIBLE : View.GONE);
		}
	}

	private void switchToCaptchaView(CaptchaViewType captchaViewType,
			ChanConfiguration.Captcha.Input input, boolean large) {
		switch (captchaViewType) {
			case LOADING: {
				blockParentView.setClickable(true);
				blockView.setVisibility(View.VISIBLE);
				imageView.setVisibility(View.GONE);
				imageView.setImageResource(android.R.color.transparent);
				loadingView.setVisibility(View.VISIBLE);
				skipBlockView.setVisibility(View.GONE);
				setInputEnabled(false, false);
				updateCaptchaHeight(false);
				break;
			}
			case ERROR: {
				blockParentView.setClickable(true);
				blockView.setVisibility(View.VISIBLE);
				imageView.setVisibility(View.VISIBLE);
				loadingView.setVisibility(View.GONE);
				skipBlockView.setVisibility(View.VISIBLE);
				setInputEnabled(false, false);
				updateCaptchaHeight(false);
				break;
			}
			case IMAGE: {
				blockParentView.setClickable(true);
				blockView.setVisibility(View.VISIBLE);
				imageView.setVisibility(View.VISIBLE);
				loadingView.setVisibility(View.GONE);
				skipBlockView.setVisibility(View.GONE);
				setInputEnabled(true, true);
				updateCaptchaInput(input != null ? input : captchaInput);
				updateCaptchaHeight(large);
				break;
			}
			case SKIP:
			case SKIP_LOCK: {
				blockParentView.setClickable(captchaViewType != CaptchaViewType.SKIP_LOCK);
				blockView.setVisibility(View.INVISIBLE);
				imageView.setVisibility(View.VISIBLE);
				imageView.setImageResource(android.R.color.transparent);
				loadingView.setVisibility(View.GONE);
				skipBlockView.setVisibility(View.VISIBLE);
				setInputEnabled(false, true);
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
