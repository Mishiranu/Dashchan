package com.mishiranu.dashchan.ui.preference;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import chan.content.ChanManager;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.LocaleManager;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;

public class GeneralFragment extends PreferenceFragment {
	@Override
	protected SharedPreferences getPreferences() {
		return Preferences.PREFERENCES;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		addList(Preferences.KEY_LOCALE, LocaleManager.VALUES_LOCALE, LocaleManager.DEFAULT_LOCALE,
				R.string.language, LocaleManager.ENTRIES_LOCALE).setOnAfterChangeListener(p -> {
					LocaleManager.getInstance().apply(requireContext(), false);
					requireActivity().recreate();
				});

		addHeader(R.string.navigation);
		addCheck(true, Preferences.KEY_CLOSE_ON_BACK, Preferences.DEFAULT_CLOSE_ON_BACK,
				R.string.close_pages, R.string.close_pages__summary);
		addCheck(true, Preferences.KEY_REMEMBER_HISTORY, Preferences.DEFAULT_REMEMBER_HISTORY,
				R.string.remember_history, 0);
		if (ChanManager.getInstance().hasMultipleAvailableChans()) {
			addCheck(true, Preferences.KEY_MERGE_CHANS, Preferences.DEFAULT_MERGE_CHANS,
					R.string.merge_pages, R.string.merge_pages__summary);
		}
		addCheck(true, Preferences.KEY_INTERNAL_BROWSER, Preferences.DEFAULT_INTERNAL_BROWSER,
				R.string.internal_browser, R.string.internal_browser__sumamry);

		if (C.API_KITKAT) {
			addHeader(R.string.services);
			addCheck(true, Preferences.KEY_RECAPTCHA_JAVASCRIPT, Preferences.DEFAULT_RECAPTCHA_JAVASCRIPT,
					R.string.use_javascript_for_recaptcha, R.string.use_javascript_for_recaptcha__summary);
		}

		addHeader(R.string.connection);
		addButton(0, R.string.specific_to_internal_services__sentence).setSelectable(false);
		addCheck(true, Preferences.KEY_USE_HTTPS_GENERAL, Preferences.DEFAULT_USE_HTTPS,
				R.string.secure_connection, R.string.secure_connection__summary);
		addCheck(true, Preferences.KEY_VERIFY_CERTIFICATE, Preferences.DEFAULT_VERIFY_CERTIFICATE,
				R.string.verify_certificate, R.string.verify_certificate__summary);
		addCheck(true, Preferences.KEY_USE_GMS_PROVIDER, Preferences.DEFAULT_USE_GMS_PROVIDER,
				R.string.use_gms_security_provider, R.string.requires_restart);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.general), null);
	}
}
