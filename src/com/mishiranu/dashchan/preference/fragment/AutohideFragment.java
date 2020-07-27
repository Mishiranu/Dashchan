package com.mishiranu.dashchan.preference.fragment;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.storage.AutohideStorage;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.preference.PreferencesActivity;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.CustomSearchView;
import com.mishiranu.dashchan.widget.ErrorEditTextSetter;
import com.mishiranu.dashchan.widget.MenuExpandListener;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class AutohideFragment extends BaseListFragment implements PreferencesActivity.ActivityHandler {
	private final Adapter adapter = new Adapter();
	private final ArrayList<AutohideStorage.AutohideItem> items = new ArrayList<>();

	private CustomSearchView searchView;
	private MenuItem searchMenuItem;
	private boolean searchExpanded = false;

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		searchView = new CustomSearchView(C.API_LOLLIPOP ? new ContextThemeWrapper(requireContext(),
				R.style.Theme_Special_White) : requireActivity().getActionBar().getThemedContext());
		searchView.setHint(getString(R.string.action_filter));
		searchView.setOnChangeListener(adapter::setSearchQuery);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		setHasOptionsMenu(true);
		requireActivity().setTitle(R.string.preference_header_autohide);
		requireActivity().getActionBar().setSubtitle(null);
		setEmptyText(R.string.message_no_rules);
		items.addAll(AutohideStorage.getInstance().getItems());
		setListAdapter(adapter);
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		searchView = null;
		searchMenuItem = null;
	}

	@Override
	public boolean onBackPressed() {
		if (searchMenuItem != null && searchMenuItem.isActionViewExpanded()) {
			searchMenuItem.collapseActionView();
			return true;
		}
		return false;
	}

	private static final int OPTIONS_MENU_NEW_RULE = 0;
	private static final int OPTIONS_MENU_SEARCH = 1;

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		ActionIconSet set = new ActionIconSet(requireContext());
		menu.add(0, OPTIONS_MENU_NEW_RULE, 0, R.string.action_new_rule).setIcon(set.getId(R.attr.actionAddRule))
				.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		searchMenuItem = menu.add(0, OPTIONS_MENU_SEARCH, 0, R.string.action_filter).setActionView(searchView)
				.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
				.setOnActionExpandListener(new MenuExpandListener((menuItem, expand) -> {
					adapter.setSearchQuery("");
					searchExpanded = expand;
					onPrepareOptionsMenu(menu);
					setEmptyText(expand ? "" : getString(R.string.message_no_rules));
					return true;
				}));
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public void onPrepareOptionsMenu(@NonNull Menu menu) {
		menu.findItem(OPTIONS_MENU_NEW_RULE).setVisible(!searchExpanded);
		super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case OPTIONS_MENU_NEW_RULE: {
				editRule(null, -1);
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}

	private static final int CONTEXT_MENU_REMOVE_RULE = 0;

	@Override
	public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(Menu.NONE, CONTEXT_MENU_REMOVE_RULE, 0, R.string.action_remove_rule);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId()) {
			case CONTEXT_MENU_REMOVE_RULE: {
				int itemIndex = items.indexOf(adapter.getItem(menuInfo.position));
				AutohideStorage.getInstance().delete(itemIndex);
				items.remove(itemIndex);
				adapter.notifyDataSetChanged();
				break;
			}
		}
		return super.onContextItemSelected(item);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		editRule(adapter.getItem(position), position);
	}

	private void editRule(AutohideStorage.AutohideItem autohideItem, int index) {
		AutohideDialog dialog = new AutohideDialog(autohideItem, index);
		dialog.setTargetFragment(this, 0);
		dialog.show(getParentFragmentManager(), AutohideDialog.class.getName());
	}

	private void onEditComplete(AutohideStorage.AutohideItem autohideItem, int index) {
		if (index == -1) {
			// Also will set id to item
			AutohideStorage.getInstance().add(autohideItem);
			items.add(autohideItem);
			adapter.notifyDataSetChanged();
		} else if (index >= 0) {
			int itemIndex = items.indexOf(adapter.getItem(index));
			AutohideStorage.getInstance().update(itemIndex, autohideItem);
			items.set(itemIndex, autohideItem);
			adapter.notifyDataSetChanged();
		}
	}

	private class Adapter extends BaseAdapter {
		private final ArrayList<AutohideStorage.AutohideItem> filteredItems = new ArrayList<>();
		private String searchQuery = "";

		public void setSearchQuery(String searchQuery) {
			filteredItems.clear();
			this.searchQuery = searchQuery;
			if (!StringUtils.isEmpty(searchQuery)) {
				Locale locale = Locale.getDefault();
				for (AutohideStorage.AutohideItem item : items) {
					if (!StringUtils.isEmpty(item.value) &&
							item.value.toLowerCase(locale).contains(searchQuery.toLowerCase(locale)) ||
							item.find(searchQuery) != null) {
						filteredItems.add(item);
					}
				}
			}
			notifyDataSetChanged();
		}

		@Override
		public AutohideStorage.AutohideItem getItem(int position) {
			return !StringUtils.isEmpty(searchQuery) ? filteredItems.get(position) : items.get(position);
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public int getCount() {
			return !StringUtils.isEmpty(searchQuery) ? filteredItems.size() : items.size();
		}

		@SuppressWarnings("UnusedAssignment")
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = ViewFactory.makeTwoLinesListItem(parent, true);
			}
			ViewFactory.TwoLinesViewHolder holder = (ViewFactory.TwoLinesViewHolder) convertView.getTag();
			AutohideStorage.AutohideItem autohideItem = getItem(position);
			holder.text1.setText(StringUtils.isEmpty(autohideItem.value)
					? getString(R.string.text_all_posts) : autohideItem.value);
			StringBuilder builder = new StringBuilder();
			boolean and = false;
			if (!StringUtils.isEmpty(autohideItem.boardName) || autohideItem.optionOriginalPost
					|| autohideItem.optionSage) {
				if (!StringUtils.isEmpty(autohideItem.boardName)) {
					if (and) {
						builder.append(" & ");
					}
					builder.append('[').append(autohideItem.boardName).append(']');
					if (!StringUtils.isEmpty(autohideItem.threadNumber)) {
						builder.append(" & ").append(autohideItem.threadNumber);
					}
					and = true;
				}
				if (autohideItem.optionOriginalPost) {
					if (and) {
						builder.append(" & ");
					}
					builder.append("op");
					and = true;
				}
				if (autohideItem.optionSage) {
					if (and) {
						builder.append(" & ");
					}
					builder.append("sage");
					and = true;
				}
			}
			int orCount = 0;
			if (autohideItem.optionSubject) {
				orCount++;
			}
			if (autohideItem.optionComment) {
				orCount++;
			}
			if (autohideItem.optionName) {
				orCount++;
			}
			if (orCount > 0) {
				if (and) {
					builder.append(" & ");
					if (orCount > 1) {
						builder.append('(');
					}
				}
				boolean or = false;
				if (autohideItem.optionSubject) {
					builder.append("subject");
					or = true;
				}
				if (autohideItem.optionComment) {
					if (or) {
						builder.append(" | ");
					}
					builder.append("comment");
					or = true;
				}
				if (autohideItem.optionName) {
					if (or) {
						builder.append(" | ");
					}
					builder.append("name");
					or = true;
				}
				if (and && orCount > 1) {
					builder.append(')');
				}
			} else {
				if (and) {
					builder.append(" & ");
				}
				builder.append("false");
			}
			holder.text2.setText(builder);
			return convertView;
		}
	}

	public static class AutohideDialog extends DialogFragment implements View.OnClickListener,
			DialogInterface.OnClickListener {
		private static final String EXTRA_ITEM = "item";
		private static final String EXTRA_INDEX = "index";

		private final HashSet<String> selectedChanNames = new HashSet<>();

		private ScrollView scrollView;
		private TextView chanNameSelector;
		private EditText boardNameEdit;
		private EditText threadNumberEdit;
		private CheckBox autohideOriginalPost;
		private CheckBox autohideSage;
		private CheckBox autohideSubject;
		private CheckBox autohideComment;
		private CheckBox autohideName;
		private EditText valueEdit;
		private TextView errorText;
		private TextView matcherText;
		private EditText testStringEdit;

		public AutohideDialog() {}

		public AutohideDialog(AutohideStorage.AutohideItem autohideItem, int index) {
			Bundle args = new Bundle();
			args.putParcelable(EXTRA_ITEM, autohideItem);
			args.putInt(EXTRA_INDEX, index);
			setArguments(args);
		}

		@SuppressLint("InflateParams")
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			ScrollView view = (ScrollView) LayoutInflater.from(requireContext())
					.inflate(R.layout.dialog_autohide, null);
			scrollView = view;
			chanNameSelector = view.findViewById(R.id.chan_name);
			boardNameEdit = view.findViewById(R.id.board_name);
			threadNumberEdit = view.findViewById(R.id.thread_number);
			autohideOriginalPost = view.findViewById(R.id.autohide_original_post);
			autohideSage = view.findViewById(R.id.autohide_sage);
			autohideSubject = view.findViewById(R.id.autohide_subject);
			autohideComment = view.findViewById(R.id.autohide_comment);
			autohideName = view.findViewById(R.id.autohide_name);
			valueEdit = view.findViewById(R.id.value);
			errorText = view.findViewById(R.id.error_text);
			matcherText = view.findViewById(R.id.matcher_result);
			testStringEdit = view.findViewById(R.id.test_string);
			valueEdit.addTextChangedListener(valueListener);
			testStringEdit.addTextChangedListener(testStringListener);
			chanNameSelector.setOnClickListener(this);
			Collection<String> chanNames = ChanManager.getInstance().getAvailableChanNames();
			if (chanNames.size() <= 1) {
				chanNameSelector.setVisibility(View.GONE);
			}
			AutohideStorage.AutohideItem autohideItem = null;
			if (savedInstanceState != null) {
				autohideItem = savedInstanceState.getParcelable(EXTRA_ITEM);
			}
			if (autohideItem == null) {
				autohideItem = requireArguments().getParcelable(EXTRA_ITEM);
			}
			if (autohideItem != null) {
				if (autohideItem.chanNames != null) {
					selectedChanNames.addAll(autohideItem.chanNames);
				}
				updateSelectedText();
				boardNameEdit.setText(autohideItem.boardName);
				threadNumberEdit.setText(autohideItem.threadNumber);
				autohideOriginalPost.setChecked(autohideItem.optionOriginalPost);
				autohideSage.setChecked(autohideItem.optionSage);
				autohideSubject.setChecked(autohideItem.optionSubject);
				autohideComment.setChecked(autohideItem.optionComment);
				autohideName.setChecked(autohideItem.optionName);
				valueEdit.setText(autohideItem.value);
			} else {
				chanNameSelector.setText(R.string.text_all_forums);
				boardNameEdit.setText(null);
				threadNumberEdit.setText(null);
				autohideOriginalPost.setChecked(false);
				autohideSage.setChecked(false);
				autohideSubject.setChecked(true);
				autohideComment.setChecked(true);
				autohideName.setChecked(true);
				valueEdit.setText(null);
			}
			updateTestResult();
		}

		@Override
		public void onSaveInstanceState(@NonNull Bundle outState) {
			super.onSaveInstanceState(outState);
			outState.putParcelable(EXTRA_ITEM, readDialogView());
		}

		@NonNull
		@Override
		public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			AlertDialog dialog = new AlertDialog.Builder(requireContext())
					.setView(scrollView).setPositiveButton(R.string.action_save, this)
					.setNegativeButton(android.R.string.cancel, null)
					.create();
			dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
			return dialog;
		}

		@Override
		public void onClick(View v) {
			MultipleChanDialog dialog = new MultipleChanDialog(new ArrayList<>(selectedChanNames));
			dialog.setTargetFragment(this, 0);
			dialog.show(getParentFragmentManager(), MultipleChanDialog.class.getName());
		}

		private void updateSelectedText() {
			String chanNameText;
			int size = selectedChanNames.size();
			if (size == 0) {
				chanNameText = getString(R.string.text_all_forums);
			} else if (size > 1) {
				chanNameText = getString(R.string.text_several_forums);
			} else {
				String chanName = selectedChanNames.iterator().next();
				ChanConfiguration configuration = ChanConfiguration.get(chanName);
				String title = configuration != null ? configuration.getTitle() : chanName;
				chanNameText = getString(R.string.text_only_forum_format, title);
			}
			chanNameSelector.setText(chanNameText);
		}

		private AutohideStorage.AutohideItem readDialogView() {
			String boardName = boardNameEdit.getText().toString();
			String threadNumber = threadNumberEdit.getText().toString();
			boolean optionOriginalPost = autohideOriginalPost.isChecked();
			boolean optionSage = autohideSage.isChecked();
			boolean optionSubject = autohideSubject.isChecked();
			boolean optionComment = autohideComment.isChecked();
			boolean optionName = autohideName.isChecked();
			String value = valueEdit.getText().toString();
			return new AutohideStorage.AutohideItem(selectedChanNames.size() > 0 ? selectedChanNames : null,
					boardName, threadNumber, optionOriginalPost, optionSage,
					optionSubject, optionComment, optionName, value);
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			((AutohideFragment) getTargetFragment()).onEditComplete(readDialogView(),
					requireArguments().getInt(EXTRA_INDEX));
		}

		private void onChansSelected(ArrayList<String> selected) {
			selectedChanNames.clear();
			selectedChanNames.addAll(selected);
			updateSelectedText();
		}

		private BackgroundColorSpan errorSpan;
		private ErrorEditTextSetter errorValueSetter;

		private void updateError(int index, String text) {
			boolean error = index >= 0;
			Editable value = valueEdit.getEditableText();
			if (error) {
				if (errorSpan == null) {
					errorSpan = new BackgroundColorSpan(ResourceUtils.getColor(requireContext(),
							R.attr.colorTextError));
				} else {
					value.removeSpan(errorSpan);
				}
				if (index > 0) {
					value.setSpan(errorSpan, index - 1, index, Editable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
			} else if (errorSpan != null) {
				value.removeSpan(errorSpan);
			}
			if (C.API_LOLLIPOP) {
				if (errorValueSetter == null) {
					errorValueSetter = new ErrorEditTextSetter(valueEdit);
				}
				errorValueSetter.setError(error);
			}
			if (StringUtils.isEmpty(text)) {
				errorText.setVisibility(View.GONE);
			} else {
				scrollView.post(() -> {
					int position = errorText.getBottom() - scrollView.getHeight();
					if (scrollView.getScrollY() < position) {
						int limit = Integer.MAX_VALUE;
						int start = valueEdit.getSelectionStart();
						if (start >= 0) {
							Layout layout = valueEdit.getLayout();
							limit = layout.getLineTop(layout.getLineForOffset(start)) + valueEdit.getTop();
						}
						if (limit > position) {
							scrollView.smoothScrollTo(0, position);
						}
					}
				});
				errorText.setVisibility(View.VISIBLE);
				errorText.setText(text);
			}
		}

		private Pattern workPattern;

		private void updateTestResult() {
			String matchedText = null;
			if (workPattern != null) {
				Matcher matcher = workPattern.matcher(testStringEdit.getText().toString());
				if (matcher.find()) {
					matchedText = StringUtils.emptyIfNull(matcher.group());
				}
			}
			if (matchedText != null) {
				if (StringUtils.isEmptyOrWhitespace(matchedText)) {
					matcherText.setText(R.string.text_match_found);
				} else {
					matcherText.setText(getString(R.string.text_match_found_format, matchedText));
				}
			} else {
				matcherText.setText(R.string.text_no_matches_found);
			}
		}

		private final TextWatcher valueListener = new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}

			@Override
			public void afterTextChanged(Editable s) {
				// Remove line breaks
				for (int i = 0; i < s.length(); i++) {
					char c = s.charAt(i);
					// Replacing or deleting will call this callback again
					if (c == '\n') {
						s.replace(i, i + 1, " ");
						return;
					} else if (c == '\r') {
						s.delete(i, i + 1);
						return;
					}
				}
				Pattern pattern = null;
				try {
					pattern = AutohideStorage.AutohideItem.makePattern(s.toString());
					updateError(-1, null);
				} catch (PatternSyntaxException e) {
					updateError(e.getIndex(), e.getDescription());
				}
				workPattern = pattern;
				updateTestResult();
			}
		};

		private final TextWatcher testStringListener = new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				updateTestResult();
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void afterTextChanged(Editable s) {}
		};
	}

	public static class MultipleChanDialog extends DialogFragment implements DialogInterface.OnMultiChoiceClickListener,
			DialogInterface.OnClickListener {
		private static final String EXTRA_SELECTED = "selected";
		private static final String EXTRA_CHECKED = "checked";

		public MultipleChanDialog() {}

		public MultipleChanDialog(ArrayList<String> selected) {
			Bundle args = new Bundle();
			args.putStringArrayList(EXTRA_SELECTED, selected);
			setArguments(args);
		}

		private List<String> chanNames;
		private boolean[] checkedItems;

		@NonNull
		@Override
		public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			Collection<String> chanNames = ChanManager.getInstance().getAvailableChanNames();
			this.chanNames = new ArrayList<>(chanNames);
			String[] items = new String[this.chanNames.size()];
			for (int i = 0; i < this.chanNames.size(); i++) {
				items[i] = ChanConfiguration.get(this.chanNames.get(i)).getTitle();
			}
			boolean[] checkedItems = savedInstanceState != null ? savedInstanceState
					.getBooleanArray(EXTRA_CHECKED) : null;
			// size != length means some chans were added or deleted while configuration was changing (very rare case)
			if (checkedItems == null || this.chanNames.size() != checkedItems.length) {
				ArrayList<String> selected = requireArguments().getStringArrayList(EXTRA_SELECTED);
				checkedItems = new boolean[items.length];
				for (int i = 0; i < this.chanNames.size(); i++) {
					checkedItems[i] = selected.contains(this.chanNames.get(i));
				}
			}
			this.checkedItems = checkedItems;
			return new AlertDialog.Builder(requireContext())
					.setMultiChoiceItems(items, checkedItems, this)
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(android.R.string.ok, this)
					.create();
		}

		@Override
		public void onSaveInstanceState(@NonNull Bundle outState) {
			super.onSaveInstanceState(outState);
			outState.putBooleanArray(EXTRA_CHECKED, checkedItems);
		}

		@Override
		public void onClick(DialogInterface dialog, int which, boolean isChecked) {
			checkedItems[which] = isChecked;
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			ArrayList<String> selected = new ArrayList<>();
			for (int i = 0; i < chanNames.size(); i++) {
				if (checkedItems[i]) {
					selected.add(chanNames.get(i));
				}
			}
			((AutohideDialog) getTargetFragment()).onChansSelected(selected);
		}
	}
}
