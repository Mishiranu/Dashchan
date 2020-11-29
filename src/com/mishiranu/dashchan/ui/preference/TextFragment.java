package com.mishiranu.dashchan.ui.preference;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import chan.content.ChanMarkup;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.LocaleManager;
import com.mishiranu.dashchan.content.async.ReadChangelogTask;
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.text.style.HeadingSpan;
import com.mishiranu.dashchan.ui.ContentFragment;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.CommentTextView;
import com.mishiranu.dashchan.widget.ExpandedLayout;
import com.mishiranu.dashchan.widget.ThemeEngine;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TextFragment extends ContentFragment implements View.OnClickListener {
	private static final String EXTRA_TYPE = "type";

	private static final String EXTRA_CHANGELOG_ENTRIES = "changelogEntries";
	private static final String EXTRA_ERROR_ITEM = "errorItem";

	public enum Type {
		LICENSES(markup -> {
			markup.addTag("h1", ChanMarkup.TAG_HEADING);
			markup.addTag("pre", ChanMarkup.TAG_CODE);
		}),
		CHANGELOG(null);

		private final ChanMarkup.MarkupBuilder builder;

		Type(ChanMarkup.MarkupBuilder.Constructor constructor) {
			builder = constructor != null ? new ChanMarkup.MarkupBuilder(constructor) : null;
		}
	}

	private View contentView;
	private CommentTextView textView;
	private ViewFactory.ErrorHolder errorHolder;
	private View progressView;

	private List<ReadChangelogTask.Entry> changelogEntries;
	private ErrorItem errorItem;

	public TextFragment() {}

	public TextFragment(Type type) {
		Bundle args = new Bundle();
		args.putString(EXTRA_TYPE, type.name());
		setArguments(args);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		float density = ResourceUtils.obtainDensity(this);
		ExpandedLayout layout = new ExpandedLayout(container.getContext(), true);
		layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		ScrollView scrollView = new ScrollView(layout.getContext()) {
			@Override
			public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
				// Don't scroll on select
				return true;
			}
		};
		scrollView.setId(android.R.id.list);
		scrollView.setClipToPadding(false);
		ThemeEngine.applyStyle(scrollView);
		layout.addView(scrollView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
		contentView = scrollView;
		FrameLayout frameLayout = new FrameLayout(scrollView.getContext());
		frameLayout.setOnClickListener(this);
		scrollView.addView(frameLayout, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		textView = new CommentTextView(requireActivity(), null, android.R.attr.textAppearanceLarge);
		int padding = (int) (16f * density);
		textView.setPadding(padding, padding, padding, padding);
		ViewUtils.setTextSizeScaled(textView, 14);
		frameLayout.addView(textView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		errorHolder = ViewFactory.createErrorLayout(layout);
		errorHolder.layout.setVisibility(View.GONE);
		layout.addView(errorHolder.layout);
		FrameLayout progress = new FrameLayout(layout.getContext());
		layout.addView(progress, ExpandedLayout.LayoutParams.MATCH_PARENT, ExpandedLayout.LayoutParams.MATCH_PARENT);
		progress.setVisibility(View.GONE);
		progressView = progress;
		ProgressBar progressBar = new ProgressBar(progress.getContext());
		progress.addView(progressBar, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
		((FrameLayout.LayoutParams) progressBar.getLayoutParams()).gravity = Gravity.CENTER;
		ThemeEngine.applyStyle(progressBar);
		return layout;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		contentView = null;
		textView = null;
		errorHolder = null;
		progressView = null;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		switch (Type.valueOf(requireArguments().getString(EXTRA_TYPE))) {
			case LICENSES: {
				((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.foss_licenses), null);
				setText(IOUtils.readRawResourceString(getResources(), R.raw.markup_licenses));
				break;
			}
			case CHANGELOG: {
				((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.changelog), null);
				changelogEntries = savedInstanceState != null ? savedInstanceState
						.getParcelableArrayList(EXTRA_CHANGELOG_ENTRIES) : null;
				errorItem = savedInstanceState != null ? savedInstanceState.getParcelable(EXTRA_ERROR_ITEM) : null;
				if (errorItem != null) {
					contentView.setVisibility(View.GONE);
					errorHolder.layout.setVisibility(View.VISIBLE);
					errorHolder.text.setText(errorItem.toString());
				} else if (changelogEntries != null) {
					setText(formatChangelogEntries(changelogEntries));
				} else {
					contentView.setVisibility(View.GONE);
					progressView.setVisibility(View.VISIBLE);
					ChangelogViewModel viewModel = new ViewModelProvider(this).get(ChangelogViewModel.class);
					if (!viewModel.hasTaskOrValue()) {
						ReadChangelogTask task = new ReadChangelogTask(viewModel.callback,
								LocaleManager.getInstance().getLocales(getResources().getConfiguration()));
						task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
						viewModel.attach(task);
					}
					viewModel.observe(getViewLifecycleOwner(), (entries, errorItem) -> {
						changelogEntries = entries;
						this.errorItem = errorItem;
						progressView.setVisibility(View.GONE);
						if (entries != null) {
							contentView.setVisibility(View.VISIBLE);
							setText(formatChangelogEntries(entries));
						} else {
							errorItem = errorItem != null ? errorItem : new ErrorItem(ErrorItem.Type.UNKNOWN);
							errorHolder.layout.setVisibility(View.VISIBLE);
							errorHolder.text.setText(errorItem.toString());
						}
					});
				}
				break;
			}
		}
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		switch (Type.valueOf(requireArguments().getString(EXTRA_TYPE))) {
			case CHANGELOG: {
				outState.putParcelable(EXTRA_ERROR_ITEM, errorItem);
				outState.putParcelableArrayList(EXTRA_CHANGELOG_ENTRIES, new ArrayList<>(changelogEntries));
				break;
			}
		}
	}

	private CharSequence formatChangelogEntries(List<ReadChangelogTask.Entry> changelogEntries) {
		DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(requireContext());
		SpannableStringBuilder builder = new SpannableStringBuilder();
		for (ReadChangelogTask.Entry entry : changelogEntries) {
			if (builder.length() > 0) {
				builder.append("\n\n");
			}
			String header;
			ReadChangelogTask.Entry.Version start = entry.versions.get(0);
			if (entry.versions.size() >= 2) {
				ReadChangelogTask.Entry.Version end = entry.versions.get(entry.versions.size() - 1);
				header = start.name + " " + formatChangelogDate(dateFormat, start.date) + " — " +
						end.name + " " + formatChangelogDate(dateFormat, end.date);
			} else {
				header = start.name + " " + formatChangelogDate(dateFormat, start.date);
			}
			builder.append(header);
			builder.setSpan(new HeadingSpan(), builder.length() - header.length(), builder.length(),
					SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
			builder.append("\n\n");
			boolean newLine = false;
			for (String line : entry.text.split("\n")) {
				if (!line.isEmpty()) {
					if (!newLine) {
						newLine = true;
					} else {
						builder.append('\n');
					}
					boolean bullet = false;
					if (line.startsWith("*")) {
						line = line.substring(1).trim();
						bullet = true;
					}
					if (bullet) {
						builder.append("\u2022 ");
					}
					if (line.startsWith("[")) {
						int end = line.indexOf(']');
						if (end >= 0) {
							String prefix = line.substring(0, end + 1);
							line = line.substring(end + 1).trim();
							builder.append(prefix);
							builder.setSpan(new PrefixSpan(), builder.length() - prefix.length(), builder.length(),
									SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
							builder.append(' ');
						}
					}
					builder.append(line);
				}
			}
		}
		return builder;
	}

	private void setText(CharSequence text) {
		Type type = Type.valueOf(requireArguments().getString(EXTRA_TYPE));
		if (type.builder != null) {
			text = type.builder.fromHtmlReduced(text.toString());
		}
		ThemeEngine.getColorScheme(requireContext()).apply(text);
		textView.setText(text);
	}

	private long lastClickTime;

	@Override
	public void onClick(View v) {
		long time = SystemClock.elapsedRealtime();
		if (time - lastClickTime < ViewConfiguration.getDoubleTapTimeout()) {
			lastClickTime = 0L;
			textView.startSelection();
		} else {
			lastClickTime = time;
		}
	}

	private static final SimpleDateFormat DATE_FORMAT_CHANGELOG = new SimpleDateFormat("dd.MM.yyyy", Locale.US);

	public static String formatChangelogDate(DateFormat dateFormat, String dateString) {
		long date;
		try {
			date = DATE_FORMAT_CHANGELOG.parse(dateString).getTime();
		} catch (java.text.ParseException e) {
			e.printStackTrace();
			return null;
		}
		return dateFormat.format(date);
	}

	public static class ChangelogViewModel extends TaskViewModel.Proxy<ReadChangelogTask, ReadChangelogTask.Callback> {}

	private static class PrefixSpan extends ReplacementSpan implements ColorScheme.Span {
		private final Paint.FontMetricsInt fontMetrics = new Paint.FontMetricsInt();
		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final RectF rect = new RectF();

		private int background;
		private int foreground;

		private static void updateTextSize(Paint paint) {
			paint.setTextSize((int) (11f / 14f * paint.getTextSize() + 0.5f));
			paint.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
		}

		@Override
		public void applyColorScheme(ColorScheme colorScheme) {
			background = colorScheme.linkColor;
			foreground = colorScheme.windowBackgroundColor;
		}

		private static CharSequence getText(CharSequence text, int start, int end) {
			StringBuilder builder = new StringBuilder();
			for (int i = start + 1; i < end - 1; i++) {
				builder.append(Character.toUpperCase(text.charAt(i)));
			}
			return builder;
		}

		@Override
		public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
			Paint workPaint = this.paint;
			workPaint.set(paint);
			updateTextSize(workPaint);
			CharSequence drawText = getText(text, start, end);
			return (int) (workPaint.measureText(drawText, 0, drawText.length()) +
					2 * workPaint.measureText(" ", 0, 1) + 0.5f);
		}

		@Override
		public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
				float x, int top, int y, int bottom, @NonNull Paint paint) {
			Paint.FontMetricsInt fontMetrics = this.fontMetrics;
			paint.getFontMetricsInt(fontMetrics);
			Paint workPaint = this.paint;
			int fullSize = getSize(paint, text, start, end, fontMetrics);
			workPaint.setColor(background);
			float radius = workPaint.getTextSize() / 11f;
			float padding = fontMetrics.descent / 3f;
			int baseline = bottom - fontMetrics.bottom;
			rect.set(x, baseline + fontMetrics.ascent + padding, x + fullSize,
					baseline + fontMetrics.descent - padding);
			canvas.drawRoundRect(rect, radius, radius, workPaint);
			workPaint.setColor(foreground);
			CharSequence drawText = getText(text, start, end);
			int textSize = (int) (workPaint.measureText(drawText, 0, drawText.length()) + 0.5f);
			float dx = (fullSize - textSize) / 2f;
			float topDy = fontMetrics.ascent;
			float bottomDy = fontMetrics.descent;
			workPaint.getFontMetricsInt(fontMetrics);
			topDy -= fontMetrics.ascent;
			bottomDy -= fontMetrics.descent;
			float dy = (topDy + bottomDy) / 2f;
			canvas.drawText(drawText, 0, drawText.length(), x + dx, baseline + dy, workPaint);
		}
	}
}
