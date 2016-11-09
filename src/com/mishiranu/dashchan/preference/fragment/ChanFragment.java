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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.text.InputType;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpClient;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.util.CommonUtils;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.AsyncManager;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.net.CloudFlarePasser;
import com.mishiranu.dashchan.preference.MultipleEditTextPreference;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.preference.PreferencesActivity;
import com.mishiranu.dashchan.util.ToastUtils;

public class ChanFragment extends BasePreferenceFragment {
	private String chanName;

	private EditTextPreference defaultBoardPreference;
	private MultipleEditTextPreference captchaPassPreference;
	private MultipleEditTextPreference userAuthorizationPreference;
	private Preference cookiePreference;
	private ListPreference domainPreference1;
	private EditTextPreference domainPreference2;
	private EditTextPreference passwordPreference;
	private CheckBoxPreference useHttpsPreference;
	private MultipleEditTextPreference proxyPreference;
	private Preference uninstallExtensionPreference;

	private HashSet<String> customPreferenceKeys;

	private static String VALUE_CUSTOM_DOMAIN = "custom_domain\n";
	private static String EXTRA_ANOTHER_DOMAIN_MODE = "another_domain_mode";

	private boolean anotherDomainMode = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		String chanName = getActivity().getIntent().getStringExtra(C.EXTRA_CHAN_NAME);
		if (chanName == null) {
			Collection<String> chanNames = ChanManager.getInstance().getAvailableChanNames();
			if (chanNames.size() == 0) {
				throw new IllegalStateException();
			}
			chanName = chanNames.iterator().next();
		}
		this.chanName = chanName;
		ChanConfiguration configuration = ChanConfiguration.get(chanName);
		ChanLocator locator = ChanLocator.get(chanName);
		getActivity().setTitle(configuration.getTitle());
		ChanConfiguration.Board board = configuration.safe().obtainBoard(null);
		ChanConfiguration.Deleting deleting = board.allowDeleting ? configuration.safe().obtainDeleting(null) : null;

		if (!configuration.getOption(ChanConfiguration.OPTION_SINGLE_BOARD_MODE)) {
			defaultBoardPreference = makeEditText(null, Preferences.KEY_DEFAULT_BOARD_NAME.bind(chanName), null,
					R.string.preference_default_board_name, 0, null, InputType.TYPE_CLASS_TEXT, false);
		}
		if (board.allowCatalog) {
			makeCheckBox(null, true, Preferences.KEY_LOAD_CATALOG.bind(chanName), Preferences.DEFAULT_LOAD_CATALOG,
					R.string.preference_load_catalog, R.string.preference_load_catalog_summary);
		}
		if (deleting != null && deleting.password) {
			Preferences.getPassword(chanName); // Ensure password existence
			passwordPreference = makeEditText(null, Preferences.KEY_PASSWORD.bind(chanName), null,
					R.string.preference_password_for_removal, R.string.preference_password_for_removal_summary,
					getString(R.string.text_password), InputType.TYPE_CLASS_TEXT |
					InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, false);
		}
		Collection<String> captchaTypes = configuration.getSupportedCaptchaTypes();
		if (captchaTypes != null && captchaTypes.size() > 1) {
			String[] values = Preferences.getCaptchaTypeValues(captchaTypes);
			makeList(null, Preferences.KEY_CAPTCHA.bind(chanName), values,
					Preferences.getCaptchaTypeDefaultValue(chanName), R.string.preference_captcha,
					Preferences.getCaptchaTypeEntries(chanName, captchaTypes));
		}
		if (configuration.getOption(ChanConfiguration.OPTION_ALLOW_CAPTCHA_PASS)) {
			ChanConfiguration.Authorization authorization = configuration.safe().obtainCaptchaPass();
			if (authorization != null && authorization.fieldsCount > 0) {
				captchaPassPreference = makeMultipleEditText(null, Preferences.KEY_CAPTCHA_PASS.bind(chanName),
						null, R.string.preference_captcha_pass, R.string.preference_captcha_pass_summary,
						authorization.fieldsCount, authorization.hints, InputType.TYPE_CLASS_TEXT
						| InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, null);
			}
		}
		if (configuration.getOption(ChanConfiguration.OPTION_ALLOW_USER_AUTHORIZATION)) {
			ChanConfiguration.Authorization authorization = configuration.safe().obtainUserAuthorization();
			if (authorization != null && authorization.fieldsCount > 0) {
				userAuthorizationPreference = makeMultipleEditText(null, Preferences.KEY_USER_AUTHORIZATION
						.bind(chanName), null, R.string.preference_user_authorization, 0, authorization.fieldsCount,
						authorization.hints, InputType.TYPE_CLASS_TEXT
						| InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, null);
			}
		}
		LinkedHashMap<String, Boolean> customPreferences = configuration.getCustomPreferences();
		if (customPreferences != null) {
			for (LinkedHashMap.Entry<String, Boolean> preferenceHolder : customPreferences.entrySet()) {
				String key = preferenceHolder.getKey();
				boolean defaultValue = preferenceHolder.getValue();
				ChanConfiguration.CustomPreference customPreference = configuration.safe().obtainCustomPreference(key);
				if (customPreference != null && customPreference.title != null) {
					if (customPreferenceKeys == null) {
						customPreferenceKeys = new HashSet<>();
						customPreferenceKeys.add(key);
					}
					CheckBoxPreference preference = makeCheckBox(null, false, key, defaultValue,
							customPreference.title, customPreference.summary);
					preference.setChecked(configuration.get(null, key, defaultValue));
				}
			}
		}
		cookiePreference = makeButton(null, R.string.preference_delete_cookies, 0, false);
		Intent intent = new Intent(getActivity(), PreferencesActivity.class);
		intent.putExtra(PreferencesActivity.EXTRA_SHOW_FRAGMENT, CookiesFragment.class.getName());
		intent.putExtra(PreferencesActivity.EXTRA_NO_HEADERS, true);
		intent.putExtra(C.EXTRA_CHAN_NAME, chanName);
		cookiePreference.setIntent(intent);

		PreferenceCategory connectionCategory = makeCategory(R.string.preference_category_connection);
		ArrayList<String> domains = locator.getChanHosts(true);
		anotherDomainMode = !domains.contains(locator.getPreferredHost()) || domains.size() == 1 ||
				savedInstanceState != null && savedInstanceState.getBoolean(EXTRA_ANOTHER_DOMAIN_MODE);
		if (anotherDomainMode) {
			domainPreference2 = makeEditText(connectionCategory, Preferences.KEY_DOMAIN.bind(chanName), "",
					R.string.preference_domain, 0, domains.get(0), InputType.TYPE_CLASS_TEXT |
					InputType.TYPE_TEXT_VARIATION_URI, true);
		} else {
			String[] domainsArray = CommonUtils.toArray(domains, String.class);
			String[] entries = new String[domainsArray.length + 1];
			System.arraycopy(domainsArray, 0, entries, 0, domainsArray.length);
			entries[entries.length - 1] = getString(R.string.preference_domain_another);
			String[] values = new String[domainsArray.length + 1];
			values[0] = "";
			System.arraycopy(domainsArray, 1, values, 1, domainsArray.length - 1);
			values[values.length - 1] = VALUE_CUSTOM_DOMAIN;
			domainPreference1 = makeList(connectionCategory, Preferences.KEY_DOMAIN.bind(chanName), values,
					values[0], R.string.preference_domain, entries);
		}
		if (locator.isHttpsConfigurable()) {
			useHttpsPreference = makeCheckBox(connectionCategory, true, Preferences.KEY_USE_HTTPS.bind(chanName),
					Preferences.DEFAULT_USE_HTTPS, R.string.preference_use_https,
					R.string.preference_use_https_summary);
		}
		if (!configuration.getOption(ChanConfiguration.OPTION_HIDDEN_DISALLOW_PROXY)) {
			proxyPreference = makeMultipleEditText(connectionCategory, Preferences.KEY_PROXY.bind(chanName), null,
					R.string.preference_proxy, 0, 3, new String[] {getString(R.string.text_address),
					getString(R.string.text_port), null}, new int[] {InputType.TYPE_CLASS_TEXT |
					InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_CLASS_NUMBER |
					InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, 0}, "%s:%s");
			proxyPreference.replaceWithDropdown(2, Preferences.ENTRIES_PROXY_2, Preferences.VALUES_PROXY_2);
		}
		if (configuration.getOption(ChanConfiguration.OPTION_READ_THREAD_PARTIALLY)) {
			makeCheckBox(connectionCategory, true, Preferences.KEY_PARTIAL_THREAD_LOADING.bind(chanName),
					Preferences.DEFAULT_PARTIAL_THREAD_LOADING, R.string.preference_partial_thread_loading,
					R.string.preference_partial_thread_loading_summary);
		}

		PreferenceCategory additionalCategory = makeCategory(R.string.preference_category_additional);
		uninstallExtensionPreference = makeButton(additionalCategory, R.string.preference_uninstall_extension,
				0, false);

		if (defaultBoardPreference != null) {
			updateDefaultBoardSummary();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (cookiePreference != null) {
			ChanConfiguration configuration = ChanConfiguration.get(chanName);
			if (!configuration.hasCookiesWithDisplayName()) {
				PreferenceGroup preferenceGroup = getParentGroup(cookiePreference);
				if (preferenceGroup != null) {
					preferenceGroup.removePreference(cookiePreference);
				}
				cookiePreference = null;
			}
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean(EXTRA_ANOTHER_DOMAIN_MODE, anotherDomainMode);
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if (preference == captchaPassPreference) {
			String[] values = (String[]) newValue;
			if (Preferences.checkHasMultipleValues(values)) {
				new AuthorizationFragment(chanName, AUTHORIZATION_TYPE_CAPTCHA_PASS, values).show(this);
			}
			return true;
		} else if (preference == userAuthorizationPreference) {
			String[] values = (String[]) newValue;
			if (Preferences.checkHasMultipleValues(values)) {
				new AuthorizationFragment(chanName, AUTHORIZATION_TYPE_USER, values).show(this);
			}
			return true;
		} else if (preference == domainPreference1 || preference == domainPreference2
				|| preference == useHttpsPreference) {
			if (preference == domainPreference1 && VALUE_CUSTOM_DOMAIN.equals(newValue)) {
				PreferenceGroup preferenceGroup = getParentGroup(preference);
				if (preferenceGroup != null) {
					int index = -1;
					for (int i = 0; i < preferenceGroup.getPreferenceCount(); i++) {
						if (preferenceGroup.getPreference(i) == preference) {
							index = i;
							break;
						}
					}
					if (index >= 0) {
						domainPreference2 = makeEditText(preferenceGroup, Preferences.KEY_DOMAIN.bind(chanName), "",
								R.string.preference_domain, 0, domainPreference1.getEntries()[0].toString(),
								InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, true);
						preferenceGroup.removePreference(preference);
						preferenceGroup.addPreference(domainPreference2);
						domainPreference2.setOrder(0);
						domainPreference1 = null;
						expandDialog(domainPreference2);
						anotherDomainMode = true;
					}
				}
				return false;
			}
			boolean result = true;
			if (preference == domainPreference2 && newValue.toString().equals(domainPreference2
					.getEditText().getHint())) {
				domainPreference2.setText("");
				result = false;
			}
			ChanConfiguration configuration = ChanConfiguration.get(chanName);
			configuration.storeCookie(CloudFlarePasser.COOKIE_CLOUDFLARE, null, null);
			configuration.commit();
			return result;
		} else if (customPreferenceKeys != null && customPreferenceKeys.contains(preference.getKey())) {
			ChanConfiguration configuration = ChanConfiguration.get(chanName);
			configuration.set(null, preference.getKey(), (Boolean) newValue);
			configuration.commit();
			return true;
		}
		return super.onPreferenceChange(preference, newValue);
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference == uninstallExtensionPreference) {
			String packageName = null;
			for (ChanManager.ExtensionItem chanItem : ChanManager.getInstance().getChanItems()) {
				if (chanName.equals(chanItem.extensionName)) {
					packageName = chanItem.packageInfo.packageName;
					break;
				}
			}
			Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE).setData(Uri.parse("package:" + packageName))
					.putExtra(Intent.EXTRA_RETURN_RESULT, true);
			startActivityForResult(intent, C.REQUEST_CODE_UNINSTALL);
			return true;
		}
		return super.onPreferenceClick(preference);
	}

	@Override
	public void onPreferenceAfterChange(Preference preference) {
		super.onPreferenceAfterChange(preference);
		if (preference == defaultBoardPreference) {
			updateDefaultBoardSummary();
		} else if (preference == proxyPreference) {
			boolean success = HttpClient.getInstance().updateProxy(chanName);
			if (!success) {
				ToastUtils.show(getActivity(), R.string.message_enter_valid_data);
				expandDialog(preference);
			}
		} else if (preference == passwordPreference) {
			String value = passwordPreference.getText();
			if (StringUtils.isEmpty(value)) {
				passwordPreference.setText(Preferences.getPassword(chanName));
				ToastUtils.show(getActivity(), R.string.message_new_password);
			}
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == C.REQUEST_CODE_UNINSTALL && resultCode == Activity.RESULT_OK) {
			MessageDialog.create(MessageDialog.TYPE_UNINSTALL_REMINDER, this, false);
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	private void updateDefaultBoardSummary() {
		EditTextPreference preference = defaultBoardPreference;
		String text = preference.getText();
		if (!StringUtils.isEmpty(text)) {
			String boardName = StringUtils.validateBoardName(text);
			if (boardName != null) {
				text = StringUtils.formatBoardTitle(chanName, boardName,
						ChanConfiguration.get(chanName).getBoardTitle(boardName));
			} else {
				text = null;
			}
		}
		preference.setSummary(text);
	}

	private static final int AUTHORIZATION_TYPE_CAPTCHA_PASS = 0;
	private static final int AUTHORIZATION_TYPE_USER = 1;

	public static class AuthorizationFragment extends DialogFragment implements AsyncManager.Callback {
		private static final String TASK_CHECK_AUTHORIZATION = "check_authorization";

		private static final String EXTRA_CHAN_NAME = "chanName";
		private static final String EXTRA_AUTHORIZATION_TYPE = "authorizationType";
		private static final String EXTRA_AUTHORIZATION_DATA = "authorizationData";

		public AuthorizationFragment() {}

		public AuthorizationFragment(String chanName, int authorizationType, String[] authorizationData) {
			Bundle args = new Bundle();
			args.putString(EXTRA_CHAN_NAME, chanName);
			args.putInt(EXTRA_AUTHORIZATION_TYPE, authorizationType);
			args.putStringArray(EXTRA_AUTHORIZATION_DATA, authorizationData);
			setArguments(args);
		}

		@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
		public void show(Fragment parent) {
			show(C.API_JELLY_BEAN_MR1 ? parent.getChildFragmentManager() : parent.getFragmentManager(),
					getClass().getName());
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			ProgressDialog dialog = new ProgressDialog(getActivity());
			dialog.setCanceledOnTouchOutside(false);
			dialog.setMessage(getString(R.string.message_loading));
			return dialog;
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
			AsyncManager.get(this).startTask(TASK_CHECK_AUTHORIZATION, this, null, false);
		}

		@Override
		public void onCancel(DialogInterface dialog) {
			super.onCancel(dialog);
			AsyncManager.get(this).cancelTask(TASK_CHECK_AUTHORIZATION, this);
		}

		@Override
		public AsyncManager.Holder onCreateAndExecuteTask(String name, HashMap<String, Object> extra) {
			Bundle args = getArguments();
			CheckAuthorizationTask task = new CheckAuthorizationTask(args.getString(EXTRA_CHAN_NAME),
					args.getInt(EXTRA_AUTHORIZATION_TYPE), args.getStringArray(EXTRA_AUTHORIZATION_DATA));
			task.executeOnExecutor(CheckAuthorizationTask.THREAD_POOL_EXECUTOR);
			return task.getHolder();
		}

		@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
		@Override
		public void onFinishTaskExecution(String name, AsyncManager.Holder holder) {
			dismiss();
			boolean valid = holder.nextArgument();
			ErrorItem errorItem = holder.nextArgument();
			boolean expandPreference;
			if (errorItem == null) {
				ToastUtils.show(getActivity(), valid ? R.string.message_validation_completed
						: R.string.message_invalid_authorization_data);
				expandPreference = !valid;
			} else {
				ToastUtils.show(getActivity(), errorItem);
				expandPreference = true;
			}
			if (C.API_JELLY_BEAN_MR1 && expandPreference) {
				Fragment fragment = getParentFragment();
				if (fragment instanceof ChanFragment) {
					ChanFragment chanFragment = ((ChanFragment) fragment);
					Preference preference = null;
					switch (getArguments().getInt(EXTRA_AUTHORIZATION_TYPE)) {
						case AUTHORIZATION_TYPE_CAPTCHA_PASS: {
							preference = chanFragment.captchaPassPreference;
							break;
						}
						case AUTHORIZATION_TYPE_USER: {
							preference = chanFragment.userAuthorizationPreference;
							break;
						}
					}
					chanFragment.expandDialog(preference);
				}
			}
		}

		@Override
		public void onRequestTaskCancel(String name, Object task) {
			((CheckAuthorizationTask) task).cancel();
		}
	}

	private static class CheckAuthorizationTask extends AsyncManager.SimpleTask<Void, Void, Void> {
		private final HttpHolder holder = new HttpHolder();

		private final String chanName;
		private final int authorizationType;
		private final String[] authorizationData;

		private boolean valid;
		private ErrorItem errorItem;

		public CheckAuthorizationTask(String chanName, int authorizationType, String[] authorizationData) {
			this.chanName = chanName;
			this.authorizationType = authorizationType;
			this.authorizationData = authorizationData;
		}

		@Override
		public Void doInBackground(Void... params) {
			try {
				ChanPerformer performer = ChanPerformer.get(chanName);
				int type = -1;
				switch (authorizationType) {
					case AUTHORIZATION_TYPE_CAPTCHA_PASS: {
						type = ChanPerformer.CheckAuthorizationData.TYPE_CAPTCHA_PASS;
						break;
					}
					case AUTHORIZATION_TYPE_USER: {
						type = ChanPerformer.CheckAuthorizationData.TYPE_USER_AUTHORIZATION;
						break;
					}
				}
				ChanPerformer.CheckAuthorizationResult result = performer.safe().onCheckAuthorization
						(new ChanPerformer.CheckAuthorizationData(type, authorizationData, holder));
				valid = result != null && result.success;
			} catch (ExtensionException | HttpException | InvalidResponseException e) {
				errorItem = e.getErrorItemAndHandle();
			} finally {
				holder.cleanup();
				ChanConfiguration.get(chanName).commit();
			}
			return null;
		}

		@Override
		protected void onStoreResult(AsyncManager.Holder holder, Void result) {
			holder.storeResult(valid, errorItem);
		}

		@Override
		public void cancel() {
			cancel(true);
			holder.interrupt();
		}
	}
}