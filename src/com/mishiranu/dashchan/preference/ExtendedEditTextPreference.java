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

public class ExtendedEditTextPreference extends EditTextPreference
{
	private TextView mDescriptionView;
	private CharSequence mNeutralButtonText;
	private DialogInterface.OnClickListener mNeutralButtonListener;
	private boolean mNeutralButtonCloseDialog;
	
	public ExtendedEditTextPreference(Context context)
	{
		super(context);
	}
	
	public void setDescription(CharSequence description)
	{
		if (!StringUtils.isEmpty(description) && mDescriptionView == null)
		{
			EditText editText = getEditText();
			float density = ResourceUtils.obtainDensity(getContext());
			mDescriptionView = new TextView(getContext(), null, android.R.attr.textAppearanceListItem);
			mDescriptionView.setPadding(editText.getPaddingLeft(), 0, editText.getPaddingRight(), (int) (8f * density));
		}
		if (mDescriptionView != null) mDescriptionView.setText(description);
	}
	
	public void setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener, boolean closeDialog)
	{
		mNeutralButtonText = text;
		mNeutralButtonListener = listener;
		mNeutralButtonCloseDialog = closeDialog;
	}
	
	@Override
	protected void onBindDialogView(View view)
	{
		super.onBindDialogView(view);
		ViewGroup parent = (ViewGroup) getEditText().getParent();
		ViewGroup oldParent = mDescriptionView != null ? (ViewGroup) mDescriptionView.getParent() : null;
		if (oldParent != parent)
		{
			if (oldParent != null) oldParent.removeView(mDescriptionView);
			if (mDescriptionView != null && mDescriptionView.getText().length() > 0)
			{
				parent.addView(mDescriptionView, parent.indexOfChild(getEditText()));
			}
		}
	}
	
	@Override
	protected void onPrepareDialogBuilder(AlertDialog.Builder builder)
	{
		super.onPrepareDialogBuilder(builder);
		if (mNeutralButtonText != null) builder.setNeutralButton(mNeutralButtonText, this);
	}
	
	@Override
	protected void showDialog(Bundle state)
	{
		super.showDialog(state);
		if (mNeutralButtonText != null && !mNeutralButtonCloseDialog)
		{
			Button button = ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_NEUTRAL);
			if (button != null)
			{
				button.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						ExtendedEditTextPreference.this.onClick(getDialog(), AlertDialog.BUTTON_NEUTRAL);
					}
				});
			}
		}
	}
	
	@Override
	public void onClick(DialogInterface dialog, int which)
	{
		super.onClick(dialog, which);
		if (which == AlertDialog.BUTTON_NEUTRAL)
		{
			if (mNeutralButtonListener != null) mNeutralButtonListener.onClick(dialog, AlertDialog.BUTTON_NEUTRAL);
		}
	}
}