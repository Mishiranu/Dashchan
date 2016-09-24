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

package com.mishiranu.dashchan.ui.navigator.manager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Looper;
import android.view.View;
import android.widget.ListView;

import chan.util.CommonUtils;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.DownloadManager;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.ToastUtils;

public class ThreadshotPerformer implements DialogInterface.OnCancelListener
{
	private final ListView mListView;
	private final UiManager mUiManager;
	private final String mChanName;
	private final String mBoardName;
	private final String mThreadNumber;
	private final String mThreadTitle;
	private final List<PostItem> mPostItems;
	private final ProgressDialog mDialog;

	private final UiManager.DemandSet mDemandSet = new UiManager.DemandSet();
	private final UiManager.ConfigurationSet mConfigurationSet = new UiManager.ConfigurationSet(null, null, null,
			null, null, null, false, false, false, false, null);

	public ThreadshotPerformer(ListView listView, UiManager uiManager, String chanName, String boardName,
			String threadNumber, String threadTitle, List<PostItem> postItems)
	{
		mListView = listView;
		mUiManager = uiManager;
		mChanName = chanName;
		mBoardName = boardName;
		mThreadNumber = threadNumber;
		mThreadTitle = threadTitle;
		mPostItems = postItems;
		mDialog = new ProgressDialog(listView.getContext());
		mDialog.setMessage(listView.getContext().getString(R.string.message_processing_data));
		mDialog.setCanceledOnTouchOutside(false);
		mDialog.setOnCancelListener(this);
		mDialog.show();
		// isBusy == true, because I must prevent view handling in main thread
		mDemandSet.isBusy = true;
		mDemandSet.selectionMode = UiManager.SELECTION_THREADSHOT;
		mAsyncTask.executeOnExecutor(ConcurrentUtils.SEPARATE_EXECUTOR);
	}

	private View getPostItem(PostItem postItem, View convertView)
	{
		return mUiManager.view().getPostView(postItem, convertView, mListView, mDemandSet, mConfigurationSet);
	}

	private final AsyncTask<Void, Void, InputStream> mAsyncTask = new AsyncTask<Void, Void, InputStream>()
	{
		@Override
		protected InputStream doInBackground(Void... params)
		{
			Looper.prepare();
			long time = System.currentTimeMillis();
			View convertView = null;
			Drawable divider = mListView.getDivider();
			int dividerHeight = divider != null ? divider.getMinimumHeight() : 0;
			int height = 0;
			boolean first = true;
			int width = mListView.getWidth();
			int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
			int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
			for (PostItem postItem : mPostItems)
			{
				convertView = getPostItem(postItem, convertView);
				convertView.measure(widthMeasureSpec, heightMeasureSpec);
				if (!first) height += dividerHeight; else first = false;
				height += convertView.getMeasuredHeight();
			}
			if (isCancelled()) return null;
			InputStream input = null;
			if (height > 0)
			{
				Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
				Canvas canvas = new Canvas(bitmap);
				canvas.drawColor(mUiManager.getColorScheme().windowBackgroundColor);
				for (PostItem postItem : mPostItems)
				{
					if (isCancelled()) return null;
					convertView = getPostItem(postItem, convertView);
					convertView.measure(widthMeasureSpec, heightMeasureSpec);
					convertView.layout(0, 0, convertView.getMeasuredWidth(), convertView.getMeasuredHeight());
					convertView.draw(canvas);
					canvas.translate(0, convertView.getHeight());
					if (divider != null && dividerHeight > 0)
					{
						divider.setBounds(0, 0, width, dividerHeight);
						divider.draw(canvas);
						canvas.translate(0, dividerHeight);
					}
				}
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
				bitmap.recycle();
				input = new ByteArrayInputStream(output.toByteArray());
			}
			CommonUtils.sleepMaxTime(time, 500);
			return input;
		}

		@Override
		protected void onPostExecute(InputStream result)
		{
			mDialog.dismiss();
			if (result != null)
			{
				DownloadManager.getInstance().saveStreamStorage(mListView.getContext(), result, mChanName, mBoardName,
						mThreadNumber, mThreadTitle, "threadshot-" + System.currentTimeMillis() + ".png", false);
			}
			else ToastUtils.show(mListView.getContext(), R.string.message_unknown_error);
		}
	};

	@Override
	public void onCancel(DialogInterface dialog)
	{
		mAsyncTask.cancel(true);
	}
}