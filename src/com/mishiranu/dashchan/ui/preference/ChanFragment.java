package com.mishiranu.dashchan.ui.preference;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
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
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.AsyncManager;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.net.RelayBlockResolver;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.CheckPreference;
import com.mishiranu.dashchan.ui.preference.core.MultipleEditTextPreference;
import com.mishiranu.dashchan.ui.preference.core.Preference;
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.widget.ProgressDialog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ChanFragment extends PreferenceFragment {
	private static final String EXTRA_CHAN_NAME = "chanName";

	private Preference<String[]> captchaPassPreference;
	private Preference<String[]> userAuthorizationPreference;
	private Preference<?> cookiePreference;

	private static final String VALUE_CUSTOM_DOMAIN = "custom_domain\n";
	private static final String EXTRA_ANOTHER_DOMAIN_MODE = "anotherDomainMode";

	private boolean anotherDomainMode = false;

	public ChanFragment() {}

	public ChanFragment(String chanName) {
		Bundle args = new Bundle();
		args.putString(EXTRA_CHAN_NAME, chanName);
		setArguments(args);
	}

	private String getChanName() {
		return requireArguments().getString(EXTRA_CHAN_NAME);
	}

	@Override
	protected SharedPreferences getPreferences() {
		return Preferences.PREFERENCES;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		String chanName = getChanName();

		ChanConfiguration configuration = ChanConfiguration.get(chanName);
		ChanLocator locator = ChanLocator.get(chanName);
		ChanConfiguration.Board board = configuration.safe().obtainBoard(null);
		ChanConfiguration.Deleting deleting = board.allowDeleting ? configuration.safe().obtainDeleting(null) : null;

		if (!configuration.getOption(ChanConfiguration.OPTION_SINGLE_BOARD_MODE)) {
			addEdit(Preferences.KEY_DEFAULT_BOARD_NAME.bind(chanName), null,
					R.string.default_starting_board, p -> {
						String text = p.getValue();
						if (!StringUtils.isEmpty(text)) {
							String boardName = StringUtils.validateBoardName(text);
							if (boardName != null) {
								text = StringUtils.formatBoardTitle(chanName, boardName,
										ChanConfiguration.get(chanName).getBoardTitle(boardName));
							} else {
								text = null;
							}
						}
						return text;
					}, null, InputType.TYPE_CLASS_TEXT);
		}
		if (board.allowCatalog) {
			addCheck(true, Preferences.KEY_LOAD_CATALOG.bind(chanName), Preferences.DEFAULT_LOAD_CATALOG,
					R.string.load_catalog, R.string.load_catalog__summary);
		}
		if (deleting != null && deleting.password) {
			Preferences.getPassword(chanName); // Ensure password existence
			addEdit(Preferences.KEY_PASSWORD.bind(chanName), null,
					R.string.password_for_removal, R.string.password_for_removal__summary,
					getString(R.string.password), InputType.TYPE_CLASS_TEXT |
					InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
					.setOnAfterChangeListener(p -> {
						String value = p.getValue();
						if (StringUtils.isEmpty(value)) {
							p.setValue(Preferences.getPassword(getChanName()));
							ToastUtils.show(requireContext(), R.string.new_password_was_generated);
						}
					});
		}
		Collection<String> captchaTypes = configuration.getSupportedCaptchaTypes();
		if (captchaTypes != null && captchaTypes.size() > 1) {
			String[] values = Preferences.getCaptchaTypeValues(captchaTypes);
			addList(Preferences.KEY_CAPTCHA.bind(chanName), values,
					Preferences.getCaptchaTypeDefaultValue(chanName), R.string.captcha_type,
					Preferences.getCaptchaTypeEntries(chanName, captchaTypes));
		}
		if (configuration.getOption(ChanConfiguration.OPTION_ALLOW_CAPTCHA_PASS)) {
			ChanConfiguration.Authorization authorization = configuration.safe().obtainCaptchaPass();
			if (authorization != null && authorization.fieldsCount > 0) {
				captchaPassPreference = addMultipleEdit(Preferences.KEY_CAPTCHA_PASS.bind(chanName),
						R.string.captcha_pass, R.string.captcha_pass__summary, authorization.hints,
						createInputTypes(authorization.fieldsCount,
								InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
				captchaPassPreference.setOnAfterChangeListener(p -> {
					String[] values = p.getValue();
					if (Preferences.checkHasMultipleValues(values)) {
						new AuthorizationFragment(getChanName(), AuthorizationType.CAPTCHA_PASS, values).show(this);
					}
				});
			}
		}
		if (configuration.getOption(ChanConfiguration.OPTION_ALLOW_USER_AUTHORIZATION)) {
			ChanConfiguration.Authorization authorization = configuration.safe().obtainUserAuthorization();
			if (authorization != null && authorization.fieldsCount > 0) {
				userAuthorizationPreference = addMultipleEdit(Preferences.KEY_USER_AUTHORIZATION.bind(chanName),
						R.string.user_authorization, 0, authorization.hints,
						createInputTypes(authorization.fieldsCount,
								InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
				userAuthorizationPreference.setOnAfterChangeListener(p -> {
					String[] values = p.getValue();
					if (Preferences.checkHasMultipleValues(values)) {
						new AuthorizationFragment(getChanName(), AuthorizationType.USER, values).show(this);
					}
				});
			}
		}
		LinkedHashMap<String, Boolean> customPreferences = configuration.getCustomPreferences();
		if (customPreferences != null) {
			for (LinkedHashMap.Entry<String, Boolean> preferenceHolder : customPreferences.entrySet()) {
				String key = preferenceHolder.getKey();
				boolean defaultValue = preferenceHolder.getValue();
				ChanConfiguration.CustomPreference customPreference = configuration.safe().obtainCustomPreference(key);
				if (customPreference != null && customPreference.title != null) {
					CheckPreference preference = addCheck(false, key, defaultValue,
							customPreference.title, customPreference.summary);
					preference.setValue(configuration.get(null, key, defaultValue));
					preference.setOnAfterChangeListener(p -> {
						configuration.set(null, preference.key, p.getValue());
						configuration.commit();
					});
				}
			}
		}
		cookiePreference = addButton(R.string.manage_cookies, 0);
		cookiePreference.setOnClickListener(p -> ((FragmentHandler) requireActivity())
				.pushFragment(new CookiesFragment(chanName)));

		ArrayList<String> domains = locator.getChanHosts(true);
		boolean localMode = configuration.getOption(ChanConfiguration.OPTION_LOCAL_MODE) || domains.isEmpty();
		boolean httpsConfigurable = locator.isHttpsConfigurable();
		boolean canReadThreadPartially = configuration.getOption(ChanConfiguration.OPTION_READ_THREAD_PARTIALLY);
		if (!localMode || httpsConfigurable || canReadThreadPartially) {
			addHeader(R.string.connection);
		}
		if (!localMode) {
			anotherDomainMode = !domains.contains(locator.getPreferredHost()) || domains.size() == 1 ||
					savedInstanceState != null && savedInstanceState.getBoolean(EXTRA_ANOTHER_DOMAIN_MODE);
			if (anotherDomainMode) {
				addAnotherDomainPreference(domains.get(0));
			} else {
				String[] domainsArray = CommonUtils.toArray(domains, String.class);
				String[] entries = new String[domainsArray.length + 1];
				System.arraycopy(domainsArray, 0, entries, 0, domainsArray.length);
				entries[entries.length - 1] = getString(R.string.another);
				String[] values = new String[domainsArray.length + 1];
				values[0] = "";
				System.arraycopy(domainsArray, 1, values, 1, domainsArray.length - 1);
				values[values.length - 1] = VALUE_CUSTOM_DOMAIN;
				Preference<String> domainPreference = addList(Preferences.KEY_DOMAIN.bind(chanName), values,
						values[0], R.string.domain_name, entries);
				domainPreference.setOnAfterChangeListener(p -> clearSpecialCookies());
				domainPreference.setOnBeforeChangeListener((preference, value) -> {
					if (VALUE_CUSTOM_DOMAIN.equals(value)) {
						anotherDomainMode = true;
						Preference<String> newDomainPreference = addAnotherDomainPreference(domains.get(0));
						movePreference(newDomainPreference, domainPreference);
						removePreference(domainPreference);
						newDomainPreference.performClick();
						return false;
					}
					return true;
				});
			}
		}
		if (httpsConfigurable) {
			addCheck(true, Preferences.KEY_USE_HTTPS.bind(chanName), Preferences.DEFAULT_USE_HTTPS,
					R.string.secure_connection, R.string.secure_connection__summary)
					.setOnAfterChangeListener(p -> clearSpecialCookies());
		}
		if (!localMode) {
			MultipleEditTextPreference proxyPreference = addMultipleEdit(Preferences.KEY_PROXY.bind(chanName),
					R.string.proxy, "%s:%s", new String[] {getString(R.string.address),
					getString(R.string.port), null}, new int[] {InputType.TYPE_CLASS_TEXT |
					InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_CLASS_NUMBER |
					InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, 0});
			proxyPreference.setValues(2, Preferences.ENTRIES_PROXY_2, Preferences.VALUES_PROXY_2);
			proxyPreference.setOnAfterChangeListener(p -> {
				boolean success = HttpClient.getInstance().checkProxyValid(p.getValue());
				if (!success) {
					ToastUtils.show(requireContext(), R.string.enter_valid_data);
					proxyPreference.performClick();
				}
			});
		}
		if (canReadThreadPartially) {
			addCheck(true, Preferences.KEY_PARTIAL_THREAD_LOADING.bind(chanName),
					Preferences.DEFAULT_PARTIAL_THREAD_LOADING, R.string.partial_thread_loading,
					R.string.partial_thread_loading__summary);
		}

		addHeader(R.string.additional);
		addButton(R.string.uninstall_extension, 0).setOnClickListener(p -> {
			String packageName = ChanManager.getInstance().getExtensionPackageName(getChanName());
			@SuppressWarnings("deprecation")
			Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE)
					.setData(Uri.parse("package:" + packageName))
					.putExtra(Intent.EXTRA_RETURN_RESULT, true);
			startActivityForResult(intent, C.REQUEST_CODE_UNINSTALL);
		});
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		captchaPassPreference = null;
		userAuthorizationPreference = null;
		cookiePreference = null;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		ChanConfiguration configuration = ChanConfiguration.get(getChanName());
		((FragmentHandler) requireActivity()).setTitleSubtitle(configuration.getTitle(), null);
		if (!ChanManager.getInstance().isExistingChanName(getChanName())) {
			((FragmentHandler) requireActivity()).removeFragment();
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		// Check every time returned from cookies fragment
		removeCookiePreferenceIfNotNeeded();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean(EXTRA_ANOTHER_DOMAIN_MODE, anotherDomainMode);
	}

	private void removeCookiePreferenceIfNotNeeded() {
		if (cookiePreference != null) {
			ChanConfiguration configuration = ChanConfiguration.get(getChanName());
			if (!configuration.hasCookies()) {
				removePreference(cookiePreference);
				cookiePreference = null;
			}
		}
	}

	private void clearSpecialCookies() {
		Map<String, String> cookies = RelayBlockResolver.getInstance().getCookies(getChanName());
		if (!cookies.isEmpty()) {
			ChanConfiguration configuration = ChanConfiguration.get(getChanName());
			for (String cookie : cookies.keySet()) {
				configuration.storeCookie(cookie, null, null);
			}
			configuration.commit();
		}
		removeCookiePreferenceIfNotNeeded();
	}

	private Preference<String> addAnotherDomainPreference(String primaryDomain) {
		Preference<String> preference = addEdit(Preferences.KEY_DOMAIN.bind(getChanName()), "",
				R.string.domain_name, primaryDomain, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
		preference.setOnAfterChangeListener(p -> clearSpecialCookies());
		preference.setOnBeforeChangeListener((p, value) -> {
			if (primaryDomain.equals(value)) {
				p.setValue("");
				return false;
			}
			return true;
		});
		return preference;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == C.REQUEST_CODE_UNINSTALL && resultCode == Activity.RESULT_OK) {
			MessageDialog.create(this, getString(R.string.changes_after_restart__sentence), true);
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	private enum AuthorizationType {CAPTCHA_PASS, USER}

	public static class AuthorizationFragment extends DialogFragment implements AsyncManager.Callback {
		private static final String TASK_CHECK_AUTHORIZATION = "check_authorization";

		private static final String EXTRA_CHAN_NAME = "chanName";
		private static final String EXTRA_AUTHORIZATION_TYPE = "authorizationType";
		private static final String EXTRA_AUTHORIZATION_DATA = "authorizationData";

		public AuthorizationFragment() {}

		public AuthorizationFragment(String chanName,
				AuthorizationType authorizationType, String[] authorizationData) {
			Bundle args = new Bundle();
			args.putString(EXTRA_CHAN_NAME, chanName);
			args.putString(EXTRA_AUTHORIZATION_TYPE, authorizationType.name());
			args.putStringArray(EXTRA_AUTHORIZATION_DATA, authorizationData);
			setArguments(args);
		}

		public void show(Fragment parent) {
			show(parent.getChildFragmentManager(), getClass().getName());
		}

		@NonNull
		@Override
		public ProgressDialog onCreateDialog(Bundle savedInstanceState) {
			ProgressDialog dialog = new ProgressDialog(requireContext(), null);
			dialog.setMessage(getString(R.string.loading__ellipsis));
			return dialog;
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
			AsyncManager.get(this).startTask(TASK_CHECK_AUTHORIZATION, this, null, false);
		}

		@Override
		public void onCancel(@NonNull DialogInterface dialog) {
			super.onCancel(dialog);
			AsyncManager.get(this).cancelTask(TASK_CHECK_AUTHORIZATION, this);
		}

		@Override
		public AsyncManager.Holder onCreateAndExecuteTask(String name, HashMap<String, Object> extra) {
			Bundle args = requireArguments();
			CheckAuthorizationTask task = new CheckAuthorizationTask(args.getString(EXTRA_CHAN_NAME),
					AuthorizationType.valueOf(args.getString(EXTRA_AUTHORIZATION_TYPE)),
					args.getStringArray(EXTRA_AUTHORIZATION_DATA));
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
				ToastUtils.show(requireContext(), valid ? R.string.validation_completed
						: R.string.invalid_authorization_data);
				expandPreference = !valid;
			} else {
				ToastUtils.show(requireContext(), errorItem);
				expandPreference = true;
			}
			if (expandPreference) {
				Fragment fragment = getParentFragment();
				if (fragment instanceof ChanFragment) {
					ChanFragment chanFragment = ((ChanFragment) fragment);
					Preference<?> preference = null;
					switch (AuthorizationType.valueOf(requireArguments().getString(EXTRA_AUTHORIZATION_TYPE))) {
						case CAPTCHA_PASS: {
							preference = chanFragment.captchaPassPreference;
							break;
						}
						case USER: {
							preference = chanFragment.userAuthorizationPreference;
							break;
						}
					}
					if (preference != null) {
						preference.performClick();
					}
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
		private final AuthorizationType authorizationType;
		private final String[] authorizationData;

		private boolean valid;
		private ErrorItem errorItem;

		public CheckAuthorizationTask(String chanName,
				AuthorizationType authorizationType, String[] authorizationData) {
			this.chanName = chanName;
			this.authorizationType = authorizationType;
			this.authorizationData = authorizationData;
		}

		@Override
		public Void doInBackground(Void... params) {
			try (HttpHolder holder = this.holder) {
				ChanPerformer performer = ChanPerformer.get(chanName);
				int type = -1;
				switch (authorizationType) {
					case CAPTCHA_PASS: {
						type = ChanPerformer.CheckAuthorizationData.TYPE_CAPTCHA_PASS;
						break;
					}
					case USER: {
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
