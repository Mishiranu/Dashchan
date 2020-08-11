package com.mishiranu.dashchan.preference.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import chan.content.ChanManager;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.preference.core.PreferenceFragment;
import com.mishiranu.dashchan.ui.FragmentHandler;
import java.util.Collection;

public class CategoriesFragment extends PreferenceFragment {
	@Override
	protected SharedPreferences getPreferences() {
		return Preferences.PREFERENCES;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		Collection<String> chanNames = ChanManager.getInstance().getAvailableChanNames();
		addCategory(R.string.preference_header_general)
				.setOnClickListener(p -> ((FragmentHandler) requireActivity())
						.pushFragment(new GeneralFragment()));
		if (chanNames.size() == 1) {
			addCategory(R.string.preference_header_forum)
					.setOnClickListener(p -> ((FragmentHandler) requireActivity())
							.pushFragment(new ChanFragment(chanNames.iterator().next())));
		} else if (chanNames.size() > 1) {
			addCategory(R.string.preference_header_forums)
					.setOnClickListener(p -> ((FragmentHandler) requireActivity())
							.pushFragment(new ChansFragment()));
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

		requireActivity().setTitle(R.string.action_preferences);
		requireActivity().getActionBar().setSubtitle(null);
	}
}
