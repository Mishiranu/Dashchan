package com.mishiranu.dashchan.ui.gallery;

import android.app.ActionBar;
import android.app.Dialog;
import android.graphics.Insets;
import android.media.AudioManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Toolbar;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ViewFactory;

public class GalleryDialog extends Dialog {
	public interface Callback {
		boolean onBackPressed();
	}

	private final Fragment fragment;
	private final MenuInflater menuInflater;

	private ViewFactory.ToolbarHolder toolbarHolder;
	private View actionBar;

	public GalleryDialog(Fragment fragment) {
		super(fragment.requireContext(), R.style.Theme_Gallery);
		this.fragment = fragment;
		this.menuInflater = fragment.requireActivity().getMenuInflater();
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
		WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
		layoutParams.setTitle(getContext().getPackageName() + "/" + getClass().getName());
		getWindow().setAttributes(layoutParams);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		if (C.API_R) {
			// ActionBarOverlayLayout relies on SYSTEM_UI_FLAG_LAYOUT_STABLE and uses deprecated
			// getSystemWindowInsetsAsRect instead of getInsetsIgnoringVisibility
			View decorView = getWindow().getDecorView();
			View overlay = decorView.findViewById(fragment.getResources()
					.getIdentifier("decor_content_parent", "id", "android"));
			View container = decorView.findViewById(fragment.getResources()
					.getIdentifier("action_bar_container", "id", "android"));
			if (overlay != null && container != null) {
				overlay.setOnApplyWindowInsetsListener((v, insets) -> {
					Insets systemInsets = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
					ViewUtils.setNewMargin(container, systemInsets.left, systemInsets.top, systemInsets.right, null);
					View actionBar = getActionBarView();
					if (actionBar != null) {
						Insets cutoutInsets = insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout());
						ViewUtils.setNewPadding(actionBar, Math.max(0, cutoutInsets.left - systemInsets.left),
								Math.max(0, cutoutInsets.top - systemInsets.top),
								Math.max(0, cutoutInsets.right - systemInsets.right), null);
					}
					return insets;
				});
			}
		}
	}

	private boolean actionBarAnimationsFixed = false;

	public void setTitleSubtitle(CharSequence title, CharSequence subtitle) {
		if (C.API_LOLLIPOP) {
			toolbarHolder.update(title, subtitle);
		} else {
			setTitle(title);
			getActionBar().setSubtitle(subtitle);
		}
	}

	@Override
	public ActionBar getActionBar() {
		ActionBar actionBar = super.getActionBar();
		if (actionBar != null && !actionBarAnimationsFixed) {
			actionBarAnimationsFixed = true;
			// Action bar animations are enabled only after onStart
			// which is called first time before action bar created
			onStop();
			onStart();
		}
		if (C.API_LOLLIPOP && toolbarHolder == null) {
			Toolbar toolbar = (Toolbar) getActionBarView();
			toolbarHolder = ViewFactory.addToolbarTitle(toolbar);
			WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
			CharSequence title = layoutParams.getTitle();
			setTitle(null);
			layoutParams.setTitle(title);
			getWindow().setAttributes(layoutParams);
		}
		return actionBar;
	}

	public View getActionBarView() {
		if (actionBar == null) {
			actionBar = getWindow().getDecorView().findViewById(fragment
					.getResources().getIdentifier("action_bar", "id", "android"));
		}
		return actionBar;
	}

	public interface OnFocusChangeListener {
		void onFocusChange(boolean hasFocus);
	}

	private OnFocusChangeListener onFocusChangeListener;

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);

		if (onFocusChangeListener != null) {
			onFocusChangeListener.onFocusChange(hasFocus);
		}
	}

	public void setOnFocusChangeListener(OnFocusChangeListener listener) {
		this.onFocusChangeListener = listener;
	}

	@Override
	public void onBackPressed() {
		if (!(fragment instanceof Callback) || !((Callback) fragment).onBackPressed()) {
			super.onBackPressed();
		}
	}

	@Override
	public boolean onPreparePanel(int featureId, View view, @NonNull Menu menu) {
		super.onPreparePanel(featureId, view, menu);
		// Dialog removes the menu completely if menu becomes once empty.
		// This logic is different from Activity and causes unwanted behavior.
		// Return "true" here to always keep menu existing.
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(@NonNull Menu menu) {
		fragment.onCreateOptionsMenu(menu, menuInflater);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
		fragment.onPrepareOptionsMenu(menu);
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, @NonNull MenuItem item) {
		if (featureId == Window.FEATURE_OPTIONS_PANEL) {
			return onOptionsItemSelected(item);
		} else {
			return false;
		}
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		return fragment.onOptionsItemSelected(item);
	}
}
