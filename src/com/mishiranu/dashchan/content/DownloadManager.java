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

package com.mishiranu.dashchan.content;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.Semaphore;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.app.service.DownloadService;
import com.mishiranu.dashchan.async.SendLocalArchiveTask;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;

public class DownloadManager
{
	private static final DownloadManager INSTANCE = new DownloadManager();
	
	public static DownloadManager getInstance()
	{
		return INSTANCE;
	}
	
	private DownloadManager()
	{
		
	}
	
	private final HashSet<String> mQueuedFiles = new HashSet<>();
	private final Semaphore mQueuePause = new Semaphore(1);
	
	private ArrayList<DialogDirectory> mLastDialogDirectotyItems;
	private File mLastRootDirectory;
	
	public String getFileGlobalQueueSetName(File file)
	{
		return file.getAbsolutePath().toLowerCase(Locale.getDefault());
	}
	
	public void notifyFileAddedToDownloadQueue(File file)
	{
		mQueuedFiles.add(getFileGlobalQueueSetName(file));
	}
	
	public void notifyFileRemovedFromDownloadQueue(File file)
	{
		mQueuedFiles.remove(getFileGlobalQueueSetName(file));
		if (file.exists() && mLastDialogDirectotyItems != null && mLastRootDirectory != null)
		{
			DialogDirectory dialogDirectory = DialogDirectory.create(file.getParentFile(), mLastRootDirectory);
			if (dialogDirectory != null)
			{
				dialogDirectory.lastModified = file.lastModified();
				while (dialogDirectory != null)
				{
					int index = mLastDialogDirectotyItems.indexOf(dialogDirectory);
					if (index == -1) mLastDialogDirectotyItems.add(dialogDirectory);
					else mLastDialogDirectotyItems.set(index, dialogDirectory);
					dialogDirectory = dialogDirectory.getParent();
				}
				Collections.sort(mLastDialogDirectotyItems);
			}
		}
	}
	
	public void notifyServiceDestroy()
	{
		mQueuedFiles.clear();
		releaseQueuePauseLock();
	}
	
	public void notifyFinishDownloadingInThread()
	{
		if (aquireQueuePauseLock(false)) releaseQueuePauseLock();
	}
	
	private boolean aquireQueuePauseLock(boolean tryOnly)
	{
		if (tryOnly) return mQueuePause.tryAcquire(); else
		{
			try
			{
				mQueuePause.acquire();
				return true;
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return false;
			}
		}
	}
	
	private void releaseQueuePauseLock()
	{
		mQueuePause.release();
	}
	
	public static class RequestItem
	{
		public final Uri uri;
		public final String fileName;
		public final String originalName;
		
		public RequestItem(Uri uri, String fileName, String originalName)
		{
			this.uri = uri;
			this.fileName = fileName;
			this.originalName = originalName;
		}
	}
	
	private static class DialogDirectory implements Comparable<DialogDirectory>
	{
		public final ArrayList<String> mSegments = new ArrayList<String>();
		public long lastModified;
		
		public static DialogDirectory create(File directory, File root)
		{
			DialogDirectory dialogDirectory = new DialogDirectory();
			dialogDirectory.lastModified = directory.lastModified();
			while (directory != null && !directory.equals(root))
			{
				dialogDirectory.mSegments.add(0, directory.getName());
				directory = directory.getParentFile();
			}
			return directory != null ? dialogDirectory : null;
		}
		
		private DialogDirectory()
		{
			
		}
		
		public DialogDirectory(String path)
		{
			Collections.addAll(mSegments, path.split("/", -1));
		}
		
		public int getDepth()
		{
			return mSegments.size();
		}
		
		private String getSegment(int index, Locale locale)
		{
			return mSegments.get(index).toLowerCase(locale);
		}
		
		public DialogDirectory getParent()
		{
			if (mSegments.size() > 1)
			{
				DialogDirectory dialogDirectory = new DialogDirectory();
				dialogDirectory.lastModified = lastModified;
				dialogDirectory.mSegments.addAll(mSegments);
				dialogDirectory.mSegments.remove(dialogDirectory.mSegments.size() - 1);
				return dialogDirectory;
			}
			return null;
		}
		
		public boolean filter(DialogDirectory constraintDirectory, boolean anyDepth)
		{
			Locale locale = Locale.getDefault();
			if (!anyDepth)
			{
				int depth = getDepth();
				if (constraintDirectory.getDepth() != depth) return false;
				for (int i = 0; i < depth - 1; i++)
				{
					if (!constraintDirectory.getSegment(i, locale).equals(getSegment(i, locale))) return false;
				}
			}
			String lastSegment = getSegment(getDepth() - 1, locale);
			String constraintLastSegment = constraintDirectory.getSegment(constraintDirectory.getDepth() - 1, locale);
			if (lastSegment.startsWith(constraintLastSegment)) return true;
			String[] splitted = lastSegment.split("[\\W_]+");
			for (String part : splitted)
			{
				if (part.startsWith(constraintLastSegment)) return true;
			}
			return false;
		}
		
		private String convert(boolean displayName)
		{
			StringBuilder builder = new StringBuilder();
			for (String segment : mSegments)
			{
				if (builder.length() > 0)
				{
					if (displayName) builder.append(" / "); else builder.append('/');
				}
				builder.append(segment);
			}
			if (!displayName) builder.append('/');
			return builder.toString();
		}
		
		public String getDisplayName()
		{
			return convert(true);
		}
		
		@Override
		public String toString()
		{
			return convert(false);
		}
		
		@Override
		public boolean equals(Object o)
		{
			if (o == this) return true;
			if (o instanceof DialogDirectory)
			{
				DialogDirectory co = (DialogDirectory) o;
				if (co.getDepth() != getDepth()) return false;
				Locale locale = Locale.getDefault();
				for (int i = 0; i < getDepth(); i++)
				{
					if (!getSegment(i, locale).equals(co.getSegment(i, locale))) return false;
				}
				return true;
			}
			return false;
		}
		
		@Override
		public int hashCode()
		{
			int prime = 31;
			int result = 1;
			Locale locale = Locale.getDefault();
			for (int i = 0; i < getDepth(); i++) result = prime * result + getSegment(i, locale).hashCode();
			return result;
		}
		
		@Override
		public int compareTo(DialogDirectory another)
		{
			return ((Long) another.lastModified).compareTo(lastModified);
		}
	}
	
	private static class DialogAdapter extends BaseAdapter implements Filterable
	{
		private final ArrayList<DialogDirectory> mDialogDirectoryItems = new ArrayList<>();
		private ArrayList<DialogDirectory> mFilteredDialogDirectoryItems;
		private final Object mLock = new Object();
		
		@Override
		public int getCount()
		{
			return (mFilteredDialogDirectoryItems != null ? mFilteredDialogDirectoryItems
					: mDialogDirectoryItems).size();
		}
		
		@Override
		public DialogDirectory getItem(int position)
		{
			return (mFilteredDialogDirectoryItems != null ? mFilteredDialogDirectoryItems
					: mDialogDirectoryItems).get(position);
		}
		
		@Override
		public long getItemId(int position)
		{
			return 0;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			DialogDirectory dialogDirectory = getItem(position);
			if (convertView == null)
			{
				convertView = LayoutInflater.from(parent.getContext()).inflate(android.R.layout
						.simple_spinner_dropdown_item, parent, false);
				((TextView) convertView).setEllipsize(TextUtils.TruncateAt.START);
			}
			((TextView) convertView).setText(dialogDirectory.getDisplayName());
			return convertView;
		}
		
		private final Filter mFilter = new Filter()
		{
			@Override
			protected void publishResults(CharSequence constraint, FilterResults results)
			{
				@SuppressWarnings("unchecked")
				ArrayList<DialogDirectory> values = (ArrayList<DialogDirectory>) results.values;
				if (results.count == mDialogDirectoryItems.size()) values = null;
				mFilteredDialogDirectoryItems = values;
				notifyDataSetChanged();
			}
			
			@Override
			protected FilterResults performFiltering(CharSequence constraint)
			{
				String constraintString = constraint.toString();
				boolean anyDepth = false;
				if (constraintString.startsWith("/"))
				{
					anyDepth = true;
					constraintString = constraintString.substring(1);
				}
				DialogDirectory constraintDirectory = new DialogDirectory(constraintString);
				FilterResults results = new FilterResults();
				ArrayList<DialogDirectory> values = new ArrayList<>();
				synchronized (mLock)
				{
					for (DialogDirectory dialogDirectory : mDialogDirectoryItems)
					{
						if (dialogDirectory.filter(constraintDirectory, anyDepth)) values.add(dialogDirectory);
					}
				}
				results.values = values;
				results.count = values.size();
				return results;
			}
		};
		
		@Override
		public Filter getFilter()
		{
			return mFilter;
		}
		
		public void setItems(Collection<DialogDirectory> directories)
		{
			synchronized (mLock)
			{
				mDialogDirectoryItems.clear();
				mDialogDirectoryItems.addAll(directories);
				notifyDataSetChanged();
			}
		}
		
		public ArrayList<DialogDirectory> getItems()
		{
			return mDialogDirectoryItems;
		}
	}
	
	private class DownloadDialog implements DialogInterface.OnClickListener, RadioGroup.OnCheckedChangeListener,
			AutoCompleteTextView.OnEditorActionListener, AdapterView.OnItemClickListener
	{
		private final Context mContext;
		private final DialogCallback mCallback;
		private final String mChanName;
		private final String mBoardName;
		private final String mThreadNumber;
		
		private final AlertDialog mDialog;
		private final DialogAdapter mAdapter = new DialogAdapter();
		private final CheckBox mDetailNameCheckBox;
		private final CheckBox mOriginalNameCheckBox;
		private final AutoCompleteTextView mEditText;
		private final InputMethodManager mInputMethodManager;
		
		private final Runnable mDropDownRunnable;
		
		@SuppressLint("InflateParams")
		public DownloadDialog(Context context, DialogCallback callback, String chanName,
				String boardName, String threadNumber, String threadTitle,
				boolean allowDetailedFileName, boolean allowOriginalName)
		{
			mContext = context;
			mCallback = callback;
			mChanName = chanName;
			mBoardName = boardName;
			mThreadNumber = threadNumber;
			mInputMethodManager = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
			final File directory = Preferences.getDownloadDirectory();
			View view = LayoutInflater.from(context).inflate(R.layout.dialog_download_choice, null);
			RadioGroup radioGroup = (RadioGroup) view.findViewById(R.id.download_choice);
			radioGroup.check(R.id.download_common);
			radioGroup.setOnCheckedChangeListener(this);
			((RadioButton) view.findViewById(R.id.download_common)).setText(context
					.getString(R.string.text_download_to_format, directory.getName()));
			mDetailNameCheckBox = (CheckBox) view.findViewById(R.id.download_detail_name);
			mOriginalNameCheckBox = (CheckBox) view.findViewById(R.id.download_original_name);
			if (chanName == null && boardName == null && threadNumber == null) allowDetailedFileName = false;
			if (allowDetailedFileName) mDetailNameCheckBox.setChecked(Preferences.isDownloadDetailName());
			else mDetailNameCheckBox.setVisibility(View.GONE);
			if (allowOriginalName) mOriginalNameCheckBox.setChecked(Preferences.isDownloadOriginalName());
			else mOriginalNameCheckBox.setVisibility(View.GONE);
			AutoCompleteTextView editText = (AutoCompleteTextView) view.findViewById(android.R.id.text1);
			if (!allowDetailedFileName && !allowOriginalName)
			{
				((ViewGroup.MarginLayoutParams) editText.getLayoutParams()).topMargin = 0;
			}
			if (threadNumber != null)
			{
				String chanTitle = ChanConfiguration.get(chanName).getTitle();
				if (threadTitle != null)
				{
					threadTitle = StringUtils.escapeFile(StringUtils.cutIfLongerToLine(threadTitle, 50, false), false);
				}
				String text = Preferences.getSubdir(chanName, chanTitle, boardName, threadNumber, threadTitle);
				editText.setText(text);
				editText.setSelection(text.length());
				if (StringUtils.isEmpty(text))
				{
					text = Preferences.formatSubdir(Preferences.DEFAULT_SUBDIR_PATTERN, chanName, chanTitle, boardName,
							threadNumber, threadTitle);
				}
				editText.setHint(text);
			}
			editText.setEnabled(false);
			editText.setOnEditorActionListener(this);
			editText.setOnItemClickListener(this);
			mEditText = editText;
			mDropDownRunnable = () -> mEditText.showDropDown();
			editText.setAdapter(mAdapter);
			mDialog = new AlertDialog.Builder(context).setTitle(R.string.text_download_title).setView(view)
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(android.R.string.ok, this).create();
			mDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
					| WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
			mDialog.show();
			if (directory.equals(mLastRootDirectory)) mAdapter.setItems(mLastDialogDirectotyItems);
			new AsyncTask<Void, Void, ArrayList<DialogDirectory>>()
			{
				private long findDirectories(ArrayList<DialogDirectory> dialogDirectories, File parent,
						File root, Collection<File> exclude)
				{
					long resultLastModified = 0L;
					File[] files = parent.listFiles();
					if (files != null)
					{
						for (File file : files)
						{
							if ((exclude == null || !exclude.contains(file)))
							{
								long lastModified;
								if (file.isDirectory())
								{
									DialogDirectory dialogDirectory = DialogDirectory.create(file, root);
									dialogDirectories.add(dialogDirectory);
									lastModified = findDirectories(dialogDirectories, file, root, exclude);
									lastModified = Math.max(dialogDirectory.lastModified, lastModified);
									dialogDirectory.lastModified = lastModified;
								}
								else lastModified = file.lastModified();
								if (lastModified > resultLastModified) resultLastModified = lastModified;
							}
						}
					}
					return resultLastModified;
				}
				
				@Override
				protected ArrayList<DialogDirectory> doInBackground(Void... params)
				{
					ArrayList<DialogDirectory> directories = new ArrayList<>();
					ArrayList<File> exclude = new ArrayList<>();
					exclude.add(SendLocalArchiveTask.getLocalDownloadDirectory(false));
					findDirectories(directories, directory, directory, exclude);
					Collections.sort(directories);
					return directories;
				}
				
				@Override
				protected void onPostExecute(ArrayList<DialogDirectory> result)
				{
					mLastDialogDirectotyItems = result;
					mLastRootDirectory = directory;
					ArrayList<DialogDirectory> dialogDirectories = mAdapter.getItems();
					int size = result.size();
					if (size > 0 && dialogDirectories.size() == size)
					{
						HashSet<DialogDirectory> set = new HashSet<>(dialogDirectories);
						for (DialogDirectory dialogDirectory : result) set.remove(dialogDirectory);
						if (set.isEmpty()) return; // Updating is not necessary
					}
					mAdapter.setItems(result);
					if (mEditText.isEnabled()) refreshDropDownContents();
				}
			}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
		
		@Override
		public void onClick(DialogInterface dialog, int which)
		{
			complete();
		}
		
		private void refreshDropDownContents()
		{
			Editable editable = mEditText.getEditableText();
			TextWatcher[] watchers = editable.getSpans(0, editable.length(), TextWatcher.class);
			if (watchers != null)
			{
				for (TextWatcher watcher : watchers)
				{
					watcher.beforeTextChanged(editable, 0, 0, 0);
					watcher.onTextChanged(editable, 0, 0, 0);
					watcher.afterTextChanged(editable);
				}
			}
		}
		
		@Override
		public void onCheckedChanged(RadioGroup group, int checkedId)
		{
			boolean enabled = checkedId == R.id.download_subdirectory;
			mEditText.setEnabled(enabled);
			mEditText.setCompoundDrawables(null, null, enabled ? ResourceUtils.getDrawable(mContext,
					R.attr.buttonCancel, 0) : null, null);
			if (enabled)
			{
				mEditText.dismissDropDown();
				refreshDropDownContents();
				if (mInputMethodManager != null)
				{
					mInputMethodManager.showSoftInput(mEditText, InputMethodManager.SHOW_IMPLICIT);
					mEditText.postDelayed(mDropDownRunnable, 250);
				}
				else mDropDownRunnable.run();
			}
			else mEditText.removeCallbacks(mDropDownRunnable);
		}
		
		@Override
		public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
		{
			mDialog.dismiss();
			complete();
			return true;
		}
		
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id)
		{
			mEditText.post(() ->
			{
				refreshDropDownContents();
				mEditText.showDropDown();
			});
		}
		
		private void complete()
		{
			String path = mEditText.isEnabled() ? StringUtils.nullIfEmpty(StringUtils
					.escapeFile(mEditText.getText().toString(), true).trim()) : null;
			mCallback.onDirectoryChosen(mContext, path, mDetailNameCheckBox.isChecked(),
					mOriginalNameCheckBox.isChecked(), mChanName, mBoardName, mThreadNumber);
		}
	}
	
	private static interface ReplaceCallback
	{
		public void onConfirmReplacement(Context context, ArrayList<DownloadService.DownloadItem> downloadItems);
	}
	
	private void confirmReplacement(final Context context, final File directory,
			final ArrayList<DownloadService.DownloadItem> downloadItems, final ReplaceCallback callback)
	{
		HashSet<String> availableFileGqsns = new HashSet<>();
		final ArrayList<DownloadService.DownloadItem> availableItems = new ArrayList<>();
		File lastExistedFile = null;
		int queued = 0, exists = 0;
		for (DownloadService.DownloadItem downloadItem : downloadItems)
		{
			File file = new File(directory, downloadItem.name);
			String gqsn = getFileGlobalQueueSetName(file);
			if (mQueuedFiles.contains(gqsn) || availableFileGqsns.contains(gqsn)) queued++;
			else if (file.exists())
			{
				exists++;
				lastExistedFile = file;
			}
			else
			{
				availableFileGqsns.add(gqsn);
				availableItems.add(downloadItem);
			}
		}
		if (availableItems.size() != downloadItems.size())
		{
			// Pause downloading until user choose the way
			aquireQueuePauseLock(true);
			int count = queued + exists;
			float density = ResourceUtils.obtainDensity(context);
			int padding = context.getResources().getDimensionPixelSize(R.dimen.dialog_padding_view);
			LinearLayout linearLayout = new LinearLayout(context);
			linearLayout.setOrientation(LinearLayout.VERTICAL);
			linearLayout.setPadding(padding, padding, padding, C.API_LOLLIPOP ? (int) (8f * density) : padding);
			TextView textView = new TextView(context, null, android.R.attr.textAppearanceListItem);
			textView.setText(context.getResources().getQuantityString(R.plurals.text_files_exist_format, count, count));
			linearLayout.addView(textView);
			final RadioGroup radioGroup = new RadioGroup(context);
			radioGroup.setOrientation(RadioGroup.VERTICAL);
			int[] options = {R.string.text_replace, R.string.text_keep_all, R.string.text_dont_replace};
			int[] ids = {android.R.id.button1, android.R.id.button2, android.R.id.button3};
			for (int i = 0; i < options.length; i++)
			{
				RadioButton radioButton = new RadioButton(context);
				radioButton.setText(options[i]);
				radioButton.setId(ids[i]);
				radioGroup.addView(radioButton);
			}
			radioGroup.check(ids[0]);
			radioGroup.setPadding(0, (int) (12f * density), 0, 0);
			linearLayout.addView(radioGroup);
			AlertDialog.Builder builder = new AlertDialog.Builder(context).setView(linearLayout)
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					switch (radioGroup.getCheckedRadioButtonId())
					{
						case android.R.id.button1:
						{
							callback.onConfirmReplacement(context, downloadItems);
							break;
						}
						case android.R.id.button2:
						{
							HashSet<String> availableFileGqsns = new HashSet<>();
							ArrayList<DownloadService.DownloadItem> finalItems = new ArrayList<>(downloadItems.size());
							for (DownloadService.DownloadItem downloadItem : downloadItems)
							{
								if (availableItems.contains(downloadItem))
								{
									availableFileGqsns.add(getFileGlobalQueueSetName(new File(directory,
											downloadItem.name)));
									finalItems.add(downloadItem);
								}
								else
								{
									String fileName = downloadItem.name;
									String dotExtension = "." + StringUtils.getFileExtension(fileName);
									fileName = fileName.substring(0, fileName.length() - dotExtension.length());
									File file = null;
									String gqsn = null;
									int i = 0;
									do
									{
										file = new File(directory, fileName + (i > 0 ? "-" + i : "") + dotExtension);
										gqsn = getFileGlobalQueueSetName(file);
										i++;
									}
									while (file.exists() || mQueuedFiles.contains(gqsn) ||
											availableFileGqsns.contains(gqsn));
									availableFileGqsns.add(gqsn);
									finalItems.add(new DownloadService.DownloadItem(downloadItem.chanName,
											downloadItem.uri, file.getName()));
								}
							}
							callback.onConfirmReplacement(context, finalItems);
							break;
						}
						case android.R.id.button3:
						{
							callback.onConfirmReplacement(context, availableItems);
							break;
						}
					}
				}
			}).setNegativeButton(android.R.string.cancel, null);
			AlertDialog dialog;
			if (exists == 1)
			{
				builder.setNeutralButton(R.string.action_view, null);
				dialog = builder.create();
				final File singleFile = lastExistedFile;
				dialog.setOnShowListener(d ->
				{
					dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
					{
						String extension = StringUtils.getFileExtension(singleFile.getPath());
						String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
						try
						{
							context.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType
									(Uri.fromFile(singleFile), type).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
						}
						catch (ActivityNotFoundException e)
						{
							ToastUtils.show(context, R.string.message_unknown_address);
						}
					});
				});
			}
			else dialog = builder.create();
			dialog.setOnDismissListener(d -> releaseQueuePauseLock());
			dialog.show();
		}
		else callback.onConfirmReplacement(context, downloadItems);
	}
	
	private interface DialogCallback
	{
		public void onDirectoryChosen(Context context, String path, boolean detailName, boolean originalName,
				String chanName, String boardName, String threadNumber);
	}
	
	private class StorageDialogCallback implements DialogCallback, ReplaceCallback
	{
		private final ArrayList<RequestItem> mRequestItems;
		private File mDirectory;
		
		public StorageDialogCallback(ArrayList<RequestItem> requestItems)
		{
			mRequestItems = requestItems;
		}
		
		@Override
		public void onDirectoryChosen(Context context, String path, boolean detailName, boolean originalName,
				String chanName, String boardName, String threadNumber)
		{
			mDirectory = getDownloadDirectory(path);
			ArrayList<DownloadService.DownloadItem> downloadItems = new ArrayList<>(mRequestItems.size());
			for (RequestItem requestItem : mRequestItems)
			{
				downloadItems.add(new DownloadService.DownloadItem(chanName, requestItem.uri,
						getDesiredFileName(requestItem.uri, requestItem.fileName, originalName
						? requestItem.originalName : null, detailName, chanName, boardName, threadNumber)));
			}
			confirmReplacement(context, mDirectory, downloadItems, this);
		}
		
		@Override
		public void onConfirmReplacement(Context context, ArrayList<DownloadService.DownloadItem> downloadItems)
		{
			DownloadService.downloadDirect(context, mDirectory, downloadItems);
		}
	}
	
	private class StreamDialogCallback implements DialogCallback, ReplaceCallback
	{
		private final InputStream mInput;
		private final String mFileName;
		private File mDirectory;
		
		public StreamDialogCallback(InputStream input, String fileName)
		{
			mInput = input;
			mFileName = fileName;
		}
		
		@Override
		public void onDirectoryChosen(Context context, String path, boolean detailName, boolean originalName,
				String chanName, String boardName, String threadNumber)
		{
			String fileName = mFileName;
			if (detailName) fileName = getFileNameWithChanBoardThreadData(fileName, chanName, boardName, threadNumber);
			mDirectory = getDownloadDirectory(path);
			ArrayList<DownloadService.DownloadItem> downloadItems = new ArrayList<>(1);
			downloadItems.add(new DownloadService.DownloadItem(chanName, null, fileName));
			confirmReplacement(context, mDirectory, downloadItems, this);
		}
		
		@Override
		public void onConfirmReplacement(Context context, ArrayList<DownloadService.DownloadItem> downloadItems)
		{
			if (downloadItems.size() == 0) return;
			File file = new File(mDirectory, downloadItems.get(0).name);
			OutputStream output = null;
			boolean success = false;
			try
			{
				output = IOUtils.openOutputStream(context, file);
				IOUtils.copyStream(mInput, output);
				success = true;
			}
			catch (IOException e)
			{
				
			}
			finally
			{
				IOUtils.close(mInput);
				IOUtils.close(output);
			}
			if (success) MediaScannerConnection.scanFile(context, new String[] {file.getAbsolutePath()}, null, null);
			DownloadService.showFake(context, file, success);
		}
	}
	
	private File getDownloadDirectory(String path)
	{
		File directory = Preferences.getDownloadDirectory();
		if (path != null)
		{
			directory = new File(directory, path);
			directory.mkdirs();
		}
		return directory;
	}
	
	private String getFileNameWithChanBoardThreadData(String fileName, String chanName, String boardName,
			String threadNumber)
	{
		String extension = StringUtils.getFileExtension(fileName);
		fileName = fileName.substring(0, fileName.length() - extension.length() - 1);
		StringBuilder builder = new StringBuilder();
		builder.append(fileName);
		if (chanName != null) builder.append('-').append(chanName);
		if (boardName != null) builder.append('-').append(boardName);
		if (threadNumber != null) builder.append('-').append(threadNumber);
		return builder.append('.').append(extension).toString();
	}
	
	private boolean isFileNameModifyingAllowed(String chanName, Uri uri)
	{
		return chanName != null && ChanLocator.get(chanName).safe(false).isAttachmentUri(uri);
	}
	
	private String getDesiredFileName(Uri uri, String fileName, String originalName, boolean detailName,
			String chanName, String boardName, String threadNumber)
	{
		if (isFileNameModifyingAllowed(chanName, uri))
		{
			if (originalName != null && Preferences.isDownloadOriginalName()) fileName = originalName;
			if (detailName) fileName = getFileNameWithChanBoardThreadData(fileName, chanName, boardName, threadNumber);
		}
		return fileName;
	}
	
	public void downloadStorage(Context context, Uri uri, String fileName, String originalName,
			String chanName, String boardName, String threadNumber, String threadTitle)
	{
		downloadStorage(context, new RequestItem(uri, fileName, originalName),
				chanName, boardName, threadNumber, threadTitle);
	}
	
	public void downloadStorage(Context context, RequestItem requestItem, String chanName,
			String boardName, String threadNumber, String threadTitle)
	{
		ArrayList<RequestItem> requestItems = new ArrayList<>(1);
		requestItems.add(requestItem);
		downloadStorage(context, requestItems, chanName, boardName, threadNumber, threadTitle, false);
	}
	
	public void downloadStorage(Context context, ArrayList<RequestItem> requestItems, String chanName,
			String boardName, String threadNumber, String threadTitle, boolean multiple)
	{
		StorageDialogCallback callback = new StorageDialogCallback(requestItems);
		if (Preferences.isDownloadSubdir(multiple))
		{
			boolean modifyingAllowed = false;
			boolean hasOriginalNames = false;
			for (RequestItem requestItem : requestItems)
			{
				if (isFileNameModifyingAllowed(chanName, requestItem.uri)) modifyingAllowed = true;
				if (requestItem.originalName != null) hasOriginalNames = true;
			}
			new DownloadDialog(context, callback, chanName, boardName, threadNumber, threadTitle,
					modifyingAllowed, modifyingAllowed && hasOriginalNames);
		}
		else
		{
			callback.onDirectoryChosen(context, null, Preferences.isDownloadDetailName(),
					Preferences.isDownloadOriginalName(), chanName, boardName, threadNumber);
		}
	}
	
	public void saveStreamStorage(Context context, InputStream input, String chanName,
			String boardName, String threadNumber, String threadTitle, String fileName, boolean forceNoDialog)
	{
		StreamDialogCallback callback = new StreamDialogCallback(input, fileName);
		if (Preferences.isDownloadSubdir(false) && !forceNoDialog)
		{
			new DownloadDialog(context, callback, chanName, boardName, threadNumber, threadTitle, true, false);
		}
		else
		{
			callback.onDirectoryChosen(context, null, Preferences.isDownloadDetailName(), false,
					chanName, boardName, threadNumber);
		}
	}
}