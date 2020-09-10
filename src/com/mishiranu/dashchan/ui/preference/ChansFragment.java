package com.mishiranu.dashchan.ui.preference;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.Preference;
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;

public class ChansFragment extends PreferenceFragment {
	@Override
	protected SharedPreferences getPreferences() {
		return Preferences.PREFERENCES;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		ChanManager manager = ChanManager.getInstance();
		for (String chanName : manager.getAvailableChanNames()) {
			Preference<?> preference = addCategory(ChanConfiguration.get(chanName).getTitle(),
					manager.getIcon(chanName));
			preference.setOnClickListener(p -> ((FragmentHandler) requireActivity())
					.pushFragment(new ChanFragment(chanName)));
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.forums), null);
		if (!ChanManager.getInstance().getAvailableChanNames().iterator().hasNext()) {
			((FragmentHandler) requireActivity()).removeFragment();
		}
	}
}
