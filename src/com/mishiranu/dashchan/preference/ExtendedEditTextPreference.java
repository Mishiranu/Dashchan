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

package com.mishiranu.dashchan.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import chan.util.StringUtils;

import com.mishiranu.dashchan.util.ResourceUtils;

public class ExtendedEditTextPreference extends EditTextPreference {
	private TextView descriptionView;
	private CharSequence neutralButtonText;
	private DialogInterface.OnClickListener neutralButtonListener;
	private boolean neutralButtonCloseDialog;

	public ExtendedEditTextPreference(Context context) {
		super(context);
	}

	public void setDescription(CharSequence description) {
		if (!StringUtils.isEmpty(description) && descriptionView == null) {
			EditText editText = getEditText();
			float density = ResourceUtils.obtainDensity(getContext());
			descriptionView = new TextView(getContext(), null, android.R.attr.textAppearanceListItem);
			descriptionView.setPadding(editText.getPaddingLeft(), 0, editText.getPaddingRight(), (int) (8f * density));
		}
		if (descriptionView != null) {
			descriptionView.setText(description);
		}
	}

	public void setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener, boolean closeDialog) {
		neutralButtonText = text;
		neutralButtonListener = listener;
		neutralButtonCloseDialog = closeDialog;
	}

	@Override
	protected void onBindDialogView(View view) {
		super.onBindDialogView(view);
		ViewGroup parent = (ViewGroup) getEditText().getParent();
		ViewGroup oldParent = descriptionView != null ? (ViewGroup) descriptionView.getParent() : null;
		if (oldParent != parent) {
			if (oldParent != null) {
				oldParent.removeView(descriptionView);
			}
			if (descriptionView != null && descriptionView.getText().length() > 0) {
				parent.addView(descriptionView, parent.indexOfChild(getEditText()));
			}
		}
	}

	@Override
	protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
		super.onPrepareDialogBuilder(builder);
		if (neutralButtonText != null) {
			builder.setNeutralButton(neutralButtonText, this);
		}
	}

	@Override
	protected void showDialog(Bundle state) {
		super.showDialog(state);
		if (neutralButtonText != null && !neutralButtonCloseDialog) {
			Button button = ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_NEUTRAL);
			if (button != null) {
				button.setOnClickListener(v -> onClick(getDialog(), AlertDialog.BUTTON_NEUTRAL));
			}
		}
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		super.onClick(dialog, which);
		if (which == AlertDialog.BUTTON_NEUTRAL) {
			if (neutralButtonListener != null) {
				neutralButtonListener.onClick(dialog, AlertDialog.BUTTON_NEUTRAL);
			}
		}
	}
}