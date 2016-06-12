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

package com.mishiranu.dashchan.preference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.text.InputType;
import android.util.Pair;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpClient;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.app.PreferencesActivity;
import com.mishiranu.dashchan.async.AsyncManager;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.net.CloudFlarePasser;
import com.mishiranu.dashchan.util.ToastUtils;

public class ChanFragment extends BasePreferenceFragment
{
	private String mChanName;
	
	private EditTextPreference mDefaultBoardPreference;
	private MultipleEditTextPreference mCaptchaPassPreference;
	private MultipleEditTextPreference mUserAuthorizationPreference;
	private ListPreference mDomainPreference1;
	private EditTextPreference mDomainPreference2;
	private EditTextPreference mPasswordPreference;
	private CheckBoxPreference mUseHttpsPreference;
	private MultipleEditTextPreference mProxyPreference;
	private Preference mUninstallExtensionPreference;
	
	private HashSet<String> mCustomPreferenceKeys;
	
	private static String VALUE_CUSTOM_DOMAIN = "custom_domain\n";
	private static String EXTRA_ANOTHER_DOMAIN_MODE = "another_domain_mode";
	
	private boolean mAnotherDomainMode = false;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		String chanName = getActivity().getIntent().getStringExtra(C.EXTRA_CHAN_NAME);
		if (chanName == null)
		{
			LinkedHashSet<String> chanNames = ChanManager.getInstance().getAvailableChanNames();
			if (chanNames.size() == 0) throw new IllegalStateException();
			chanName = chanNames.iterator().next();
		}
		mChanName = chanName;
		ChanConfiguration configuration = ChanConfiguration.get(chanName);
		ChanLocator locator = ChanLocator.get(chanName);
		getActivity().setTitle(configuration.getTitle());
		ChanConfiguration.Board board = configuration.safe().obtainBoard(null);
		ChanConfiguration.Deleting deleting = board.allowDeleting ? configuration.safe().obtainDeleting(null) : null;
		
		if (!configuration.getOption(ChanConfiguration.OPTION_SINGLE_BOARD_MODE))
		{
			mDefaultBoardPreference = makeEditText(null, Preferences.KEY_DEFAULT_BOARD_NAME.bind(chanName), null,
					R.string.preference_default_board_name, 0, null, InputType.TYPE_CLASS_TEXT, false);
		}
		if (board.allowCatalog)
		{
			makeCheckBox(null, true, Preferences.KEY_LOAD_CATALOG.bind(chanName), Preferences.DEFAULT_LOAD_CATALOG,
					R.string.preference_load_catalog, R.string.preference_load_catalog_summary);
		}
		if (deleting != null && deleting.password)
		{
			Preferences.getPassword(chanName); // Ensure password existence
			mPasswordPreference = makeEditText(null, Preferences.KEY_PASSWORD.bind(chanName), null,
					R.string.preference_password_for_removal, R.string.preference_password_for_removal_summary,
					getString(R.string.text_password), InputType.TYPE_CLASS_TEXT |
					InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, false);
		}
		String[] captchaTypes = configuration.getSupportedCaptchaTypes();
		if (captchaTypes != null && captchaTypes.length > 1)
		{
			String[] values = Preferences.getCaptchaTypeValues(captchaTypes);
			makeList(null, Preferences.KEY_CAPTCHA.bind(chanName), values,
					Preferences.getCaptchaTypeDefaultValue(chanName), R.string.preference_captcha,
					Preferences.getCaptchaTypeEntries(chanName, captchaTypes));
		}
		if (configuration.getOption(ChanConfiguration.OPTION_ALLOW_CAPTCHA_PASS))
		{
			ChanConfiguration.Authorization authorization = configuration.safe().obtainCaptchaPass();
			if (authorization != null && authorization.fieldsCount > 0)
			{
				mCaptchaPassPreference = makeMultipleEditText(null, Preferences.KEY_CAPTCHA_PASS.bind(chanName),
						null, R.string.preference_captcha_pass, R.string.preference_captcha_pass_summary,
						authorization.fieldsCount, authorization.hints, InputType.TYPE_CLASS_TEXT
						| InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, null);
			}
		}
		if (configuration.getOption(ChanConfiguration.OPTION_ALLOW_USER_AUTHORIZATION))
		{
			ChanConfiguration.Authorization authorization = configuration.safe().obtainUserAuthorization();
			if (authorization != null && authorization.fieldsCount > 0)
			{
				mUserAuthorizationPreference = makeMultipleEditText(null, Preferences.KEY_USER_AUTHORIZATION
						.bind(chanName), null, R.string.preference_user_authorization, 0, authorization.fieldsCount,
						authorization.hints, InputType.TYPE_CLASS_TEXT
						| InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, null);
			}
		}
		ArrayList<Pair<String, Boolean>> customPreferences = configuration.getCustomPreferences();
		if (customPreferences != null)
		{
			for (Pair<String, Boolean> preferenceHolder : customPreferences)
			{
				ChanConfiguration.CustomPreference customPreference = configuration
						.safe().obtainCustomPreference(preferenceHolder.first);
				if (customPreference != null && customPreference.title != null)
				{
					String key = preferenceHolder.first;
					boolean defaultValue = preferenceHolder.second;
					if (mCustomPreferenceKeys == null)
					{
						mCustomPreferenceKeys = new HashSet<>();
						mCustomPreferenceKeys.add(key);
					}
					CheckBoxPreference preference = makeCheckBox(null, false, key, defaultValue,
							customPreference.title, customPreference.summary);
					preference.setChecked(configuration.get(null, key, defaultValue));
				}
			}
		}
		if (configuration.hasCookies())
		{
			Preference preference = makeButton(null, R.string.preference_delete_cookies, 0, false);
			Intent intent = new Intent(getActivity(), PreferencesActivity.class);
			intent.putExtra(PreferencesActivity.EXTRA_SHOW_FRAGMENT, CookiesFragment.class.getName());
			intent.putExtra(PreferencesActivity.EXTRA_NO_HEADERS, true);
			intent.putExtra(C.EXTRA_CHAN_NAME, chanName);
			preference.setIntent(intent);
		}
		
		PreferenceCategory connectionCategory = makeCategory(R.string.preference_category_connection);
		ArrayList<String> domains = locator.getChanHosts(true);
		mAnotherDomainMode = !domains.contains(locator.getPreferredHost()) || domains.size() == 1 ||
				savedInstanceState != null && savedInstanceState.getBoolean(EXTRA_ANOTHER_DOMAIN_MODE);
		if (mAnotherDomainMode)
		{
			mDomainPreference2 = makeEditText(connectionCategory, Preferences.KEY_DOMAIN.bind(chanName), "",
					R.string.preference_domain, 0, domains.get(0), InputType.TYPE_CLASS_TEXT |
					InputType.TYPE_TEXT_VARIATION_URI, true);
		}
		else
		{
			String[] domainsArray = domains.toArray(new String[domains.size()]);
			String[] entries = new String[domainsArray.length + 1];
			System.arraycopy(domainsArray, 0, entries, 0, domainsArray.length);
			entries[entries.length - 1] = getString(R.string.preference_domain_another);
			String[] values = new String[domainsArray.length + 1];
			values[0] = "";
			System.arraycopy(domainsArray, 1, values, 1, domainsArray.length - 1);
			values[values.length - 1] = VALUE_CUSTOM_DOMAIN;
			mDomainPreference1 = makeList(connectionCategory, Preferences.KEY_DOMAIN.bind(chanName), values,
					values[0], R.string.preference_domain, entries);
		}
		if (locator.isHttpsConfigurable())
		{
			mUseHttpsPreference = makeCheckBox(connectionCategory, true, Preferences.KEY_USE_HTTPS.bind(chanName),
					Preferences.DEFAULT_USE_HTTPS, R.string.preference_use_https,
					R.string.preference_use_https_summary);
		}
		if (!configuration.getOption(ChanConfiguration.OPTION_HIDDEN_DISALLOW_PROXY))
		{
			mProxyPreference = makeMultipleEditText(connectionCategory, Preferences.KEY_PROXY.bind(chanName), null,
					R.string.preference_proxy, 0, 2, new String[] {getString(R.string.text_address),
					getString(R.string.text_port)}, new int[] {InputType.TYPE_CLASS_TEXT |
					InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_CLASS_NUMBER |
					InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD}, "%s:%s");
		}
		if (configuration.getOption(ChanConfiguration.OPTION_READ_THREAD_PARTIALLY))
		{
			makeCheckBox(connectionCategory, true, Preferences.KEY_PARTIAL_THREAD_LOADING.bind(chanName),
					Preferences.DEFAULT_PARTIAL_THREAD_LOADING, R.string.preference_partial_thread_loading,
					R.string.preference_partial_thread_loading_summary);
		}
		
		PreferenceCategory additionalCategory = makeCategory(R.string.preference_category_additional);
		mUninstallExtensionPreference = makeButton(additionalCategory, R.string.preference_uninstall_extension,
				0, false);
		
		if (mDefaultBoardPreference != null) updateDefaultBoardSummary();
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		outState.putBoolean(EXTRA_ANOTHER_DOMAIN_MODE, mAnotherDomainMode);
	}
	
	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue)
	{
		if (preference == mCaptchaPassPreference)
		{
			String[] values = (String[]) newValue;
			if (Preferences.checkHasMultipleValues(values))
			{
				new AuthorizationFragment(mChanName, AUTHORIZATION_TYPE_CAPTCHA_PASS, values).show(this);
			}
			return true;
		}
		else if (preference == mUserAuthorizationPreference)
		{
			String[] values = (String[]) newValue;
			if (Preferences.checkHasMultipleValues(values))
			{
				new AuthorizationFragment(mChanName, AUTHORIZATION_TYPE_USER, values).show(this);
			}
			return true;
		}
		else if (preference == mDomainPreference1 || preference == mDomainPreference2
				|| preference == mUseHttpsPreference)
		{
			if (preference == mDomainPreference1 && VALUE_CUSTOM_DOMAIN.equals(newValue))
			{
				PreferenceGroup preferenceGroup = getParentGroup(preference);
				if (preferenceGroup != null)
				{
					int index = -1;
					for (int i = 0; i < preferenceGroup.getPreferenceCount(); i++)
					{
						if (preferenceGroup.getPreference(i) == preference)
						{
							index = i;
							break;
						}
					}
					if (index >= 0)
					{
						mDomainPreference2 = makeEditText(preferenceGroup, Preferences.KEY_DOMAIN.bind(mChanName), "",
								R.string.preference_domain, 0, mDomainPreference1.getEntries()[0].toString(),
								InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, true);
						preferenceGroup.removePreference(preference);
						preferenceGroup.addPreference(mDomainPreference2);
						mDomainPreference2.setOrder(0);
						mDomainPreference1 = null;
						expandDialog(mDomainPreference2);
						mAnotherDomainMode = true;
					}
				}
				return false;
			}
			boolean result = true;
			if (preference == mDomainPreference2 && newValue.toString().equals(mDomainPreference2
					.getEditText().getHint()))
			{
				mDomainPreference2.setText("");
				result = false;
			}
			ChanConfiguration configuration = ChanConfiguration.get(mChanName);
			configuration.storeCookie(CloudFlarePasser.COOKIE_CLOUDFLARE, null, null);
			configuration.commit();
			return result;
		}
		else if (mCustomPreferenceKeys != null && mCustomPreferenceKeys.contains(preference.getKey()))
		{
			ChanConfiguration configuration = ChanConfiguration.get(mChanName);
			configuration.set(null, preference.getKey(), (Boolean) newValue);
			configuration.commit();
			return true;
		}
		return super.onPreferenceChange(preference, newValue);
	}
	
	@Override
	public boolean onPreferenceClick(Preference preference)
	{
		if (preference == mUninstallExtensionPreference)
		{
			String packageName = null;
			for (ChanManager.ExtensionItem chanItem : ChanManager.getInstance().getChanItems())
			{
				if (mChanName.equals(chanItem.extensionName))
				{
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
	public void onPreferenceAfterChange(Preference preference)
	{
		super.onPreferenceAfterChange(preference);
		if (preference == mDefaultBoardPreference)
		{
			updateDefaultBoardSummary();
		}
		else if (preference == mProxyPreference)
		{
			boolean success = HttpClient.getInstance().updateProxy(mChanName);
			if (!success)
			{
				ToastUtils.show(getActivity(), R.string.message_enter_valid_data);
				expandDialog(preference);
			}
		}
		else if (preference == mPasswordPreference)
		{
			String value = mPasswordPreference.getText();
			if (StringUtils.isEmpty(value))
			{
				mPasswordPreference.setText(Preferences.getPassword(mChanName));
				ToastUtils.show(getActivity(), R.string.message_new_password);
			}
		}
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if (requestCode == C.REQUEST_CODE_UNINSTALL && resultCode == Activity.RESULT_OK)
		{
			MessageDialog.create(MessageDialog.TYPE_UNINSTALL_REMINDER, this, false);
		}
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	private void updateDefaultBoardSummary()
	{
		EditTextPreference preference = mDefaultBoardPreference;
		String text = preference.getText();
		if (!StringUtils.isEmpty(text))
		{
			String boardName = StringUtils.validateBoardName(text);
			if (boardName != null)
			{
				text = StringUtils.formatBoardTitle(mChanName, boardName,
						ChanConfiguration.get(mChanName).getBoardTitle(boardName));
			}
			else text = null;
		}
		preference.setSummary(text);
	}
	
	private static final int AUTHORIZATION_TYPE_CAPTCHA_PASS = 0;
	private static final int AUTHORIZATION_TYPE_USER = 1;
	
	public static class AuthorizationFragment extends DialogFragment implements AsyncManager.Callback
	{
		private static final String TASK_CHECK_AUTHORIZATION = "check_authorization";
		
		private static final String EXTRA_CHAN_NAME = "chanName";
		private static final String EXTRA_AUTHORIZATION_TYPE = "authorizationType";
		private static final String EXTRA_AUTHORIZATION_DATA = "authorizationData";
		
		public AuthorizationFragment()
		{
			
		}
		
		public AuthorizationFragment(String chanName, int authorizationType, String[] authorizationData)
		{
			Bundle args = new Bundle();
			args.putString(EXTRA_CHAN_NAME, chanName);
			args.putInt(EXTRA_AUTHORIZATION_TYPE, authorizationType);
			args.putStringArray(EXTRA_AUTHORIZATION_DATA, authorizationData);
			setArguments(args);
		}
		
		@SuppressLint("NewApi")
		public void show(Fragment parent)
		{
			show(C.API_JELLY_BEAN_MR1 ? parent.getChildFragmentManager() : parent.getFragmentManager(),
					getClass().getName());
		}
		
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState)
		{
			ProgressDialog dialog = new ProgressDialog(getActivity());
			dialog.setCanceledOnTouchOutside(false);
			dialog.setMessage(getString(R.string.message_loading));
			return dialog;
		}
		
		@Override
		public void onActivityCreated(Bundle savedInstanceState)
		{
			super.onActivityCreated(savedInstanceState);
			AsyncManager.get(this).startTask(TASK_CHECK_AUTHORIZATION, this, null, false);
		}
		
		@Override
		public void onCancel(DialogInterface dialog)
		{
			super.onCancel(dialog);
			AsyncManager.get(this).cancelTask(TASK_CHECK_AUTHORIZATION, this);
		}
		
		@Override
		public Pair<Object, AsyncManager.Holder> onCreateAndExecuteTask(String name, HashMap<String, Object> extra)
		{
			Bundle args = getArguments();
			CheckAuthorizationTask task = new CheckAuthorizationTask(args.getString(EXTRA_CHAN_NAME),
					args.getInt(EXTRA_AUTHORIZATION_TYPE), args.getStringArray(EXTRA_AUTHORIZATION_DATA));
			task.executeOnExecutor(CheckAuthorizationTask.THREAD_POOL_EXECUTOR);
			return task.getPair();
		}
		
		@SuppressLint("NewApi")
		@Override
		public void onFinishTaskExecution(String name, AsyncManager.Holder holder)
		{
			dismissAllowingStateLoss();
			boolean valid = holder.nextArgument();
			ErrorItem errorItem = holder.nextArgument();
			boolean expandPreference = false;
			if (errorItem == null)
			{
				ToastUtils.show(getActivity(), valid ? R.string.message_validation_completed
						: R.string.message_invalid_authorization_data);
				expandPreference = !valid;
			}
			else
			{
				ToastUtils.show(getActivity(), errorItem);
				expandPreference = true;
			}
			if (C.API_JELLY_BEAN_MR1 && expandPreference)
			{
				Fragment fragment = getParentFragment();
				if (fragment instanceof ChanFragment)
				{
					ChanFragment chanFragment = ((ChanFragment) fragment);
					Preference preference = null;
					switch (getArguments().getInt(EXTRA_AUTHORIZATION_TYPE))
					{
						case AUTHORIZATION_TYPE_CAPTCHA_PASS:
						{
							preference = chanFragment.mCaptchaPassPreference;
							break;
						}
						case AUTHORIZATION_TYPE_USER:
						{
							preference = chanFragment.mUserAuthorizationPreference;
							break;
						}
					}
					chanFragment.expandDialog(preference);
				}
			}
		}
		
		@Override
		public void onRequestTaskCancel(String name, Object task)
		{
			((CheckAuthorizationTask) task).cancel();
		}
	}
	
	private static class CheckAuthorizationTask extends AsyncManager.SimpleTask<Void, Void, Void>
	{
		private final HttpHolder mHolder = new HttpHolder();
		
		private final String mChanName;
		private final int mAuthorizationType;
		private final String[] mAuthorizationData;
		
		private boolean mValid;
		private ErrorItem mErrorItem;
		
		public CheckAuthorizationTask(String chanName, int authorizationType, String[] authorizationData)
		{
			mChanName = chanName;
			mAuthorizationType = authorizationType;
			mAuthorizationData = authorizationData;
		}
		
		@Override
		public Void doInBackground(Void... params)
		{
			try
			{
				ChanPerformer performer = ChanPerformer.get(mChanName);
				int type = -1;
				switch (mAuthorizationType)
				{
					case AUTHORIZATION_TYPE_CAPTCHA_PASS:
					{
						type = ChanPerformer.CheckAuthorizationData.TYPE_CAPTCHA_PASS;
						break;
					}
					case AUTHORIZATION_TYPE_USER:
					{
						type = ChanPerformer.CheckAuthorizationData.TYPE_USER_AUTHORIZATION;
						break;
					}
				}
				try
				{
					ChanPerformer.CheckAuthorizationResult result = performer.onCheckAuthorization
							(new ChanPerformer.CheckAuthorizationData(type, mAuthorizationData, mHolder));
					mValid = result != null && result.success;
				}
				catch (LinkageError | RuntimeException e)
				{
					mErrorItem = ExtensionException.obtainErrorItemAndLogException(e);
				}
			}
			catch (HttpException | InvalidResponseException e)
			{
				mErrorItem = e.getErrorItemAndHandle();
			}
			finally
			{
				ChanConfiguration.get(mChanName).commit();
			}
			return null;
		}
		
		@Override
		protected void onStoreResult(AsyncManager.Holder holder, Void result)
		{
			holder.storeResult(mValid, mErrorItem);
		}
		
		public void cancel()
		{
			cancel(true);
			mHolder.interrupt();
		}
	}
}