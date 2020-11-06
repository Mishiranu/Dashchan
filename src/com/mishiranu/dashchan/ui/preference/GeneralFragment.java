package com.mishiranu.dashchan.ui.preference;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import chan.content.Chan;
import chan.content.ChanManager;
import chan.http.HttpException;
import chan.http.HttpHolder;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.LocaleManager;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.HttpHolderTask;
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.net.CaptchaSolving;
import com.mishiranu.dashchan.ui.ActivityHandler;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.MultipleEditPreference;
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.widget.ProgressDialog;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public class GeneralFragment extends PreferenceFragment implements ActivityHandler, ChanMultiChoiceDialog.Callback {
	private MultipleEditPreference<?> captchaSolvingPreference;

	@Override
	protected SharedPreferences getPreferences() {
		return Preferences.PREFERENCES;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		addList(Preferences.KEY_LOCALE, LocaleManager.VALUES_LOCALE, LocaleManager.DEFAULT_LOCALE,
				R.string.language, LocaleManager.ENTRIES_LOCALE)
				.setOnAfterChangeListener(p -> requireActivity().recreate());

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

		addHeader(R.string.services);
		if (C.API_KITKAT) {
			addCheck(true, Preferences.KEY_RECAPTCHA_JAVASCRIPT, Preferences.DEFAULT_RECAPTCHA_JAVASCRIPT,
					R.string.use_javascript_for_recaptcha, R.string.use_javascript_for_recaptcha__summary);
		}
		captchaSolvingPreference = addMultipleEdit(Preferences.KEY_CAPTCHA_SOLVING,
				R.string.captcha_solving, R.string.captcha_solving__summary, Arrays.asList("Endpoint", "Token"),
				Arrays.asList(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
						InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD),
				new MultipleEditPreference.MapValueCodec(Preferences.KEYS_CAPTCHA_SOLVING));
		captchaSolvingPreference.setOnAfterChangeListener(p -> {
			if (CaptchaSolving.getInstance().shouldValidateConfiguration()) {
				new CheckDataFragment().show(getChildFragmentManager(), CheckDataFragment.class.getName());
			}
		});
		configureCaptchaSolvingNeutralButton();

		addHeader(R.string.connection);
		addButton(0, R.string.specific_to_internal_services__sentence).setSelectable(false);
		addCheck(true, Preferences.KEY_USE_HTTPS_GENERAL, Preferences.DEFAULT_USE_HTTPS,
				R.string.secure_connection, R.string.secure_connection__summary);
		addCheck(true, Preferences.KEY_VERIFY_CERTIFICATE, Preferences.DEFAULT_VERIFY_CERTIFICATE,
				R.string.verify_certificate, R.string.verify_certificate__summary);
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		captchaSolvingPreference = null;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.general), null);
	}

	@Override
	public void onChansChanged(Collection<String> changed, Collection<String> removed) {
		configureCaptchaSolvingNeutralButton();
	}

	@Override
	public void onChansSelected(Collection<String> chanNames) {
		Preferences.setCaptchaSolvingChans(chanNames);
	}

	private void configureCaptchaSolvingNeutralButton() {
		if (ChanManager.getInstance().getAvailableChans().iterator().hasNext()) {
			captchaSolvingPreference.setNeutralButton(getString(R.string.forums),
					() -> new ChanMultiChoiceDialog(Preferences.getCaptchaSolvingChans()).show(this));
		} else {
			captchaSolvingPreference.setNeutralButton(null, null);
		}
	}

	public static class CheckDataFragment extends DialogFragment {
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

			CheckViewModel viewModel = new ViewModelProvider(this).get(CheckViewModel.class);
			if (!viewModel.hasTaskOrValue()) {
				CheckCaptchaSolvingTask task = new CheckCaptchaSolvingTask(viewModel, Preferences.getCaptchaSolving());
				task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
				viewModel.attach(task);
			}
			viewModel.observe(this, result -> {
				dismiss();
				if (result == CheckCaptchaSolvingTask.SUCCESS) {
					ToastUtils.show(requireContext(), R.string.validation_completed);
				} else {
					ToastUtils.show(requireContext(), result);
					((GeneralFragment) getParentFragment()).captchaSolvingPreference.performClick();
				}
			});
		}
	}

	public static class CheckViewModel extends TaskViewModel<CheckCaptchaSolvingTask, ErrorItem> {}

	private static class CheckCaptchaSolvingTask extends HttpHolderTask<Void, ErrorItem> {
		private static final ErrorItem SUCCESS = new ErrorItem("");

		private final CheckViewModel viewModel;
		private final Map<String, String> data;

		public CheckCaptchaSolvingTask(CheckViewModel viewModel, Map<String, String> data) {
			super(Chan.getFallback());
			this.viewModel = viewModel;
			this.data = data;
		}

		@Override
		protected ErrorItem run(HttpHolder holder) {
			try {
				String endpoint = data.get(Preferences.SUB_KEY_CAPTCHA_SOLVING_ENDPOINT);
				String token = data.get(Preferences.SUB_KEY_CAPTCHA_SOLVING_TOKEN);
				CaptchaSolving.getInstance().checkService(holder, endpoint, token);
				return SUCCESS;
			} catch (HttpException e) {
				return e.getErrorItemAndHandle();
			} catch (CaptchaSolving.UnsupportedServiceException e) {
				return new ErrorItem(ErrorItem.Type.UNSUPPORTED_SERVICE);
			} catch (CaptchaSolving.InvalidTokenException e) {
				return new ErrorItem(ErrorItem.Type.INVALID_AUTHORIZATION_DATA);
			}
		}

		@Override
		protected void onComplete(ErrorItem result) {
			viewModel.handleResult(result);
		}
	}
}
