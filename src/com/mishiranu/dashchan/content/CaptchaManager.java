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

package com.mishiranu.dashchan.content;

import java.lang.ref.WeakReference;
import java.util.HashMap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.util.Pair;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.content.ChanPerformer;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.app.MainApplication;
import com.mishiranu.dashchan.async.AsyncManager;
import com.mishiranu.dashchan.async.ReadCaptchaTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.graphics.SelectorBorderDrawable;
import com.mishiranu.dashchan.graphics.SelectorCheckDrawable;
import com.mishiranu.dashchan.net.RecaptchaReader;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.CaptchaController;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableView;

public class CaptchaManager implements Handler.Callback
{
	private static final CaptchaManager INSTANCE = new CaptchaManager();
	
	private CaptchaManager()
	{
		
	}
	
	public static CaptchaManager getInstance()
	{
		return INSTANCE;
	}
	
	private static final int MESSAGE_REQUIRE_USER_CAPTCHA = 1;
	private static final int MESSAGE_REQUIRE_ASYNC_CHECK = 2;
	private static final int MESSAGE_REQUIRE_USER_IMAGE_CHOICE = 3;
	private static final int MESSAGE_SHOW_CAPTCHA_INVALID = 4;
	
	private final Handler mHandler = new Handler(Looper.getMainLooper(), this);
	private final SparseArray<PendingData> mPendingDataArray = new SparseArray<>();
	
	private int mPendingDataStartIndex = 0;
	private WeakReference<Activity> mActivity;
	
	private Activity getActivity()
	{
		return mActivity != null ? mActivity.get() : null;
	}
	
	public static class CaptchaDialogFragment extends DialogFragment implements CaptchaController.Callback,
			AsyncManager.Callback, ReadCaptchaTask.Callback
	{
		private static final String EXTRA_PENDING_DATA_INDEX = "pendingDataIndex";
		private static final String EXTRA_CHAN_NAME = "chanName";
		private static final String EXTRA_CAPTCHA_TYPE = "captchaType";
		private static final String EXTRA_REQUIREMENT = "requirement";
		private static final String EXTRA_BOARD_NAME = "boardName";
		private static final String EXTRA_THREAD_NUMBER = "threadNumber";
		private static final String EXTRA_DESCRIPTION_RES_ID = "descriptionResId";
		private static final String EXTRA_TASK_NAME = "taskName";
		
		private static final String EXTRA_CAPTCHA_STATE = "captchaState";
		private static final String EXTRA_LOADED_INPUT = "loadedInput";
		private static final String EXTRA_IMAGE = "image";
		private static final String EXTRA_LARGE = "large";
		private static final String EXTRA_BLACK_AND_WHITE = "blackAndWhite";
		
		private static final String EXTRA_FORCE_CAPTCHA = "forceCaptcha";
		private static final String EXTRA_MAY_SHOW_LOAD_BUTTON = "mayShowLoadButton";
		
		private ChanPerformer.CaptchaState mCaptchaState;
		private ChanConfiguration.Captcha.Input mLoadedInput;
		private Bitmap mImage;
		private boolean mLarge;
		private boolean mBlackAndWhite;
		
		private final CaptchaController mCaptchaController = new CaptchaController(this);
		
		public CaptchaDialogFragment()
		{
			
		}
		
		public CaptchaDialogFragment(int pendingDataIndex, String chanName, String captchaType, String requirement,
				String boardName, String threadNumber, int descriptionResId)
		{
			Bundle args = new Bundle();
			args.putInt(EXTRA_PENDING_DATA_INDEX, pendingDataIndex);
			args.putString(EXTRA_CHAN_NAME, chanName);
			args.putString(EXTRA_CAPTCHA_TYPE, captchaType);
			args.putString(EXTRA_REQUIREMENT, requirement);
			args.putString(EXTRA_BOARD_NAME, boardName);
			args.putString(EXTRA_THREAD_NUMBER, threadNumber);
			args.putString(EXTRA_TASK_NAME, "read_captcha_" + System.currentTimeMillis());
			args.putInt(EXTRA_DESCRIPTION_RES_ID, descriptionResId);
			setArguments(args);
		}
		
		public void show(Activity activity)
		{
			show(activity.getFragmentManager(), getClass().getName());
		}
		
		@Override
		public void onActivityCreated(Bundle savedInstanceState)
		{
			super.onActivityCreated(savedInstanceState);
			CaptchaPendingData pendingData = getPendingData();
			if (pendingData == null)
			{
				dismissAllowingStateLoss();
				return;
			}
			boolean needLoad = true;
			if (pendingData.captchaData != null && savedInstanceState != null)
			{
				ChanPerformer.CaptchaState captchaState = (ChanPerformer.CaptchaState) savedInstanceState
						.getSerializable(EXTRA_CAPTCHA_STATE);
				Bitmap image = savedInstanceState.getParcelable(EXTRA_IMAGE);
				if (captchaState != null)
				{
					showCaptcha(captchaState, pendingData.loadedCaptchaType, (ChanConfiguration.Captcha.Input)
							savedInstanceState.getSerializable(EXTRA_LOADED_INPUT), image,
							savedInstanceState.getBoolean(EXTRA_LARGE),
							savedInstanceState.getBoolean(EXTRA_BLACK_AND_WHITE));
					needLoad = false;
				}
			}
			if (needLoad) reloadCaptcha(pendingData, false, true, false);
		}
		
		@Override
		public void onSaveInstanceState(Bundle outState)
		{
			super.onSaveInstanceState(outState);
			outState.putSerializable(EXTRA_CAPTCHA_STATE, mCaptchaState);
			outState.putSerializable(EXTRA_LOADED_INPUT, mLoadedInput);
			outState.putParcelable(EXTRA_IMAGE, mImage);
			outState.putBoolean(EXTRA_LARGE, mLarge);
			outState.putBoolean(EXTRA_BLACK_AND_WHITE, mBlackAndWhite);
		}
		
		private CaptchaPendingData getPendingData()
		{
			CaptchaManager captchaManager = CaptchaManager.getInstance();
			synchronized (captchaManager.mPendingDataArray)
			{
				int pendingDataIndex = getArguments().getInt(EXTRA_PENDING_DATA_INDEX);
				CaptchaPendingData pendingData = (CaptchaPendingData) captchaManager.mPendingDataArray
						.get(pendingDataIndex);
				return pendingData;
			}
		}
		
		private void reloadCaptcha(CaptchaPendingData pendingData, boolean forceCaptcha, boolean mayShowLoadButton,
				boolean restart)
		{
			pendingData.captchaData = null;
			pendingData.loadedCaptchaType = null;
			mCaptchaController.showLoading();
			HashMap<String, Object> extra = new HashMap<>();
			extra.put(EXTRA_FORCE_CAPTCHA, forceCaptcha);
			extra.put(EXTRA_MAY_SHOW_LOAD_BUTTON, mayShowLoadButton);
			AsyncManager.get(this).startTask(getArguments().getString(EXTRA_TASK_NAME), this, extra, restart);
		}
		
		private static class ReadCaptchaHolder extends AsyncManager.Holder implements ReadCaptchaTask.Callback
		{
			@Override
			public void onReadCaptchaSuccess(ChanPerformer.CaptchaState captchaState,
					ChanPerformer.CaptchaData captchaData, String captchaType, ChanConfiguration.Captcha.Input input,
					ChanConfiguration.Captcha.Validity validity, Bitmap image, boolean large, boolean blackAndWhite)
			{
				storeResult("onReadCaptchaSuccess", captchaState, captchaData, captchaType, input, validity,
						image, large, blackAndWhite);
			}
			
			@Override
			public void onReadCaptchaError(ErrorItem errorItem)
			{
				storeResult("onReadCaptchaError", errorItem);
			}
		}
		
		@Override
		public Pair<Object, AsyncManager.Holder> onCreateAndExecuteTask(String name, HashMap<String, Object> extra)
		{
			Bundle args = getArguments();
			String chanName = args.getString(EXTRA_CHAN_NAME);
			boolean forceCaptcha = (boolean) extra.get(EXTRA_FORCE_CAPTCHA);
			boolean mayShowLoadButton = (boolean) extra.get(EXTRA_MAY_SHOW_LOAD_BUTTON);
			String[] captchaPass = forceCaptcha || chanName == null ? null : Preferences.getCaptchaPass(chanName);
			ReadCaptchaHolder holder = new ReadCaptchaHolder();
			ReadCaptchaTask task = new ReadCaptchaTask(getActivity(), holder, getPendingData().captchaReader,
					args.getString(EXTRA_CAPTCHA_TYPE), args.getString(EXTRA_REQUIREMENT), captchaPass,
					mayShowLoadButton, chanName, args.getString(EXTRA_BOARD_NAME), args.getString(EXTRA_THREAD_NUMBER));
			task.executeOnExecutor(ReadCaptchaTask.THREAD_POOL_EXECUTOR);
			return new Pair<Object, AsyncManager.Holder>(task, holder);
		}
		
		@Override
		public void onFinishTaskExecution(String name, AsyncManager.Holder holder)
		{
			String methodName = holder.nextArgument();
			if ("onReadCaptchaSuccess".equals(methodName))
			{
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
			}
			else if ("onReadCaptchaError".equals(methodName))
			{
				ErrorItem errorItem = holder.nextArgument();
				onReadCaptchaError(errorItem);
			}
		}
		
		@Override
		public void onRequestTaskCancel(String name, Object task)
		{
			((ReadCaptchaTask) task).cancel();
		}
		
		@Override
		public void onReadCaptchaSuccess(ChanPerformer.CaptchaState captchaState, ChanPerformer.CaptchaData captchaData,
				String captchaType, ChanConfiguration.Captcha.Input input, ChanConfiguration.Captcha.Validity validity,
				Bitmap image, boolean large, boolean blackAndWhite)
		{
			CaptchaPendingData pendingData = getPendingData();
			if (pendingData == null)
			{
				dismissAllowingStateLoss();
				return;
			}
			pendingData.captchaData = captchaData;
			pendingData.loadedCaptchaType = captchaType;
			showCaptcha(captchaState, captchaType, input, image, large, blackAndWhite);
		}
		
		@Override
		public void onReadCaptchaError(ErrorItem errorItem)
		{
			CaptchaPendingData pendingData = getPendingData();
			if (pendingData == null)
			{
				dismissAllowingStateLoss();
				return;
			}
			pendingData.captchaData = null;
			pendingData.loadedCaptchaType = null;
			mCaptchaState = null;
			mImage = null;
			mLarge = false;
			mBlackAndWhite = false;
			ToastUtils.show(getActivity(), errorItem);
			mCaptchaController.showError();
		}
		
		private void showCaptcha(ChanPerformer.CaptchaState captchaState, String captchaType,
				ChanConfiguration.Captcha.Input input, Bitmap image, boolean large, boolean blackAndWhite)
		{
			mCaptchaState = captchaState;
			if (captchaType != null && input == null)
			{
				input = ChanConfiguration.get(getArguments().getString(EXTRA_CHAN_NAME)).safe()
						.obtainCaptcha(captchaType).input;
			}
			mLoadedInput = input;
			if (mImage != null && mImage != image) mImage.recycle();
			mImage = image;
			mLarge = large;
			mBlackAndWhite = blackAndWhite;
			boolean invertColors = blackAndWhite && !GraphicsUtils.isLight(ResourceUtils
					.getDialogBackground(getActivity()));
			mCaptchaController.showCaptcha(captchaState, input, image, large, blackAndWhite, invertColors);
		}
		
		private void finishDialog(CaptchaPendingData pendingData)
		{
			AsyncManager.get(this).cancelTask(getArguments().getString(EXTRA_TASK_NAME), this);
			if (pendingData != null)
			{
				synchronized (pendingData)
				{
					pendingData.ready = true;
					pendingData.notifyAll();
				}
			}
		}
		
		@SuppressLint("InflateParams")
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState)
		{
			Activity activity = getActivity();
			Bundle args = getArguments();
			View container = LayoutInflater.from(activity).inflate(R.layout.dialog_captcha, null);
			TextView comment = (TextView) container.findViewById(R.id.comment);
			int descriptionResId = args.getInt(EXTRA_DESCRIPTION_RES_ID);
			if (descriptionResId != 0) comment.setText(descriptionResId); else comment.setVisibility(View.GONE);
			ChanConfiguration.Captcha captcha = ChanConfiguration.get(args.getString(EXTRA_CHAN_NAME))
					.safe().obtainCaptcha(args.getString(EXTRA_CAPTCHA_TYPE));
			EditText captchaInputView = (EditText) container.findViewById(R.id.captcha_input);
			mCaptchaController.setupViews(container, null, captchaInputView, true, captcha);
			AlertDialog alertDialog = new AlertDialog.Builder(activity).setView(container).setTitle(R.string
					.text_confirmation).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					onConfirmCaptcha();
				}
			}).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					cancelInternal();
				}
			}).create();
			alertDialog.setCanceledOnTouchOutside(false);
			return alertDialog;
		}
		
		@Override
		public void onRefreshCapctha(boolean forceRefresh)
		{
			CaptchaPendingData pendingData = getPendingData();
			if (pendingData == null) dismissAllowingStateLoss();
			else reloadCaptcha(pendingData, forceRefresh, false, true);
		}
		
		@Override
		public void onConfirmCaptcha()
		{
			CaptchaPendingData pendingData = getPendingData();
			if (pendingData != null && pendingData.captchaData != null)
			{
				pendingData.captchaData.put(ChanPerformer.CaptchaData.INPUT, mCaptchaController.getInput());
			}
			finishDialog(pendingData);
		}
		
		@Override
		public void onCancel(DialogInterface dialog)
		{
			super.onCancel(dialog);
			cancelInternal();
		}
		
		private void cancelInternal()
		{
			CaptchaPendingData pendingData = getPendingData();
			if (pendingData != null)
			{
				pendingData.captchaData = null;
				pendingData.loadedCaptchaType = null;
			}
			finishDialog(pendingData);
		}
	}
	
	public static class CaptchaCheckDialogFragment extends DialogFragment implements AsyncManager.Callback
	{
		private static final String EXTRA_PENDING_DATA_INDEX = "pendingDataIndex";
		private static final String EXTRA_CAPTCHA_TYPE = "captchaType";
		private static final String EXTRA_TASK_NAME = "taskName";
		
		public CaptchaCheckDialogFragment()
		{
			
		}
		
		public CaptchaCheckDialogFragment(int pendingDataIndex, String captchaType)
		{
			Bundle args = new Bundle();
			args.putInt(EXTRA_PENDING_DATA_INDEX, pendingDataIndex);
			args.putString(EXTRA_CAPTCHA_TYPE, captchaType);
			args.putString(EXTRA_TASK_NAME, "check_captcha_" + System.currentTimeMillis());
			setArguments(args);
		}
		
		public void show(Activity activity)
		{
			show(activity.getFragmentManager(), getClass().getName());
		}
		
		@Override
		public void onActivityCreated(Bundle savedInstanceState)
		{
			super.onActivityCreated(savedInstanceState);
			CaptchaPendingData pendingData = getPendingData();
			if (pendingData == null)
			{
				dismissAllowingStateLoss();
				return;
			}
			Bundle args = getArguments();
			AsyncManager.get(this).startTask(args.getString(EXTRA_TASK_NAME), this, null, false);
		}
		
		private CaptchaPendingData getPendingData()
		{
			CaptchaManager captchaManager = CaptchaManager.getInstance();
			synchronized (captchaManager.mPendingDataArray)
			{
				int pendingDataIndex = getArguments().getInt(EXTRA_PENDING_DATA_INDEX);
				CaptchaPendingData pendingData = (CaptchaPendingData) captchaManager.mPendingDataArray
						.get(pendingDataIndex);
				return pendingData;
			}
		}
		
		@Override
		public Pair<Object, AsyncManager.Holder> onCreateAndExecuteTask(String name, HashMap<String, Object> extra)
		{
			CaptchaPendingData pendingData = getPendingData();
			String apiKey = pendingData.captchaData.get(ChanPerformer.CaptchaData.API_KEY);
			String challenge = pendingData.captchaData.get(ChanPerformer.CaptchaData.CHALLENGE);
			String input = pendingData.captchaData.get(ChanPerformer.CaptchaData.INPUT);
			CheckCaptchaTask task = new CheckCaptchaTask(getArguments().getString(EXTRA_CAPTCHA_TYPE),
					apiKey, challenge, input);
			task.executeOnExecutor(CheckCaptchaTask.THREAD_POOL_EXECUTOR);
			return task.getPair();
		}
		
		@Override
		public void onFinishTaskExecution(String name, AsyncManager.Holder holder)
		{
			CaptchaPendingData pendingData = getPendingData();
			if (pendingData == null)
			{
				dismissAllowingStateLoss();
				return;
			}
			String apiKey = holder.nextArgument();
			String challenge = holder.nextArgument();
			String input = holder.nextArgument();
			ErrorItem errorItem = holder.nextArgument();
			if (errorItem == null)
			{
				pendingData.captchaData.put(ChanPerformer.CaptchaData.API_KEY, apiKey);
				pendingData.captchaData.put(ChanPerformer.CaptchaData.CHALLENGE, challenge);
				pendingData.captchaData.put(ChanPerformer.CaptchaData.INPUT, input);
			}
			else
			{
				pendingData.captchaData = null;
				ToastUtils.show(getActivity(), errorItem);
			}
			finishDialog(pendingData, false);
			dismissAllowingStateLoss();
		}
		
		@Override
		public void onRequestTaskCancel(String name, Object task)
		{
			((CheckCaptchaTask) task).cancel();
		}
		
		private void finishDialog(CaptchaPendingData pendingData, boolean cancel)
		{
			AsyncManager.get(this).cancelTask(getArguments().getString(EXTRA_TASK_NAME), this);
			if (pendingData != null)
			{
				if (cancel) pendingData.captchaData = null;
				synchronized (pendingData)
				{
					pendingData.ready = true;
					pendingData.notifyAll();
				}
			}
		}
		
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState)
		{
			ProgressDialog dialog = new ProgressDialog(getActivity());
			dialog.setMessage(getString(R.string.message_sending));
			dialog.setCanceledOnTouchOutside(false);
			return dialog;
		}
		
		@Override
		public void onCancel(DialogInterface dialog)
		{
			super.onCancel(dialog);
			CaptchaPendingData pendingData = getPendingData();
			if (pendingData != null) pendingData.captchaData = null;
			finishDialog(pendingData, true);
		}
	}
	
	private static class CheckCaptchaTask extends AsyncManager.SimpleTask<Void, Void, Boolean>
	{
		private final HttpHolder mHolder = new HttpHolder();
		
		private final String mCaptchaType;
		private final String mApiKey;
		private final String mChallenge;
		private String mInput;
		
		private ErrorItem mErrorItem;
		
		public CheckCaptchaTask(String captchaType, String apiKey, String challenge, String input)
		{
			mCaptchaType = captchaType;
			mApiKey = apiKey;
			mChallenge = challenge;
			mInput = input;
		}
		
		@Override
		protected Boolean doInBackground(Void... params)
		{
			boolean newRecaptcha = ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_JAVASCRIPT.equals(mCaptchaType) ||
					ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_FALLBACK.equals(mCaptchaType);
			if (newRecaptcha)
			{
				boolean fallback = ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_FALLBACK.equals(mCaptchaType);
				try
				{
					String recaptchaResponse = RecaptchaReader.getInstance().getResponseField2(mHolder, mApiKey,
							mChallenge, mInput, fallback);
					mInput = recaptchaResponse;
					return true;
				}
				catch (HttpException e)
				{
					mErrorItem = e.getErrorItemAndHandle();
					return false;
				}
			}
			else
			{
				mErrorItem = new ErrorItem(ErrorItem.TYPE_UNKNOWN);
				return false;
			}
		}
		
		@Override
		protected void onStoreResult(AsyncManager.Holder holder, Boolean result)
		{
			holder.storeResult(mApiKey, mChallenge, mInput, mErrorItem);
		}
		
		@Override
		public void cancel()
		{
			cancel(true);
			mHolder.interrupt();
		}
	}
	
	public static class ChoiceDialogFragment extends DialogFragment implements View.OnClickListener,
			DialogInterface.OnClickListener
	{
		private static final String EXTRA_PENDING_DATA_INDEX = "pendingDataIndex";
		private static final String EXTRA_COLUMNS = "columns";
		private static final String EXTRA_SELECTED = "selected";
		private static final String EXTRA_IMAGES = "images";
		private static final String EXTRA_DESCRIPTION_TEXT = "descriptionText";
		private static final String EXTRA_DESCRIPTION_IMAGE = "descriptionImage";
		private static final String EXTRA_MULTIPLE = "multiple";

		private FrameLayout[] mSelectionViews;
		private boolean[] mSelected;
		
		public ChoiceDialogFragment()
		{
			
		}
		
		public ChoiceDialogFragment(int pendingDataIndex, int columns, boolean[] selected, Bitmap[] images,
				String descriptionText, Bitmap descriptionImage, boolean multiple)
		{
			Bundle args = new Bundle();
			args.putInt(EXTRA_PENDING_DATA_INDEX, pendingDataIndex);
			args.putInt(EXTRA_COLUMNS, columns);
			args.putBooleanArray(EXTRA_SELECTED, selected);
			args.putParcelableArray(EXTRA_IMAGES, images);
			args.putString(EXTRA_DESCRIPTION_TEXT, descriptionText);
			args.putParcelable(EXTRA_DESCRIPTION_IMAGE, descriptionImage);
			args.putBoolean(EXTRA_MULTIPLE, multiple);
			setArguments(args);
		}
		
		public void show(Activity activity)
		{
			show(activity.getFragmentManager(), getClass().getName());
		}
		
		private void ensureArrays()
		{
			if (mSelectionViews == null)
			{
				int columns = getArguments().getInt(EXTRA_COLUMNS);
				Parcelable[] parcelables = getArguments().getParcelableArray(EXTRA_IMAGES);
				int count = parcelables != null ? parcelables.length : 0;
				mSelectionViews = new FrameLayout[(count + columns - 1) / columns * columns];
				mSelected = new boolean[count];
			}
		}
		
		@Override
		public void onActivityCreated(Bundle savedInstanceState)
		{
			super.onActivityCreated(savedInstanceState);
			PendingData pendingData = getPendingData();
			if (pendingData == null)
			{
				dismissAllowingStateLoss();
				return;
			}
			ensureArrays();
			boolean[] selected = savedInstanceState != null ? savedInstanceState.getBooleanArray(EXTRA_SELECTED) : null;
			if (selected == null) selected = getArguments().getBooleanArray(EXTRA_SELECTED);
			if (selected != null && selected.length == mSelected.length)
			{
				System.arraycopy(selected, 0, mSelected, 0, selected.length);
			}
			for (int i = 0; i < mSelected.length; i++) updateSelection(i);
		}
		
		@Override
		public void onSaveInstanceState(Bundle outState)
		{
			super.onSaveInstanceState(outState);
			outState.putBooleanArray(EXTRA_SELECTED, mSelected);
		}
		
		private ChoicePendingData getPendingData()
		{
			CaptchaManager captchaManager = CaptchaManager.getInstance();
			synchronized (captchaManager.mPendingDataArray)
			{
				int pendingDataIndex = getArguments().getInt(EXTRA_PENDING_DATA_INDEX);
				ChoicePendingData pendingData = (ChoicePendingData) captchaManager.mPendingDataArray
						.get(pendingDataIndex);
				return pendingData;
			}
		}
		
		@SuppressLint("InflateParams")
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState)
		{
			Activity activity = getActivity();
			final float density = ResourceUtils.obtainDensity(activity);
			LinearLayout container = new LinearLayout(activity);
			container.setOrientation(LinearLayout.VERTICAL);
			Parcelable[] parcelables = getArguments().getParcelableArray(EXTRA_IMAGES);
			Bitmap[] images = new Bitmap[parcelables != null ? parcelables.length : 0];
			if (images.length > 0) System.arraycopy(parcelables, 0, images, 0, images.length);
			String descriptionText = getArguments().getString(EXTRA_DESCRIPTION_TEXT);
			Bitmap descriptionImage = getArguments().getParcelable(EXTRA_DESCRIPTION_IMAGE);
			int outerPadding = activity.getResources().getDimensionPixelOffset(R.dimen.dialog_padding_text);
			container.setPadding(outerPadding, outerPadding, outerPadding, outerPadding);
			if (!StringUtils.isEmpty(descriptionText))
			{
				TextView descriptionView = new TextView(activity, null, android.R.attr.textAppearanceListItem);
				descriptionView.setPadding(0, 0, 0, outerPadding);
				descriptionView.setText(descriptionText);
				container.addView(descriptionView);
			}
			int cornersRadius = (int) (2f * density);
			if (descriptionImage != null)
			{
				ImageView imageView = new ImageView(activity)
				{
					@Override
					protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
					{
						super.onMeasure(widthMeasureSpec, heightMeasureSpec);
						int width = getMeasuredWidth();
						int height = 0;
						Drawable drawable = getDrawable();
						if (drawable != null)
						{
							int dw = drawable.getIntrinsicWidth(), dh = drawable.getIntrinsicHeight();
							if (dw > 0 && dh > 0) height = width * dh / dw;
						}
						height = Math.min(height, (int) (120f * density));
						setMeasuredDimension(width, height);
					}
				};
				imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
				imageView.setImageBitmap(descriptionImage);
				container.addView(imageView);
				((LinearLayout.LayoutParams) imageView.getLayoutParams()).setMargins(0, 0, 0, (int) (20f * density));
			}
			int innerPadding = (int) (8f * density);
			int columns = getArguments().getInt(EXTRA_COLUMNS);
			int rows = (images.length + columns - 1) / columns;
			ensureArrays();
			for (int i = 0; i < rows; i++)
			{
				LinearLayout inner = new LinearLayout(activity);
				inner.setOrientation(LinearLayout.HORIZONTAL);
				container.addView(inner, LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT);
				for (int j = 0; j < columns; j++)
				{
					int index = columns * i + j;
					FrameLayout frameLayout = new FrameLayout(activity);
					mSelectionViews[index] = frameLayout;
					LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0,
							LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
					if (j < columns - 1) layoutParams.rightMargin = innerPadding;
					if (i < rows - 1) layoutParams.bottomMargin = innerPadding;
					inner.addView(frameLayout, layoutParams);
					if (index < images.length)
					{
						ImageView imageView = new ImageView(activity)
						{
							@Override
							protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
							{
								super.onMeasure(widthMeasureSpec, heightMeasureSpec);
								setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
							}
						};
						imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
						imageView.setImageBitmap(images[index]);
						ViewUtils.makeRoundedCorners(imageView, cornersRadius, false);
						frameLayout.addView(imageView, FrameLayout.LayoutParams.MATCH_PARENT,
								FrameLayout.LayoutParams.WRAP_CONTENT);
						ClickableView view = new ClickableView(activity);
						view.setTag(index);
						view.setOnClickListener(this);
						frameLayout.addView(view, FrameLayout.LayoutParams.MATCH_PARENT,
								FrameLayout.LayoutParams.MATCH_PARENT);
						if (C.API_LOLLIPOP) frameLayout.setForeground(new SelectorCheckDrawable());
						else frameLayout.setForeground(new SelectorBorderDrawable(activity));
					}
				}
			}
			ScrollView scrollView = new ScrollView(activity);
			scrollView.addView(container, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			final AlertDialog alertDialog = new AlertDialog.Builder(activity).setView(scrollView).setPositiveButton
					(android.R.string.ok, this).setNegativeButton(android.R.string.cancel, this).create();
			alertDialog.setOnShowListener(new DialogInterface.OnShowListener()
			{
				@Override
				public void onShow(DialogInterface dialog)
				{
					alertDialog.getWindow().setLayout((int) (320 * density), WindowManager.LayoutParams.WRAP_CONTENT);
				}
			});
			return alertDialog;
		}
		
		@Override
		public void onClick(View v)
		{
			int index = (int) v.getTag();
			mSelected[index] = !mSelected[index];
			updateSelection(index);
			if (!getArguments().getBoolean(EXTRA_MULTIPLE))
			{
				dismiss();
				storeResult(true);
			}
		}
		
		private void updateSelection(int index)
		{
			Drawable drawable = mSelectionViews[index].getForeground();
			if (C.API_LOLLIPOP) ((SelectorCheckDrawable) drawable).setSelected(mSelected[index], true);
			else ((SelectorBorderDrawable) drawable).setSelected(mSelected[index]);
		}
		
		@Override
		public void onClick(DialogInterface dialog, int which)
		{
			storeResult(which == AlertDialog.BUTTON_POSITIVE);
		}
		
		@Override
		public void onCancel(DialogInterface dialog)
		{
			super.onCancel(dialog);
			storeResult(false);
		}
		
		private void storeResult(boolean success)
		{
			ChoicePendingData pendingData = getPendingData();
			if (pendingData != null)
			{
				synchronized (pendingData)
				{
					pendingData.result = success ? mSelected : null;
					pendingData.ready = true;
					pendingData.notifyAll();
				}
			}
		}
	}
	
	@Override
	public boolean handleMessage(Message msg)
	{
		switch (msg.what)
		{
			case MESSAGE_REQUIRE_USER_CAPTCHA:
			case MESSAGE_REQUIRE_ASYNC_CHECK:
			case MESSAGE_REQUIRE_USER_IMAGE_CHOICE:
			{
				HandlerData handlerData = (HandlerData) msg.obj;
				Activity activity = getActivity();
				if (activity == null)
				{
					PendingData pendingData;
					synchronized (mPendingDataArray)
					{
						pendingData = mPendingDataArray.get(handlerData.pendingDataIndex);
					}
					synchronized (pendingData)
					{
						pendingData.ready = true;
						pendingData.notifyAll();
					}
				}
				else
				{
					switch (msg.what)
					{
						case MESSAGE_REQUIRE_USER_CAPTCHA:
						{
							CaptchaHandlerData captchaHandlerData = (CaptchaHandlerData) handlerData;
							new CaptchaDialogFragment(handlerData.pendingDataIndex, captchaHandlerData.chanName,
									captchaHandlerData.captchaType, captchaHandlerData.requirement,
									captchaHandlerData.boardName, captchaHandlerData.threadNumber,
									captchaHandlerData.descriptionResId).show(activity);
							break;
						}
						case MESSAGE_REQUIRE_ASYNC_CHECK:
						{
							CaptchaHandlerData captchaHandlerData = (CaptchaHandlerData) handlerData;
							new CaptchaCheckDialogFragment(captchaHandlerData.pendingDataIndex,
									captchaHandlerData.captchaType).show(activity);
							break;
						}
						case MESSAGE_REQUIRE_USER_IMAGE_CHOICE:
						{
							ChoiceHandlerData choiceHandlerData = (ChoiceHandlerData) handlerData;
							new ChoiceDialogFragment(handlerData.pendingDataIndex, choiceHandlerData.columns,
									choiceHandlerData.selected, choiceHandlerData.images,
									choiceHandlerData.descriptionText, choiceHandlerData.descriptionImage,
									choiceHandlerData.multiple).show(activity);
							break;
						}
					}
				}
				return true;
			}
			case MESSAGE_SHOW_CAPTCHA_INVALID:
			{
				ToastUtils.show(MainApplication.getInstance(), R.string.message_captcha_not_valid);
				return true;
			}
		}
		return false;
	}
	
	private static abstract class HandlerData
	{
		public final int pendingDataIndex;
		
		public HandlerData(int pendingDataIndex)
		{
			this.pendingDataIndex = pendingDataIndex;
		}
	}
	
	private static class CaptchaHandlerData extends HandlerData
	{
		public final String chanName;
		public final String captchaType;
		public final String requirement;
		public final String boardName;
		public final String threadNumber;
		public final int descriptionResId;
		
		public CaptchaHandlerData(int pendingDataIndex, String chanName, String captchaType, String requirement,
				String boardName, String threadNumber, int descriptionResId)
		{
			super(pendingDataIndex);
			this.chanName = chanName;
			this.captchaType = captchaType;
			this.requirement = requirement;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.descriptionResId = descriptionResId;
		}
	}
	
	private static class ChoiceHandlerData extends HandlerData
	{
		public final int columns;
		public final boolean[] selected;
		public final Bitmap[] images;
		public final String descriptionText;
		public final Bitmap descriptionImage;
		public final boolean multiple;
		
		public ChoiceHandlerData(int pendingDataIndex, int columns, boolean[] selected, Bitmap[] images,
				String descriptionText, Bitmap descriptionImage, boolean multiple)
		{
			super(pendingDataIndex);
			this.columns = columns;
			this.selected = selected;
			this.images = images;
			this.descriptionText = descriptionText;
			this.descriptionImage = descriptionImage;
			this.multiple = multiple;
		}
	}
	
	private static abstract class PendingData
	{
		public boolean ready = false;
	}
	
	private static class CaptchaPendingData extends PendingData
	{
		public final ReadCaptchaTask.CaptchaReader captchaReader;
		
		public ChanPerformer.CaptchaData captchaData;
		public String loadedCaptchaType;
		
		public CaptchaPendingData(ReadCaptchaTask.CaptchaReader captchaReader)
		{
			this.captchaReader = captchaReader;
		}
	}
	
	private static class ChoicePendingData extends PendingData
	{
		public boolean[] result;
	}
	
	public ChanPerformer.CaptchaData requireUserCaptcha(ChanManager.Linked linked, String requirement,
			String boardName, String threadNumber, boolean retry)
	{
		ChanConfiguration configuration = ChanConfiguration.get(linked);
		String captchaType = configuration.getCaptchaType();
		String chanName = linked.getChanName();
		return requireUserCaptcha(null, captchaType, requirement, chanName, boardName, threadNumber, 0, retry);
	}
	
	public ChanPerformer.CaptchaData requireUserCaptcha(ReadCaptchaTask.CaptchaReader captchaReader,
			String captchaType, String requirement, String chanName, String boardName, String threadNumber,
			int descriptionResId, boolean retry)
	{
		CaptchaPendingData pendingData;
		int pendingDataIndex;
		synchronized (mPendingDataArray)
		{
			pendingData = new CaptchaPendingData(captchaReader);
			pendingDataIndex = mPendingDataStartIndex++;
			mPendingDataArray.put(pendingDataIndex, pendingData);
		}
		try
		{
			CaptchaHandlerData handlerData = new CaptchaHandlerData(pendingDataIndex, chanName, captchaType,
					requirement, boardName, threadNumber, descriptionResId);
			mHandler.obtainMessage(MESSAGE_REQUIRE_USER_CAPTCHA, handlerData).sendToTarget();
			if (retry) mHandler.sendEmptyMessage(MESSAGE_SHOW_CAPTCHA_INVALID);
			synchronized (pendingData)
			{
				while (!pendingData.ready)
				{
					try
					{
						pendingData.wait();
					}
					catch (InterruptedException e)
					{
						Thread.currentThread().interrupt();
						return null;
					}
				}
			}
			ChanPerformer.CaptchaData captchaData = pendingData.captchaData;
			if (captchaData != null)
			{
				String workCaptchaType = pendingData.loadedCaptchaType != null
						? pendingData.loadedCaptchaType : captchaType;
				String apiKey = captchaData.get(ChanPerformer.CaptchaData.API_KEY);
				boolean newRecaptcha = ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_JAVASCRIPT.equals(workCaptchaType) ||
						ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_FALLBACK.equals(workCaptchaType);
				if (apiKey != null && newRecaptcha)
				{
					String recaptchaResponse = captchaData.get(ReadCaptchaTask.RECAPTCHA_SKIP_RESPONSE);
					if (recaptchaResponse == null)
					{
						pendingData.ready = false;
						mHandler.obtainMessage(MESSAGE_REQUIRE_ASYNC_CHECK, handlerData).sendToTarget();
						synchronized (pendingData)
						{
							while (!pendingData.ready)
							{
								try
								{
									pendingData.wait();
								}
								catch (InterruptedException e)
								{
									Thread.currentThread().interrupt();
									return null;
								}
							}
						}
						if (pendingData.captchaData == null)
						{
							// Error or canceled, try again
							return requireUserCaptcha(captchaReader, captchaType, requirement,
									chanName, boardName, threadNumber, descriptionResId, false);
						}
					}
					else captchaData.put(ChanPerformer.CaptchaData.INPUT, recaptchaResponse);
				}
			}
			return captchaData;
		}
		finally
		{
			synchronized (mPendingDataArray)
			{
				mPendingDataArray.remove(pendingDataIndex);
			}
		}
	}
	
	public Integer requireUserImageSingleChoice(int columns, int selected, Bitmap[] images,
			String descriptionText, Bitmap descriptionImage)
	{
		boolean[] selectedArray = null;
		if (selected >= 0 && selected < images.length)
		{
			selectedArray = new boolean[images.length];
			selectedArray[selected] = true;
		}
		boolean[] result = requireUserImageChoice(columns, selectedArray, images, descriptionText,
				descriptionImage, false);
		if (result != null)
		{
			for (int i = 0; i < result.length; i++)
			{
				if (result[i]) return i;
			}
			return -1;
		}
		return null;
	}
	
	public boolean[] requireUserImageMultipleChoice(int columns, boolean[] selected, Bitmap[] images,
			String descriptionText, Bitmap descriptionImage)
	{
		return requireUserImageChoice(columns, selected, images, descriptionText, descriptionImage, true);
	}
	
	private boolean[] requireUserImageChoice(int columns, boolean[] selected, Bitmap[] images,
			String descriptionText, Bitmap descriptionImage, boolean multiple)
	{
		if (images == null) throw new NullPointerException("Images array is null");
		ChoicePendingData pendingData;
		int pendingDataIndex;
		synchronized (mPendingDataArray)
		{
			pendingData = new ChoicePendingData();
			pendingDataIndex = mPendingDataStartIndex++;
			mPendingDataArray.put(pendingDataIndex, pendingData);
		}
		try
		{
			ChoiceHandlerData handlerData = new ChoiceHandlerData(pendingDataIndex, columns, selected, images,
					descriptionText, descriptionImage, multiple);
			mHandler.obtainMessage(MESSAGE_REQUIRE_USER_IMAGE_CHOICE, handlerData).sendToTarget();
			synchronized (pendingData)
			{
				while (!pendingData.ready)
				{
					try
					{
						pendingData.wait();
					}
					catch (InterruptedException e)
					{
						Thread.currentThread().interrupt();
						return null;
					}
				}
			}
			return pendingData.result;
		}
		finally
		{
			synchronized (mPendingDataArray)
			{
				mPendingDataArray.remove(pendingDataIndex);
			}
		}
	}
	
	public static void registerForeground(Activity activity)
	{
		INSTANCE.mActivity = new WeakReference<>(activity);
	}
	
	public static void unregisterForeground(Activity activity)
	{
		if (INSTANCE.getActivity() == activity) INSTANCE.mActivity = null;
	}
}