package com.mishiranu.dashchan.ui.preference;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.ChanManager;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.database.ChanDatabase;
import com.mishiranu.dashchan.ui.ActivityHandler;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.widget.CursorAdapter;
import com.mishiranu.dashchan.widget.SimpleViewHolder;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.util.Collection;

public class CookiesFragment extends BaseListFragment implements ActivityHandler {
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
		getRecyclerView().setAdapter(new Adapter());
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
		ActionDialog dialog = new ActionDialog(cookieItem.name, cookieItem.blocked);
		dialog.show(getChildFragmentManager(), ActionDialog.TAG);
	}

	private void removeCookie(String cookie) {
		ChanDatabase.getInstance().setCookie(getChanName(), cookie, null, null);
		if (!updateCursor()) {
			((FragmentHandler) requireActivity()).removeFragment();
		}
	}

	private void setBlocked(String cookie, boolean blocked) {
		ChanDatabase.getInstance().setCookieBlocked(getChanName(), cookie, blocked);
		if (!updateCursor()) {
			((FragmentHandler) requireActivity()).removeFragment();
		}
	}

	private boolean updateCursor() {
		Adapter adapter = (Adapter) getRecyclerView().getAdapter();
		adapter.setCursor(ChanDatabase.getInstance().getCookies(getChanName()));
		return adapter.getItemCount() > 0;
	}

	private class Adapter extends CursorAdapter<ChanDatabase.CookieCursor, RecyclerView.ViewHolder>
			implements ListViewUtils.ClickCallback<Void, RecyclerView.ViewHolder> {
		private final ChanDatabase.CookieItem cookieItem = new ChanDatabase.CookieItem();

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return ListViewUtils.bind(new SimpleViewHolder(ViewFactory.makeTwoLinesListItem(parent, 0).view),
					false, null, this);
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
			ChanDatabase.CookieItem cookieItem = this.cookieItem.update(moveTo(position));
			ViewFactory.TwoLinesViewHolder viewHolder = (ViewFactory.TwoLinesViewHolder) holder.itemView.getTag();
			viewHolder.text1.setText(StringUtils.isEmpty(cookieItem.title) ? cookieItem.name : cookieItem.title);
			viewHolder.text2.setText(cookieItem.value);
			viewHolder.text2.setVisibility(StringUtils.isEmpty(cookieItem.value) ? View.GONE : View.VISIBLE);
			viewHolder.text1.setEnabled(!cookieItem.blocked);
			viewHolder.text2.setEnabled(!cookieItem.blocked);
		}

		@Override
		public boolean onItemClick(RecyclerView.ViewHolder holder, int position, Void item, boolean longClick) {
			onCookieClick(cookieItem.update(moveTo(position)).copy());
			return true;
		}
	}

	public static class ActionDialog extends DialogFragment {
		private static final String TAG = ActionDialog.class.getName();

		private static final String EXTRA_COOKIE = "cookie";
		private static final String EXTRA_BLOCKED = "blocked";

		public ActionDialog() {}

		public ActionDialog(String cookie, boolean blocked) {
			Bundle args = new Bundle();
			args.putString(EXTRA_COOKIE, cookie);
			args.putBoolean(EXTRA_BLOCKED, blocked);
			setArguments(args);
		}

		@NonNull
		@Override
		public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			Bundle args = requireArguments();
			boolean blocked = args.getBoolean(EXTRA_BLOCKED);
			DialogMenu dialogMenu = new DialogMenu(requireContext());
			dialogMenu.add(R.string.block, blocked, () -> ((CookiesFragment) getParentFragment())
					.setBlocked(args.getString(EXTRA_COOKIE), !args.getBoolean(EXTRA_BLOCKED)));
			if (!blocked) {
				dialogMenu.add(R.string.delete, () -> ((CookiesFragment) getParentFragment())
						.removeCookie(args.getString(EXTRA_COOKIE)));
			}
			return dialogMenu.create();
		}
	}
}
