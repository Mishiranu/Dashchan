package com.mishiranu.dashchan.ui.preference;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Pair;
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
import chan.content.Chan;
import chan.content.ChanMarkup;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.LocaleManager;
import com.mishiranu.dashchan.content.async.HttpHolderTask;
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.ui.ContentFragment;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.CommentTextView;
import com.mishiranu.dashchan.widget.ThemeEngine;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextFragment extends ContentFragment implements View.OnClickListener {
	private static final String EXTRA_TYPE = "type";

	private static final String EXTRA_ERROR_ITEM = "errorItem";
	private static final String EXTRA_TEXT = "text";

	public enum Type {
		LICENSES(markup -> {
			markup.addTag("h1", ChanMarkup.TAG_HEADING);
			markup.addTag("pre", ChanMarkup.TAG_CODE);
		}),
		CHANGELOG(markup -> {
			markup.addTag("h4", ChanMarkup.TAG_HEADING);
			markup.addTag("em", ChanMarkup.TAG_ITALIC);
		});

		private final ChanMarkup.MarkupBuilder builder;

		Type(ChanMarkup.MarkupBuilder.Constructor constructor) {
			builder = new ChanMarkup.MarkupBuilder(constructor);
		}
	}

	private View contentView;
	private CommentTextView textView;
	private ViewFactory.ErrorHolder errorHolder;
	private View progressView;

	private ErrorItem errorItem;
	private String text;

	public TextFragment() {}

	public TextFragment(Type type) {
		Bundle args = new Bundle();
		args.putString(EXTRA_TYPE, type.name());
		setArguments(args);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		float density = ResourceUtils.obtainDensity(this);
		FrameLayout view = new FrameLayout(requireContext());
		view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		ScrollView scrollView = new ScrollView(view.getContext()) {
			@Override
			public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
				// Don't scroll on select
				return true;
			}
		};
		scrollView.setId(android.R.id.list);
		ThemeEngine.applyStyle(scrollView);
		view.addView(scrollView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
		contentView = scrollView;
		FrameLayout frameLayout = new FrameLayout(scrollView.getContext());
		frameLayout.setOnClickListener(this);
		scrollView.addView(frameLayout, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		textView = new CommentTextView(requireActivity(), null, android.R.attr.textAppearanceLarge);
		int padding = (int) (16f * density);
		textView.setPadding(padding, padding, padding, padding);
		ViewUtils.setTextSizeScaled(textView, 14);
		frameLayout.addView(textView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		errorHolder = ViewFactory.createErrorLayout(view);
		errorHolder.layout.setVisibility(View.GONE);
		view.addView(errorHolder.layout);
		ProgressBar progressBar = new ProgressBar(view.getContext());
		ThemeEngine.applyStyle(progressBar);
		view.addView(progressBar, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
		((FrameLayout.LayoutParams) progressBar.getLayoutParams()).gravity = Gravity.CENTER;
		progressBar.setVisibility(View.GONE);
		progressView = progressBar;
		return view;
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
				errorItem = savedInstanceState != null ? savedInstanceState.getParcelable(EXTRA_ERROR_ITEM) : null;
				text = savedInstanceState != null ? savedInstanceState.getString(EXTRA_TEXT) : null;
				if (errorItem != null) {
					contentView.setVisibility(View.GONE);
					errorHolder.layout.setVisibility(View.VISIBLE);
					errorHolder.text.setText(errorItem.toString());
				} else if (text != null) {
					setText(text);
				} else {
					contentView.setVisibility(View.GONE);
					progressView.setVisibility(View.VISIBLE);
					ChangelogViewModel viewModel = new ViewModelProvider(this).get(ChangelogViewModel.class);
					if (!viewModel.hasTaskOrValue()) {
						ReadChangelogTask task = new ReadChangelogTask(requireContext(), viewModel);
						task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
						viewModel.attach(task);
					}
					viewModel.observe(getViewLifecycleOwner(), result -> {
						progressView.setVisibility(View.GONE);
						if (result.second != null) {
							text = result.second;
							contentView.setVisibility(View.VISIBLE);
							setText(text);
						} else {
							errorItem = result.first != null ? result.first : new ErrorItem(ErrorItem.Type.UNKNOWN);
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
				outState.putString(EXTRA_TEXT, text);
				break;
			}
		}
	}

	private void setText(String text) {
		Type type = Type.valueOf(requireArguments().getString(EXTRA_TYPE));
		CharSequence spanned = type.builder.fromHtmlReduced(text);
		ThemeEngine.getColorScheme(requireContext()).apply(spanned);
		textView.setText(spanned);
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

	public static class ChangelogViewModel extends TaskViewModel<ReadChangelogTask, Pair<ErrorItem, String>> {}

	private static class ReadChangelogTask extends HttpHolderTask<Void, Pair<ErrorItem, String>> {
		private static final Pattern PATTERN_TITLE = Pattern.compile("<h1.*?>Changelog (.*)</h1>");

		private final ChangelogViewModel viewModel;
		private final Configuration configuration;

		public ReadChangelogTask(Context context, ChangelogViewModel viewModel) {
			super(Chan.getFallback());
			this.viewModel = viewModel;
			configuration = context.getResources().getConfiguration();
		}

		private static String downloadChangelog(HttpHolder holder, String suffix) throws HttpException {
			Uri uri = Chan.getFallback().locator.buildPathWithHost("github.com",
					"Mishiranu", "Dashchan", "wiki", "Changelog-" + suffix);
			String response = new HttpRequest(uri, holder).setSuccessOnly(false).perform().readString();
			Matcher matcher = PATTERN_TITLE.matcher(StringUtils.emptyIfNull(response));
			if (matcher.find()) {
				String titleSuffix = matcher.group(1);
				if (titleSuffix.replace(' ', '-').toLowerCase(Locale.US).equals(suffix.toLowerCase(Locale.US))) {
					return response;
				}
			}
			return null;
		}

		@Override
		public Pair<ErrorItem, String> run(HttpHolder holder) {
			try {
				String result = null;
				for (Locale locale : LocaleManager.getInstance().getLocales(configuration)) {
					String language = locale.getLanguage();
					String country = locale.getCountry();
					if (!StringUtils.isEmpty(country)) {
						result = downloadChangelog(holder, language.toUpperCase(Locale.US) +
								"-" + country.toUpperCase(Locale.US));
						if (result != null) {
							break;
						}
					}
					result = downloadChangelog(holder, language.toUpperCase(Locale.US));
					if (result != null) {
						break;
					}
				}
				if (result == null) {
					result = downloadChangelog(holder, Locale.US.getLanguage().toUpperCase(Locale.US));
				}
				if (result != null) {
					result = ChangelogGroupCallback.parse(result);
				}
				if (result == null) {
					return new Pair<>(new ErrorItem(ErrorItem.Type.UNKNOWN), null);
				} else {
					return new Pair<>(null, result);
				}
			} catch (HttpException e) {
				return new Pair<>(e.getErrorItemAndHandle(), null);
			}
		}

		@Override
		protected void onComplete(Pair<ErrorItem, String> result) {
			viewModel.handleResult(result);
		}
	}

	private static class ChangelogGroupCallback implements GroupParser.Callback {
		private String result;

		public static String parse(String source) {
			ChangelogGroupCallback callback = new ChangelogGroupCallback();
			try {
				GroupParser.parse(source, callback);
			} catch (ParseException e) {
				if (StringUtils.isEmpty(callback.result)) {
					Log.persistent().stack(e);
				}
			}
			return callback.result;
		}

		@Override
		public boolean onStartElement(GroupParser parser, String tagName, GroupParser.Attributes attributes) {
			return "div".equals(tagName) && "markdown-body".equals(attributes.get("class"));
		}

		@Deprecated
		@Override
		public boolean onStartElement(GroupParser parser, String tagName, String attrs) {
			throw new IllegalStateException();
		}

		@Override
		public void onEndElement(GroupParser parser, String tagName) {}

		@Override
		public void onText(GroupParser parser, CharSequence text) {}

		@Deprecated
		@Override
		public void onText(GroupParser parser, String source, int start, int end) {
			throw new IllegalStateException();
		}

		@Override
		public void onGroupComplete(GroupParser parser, String text) throws ParseException {
			result = text;
			// Cancel parsing
			throw new ParseException();
		}
	}
}
