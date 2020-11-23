package com.mishiranu.dashchan.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.http.HttpException;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ReadCaptchaTask;
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.net.RecaptchaReader;
import com.mishiranu.dashchan.graphics.SelectorBorderDrawable;
import com.mishiranu.dashchan.graphics.SelectorCheckDrawable;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ForegroundManager implements Handler.Callback {
	private static final ForegroundManager INSTANCE = new ForegroundManager();

	private ForegroundManager() {}

	public static ForegroundManager getInstance() {
		return INSTANCE;
	}

	private static final int MESSAGE_INTERRUPT = 1;
	private static final int MESSAGE_REQUIRE_USER_CAPTCHA = 2;
	private static final int MESSAGE_REQUIRE_USER_CHOICE = 3;
	private static final int MESSAGE_REQUIRE_USER_RECAPTCHA_V2 = 4;
	private static final int MESSAGE_SHOW_CAPTCHA_INVALID = 5;

	private static class DelayedMessage {
		public final int what;
		public final HandlerData handlerData;

		public DelayedMessage(int what, HandlerData handlerData) {
			this.what = what;
			this.handlerData = handlerData;
		}
	}

	private final Handler handler = new Handler(Looper.getMainLooper(), this);
	private final HashMap<String, PendingData> pendingDataMap = new HashMap<>();
	private final ArrayList<DelayedMessage> delayedMessages = new ArrayList<>();

	private WeakReference<FragmentActivity> activity;
	private WeakReference<InstanceViewModel> viewModel;

	private FragmentActivity getActivity() {
		FragmentActivity activity = this.activity != null ? this.activity.get() : null;
		return activity == null || activity.getLifecycle().getCurrentState()
				== Lifecycle.State.DESTROYED ? null : activity;
	}

	private PendingData getPendingData(String pendingDataId) {
		synchronized (pendingDataMap) {
			return pendingDataMap.get(pendingDataId);
		}
	}

	private final LifecycleObserver lifecycleObserver = new LifecycleObserver() {
		@SuppressWarnings("unused")
		@OnLifecycleEvent(Lifecycle.Event.ON_START)
		public void onStart(LifecycleOwner owner) {
			handleStartResume(owner);
		}

		@SuppressWarnings("unused")
		@OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
		public void onResume(LifecycleOwner owner) {
			handleStartResume(owner);
		}

		private void handleStartResume(LifecycleOwner owner) {
			FragmentActivity activity = getActivity();
			if (activity != null && activity == owner) {
				handleActivityResumeChecked(activity);
			}
		}
	};

	public static class InstanceViewModel extends ViewModel {
		@Override
		protected void onCleared() {
			INSTANCE.handleCleared(this);
		}
	}

	private void handleActivityResumeChecked(FragmentActivity activity) {
		FragmentManager fragmentManager = activity.getSupportFragmentManager();
		if (!fragmentManager.isStateSaved()) {
			for (Fragment fragment : fragmentManager.getFragments()) {
				if (fragment instanceof PendingDataDialog) {
					PendingDataDialog<?> dialog = (PendingDataDialog<?>) fragment;
					dialog.getPendingDataOrDismiss();
				}
			}
			if (!delayedMessages.isEmpty()) {
				ArrayList<DelayedMessage> delayedMessages = new ArrayList<>(this.delayedMessages);
				this.delayedMessages.clear();
				for (DelayedMessage delayedMessage : delayedMessages) {
					handleMessage(handler.obtainMessage(delayedMessage.what, delayedMessage.handlerData));
				}
			}
		}
	}

	private void handleCleared(InstanceViewModel viewModel) {
		Objects.requireNonNull(viewModel);
		if (this.viewModel != null && this.viewModel.get() == viewModel) {
			this.viewModel = null;
			if (!delayedMessages.isEmpty()) {
				ArrayList<DelayedMessage> delayedMessages = new ArrayList<>(this.delayedMessages);
				this.delayedMessages.clear();
				for (DelayedMessage delayedMessage : delayedMessages) {
					PendingData pendingData = getPendingData(delayedMessage.handlerData.pendingDataId);
					if (pendingData != null) {
						synchronized (pendingData) {
							pendingData.ready = true;
							pendingData.notifyAll();
						}
					}
				}
			}
		}
	}

	private interface PendingDataDialog<T extends PendingData> extends LifecycleObserver {
		interface StoreResultCallback<T> {
			void onStoreResult(T pendingData);
		}

		String EXTRA_PENDING_DATA_ID = "pendingDataId";

		void show(@NonNull FragmentManager manager, String tag);
		@NonNull Bundle requireArguments();
		@NonNull FragmentManager requireFragmentManager();
		void dismiss();

		default void fillArguments(Bundle args, String pendingDataId) {
			args.putString(EXTRA_PENDING_DATA_ID, pendingDataId);
		}

		default String getPendingDataId() {
			return requireArguments().getString(EXTRA_PENDING_DATA_ID);
		}

		default T getPendingData() {
			@SuppressWarnings("unchecked")
			T result = (T) getInstance().getPendingData(getPendingDataId());
			return result;
		}

		default T getPendingDataOrDismiss() {
			T pendingData = getPendingData();
			if (pendingData == null && !requireFragmentManager().isStateSaved()) {
				dismiss();
			}
			return pendingData;
		}

		default void show(FragmentActivity activity) {
			show(activity.getSupportFragmentManager(), getPendingDataId());
		}

		default void notifyResult(StoreResultCallback<T> callback) {
			T pendingData = getPendingData();
			if (pendingData != null) {
				synchronized (pendingData) {
					if (callback != null) {
						callback.onStoreResult(pendingData);
					}
					pendingData.ready = true;
					pendingData.notifyAll();
				}
			}
		}
	}

	public static class CaptchaDialog extends DialogFragment implements PendingDataDialog<CaptchaPendingData>,
			CaptchaForm.Callback, ReadCaptchaTask.Callback {
		private static final String EXTRA_CHAN_NAME = "chanName";
		private static final String EXTRA_CAPTCHA_TYPE = "captchaType";
		private static final String EXTRA_REQUIREMENT = "requirement";
		private static final String EXTRA_BOARD_NAME = "boardName";
		private static final String EXTRA_THREAD_NUMBER = "threadNumber";
		private static final String EXTRA_DESCRIPTION = "description";

		private static final String EXTRA_CAPTCHA_STATE = "captchaState";
		private static final String EXTRA_LOADED_INPUT = "loadedInput";
		private static final String EXTRA_IMAGE = "image";
		private static final String EXTRA_LARGE = "large";
		private static final String EXTRA_BLACK_AND_WHITE = "blackAndWhite";

		private ReadCaptchaTask.CaptchaState captchaState;
		private ChanConfiguration.Captcha.Input loadedInput;
		private Bitmap image;
		private boolean large;
		private boolean blackAndWhite;

		private CaptchaForm captchaForm;

		private Button positiveButton;

		public CaptchaDialog() {}

		public CaptchaDialog(String pendingDataId, String chanName, String captchaType, String requirement,
				String boardName, String threadNumber, String description) {
			Bundle args = new Bundle();
			fillArguments(args, pendingDataId);
			args.putString(EXTRA_CHAN_NAME, chanName);
			args.putString(EXTRA_CAPTCHA_TYPE, captchaType);
			args.putString(EXTRA_REQUIREMENT, requirement);
			args.putString(EXTRA_BOARD_NAME, boardName);
			args.putString(EXTRA_THREAD_NUMBER, threadNumber);
			args.putString(EXTRA_DESCRIPTION, description);
			setArguments(args);
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);

			CaptchaPendingData pendingData = getPendingDataOrDismiss();
			if (pendingData == null) {
				return;
			}
			boolean needLoad = true;
			if (pendingData.captchaData != null && savedInstanceState != null) {
				String captchaStateString = savedInstanceState.getString(EXTRA_CAPTCHA_STATE);
				ReadCaptchaTask.CaptchaState captchaState = captchaStateString != null
						? ReadCaptchaTask.CaptchaState.valueOf(captchaStateString) : null;
				Bitmap image = savedInstanceState.getParcelable(EXTRA_IMAGE);
				if (captchaState != null) {
					String loadedInputString = savedInstanceState.getString(EXTRA_LOADED_INPUT);
					ChanConfiguration.Captcha.Input loadedInput = loadedInputString != null
							? ChanConfiguration.Captcha.Input.valueOf(loadedInputString) : null;
					showCaptcha(captchaState, pendingData.loadedCaptchaType, loadedInput, image,
							savedInstanceState.getBoolean(EXTRA_LARGE),
							savedInstanceState.getBoolean(EXTRA_BLACK_AND_WHITE));
					needLoad = false;
				}
			}
			if (needLoad) {
				reloadCaptcha(pendingData, false, true, false);
			}

			CaptchaViewModel viewModel = new ViewModelProvider(this).get(CaptchaViewModel.class);
			viewModel.observe(this, this);
		}

		@Override
		public void onSaveInstanceState(@NonNull Bundle outState) {
			super.onSaveInstanceState(outState);
			outState.putString(EXTRA_CAPTCHA_STATE, captchaState != null ? captchaState.name() : null);
			outState.putString(EXTRA_LOADED_INPUT, loadedInput != null ? loadedInput.name() : null);
			outState.putParcelable(EXTRA_IMAGE, image);
			outState.putBoolean(EXTRA_LARGE, large);
			outState.putBoolean(EXTRA_BLACK_AND_WHITE, blackAndWhite);
		}

		private void reloadCaptcha(CaptchaPendingData pendingData,
				boolean forceCaptcha, boolean mayShowLoadButton, boolean restart) {
			pendingData.captchaData = null;
			pendingData.loadedCaptchaType = null;
			boolean allowSolveAutomatically = !forceCaptcha ||
					captchaState != ReadCaptchaTask.CaptchaState.MAY_LOAD_SOLVING;
			captchaState = null;
			image = null;
			large = false;
			blackAndWhite = false;
			updatePositiveButtonState();
			captchaForm.showLoading();
			CaptchaViewModel viewModel = new ViewModelProvider(this).get(CaptchaViewModel.class);
			if (restart || !viewModel.hasTaskOrValue()) {
				Bundle args = requireArguments();
				Chan chan = Chan.get(args.getString(EXTRA_CHAN_NAME));
				List<String> captchaPass = forceCaptcha || chan.name == null ? null : Preferences.getCaptchaPass(chan);
				ReadCaptchaTask task = new ReadCaptchaTask(viewModel.callback, pendingData.captchaReader,
						args.getString(EXTRA_CAPTCHA_TYPE), args.getString(EXTRA_REQUIREMENT), captchaPass,
						mayShowLoadButton, allowSolveAutomatically, chan,
						args.getString(EXTRA_BOARD_NAME), args.getString(EXTRA_THREAD_NUMBER));
				task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
				viewModel.attach(task);
			}
		}

		public static class CaptchaViewModel extends TaskViewModel.Proxy<ReadCaptchaTask, ReadCaptchaTask.Callback> {}

		public void onReadCaptchaSuccess(ReadCaptchaTask.Result result) {
			CaptchaPendingData pendingData = getPendingDataOrDismiss();
			if (pendingData == null) {
				return;
			}
			pendingData.captchaData = result.captchaData != null ? result.captchaData : new ChanPerformer.CaptchaData();
			pendingData.loadedCaptchaType = result.captchaType;
			showCaptcha(result.captchaState, result.captchaType, result.input,
					result.image, result.large, result.blackAndWhite);
			if (result.captchaState == ReadCaptchaTask.CaptchaState.SKIP) {
				onConfirmCaptcha();
			}
		}

		@Override
		public void onReadCaptchaError(ErrorItem errorItem) {
			if (getPendingDataOrDismiss() != null) {
				ClickableToast.show(errorItem);
				captchaForm.showError();
			}
		}

		private void showCaptcha(ReadCaptchaTask.CaptchaState captchaState, String captchaType,
				ChanConfiguration.Captcha.Input input, Bitmap image, boolean large, boolean blackAndWhite) {
			this.captchaState = captchaState;
			if (captchaType != null && input == null) {
				Chan chan = Chan.get(requireArguments().getString(EXTRA_CHAN_NAME));
				input = chan.configuration.safe().obtainCaptcha(captchaType).input;
			}
			loadedInput = input;
			if (this.image != null && this.image != image) {
				this.image.recycle();
			}
			this.image = image;
			this.large = large;
			this.blackAndWhite = blackAndWhite;
			boolean invertColors = blackAndWhite && !GraphicsUtils.isLight(ResourceUtils
					.getDialogBackground(requireContext()));
			captchaForm.showCaptcha(captchaState, input, image, large, invertColors);
			updatePositiveButtonState();
		}

		private void updatePositiveButtonState() {
			if (positiveButton != null) {
				positiveButton.setEnabled(captchaState != null &&
						captchaState != ReadCaptchaTask.CaptchaState.NEED_LOAD &&
						captchaState != ReadCaptchaTask.CaptchaState.MAY_LOAD &&
						captchaState != ReadCaptchaTask.CaptchaState.MAY_LOAD_SOLVING);
			}
		}

		@NonNull
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			Bundle args = requireArguments();
			View container = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_captcha, null);
			TextView comment = container.findViewById(R.id.comment);
			String description = args.getString(EXTRA_DESCRIPTION);
			if (!StringUtils.isEmpty(description)) {
				comment.setText(description);
			} else {
				comment.setVisibility(View.GONE);
			}
			Chan chan = Chan.get(args.getString(EXTRA_CHAN_NAME));
			ChanConfiguration.Captcha captcha = chan.configuration
					.safe().obtainCaptcha(args.getString(EXTRA_CAPTCHA_TYPE));
			EditText captchaInputView = container.findViewById(R.id.captcha_input);
			captchaForm = new CaptchaForm(this, false, true, container, null, captchaInputView, captcha);
			AlertDialog alertDialog = new AlertDialog.Builder(requireContext())
					.setTitle(R.string.confirmation).setView(container)
					.setPositiveButton(android.R.string.ok, (dialog, which) -> confirmCaptchaInternal())
					.setNegativeButton(android.R.string.cancel, (dialog, which) -> cancelInternal())
					.create();
			alertDialog.setCanceledOnTouchOutside(false);
			alertDialog.setOnShowListener(dialog -> {
				positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
				updatePositiveButtonState();
			});
			return alertDialog;
		}

		@Override
		public void onDestroyView() {
			super.onDestroyView();
			captchaForm = null;
		}

		@Override
		public void onRefreshCaptcha(boolean forceRefresh) {
			CaptchaPendingData pendingData = getPendingDataOrDismiss();
			if (pendingData == null) {
				return;
			}
			reloadCaptcha(pendingData, forceRefresh, false, true);
		}

		@Override
		public void onConfirmCaptcha() {
			dismiss();
			confirmCaptchaInternal();
		}

		@Override
		public void onCancel(@NonNull DialogInterface dialog) {
			super.onCancel(dialog);
			cancelInternal();
		}

		private void confirmCaptchaInternal() {
			notifyResult(pendingData -> {
				if (pendingData.captchaData != null) {
					pendingData.captchaData.put(ChanPerformer.CaptchaData.INPUT, captchaForm.getInput());
				}
			});
		}

		private void cancelInternal() {
			notifyResult(pendingData -> {
				pendingData.captchaData = null;
				pendingData.loadedCaptchaType = null;
			});
		}
	}

	private static ImageView appendDescriptionImageView(ViewGroup viewGroup, Bitmap descriptionImage) {
		ImageView imageView = new ImageView(viewGroup.getContext()) {
			@Override
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
				super.onMeasure(widthMeasureSpec, heightMeasureSpec);
				int width = getMeasuredWidth();
				int height = 0;
				Drawable drawable = getDrawable();
				if (drawable != null) {
					int dw = drawable.getIntrinsicWidth(), dh = drawable.getIntrinsicHeight();
					if (dw > 0 && dh > 0) {
						height = width * dh / dw;
					}
				}
				height = Math.min(height, (int) (120f * ResourceUtils.obtainDensity(this)));
				setMeasuredDimension(width, height);
			}
		};
		imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
		imageView.setImageBitmap(descriptionImage);
		viewGroup.addView(imageView);
		return imageView;
	}

	private static class ItemsAdapter extends ArrayAdapter<CharSequence> {
		private final View header;

		public ItemsAdapter(Context context, int resource, ArrayList<CharSequence> items, View header) {
			super(context, resource, android.R.id.text1, items);
			this.header = header;
		}

		@Override
		public int getItemViewType(int position) {
			if (header != null && position == 0) {
				return ListView.ITEM_VIEW_TYPE_IGNORE;
			}
			return super.getItemViewType(position);
		}

		@NonNull
		@Override
		public View getView(int position, View convertView, @NonNull ViewGroup parent) {
			if (header != null && position == 0) {
				return header;
			}
			return super.getView(position, convertView, parent);
		}

		@Override
		public boolean areAllItemsEnabled() {
			return false;
		}

		@Override
		public boolean isEnabled(int position) {
			return header == null || position != 0;
		}
	}

	public static class ItemChoiceDialog extends DialogFragment implements PendingDataDialog<ChoicePendingData>,
			DialogInterface.OnClickListener, AdapterView.OnItemClickListener {
		private static final String EXTRA_SELECTED = "selected";
		private static final String EXTRA_ITEMS = "items";
		private static final String EXTRA_DESCRIPTION_TEXT = "descriptionText";
		private static final String EXTRA_DESCRIPTION_IMAGE = "descriptionImage";
		private static final String EXTRA_MULTIPLE = "multiple";

		private boolean[] selected;
		private boolean hasImage;

		public ItemChoiceDialog() {}

		public ItemChoiceDialog(String pendingDataId, boolean[] selected, CharSequence[] items,
				String descriptionText, Bitmap descriptionImage, boolean multiple) {
			Bundle args = new Bundle();
			fillArguments(args, pendingDataId);
			args.putBooleanArray(EXTRA_SELECTED, selected);
			args.putCharSequenceArray(EXTRA_ITEMS, items);
			args.putString(EXTRA_DESCRIPTION_TEXT, descriptionText);
			args.putParcelable(EXTRA_DESCRIPTION_IMAGE, descriptionImage);
			args.putBoolean(EXTRA_MULTIPLE, multiple);
			setArguments(args);
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
			getPendingDataOrDismiss();
		}

		@Override
		public void onSaveInstanceState(@NonNull Bundle outState) {
			super.onSaveInstanceState(outState);
			outState.putBooleanArray(EXTRA_SELECTED, selected);
		}

		@NonNull
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			CharSequence[] items = requireArguments().getCharSequenceArray(EXTRA_ITEMS);
			if (items == null) {
				items = new CharSequence[0];
			}
			String descriptionText = requireArguments().getString(EXTRA_DESCRIPTION_TEXT);
			Bitmap descriptionImage = requireArguments().getParcelable(EXTRA_DESCRIPTION_IMAGE);
			boolean multiple = requireArguments().getBoolean(EXTRA_MULTIPLE);
			selected = new boolean[items.length];
			boolean[] selected = savedInstanceState != null ? savedInstanceState.getBooleanArray(EXTRA_SELECTED) : null;
			if (selected == null) {
				selected = requireArguments().getBooleanArray(EXTRA_SELECTED);
			}
			if (selected != null && selected.length == this.selected.length) {
				System.arraycopy(selected, 0, this.selected, 0, selected.length);
			}

			FrameLayout imageLayout = null;
			if (descriptionImage != null) {
				imageLayout = new FrameLayout(requireContext());
				ImageView imageView = appendDescriptionImageView(imageLayout, descriptionImage);
				int outerPadding = imageLayout.getResources().getDimensionPixelOffset(R.dimen.dialog_padding_text);
				((FrameLayout.LayoutParams) imageView.getLayoutParams()).setMargins(0, outerPadding, outerPadding,
						outerPadding / 2);
			}
			ArrayList<CharSequence> itemsList = new ArrayList<>();
			if (imageLayout != null) {
				itemsList.add(null);
			}
			Collections.addAll(itemsList, items);
			int resId = ResourceUtils.obtainAlertDialogLayoutResId(requireContext(), multiple
					? ResourceUtils.DialogLayout.MULTI_CHOICE : ResourceUtils.DialogLayout.SINGLE_CHOICE);
			ItemsAdapter adapter = new ItemsAdapter(requireContext(), resId, itemsList, imageLayout);
			AlertDialog alertDialog = new AlertDialog.Builder(requireContext())
					.setTitle(descriptionText)
					.setAdapter(adapter, null)
					.setPositiveButton(android.R.string.ok, this)
					.setNegativeButton(android.R.string.cancel, this)
					.create();
			ListView listView = alertDialog.getListView();
			listView.setOnItemClickListener(this);
			listView.setChoiceMode(multiple ? ListView.CHOICE_MODE_MULTIPLE : ListView.CHOICE_MODE_SINGLE);
			for (int i = 0, j = imageLayout == null ? 0 : 1; i < this.selected.length; i++, j++) {
				listView.setItemChecked(j, this.selected[i]);
			}
			hasImage = imageLayout != null;
			return alertDialog;
		}

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			if (hasImage && position == 0) {
				return;
			}
			int arrayPosition = hasImage ? position - 1 : position;
			if (requireArguments().getBoolean(EXTRA_MULTIPLE)) {
				selected[arrayPosition] = !selected[arrayPosition];
				((ListView) parent).setItemChecked(position, selected[arrayPosition]);
			} else {
				for (int i = 0; i < selected.length; i++) {
					selected[i] = i == arrayPosition;
				}
				dismiss();
				publishResult(true);
			}
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			publishResult(which == AlertDialog.BUTTON_POSITIVE);
		}

		@Override
		public void onCancel(@NonNull DialogInterface dialog) {
			super.onCancel(dialog);
			publishResult(false);
		}

		private void publishResult(boolean success) {
			notifyResult(pendingData -> pendingData.result = success ? selected : null);
		}
	}

	public static class ImageChoiceDialog extends DialogFragment implements PendingDataDialog<ChoicePendingData>,
			View.OnClickListener, DialogInterface.OnClickListener {
		private static final String EXTRA_COLUMNS = "columns";
		private static final String EXTRA_SELECTED = "selected";
		private static final String EXTRA_IMAGES = "images";
		private static final String EXTRA_DESCRIPTION_TEXT = "descriptionText";
		private static final String EXTRA_DESCRIPTION_IMAGE = "descriptionImage";
		private static final String EXTRA_MULTIPLE = "multiple";

		private FrameLayout[] selectionViews;
		private boolean[] selected;

		public ImageChoiceDialog() {}

		public ImageChoiceDialog(String pendingDataId, int columns, boolean[] selected, Bitmap[] images,
				String descriptionText, Bitmap descriptionImage, boolean multiple) {
			Bundle args = new Bundle();
			fillArguments(args, pendingDataId);
			args.putInt(EXTRA_COLUMNS, columns);
			args.putBooleanArray(EXTRA_SELECTED, selected);
			args.putParcelableArray(EXTRA_IMAGES, images);
			args.putString(EXTRA_DESCRIPTION_TEXT, descriptionText);
			args.putParcelable(EXTRA_DESCRIPTION_IMAGE, descriptionImage);
			args.putBoolean(EXTRA_MULTIPLE, multiple);
			setArguments(args);
		}

		private void ensureArrays() {
			if (selectionViews == null) {
				int columns = requireArguments().getInt(EXTRA_COLUMNS);
				Parcelable[] parcelables = requireArguments().getParcelableArray(EXTRA_IMAGES);
				int count = parcelables != null ? parcelables.length : 0;
				selectionViews = new FrameLayout[(count + columns - 1) / columns * columns];
				selected = new boolean[count];
			}
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
			PendingData pendingData = getPendingDataOrDismiss();
			if (pendingData == null) {
				return;
			}
			ensureArrays();
			boolean[] selected = savedInstanceState != null ? savedInstanceState.getBooleanArray(EXTRA_SELECTED) : null;
			if (selected == null) {
				selected = requireArguments().getBooleanArray(EXTRA_SELECTED);
			}
			if (selected != null && selected.length == this.selected.length) {
				System.arraycopy(selected, 0, this.selected, 0, selected.length);
			}
			for (int i = 0; i < this.selected.length; i++) {
				updateSelection(i);
			}
		}

		@Override
		public void onSaveInstanceState(@NonNull Bundle outState) {
			super.onSaveInstanceState(outState);
			outState.putBooleanArray(EXTRA_SELECTED, selected);
		}

		@NonNull
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			final float density = ResourceUtils.obtainDensity(requireContext());
			LinearLayout container = new LinearLayout(requireContext());
			container.setOrientation(LinearLayout.VERTICAL);
			Parcelable[] parcelables = requireArguments().getParcelableArray(EXTRA_IMAGES);
			Bitmap[] images = new Bitmap[parcelables != null ? parcelables.length : 0];
			if (images.length > 0) {
				// noinspection SuspiciousSystemArraycopy
				System.arraycopy(parcelables, 0, images, 0, images.length);
			}
			String descriptionText = requireArguments().getString(EXTRA_DESCRIPTION_TEXT);
			Bitmap descriptionImage = requireArguments().getParcelable(EXTRA_DESCRIPTION_IMAGE);
			int outerPadding = container.getResources().getDimensionPixelOffset(R.dimen.dialog_padding_text);
			container.setPadding(outerPadding, outerPadding, outerPadding, outerPadding);
			int cornersRadius = (int) (2f * density);
			if (descriptionImage != null) {
				ImageView imageView = appendDescriptionImageView(container, descriptionImage);
				((LinearLayout.LayoutParams) imageView.getLayoutParams()).setMargins(0, 0, 0, (int) (20f * density));
			}
			int innerPadding = (int) (8f * density);
			int columns = requireArguments().getInt(EXTRA_COLUMNS);
			int rows = (images.length + columns - 1) / columns;
			ensureArrays();
			for (int i = 0; i < rows; i++) {
				LinearLayout inner = new LinearLayout(container.getContext());
				inner.setOrientation(LinearLayout.HORIZONTAL);
				container.addView(inner, LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT);
				for (int j = 0; j < columns; j++) {
					int index = columns * i + j;
					FrameLayout frameLayout = new FrameLayout(inner.getContext());
					selectionViews[index] = frameLayout;
					LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0,
							LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
					if (j < columns - 1) {
						layoutParams.rightMargin = innerPadding;
					}
					if (i < rows - 1) {
						layoutParams.bottomMargin = innerPadding;
					}
					inner.addView(frameLayout, layoutParams);
					if (index < images.length) {
						ImageView imageView = new ImageView(frameLayout.getContext()) {
							@Override
							protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
								super.onMeasure(widthMeasureSpec, heightMeasureSpec);
								setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
							}
						};
						imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
						imageView.setImageBitmap(images[index]);
						ViewUtils.makeRoundedCorners(imageView, cornersRadius, false);
						frameLayout.addView(imageView, FrameLayout.LayoutParams.MATCH_PARENT,
								FrameLayout.LayoutParams.WRAP_CONTENT);
						View view = new View(frameLayout.getContext());
						ViewUtils.setSelectableItemBackground(view);
						view.setTag(index);
						view.setOnClickListener(this);
						frameLayout.addView(view, FrameLayout.LayoutParams.MATCH_PARENT,
								FrameLayout.LayoutParams.MATCH_PARENT);
						if (C.API_LOLLIPOP) {
							frameLayout.setForeground(new SelectorCheckDrawable());
						} else {
							frameLayout.setForeground(new SelectorBorderDrawable(frameLayout.getContext()));
						}
					}
				}
			}

			AlertDialog[] futureAlertDialog = {null};
			ScrollView scrollView = new ScrollView(container.getContext()) {
				@Override
				protected void onLayout(boolean changed, int l, int t, int r, int b) {
					super.onLayout(changed, l, t, r, b);

					// Ensure at least count=columns rows can fit
					int cellSize = (b - t - (columns - 1) * innerPadding - 2 * outerPadding) / columns;
					int width = Math.min((int) (480 * density),
							columns * cellSize + (columns - 1) * innerPadding + 2 * outerPadding);
					if (r - l > width) {
						int totalPadding = 0;
						View root = this;
						while (true) {
							totalPadding += root.getPaddingLeft() + root.getPaddingRight();
							ViewGroup.LayoutParams layoutParams = root.getLayoutParams();
							if (layoutParams instanceof MarginLayoutParams) {
								MarginLayoutParams marginLayoutParams = (MarginLayoutParams) layoutParams;
								totalPadding += marginLayoutParams.leftMargin + marginLayoutParams.rightMargin;
							}
							ViewParent parent = root.getParent();
							if (parent instanceof View) {
								root = (View) parent;
							} else {
								break;
							}
						}
						width += totalPadding;
						futureAlertDialog[0].getWindow().setLayout(width, LayoutParams.WRAP_CONTENT);
					}
				}
			};
			scrollView.addView(container, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			final AlertDialog alertDialog = new AlertDialog.Builder(requireContext())
					.setTitle(descriptionText).setView(scrollView)
					.setPositiveButton(android.R.string.ok, this)
					.setNegativeButton(android.R.string.cancel, this)
					.create();
			futureAlertDialog[0] = alertDialog;
			return alertDialog;
		}

		@Override
		public void onClick(View v) {
			int index = (int) v.getTag();
			if (requireArguments().getBoolean(EXTRA_MULTIPLE)) {
				selected[index] = !selected[index];
				updateSelection(index);
			} else {
				for (int i = 0; i < selected.length; i++) {
					selected[i] = i == index;
				}
				dismiss();
				publishResult(true);
			}
		}

		private void updateSelection(int index) {
			Drawable drawable = selectionViews[index].getForeground();
			if (C.API_LOLLIPOP) {
				((SelectorCheckDrawable) drawable).setSelected(selected[index], true);
			} else {
				((SelectorBorderDrawable) drawable).setSelected(selected[index]);
			}
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			publishResult(which == AlertDialog.BUTTON_POSITIVE);
		}

		@Override
		public void onCancel(@NonNull DialogInterface dialog) {
			super.onCancel(dialog);
			publishResult(false);
		}

		private void publishResult(boolean success) {
			notifyResult(pendingData -> pendingData.result = success ? selected : null);
		}
	}

	public static class RecaptchaV2Dialog extends RecaptchaReader.V2Dialog
			implements PendingDataDialog<RecaptchaV2PendingData> {
		public RecaptchaV2Dialog() {}

		public RecaptchaV2Dialog(String pendingDataId, String referer, String apiKey,
				boolean invisible, boolean hcaptcha, RecaptchaReader.ChallengeExtra challengeExtra) {
			super(referer, apiKey, invisible, hcaptcha, challengeExtra);
			fillArguments(getArguments(), pendingDataId);
		}

		@Override
		public void publishResult(String response, HttpException exception) {
			notifyResult(pendingData -> {
				pendingData.response = response;
				pendingData.exception = exception;
			});
		}
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
			case MESSAGE_INTERRUPT: {
				HandlerData handlerData = (HandlerData) msg.obj;
				FragmentActivity activity = getActivity();
				if (activity != null) {
					FragmentManager fragmentManager = activity.getSupportFragmentManager();
					if (!fragmentManager.isStateSaved()) {
						DialogFragment fragment = (DialogFragment) fragmentManager
								.findFragmentByTag(handlerData.pendingDataId);
						if (fragment != null) {
							fragment.dismiss();
						}
					}
				}
				return true;
			}
			case MESSAGE_REQUIRE_USER_CAPTCHA:
			case MESSAGE_REQUIRE_USER_CHOICE:
			case MESSAGE_REQUIRE_USER_RECAPTCHA_V2: {
				HandlerData handlerData = (HandlerData) msg.obj;
				FragmentActivity activity = getActivity();
				PendingData pendingData = getPendingData(handlerData.pendingDataId);
				if (pendingData == null) {
					return true;
				}
				if (activity == null) {
					synchronized (pendingData) {
						pendingData.ready = true;
						pendingData.notifyAll();
					}
				} else if (activity.getSupportFragmentManager().isStateSaved()) {
					delayedMessages.add(new DelayedMessage(msg.what, handlerData));
				} else {
					switch (msg.what) {
						case MESSAGE_REQUIRE_USER_CAPTCHA: {
							CaptchaHandlerData captchaHandlerData = (CaptchaHandlerData) handlerData;
							new CaptchaDialog(handlerData.pendingDataId, captchaHandlerData.chanName,
									captchaHandlerData.captchaType, captchaHandlerData.requirement,
									captchaHandlerData.boardName, captchaHandlerData.threadNumber,
									captchaHandlerData.description).show(activity);
							break;
						}
						case MESSAGE_REQUIRE_USER_CHOICE: {
							ChoiceHandlerData choiceHandlerData = (ChoiceHandlerData) handlerData;
							if (choiceHandlerData.images != null) {
								new ImageChoiceDialog(handlerData.pendingDataId, choiceHandlerData.columns,
										choiceHandlerData.selected, choiceHandlerData.images,
										choiceHandlerData.descriptionText, choiceHandlerData.descriptionImage,
										choiceHandlerData.multiple).show(activity);
							} else {
								new ItemChoiceDialog(handlerData.pendingDataId, choiceHandlerData.selected,
										choiceHandlerData.items, choiceHandlerData.descriptionText,
										choiceHandlerData.descriptionImage, choiceHandlerData.multiple).show(activity);
							}
							break;
						}
						case MESSAGE_REQUIRE_USER_RECAPTCHA_V2: {
							RecaptchaV2HandlerData recaptchaV2HandlerData = (RecaptchaV2HandlerData) handlerData;
							new RecaptchaV2Dialog(handlerData.pendingDataId, recaptchaV2HandlerData.referer,
									recaptchaV2HandlerData.apiKey, recaptchaV2HandlerData.invisible,
									recaptchaV2HandlerData.hcaptcha, recaptchaV2HandlerData.challengeExtra)
									.show(activity);
						}
					}
				}
				return true;
			}
			case MESSAGE_SHOW_CAPTCHA_INVALID: {
				ClickableToast.show(R.string.captcha_is_not_valid);
				return true;
			}
		}
		return false;
	}

	private static abstract class HandlerData {
		public final String pendingDataId;

		public HandlerData(String pendingDataId) {
			this.pendingDataId = pendingDataId;
		}
	}

	private static class CaptchaHandlerData extends HandlerData {
		public final String chanName;
		public final String captchaType;
		public final String requirement;
		public final String boardName;
		public final String threadNumber;
		public final String description;

		public CaptchaHandlerData(String pendingDataId, String chanName, String captchaType, String requirement,
				String boardName, String threadNumber, String description) {
			super(pendingDataId);
			this.chanName = chanName;
			this.captchaType = captchaType;
			this.requirement = requirement;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.description = description;
		}
	}

	private static class ChoiceHandlerData extends HandlerData {
		public final int columns;
		public final boolean[] selected;
		public final Bitmap[] images;
		public final CharSequence[] items;
		public final String descriptionText;
		public final Bitmap descriptionImage;
		public final boolean multiple;

		public ChoiceHandlerData(String pendingDataId, int columns, boolean[] selected, Bitmap[] images,
				CharSequence[] items, String descriptionText, Bitmap descriptionImage, boolean multiple) {
			super(pendingDataId);
			this.columns = columns;
			this.selected = selected;
			this.images = images;
			this.items = items;
			this.descriptionText = descriptionText;
			this.descriptionImage = descriptionImage;
			this.multiple = multiple;
		}
	}

	private static class RecaptchaV2HandlerData extends HandlerData {
		public final String referer;
		public final String apiKey;
		public final boolean invisible;
		public final boolean hcaptcha;
		public final RecaptchaReader.ChallengeExtra challengeExtra;

		private RecaptchaV2HandlerData(String pendingDataId, String referer, String apiKey,
				boolean invisible, boolean hcaptcha, RecaptchaReader.ChallengeExtra challengeExtra) {
			super(pendingDataId);
			this.referer = referer;
			this.apiKey = apiKey;
			this.invisible = invisible;
			this.hcaptcha = hcaptcha;
			this.challengeExtra = challengeExtra;
		}
	}

	private static abstract class PendingData {
		public boolean ready = false;

		public boolean await(Handler handler, HandlerData handlerData) throws InterruptedException {
			synchronized (this) {
				while (!ready) {
					try {
						wait();
					} catch (InterruptedException e) {
						handler.obtainMessage(MESSAGE_INTERRUPT, handlerData).sendToTarget();
						throw e;
					}
				}
			}
			return true;
		}
	}

	private static class CaptchaPendingData extends PendingData {
		public final ReadCaptchaTask.CaptchaReader captchaReader;

		public ChanPerformer.CaptchaData captchaData;
		public String loadedCaptchaType;

		public CaptchaPendingData(ReadCaptchaTask.CaptchaReader captchaReader) {
			this.captchaReader = captchaReader;
		}
	}

	private static class ChoicePendingData extends PendingData {
		public boolean[] result;
	}

	private static class RecaptchaV2PendingData extends PendingData {
		public String response;
		public HttpException exception;
	}

	private String putPendingData(PendingData pendingData) {
		String id = UUID.randomUUID().toString();
		synchronized (pendingDataMap) {
			pendingDataMap.put(id, pendingData);
		}
		return id;
	}

	private void removePendingData(String id) {
		synchronized (pendingDataMap) {
			pendingDataMap.remove(id);
		}
	}

	public ChanPerformer.CaptchaData requireUserCaptcha(Chan chan, String requirement,
			String boardName, String threadNumber, boolean retry) throws InterruptedException {
		return requireUserCaptcha(null, chan.configuration.getCaptchaType(), requirement,
				chan.name, boardName, threadNumber, null, retry);
	}

	public ChanPerformer.CaptchaData requireUserCaptcha(ReadCaptchaTask.CaptchaReader captchaReader,
			String captchaType, String requirement, String chanName, String boardName, String threadNumber,
			String description, boolean retry) throws InterruptedException {
		CaptchaPendingData pendingData = new CaptchaPendingData(captchaReader);
		String pendingDataId = putPendingData(pendingData);
		try {
			CaptchaHandlerData handlerData = new CaptchaHandlerData(pendingDataId, chanName, captchaType,
					requirement, boardName, threadNumber, description);
			handler.obtainMessage(MESSAGE_REQUIRE_USER_CAPTCHA, handlerData).sendToTarget();
			if (retry) {
				handler.sendEmptyMessage(MESSAGE_SHOW_CAPTCHA_INVALID);
			}
			if (!pendingData.await(handler, handlerData)) {
				return null;
			}
			ChanPerformer.CaptchaData captchaData = pendingData.captchaData;
			if (captchaData != null) {
				String workCaptchaType = pendingData.loadedCaptchaType != null
						? pendingData.loadedCaptchaType : captchaType;
				String apiKey = captchaData.get(ChanPerformer.CaptchaData.API_KEY);
				if (apiKey != null && (ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2.equals(workCaptchaType) ||
						ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE.equals(workCaptchaType) ||
						ChanConfiguration.CAPTCHA_TYPE_HCAPTCHA.equals(workCaptchaType))) {
					captchaData.put(ChanPerformer.CaptchaData.INPUT,
							captchaData.get(ReadCaptchaTask.RECAPTCHA_SKIP_RESPONSE));
				}
			}
			return captchaData;
		} finally {
			removePendingData(pendingDataId);
		}
	}

	public Integer requireUserItemSingleChoice(int selected, CharSequence[] items, String descriptionText,
			Bitmap descriptionImage) throws InterruptedException {
		return requireUserSingleChoice(0, selected, items, null, descriptionText, descriptionImage, false);
	}

	public boolean[] requireUserItemMultipleChoice(boolean[] selected, CharSequence[] items, String descriptionText,
			Bitmap descriptionImage) throws InterruptedException {
		return requireUserChoice(0, selected, items, null, descriptionText, descriptionImage, true, false);
	}

	public Integer requireUserImageSingleChoice(int columns, int selected, Bitmap[] images,
			String descriptionText, Bitmap descriptionImage) throws InterruptedException {
		return requireUserSingleChoice(columns, selected, null, images, descriptionText, descriptionImage, true);
	}

	public boolean[] requireUserImageMultipleChoice(int columns, boolean[] selected, Bitmap[] images,
			String descriptionText, Bitmap descriptionImage) throws InterruptedException {
		return requireUserChoice(columns, selected, null, images, descriptionText, descriptionImage, true, true);
	}

	private Integer requireUserSingleChoice(int columns, int selected, CharSequence[] items, Bitmap[] images,
			String descriptionText, Bitmap descriptionImage, boolean imageChoice) throws InterruptedException {
		boolean[] selectedArray = null;
		int length = items != null ? items.length : images != null ? images.length : 0;
		if (selected >= 0 && selected < length) {
			selectedArray = new boolean[length];
			selectedArray[selected] = true;
		}
		boolean[] result = requireUserChoice(columns, selectedArray, items, images, descriptionText, descriptionImage,
				false, imageChoice);
		if (result != null) {
			for (int i = 0; i < result.length; i++) {
				if (result[i]) {
					return i;
				}
			}
			return -1;
		}
		return null;
	}

	private boolean[] requireUserChoice(int columns, boolean[] selected, CharSequence[] items, Bitmap[] images,
			String descriptionText, Bitmap descriptionImage, boolean multiple, boolean imageChoice)
			throws InterruptedException {
		if (imageChoice && images == null) {
			throw new NullPointerException("Images array is null");
		}
		if (!imageChoice && items == null) {
			throw new NullPointerException("Items array is null");
		}
		ChoicePendingData pendingData = new ChoicePendingData();
		String pendingDataId = putPendingData(pendingData);
		try {
			ChoiceHandlerData handlerData = new ChoiceHandlerData(pendingDataId, columns, selected, images, items,
					descriptionText, descriptionImage, multiple);
			handler.obtainMessage(MESSAGE_REQUIRE_USER_CHOICE, handlerData).sendToTarget();
			return pendingData.await(handler, handlerData) ? pendingData.result : null;
		} finally {
			removePendingData(pendingDataId);
		}
	}

	public String requireUserRecaptchaV2(String referer, String apiKey, boolean invisible, boolean hcaptcha,
			RecaptchaReader.ChallengeExtra challengeExtra) throws HttpException, InterruptedException {
		RecaptchaV2PendingData pendingData = new RecaptchaV2PendingData();
		String pendingDataId = putPendingData(pendingData);
		try {
			RecaptchaV2HandlerData handlerData = new RecaptchaV2HandlerData(pendingDataId,
					referer, apiKey, invisible, hcaptcha, challengeExtra);
			handler.obtainMessage(MESSAGE_REQUIRE_USER_RECAPTCHA_V2, handlerData).sendToTarget();
			if (!pendingData.await(handler, handlerData)) {
				return null;
			}
			if (pendingData.exception != null) {
				throw pendingData.exception;
			}
			return pendingData.response;
		} finally {
			removePendingData(pendingDataId);
		}
	}

	public void register(@NonNull FragmentActivity activity) {
		Objects.requireNonNull(activity);
		if (this.activity != null) {
			FragmentActivity oldActivity = this.activity.get();
			if (oldActivity == activity) {
				return;
			}
			if (oldActivity != null) {
				oldActivity.getLifecycle().removeObserver(lifecycleObserver);
			}
		}
		this.activity = new WeakReference<>(activity);
		this.viewModel = new WeakReference<>(new ViewModelProvider(activity).get(InstanceViewModel.class));
		activity.getLifecycle().addObserver(lifecycleObserver);
		handleActivityResumeChecked(activity);
	}
}
