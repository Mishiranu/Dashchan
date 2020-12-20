package com.mishiranu.dashchan.ui.preference;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.text.style.ReplacementSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.ChanMarkup;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.LocaleManager;
import com.mishiranu.dashchan.content.async.ReadChangelogTask;
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.text.SpanComparator;
import com.mishiranu.dashchan.text.style.HeadingSpan;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.CommentTextView;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.ExpandedLayout;
import com.mishiranu.dashchan.widget.PostLinearLayout;
import com.mishiranu.dashchan.widget.PostsLayoutManager;
import com.mishiranu.dashchan.widget.ThemeEngine;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TextFragment extends BaseListFragment {
	private static final String EXTRA_TYPE = "type";

	private static final String EXTRA_CHANGELOG_ENTRIES = "changelogEntries";
	private static final String EXTRA_ERROR_ITEM = "errorItem";

	public enum Type {LICENSES, CHANGELOG}

	private List<ReadChangelogTask.Entry> changelogEntries;
	private ErrorItem errorItem;

	private View progressView;

	public TextFragment() {}

	public TextFragment(Type type) {
		Bundle args = new Bundle();
		args.putString(EXTRA_TYPE, type.name());
		setArguments(args);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		ExpandedLayout layout = (ExpandedLayout) super.onCreateView(inflater, container, savedInstanceState);
		progressView = ViewFactory.createProgressLayout(layout);
		return layout;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		progressView = null;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		RecyclerView recyclerView = getRecyclerView();
		Context context = recyclerView.getContext();
		recyclerView.setLayoutManager(new PostsLayoutManager(context));
		TextAdapter adapter = new TextAdapter();
		recyclerView.setAdapter(adapter);

		switch (Type.valueOf(requireArguments().getString(EXTRA_TYPE))) {
			case LICENSES: {
				((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.foss_licenses), null);
				String text = IOUtils.readRawResourceString(getResources(), R.raw.markup_licenses);
				adapter.setItems(context, formatText(text));
				break;
			}
			case CHANGELOG: {
				((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.changelog), null);
				changelogEntries = savedInstanceState != null ? savedInstanceState
						.getParcelableArrayList(EXTRA_CHANGELOG_ENTRIES) : null;
				errorItem = savedInstanceState != null ? savedInstanceState.getParcelable(EXTRA_ERROR_ITEM) : null;
				if (errorItem != null) {
					recyclerView.setVisibility(View.GONE);
					setErrorText(errorItem.toString());
				} else if (changelogEntries != null) {
					adapter.setItems(context, formatChangelogEntries(context, changelogEntries));
				} else {
					recyclerView.setVisibility(View.GONE);
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
							recyclerView.setVisibility(View.VISIBLE);
							adapter.setItems(context, formatChangelogEntries(context, entries));
						} else {
							errorItem = errorItem != null ? errorItem : new ErrorItem(ErrorItem.Type.UNKNOWN);
							setErrorText(errorItem.toString());
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

	private static final ChanMarkup.MarkupBuilder BUILDER = new ChanMarkup.MarkupBuilder(markup -> {
		markup.addTag("h1", ChanMarkup.TAG_HEADING);
		markup.addTag("pre", ChanMarkup.TAG_CODE);
	});

	private static List<CharSequence> formatText(String html) {
		CharSequence text = BUILDER.fromHtmlReduced(html);
		SpannableStringBuilder builder = new SpannableStringBuilder(text);
		HeadingSpan[] spans = builder.getSpans(0, builder.length(), HeadingSpan.class);
		if (spans != null && spans.length > 1) {
			Arrays.sort(spans, new SpanComparator(builder, SpanComparator.Property.START));
			ArrayList<CharSequence> items = new ArrayList<>();
			for (int i = 0; i < spans.length; i++) {
				int start = builder.getSpanStart(spans[i]);
				int end = i < spans.length - 1 ? builder.getSpanStart(spans[i + 1]) : builder.length();
				SpannableStringBuilder subBuilder = new SpannableStringBuilder(builder, start, end);
				int subEnd = subBuilder.length();
				while (subEnd > 0) {
					if (subBuilder.charAt(subEnd - 1) == '\n') {
						subEnd--;
					} else {
						break;
					}
				}
				if (subEnd < subBuilder.length()) {
					subBuilder.delete(subEnd, subBuilder.length());
				}
				if (subBuilder.length() > 0) {
					int spanEnd = subBuilder.getSpanEnd(spans[i]);
					subBuilder.removeSpan(spans[i]);
					subBuilder.setSpan(new ListHeaderSpan(false), 0, spanEnd,
							SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
					items.add(subBuilder);
				}
			}
			return items;
		} else {
			return Collections.singletonList(builder);
		}
	}

	private static List<CharSequence> formatChangelogEntries(Context context,
			List<ReadChangelogTask.Entry> changelogEntries) {
		DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(context);
		ArrayList<CharSequence> items = new ArrayList<>();
		String versionText = context.getString(R.string.version);
		for (ReadChangelogTask.Entry entry : changelogEntries) {
			SpannableStringBuilder builder = new SpannableStringBuilder();
			String header;
			String subHeader;
			ReadChangelogTask.Entry.Version start = entry.versions.get(0);
			String startName = start.getMajorMinor();
			String startDate = formatChangelogDate(dateFormat, start.date);
			if (entry.versions.size() >= 2) {
				ReadChangelogTask.Entry.Version end = entry.versions.get(entry.versions.size() - 1);
				String endName = end.getMajorMinor();
				String endDate = formatChangelogDate(dateFormat, end.date);
				if (startName.equals(endName)) {
					if (startDate.equals(endDate)) {
						header = versionText + " " + startName;
						subHeader = startDate;
					} else {
						header = versionText + " " + startName;
						subHeader = startDate + " — " + endDate;
					}
				} else {
					header = versionText + " " + startName + " — " + endName;
					subHeader = startDate + " — " + endDate;
				}
			} else {
				String startNameSuffix = start.name.substring(startName.length());
				if (startNameSuffix.equals(".0")) {
					header = versionText + " " + startName;
				} else {
					header = versionText + " " + start.name;
				}
				subHeader = startDate;
			}
			builder.append(header);
			builder.setSpan(new ListHeaderSpan(false), builder.length() - header.length(), builder.length(),
					SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
			builder.append("\n");
			builder.append(subHeader);
			builder.append("\n\n");
			boolean newLine = false;
			for (String text : entry.texts) {
				for (String line : text.split("\n")) {
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
			items.add(StringUtils.reduceEmptyLines(builder));
		}
		return items;
	}

	private static int getPadding(Resources resources) {
		float density = ResourceUtils.obtainDensity(resources);
		return (int) (16f * density);
	}

	@Override
	protected void setListPadding(RecyclerView recyclerView) {}

	@Override
	protected DividerItemDecoration.Configuration configureDivider
			(DividerItemDecoration.Configuration configuration, int position) {
		int padding = getPadding(getResources());
		return configuration.need(true).horizontal(padding, padding);
	}

	private static class ListHeaderSpan extends RelativeSizeSpan {
		public ListHeaderSpan(boolean sub) {
			super((sub ? 12f : 16f) / 14f);
		}
	}

	private static class TextAdapter extends RecyclerView.Adapter<TextAdapter.ViewHolder> {
		private static class ViewHolder extends RecyclerView.ViewHolder
				implements ListViewUtils.ClickCallback<Void, ViewHolder> {
			public final CommentTextView textView;

			private long lastClickTime;

			public ViewHolder(ViewGroup parent) {
				super(new PostLinearLayout(parent.getContext()));
				PostLinearLayout layout = (PostLinearLayout) itemView;
				layout.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT,
						RecyclerView.LayoutParams.WRAP_CONTENT));
				ViewUtils.setSelectableItemBackground(layout);
				textView = new CommentTextView(parent.getContext(), null, android.R.attr.textAppearance);
				ViewUtils.setTextSizeScaled(textView, 14);
				int padding = getPadding(textView.getResources());
				textView.setPadding(padding, padding, padding, padding);
				layout.addView(textView, PostLinearLayout.LayoutParams.MATCH_PARENT,
						PostLinearLayout.LayoutParams.WRAP_CONTENT);
				ListViewUtils.bind(this, false, null, this);
			}

			@Override
			public boolean onItemClick(ViewHolder holder, int position, Void item, boolean longClick) {
				long time = SystemClock.elapsedRealtime();
				if (time - lastClickTime < ViewConfiguration.getDoubleTapTimeout()) {
					lastClickTime = 0;
					textView.startSelection();
				} else {
					lastClickTime = time;
				}
				return true;
			}
		}

		private List<CharSequence> items = Collections.emptyList();

		public void setItems(Context context, List<CharSequence> items) {
			ColorScheme colorScheme = ThemeEngine.getColorScheme(context);
			for (CharSequence text : items) {
				colorScheme.apply(text);
			}
			this.items = items;
			notifyDataSetChanged();
		}

		@Override
		public int getItemCount() {
			return items.size();
		}

		@NonNull
		@Override
		public TextAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return new ViewHolder(parent);
		}

		@Override
		public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
			holder.textView.setText(items.get(position));
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
