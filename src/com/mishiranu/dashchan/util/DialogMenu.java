package com.mishiranu.dashchan.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DialogMenu {
	private final Context context;
	private final AlertDialog.Builder builder;

	private final Callback callback;

	private final ArrayList<ListItem> listItems = new ArrayList<>();
	private boolean longTitle;

	private AlertDialog dialog;
	private DialogInterface.OnDismissListener onDismissListener;

	private boolean dismissCalled = false;

	private static class ListItem {
		public final int id;
		public final String title;
		public final boolean checkable;
		public final boolean checked;

		public ListItem(int id, String title, boolean checkable, boolean checked) {
			this.id = id;
			this.title = title;
			this.checkable = checkable;
			this.checked = checked;
		}
	}

	private HashMap<String, Object> extra;
	private boolean consumed = false;

	public DialogMenu(Context context, Callback callback) {
		this.context = context;
		this.builder = new AlertDialog.Builder(context);
		this.callback = callback;
	}

	public DialogMenu setTitle(String title, boolean longTitle) {
		checkConsumed();
		builder.setTitle(title);
		this.longTitle = longTitle;
		return this;
	}

	private DialogMenu addItem(int id, String title, boolean checkable, boolean checked) {
		checkConsumed();
		listItems.add(new ListItem(id, title, checkable, checked));
		return this;
	}

	public DialogMenu addItem(int id, String title) {
		return addItem(id, title, false, false);
	}

	public DialogMenu addItem(int id, int titleRes) {
		return addItem(id, context.getString(titleRes));
	}

	public DialogMenu addCheckableItem(int id, String title, boolean checked) {
		return addItem(id, title, true, checked);
	}

	public DialogMenu addCheckableItem(int id, int titleRes, boolean checked) {
		return addCheckableItem(id, context.getString(titleRes), checked);
	}

	public DialogMenu putExtra(String key, Object value) {
		if (extra == null) {
			extra = new HashMap<>();
		}
		extra.put(key, value);
		return this;
	}

	public AlertDialog create() {
		checkConsumed();
		if (listItems.size() > 0) {
			dialog = builder.setAdapter(new DialogAdapter(), (d, which) -> {
				if (!dismissCalled && onDismissListener != null) {
					dismissCalled = true;
					onDismissListener.onDismiss(d);
				}
				callback.onItemClick(context, listItems.get(which).id, extra);
			}).create();
			if (longTitle) {
				dialog.setOnShowListener(ViewUtils.ALERT_DIALOG_LONGER_TITLE);
			}
			if (onDismissListener != null) {
				dialog.setOnDismissListener(d -> {
					if (!dismissCalled) {
						onDismissListener.onDismiss(d);
					}
				});
			}
			return dialog;
		}
		consumed = true;
		return null;
	}

	public void show() {
		AlertDialog dialog = create();
		if (dialog != null) {
			dialog.show();
		}
	}

	public void dismiss() {
		if (dialog != null) {
			dialog.dismiss();
			dialog = null;
		}
	}

	public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
		checkConsumed();
		onDismissListener = listener;
	}

	private void checkConsumed() {
		if (consumed) {
			throw new RuntimeException("DialogMenu is already consumed.");
		}
	}

	public interface Callback {
		public void onItemClick(Context context, int id, Map<String, Object> extra);
	}

	private class DialogAdapter extends BaseAdapter {
		private static final int TYPE_SIMPLE = 0;
		private static final int TYPE_CHECKABLE = 1;

		private final int layoutResId;

		public DialogAdapter() {
			layoutResId = ResourceUtils.obtainAlertDialogLayoutResId(context, ResourceUtils.DIALOG_LAYOUT_SIMPLE);
		}

		@Override
		public int getViewTypeCount() {
			return 2;
		}

		@Override
		public int getItemViewType(int position) {
			return getItem(position).checkable ? TYPE_CHECKABLE : TYPE_SIMPLE;
		}

		@Override
		public int getCount() {
			return listItems.size();
		}

		@Override
		public ListItem getItem(int position) {
			return listItems.get(position);
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ListItem listItem = getItem(position);
			ViewHolder viewHolder;
			if (convertView == null) {
				viewHolder = new ViewHolder();
				View view = LayoutInflater.from(parent.getContext()).inflate(layoutResId, parent, false);
				if (listItem.checkable) {
					LinearLayout linearLayout = new LinearLayout(parent.getContext());
					linearLayout.setOrientation(LinearLayout.HORIZONTAL);
					linearLayout.setGravity(Gravity.CENTER_VERTICAL);
					linearLayout.addView(view, new LinearLayout.LayoutParams(0,
							LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
					CheckBox checkBox = new CheckBox(parent.getContext());
					checkBox.setClickable(false);
					checkBox.setFocusable(false);
					int paddingRight = view.getPaddingRight() - (int) (4f * ResourceUtils.obtainDensity(view));
					checkBox.setPadding(checkBox.getPaddingLeft(), checkBox.getPaddingTop(),
							Math.max(checkBox.getPaddingRight(), paddingRight), checkBox.getPaddingBottom());
					linearLayout.addView(checkBox, LinearLayout.LayoutParams.WRAP_CONTENT,
							LinearLayout.LayoutParams.WRAP_CONTENT);
					viewHolder.checkBox = checkBox;
					view = linearLayout;
				}
				viewHolder.textView = view.findViewById(android.R.id.text1);
				view.setTag(viewHolder);
				convertView = view;
			} else {
				viewHolder = (ViewHolder) convertView.getTag();
			}
			viewHolder.textView.setText(listItem.title);
			if (listItem.checkable) {
				viewHolder.checkBox.setChecked(listItem.checked);
			}
			return convertView;
		}
	}

	private static class ViewHolder {
		public TextView textView;
		public CheckBox checkBox;
	}
}
