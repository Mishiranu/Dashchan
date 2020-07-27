package com.mishiranu.dashchan.preference.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.preference.PreferencesActivity;
import com.mishiranu.dashchan.preference.core.Preference;
import com.mishiranu.dashchan.preference.core.PreferenceFragment;
import com.mishiranu.dashchan.util.ResourceUtils;

public class ChansFragment extends PreferenceFragment {
	@Override
	protected SharedPreferences getPreferences() {
		return Preferences.PREFERENCES;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		ChanManager manager = ChanManager.getInstance();
		int color = ResourceUtils.getColor(view.getContext(), R.attr.drawerIconColor);
		for (String chanName : manager.getAvailableChanNames()) {
			Preference<?> preference = addCategory(ChanConfiguration.get(chanName).getTitle(),
					manager.getIcon(chanName, color));
			preference.setOnClickListener(p -> ((PreferencesActivity) requireActivity())
					.navigateFragment(new ChanFragment(chanName)));
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		requireActivity().setTitle(R.string.preference_header_forums);
		requireActivity().getActionBar().setSubtitle(null);
	}
}
