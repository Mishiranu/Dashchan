package com.mishiranu.dashchan.preference;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import chan.content.ChanManager;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.LocaleManager;
import com.mishiranu.dashchan.content.async.ReadUpdateTask;
import com.mishiranu.dashchan.preference.fragment.CategoriesFragment;
import com.mishiranu.dashchan.preference.fragment.UpdateFragment;
import com.mishiranu.dashchan.ui.ForegroundManager;
import com.mishiranu.dashchan.ui.StateActivity;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import java.util.ArrayList;

public class PreferencesActivity extends StateActivity {
	private static final String EXTRA_FRAGMENTS = "fragments";

	public interface ActivityHandler {
		boolean onBackPressed();
	}

	private static class FragmentState implements Parcelable {
		public final String className;
		public final Bundle arguments;
		public final Fragment.SavedState savedState;

		public FragmentState(String className, Bundle arguments, Fragment.SavedState savedState) {
			this.className = className;
			this.arguments = arguments;
			this.savedState = savedState;
		}

		private FragmentState(Parcel in) {
			className = in.readString();
			arguments = in.readByte() != 0 ? Bundle.CREATOR.createFromParcel(in) : null;
			if (arguments != null) {
				arguments.setClassLoader(getClass().getClassLoader());
			}
			savedState = in.readByte() != 0 ? Fragment.SavedState.CREATOR.createFromParcel(in) : null;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(className);
			dest.writeByte((byte) (arguments != null ? 1 : 0));
			if (arguments != null) {
				arguments.writeToParcel(dest, flags);
			}
			dest.writeByte((byte) (savedState != null ? 1 : 0));
			if (savedState != null) {
				savedState.writeToParcel(dest, flags);
			}
		}

		public static final Creator<FragmentState> CREATOR = new Creator<FragmentState>() {
			@Override
			public FragmentState createFromParcel(Parcel in) {
				return new FragmentState(in);
			}

			@Override
			public FragmentState[] newArray(int size) {
				return new FragmentState[size];
			}
		};
	}

	private boolean hasChans;
	private final ArrayList<FragmentState> fragments = new ArrayList<>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		LocaleManager.getInstance().apply(this);
		ResourceUtils.applyPreferredTheme(this);
		super.onCreate(savedInstanceState);

		if (!C.API_KITKAT) {
			// Show white logo on search
			getActionBar().setIcon(R.drawable.ic_logo);
		}
		FrameLayout content = new FrameLayout(this);
		content.setId(android.R.id.custom);
		content.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		setContentView(content);

		fragments.clear();
		if (savedInstanceState != null) {
			fragments.addAll(savedInstanceState.getParcelableArrayList(EXTRA_FRAGMENTS));
		}
		hasChans = !ChanManager.getInstance().getAvailableChanNames().isEmpty();
		if (hasChans || !fragments.isEmpty()) {
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}

		if (savedInstanceState == null) {
			ReadUpdateTask.UpdateDataMap updateDataMap = UpdateFragment.extractUpdateDataMap(getIntent());
			getSupportFragmentManager().beginTransaction()
					.replace(android.R.id.custom,
							updateDataMap != null ? new UpdateFragment(updateDataMap) : new CategoriesFragment())
					.commit();
		}
		if (!hasChans && savedInstanceState == null) {
			ToastUtils.show(this, R.string.message_no_extensions);
		}
		ViewUtils.applyToolbarStyle(this, null);
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelableArrayList(EXTRA_FRAGMENTS, fragments);
	}

	public void navigateFragment(Fragment fragment) {
		getActionBar().setDisplayHomeAsUpEnabled(true);
		Fragment currentFragment = getCurrentFragment();
		fragments.add(new FragmentState(currentFragment.getClass().getName(), currentFragment.getArguments(),
				getSupportFragmentManager().saveFragmentInstanceState(currentFragment)));
		getSupportFragmentManager().beginTransaction()
				.setCustomAnimations(R.animator.fragment_in, R.animator.fragment_out)
				.replace(android.R.id.custom, fragment)
				.commit();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home: {
				onBackPressed();
				return true;
			}
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onResume() {
		super.onResume();
		ForegroundManager.register(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		ForegroundManager.unregister(this);
	}

	private Fragment getCurrentFragment() {
		getSupportFragmentManager().executePendingTransactions();
		return getSupportFragmentManager().findFragmentById(android.R.id.custom);
	}

	@Override
	public void onBackPressed() {
		Fragment currentFragment = getCurrentFragment();
		if (currentFragment instanceof ActivityHandler) {
			if (((ActivityHandler) currentFragment).onBackPressed()) {
				return;
			}
		}
		if (fragments.isEmpty()) {
			super.onBackPressed();
		} else {
			FragmentState fragmentState = fragments.remove(fragments.size() - 1);
			Fragment fragment;
			try {
				fragment = (Fragment) Class.forName(fragmentState.className).newInstance();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			if (fragmentState.arguments != null) {
				fragment.setArguments(fragmentState.arguments);
			}
			if (fragmentState.savedState != null) {
				fragment.setInitialSavedState(fragmentState.savedState);
			}
			getSupportFragmentManager().beginTransaction()
					.setCustomAnimations(R.animator.fragment_in, R.animator.fragment_out)
					.replace(android.R.id.custom, fragment)
					.commit();
			if (fragments.isEmpty() && !hasChans) {
				getActionBar().setDisplayHomeAsUpEnabled(false);
			}
		}
	}

	public static int checkNewVersions(ReadUpdateTask.UpdateDataMap updateDataMap) {
		return UpdateFragment.checkNewVersions(updateDataMap);
	}

	public static Intent createUpdateIntent(Context context, ReadUpdateTask.UpdateDataMap updateDataMap) {
		Intent intent = new Intent(context, PreferencesActivity.class);
		UpdateFragment.modifyUpdateIntent(intent, updateDataMap);
		return intent;
	}
}
