package com.mishiranu.dashchan.ui.preference;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.Chan;
import chan.content.ChanManager;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.storage.AutohideStorage;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.CustomSearchView;
import com.mishiranu.dashchan.widget.ErrorEditTextSetter;
import com.mishiranu.dashchan.widget.MenuExpandListener;
import com.mishiranu.dashchan.widget.SimpleViewHolder;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class AutohideFragment extends BaseListFragment {
	private static final String EXTRA_SEARCH_QUERY = "searchQuery";
	private static final String EXTRA_SEARCH_FOCUSED = "searchFocused";

	private final ArrayList<AutohideStorage.AutohideItem> items = new ArrayList<>();

	private CustomSearchView searchView;
	private MenuItem searchMenuItem;

	private String searchQuery;
	private boolean searchFocused;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		searchQuery = savedInstanceState != null ? savedInstanceState.getString(EXTRA_SEARCH_QUERY) : null;
		searchFocused = savedInstanceState != null && savedInstanceState.getBoolean(EXTRA_SEARCH_FOCUSED);
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		searchView = getViewHolder().obtainSearchView();
		searchView.setHint(getString(R.string.filter));
		searchView.setOnChangeListener(query -> {
			((Adapter) getRecyclerView().getAdapter()).setSearchQuery(query);
			if (searchQuery != null) {
				searchQuery = query;
			}
		});
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		setHasOptionsMenu(true);
		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.autohide), null);
		items.addAll(AutohideStorage.getInstance().getItems());
		if (items.isEmpty()) {
			setErrorText(getString(R.string.no_rules_defined));
		}
		getRecyclerView().setAdapter(new Adapter());
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		searchView = null;
		searchMenuItem = null;
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		if (searchView != null) {
			searchFocused = searchView.isSearchFocused();
		}
		outState.putString(EXTRA_SEARCH_QUERY, searchQuery);
		outState.putBoolean(EXTRA_SEARCH_FOCUSED, searchFocused);
	}

	@Override
	public void onTerminate() {
		if (searchMenuItem != null && searchMenuItem.isActionViewExpanded()) {
			searchMenuItem.setOnActionExpandListener(null);
			searchMenuItem.collapseActionView();
		}
	}

	@Override
	public boolean onBackPressed() {
		if (searchMenuItem != null && searchMenuItem.isActionViewExpanded()) {
			searchMenuItem.collapseActionView();
			return true;
		}
		return false;
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		menu.add(0, R.id.menu_new_rule, 0, R.string.new_rule)
				.setIcon(((FragmentHandler) requireActivity()).getActionBarIcon(R.attr.iconActionAddRule))
				.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		searchMenuItem = menu.add(0, R.id.menu_search, 0, R.string.filter).setActionView(searchView)
				.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
				.setOnActionExpandListener(new MenuExpandListener((menuItem, expand) -> {
					if (expand) {
						searchView.setFocusOnExpand(searchFocused);
						if (searchQuery != null) {
							searchView.setQuery(searchQuery);
						} else {
							searchQuery = "";
						}
					} else {
						searchQuery = null;
					}
					((Adapter) getRecyclerView().getAdapter()).setSearchQuery(searchQuery);
					onPrepareOptionsMenu(menu);
					return true;
				}));
		if (searchQuery != null) {
			searchMenuItem.expandActionView();
		}
	}

	@Override
	public void onPrepareOptionsMenu(@NonNull Menu menu) {
		menu.findItem(R.id.menu_new_rule).setVisible(searchQuery == null);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_new_rule: {
				editRule(null, -1);
				return true;
			}
			case R.id.menu_search: {
				searchFocused = true;
				return false;
			}
		}
		return super.onOptionsItemSelected(item);
	}

	private void editRule(AutohideStorage.AutohideItem autohideItem, int index) {
		AutohideDialog dialog = new AutohideDialog(autohideItem, index);
		dialog.show(getChildFragmentManager(), AutohideDialog.class.getName());
	}

	private void onEditComplete(AutohideStorage.AutohideItem autohideItem, int index) {
		Adapter adapter = (Adapter) getRecyclerView().getAdapter();
		if (index == -1) {
			AutohideStorage.getInstance().add(autohideItem);
			items.add(autohideItem);
			setErrorText(null);
		} else if (index >= 0) {
			AutohideStorage.getInstance().update(index, autohideItem);
			items.set(index, autohideItem);
		}
		adapter.invalidate();
	}

	private void onDelete(int index) {
		AutohideStorage.getInstance().delete(index);
		items.remove(index);
		((Adapter) getRecyclerView().getAdapter()).invalidate();
		if (items.isEmpty()) {
			setErrorText(getString(R.string.no_rules_defined));
		}
	}

	private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
			implements ListViewUtils.ClickCallback<Void, RecyclerView.ViewHolder> {
		private final ArrayList<AutohideStorage.AutohideItem> filteredItems = new ArrayList<>();
		private String searchQuery;

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

		public void invalidate() {
			setSearchQuery(searchQuery);
		}

		public AutohideStorage.AutohideItem getItem(int position) {
			return !StringUtils.isEmpty(searchQuery) ? filteredItems.get(position) : items.get(position);
		}

		@Override
		public int getItemCount() {
			return !StringUtils.isEmpty(searchQuery) ? filteredItems.size() : items.size();
		}

		@Override
		public boolean onItemClick(RecyclerView.ViewHolder holder, int position, Void nothing, boolean longClick) {
			AutohideStorage.AutohideItem autohideItem = getItem(position);
			editRule(autohideItem, items.indexOf(autohideItem));
			return true;
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return ListViewUtils.bind(new SimpleViewHolder(ViewFactory.makeTwoLinesListItem(parent,
					ViewFactory.FEATURE_SINGLE_LINE).view), false, null, this);
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
			ViewFactory.TwoLinesViewHolder viewHolder = (ViewFactory.TwoLinesViewHolder) holder.itemView.getTag();
			AutohideStorage.AutohideItem autohideItem = getItem(position);
			viewHolder.text1.setText(StringUtils.isEmpty(autohideItem.value)
					? getString(R.string.all_posts) : autohideItem.value);
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
			if (autohideItem.optionFileName) {
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
				}
				if (autohideItem.optionFileName) {
					if (or) {
						builder.append(" | ");
					}
					builder.append("file");
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
			viewHolder.text2.setText(builder);
		}
	}

	public static class AutohideDialog extends DialogFragment implements ChanMultiChoiceDialog.Callback {
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
		private CheckBox autohideFileName;
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
			autohideFileName = view.findViewById(R.id.autohide_file_name);
			valueEdit = view.findViewById(R.id.value);
			errorText = view.findViewById(R.id.error_text);
			matcherText = view.findViewById(R.id.matcher_result);
			testStringEdit = view.findViewById(R.id.test_string);
			valueEdit.addTextChangedListener(valueListener);
			testStringEdit.addTextChangedListener(testStringListener);
			chanNameSelector.setOnClickListener(v -> new ChanMultiChoiceDialog(selectedChanNames).show(this));
			if (C.API_LOLLIPOP) {
				chanNameSelector.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
			}
			if (!ChanManager.getInstance().hasMultipleAvailableChans()) {
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
				autohideFileName.setChecked(autohideItem.optionFileName);
				valueEdit.setText(autohideItem.value);
			} else {
				chanNameSelector.setText(R.string.all_forums);
				boardNameEdit.setText(null);
				threadNumberEdit.setText(null);
				autohideOriginalPost.setChecked(false);
				autohideSage.setChecked(false);
				autohideSubject.setChecked(true);
				autohideComment.setChecked(true);
				autohideName.setChecked(true);
				autohideFileName.setChecked(false);
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
			int index = requireArguments().getInt(EXTRA_INDEX);
			AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
					.setView(scrollView)
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(R.string.save, (d, which) -> ((AutohideFragment) getParentFragment())
							.onEditComplete(readDialogView(), index));
			if (index >= 0) {
				builder.setNeutralButton(R.string.delete,
						(d, which) -> ((AutohideFragment) getParentFragment()).onDelete(index));
			}
			AlertDialog dialog = builder.create();
			dialog.getWindow().setSoftInputMode(C.API_R ? WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
					: ViewUtils.SOFT_INPUT_ADJUST_RESIZE_COMPAT);
			return dialog;
		}

		private void updateSelectedText() {
			String chanNameText;
			int size = selectedChanNames.size();
			if (size == 0) {
				chanNameText = getString(R.string.all_forums);
			} else if (size > 1) {
				chanNameText = getString(R.string.multiple_forums);
			} else {
				String chanName = selectedChanNames.iterator().next();
				Chan chan = Chan.get(chanName);
				String title = chan.name != null ? chan.configuration.getTitle() : chanName;
				chanNameText = getString(R.string.forum_only__format, title);
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
			boolean optionFileName = autohideFileName.isChecked();
			String value = valueEdit.getText().toString();
			return new AutohideStorage.AutohideItem(selectedChanNames.size() > 0 ? selectedChanNames : null,
					boardName, threadNumber, optionOriginalPost, optionSage,
					optionSubject, optionComment, optionName, optionFileName, value);
		}

		@Override
		public void onChansSelected(Collection<String> chanNames) {
			selectedChanNames.clear();
			selectedChanNames.addAll(chanNames);
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
					matcherText.setText(R.string.match_found);
				} else {
					matcherText.setText(ResourceUtils.getColonString(getResources(),
							R.string.match_found, matchedText));
				}
			} else {
				matcherText.setText(R.string.no_matches_found);
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
}
