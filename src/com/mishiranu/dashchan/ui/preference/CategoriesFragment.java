package com.mishiranu.dashchan.ui.preference;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import chan.content.ChanManager;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;
import java.util.Iterator;

public class CategoriesFragment extends PreferenceFragment {
	@Override
	protected SharedPreferences getPreferences() {
		return Preferences.PREFERENCES;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		Iterator<String> chanNames = ChanManager.getInstance().getAvailableChanNames().iterator();
		boolean hasChan = chanNames.hasNext();
		String singleChanName = hasChan ? chanNames.next() : null;
		boolean hasMultipleChans = hasChan && chanNames.hasNext();
		addCategory(R.string.preference_header_general)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new GeneralFragment()));
		if (hasMultipleChans) {
			addCategory(R.string.preference_header_forums)
					.setOnClickListener(p -> ((FragmentHandler) requireActivity())
							.pushFragment(new ChansFragment()));
		} else if (hasChan) {
			addCategory(R.string.preference_header_forum)
					.setOnClickListener(p -> ((FragmentHandler) requireActivity())
							.pushFragment(new ChanFragment(singleChanName)));
		}
		addCategory(R.string.preference_header_interface)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new InterfaceFragment()));
		addCategory(R.string.preference_header_contents)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new ContentsFragment()));
		addCategory(R.string.preference_header_favorites)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new FavoritesFragment()));
		addCategory(R.string.preference_header_autohide)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new AutohideFragment()));
		addCategory(R.string.preference_header_about)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new AboutFragment()));
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.action_preferences), null);
	}
}
