package com.mishiranu.dashchan.ui.navigator.manager;

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
import com.mishiranu.dashchan.widget.ProgressDialog;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

public class ThreadshotPerformer implements DialogInterface.OnCancelListener {
	private final ListView listView;
	private final UiManager uiManager;
	private final String chanName;
	private final String boardName;
	private final String threadNumber;
	private final String threadTitle;
	private final List<PostItem> postItems;
	private final ProgressDialog dialog;

	private final UiManager.DemandSet demandSet = new UiManager.DemandSet();
	private final UiManager.ConfigurationSet configurationSet = new UiManager.ConfigurationSet(null, null, null,
			null, null, null, false, false, false, false, false, null);

	public ThreadshotPerformer(ListView listView, UiManager uiManager, String chanName, String boardName,
			String threadNumber, String threadTitle, List<PostItem> postItems) {
		this.listView = listView;
		this.uiManager = uiManager;
		this.chanName = chanName;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.threadTitle = threadTitle;
		this.postItems = postItems;
		dialog = new ProgressDialog(listView.getContext(), null);
		dialog.setMessage(listView.getContext().getString(R.string.message_processing_data));
		dialog.setOnCancelListener(this);
		dialog.show();
		// isBusy == true, because I must prevent view handling in main thread
		demandSet.isBusy = true;
		demandSet.selectionMode = UiManager.SELECTION_THREADSHOT;
		asyncTask.executeOnExecutor(ConcurrentUtils.SEPARATE_EXECUTOR);
	}

	private View getPostItem(PostItem postItem, View convertView) {
		return uiManager.view().getPostView(postItem, convertView, listView, demandSet, configurationSet);
	}

	private final AsyncTask<Void, Void, InputStream> asyncTask = new AsyncTask<Void, Void, InputStream>() {
		@Override
		protected InputStream doInBackground(Void... params) {
			Looper.prepare();
			long time = System.currentTimeMillis();
			View convertView = null;
			Drawable divider = listView.getDivider();
			int dividerHeight = divider != null ? divider.getMinimumHeight() : 0;
			int height = 0;
			boolean first = true;
			int width = listView.getWidth();
			int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
			int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
			for (PostItem postItem : postItems) {
				convertView = getPostItem(postItem, convertView);
				convertView.measure(widthMeasureSpec, heightMeasureSpec);
				if (!first) {
					height += dividerHeight;
				} else {
					first = false;
				}
				height += convertView.getMeasuredHeight();
			}
			if (isCancelled()) {
				return null;
			}
			InputStream input = null;
			if (height > 0) {
				Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
				Canvas canvas = new Canvas(bitmap);
				canvas.drawColor(uiManager.getColorScheme().windowBackgroundColor);
				for (PostItem postItem : postItems) {
					if (isCancelled()) {
						return null;
					}
					convertView = getPostItem(postItem, convertView);
					convertView.measure(widthMeasureSpec, heightMeasureSpec);
					convertView.layout(0, 0, convertView.getMeasuredWidth(), convertView.getMeasuredHeight());
					convertView.draw(canvas);
					canvas.translate(0, convertView.getHeight());
					if (divider != null && dividerHeight > 0) {
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
		protected void onPostExecute(InputStream result) {
			dialog.dismiss();
			if (result != null) {
				DownloadManager.getInstance().saveStreamStorage(listView.getContext(), result, chanName, boardName,
						threadNumber, threadTitle, "threadshot-" + System.currentTimeMillis() + ".png", false);
			} else {
				ToastUtils.show(listView.getContext(), R.string.message_unknown_error);
			}
		}
	};

	@Override
	public void onCancel(DialogInterface dialog) {
		asyncTask.cancel(true);
	}
}
