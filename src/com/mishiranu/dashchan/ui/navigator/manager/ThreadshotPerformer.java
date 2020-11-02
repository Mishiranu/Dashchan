package com.mishiranu.dashchan.ui.navigator.manager;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import chan.util.CommonUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.ExecutorTask;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.widget.ProgressDialog;
import com.mishiranu.dashchan.widget.ThemeEngine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

public class ThreadshotPerformer implements DialogInterface.OnCancelListener {
	private final ViewGroup parent;
	private final UiManager uiManager;
	private final String chanName;
	private final String boardName;
	private final String threadNumber;
	private final String threadTitle;
	private final List<PostItem> postItems;
	private final RecyclerView.ViewHolder holder;
	private final Drawable divider;
	private final int width;
	private final int background;
	private final ProgressDialog dialog;

	public ThreadshotPerformer(ViewGroup parent, UiManager uiManager, String chanName, String boardName,
			String threadNumber, String threadTitle, List<PostItem> postItems) {
		this.parent = parent;
		this.uiManager = uiManager;
		this.chanName = chanName;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.threadTitle = threadTitle;
		this.postItems = postItems;
		holder = uiManager.view().createView(parent, ViewUnit.ViewType.POST);
		divider = ResourceUtils.getDrawable(parent.getContext(), android.R.attr.listDivider, 0);
		width = parent.getWidth();
		background = ThemeEngine.getColorScheme(parent.getContext()).windowBackgroundColor;
		dialog = new ProgressDialog(parent.getContext(), null);
		dialog.setMessage(parent.getContext().getString(R.string.processing_data__ellipsis));
		dialog.setOnCancelListener(this);
		uiManager.getConfigurationLock().lockConfiguration(dialog);
		dialog.show();
		task.execute(ConcurrentUtils.SEPARATE_EXECUTOR);
	}

	private final ExecutorTask<Void, InputStream> task = new ExecutorTask<Void, InputStream>() {
		@Override
		protected InputStream run() {
			long time = SystemClock.elapsedRealtime();
			UiManager.ConfigurationSet configurationSet = new UiManager.ConfigurationSet(chanName, null, null,
					UiManager.PostStateProvider.DEFAULT, null, null, null, null,
					false, false, false, false, false, null);
			UiManager.DemandSet demandSet = new UiManager.DemandSet();
			demandSet.selection = UiManager.Selection.THREADSHOT;
			int dividerHeight = divider.getIntrinsicHeight();
			int dividerPadding = (int) (12f * ResourceUtils.obtainDensity(uiManager.getContext()));
			int height = 0;
			boolean first = true;
			int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
			int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
			for (PostItem postItem : postItems) {
				int measuredHeight = ConcurrentUtils.mainGet(() -> {
					uiManager.view().bindPostView(holder, postItem, configurationSet, demandSet);
					holder.itemView.measure(widthMeasureSpec, heightMeasureSpec);
					return holder.itemView.getMeasuredHeight();
				});
				if (!first) {
					height += dividerHeight;
				} else {
					first = false;
				}
				height += measuredHeight;
			}
			if (isCancelled()) {
				return null;
			}
			InputStream input = null;
			if (height > 0) {
				Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
				Canvas canvas = new Canvas(bitmap);
				canvas.drawColor(background);
				for (PostItem postItem : postItems) {
					if (isCancelled()) {
						return null;
					}
					ConcurrentUtils.mainGet(() -> {
						uiManager.view().bindPostView(holder, postItem, configurationSet, demandSet);
						holder.itemView.measure(widthMeasureSpec, heightMeasureSpec);
						holder.itemView.layout(0, 0, holder.itemView.getMeasuredWidth(),
								holder.itemView.getMeasuredHeight());
						holder.itemView.draw(canvas);
						canvas.translate(0, holder.itemView.getHeight());
						if (divider != null && dividerHeight > 0) {
							divider.setBounds(dividerPadding, 0, width - dividerPadding, dividerHeight);
							divider.draw(canvas);
							canvas.translate(0, dividerHeight);
						}
						return null;
					});
				}
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
				bitmap.recycle();
				input = new ByteArrayInputStream(output.toByteArray());
			}
			CommonUtils.sleepMaxRealtime(time, 500);
			return input;
		}

		@Override
		protected void onComplete(InputStream result) {
			dialog.dismiss();
			if (result != null) {
				DownloadService.Binder binder = uiManager.callback().getDownloadBinder();
				if (binder != null) {
					binder.downloadStorage(result, chanName, boardName, threadNumber, threadTitle,
							"threadshot-" + System.currentTimeMillis() + ".png", true, false);
				}
			} else {
				ToastUtils.show(parent.getContext(), R.string.unknown_error);
			}
		}
	};

	@Override
	public void onCancel(DialogInterface dialog) {
		task.cancel();
	}
}
