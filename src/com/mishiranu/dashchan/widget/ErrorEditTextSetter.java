package com.mishiranu.dashchan.widget;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.widget.EditText;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ResourceUtils;

public class ErrorEditTextSetter {
	private final EditText editText;
	private boolean error = false;

	private Drawable backgroundNormal;
	private Drawable backgroundError;
	private PorterDuffColorFilter colorFilter;

	public ErrorEditTextSetter(EditText editText) {
		this.editText = editText;
	}

	public void setError(boolean error) {
		if (this.error != error) {
			this.error = error;
			if (backgroundNormal == null) {
				backgroundNormal = ResourceUtils.getDrawable(editText.getContext(),
						android.R.attr.editTextBackground, 0);
				backgroundError = ResourceUtils.getDrawable(editText.getContext(),
						android.R.attr.editTextBackground, 0);
				if (colorFilter == null) {
					colorFilter = new PorterDuffColorFilter(ResourceUtils.getColor(editText.getContext(),
							R.attr.colorTextError), PorterDuff.Mode.SRC_IN);
				}
				backgroundError.mutate().setColorFilter(colorFilter);
			}
			editText.setBackground(error ? backgroundError : backgroundNormal);
		}
	}
}
