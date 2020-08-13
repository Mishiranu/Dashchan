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
import com.mishiranu.dashchan.ui.preference.core.PreferenceFragment;

public class InterfaceFragment extends PreferenceFragment {
	@Override
	protected SharedPreferences getPreferences() {
		return Preferences.PREFERENCES;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		addSeek(Preferences.KEY_TEXT_SCALE, Preferences.DEFAULT_TEXT_SCALE,
				R.string.preference_text_scale, R.string.preference_text_scale_summary_format, 75, 200, 5, 1f);
		addSeek(Preferences.KEY_THUMBNAILS_SCALE, Preferences.DEFAULT_THUMBNAILS_SCALE,
				R.string.preference_thumbnails_scale, R.string.preference_thumbnails_scale_summary_format,
				100, 200, 10, 1f);
		addCheck(true, Preferences.KEY_CUT_THUMBNAILS, Preferences.DEFAULT_CUT_THUMBNAILS,
				R.string.preference_cut_thumbnails, R.string.preference_cut_thumbnails_summary);
		addCheck(true, Preferences.KEY_ACTIVE_SCROLLBAR, Preferences.DEFAULT_ACTIVE_SCROLLBAR,
				R.string.preference_active_scrollbar, 0);
		addCheck(true, Preferences.KEY_SCROLL_THREAD_GALLERY, Preferences.DEFAULT_SCROLL_THREAD_GALLERY,
				R.string.preference_scroll_thread_gallery, 0);

		addHeader(R.string.preference_category_navigation_drawer);
		addList(Preferences.KEY_PAGES_LIST, Preferences.VALUES_PAGES_LIST,
				Preferences.DEFAULT_PAGES_LIST, R.string.preference_pages_list, R.array.preference_pages_list_choices);
		addList(Preferences.KEY_DRAWER_INITIAL_POSITION,
				Preferences.VALUES_DRAWER_INITIAL_POSITION, Preferences.DEFAULT_DRAWER_INITIAL_POSITION,
				R.string.preference_drawer_initial_position, R.array.preference_drawer_initial_position_choices);

		addHeader(R.string.preference_category_threads);
		addCheck(true, Preferences.KEY_PAGE_BY_PAGE, Preferences.DEFAULT_PAGE_BY_PAGE,
				R.string.preference_page_by_page, R.string.preference_page_by_page_summary);
		addCheck(true, Preferences.KEY_DISPLAY_HIDDEN_THREADS,
				Preferences.DEFAULT_DISPLAY_HIDDEN_THREADS, R.string.preference_display_hidden_threads, 0);

		addHeader(R.string.preference_category_posts);
		addEdit(Preferences.KEY_POST_MAX_LINES, Preferences.DEFAULT_POST_MAX_LINES,
				R.string.preference_post_max_lines, R.string.preference_post_max_lines_summary, null,
				InputType.TYPE_CLASS_NUMBER)
				.setOnAfterChangeListener(p -> requireActivity().recreate());
		addCheck(true, Preferences.KEY_ALL_ATTACHMENTS, Preferences.DEFAULT_ALL_ATTACHMENTS,
				R.string.preference_all_attachments, R.string.preference_all_attachments_summary);
		addList(Preferences.KEY_HIGHLIGHT_UNREAD, Preferences.VALUES_HIGHLIGHT_UNREAD,
				Preferences.DEFAULT_HIGHLIGHT_UNREAD, R.string.preference_highlight_unread,
				R.array.preference_highlight_unread_choices);
		addCheck(true, Preferences.KEY_ADVANCED_SEARCH,
				Preferences.DEFAULT_ADVANCED_SEARCH, R.string.preference_advanced_search,
				R.string.preference_advanced_search_summary)
				.setOnAfterChangeListener(p -> {
					if (p.getValue()) {
						SpannableStringBuilder builder = new SpannableStringBuilder
								(getText(R.string.preference_advanced_search_message));
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
				R.string.preference_display_icons, R.string.preference_display_icons_summary);

		addHeader(R.string.preference_category_submission);
		addCheck(true, Preferences.KEY_HIDE_PERSONAL_DATA,
				Preferences.DEFAULT_HIDE_PERSONAL_DATA, R.string.preference_hide_personal_data, 0);
		addCheck(true, Preferences.KEY_HUGE_CAPTCHA, Preferences.DEFAULT_HUGE_CAPTCHA,
				R.string.preference_huge_captcha, 0);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		requireActivity().setTitle(R.string.preference_header_interface);
		requireActivity().getActionBar().setSubtitle(null);
	}
}
