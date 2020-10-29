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
import android.util.SparseArray;
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
import androidx.fragment.app.FragmentActivity;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.http.HttpException;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.AsyncManager;
import com.mishiranu.dashchan.content.async.ReadCaptchaTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.net.RecaptchaReader;
import com.mishiranu.dashchan.graphics.SelectorBorderDrawable;
import com.mishiranu.dashchan.graphics.SelectorCheckDrawable;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class ForegroundManager implements Handler.Callback {
	private static final ForegroundManager INSTANCE = new ForegroundManager();

	private ForegroundManager() {}

	public static ForegroundManager getInstance() {
		return INSTANCE;
	}

	private static final int MESSAGE_REQUIRE_USER_CAPTCHA = 1;
	private static final int MESSAGE_REQUIRE_USER_CHOICE = 2;
	private static final int MESSAGE_REQUIRE_USER_RECAPTCHA_V2 = 3;
	private static final int MESSAGE_SHOW_CAPTCHA_INVALID = 4;

	private final Handler handler = new Handler(Looper.getMainLooper(), this);
	private final SparseArray<PendingData> pendingDataArray = new SparseArray<>();

	private int pendingDataStartIndex = 0;
	private WeakReference<FragmentActivity> activity;

	private FragmentActivity getActivity() {
		return activity != null ? activity.get() : null;
	}

	@SuppressWarnings("unchecked")
	private <T extends PendingData> T getPendingData(int pendingDataIndex) {
		synchronized (pendingDataArray) {
			return (T) pendingDataArray.get(pendingDataIndex);
		}
	}

	private static final String EXTRA_PENDING_DATA_INDEX = "pendingDataIndex";

	private interface PendingDataDialogFragment {
		Bundle requireArguments();
		void dismiss();

		default void fillArguments(Bundle args, int pendingDataIndex) {
			args.putInt(EXTRA_PENDING_DATA_INDEX, pendingDataIndex);
		}

		default <T extends PendingData> T getPendingData(boolean dismissIfNull) {
			T result = getInstance().getPendingData(requireArguments().getInt(EXTRA_PENDING_DATA_INDEX));
			if (dismissIfNull && result == null) {
				dismiss();
			}
			return result;
		}
	}

	public static class CaptchaDialogFragment extends DialogFragment implements PendingDataDialogFragment,
			CaptchaForm.Callback, AsyncManager.Callback, ReadCaptchaTask.Callback {
		private static final String EXTRA_CHAN_NAME = "chanName";
		private static final String EXTRA_CAPTCHA_TYPE = "captchaType";
		private static final String EXTRA_REQUIREMENT = "requirement";
		private static final String EXTRA_BOARD_NAME = "boardName";
		private static final String EXTRA_THREAD_NUMBER = "threadNumber";
		private static final String EXTRA_DESCRIPTION = "description";
		private static final String EXTRA_TASK_NAME = "taskName";

		private static final String EXTRA_CAPTCHA_STATE = "captchaState";
		private static final String EXTRA_LOADED_INPUT = "loadedInput";
		private static final String EXTRA_IMAGE = "image";
		private static final String EXTRA_LARGE = "large";
		private static final String EXTRA_BLACK_AND_WHITE = "blackAndWhite";

		private static final String EXTRA_FORCE_CAPTCHA = "forceCaptcha";
		private static final String EXTRA_MAY_SHOW_LOAD_BUTTON = "mayShowLoadButton";

		private ChanPerformer.CaptchaState captchaState;
		private ChanConfiguration.Captcha.Input loadedInput;
		private Bitmap image;
		private boolean large;
		private boolean blackAndWhite;

		private CaptchaForm captchaForm;

		private Button positiveButton;

		public CaptchaDialogFragment() {}

		public CaptchaDialogFragment(int pendingDataIndex, String chanName, String captchaType, String requirement,
				String boardName, String threadNumber, String description) {
			Bundle args = new Bundle();
			fillArguments(args, pendingDataIndex);
			args.putString(EXTRA_CHAN_NAME, chanName);
			args.putString(EXTRA_CAPTCHA_TYPE, captchaType);
			args.putString(EXTRA_REQUIREMENT, requirement);
			args.putString(EXTRA_BOARD_NAME, boardName);
			args.putString(EXTRA_THREAD_NUMBER, threadNumber);
			args.putString(EXTRA_TASK_NAME, "read_captcha_" + UUID.randomUUID().toString());
			args.putString(EXTRA_DESCRIPTION, description);
			setArguments(args);
		}

		public void show(FragmentActivity activity) {
			show(activity.getSupportFragmentManager(), getClass().getName());
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
			CaptchaPendingData pendingData = getPendingData(true);
			if (pendingData == null) {
				return;
			}
			boolean needLoad = true;
			if (pendingData.captchaData != null && savedInstanceState != null) {
				String captchaStateString = savedInstanceState.getString(EXTRA_CAPTCHA_STATE);
				ChanPerformer.CaptchaState captchaState = captchaStateString != null
						? ChanPerformer.CaptchaState.valueOf(captchaStateString) : null;
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

		private void reloadCaptcha(CaptchaPendingData pendingData, boolean forceCaptcha, boolean mayShowLoadButton,
				boolean restart) {
			pendingData.captchaData = null;
			pendingData.loadedCaptchaType = null;
			captchaState = null;
			image = null;
			large = false;
			blackAndWhite = false;
			updatePositiveButtonState();
			captchaForm.showLoading();
			HashMap<String, Object> extra = new HashMap<>();
			extra.put(EXTRA_FORCE_CAPTCHA, forceCaptcha);
			extra.put(EXTRA_MAY_SHOW_LOAD_BUTTON, mayShowLoadButton);
			AsyncManager.get(this).startTask(requireArguments().getString(EXTRA_TASK_NAME), this, extra, restart);
		}

		private static class ReadCaptchaHolder extends AsyncManager.Holder implements ReadCaptchaTask.Callback {
			@Override
			public void onReadCaptchaSuccess(ChanPerformer.CaptchaState captchaState,
					ChanPerformer.CaptchaData captchaData, String captchaType, ChanConfiguration.Captcha.Input input,
					ChanConfiguration.Captcha.Validity validity, Bitmap image, boolean large, boolean blackAndWhite) {
				storeResult("onReadCaptchaSuccess", captchaState, captchaData, captchaType, input, validity,
						image, large, blackAndWhite);
			}

			@Override
			public void onReadCaptchaError(ErrorItem errorItem) {
				storeResult("onReadCaptchaError", errorItem);
			}
		}

		@Override
		public AsyncManager.Holder onCreateAndExecuteTask(String name, HashMap<String, Object> extra) {
			Bundle args = requireArguments();
			Chan chan = Chan.get(args.getString(EXTRA_CHAN_NAME));
			boolean forceCaptcha = (boolean) extra.get(EXTRA_FORCE_CAPTCHA);
			boolean mayShowLoadButton = (boolean) extra.get(EXTRA_MAY_SHOW_LOAD_BUTTON);
			List<String> captchaPass = forceCaptcha || chan.name == null ? null : Preferences.getCaptchaPass(chan);
			CaptchaPendingData pendingData = getPendingData(false);
			if (pendingData == null) {
				return null;
			}
			ReadCaptchaHolder holder = new ReadCaptchaHolder();
			ReadCaptchaTask task = new ReadCaptchaTask(holder, pendingData.captchaReader,
					args.getString(EXTRA_CAPTCHA_TYPE), args.getString(EXTRA_REQUIREMENT), captchaPass,
					mayShowLoadButton, chan, args.getString(EXTRA_BOARD_NAME), args.getString(EXTRA_THREAD_NUMBER));
			task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
			return holder.attach(task);
		}

		@Override
		public void onFinishTaskExecution(String name, AsyncManager.Holder holder) {
			String methodName = holder.nextArgument();
			if ("onReadCaptchaSuccess".equals(methodName)) {
				ChanPerformer.CaptchaState captchaState = holder.nextArgument();
				ChanPerformer.CaptchaData captchaData = holder.nextArgument();
				String captchaType = holder.nextArgument();
				ChanConfiguration.Captcha.Input input = holder.nextArgument();
				ChanConfiguration.Captcha.Validity validity = holder.nextArgument();
				Bitmap image = holder.nextArgument();
				boolean large = holder.nextArgument();
				boolean blackAndWhite = holder.nextArgument();
				onReadCaptchaSuccess(captchaState, captchaData, captchaType, input, validity,
						image, large, blackAndWhite);
			} else if ("onReadCaptchaError".equals(methodName)) {
				ErrorItem errorItem = holder.nextArgument();
				onReadCaptchaError(errorItem);
			}
		}

		@Override
		public void onRequestTaskCancel(String name, Object task) {
			((ReadCaptchaTask) task).cancel();
		}

		@Override
		public void onReadCaptchaSuccess(ChanPerformer.CaptchaState captchaState, ChanPerformer.CaptchaData captchaData,
				String captchaType, ChanConfiguration.Captcha.Input input, ChanConfiguration.Captcha.Validity validity,
				Bitmap image, boolean large, boolean blackAndWhite) {
			CaptchaPendingData pendingData = getPendingData(true);
			if (pendingData == null) {
				return;
			}
			pendingData.captchaData = captchaData != null ? captchaData : new ChanPerformer.CaptchaData();
			pendingData.loadedCaptchaType = captchaType;
			showCaptcha(captchaState, captchaType, input, image, large, blackAndWhite);
			if (isResumed() && captchaState == ChanPerformer.CaptchaState.SKIP) {
				onConfirmCaptcha();
			}
		}

		@Override
		public void onReadCaptchaError(ErrorItem errorItem) {
			CaptchaPendingData pendingData = getPendingData(true);
			if (pendingData == null) {
				return;
			}
			ToastUtils.show(requireContext(), errorItem);
			captchaForm.showError();
		}

		private void showCaptcha(ChanPerformer.CaptchaState captchaState, String captchaType,
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

		private void finishDialog(CaptchaPendingData pendingData) {
			AsyncManager.get(this).cancelTask(requireArguments().getString(EXTRA_TASK_NAME), this);
			if (pendingData != null) {
				synchronized (pendingData) {
					pendingData.ready = true;
					pendingData.notifyAll();
				}
			}
		}

		private void updatePositiveButtonState() {
			if (positiveButton != null) {
				positiveButton.setEnabled(captchaState != null &&
						captchaState != ChanPerformer.CaptchaState.NEED_LOAD);
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
					.setPositiveButton(android.R.string.ok, (dialog, which) -> onConfirmCaptcha())
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
			CaptchaPendingData pendingData = getPendingData(true);
			if (pendingData == null) {
				return;
			}
			reloadCaptcha(pendingData, forceRefresh, false, true);
		}

		@Override
		public void onConfirmCaptcha() {
			CaptchaPendingData pendingData = getPendingData(true);
			if (pendingData == null) {
				return;
			}
			if (pendingData.captchaData != null) {
				pendingData.captchaData.put(ChanPerformer.CaptchaData.INPUT, captchaForm.getInput());
			}
			finishDialog(pendingData);
			dismiss();
		}

		@Override
		public void onCancel(@NonNull DialogInterface dialog) {
			super.onCancel(dialog);
			cancelInternal();
		}

		private void cancelInternal() {
			CaptchaPendingData pendingData = getPendingData(false);
			if (pendingData != null) {
				pendingData.captchaData = null;
				pendingData.loadedCaptchaType = null;
			}
			finishDialog(pendingData);
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

	public static class ItemChoiceDialogFragment extends DialogFragment implements PendingDataDialogFragment,
			DialogInterface.OnClickListener, AdapterView.OnItemClickListener {
		private static final String EXTRA_SELECTED = "selected";
		private static final String EXTRA_ITEMS = "items";
		private static final String EXTRA_DESCRIPTION_TEXT = "descriptionText";
		private static final String EXTRA_DESCRIPTION_IMAGE = "descriptionImage";
		private static final String EXTRA_MULTIPLE = "multiple";

		private boolean[] selected;
		private boolean hasImage;

		public ItemChoiceDialogFragment() {}

		public ItemChoiceDialogFragment(int pendingDataIndex, boolean[] selected, CharSequence[] items,
				String descriptionText, Bitmap descriptionImage, boolean multiple) {
			Bundle args = new Bundle();
			fillArguments(args, pendingDataIndex);
			args.putBooleanArray(EXTRA_SELECTED, selected);
			args.putCharSequenceArray(EXTRA_ITEMS, items);
			args.putString(EXTRA_DESCRIPTION_TEXT, descriptionText);
			args.putParcelable(EXTRA_DESCRIPTION_IMAGE, descriptionImage);
			args.putBoolean(EXTRA_MULTIPLE, multiple);
			setArguments(args);
		}

		public void show(FragmentActivity activity) {
			show(activity.getSupportFragmentManager(), getClass().getName());
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
			getPendingData(true); // Dismiss if null
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
				storeResult(true);
			}
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			storeResult(which == AlertDialog.BUTTON_POSITIVE);
		}

		@Override
		public void onCancel(@NonNull DialogInterface dialog) {
			super.onCancel(dialog);
			storeResult(false);
		}

		private void storeResult(boolean success) {
			ChoicePendingData pendingData = getPendingData(false);
			if (pendingData != null) {
				synchronized (pendingData) {
					pendingData.result = success ? selected : null;
					pendingData.ready = true;
					pendingData.notifyAll();
				}
			}
		}
	}

	public static class ImageChoiceDialogFragment extends DialogFragment implements PendingDataDialogFragment,
			View.OnClickListener, DialogInterface.OnClickListener {
		private static final String EXTRA_COLUMNS = "columns";
		private static final String EXTRA_SELECTED = "selected";
		private static final String EXTRA_IMAGES = "images";
		private static final String EXTRA_DESCRIPTION_TEXT = "descriptionText";
		private static final String EXTRA_DESCRIPTION_IMAGE = "descriptionImage";
		private static final String EXTRA_MULTIPLE = "multiple";

		private FrameLayout[] selectionViews;
		private boolean[] selected;

		public ImageChoiceDialogFragment() {}

		public ImageChoiceDialogFragment(int pendingDataIndex, int columns, boolean[] selected, Bitmap[] images,
				String descriptionText, Bitmap descriptionImage, boolean multiple) {
			Bundle args = new Bundle();
			fillArguments(args, pendingDataIndex);
			args.putInt(EXTRA_COLUMNS, columns);
			args.putBooleanArray(EXTRA_SELECTED, selected);
			args.putParcelableArray(EXTRA_IMAGES, images);
			args.putString(EXTRA_DESCRIPTION_TEXT, descriptionText);
			args.putParcelable(EXTRA_DESCRIPTION_IMAGE, descriptionImage);
			args.putBoolean(EXTRA_MULTIPLE, multiple);
			setArguments(args);
		}

		public void show(FragmentActivity activity) {
			show(activity.getSupportFragmentManager(), getClass().getName());
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
			PendingData pendingData = getPendingData(true);
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
				storeResult(true);
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
			storeResult(which == AlertDialog.BUTTON_POSITIVE);
		}

		@Override
		public void onCancel(@NonNull DialogInterface dialog) {
			super.onCancel(dialog);
			storeResult(false);
		}

		private void storeResult(boolean success) {
			ChoicePendingData pendingData = getPendingData(false);
			if (pendingData != null) {
				synchronized (pendingData) {
					pendingData.result = success ? selected : null;
					pendingData.ready = true;
					pendingData.notifyAll();
				}
			}
		}
	}

	public static class RecaptchaV2DialogFragment extends RecaptchaReader.V2Dialog
			implements PendingDataDialogFragment {
		public RecaptchaV2DialogFragment() {}

		public RecaptchaV2DialogFragment(int pendingDataIndex, String referer, String apiKey,
				boolean invisible, boolean hcaptcha, RecaptchaReader.ChallengeExtra challengeExtra) {
			super(referer, apiKey, invisible, hcaptcha, challengeExtra);
			fillArguments(getArguments(), pendingDataIndex);
		}

		public void show(FragmentActivity activity) {
			show(activity.getSupportFragmentManager(), getClass().getName());
		}

		@Override
		public void publishResponse(String response, HttpException exception) {
			RecaptchaV2PendingData pendingData = getPendingData(false);
			if (pendingData != null) {
				synchronized (pendingData) {
					pendingData.response = response;
					pendingData.exception = exception;
					pendingData.ready = true;
					pendingData.notifyAll();
				}
			}
		}
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
			case MESSAGE_REQUIRE_USER_CAPTCHA:
			case MESSAGE_REQUIRE_USER_CHOICE:
			case MESSAGE_REQUIRE_USER_RECAPTCHA_V2: {
				HandlerData handlerData = (HandlerData) msg.obj;
				FragmentActivity activity = getActivity();
				if (activity == null) {
					PendingData pendingData;
					synchronized (pendingDataArray) {
						pendingData = pendingDataArray.get(handlerData.pendingDataIndex);
					}
					synchronized (pendingData) {
						pendingData.ready = true;
						pendingData.notifyAll();
					}
				} else {
					switch (msg.what) {
						case MESSAGE_REQUIRE_USER_CAPTCHA: {
							CaptchaHandlerData captchaHandlerData = (CaptchaHandlerData) handlerData;
							new CaptchaDialogFragment(handlerData.pendingDataIndex, captchaHandlerData.chanName,
									captchaHandlerData.captchaType, captchaHandlerData.requirement,
									captchaHandlerData.boardName, captchaHandlerData.threadNumber,
									captchaHandlerData.description).show(activity);
							break;
						}
						case MESSAGE_REQUIRE_USER_CHOICE: {
							ChoiceHandlerData choiceHandlerData = (ChoiceHandlerData) handlerData;
							if (choiceHandlerData.images != null) {
								new ImageChoiceDialogFragment(handlerData.pendingDataIndex, choiceHandlerData.columns,
										choiceHandlerData.selected, choiceHandlerData.images,
										choiceHandlerData.descriptionText, choiceHandlerData.descriptionImage,
										choiceHandlerData.multiple).show(activity);
							} else {
								new ItemChoiceDialogFragment(handlerData.pendingDataIndex, choiceHandlerData.selected,
										choiceHandlerData.items, choiceHandlerData.descriptionText,
										choiceHandlerData.descriptionImage, choiceHandlerData.multiple).show(activity);
							}
							break;
						}
						case MESSAGE_REQUIRE_USER_RECAPTCHA_V2: {
							RecaptchaV2HandlerData recaptchaV2HandlerData = (RecaptchaV2HandlerData) handlerData;
							new RecaptchaV2DialogFragment(handlerData.pendingDataIndex, recaptchaV2HandlerData.referer,
									recaptchaV2HandlerData.apiKey, recaptchaV2HandlerData.invisible,
									recaptchaV2HandlerData.hcaptcha, recaptchaV2HandlerData.challengeExtra)
									.show(activity);
						}
					}
				}
				return true;
			}
			case MESSAGE_SHOW_CAPTCHA_INVALID: {
				FragmentActivity activity = getActivity();
				if (activity != null) {
					ToastUtils.show(activity, R.string.captcha_is_not_valid);
				}
				return true;
			}
		}
		return false;
	}

	private static abstract class HandlerData {
		public final int pendingDataIndex;

		public HandlerData(int pendingDataIndex) {
			this.pendingDataIndex = pendingDataIndex;
		}
	}

	private static class CaptchaHandlerData extends HandlerData {
		public final String chanName;
		public final String captchaType;
		public final String requirement;
		public final String boardName;
		public final String threadNumber;
		public final String description;

		public CaptchaHandlerData(int pendingDataIndex, String chanName, String captchaType, String requirement,
				String boardName, String threadNumber, String description) {
			super(pendingDataIndex);
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

		public ChoiceHandlerData(int pendingDataIndex, int columns, boolean[] selected, Bitmap[] images,
				CharSequence[] items, String descriptionText, Bitmap descriptionImage, boolean multiple) {
			super(pendingDataIndex);
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

		private RecaptchaV2HandlerData(int pendingDataIndex, String referer, String apiKey,
				boolean invisible, boolean hcaptcha, RecaptchaReader.ChallengeExtra challengeExtra) {
			super(pendingDataIndex);
			this.referer = referer;
			this.apiKey = apiKey;
			this.invisible = invisible;
			this.hcaptcha = hcaptcha;
			this.challengeExtra = challengeExtra;
		}
	}

	private static abstract class PendingData {
		public boolean ready = false;

		public boolean await() {
			synchronized (this) {
				while (!ready) {
					try {
						wait();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return false;
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

	private int putPendingData(PendingData pendingData) {
		int index;
		synchronized (pendingDataArray) {
			index = pendingDataStartIndex++;
			pendingDataArray.put(index, pendingData);
		}
		return index;
	}

	private void removePendingData(int index) {
		synchronized (pendingDataArray) {
			pendingDataArray.remove(index);
		}
	}

	public ChanPerformer.CaptchaData requireUserCaptcha(Chan chan, String requirement,
			String boardName, String threadNumber, boolean retry) {
		return requireUserCaptcha(null, chan.configuration.getCaptchaType(), requirement,
				chan.name, boardName, threadNumber, null, retry);
	}

	public ChanPerformer.CaptchaData requireUserCaptcha(ReadCaptchaTask.CaptchaReader captchaReader,
			String captchaType, String requirement, String chanName, String boardName, String threadNumber,
			String description, boolean retry) {
		CaptchaPendingData pendingData = new CaptchaPendingData(captchaReader);
		int pendingDataIndex = putPendingData(pendingData);
		try {
			CaptchaHandlerData handlerData = new CaptchaHandlerData(pendingDataIndex, chanName, captchaType,
					requirement, boardName, threadNumber, description);
			handler.obtainMessage(MESSAGE_REQUIRE_USER_CAPTCHA, handlerData).sendToTarget();
			if (retry) {
				handler.sendEmptyMessage(MESSAGE_SHOW_CAPTCHA_INVALID);
			}
			if (!pendingData.await()) {
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
			removePendingData(pendingDataIndex);
		}
	}

	public Integer requireUserItemSingleChoice(int selected, CharSequence[] items, String descriptionText,
			Bitmap descriptionImage) {
		return requireUserSingleChoice(0, selected, items, null, descriptionText, descriptionImage, false);
	}

	public boolean[] requireUserItemMultipleChoice(boolean[] selected, CharSequence[] items, String descriptionText,
			Bitmap descriptionImage) {
		return requireUserChoice(0, selected, items, null, descriptionText, descriptionImage, true, false);
	}

	public Integer requireUserImageSingleChoice(int columns, int selected, Bitmap[] images,
			String descriptionText, Bitmap descriptionImage) {
		return requireUserSingleChoice(columns, selected, null, images, descriptionText, descriptionImage, true);
	}

	public boolean[] requireUserImageMultipleChoice(int columns, boolean[] selected, Bitmap[] images,
			String descriptionText, Bitmap descriptionImage) {
		return requireUserChoice(columns, selected, null, images, descriptionText, descriptionImage, true, true);
	}

	private Integer requireUserSingleChoice(int columns, int selected, CharSequence[] items, Bitmap[] images,
			String descriptionText, Bitmap descriptionImage, boolean imageChoice) {
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
			String descriptionText, Bitmap descriptionImage, boolean multiple, boolean imageChoice) {
		if (imageChoice && images == null) {
			throw new NullPointerException("Images array is null");
		}
		if (!imageChoice && items == null) {
			throw new NullPointerException("Items array is null");
		}
		ChoicePendingData pendingData = new ChoicePendingData();
		int pendingDataIndex = putPendingData(pendingData);
		try {
			ChoiceHandlerData handlerData = new ChoiceHandlerData(pendingDataIndex, columns, selected, images, items,
					descriptionText, descriptionImage, multiple);
			handler.obtainMessage(MESSAGE_REQUIRE_USER_CHOICE, handlerData).sendToTarget();
			return pendingData.await() ? pendingData.result : null;
		} finally {
			removePendingData(pendingDataIndex);
		}
	}

	public String requireUserRecaptchaV2(String referer, String apiKey,
			boolean invisible, boolean hcaptcha, RecaptchaReader.ChallengeExtra challengeExtra) throws HttpException {
		RecaptchaV2PendingData pendingData = new RecaptchaV2PendingData();
		int pendingDataIndex = putPendingData(pendingData);
		try {
			RecaptchaV2HandlerData handlerData = new RecaptchaV2HandlerData(pendingDataIndex,
					referer, apiKey, invisible, hcaptcha, challengeExtra);
			handler.obtainMessage(MESSAGE_REQUIRE_USER_RECAPTCHA_V2, handlerData).sendToTarget();
			if (!pendingData.await()) {
				return null;
			}
			if (pendingData.exception != null) {
				throw pendingData.exception;
			}
			return pendingData.response;
		} finally {
			removePendingData(pendingDataIndex);
		}
	}

	public static void register(FragmentActivity activity) {
		INSTANCE.activity = new WeakReference<>(activity);
	}

	public static void unregister(FragmentActivity activity) {
		if (INSTANCE.getActivity() == activity) {
			INSTANCE.activity = null;
		}
	}
}
