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
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
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
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.CommentTextView;
import com.mishiranu.dashchan.widget.ThemeEngine;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextFragment extends Fragment implements View.OnClickListener {
	private static final String EXTRA_TYPE = "type";

	private static final String EXTRA_ERROR_ITEM = "errorItem";
	private static final String EXTRA_TEXT = "text";

	public enum Type {LICENSES, CHANGELOG}

	private View contentView;
	private CommentTextView textView;
	private View emptyView;
	private TextView emptyText;
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
		inflater.inflate(R.layout.widget_error, view);
		emptyView = view.findViewById(R.id.error);
		emptyText = view.findViewById(R.id.error_text);
		emptyView.setVisibility(View.GONE);
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
		emptyView = null;
		emptyText = null;
		progressView = null;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		switch (Type.valueOf(requireArguments().getString(EXTRA_TYPE))) {
			case LICENSES: {
				((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.foss_licenses), null);
				setText(IOUtils.readRawResourceString(getResources(), R.raw.licenses));
				break;
			}
			case CHANGELOG: {
				((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.changelog), null);
				errorItem = savedInstanceState != null ? savedInstanceState.getParcelable(EXTRA_ERROR_ITEM) : null;
				text = savedInstanceState != null ? savedInstanceState.getString(EXTRA_TEXT) : null;
				if (errorItem != null) {
					contentView.setVisibility(View.GONE);
					emptyView.setVisibility(View.VISIBLE);
					emptyText.setText(errorItem.toString());
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
							emptyView.setVisibility(View.VISIBLE);
							emptyText.setText(errorItem.toString());
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
		CharSequence spanned = HtmlParser.spanify(text, MARKUP.getMarkup(), null, null, null);
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

	private static final ChanMarkup MARKUP = new ChanMarkup(Chan.getFallback()) {{
		addTag("h1", TAG_HEADING);
		addTag("h2", TAG_HEADING);
		addTag("h3", TAG_HEADING);
		addTag("h4", TAG_HEADING);
		addTag("h5", TAG_HEADING);
		addTag("h6", TAG_HEADING);
		addTag("strong", TAG_BOLD);
		addTag("em", TAG_ITALIC);
		addTag("pre", TAG_CODE);
	}};

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
