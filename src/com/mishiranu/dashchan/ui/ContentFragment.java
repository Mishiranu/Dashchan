package com.mishiranu.dashchan.ui;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.CustomSearchView;

public abstract class ContentFragment extends Fragment {
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

	public void onTerminate() {}

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

	protected ViewHolderFragment getViewHolder() {
		FragmentManager fragmentManager = getParentFragmentManager();
		return (ViewHolderFragment) fragmentManager.findFragmentByTag(ViewHolderFragment.TAG);
	}

	public static class ViewHolderFragment extends Fragment {
		public static final String TAG = ViewHolderFragment.class.getName();

		private CustomSearchView searchView;

		public CustomSearchView obtainSearchView() {
			if (searchView == null) {
				searchView = new CustomSearchView(C.API_LOLLIPOP ? new ContextThemeWrapper(requireContext(),
						R.style.Theme_Special_White) : requireActivity().getActionBar().getThemedContext());
			}
			searchView.setOnSubmitListener(null);
			searchView.setOnChangeListener(null);
			ViewUtils.removeFromParent(searchView);
			searchView.setQuery("");
			return searchView;
		}
	}
}
