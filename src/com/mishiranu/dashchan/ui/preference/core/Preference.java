package com.mishiranu.dashchan.ui.preference.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ViewFactory;

public abstract class Preference<T> {
	public enum ViewType {NORMAL, CATEGORY, HEADER, CHECK}

	public interface SummaryProvider<T> {
		CharSequence getSummary(Preference<T> value);
	}

	public interface OnClickListener<T> {
		void onClick(Preference<T> preference);
	}

	protected interface OnChangeListener {
		void onChange(boolean newValue);
	}

	public interface OnBeforeChangeListener<T> {
		boolean onBeforeChange(Preference<T> preference, T value);
	}

	public interface OnAfterChangeListener<T> {
		void onAfterChange(Preference<T> preference);
	}

	public static class ViewHolder {
		public final View view;
		public final TextView title;
		public final TextView summary;
		public final LinearLayout widgetFrame;

		public ViewHolder(View view, TextView title, TextView summary, LinearLayout widgetFrame) {
			this.view = view;
			this.title = title;
			this.summary = summary;
			this.widgetFrame = widgetFrame;
		}

		public ViewHolder(ViewHolder viewHolder) {
			this(viewHolder.view, viewHolder.title, viewHolder.summary, viewHolder.widgetFrame);
		}
	}

	public final Context context;
	public final String key;
	protected final T defaultValue;
	protected final CharSequence title;
	protected final SummaryProvider<T> summaryProvider;

	private T value;
	private boolean enabled = true;
	private boolean selectable = true;
	private OnClickListener<T> onClickListener;
	private OnChangeListener onChangeListener;
	private OnBeforeChangeListener<T> onBeforeChangeListener;
	private OnAfterChangeListener<T> onAfterChangeListener;

	public Preference(Context context, String key, T defaultValue,
			CharSequence title, SummaryProvider<T> summaryProvider) {
		this.context = context;
		this.key = key;
		this.defaultValue = defaultValue;
		this.title = title;
		this.summaryProvider = summaryProvider;
	}

	public ViewType getViewType() {
		return ViewType.NORMAL;
	}

	public ViewHolder createViewHolder(ViewGroup parent) {
		ViewFactory.TwoLinesViewHolder holder = ViewFactory.makeTwoLinesListItem(parent, ViewFactory.FEATURE_WIDGET);
		return new ViewHolder(holder.view, holder.text1, holder.text2, holder.widgetFrame);
	}

	public void bindViewHolder(ViewHolder viewHolder) {
		if (viewHolder.title != null) {
			viewHolder.title.setText(title);
			viewHolder.title.setVisibility(StringUtils.isEmpty(title) ? View.GONE : View.VISIBLE);
			viewHolder.title.setEnabled(enabled);
		}
		if (viewHolder.summary != null) {
			CharSequence summary = summaryProvider != null ? summaryProvider.getSummary(this) : null;
			viewHolder.summary.setText(summary);
			viewHolder.summary.setVisibility(StringUtils.isEmpty(summary) ? View.GONE : View.VISIBLE);
			viewHolder.summary.setEnabled(enabled);
		}
		viewHolder.view.setEnabled(enabled && selectable);
	}

	public void performClick() {
		if (onClickListener != null) {
			onClickListener.onClick(this);
		}
	}

	protected abstract void extract(SharedPreferences preferences);
	protected abstract void persist(SharedPreferences preferences);

	public void invalidate() {
		if (onChangeListener != null) {
			onChangeListener.onChange(false);
		}
	}

	protected void notifyAfterChange() {
		if (onAfterChangeListener != null) {
			onAfterChangeListener.onAfterChange(this);
		}
	}

	public void setValue(T value) {
		if (onBeforeChangeListener == null || onBeforeChangeListener.onBeforeChange(this, value)) {
			this.value = value;
			if (onChangeListener != null) {
				onChangeListener.onChange(true);
			}
		}
	}

	public T getValue() {
		return value;
	}

	public void setOnClickListener(OnClickListener<T> listener) {
		this.onClickListener = listener;
	}

	protected void setOnChangeListener(OnChangeListener listener) {
		this.onChangeListener = listener;
	}

	public void setOnBeforeChangeListener(OnBeforeChangeListener<T> listener) {
		this.onBeforeChangeListener = listener;
	}

	public void setOnAfterChangeListener(OnAfterChangeListener<T> listener) {
		this.onAfterChangeListener = listener;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		invalidate();
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setSelectable(boolean selectable) {
		this.selectable = selectable;
		invalidate();
	}

	public static class Runtime<T> extends Preference<T> {
		public Runtime(Context context, String key, T defaultValue,
				CharSequence title, SummaryProvider<T> summaryProvider) {
			super(context, key, defaultValue, title, summaryProvider);
		}

		@Override
		protected void extract(SharedPreferences preferences) {
			throw new UnsupportedOperationException();
		}

		@Override
		protected void persist(SharedPreferences preferences) {
			throw new UnsupportedOperationException();
		}

		public static class IconViewHolder extends ViewHolder {
			public final ImageView icon;

			public IconViewHolder(ViewHolder viewHolder, ImageView icon) {
				super(viewHolder);
				this.icon = icon;
			}
		}

		public IconViewHolder createIconViewHolder(ViewGroup parent) {
			ViewHolder viewHolder = super.createViewHolder(parent);
			float density = ResourceUtils.obtainDensity(parent);
			ImageView icon = new ImageView(viewHolder.view.getContext());
			LinearLayout.LayoutParams layoutParams = new LinearLayout
					.LayoutParams((int) (24f * density), (int) (24f * density));
			if (C.API_LOLLIPOP) {
				layoutParams.setMarginEnd((int) (32f * density));
			} else if (C.API_JELLY_BEAN_MR1) {
				layoutParams.setMarginEnd((int) (8f * density));
			} else {
				layoutParams.setMargins(0, 0, (int) (8f * density), 0);
			}
			icon.setLayoutParams(layoutParams);
			((LinearLayout) viewHolder.view).addView(icon, 0);
			return new IconViewHolder(viewHolder, icon);
		}
	}
}
