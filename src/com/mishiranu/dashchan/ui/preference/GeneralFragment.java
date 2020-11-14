package com.mishiranu.dashchan.ui.preference;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Pair;
import android.view.View;
import androidx.annotation.NonNull;
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
import com.mishiranu.dashchan.text.style.MonospaceSpan;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.MultipleEditPreference;
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.ProgressDialog;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public class GeneralFragment extends PreferenceFragment implements FragmentHandler.Callback,
		ChanMultiChoiceDialog.Callback {
	private MultipleEditPreference<?> captchaSolvingPreference;
	private ProgressDialog captchaSolvingCheckDialog;

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
		captchaSolvingPreference = addMultipleEdit(Preferences.KEY_CAPTCHA_SOLVING, R.string.captcha_solving,
				p -> configureCaptchaSolvingSummary(false), Arrays.asList("Endpoint", "Token"),
				Arrays.asList(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
						InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD),
				new MultipleEditPreference.MapValueCodec(Preferences.KEYS_CAPTCHA_SOLVING));
		captchaSolvingPreference.setOnAfterChangeListener(p -> configureCaptchaSolvingSummary(true));
		captchaSolvingPreference.setDescription(getString(R.string.captcha_solving_info__sentence));
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
		if (captchaSolvingCheckDialog != null) {
			captchaSolvingCheckDialog.dismiss();
			captchaSolvingCheckDialog = null;
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.general), null);
		CheckViewModel viewModel = new ViewModelProvider(this).get(CheckViewModel.class);
		if (viewModel.showDialog) {
			displayCaptchaSolvingCheckDialog();
		}
		viewModel.observe(getViewLifecycleOwner(), result -> {
			viewModel.showDialog = false;
			viewModel.errorItem = result.first;
			viewModel.extraMap = result.second;
			captchaSolvingPreference.invalidate();
			if (captchaSolvingCheckDialog != null) {
				captchaSolvingCheckDialog.dismiss();
				captchaSolvingCheckDialog = null;
				if (result.second != null) {
					ClickableToast.show(R.string.validation_completed);
				} else {
					ClickableToast.show(result.first);
					captchaSolvingPreference.performClick();
				}
			}
		});
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

	private CharSequence configureCaptchaSolvingSummary(boolean resetAndShowDialog) {
		CheckViewModel viewModel = new ViewModelProvider(this).get(CheckViewModel.class);
		if (resetAndShowDialog) {
			viewModel.showDialog = false;
			viewModel.extraMap = null;
			viewModel.errorItem = null;
			viewModel.attach(null);
			viewModel.handleResult(null);
		}
		if (CaptchaSolving.getInstance().hasConfiguration()) {
			if (resetAndShowDialog) {
				viewModel.showDialog = true;
				displayCaptchaSolvingCheckDialog();
			}
			if (viewModel.extraMap == null && viewModel.errorItem == null && !viewModel.hasTaskOrValue()) {
				CheckCaptchaSolvingTask task = new CheckCaptchaSolvingTask(viewModel);
				task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
				viewModel.attach(task);
			}
			if (viewModel.extraMap != null) {
				SpannableStringBuilder builder = new SpannableStringBuilder();
				builder.append(getString(R.string.validation_completed));
				if (!viewModel.extraMap.isEmpty()) {
					for (Map.Entry<String, String> entry : viewModel.extraMap.entrySet()) {
						builder.append('\n');
						int start = builder.length();
						builder.append(entry.getKey()).append(": ").append(entry.getValue());
						int end = builder.length();
						builder.setSpan(new MonospaceSpan(false), start, end,
								SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
					}
				}
				return builder;
			} else if (viewModel.errorItem != null) {
				SpannableStringBuilder builder = new SpannableStringBuilder(viewModel.errorItem.toString());
				builder.setSpan(new ForegroundColorSpan(ResourceUtils.getColor(requireContext(),
						R.attr.colorTextError)), 0, builder.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
				return builder;
			} else {
				return getString(R.string.loading__ellipsis);
			}
		} else {
			return getString(R.string.captcha_solving__summary);
		}
	}

	private void displayCaptchaSolvingCheckDialog() {
		if (captchaSolvingCheckDialog == null) {
			captchaSolvingCheckDialog = new ProgressDialog(requireContext(), null);
			captchaSolvingCheckDialog.setMessage(getString(R.string.loading__ellipsis));
			captchaSolvingCheckDialog.setOnCancelListener(d -> {
				captchaSolvingCheckDialog = null;
				CheckViewModel viewModel = new ViewModelProvider(this).get(CheckViewModel.class);
				viewModel.showDialog = false;
				viewModel.attach(null);
				viewModel.handleResult(new Pair<>(new ErrorItem(ErrorItem.Type.UNKNOWN), null));
			});
			captchaSolvingCheckDialog.show();
		}
	}

	public static class CheckViewModel extends TaskViewModel<CheckCaptchaSolvingTask,
			Pair<ErrorItem, Map<String, String>>> {
		private boolean showDialog;
		private Map<String, String> extraMap;
		private ErrorItem errorItem;
	}

	private static class CheckCaptchaSolvingTask extends HttpHolderTask<Void, Pair<ErrorItem, Map<String, String>>> {
		private final CheckViewModel viewModel;

		public CheckCaptchaSolvingTask(CheckViewModel viewModel) {
			super(Chan.getFallback());
			this.viewModel = viewModel;
		}

		@Override
		protected Pair<ErrorItem, Map<String, String>> run(HttpHolder holder) {
			try {
				Map<String, String> extra = CaptchaSolving.getInstance().checkService(holder);
				return new Pair<>(null, extra);
			} catch (HttpException e) {
				return new Pair<>(e.getErrorItemAndHandle(), null);
			} catch (CaptchaSolving.UnsupportedServiceException e) {
				return new Pair<>(new ErrorItem(ErrorItem.Type.UNSUPPORTED_SERVICE), null);
			} catch (CaptchaSolving.InvalidTokenException e) {
				return new Pair<>(new ErrorItem(ErrorItem.Type.INVALID_AUTHORIZATION_DATA), null);
			}
		}

		@Override
		protected void onComplete(Pair<ErrorItem, Map<String, String>> result) {
			viewModel.handleResult(result);
		}
	}
}
