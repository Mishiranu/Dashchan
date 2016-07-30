/*
 * Copyright 2014-2016 Fukurou Mishiranu
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.preference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.storage.AutohideStorage;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ErrorEditTextSetter;
import com.mishiranu.dashchan.widget.ViewFactory;

public class AutohideFragment extends BaseListFragment
{
	private ArrayAdapter<AutohideStorage.AutohideItem> mAdapter;
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		setHasOptionsMenu(true);
		setEmptyText(R.string.message_no_rules);
		mAdapter = new ArrayAdapter<AutohideStorage.AutohideItem>(getActivity(), 0)
		{
			@Override
			public View getView(int position, View convertView, ViewGroup parent)
			{
				ViewFactory.TwoLinesViewHolder holder;
				if (convertView == null)
				{
					convertView = ViewFactory.makeTwoLinesListItem(parent, true);
					holder = (ViewFactory.TwoLinesViewHolder) convertView.getTag();
				}
				else holder = (ViewFactory.TwoLinesViewHolder) convertView.getTag();
				AutohideStorage.AutohideItem autohideItem = getItem(position);
				holder.text1.setText(StringUtils.isEmpty(autohideItem.value)
						? getString(R.string.text_all_posts) : autohideItem.value);
				StringBuilder builder = new StringBuilder();
				boolean and = false;
				if (!StringUtils.isEmpty(autohideItem.boardName) || autohideItem.optionOriginalPost
						|| autohideItem.optionSage)
				{
					if (!StringUtils.isEmpty(autohideItem.boardName))
					{
						if (and) builder.append(" & ");
						builder.append('[').append(autohideItem.boardName).append(']');
						if (!StringUtils.isEmpty(autohideItem.threadNumber))
						{
							builder.append(" & ").append(autohideItem.threadNumber);
						}
						and = true;
					}
					if (autohideItem.optionOriginalPost)
					{
						if (and) builder.append(" & ");
						builder.append("op");
						and = true;
					}
					if (autohideItem.optionSage)
					{
						if (and) builder.append(" & ");
						builder.append("sage");
						and = true;
					}
				}
				int orCount = 0;
				if (autohideItem.optionSubject) orCount++;
				if (autohideItem.optionComment) orCount++;
				if (autohideItem.optionName) orCount++;
				if (orCount > 0)
				{
					if (and)
					{
						builder.append(" & ");
						if (orCount > 1) builder.append('(');
					}
					boolean or = false;
					if (autohideItem.optionSubject)
					{
						builder.append("subject");
						or = true;
					}
					if (autohideItem.optionComment)
					{
						if (or) builder.append(" | ");
						builder.append("comment");
						or = true;
					}
					if (autohideItem.optionName)
					{
						if (or) builder.append(" | ");
						builder.append("name");
						or = true;
					}
					if (and && orCount > 1) builder.append(')');
				}
				else
				{
					if (and) builder.append(" & ");
					builder.append("false");
				}
				holder.text2.setText(builder);
				return convertView;
			}
		};
		mAdapter.addAll(AutohideStorage.getInstance().getItems());
		setListAdapter(mAdapter);
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		editRule(mAdapter.getItem(position), position);
	}
	
	private static final int OPTIONS_MENU_NEW_RULE = 0;
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		ActionIconSet set = new ActionIconSet(getActivity());
		menu.add(0, OPTIONS_MENU_NEW_RULE, 0, R.string.action_new_rule).setIcon(set.getId(R.attr.actionAddRule))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		super.onCreateOptionsMenu(menu, inflater);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case OPTIONS_MENU_NEW_RULE:
			{
				editRule(null, -1);
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}
	
	private static final int CONTEXT_MENU_REMOVE_RULE = 0;
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
	{
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(Menu.NONE, CONTEXT_MENU_REMOVE_RULE, 0, R.string.action_remove_rule);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId())
		{
			case CONTEXT_MENU_REMOVE_RULE:
			{
				AutohideStorage.getInstance().delete(menuInfo.position);
				mAdapter.remove(mAdapter.getItem(menuInfo.position));
				break;
			}
		}
		return super.onContextItemSelected(item);
	}
	
	private void editRule(AutohideStorage.AutohideItem autohideItem, int index)
	{
		AutohideDialog dialog = new AutohideDialog(autohideItem, index);
		dialog.setTargetFragment(this, 0);
		dialog.show(getFragmentManager(), AutohideDialog.class.getName());
	}
	
	private void onEditComplete(AutohideStorage.AutohideItem autohideItem, int index)
	{
		if (index == -1)
		{
			// Also will set id to item
			AutohideStorage.getInstance().add(autohideItem);
			mAdapter.add(autohideItem);
		}
		else if (index >= 0)
		{
			AutohideStorage.getInstance().update(index, autohideItem);
			mAdapter.remove(mAdapter.getItem(index));
			mAdapter.insert(autohideItem, index);
		}
	}
	
	public static class AutohideDialog extends DialogFragment implements View.OnClickListener,
			DialogInterface.OnClickListener
	{
		private static final String EXTRA_ITEM = "item";
		private static final String EXTRA_INDEX = "index";
		
		private final HashSet<String> mSelectedChanNames = new HashSet<>();
		
		private ScrollView mScrollView;
		private TextView mChanNameSelector;
		private EditText mBoardNameEdit;
		private EditText mThreadNumberEdit;
		private CheckBox mAutohideOriginalPost;
		private CheckBox mAutohideSage;
		private CheckBox mAutohideSubject;
		private CheckBox mAutohideComment;
		private CheckBox mAutohideName;
		private EditText mValueEdit;
		private TextView mErrorText;
		private TextView mMatcherText;
		private EditText mTestStringEdit;
		
		public AutohideDialog()
		{
			
		}
		
		public AutohideDialog(AutohideStorage.AutohideItem autohideItem, int index)
		{
			Bundle args = new Bundle();
			args.putParcelable(EXTRA_ITEM, autohideItem);
			args.putInt(EXTRA_INDEX, index);
			setArguments(args);
		}
		
		@SuppressLint("InflateParams")
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			ScrollView view = (ScrollView) LayoutInflater.from(getActivity()).inflate(R.layout.dialog_autohide, null);
			mScrollView = view;
			mChanNameSelector = (TextView) view.findViewById(R.id.chan_name);
			mBoardNameEdit = (EditText) view.findViewById(R.id.board_name);
			mThreadNumberEdit = (EditText) view.findViewById(R.id.thread_number);
			mAutohideOriginalPost = (CheckBox) view.findViewById(R.id.autohide_original_post);
			mAutohideSage = (CheckBox) view.findViewById(R.id.autohide_sage);
			mAutohideSubject = (CheckBox) view.findViewById(R.id.autohide_subject);
			mAutohideComment = (CheckBox) view.findViewById(R.id.autohide_comment);
			mAutohideName = (CheckBox) view.findViewById(R.id.autohide_name);
			mValueEdit = (EditText) view.findViewById(R.id.value);
			mErrorText = (TextView) view.findViewById(R.id.error_text);
			mMatcherText = (TextView) view.findViewById(R.id.matcher_result);
			mTestStringEdit = (EditText) view.findViewById(R.id.test_string);
			mValueEdit.addTextChangedListener(mValueListener);
			mTestStringEdit.addTextChangedListener(mTestStringListener);
			mChanNameSelector.setOnClickListener(this);
			Collection<String> chanNames = ChanManager.getInstance().getAvailableChanNames();
			if (chanNames.size() <= 1) mChanNameSelector.setVisibility(View.GONE);
			AutohideStorage.AutohideItem autohideItem = null;
			if (savedInstanceState != null) autohideItem = savedInstanceState.getParcelable(EXTRA_ITEM);
			if (autohideItem == null) autohideItem = getArguments().getParcelable(EXTRA_ITEM);
			if (autohideItem != null)
			{
				if (autohideItem.chanNames != null) mSelectedChanNames.addAll(autohideItem.chanNames);
				updateSelectedText();
				mBoardNameEdit.setText(autohideItem.boardName);
				mThreadNumberEdit.setText(autohideItem.threadNumber);
				mAutohideOriginalPost.setChecked(autohideItem.optionOriginalPost);
				mAutohideSage.setChecked(autohideItem.optionSage);
				mAutohideSubject.setChecked(autohideItem.optionSubject);
				mAutohideComment.setChecked(autohideItem.optionComment);
				mAutohideName.setChecked(autohideItem.optionName);
				mValueEdit.setText(autohideItem.value);
			}
			else
			{
				mChanNameSelector.setText(R.string.text_all_forums);
				mBoardNameEdit.setText(null);
				mThreadNumberEdit.setText(null);
				mAutohideOriginalPost.setChecked(false);
				mAutohideSage.setChecked(false);
				mAutohideSubject.setChecked(true);
				mAutohideComment.setChecked(true);
				mAutohideName.setChecked(true);
				mValueEdit.setText(null);
			}
			updateTestResult();
		}
		
		@Override
		public void onSaveInstanceState(Bundle outState)
		{
			super.onSaveInstanceState(outState);
			outState.putParcelable(EXTRA_ITEM, readDialogView());
		}
		
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState)
		{
			AlertDialog dialog = new AlertDialog.Builder(getActivity()).setView(mScrollView).setPositiveButton
					(R.string.action_save, this).setNegativeButton(android.R.string.cancel, null).create();
			dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
			return dialog;
		}
		
		@Override
		public void onClick(View v)
		{
			MultipleChanDialog dialog = new MultipleChanDialog(new ArrayList<String>(mSelectedChanNames));
			dialog.setTargetFragment(this, 0);
			dialog.show(getFragmentManager(), MultipleChanDialog.class.getName());
		}
		
		private void updateSelectedText()
		{
			String chanNameText;
			int size = mSelectedChanNames.size();
			if (size == 0) chanNameText = getString(R.string.text_all_forums);
			else if (size > 1) chanNameText = getString(R.string.text_several_forums); else
			{
				String chanName = mSelectedChanNames.iterator().next();
				ChanConfiguration configuration = ChanConfiguration.get(chanName);
				String title = configuration != null ? configuration.getTitle() : chanName;
				chanNameText = getString(R.string.text_only_forum_format, title);
			}
			mChanNameSelector.setText(chanNameText);
		}
		
		private AutohideStorage.AutohideItem readDialogView()
		{
			String boardName = mBoardNameEdit.getText().toString();
			String threadNumber = mThreadNumberEdit.getText().toString();
			boolean optionOriginalPost = mAutohideOriginalPost.isChecked();
			boolean optionSage = mAutohideSage.isChecked();
			boolean optionSubject = mAutohideSubject.isChecked();
			boolean optionComment = mAutohideComment.isChecked();
			boolean optionName = mAutohideName.isChecked();
			String value = mValueEdit.getText().toString();
			return new AutohideStorage.AutohideItem(mSelectedChanNames.size() > 0 ? mSelectedChanNames : null,
					boardName, threadNumber, optionOriginalPost, optionSage,
					optionSubject, optionComment, optionName, value);
		}
		
		@Override
		public void onClick(DialogInterface dialog, int which)
		{
			((AutohideFragment) getTargetFragment()).onEditComplete(readDialogView(),
					getArguments().getInt(EXTRA_INDEX));
		}
		
		private void onChansSelected(ArrayList<String> selected)
		{
			mSelectedChanNames.clear();
			mSelectedChanNames.addAll(selected);
			updateSelectedText();
		}
		
		private BackgroundColorSpan mErrorSpan;
		private ErrorEditTextSetter mErrorValueSetter;
		
		private void updateError(int index, String text)
		{
			boolean error = index >= 0;
			Editable value = mValueEdit.getEditableText();
			if (error)
			{
				if (mErrorSpan == null)
				{
					mErrorSpan = new BackgroundColorSpan(ResourceUtils.getColor(getActivity(), R.attr.colorTextError));
				}
				else value.removeSpan(mErrorSpan);
				if (index > 0)
				{
					value.setSpan(mErrorSpan, index - 1, index, Editable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
			}
			else if (mErrorSpan != null) value.removeSpan(mErrorSpan);
			if (C.API_LOLLIPOP)
			{
				if (mErrorValueSetter == null) mErrorValueSetter = new ErrorEditTextSetter(mValueEdit);
				mErrorValueSetter.setError(error);
			}
			if (StringUtils.isEmpty(text)) mErrorText.setVisibility(View.GONE); else
			{
				mScrollView.post(() ->
				{
					int position = mErrorText.getBottom() - mScrollView.getHeight();
					if (mScrollView.getScrollY() < position) mScrollView.smoothScrollTo(0, position);
				});
				mErrorText.setVisibility(View.VISIBLE);
				mErrorText.setText(text);
			}
		}
		
		private Pattern mWorkPattern;
		
		private void updateTestResult()
		{
			String matchedText = null;
			if (mWorkPattern != null)
			{
				Matcher matcher = mWorkPattern.matcher(mTestStringEdit.getText().toString());
				if (matcher.find()) matchedText = StringUtils.emptyIfNull(matcher.group());
			}
			if (matchedText != null)
			{
				if (StringUtils.isEmptyOrWhitespace(matchedText)) mMatcherText.setText(R.string.text_match_found);
				else mMatcherText.setText(getString(R.string.text_match_found_format, matchedText));
			}
			else mMatcherText.setText(R.string.text_no_matches_found);
		}
		
		private final TextWatcher mValueListener = new TextWatcher()
		{
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count)
			{
				Pattern pattern = null;
				try
				{
					pattern = AutohideStorage.AutohideItem.makePattern(s.toString());
					updateError(-1, null);
				}
				catch (PatternSyntaxException e)
				{
					updateError(e.getIndex(), e.getDescription());
				}
				mWorkPattern = pattern;
				updateTestResult();
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after)
			{
				
			}
			
			@Override
			public void afterTextChanged(Editable s)
			{
				
			}
		};
		
		private final TextWatcher mTestStringListener = new TextWatcher()
		{
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count)
			{
				updateTestResult();
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after)
			{
				
			}
			
			@Override
			public void afterTextChanged(Editable s)
			{
				
			}
		};
	}
	
	public static class MultipleChanDialog extends DialogFragment implements DialogInterface.OnMultiChoiceClickListener,
			DialogInterface.OnClickListener
	{
		private static final String EXTRA_SELECTED = "selected";
		private static final String EXTRA_CHECKED = "checked";
		
		public MultipleChanDialog()
		{
			
		}
		
		public MultipleChanDialog(ArrayList<String> selected)
		{
			Bundle args = new Bundle();
			args.putStringArrayList(EXTRA_SELECTED, selected);
			setArguments(args);
		}
		
		private List<String> mChanNames;
		private boolean[] mCheckedItems;
		
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState)
		{
			Collection<String> chanNames = ChanManager.getInstance().getAvailableChanNames();
			mChanNames = new ArrayList<String>(chanNames);
			String[] items = new String[chanNames.size()];
			for (int i = 0; i < chanNames.size(); i++) items[i] = ChanConfiguration.get(mChanNames.get(i)).getTitle();
			boolean[] checkedItems = savedInstanceState != null ? savedInstanceState
					.getBooleanArray(EXTRA_CHECKED) : null;
			// size != length means some chans were added or deleted while configuration was changing (very rare case)
			if (checkedItems == null || chanNames.size() != checkedItems.length)
			{
				ArrayList<String> selected = getArguments().getStringArrayList(EXTRA_SELECTED);
				checkedItems = new boolean[items.length];
				for (int i = 0; i < chanNames.size(); i++) checkedItems[i] = selected.contains(mChanNames.get(i));
			}
			mCheckedItems = checkedItems;
			return new AlertDialog.Builder(getActivity()).setMultiChoiceItems(items, checkedItems, this)
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(android.R.string.ok, this).create();
		}
		
		@Override
		public void onSaveInstanceState(Bundle outState)
		{
			super.onSaveInstanceState(outState);
			outState.putBooleanArray(EXTRA_CHECKED, mCheckedItems);
		}
		
		@Override
		public void onClick(DialogInterface dialog, int which, boolean isChecked)
		{
			mCheckedItems[which] = isChecked;
		}
		
		@Override
		public void onClick(DialogInterface dialog, int which)
		{
			ArrayList<String> selected = new ArrayList<>();
			for (int i = 0; i < mChanNames.size(); i++)
			{
				if (mCheckedItems[i]) selected.add(mChanNames.get(i));
			}
			((AutohideDialog) getTargetFragment()).onChansSelected(selected);
		}
	}
}