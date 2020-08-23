package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.ResourceUtils;
import java.util.Collection;

public class DropdownView extends FrameLayout {
	private final Spinner spinner;

	public DropdownView(Context context) {
		this(context, null);
	}

	public DropdownView(Context context, AttributeSet attrs) {
		super(context, attrs);

		spinner = new Spinner(context);
		spinner.setId(android.R.id.edit);
		spinner.setPadding(0, 0, 0, 0);
		addView(spinner, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		LayoutParams layoutParams = (LayoutParams) spinner.getLayoutParams();
		if (C.API_LOLLIPOP) {
			layoutParams.gravity = Gravity.CENTER_VERTICAL;
			setBackgroundResource(ResourceUtils.getResourceId(context, android.R.attr.editTextBackground, 0));
			setAddStatesFromChildren(true);
			ThemeEngine.applyStyle(this);
		} else {
			float density = ResourceUtils.obtainDensity(context);
			layoutParams.setMargins(0, (int) (4f * density), 0, (int) (4f * density));
		}
	}

	public void setItems(Collection<? extends CharSequence> collection) {
		ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getContext(),
				android.R.layout.simple_spinner_item) {
			@Override
			public View getView(int position, View convertView, @NonNull ViewGroup parent) {
				View view = super.getView(position, convertView, parent);
				if (C.API_LOLLIPOP && convertView == null) {
					view.setPadding(0, 0, 0, 0);
				}
				return view;
			}
		};
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		adapter.addAll(collection);
		spinner.setAdapter(adapter);
	}

	public void setSelection(int position) {
		spinner.setSelection(position);
	}

	public int getSelectedItemPosition() {
		return spinner.getSelectedItemPosition();
	}
}
