package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import java.util.Arrays;
import java.util.Collection;

public class DropdownView extends FrameLayout {
	private interface TextViewFactory {
		TextView newInstance();
	}

	private final Spinner spinner;
	private final TextViewFactory factory;

	public DropdownView(Context context) {
		this(context, null);
	}

	public DropdownView(Context context, AttributeSet attrs) {
		super(context, attrs);

		spinner = new Spinner(context);
		spinner.setId(android.R.id.edit);
		spinner.setPadding(0, 0, 0, 0);
		addView(spinner, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		if (C.API_LOLLIPOP) {
			EditText editText = new EditText(context);
			ThemeEngine.applyStyle(editText);
			setBackground(editText.getBackground());
			setBackgroundTintList(editText.getBackgroundTintList());
			setPadding(0, 0, 0, 0);
			setAddStatesFromChildren(true);
			int paddingLeft = editText.getPaddingLeft();
			int paddingTop = editText.getPaddingTop();
			int paddingRight = editText.getPaddingRight();
			int paddingBottom = editText.getPaddingBottom();
			ColorStateList textColors = editText.getTextColors();
			float textSize = editText.getTextSize();
			Typeface typeface = editText.getTypeface();
			factory = () -> {
				TextView textView = new TextView(context);
				textView.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
				textView.setTextColor(textColors);
				textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
				textView.setTypeface(typeface);
				textView.setSingleLine(true);
				return textView;
			};

			editText.setText("XXXXXXXXXX");
			int measureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
			editText.measure(measureSpec, measureSpec);
			Drawable background = spinner.getBackground();
			Bitmap bitmap = Bitmap.createBitmap(editText.getMeasuredWidth(),
					editText.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(bitmap);
			background.setBounds(0, 0, editText.getMeasuredWidth(), editText.getMeasuredHeight());
			background.draw(canvas);
			int[] pixels = new int[bitmap.getHeight()];
			@SuppressWarnings("MismatchedReadAndWriteOfArray")
			int[] zeroPixels = new int[bitmap.getHeight()];
			int left = -1;
			int right = -1;
			for (int i = 0; i < bitmap.getWidth(); i++) {
				bitmap.getPixels(pixels, 0, 1, i, 0, 1, bitmap.getHeight());
				if (!Arrays.equals(pixels, zeroPixels)) {
					left = i;
					break;
				}
			}
			for (int i = bitmap.getWidth() - 1; i >= 0; i--) {
				bitmap.getPixels(pixels, 0, 1, i, 0, 1, bitmap.getHeight());
				if (!Arrays.equals(pixels, zeroPixels)) {
					right = bitmap.getWidth() - 1 - i;
					break;
				}
			}
			bitmap.recycle();
			if (left >= 0 && right >= 0) {
				int imagePadding = Math.min(left, right);
				int textPadding = Math.min(paddingLeft, paddingRight);
				float density = ResourceUtils.obtainDensity(context);
				int targetPadding = (int) (8f * density) + textPadding;
				int margin = targetPadding - imagePadding;
				ViewUtils.setNewMarginRelative(spinner, 0, 0, margin, 0);
			}
		} else {
			float density = ResourceUtils.obtainDensity(context);
			ViewUtils.setNewMargin(spinner, 0, (int) (4f * density), 0, (int) (4f * density));
			factory = null;
		}
	}

	public void setItems(Collection<? extends CharSequence> collection) {
		ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getContext(),
				android.R.layout.simple_spinner_item) {
			@Override
			public View getView(int position, View convertView, @NonNull ViewGroup parent) {
				if (convertView == null && factory != null) {
					convertView = factory.newInstance();
				}
				return super.getView(position, convertView, parent);
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
