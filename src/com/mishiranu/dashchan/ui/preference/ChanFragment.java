package com.mishiranu.dashchan.ui.preference;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpClient;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.HttpHolderTask;
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.content.database.ChanDatabase;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.net.RelayBlockResolver;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.CheckPreference;
import com.mishiranu.dashchan.ui.preference.core.MultipleEditPreference;
import com.mishiranu.dashchan.ui.preference.core.Preference;
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.ProgressDialog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChanFragment extends PreferenceFragment implements FragmentHandler.Callback {
	private static final String EXTRA_CHAN_NAME = "chanName";

	private Preference<List<String>> captchaPassPreference;
	private Preference<List<String>> userAuthorizationPreference;
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
		Chan chan = Chan.get(chanName);
		ChanConfiguration.Board board = chan.configuration.safe().obtainBoard(null);
		ChanConfiguration.Deleting deleting = board.allowDeleting
				? chan.configuration.safe().obtainDeleting(null) : null;

		if (!chan.configuration.getOption(ChanConfiguration.OPTION_SINGLE_BOARD_MODE)) {
			addEdit(Preferences.KEY_DEFAULT_BOARD_NAME.bind(chanName), null,
					R.string.default_starting_board, p -> {
						String text = p.getValue();
						if (!StringUtils.isEmpty(text)) {
							String boardName = StringUtils.validateBoardName(text);
							if (boardName != null) {
								text = StringUtils.formatBoardTitle(chanName, boardName,
										Chan.get(chanName).configuration.getBoardTitle(boardName));
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
			Preferences.getPassword(chan); // Ensure password existence
			addEdit(Preferences.KEY_PASSWORD.bind(chanName), null,
					R.string.password_for_removal, R.string.password_for_removal__summary,
					getString(R.string.password), InputType.TYPE_CLASS_TEXT |
					InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
					.setOnAfterChangeListener(p -> {
						String value = p.getValue();
						if (StringUtils.isEmpty(value)) {
							p.setValue(Preferences.getPassword(Chan.get(chanName)));
							ClickableToast.show(R.string.new_password_was_generated);
						}
					});
		}
		Collection<String> captchaTypes = chan.configuration.getSupportedCaptchaTypes();
		if (captchaTypes != null && captchaTypes.size() > 1) {
			addList(Preferences.KEY_CAPTCHA.bind(chanName), Preferences.getCaptchaTypeValues(captchaTypes),
					Preferences.getCaptchaTypeDefaultValue(chan), R.string.captcha_type,
					Preferences.getCaptchaTypeEntries(chan, captchaTypes));
		}
		if (chan.configuration.getOption(ChanConfiguration.OPTION_ALLOW_CAPTCHA_PASS)) {
			ChanConfiguration.Authorization authorization = chan.configuration.safe().obtainCaptchaPass();
			if (authorization != null && authorization.fieldsCount > 0) {
				captchaPassPreference = addMultipleEdit(Preferences.KEY_CAPTCHA_PASS.bind(chanName),
						R.string.captcha_pass, R.string.captcha_pass__summary,
						authorization.hints != null ? Arrays.asList(authorization.hints) : null,
						createInputTypes(authorization.fieldsCount,
								InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD),
						new MultipleEditPreference.ListValueCodec(authorization.fieldsCount));
				captchaPassPreference.setOnAfterChangeListener(p -> {
					List<String> values = p.getValue();
					if (Preferences.checkHasMultipleValues(values)) {
						AuthorizationDialog dialog = new AuthorizationDialog(getChanName(),
								AuthorizationType.CAPTCHA_PASS, values);
						dialog.show(getChildFragmentManager(), AuthorizationDialog.class.getName());
					}
				});
			}
		}
		if (chan.configuration.getOption(ChanConfiguration.OPTION_ALLOW_USER_AUTHORIZATION)) {
			ChanConfiguration.Authorization authorization = chan.configuration.safe().obtainUserAuthorization();
			if (authorization != null && authorization.fieldsCount > 0) {
				userAuthorizationPreference = addMultipleEdit(Preferences.KEY_USER_AUTHORIZATION.bind(chanName),
						R.string.user_authorization, 0,
						authorization.hints != null ? Arrays.asList(authorization.hints) : null,
						createInputTypes(authorization.fieldsCount,
								InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD),
						new MultipleEditPreference.ListValueCodec(authorization.fieldsCount));
				userAuthorizationPreference.setOnAfterChangeListener(p -> {
					List<String> values = p.getValue();
					if (Preferences.checkHasMultipleValues(values)) {
						AuthorizationDialog dialog = new AuthorizationDialog(getChanName(),
								AuthorizationType.USER, values);
						dialog.show(getChildFragmentManager(), AuthorizationDialog.class.getName());
					}
				});
			}
		}
		LinkedHashMap<String, Boolean> customPreferences = chan.configuration.getCustomPreferences();
		if (customPreferences != null) {
			for (LinkedHashMap.Entry<String, Boolean> preferenceHolder : customPreferences.entrySet()) {
				String key = preferenceHolder.getKey();
				boolean defaultValue = preferenceHolder.getValue();
				ChanConfiguration.CustomPreference customPreference =
						chan.configuration.safe().obtainCustomPreference(key);
				if (customPreference != null && customPreference.title != null) {
					CheckPreference preference = addCheck(false, key, defaultValue,
							customPreference.title, customPreference.summary);
					preference.setValue(chan.configuration.get(null, key, defaultValue));
					preference.setOnAfterChangeListener(p -> {
						Chan callbackChan = Chan.get(chanName);
						callbackChan.configuration.set(null, preference.key, p.getValue());
						callbackChan.configuration.commit();
					});
				}
			}
		}
		cookiePreference = addButton(R.string.manage_cookies, 0);
		cookiePreference.setOnClickListener(p -> ((FragmentHandler) requireActivity())
				.pushFragment(new CookiesFragment(chanName)));

		ArrayList<String> domains = chan.locator.getChanHosts(true);
		boolean localMode = chan.configuration.getOption(ChanConfiguration.OPTION_LOCAL_MODE) || domains.isEmpty();
		boolean httpsConfigurable = chan.locator.isHttpsConfigurable();
		boolean canReadThreadPartially = chan.configuration.getOption(ChanConfiguration.OPTION_READ_THREAD_PARTIALLY);
		if (!localMode || httpsConfigurable || canReadThreadPartially) {
			addHeader(R.string.connection);
		}
		if (!localMode) {
			anotherDomainMode = !domains.contains(chan.locator.getPreferredHost()) || domains.size() == 1 ||
					savedInstanceState != null && savedInstanceState.getBoolean(EXTRA_ANOTHER_DOMAIN_MODE);
			if (anotherDomainMode) {
				addAnotherDomainPreference(domains.get(0));
			} else {
				ArrayList<CharSequence> entries = new ArrayList<>(domains);
				entries.add(getString(R.string.another));
				ArrayList<String> values = new ArrayList<>(domains);
				values.add(VALUE_CUSTOM_DOMAIN);
				values.set(0, "");
				Preference<String> domainPreference = addList(Preferences.KEY_DOMAIN.bind(chanName), values,
						values.get(0), R.string.domain_name, entries);
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
			MultipleEditPreference<Map<String, String>> proxyPreference = addMultipleEdit
					(Preferences.KEY_PROXY.bind(chanName), R.string.proxy, "%s:%s",
							Arrays.asList(getString(R.string.address), getString(R.string.port), null),
							Arrays.asList(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
									InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, 0),
							new MultipleEditPreference.MapValueCodec(Preferences.KEYS_PROXY));
			proxyPreference.setValues(Preferences.KEYS_PROXY.indexOf(Preferences.SUB_KEY_PROXY_TYPE),
					Preferences.ENTRIES_PROXY_TYPE, Preferences.VALUES_PROXY_TYPE);
			proxyPreference.setOnAfterChangeListener(p -> {
				boolean success = HttpClient.getInstance().checkProxyValid(p.getValue());
				if (!success) {
					ClickableToast.show(R.string.enter_valid_data);
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
			Chan innerChan = Chan.get(chanName);
			if (innerChan.name != null) {
				@SuppressWarnings("deprecation")
				Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE)
						.setData(Uri.parse("package:" + innerChan.packageName))
						.putExtra(Intent.EXTRA_RETURN_RESULT, true);
				startActivity(intent);
			}
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

		Chan chan = Chan.get(getChanName());
		((FragmentHandler) requireActivity()).setTitleSubtitle(chan.configuration.getTitle(), null);
	}

	@Override
	public void onResume() {
		super.onResume();

		if (!ChanManager.getInstance().isExistingChanName(getChanName())) {
			((FragmentHandler) requireActivity()).removeFragment();
		} else {
			// Check every time returned from cookies fragment
			removeCookiePreferenceIfNotNeeded();
		}
	}

	@Override
	public void onChansChanged(Collection<String> changed, Collection<String> removed) {
		if (changed.contains(getChanName()) || removed.contains(getChanName())) {
			// Don't bother with updating fragment
			((FragmentHandler) requireActivity()).removeFragment();
		}
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean(EXTRA_ANOTHER_DOMAIN_MODE, anotherDomainMode);
	}

	private void removeCookiePreferenceIfNotNeeded() {
		if (cookiePreference != null) {
			if (!ChanDatabase.getInstance().hasCookies(getChanName())) {
				removePreference(cookiePreference);
				cookiePreference = null;
			}
		}
	}

	private void clearSpecialCookies() {
		Chan chan = Chan.get(getChanName());
		Map<String, String> cookies = RelayBlockResolver.getInstance().getCookies(chan);
		if (!cookies.isEmpty()) {
			for (String cookie : cookies.keySet()) {
				chan.configuration.storeCookie(cookie, null, null);
			}
			chan.configuration.commit();
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

	private enum AuthorizationType {CAPTCHA_PASS, USER}

	public static class AuthorizationDialog extends DialogFragment {
		private static final String EXTRA_CHAN_NAME = "chanName";
		private static final String EXTRA_AUTHORIZATION_TYPE = "authorizationType";
		private static final String EXTRA_AUTHORIZATION_DATA = "authorizationData";

		public AuthorizationDialog() {}

		public AuthorizationDialog(String chanName,
				AuthorizationType authorizationType, List<String> authorizationData) {
			Bundle args = new Bundle();
			args.putString(EXTRA_CHAN_NAME, chanName);
			args.putString(EXTRA_AUTHORIZATION_TYPE, authorizationType.name());
			args.putStringArrayList(EXTRA_AUTHORIZATION_DATA, authorizationData != null
					? new ArrayList<>(authorizationData) : null);
			setArguments(args);
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

			CheckAuthorizationViewModel viewModel = new ViewModelProvider(this).get(CheckAuthorizationViewModel.class);
			if (!viewModel.hasTaskOrValue()) {
				Bundle args = requireArguments();
				Chan chan = Chan.get(args.getString(EXTRA_CHAN_NAME));
				CheckAuthorizationTask task = new CheckAuthorizationTask(viewModel, chan,
						AuthorizationType.valueOf(args.getString(EXTRA_AUTHORIZATION_TYPE)),
						args.getStringArrayList(EXTRA_AUTHORIZATION_DATA));
				task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
				viewModel.attach(task);
			}
			viewModel.observe(this, result -> {
				dismiss();
				if (result == CheckAuthorizationTask.SUCCESS) {
					ClickableToast.show(R.string.validation_completed);
				} else {
					ClickableToast.show(result);
					ChanFragment chanFragment = (ChanFragment) getParentFragment();
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
			});
		}
	}

	public static class CheckAuthorizationViewModel extends TaskViewModel<CheckAuthorizationTask, ErrorItem> {}

	private static class CheckAuthorizationTask extends HttpHolderTask<Void, ErrorItem> {
		public static final ErrorItem SUCCESS = new ErrorItem("");

		private final CheckAuthorizationViewModel viewModel;
		private final Chan chan;
		private final AuthorizationType authorizationType;
		private final List<String> authorizationData;

		public CheckAuthorizationTask(CheckAuthorizationViewModel viewModel,
				Chan chan, AuthorizationType authorizationType, List<String> authorizationData) {
			super(chan);
			this.viewModel = viewModel;
			this.chan = chan;
			this.authorizationType = authorizationType;
			this.authorizationData = authorizationData;
		}

		@Override
		protected ErrorItem run(HttpHolder holder) {
			try {
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
				ChanPerformer.CheckAuthorizationResult result = chan.performer.safe()
						.onCheckAuthorization(new ChanPerformer.CheckAuthorizationData(type,
								CommonUtils.toArray(authorizationData, String.class), holder));
				return result != null && result.success ? SUCCESS
						: new ErrorItem(ErrorItem.Type.INVALID_AUTHORIZATION_DATA);
			} catch (ExtensionException | HttpException | InvalidResponseException e) {
				return e.getErrorItemAndHandle();
			} finally {
				chan.configuration.commit();
			}
		}

		@Override
		protected void onComplete(ErrorItem result) {
			viewModel.handleResult(result);
		}
	}
}
