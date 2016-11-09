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

package com.mishiranu.dashchan.widget;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.widget.EditText;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ResourceUtils;

public class ErrorEditTextSetter {
	private final EditText editText;
	private boolean error = false;

	private Drawable backgroundNormal;
	private Drawable backgroundError;

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
				backgroundError.mutate().setColorFilter(ResourceUtils.getColor(editText.getContext(),
						R.attr.colorTextError), PorterDuff.Mode.SRC_IN);
			}
			editText.setBackground(error ? backgroundError : backgroundNormal);
		}
	}
}