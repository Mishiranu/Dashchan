package com.mishiranu.dashchan.ui.preference;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.ChanManager;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.database.ChanDatabase;
import com.mishiranu.dashchan.ui.DialogMenu;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.CursorAdapter;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.util.Collection;

public class CookiesFragment extends BaseListFragment implements FragmentHandler.Callback {
	private static final String EXTRA_CHAN_NAME = "chanName";

	public CookiesFragment() {}

	public CookiesFragment(String chanName) {
		Bundle args = new Bundle();
		args.putCharSequence(EXTRA_CHAN_NAME, chanName);
		setArguments(args);
	}

	private String getChanName() {
		return requireArguments().getString(EXTRA_CHAN_NAME);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.manage_cookies), null);
		getRecyclerView().setAdapter(new Adapter(this::onCookieClick));
		updateCursor();
	}

	@Override
	public void onDestroyView() {
		((Adapter) getRecyclerView().getAdapter()).setCursor(null);
		super.onDestroyView();
	}

	@Override
	public void onResume() {
		super.onResume();

		if (!ChanManager.getInstance().isExistingChanName(getChanName())) {
			((FragmentHandler) requireActivity()).removeFragment();
		}
	}

	@Override
	public void onChansChanged(Collection<String> changed, Collection<String> removed) {
		if (removed.contains(getChanName())) {
			((FragmentHandler) requireActivity()).removeFragment();
		}
	}

	private void onCookieClick(ChanDatabase.CookieItem cookieItem) {
		ActionDialog dialog = new ActionDialog(cookieItem.name, cookieItem.blocked, cookieItem.deleteOnExit);
		dialog.show(getChildFragmentManager(), ActionDialog.TAG);
	}

	private void removeCookie(String cookie) {
		ChanDatabase.getInstance().setCookie(getChanName(), cookie, null, null);
		if (!updateCursor()) {
			((FragmentHandler) requireActivity()).removeFragment();
		}
	}

	private void setCookieState(String cookie, Boolean blocked, Boolean deleteOnExit) {
		ChanDatabase.getInstance().setCookieState(getChanName(), cookie, blocked, deleteOnExit);
		if (!updateCursor()) {
			((FragmentHandler) requireActivity()).removeFragment();
		}
	}

	private boolean updateCursor() {
		Adapter adapter = (Adapter) getRecyclerView().getAdapter();
		adapter.setCursor(ChanDatabase.getInstance().getCookies(getChanName()));
		return adapter.getItemCount() > 0;
	}

	private static class Adapter extends CursorAdapter<ChanDatabase.CookieCursor, Adapter.ViewHolder>
			implements ListViewUtils.ClickCallback<Void, Adapter.ViewHolder> {
		public interface Callback {
			void onCookieClick(ChanDatabase.CookieItem cookieItem);
		}

		private static class ViewHolder extends RecyclerView.ViewHolder {
			public final ImageView blocked;
			public final ImageView deleteOnExit;
			public final TextView title;
			public final TextView value;

			public ViewHolder(ViewFactory.TwoLinesViewHolder viewHolder) {
				super(viewHolder.view);
				blocked = new ImageView(itemView.getContext());
				deleteOnExit = new ImageView(itemView.getContext());
				title = viewHolder.text1;
				value = viewHolder.text2;

				ViewGroup parent = (ViewGroup) title.getParent();
				int index = parent.indexOfChild(title);
				parent.removeView(title);
				LinearLayout titleLayout = new LinearLayout(parent.getContext());
				titleLayout.setOrientation(LinearLayout.HORIZONTAL);
				titleLayout.setGravity(Gravity.CENTER_VERTICAL);
				parent.addView(titleLayout, index, title.getLayoutParams());
				titleLayout.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
						LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

				float density = ResourceUtils.obtainDensity(parent);
				int size = (int) (12f * density + 0.5f);
				int top = (int) (1f * density + 0.5f);
				int margin = (int) (6f * density + 0.5f);
				blocked.setImageDrawable(ResourceUtils.getDrawable(parent.getContext(), R.attr.iconPostClosed, 0));
				deleteOnExit.setImageDrawable(ResourceUtils.getDrawable(parent.getContext(), R.attr.iconPostBanned, 0));
				if (C.API_LOLLIPOP) {
					blocked.setImageTintList(title.getTextColors());
					deleteOnExit.setImageTintList(title.getTextColors());
				}
				titleLayout.addView(blocked, size, size);
				ViewUtils.setNewMarginRelative(blocked, margin, top, 0, 0);
				titleLayout.addView(deleteOnExit, size, size);
				ViewUtils.setNewMarginRelative(deleteOnExit, margin, top, 0, 0);
			}
		}

		private final Callback callback;
		private final ChanDatabase.CookieItem cookieItem = new ChanDatabase.CookieItem();

		public Adapter(Callback callback) {
			this.callback = callback;
		}

		@NonNull
		@Override
		public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return ListViewUtils.bind(new ViewHolder(ViewFactory.makeTwoLinesListItem(parent, 0)), false, null, this);
		}

		@Override
		public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
			ChanDatabase.CookieItem cookieItem = this.cookieItem.update(moveTo(position));
			holder.blocked.setVisibility(cookieItem.blocked ? View.VISIBLE : View.GONE);
			holder.deleteOnExit.setVisibility(cookieItem.deleteOnExit ? View.VISIBLE : View.GONE);
			holder.title.setText(StringUtils.isEmpty(cookieItem.title) ? cookieItem.name : cookieItem.title);
			holder.value.setText(cookieItem.value);
			holder.value.setVisibility(StringUtils.isEmpty(cookieItem.value) ? View.GONE : View.VISIBLE);
			holder.blocked.setEnabled(!cookieItem.blocked);
			holder.deleteOnExit.setEnabled(!cookieItem.blocked);
			holder.title.setEnabled(!cookieItem.blocked);
			holder.value.setEnabled(!cookieItem.blocked);
		}

		@Override
		public boolean onItemClick(ViewHolder holder, int position, Void item, boolean longClick) {
			callback.onCookieClick(cookieItem.update(moveTo(position)).copy());
			return true;
		}
	}

	public static class ActionDialog extends DialogFragment {
		private static final String TAG = ActionDialog.class.getName();

		private static final String EXTRA_COOKIE = "cookie";
		private static final String EXTRA_BLOCKED = "blocked";
		private static final String EXTRA_DELETE_ON_EXIT = "deleteOnExit";

		public ActionDialog() {}

		public ActionDialog(String cookie, boolean blocked, boolean deleteOnExit) {
			Bundle args = new Bundle();
			args.putString(EXTRA_COOKIE, cookie);
			args.putBoolean(EXTRA_BLOCKED, blocked);
			args.putBoolean(EXTRA_DELETE_ON_EXIT, deleteOnExit);
			setArguments(args);
		}

		@NonNull
		@Override
		public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			Bundle args = requireArguments();
			boolean blocked = args.getBoolean(EXTRA_BLOCKED);
			boolean deleteOnExit = args.getBoolean(EXTRA_DELETE_ON_EXIT);
			DialogMenu dialogMenu = new DialogMenu(requireContext());
			dialogMenu.addCheck(R.string.block, blocked, () -> ((CookiesFragment) getParentFragment())
					.setCookieState(args.getString(EXTRA_COOKIE), !blocked, null));
			dialogMenu.addCheck(R.string.delete_on_exit, deleteOnExit, () -> ((CookiesFragment) getParentFragment())
					.setCookieState(args.getString(EXTRA_COOKIE), null, !deleteOnExit));
			if (!blocked && !deleteOnExit) {
				dialogMenu.add(R.string.delete, () -> ((CookiesFragment) getParentFragment())
						.removeCookie(args.getString(EXTRA_COOKIE)));
			}
			return dialogMenu.create();
		}
	}
}
