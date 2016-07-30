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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.AttributeSet;
import android.widget.EditText;

// Removes spans on paste event.
public class SafePasteEditText extends EditText
{
	public SafePasteEditText(Context context)
	{
		super(context);
	}
	
	public SafePasteEditText(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}
	
	public SafePasteEditText(Context context, AttributeSet attrs, int defStyleAttr)
	{
		super(context, attrs, defStyleAttr);
	}
	
	@SuppressWarnings("unused")
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public SafePasteEditText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes)
	{
		super(context, attrs, defStyleAttr, defStyleRes);
	}
	
	private static final InputFilter SPAN_FILTER = (source, start, end, dest, dstart, dend) ->
	{
		return source instanceof Spanned ? source.toString() : source;
	};
	
	@Override
	public boolean onTextContextMenuItem(int id)
	{
		if (id == android.R.id.paste)
		{
			Editable editable = getEditableText();
			InputFilter[] filters = editable.getFilters();
			InputFilter[] tempFilters = new InputFilter[filters != null ? filters.length + 1 : 1];
			if (filters != null) System.arraycopy(filters, 0, tempFilters, 1, filters.length);
			tempFilters[0] = SPAN_FILTER;
			editable.setFilters(tempFilters);
			try
			{
				return super.onTextContextMenuItem(id);
			}
			finally
			{
				editable.setFilters(filters);
			}
		}
		else return super.onTextContextMenuItem(id);
	}
}