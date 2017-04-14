/*
 * Copyright 2014-2016 Fukurou Mishiranu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.preference.fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import chan.content.ChanConfiguration;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.DialogMenu;

public class CookiesFragment extends BasePreferenceFragment {
	private ChanConfiguration configuration;
	private HashMap<String, ChanConfiguration.CookieData> cookies = new HashMap<>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		String chanName = getActivity().getIntent().getStringExtra(C.EXTRA_CHAN_NAME);
		if (chanName == null) {
			throw new IllegalStateException();
		}
		getActivity().setTitle(R.string.preference_manage_cookies);

		configuration = ChanConfiguration.get(chanName);
		ArrayList<ChanConfiguration.CookieData> cookies = configuration.getCookies();
		Collections.sort(cookies, (l, r) -> StringUtils.compare(l.cookie, r.cookie, true));
		for (ChanConfiguration.CookieData cookieData : cookies) {
			this.cookies.put(cookieData.cookie, cookieData);
			CookiePreference preference = new CookiePreference(getActivity());
			preference.setPersistent(false);
			preference.setKey(cookieData.cookie);
			preference.setTitle(cookieData.displayName);
			preference.setSummary(cookieData.value);
			preference.setViewGrayed(cookieData.blocked);
			preference.setOnPreferenceClickListener(this);
			addPreference(null, preference);
		}
	}

	private static class CookiePreference extends Preference {
		public CookiePreference(Context context) {
			super(context);
		}

		private boolean viewGrayed = false;

		public void setViewGrayed(boolean viewGrayed) {
			if (this.viewGrayed != viewGrayed) {
				this.viewGrayed = viewGrayed;
				notifyChanged();
			}
		}

		@Override
		public View getView(View convertView, ViewGroup parent) {
			convertView = super.getView(convertView, parent);
			TextView titleTextView = (TextView) convertView.findViewById(android.R.id.title);
			TextView summaryTextView = (TextView) convertView.findViewById(android.R.id.summary);
			if (titleTextView != null && summaryTextView != null) {
				titleTextView.setEnabled(!viewGrayed);
				summaryTextView.setEnabled(!viewGrayed);
			}
			return convertView;
		}
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		return false;
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		String cookie = preference.getKey();
		ChanConfiguration.CookieData cookieData = cookies.get(cookie);
		if (cookieData != null) {
			ActionDialog dialog = new ActionDialog(cookie, cookieData.blocked);
			dialog.setTargetFragment(this, 0);
			dialog.show(getFragmentManager(), ActionDialog.TAG);
		}
		return true;
	}

	private boolean removeCookie(String cookie, boolean preferenceOnly) {
		if (!preferenceOnly) {
			configuration.storeCookie(cookie, null, null);
			configuration.commit();
		}
		CookiePreference preference = (CookiePreference) findPreference(cookie);
		if (preference != null) {
			PreferenceGroup preferenceGroup = getParentGroup(preference);
			preferenceGroup.removePreference(preference);
			cookies.remove(cookie);
			if (preferenceGroup.getPreferenceCount() == 0) {
				getActivity().finish();
				return true;
			}
		}
		return false;
	}

	private void setBlocked(String cookie, boolean blocked) {
		boolean remove = configuration.setCookieBlocked(cookie, blocked);
		configuration.commit();
		if (remove) {
			removeCookie(cookie, true);
		} else {
			ChanConfiguration.CookieData cookieData = cookies.get(cookie);
			if (cookieData != null) {
				cookies.put(cookie, new ChanConfiguration.CookieData(cookie,
						cookieData.value, cookieData.displayName, blocked));
			}
		}
		CookiePreference preference = (CookiePreference) findPreference(cookie);
		if (preference != null) {
			preference.setViewGrayed(blocked);
		}
	}

	public static class ActionDialog extends DialogFragment implements DialogMenu.Callback {
		private static final String TAG = UpdateFragment.TargetDialog.class.getName();

		private static final String EXTRA_COOKIE = "cookie";
		private static final String EXTRA_BLOCKED = "blocked";

		public ActionDialog() {}

		public ActionDialog(String cookie, boolean blocked) {
			Bundle args = new Bundle();
			args.putString(EXTRA_COOKIE, cookie);
			args.putBoolean(EXTRA_BLOCKED, blocked);
			setArguments(args);
		}

		private static final int MENU_BLOCKED = 0;
		private static final int MENU_REMOVE = 1;

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			Bundle args = getArguments();
			boolean blocked = args.getBoolean(EXTRA_BLOCKED);
			DialogMenu dialogMenu = new DialogMenu(getActivity(), this);
			dialogMenu.addCheckableItem(MENU_BLOCKED, R.string.action_block, blocked);
			if (!blocked) {
				dialogMenu.addItem(MENU_REMOVE, R.string.action_delete);
			}
			return dialogMenu.create();
		}

		@Override
		public void onItemClick(Context context, int id, Map<String, Object> extra) {
			Bundle args = getArguments();
			switch (id) {
				case MENU_BLOCKED: {
					((CookiesFragment) getTargetFragment()).setBlocked(args.getString(EXTRA_COOKIE),
							!args.getBoolean(EXTRA_BLOCKED));
					break;
				}
				case MENU_REMOVE: {
					((CookiesFragment) getTargetFragment()).removeCookie(args.getString(EXTRA_COOKIE), false);
					break;
				}
			}
		}
	}
}