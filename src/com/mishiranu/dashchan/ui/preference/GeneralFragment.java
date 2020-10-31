package com.mishiranu.dashchan.ui.preference;

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import chan.content.Chan;
import chan.content.ChanManager;
import chan.http.HttpException;
import chan.http.HttpHolder;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.LocaleManager;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.AsyncManager;
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
import java.util.HashMap;
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
				new CheckDataFragment().show(this);
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

	public static class CheckDataFragment extends DialogFragment implements AsyncManager.Callback {
		private static final String TASK_CHECK_DATA = "check_data";

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
			AsyncManager.get(this).startTask(TASK_CHECK_DATA, this, null, false);
		}

		@Override
		public void onCancel(@NonNull DialogInterface dialog) {
			super.onCancel(dialog);
			AsyncManager.get(this).cancelTask(TASK_CHECK_DATA, this);
		}

		@Override
		public AsyncManager.Holder onCreateAndExecuteTask(String name, HashMap<String, Object> extra) {
			CheckCaptchaSolvingTask task = new CheckCaptchaSolvingTask(Preferences.getCaptchaSolving());
			task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
			return task.getHolder();
		}

		@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
		@Override
		public void onFinishTaskExecution(String name, AsyncManager.Holder holder) {
			dismiss();
			ErrorItem errorItem = holder.nextArgument();
			if (errorItem == null) {
				ToastUtils.show(requireContext(), R.string.validation_completed);
			} else {
				ToastUtils.show(requireContext(), errorItem);
				Fragment fragment = getParentFragment();
				if (fragment instanceof GeneralFragment) {
					GeneralFragment generalFragment = ((GeneralFragment) fragment);
					generalFragment.captchaSolvingPreference.performClick();
				}
			}
		}

		@Override
		public void onRequestTaskCancel(String name, Object task) {
			((CheckCaptchaSolvingTask) task).cancel();
		}
	}

	private static class CheckCaptchaSolvingTask extends AsyncManager.SimpleTask<Void> {
		private final HttpHolder holder = new HttpHolder(Chan.getFallback());

		private final Map<String, String> data;

		private ErrorItem errorItem;

		public CheckCaptchaSolvingTask(Map<String, String> data) {
			this.data = data;
		}

		@Override
		public Void run() {
			try (HttpHolder.Use ignored = holder.use()) {
				String endpoint = data.get(Preferences.SUB_KEY_CAPTCHA_SOLVING_ENDPOINT);
				String token = data.get(Preferences.SUB_KEY_CAPTCHA_SOLVING_TOKEN);
				CaptchaSolving.getInstance().checkService(holder, endpoint, token);
			} catch (HttpException e) {
				errorItem = e.getErrorItemAndHandle();
			} catch (CaptchaSolving.UnsupportedServiceException e) {
				errorItem = new ErrorItem(ErrorItem.Type.UNSUPPORTED_SERVICE);
			} catch (CaptchaSolving.InvalidTokenException e) {
				errorItem = new ErrorItem(ErrorItem.Type.INVALID_AUTHORIZATION_DATA);
			}
			return null;
		}

		@Override
		protected void onStoreResult(AsyncManager.Holder holder, Void result) {
			holder.storeResult(errorItem);
		}

		@Override
		public void cancel() {
			super.cancel();
			holder.interrupt();
		}
	}
}
