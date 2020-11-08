package com.mishiranu.dashchan.ui.navigator.manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;
import chan.util.CommonUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.MainApplication;
import com.mishiranu.dashchan.content.async.ExecutorTask;
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.ui.InstanceDialog;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.ProgressDialog;
import com.mishiranu.dashchan.widget.ThemeEngine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

public class ThreadshotPerformer {
	private final UiManager uiManager;
	private final String chanName;
	private final List<PostItem> postItems;
	private final RecyclerView.ViewHolder holder;
	private final Drawable divider;
	private final int width;
	private final int background;

	private ThreadshotViewModel viewModel;

	public ThreadshotPerformer(FragmentManager fragmentManager, String chanName, String boardName,
			String threadNumber, String threadTitle, List<PostItem> postItems, int width) {
		Context context = ThemeEngine.attach(new ContextThemeWrapper
				(MainApplication.getInstance().getLocalizedContext(), 0));
		ThemeEngine.applyTheme(context);
		this.uiManager = new UiManager(context, null, null);
		this.chanName = chanName;
		this.postItems = postItems;
		holder = uiManager.view().createView(new FrameLayout(context), ViewUnit.ViewType.POST);
		divider = ResourceUtils.getDrawable(context, android.R.attr.listDivider, 0);
		this.width = width;
		background = ThemeEngine.getColorScheme(context).windowBackgroundColor;
		start(this, fragmentManager, chanName, boardName, threadNumber, threadTitle);
	}

	private static void start(ThreadshotPerformer performer, FragmentManager fragmentManager,
			String chanName, String boardName, String threadNumber, String threadTitle) {
		new InstanceDialog(fragmentManager, null, provider -> {
			ProgressDialog dialog = new ProgressDialog(provider.getContext(), null);
			dialog.setMessage(provider.getContext().getString(R.string.processing_data__ellipsis));
			ThreadshotViewModel viewModel = provider.getViewModel(ThreadshotViewModel.class);
			if (!viewModel.hasTaskOrValue()) {
				performer.viewModel = viewModel;
				performer.task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
				viewModel.attach(performer.task);
			}
			viewModel.observe(provider.getLifecycleOwner(), result -> {
				provider.dismiss();
				if (result != null) {
					DownloadService.Binder binder = UiManager.extract(provider).callback().getDownloadBinder();
					if (binder != null) {
						binder.downloadStorage(result, chanName, boardName, threadNumber, threadTitle,
								"threadshot-" + System.currentTimeMillis() + ".png", true, false);
					}
				} else {
					ClickableToast.show(R.string.unknown_error);
				}
			});
			return dialog;
		});
	}

	public static class ThreadshotViewModel extends TaskViewModel<ExecutorTask<Void, InputStream>, InputStream> {}

	private final ExecutorTask<Void, InputStream> task = new ExecutorTask<Void, InputStream>() {
		@Override
		protected InputStream run() {
			long time = SystemClock.elapsedRealtime();
			UiManager.ConfigurationSet configurationSet = new UiManager.ConfigurationSet(chanName, null,
					null, UiManager.PostStateProvider.DEFAULT, null, null, null, null, null,
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
			viewModel.handleResult(result);
		}
	};
}
