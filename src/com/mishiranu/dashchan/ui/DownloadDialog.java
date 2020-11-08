package com.mishiranu.dashchan.ui;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import chan.content.Chan;
import chan.util.CommonUtils;
import chan.util.DataFile;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.FileProvider;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ExecutorTask;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.MimeTypes;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.ProgressDialog;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

public class DownloadDialog {
	private static final Executor EXECUTOR = ConcurrentUtils.newSingleThreadPool(2000, "DownloadDialog", null);

	public interface Callback {
		void resolve(DownloadService.ChoiceRequest choiceRequest, DownloadService.DirectRequest directRequest);
		void resolve(DownloadService.ReplaceRequest replaceRequest, DownloadService.ReplaceRequest.Action action);
		void cancel(DownloadService.PrepareRequest prepareRequest);
	}

	private final Context context;
	private final Callback callback;

	private static class DialogHolder<Request> {
		private Request request;
		private AlertDialog dialog;

		public void dismissIfNotEqual(Request request) {
			if (dialog != null && (request == null || this.request != request)) {
				this.request = null;
				dialog.dismiss();
				dialog = null;
			}
		}

		public void onDismiss(DialogInterface dialog) {
			if (this.dialog == dialog) {
				request = null;
				this.dialog = null;
			}
		}

		public void install(Request request, AlertDialog dialog) {
			this.request = request;
			this.dialog = dialog;
		}
	}

	private final DialogHolder<DownloadService.ChoiceRequest> choiceDialog = new DialogHolder<>();
	private final DialogHolder<DownloadService.ReplaceRequest> replaceDialog = new DialogHolder<>();
	private final DialogHolder<DownloadService.PrepareRequest> prepareDialog = new DialogHolder<>();

	public DownloadDialog(Context context, Callback callback) {
		this.context = new ContextThemeWrapper(context, R.style.Theme_Gallery);
		this.callback = callback;
	}

	public void handleRequest(DownloadService.Request request) {
		if (request instanceof DownloadService.ChoiceRequest) {
			DownloadService.ChoiceRequest choiceRequest = (DownloadService.ChoiceRequest) request;
			choiceDialog.dismissIfNotEqual(choiceRequest);
			replaceDialog.dismissIfNotEqual(null);
			prepareDialog.dismissIfNotEqual(null);
			if (choiceDialog.dialog == null) {
				choiceDialog.install(choiceRequest, createChoice(choiceRequest, choiceDialog::onDismiss));
			}
		} else if (request instanceof DownloadService.ReplaceRequest) {
			DownloadService.ReplaceRequest replaceRequest = (DownloadService.ReplaceRequest) request;
			choiceDialog.dismissIfNotEqual(null);
			replaceDialog.dismissIfNotEqual(replaceRequest);
			prepareDialog.dismissIfNotEqual(null);
			if (replaceDialog.dialog == null) {
				replaceDialog.install(replaceRequest, createReplace(replaceRequest, choiceDialog::onDismiss));
			}
		} else if (request instanceof DownloadService.PrepareRequest) {
			DownloadService.PrepareRequest prepareRequest = (DownloadService.PrepareRequest) request;
			choiceDialog.dismissIfNotEqual(null);
			replaceDialog.dismissIfNotEqual(null);
			prepareDialog.dismissIfNotEqual(prepareRequest);
			if (prepareDialog.dialog == null) {
				prepareDialog.install(prepareRequest, createPrepare(prepareRequest, choiceDialog::onDismiss));
			}
		} else if (request == null) {
			choiceDialog.dismissIfNotEqual(null);
			replaceDialog.dismissIfNotEqual(null);
			prepareDialog.dismissIfNotEqual(null);
		} else {
			throw new IllegalArgumentException("Request is not supported");
		}
	}

	private static class ChoiceState {
		public boolean subdirectory;
		public boolean detailedName;
		public boolean originalName;
		public String path;
	}

	private AlertDialog createChoice(DownloadService.ChoiceRequest choiceRequest,
			AlertDialog.OnDismissListener onDismissListener) {
		boolean newState = false;
		if (!(choiceRequest.state instanceof ChoiceState)) {
			choiceRequest.state = new ChoiceState();
			newState = true;
		}
		ChoiceState state = (ChoiceState) choiceRequest.state;
		if (newState) {
			state.detailedName = Preferences.isDownloadDetailName();
			state.originalName = Preferences.isDownloadOriginalName();
		}

		DataFile root = DataFile.obtain(context, DataFile.Target.DOWNLOADS, null);
		InputMethodManager inputMethodManager = (InputMethodManager) context
				.getSystemService(Context.INPUT_METHOD_SERVICE);
		View view = LayoutInflater.from(context)
				.inflate(R.layout.dialog_download_choice, null);

		boolean allowDetailName = choiceRequest.allowDetailName();
		boolean allowOriginalName = choiceRequest.allowOriginalName();
		CheckBox detailNameCheckBox = view.findViewById(R.id.download_detail_name);
		CheckBox originalNameCheckBox = view.findViewById(R.id.download_original_name);
		if (choiceRequest.chanName == null && choiceRequest.boardName == null
				&& choiceRequest.threadNumber == null) {
			allowDetailName = false;
		}
		if (allowDetailName) {
			detailNameCheckBox.setChecked(state.detailedName);
			detailNameCheckBox.setOnCheckedChangeListener((b, isChecked) -> state.detailedName = isChecked);
		} else {
			detailNameCheckBox.setVisibility(View.GONE);
		}
		if (allowOriginalName) {
			originalNameCheckBox.setChecked(state.originalName);
			originalNameCheckBox.setOnCheckedChangeListener((b, isChecked) -> state.originalName = isChecked);
		} else {
			originalNameCheckBox.setVisibility(View.GONE);
		}

		AutoCompleteTextView editText = view.findViewById(android.R.id.text1);
		if (!allowDetailName && !allowOriginalName) {
			((ViewGroup.MarginLayoutParams) editText.getLayoutParams()).topMargin = 0;
		}

		if (choiceRequest.chanName != null && choiceRequest.threadNumber != null) {
			String chanTitle = Chan.get(choiceRequest.chanName).configuration.getTitle();
			String threadTitle = choiceRequest.threadTitle;
			if (threadTitle != null) {
				threadTitle = StringUtils.escapeFile(StringUtils.cutIfLongerToLine(threadTitle, 50, false), false);
			}
			String text = Preferences.getSubdir(choiceRequest.chanName, chanTitle,
					choiceRequest.boardName, choiceRequest.threadNumber, threadTitle);
			if (newState) {
				state.path = text;
			}
			editText.setText(state.path);
			editText.setSelection(editText.getText().length());
			editText.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}

				@Override
				public void afterTextChanged(Editable s) {
					state.path = s.toString();
				}
			});
			if (StringUtils.isEmpty(text)) {
				text = Preferences.formatSubdir(Preferences.DEFAULT_SUBDIR_PATTERN, choiceRequest.chanName,
						chanTitle, choiceRequest.boardName, choiceRequest.threadNumber, threadTitle);
			}
			editText.setHint(text);
		}

		editText.setOnItemClickListener((parent, v, position, id) -> v.post(() -> {
			Adapter adapter = (Adapter) editText.getAdapter();
			adapter.items = Collections.emptyList();
			adapter.notifyDataSetChanged();
			refreshDropDownContents(editText);
			editText.showDropDown();
		}));
		Runnable dropDownRunnable = editText::showDropDown;

		RadioGroup radioGroup = view.findViewById(R.id.download_choice);
		radioGroup.setOnCheckedChangeListener((rg, checkedId) -> {
			boolean enabled = checkedId == R.id.download_subdirectory;
			editText.setEnabled(enabled);
			state.subdirectory = enabled;
			if (enabled) {
				editText.dismissDropDown();
				refreshDropDownContents(editText);
				if (inputMethodManager != null) {
					inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
					editText.postDelayed(dropDownRunnable, 250);
				} else {
					dropDownRunnable.run();
				}
			} else {
				editText.removeCallbacks(dropDownRunnable);
			}
		});
		radioGroup.check(state.subdirectory ? R.id.download_subdirectory : R.id.download_common);
		view.<RadioButton>findViewById(R.id.download_common)
				.setText(context.getString(R.string.save_to_directory__format, root.getName()));

		Adapter adapter = new Adapter(root, () -> {
			if (editText.isEnabled()) {
				refreshDropDownContents(editText);
			}
		});
		editText.setAdapter(adapter);

		AlertDialog dialog = new AlertDialog.Builder(context)
				.setTitle(R.string.select_where_to_save)
				.setView(view)
				.setNegativeButton(android.R.string.cancel, (d, w) -> callback.resolve(choiceRequest, null))
				.setPositiveButton(android.R.string.ok, (d, w) -> handleChoiceResolve(choiceRequest,
						editText, detailNameCheckBox, originalNameCheckBox))
				.setOnCancelListener(d -> callback.resolve(choiceRequest, null))
				.create();
		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN |
				(C.API_R ? WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
						: ViewUtils.SOFT_INPUT_ADJUST_RESIZE_COMPAT));
		editText.setOnEditorActionListener((v, actionId, event) -> {
			handleChoiceResolve(choiceRequest, editText, detailNameCheckBox, originalNameCheckBox);
			dialog.dismiss();
			return true;
		});
		dialog.setOnDismissListener(d -> {
			adapter.shutdown();
			onDismissListener.onDismiss(d);
		});
		dialog.show();
		return dialog;
	}

	private void refreshDropDownContents(AutoCompleteTextView editText) {
		Editable editable = editText.getEditableText();
		TextWatcher[] watchers = editable.getSpans(0, editable.length(), TextWatcher.class);
		if (watchers != null) {
			for (TextWatcher watcher : watchers) {
				watcher.beforeTextChanged(editable, 0, 0, 0);
				watcher.onTextChanged(editable, 0, 0, 0);
				watcher.afterTextChanged(editable);
			}
		}
	}

	private void handleChoiceResolve(DownloadService.ChoiceRequest choiceRequest,
			AutoCompleteTextView editText, CheckBox detailNameCheckBox, CheckBox originalNameCheckBox) {
		String pathCandidate = editText.isEnabled() ? StringUtils
				.escapeFile(editText.getText().toString(), true).trim() : "";
		ArrayList<String> segments = new ArrayList<>();
		for (String segment : pathCandidate.split("/")) {
			if (DataFile.isValidSegment(segment)) {
				segments.add(segment);
			}
		}
		String path = StringUtils.nullIfEmpty(Adapter.buildPath(segments, 0));
		DownloadService.DirectRequest directRequest = choiceRequest.complete(path,
				detailNameCheckBox.isChecked(), originalNameCheckBox.isChecked());
		callback.resolve(choiceRequest, directRequest);
	}

	private static class ReplaceState {
		public int selectedId;
	}

	private AlertDialog createReplace(DownloadService.ReplaceRequest replaceRequest,
			AlertDialog.OnDismissListener onDismissListener) {
		if (!(replaceRequest.state instanceof ReplaceState)) {
			replaceRequest.state = new ReplaceState();
		}
		ReplaceState state = (ReplaceState) replaceRequest.state;

		int count = replaceRequest.queued + replaceRequest.exists;
		float density = ResourceUtils.obtainDensity(context);
		int padding = context.getResources().getDimensionPixelSize(R.dimen.dialog_padding_view);
		LinearLayout linearLayout = new LinearLayout(context);
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		linearLayout.setPadding(padding, padding, padding, C.API_LOLLIPOP ? (int) (8f * density) : padding);
		TextView textView = new TextView(context, null, android.R.attr.textAppearanceListItem);
		textView.setText(context.getResources().getQuantityString
				(R.plurals.number_files_already_exist__sentence_format, count, count));
		linearLayout.addView(textView);

		RadioGroup radioGroup = new RadioGroup(context);
		radioGroup.setOrientation(RadioGroup.VERTICAL);
		int[] options = {R.string.replace, R.string.keep_all, R.string.skip};
		int[] ids = {android.R.id.button1, android.R.id.button2, android.R.id.button3};
		for (int i = 0; i < options.length; i++) {
			RadioButton radioButton = new RadioButton(context);
			radioButton.setText(options[i]);
			radioButton.setId(ids[i]);
			radioGroup.addView(radioButton);
		}
		if (state.selectedId == 0) {
			state.selectedId = ids[0];
		}
		radioGroup.check(state.selectedId);
		radioGroup.setPadding(0, (int) (12f * density), 0, 0);
		radioGroup.setOnCheckedChangeListener((g, id) -> state.selectedId = id);
		linearLayout.addView(radioGroup);

		AlertDialog.Builder builder = new AlertDialog.Builder(context)
				.setView(linearLayout)
				.setPositiveButton(android.R.string.ok, (dialog, which) -> {
					switch (radioGroup.getCheckedRadioButtonId()) {
						case android.R.id.button1: {
							callback.resolve(replaceRequest, DownloadService.ReplaceRequest.Action.REPLACE);
							break;
						}
						case android.R.id.button2: {
							callback.resolve(replaceRequest, DownloadService.ReplaceRequest.Action.KEEP_ALL);
							break;
						}
						case android.R.id.button3: {
							callback.resolve(replaceRequest, DownloadService.ReplaceRequest.Action.SKIP);
							break;
						}
					}
				})
				.setNegativeButton(android.R.string.cancel, (d, w) -> callback.resolve(replaceRequest, null))
				.setOnCancelListener(d -> callback.resolve(replaceRequest, null));

		AlertDialog dialog;
		if (replaceRequest.exists == 1) {
			builder.setNeutralButton(R.string.view__verb, null);
			dialog = builder.create();
			final DataFile singleFile = replaceRequest.lastExistingFile;
			dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
				String extension = StringUtils.getFileExtension(singleFile.getName());
				String type = MimeTypes.forExtension(extension, "image/jpeg");
				Pair<File, Uri> fileOrUri = singleFile.getFileOrUri();
				Uri uri;
				if (fileOrUri.first != null) {
					uri = FileProvider.convertDownloadsLegacyFile(fileOrUri.first, type);
				} else if (fileOrUri.second != null) {
					uri = fileOrUri.second;
				} else {
					uri = null;
				}
				if (uri != null) {
					try {
						context.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
								.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION));
					} catch (ActivityNotFoundException e) {
						ClickableToast.show(R.string.unknown_address);
					}
				}
			}));
		} else {
			dialog = builder.create();
		}
		dialog.setOnDismissListener(onDismissListener);
		dialog.show();
		return dialog;
	}

	private ProgressDialog createPrepare(DownloadService.PrepareRequest prepareRequest,
			ProgressDialog.OnDismissListener onDismissListener) {
		ProgressDialog dialog = new ProgressDialog(context, null);
		dialog.setMessage(context.getString(R.string.processing_data__ellipsis));
		dialog.setButton(ProgressDialog.BUTTON_NEGATIVE, context.getString(android.R.string.cancel),
				(d, w) -> callback.cancel(prepareRequest));
		dialog.setOnCancelListener(d -> callback.cancel(prepareRequest));
		dialog.setOnDismissListener(onDismissListener);
		dialog.show();
		return dialog;
	}

	private static class Adapter extends BaseAdapter implements Filterable {
		private final DataFile root;
		private final Runnable refresh;

		private List<DialogDirectory> items = Collections.emptyList();

		public Adapter(DataFile root, Runnable refresh) {
			this.root = root;
			this.refresh = refresh;
		}

		@Override
		public int getCount() {
			return items.size();
		}

		@Override
		public DialogDirectory getItem(int position) {
			return items.get(position);
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			DialogDirectory dialogDirectory = getItem(position);
			if (convertView == null) {
				convertView = LayoutInflater.from(parent.getContext()).inflate(android.R.layout
						.simple_spinner_dropdown_item, parent, false);
				((TextView) convertView).setEllipsize(TextUtils.TruncateAt.START);
			}
			((TextView) convertView).setText(dialogDirectory.getDisplayName());
			return convertView;
		}

		private final Object lastDirectoryLock = new Object();
		private boolean lastDirectoryCancel = false;
		private String lastDirectoryPath;
		private final HashMap<String, DataFile> cachedDirectories = new HashMap<>();
		private List<DialogDirectory> lastDirectoryItems;
		private ExecutorTask<?, ?> lastDirectoryTask;

		public static String buildPath(List<String> segments, int parent) {
			StringBuilder directoryPathBuilder = new StringBuilder();
			for (int i = 0; i < segments.size() - parent; i++) {
				if (directoryPathBuilder.length() > 0) {
					directoryPathBuilder.append('/');
				}
				directoryPathBuilder.append(segments.get(i));
			}
			return directoryPathBuilder.toString();
		}

		private final Filter filter = new Filter() {
			@Override
			protected FilterResults performFiltering(CharSequence constraint) {
				String constraintString = constraint.toString();
				int separatorIndex = constraintString.lastIndexOf('/');
				String enterDirectoryPath = separatorIndex >= 0 ? constraintString.substring(0, separatorIndex) : "";
				List<String> segments = new ArrayList<>();
				for (String segment : enterDirectoryPath.split("/")) {
					if (DataFile.isValidSegment(segment)) {
						segments.add(segment);
					}
				}

				List<DialogDirectory> items;
				synchronized (lastDirectoryLock) {
					String directoryPath = buildPath(segments, 0);
					if (lastDirectoryPath == null || !CommonUtils.equals(lastDirectoryPath, directoryPath)) {
						lastDirectoryPath = directoryPath;
						lastDirectoryItems = Collections.emptyList();

						if (lastDirectoryTask != null) {
							lastDirectoryTask.cancel();
							lastDirectoryTask = null;
						}

						if (!lastDirectoryCancel) {
							Pair<DataFile, String> cachedDirectory = null;
							// Optimize deep traversal
							for (int i = 0; i < segments.size(); i++) {
								String path = buildPath(segments, i);
								DataFile directory = cachedDirectories.get(path);
								if (directory != null) {
									cachedDirectory = new Pair<>(directory, i == 0 ? ""
											: directoryPath.substring(path.length() + 1));
								}
							}
							Pair<DataFile, String> cachedDirectoryFinal = cachedDirectory;
							lastDirectoryTask = new ExecutorTask<Void, Pair<List<DataFile>, List<DialogDirectory>>>() {
								@Override
								protected Pair<List<DataFile>, List<DialogDirectory>> run() {
									DataFile directory = cachedDirectoryFinal != null
											? cachedDirectoryFinal.first.getChild(cachedDirectoryFinal.second) :
											StringUtils.isEmpty(directoryPath) ? root : root.getChild(directoryPath);
									ArrayList<DataFile> cachedFiles = new ArrayList<>();
									ArrayList<DialogDirectory> items = new ArrayList<>();
									List<DataFile> files = directory.getChildren();
									if (files != null) {
										for (DataFile file : files) {
											if (isCancelled()) {
												break;
											}
											if (file.isDirectory()) {
												List<String> childSegments = new ArrayList<>(segments);
												childSegments.add(file.getName());
												items.add(new DialogDirectory(childSegments, file.getLastModified()));
												cachedFiles.add(file);
											}
										}
									}
									if (!isCancelled()) {
										Collections.sort(items);
									}
									return new Pair<>(cachedFiles, items);
								}

								@Override
								protected void onComplete(Pair<List<DataFile>, List<DialogDirectory>> items) {
									synchronized (lastDirectoryLock) {
										if (items.first != null) {
											for (DataFile file : items.first) {
												cachedDirectories.put(file.getRelativePath(), file);
											}
										}
										lastDirectoryItems = items.second;
										lastDirectoryTask = null;
										notifyDataSetChanged();
										refresh.run();
									}
								}
							};
							lastDirectoryTask.execute(EXECUTOR);
						}
					}

					items = lastDirectoryItems;
				}

				String name = constraintString.substring(separatorIndex + 1);
				ArrayList<DialogDirectory> result = new ArrayList<>();
				for (DialogDirectory item : items) {
					if (item.filter(name)) {
						result.add(item);
					}
				}

				FilterResults results = new FilterResults();
				results.values = result;
				results.count = result.size();
				return results;
			}

			@Override
			protected void publishResults(CharSequence constraint, FilterResults results) {
				@SuppressWarnings("unchecked")
				ArrayList<DialogDirectory> items = (ArrayList<DialogDirectory>) results.values;
				Adapter.this.items = items;
				notifyDataSetChanged();
			}
		};

		@Override
		public Filter getFilter() {
			return filter;
		}

		public void shutdown() {
			synchronized (lastDirectoryLock) {
				lastDirectoryCancel = true;
				if (lastDirectoryTask != null) {
					lastDirectoryTask.cancel();
					lastDirectoryTask = null;
				}
			}
		}
	}

	private static class DialogDirectory implements Comparable<DialogDirectory> {
		public final List<String> segments;
		public final long lastModified;

		public DialogDirectory(List<String> segments, long lastModified) {
			this.segments = segments;
			this.lastModified = lastModified;
		}

		public boolean filter(String name) {
			Locale locale = Locale.getDefault();
			name = name.toLowerCase(locale);
			String lastSegment = segments.get(segments.size() - 1).toLowerCase(locale);
			if (lastSegment.startsWith(name)) {
				return true;
			}
			String[] splitted = lastSegment.split("[\\W_]+");
			for (String part : splitted) {
				if (part.startsWith(name)) {
					return true;
				}
			}
			return false;
		}

		private String convert(boolean displayName) {
			StringBuilder builder = new StringBuilder();
			for (String segment : segments) {
				if (builder.length() > 0) {
					if (displayName) {
						builder.append(" / ");
					} else {
						builder.append('/');
					}
				}
				builder.append(segment);
			}
			if (!displayName) {
				builder.append('/');
			}
			return builder.toString();
		}

		public String getDisplayName() {
			return convert(true);
		}

		@NonNull
		@Override
		public String toString() {
			return convert(false);
		}

		@Override
		public int compareTo(DialogDirectory another) {
			return Long.compare(another.lastModified, lastModified);
		}
	}
}
