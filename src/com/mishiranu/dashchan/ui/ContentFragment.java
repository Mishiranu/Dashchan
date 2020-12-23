package com.mishiranu.dashchan.ui;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toolbar;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.CustomSearchView;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

public abstract class ContentFragment extends Fragment {
	private static class MenuState {
		public boolean created;
	}

	private final WeakHashMap<Menu, MenuState> menuStates = new WeakHashMap<>();

	public boolean isSearchMode() {
		return false;
	}

	public boolean onSearchRequested() {
		return false;
	}

	public boolean onHomePressed() {
		return onBackPressed();
	}

	public boolean onBackPressed() {
		return false;
	}

	private void clearOptionMenus() {
		for (WeakHashMap.Entry<Menu, MenuState> entry : menuStates.entrySet()) {
			if (entry.getValue().created) {
				Menu menu = entry.getKey();
				int size = menu.size();
				for (int i = 0; i < size; i++) {
					MenuItem menuItem = menu.getItem(i);
					if (menuItem.getActionView() != null) {
						menuItem.setOnActionExpandListener(null);
						if (menuItem.isActionViewExpanded()) {
							menuItem.collapseActionView();
						}
					}
				}
			}
		}
		menuStates.clear();
	}

	public void onTerminate() {
		clearOptionMenus();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		clearOptionMenus();
		ViewHolderFragment viewHolder = getViewHolder();
		if (viewHolder != null) {
			viewHolder.resetSearchView(this);
		}
	}

	public boolean dispatchKeyEvent(KeyEvent event) {
		if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
			// Block hardware menu button if menu is empty. This fixes menu issues on some Android 5 devices
			// and ensures empty hardware menu will never appear.
			boolean hasMenuItems = false;
			for (WeakHashMap.Entry<Menu, MenuState> entry : menuStates.entrySet()) {
				hasMenuItems = entry.getValue().created && entry.getKey().hasVisibleItems();
			}
			if (!hasMenuItems) {
				return true;
			} else if (C.API_OREO && !C.API_PIE) {
				// Fix invalid hardware menu layout on Android 8 and 8.1
				Toolbar toolbar = (Toolbar) ((FragmentHandler) requireActivity()).getToolbarView();
				toolbar.showOverflowMenu();
				return true;
			}
		}
		return false;
	}

	@Override
	public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
		if (transit == FragmentTransaction.TRANSIT_FRAGMENT_OPEN) {
			return createAnimator(getView(), enter);
		} else {
			return null;
		}
	}

	protected Animator createAnimator(View view, boolean enter) {
		if (enter) {
			ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f);
			alphaAnimator.setDuration(150);
			ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.925f, 1f);
			scaleXAnimator.setDuration(150);
			ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.925f, 1f);
			scaleYAnimator.setDuration(150);
			AnimatorSet set = new AnimatorSet();
			set.playTogether(alphaAnimator, scaleXAnimator, scaleYAnimator);
			return set;
		} else {
			ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f);
			animator.setDuration(100);
			animator.setInterpolator(new DecelerateInterpolator());
			return animator;
		}
	}

	public static void prepare(FragmentActivity activity) {
		FragmentManager fragmentManager = activity.getSupportFragmentManager();
		ViewHolderFragment viewHolder = (ViewHolderFragment) fragmentManager.findFragmentByTag(ViewHolderFragment.TAG);
		if (viewHolder == null) {
			viewHolder = new ViewHolderFragment();
			fragmentManager.beginTransaction().add(viewHolder, ViewHolderFragment.TAG).commitNow();
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		super.setHasOptionsMenu(true);
	}

	@Override
	public void onResume() {
		super.onResume();
		// Menu can be requested too early on some Android 4.x
		invalidateMenuInternal(true);
	}

	@Override
	public void setHasOptionsMenu(boolean hasMenu) {
		throw new UnsupportedOperationException();
	}

	private MenuState obtainMenuState(Menu menu) {
		MenuState menuState = menuStates.get(menu);
		if (menuState == null) {
			menuState = new MenuState();
			menuStates.put(menu, menuState);
		}
		return menuState;
	}

	@Override
	public final void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		MenuState menuState = obtainMenuState(menu);
		if (isAdded() && isValidOptionsMenuState()) {
			menuState.created = true;
			onCreateOptionsMenu(menu, isPrimaryMenu(menu));
		}
	}

	@Override
	public final void onPrepareOptionsMenu(@NonNull Menu menu) {
		if (isAdded() && isValidOptionsMenuState()) {
			boolean primary = isPrimaryMenu(menu);
			MenuState menuState = obtainMenuState(menu);
			if (!menuState.created) {
				// onPrepareOptionsMenu can be called when onCreateOptionsMenu was called in
				// invalid state (when isValidOptionsMenuState returned false) or wasn't called at all
				// (this is the case for devices with hardware menu button which have 2 Menu instances)
				menuState.created = true;
				menu.clear();
				onCreateOptionsMenu(menu, primary);
			}
			onPrepareOptionsMenu(menu, primary);
		}
	}

	public boolean isValidOptionsMenuState() {
		return true;
	}

	public void onCreateOptionsMenu(Menu menu, boolean primary) {}

	public void onPrepareOptionsMenu(Menu menu, boolean primary) {}

	public void invalidateOptionsMenu() {
		invalidateMenuInternal(false);
	}

	private void invalidateMenuInternal(boolean prepareOnly) {
		for (WeakHashMap.Entry<Menu, MenuState> entry : menuStates.entrySet()) {
			if (!prepareOnly || !entry.getValue().created) {
				onPrepareOptionsMenu(entry.getKey());
			}
		}
	}

	private boolean isPrimaryMenu(Menu menu) {
		if (C.API_LOLLIPOP) {
			Toolbar toolbar = (Toolbar) ((FragmentHandler) requireActivity()).getToolbarView();
			return toolbar.getMenu() == menu;
		} else {
			return true;
		}
	}

	private ViewHolderFragment getViewHolder() {
		FragmentManager fragmentManager = getParentFragmentManager();
		return (ViewHolderFragment) fragmentManager.findFragmentByTag(ViewHolderFragment.TAG);
	}

	protected CustomSearchView obtainSearchView() {
		ViewHolderFragment viewHolder = getViewHolder();
		return viewHolder.obtainSearchView(this);
	}

	public static class ViewHolderFragment extends Fragment {
		public static final String TAG = ViewHolderFragment.class.getName();

		private CustomSearchView searchView;
		private WeakReference<ContentFragment> searchViewOwner;

		private void resetSearchView(ContentFragment fragment) {
			if (searchView != null) {
				boolean reset;
				if (fragment != null && searchViewOwner != null) {
					ContentFragment ownerFragment = searchViewOwner.get();
					reset = ownerFragment == null || ownerFragment == fragment;
				} else {
					reset = true;
				}
				if (reset) {
					searchView.setOnSubmitListener(null);
					searchView.setOnChangeListener(null);
				}
			}
		}

		private CustomSearchView obtainSearchView(ContentFragment fragment) {
			resetSearchView(null);
			if (searchView == null) {
				searchView = new CustomSearchView(C.API_LOLLIPOP ? new ContextThemeWrapper(requireContext(),
						R.style.Theme_Special_White) : requireActivity().getActionBar().getThemedContext());
			}
			searchViewOwner = new WeakReference<>(fragment);
			ViewUtils.removeFromParent(searchView);
			searchView.setQuery("");
			return searchView;
		}
	}
}
