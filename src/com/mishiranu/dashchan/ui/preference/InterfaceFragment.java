package com.mishiranu.dashchan.ui.preference;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.style.TypefaceSpan;
import android.view.View;
import androidx.annotation.NonNull;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;
import com.mishiranu.dashchan.util.ResourceUtils;

public class InterfaceFragment extends PreferenceFragment {
	@Override
	protected SharedPreferences getPreferences() {
		return Preferences.PREFERENCES;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		String scaleFormat = ResourceUtils.getColonString(getResources(), R.string.scale, "%d%%");
		addSeek(Preferences.KEY_TEXT_SCALE, Preferences.DEFAULT_TEXT_SCALE,
				getString(R.string.text_scale), scaleFormat, 75, 200, 5, 1f);
		addSeek(Preferences.KEY_THUMBNAILS_SCALE, Preferences.DEFAULT_THUMBNAILS_SCALE,
				getString(R.string.thumbnail_scale), scaleFormat, 100, 200, 10, 1f);
		addCheck(true, Preferences.KEY_CUT_THUMBNAILS, Preferences.DEFAULT_CUT_THUMBNAILS,
				R.string.crop_thumbnails, R.string.crop_thumbnails__summary);
		addCheck(true, Preferences.KEY_ACTIVE_SCROLLBAR, Preferences.DEFAULT_ACTIVE_SCROLLBAR,
				R.string.active_scrollbar, 0);
		addCheck(true, Preferences.KEY_SCROLL_THREAD_GALLERY, Preferences.DEFAULT_SCROLL_THREAD_GALLERY,
				R.string.scroll_thread_when_scrolling_gallery, 0);

		addHeader(R.string.navigation_drawer);
		addList(Preferences.KEY_PAGES_LIST, Preferences.VALUES_PAGES_LIST, Preferences.DEFAULT_PAGES_LIST,
				R.string.headers_order, Preferences.ENTRIES_PAGES_LIST);
		addList(Preferences.KEY_DRAWER_INITIAL_POSITION, Preferences.VALUES_DRAWER_INITIAL_POSITION,
				Preferences.DEFAULT_DRAWER_INITIAL_POSITION, R.string.initial_position,
				Preferences.ENTRIES_DRAWER_INITIAL_POSITION);

		addHeader(R.string.threads_list);
		addCheck(true, Preferences.KEY_PAGE_BY_PAGE, Preferences.DEFAULT_PAGE_BY_PAGE,
				R.string.paged_board_navigation, R.string.paged_board_navigation__summary);
		addCheck(true, Preferences.KEY_DISPLAY_HIDDEN_THREADS,
				Preferences.DEFAULT_DISPLAY_HIDDEN_THREADS, R.string.display_hidden_threads, 0);

		addHeader(R.string.posts_list);
		addEdit(Preferences.KEY_POST_MAX_LINES, Preferences.DEFAULT_POST_MAX_LINES,
				R.string.max_lines_count, R.string.max_lines_count__summary, null, InputType.TYPE_CLASS_NUMBER)
				.setOnAfterChangeListener(p -> requireActivity().recreate());
		addCheck(true, Preferences.KEY_ALL_ATTACHMENTS, Preferences.DEFAULT_ALL_ATTACHMENTS,
				R.string.all_attachments, R.string.all_attachments__summary);
		addList(Preferences.KEY_HIGHLIGHT_UNREAD, Preferences.VALUES_HIGHLIGHT_UNREAD,
				Preferences.DEFAULT_HIGHLIGHT_UNREAD, R.string.highlight_unread_posts,
				Preferences.ENTRIES_HIGHLIGHT_UNREAD);
		addCheck(true, Preferences.KEY_ADVANCED_SEARCH, Preferences.DEFAULT_ADVANCED_SEARCH,
				R.string.advanced_search, R.string.advanced_search__summary)
				.setOnAfterChangeListener(p -> {
					if (p.getValue()) {
						SpannableStringBuilder builder = new SpannableStringBuilder
								(getText(R.string.advanced_search__sentence));
						Object[] spans = builder.getSpans(0, builder.length(), Object.class);
						for (Object span : spans) {
							int start = builder.getSpanStart(span);
							int end = builder.getSpanEnd(span);
							int flags = builder.getSpanFlags(span);
							builder.removeSpan(span);
							builder.setSpan(new TypefaceSpan("sans-serif-medium"), start, end, flags);
						}
						MessageDialog.create(this, builder, false);
					}
				});
		addCheck(true, Preferences.KEY_DISPLAY_ICONS, Preferences.DEFAULT_DISPLAY_ICONS,
				R.string.display_post_icons, R.string.display_post_icons__summary);

		addHeader(R.string.submission_form);
		addCheck(true, Preferences.KEY_HIDE_PERSONAL_DATA,
				Preferences.DEFAULT_HIDE_PERSONAL_DATA, R.string.hide_personal_data_block, 0);
		addCheck(true, Preferences.KEY_HUGE_CAPTCHA, Preferences.DEFAULT_HUGE_CAPTCHA,
				R.string.huge_captcha, 0);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.user_interface), null);
	}
}
