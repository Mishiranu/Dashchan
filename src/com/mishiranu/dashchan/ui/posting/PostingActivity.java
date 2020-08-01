package com.mishiranu.dashchan.ui.posting;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.text.Editable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toolbar;
import androidx.annotation.NonNull;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanMarkup;
import chan.content.ChanPerformer;
import chan.text.CommentEditor;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.AsyncManager;
import com.mishiranu.dashchan.content.async.ReadCaptchaTask;
import com.mishiranu.dashchan.content.async.SendPostTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.content.service.PostingService;
import com.mishiranu.dashchan.content.storage.DraftsStorage;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.graphics.RoundedCornersDrawable;
import com.mishiranu.dashchan.graphics.TransparentTileDrawable;
import com.mishiranu.dashchan.media.JpegData;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.CaptchaForm;
import com.mishiranu.dashchan.ui.ForegroundManager;
import com.mishiranu.dashchan.ui.StateActivity;
import com.mishiranu.dashchan.ui.posting.dialog.AttachmentOptionsDialog;
import com.mishiranu.dashchan.ui.posting.dialog.AttachmentRatingDialog;
import com.mishiranu.dashchan.ui.posting.dialog.AttachmentWarningDialog;
import com.mishiranu.dashchan.ui.posting.dialog.PostingDialog;
import com.mishiranu.dashchan.ui.posting.dialog.ReencodingDialog;
import com.mishiranu.dashchan.ui.posting.dialog.SendPostFailDetailsDialog;
import com.mishiranu.dashchan.ui.posting.text.CommentEditWatcher;
import com.mishiranu.dashchan.ui.posting.text.MarkupButtonProvider;
import com.mishiranu.dashchan.ui.posting.text.NameEditWatcher;
import com.mishiranu.dashchan.ui.posting.text.QuoteEditWatcher;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DropdownView;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

public class PostingActivity extends StateActivity implements View.OnClickListener, View.OnFocusChangeListener,
		ServiceConnection, PostingService.Callback, CaptchaForm.Callback, AsyncManager.Callback,
		ReadCaptchaTask.Callback, PostingDialog.Callback {
	private String chanName;
	private String boardName;
	private String threadNumber;
	private CommentEditor commentEditor;

	private ChanConfiguration.Posting postingConfiguration;
	private List<Pair<String, String>> userIconItems;
	private List<Pair<String, String>> attachmentRatingItems;

	private String captchaType;
	private ChanPerformer.CaptchaState captchaState;
	private ChanPerformer.CaptchaData captchaData;
	private String loadedCaptchaType;
	private ChanConfiguration.Captcha.Input loadedCaptchaInput;
	private ChanConfiguration.Captcha.Validity loadedCaptchaValidity;
	private Bitmap captchaImage;
	private boolean captchaLarge;
	private boolean captchaBlackAndWhite;
	private long captchaLoadTime;

	private boolean storeDraftOnFinish = true;

	private ResizingScrollView scrollView;
	private EditText commentView;
	private CheckBox sageCheckBox;
	private CheckBox spoilerCheckBox;
	private CheckBox originalPosterCheckBox;
	private View checkBoxParent;
	private LinearLayout attachmentContainer;
	private EditText nameView;
	private EditText emailView;
	private EditText passwordView;
	private EditText subjectView;
	private DropdownView iconView;
	private View personalDataBlock;
	private ViewGroup textFormatView;

	private CommentEditWatcher commentEditWatcher;
	private final CaptchaForm captchaForm = new CaptchaForm(this);
	private Button sendButton;
	private int attachmentColumnCount;

	private final ArrayList<AttachmentHolder> attachments = new ArrayList<>();

	private final ClickableToast.Holder clickableToastHolder = new ClickableToast.Holder(this);

	private PostingService.Binder postingServiceBinder;

	private boolean sendButtonEnabled = true;

	private static final String EXTRA_SAVED_POST_DRAFT = "ExtraSavedPostDraft";
	private static final String EXTRA_SAVED_CAPTCHA = "ExtraSavedCaptcha";

	private static final String TASK_READ_CAPTCHA = "read_captcha";

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if (C.API_LOLLIPOP) {
			requestWindowFeature(Window.FEATURE_NO_TITLE);
			Configuration configuration = getResources().getConfiguration();
			if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT
					|| configuration.smallestScreenWidthDp <= 360 && !C.API_MARSHMALLOW) {
				requestWindowFeature(Window.FEATURE_ACTION_MODE_OVERLAY);
			}
		}
		ResourceUtils.applyPreferredTheme(this);
		super.onCreate(savedInstanceState);
		bindService(new Intent(this, PostingService.class), this, BIND_AUTO_CREATE);
		chanName = getIntent().getStringExtra(C.EXTRA_CHAN_NAME);
		boardName = getIntent().getStringExtra(C.EXTRA_BOARD_NAME);
		threadNumber = getIntent().getStringExtra(C.EXTRA_THREAD_NUMBER);
		if (chanName == null) {
			throw new IllegalStateException();
		}
		ChanConfiguration chanConfiguration = ChanConfiguration.get(chanName);
		ChanConfiguration.Posting posting = chanConfiguration.safe().obtainPosting(boardName, threadNumber == null);
		if (posting == null) {
			finish();
			return;
		}
		DraftsStorage draftsStorage = DraftsStorage.getInstance();
		postingConfiguration = posting;
		captchaType = chanConfiguration.getCaptchaType();
		ChanMarkup markup = ChanMarkup.get(chanName);
		commentEditor = markup.safe().obtainCommentEditor(boardName);
		Configuration configuration = getResources().getConfiguration();
		boolean hugeCaptcha = Preferences.isHugeCaptcha();
		boolean longLayout = configuration.screenWidthDp >= 480;

		setContentView(R.layout.activity_posting);
		ClickableToast.register(clickableToastHolder);
		if (C.API_LOLLIPOP) {
			Toolbar toolbar = findViewById(R.id.toolbar);
			setActionBar(toolbar);
		}
		getActionBar().setDisplayHomeAsUpEnabled(true);
		scrollView = findViewById(R.id.scroll_view);
		commentView = findViewById(R.id.comment);
		sageCheckBox = findViewById(R.id.sage_checkbox);
		spoilerCheckBox = findViewById(R.id.spoiler_checkbox);
		originalPosterCheckBox = findViewById(R.id.original_poster_checkbox);
		checkBoxParent = findViewById(R.id.checkbox_parent);
		nameView = findViewById(R.id.name);
		emailView = findViewById(R.id.email);
		passwordView = findViewById(R.id.password);
		subjectView = findViewById(R.id.subject);
		iconView = findViewById(R.id.icon);
		personalDataBlock = findViewById(R.id.personal_data_block);
		attachmentContainer = findViewById(R.id.attachment_container);
		commentView.setOnFocusChangeListener(this);
		TextView tripcodeWarning = findViewById(R.id.personal_tripcode_warning);
		TextView remainingCharacters = findViewById(R.id.remaining_characters);
		nameView.addTextChangedListener(new NameEditWatcher(posting.allowName && !posting.allowTripcode,
				nameView, tripcodeWarning, () -> scrollView.postResizeComment()));
		commentEditWatcher = new CommentEditWatcher(postingConfiguration,
				commentView, remainingCharacters, () -> scrollView.postResizeComment(),
				() -> DraftsStorage.getInstance().store(obtainPostDraft()));
		commentView.addTextChangedListener(commentEditWatcher);
		commentView.addTextChangedListener(new QuoteEditWatcher(this));
		textFormatView = findViewById(R.id.text_format_view);
		updatePostingConfiguration(true, false, false);
		new MarkupButtonsBuilder();

		boolean longFooter = longLayout && !hugeCaptcha;
		int resId = longFooter ? R.layout.activity_posting_footer_long : R.layout.activity_posting_footer_common;
		FrameLayout footerContainer = findViewById(R.id.footer_container);
		getLayoutInflater().inflate(resId, footerContainer);
		View captchaInputParentView = footerContainer.findViewById(R.id.captcha_input_parent);
		EditText captchaInputView = footerContainer.findViewById(R.id.captcha_input);
		ChanConfiguration.Captcha captcha = chanConfiguration.safe().obtainCaptcha(captchaType);
		captchaForm.setupViews(footerContainer, captchaInputParentView, captchaInputView, !longFooter, captcha);
		sendButton = footerContainer.findViewById(R.id.send_button);
		sendButton.setOnClickListener(this);
		attachmentColumnCount = configuration.screenWidthDp >= 960 ? 4 : configuration.screenWidthDp >= 480 ? 2 : 1;

		StringBuilder builder = new StringBuilder();
		int commentCarriage = 0;

		DraftsStorage.PostDraft postDraft;
		if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_SAVED_POST_DRAFT)) {
			try {
				postDraft = DraftsStorage.PostDraft.fromJsonObject(new JSONObject(savedInstanceState
						.getString(EXTRA_SAVED_POST_DRAFT)));
			} catch (JSONException e) {
				postDraft = null;
			}
		} else {
			postDraft = draftsStorage.getPostDraft(chanName, boardName, threadNumber);
		}
		if (postDraft != null) {
			if (!StringUtils.isEmpty(postDraft.comment)) {
				builder.append(postDraft.comment);
				commentCarriage = postDraft.commentCarriage;
			}
			ArrayList<DraftsStorage.AttachmentDraft> attachmentDrafts = postDraft.attachmentDrafts;
			if (attachmentDrafts != null && !attachmentDrafts.isEmpty()) {
				for (DraftsStorage.AttachmentDraft attachmentDraft : attachmentDrafts) {
					addAttachment(attachmentDraft.hash, attachmentDraft.name, attachmentDraft.rating,
							attachmentDraft.optionUniqueHash, attachmentDraft.optionRemoveMetadata,
							attachmentDraft.optionRemoveFileName, attachmentDraft.optionSpoiler,
							attachmentDraft.reencoding);
				}
			}
			nameView.setText(postDraft.name);
			emailView.setText(postDraft.email);
			passwordView.setText(postDraft.password);
			subjectView.setText(postDraft.subject);
			sageCheckBox.setChecked(postDraft.optionSage);
			spoilerCheckBox.setChecked(postDraft.optionSpoiler);
			originalPosterCheckBox.setChecked(postDraft.optionOriginalPoster);
			if (userIconItems != null) {
				int index = 0;
				if (postDraft.userIcon != null) {
					for (int i = 0; i < userIconItems.size(); i++) {
						if (postDraft.userIcon.equals(userIconItems.get(i).first)) {
							index = i + 1;
							break;
						}
					}
				}
				iconView.setSelection(index);
			}
		}

		boolean captchaRestoreSuccess = false;
		if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_SAVED_CAPTCHA)) {
			DraftsStorage.CaptchaDraft captchaDraft = savedInstanceState.getParcelable(EXTRA_SAVED_CAPTCHA);
			if (captchaDraft.captchaState != null) {
				captchaLoadTime = captchaDraft.loadTime;
				showCaptcha(captchaDraft.captchaState, captchaDraft.captchaData, captchaDraft.loadedCaptchaType,
						captchaDraft.loadedInput, captchaDraft.loadedValidity, captchaDraft.image,
						captchaDraft.large, captchaDraft.blackAndWhite);
				captchaForm.setText(captchaDraft.text);
				captchaRestoreSuccess = true;
			}
		} else {
			DraftsStorage.CaptchaDraft captchaDraft = draftsStorage.getCaptchaDraft(chanName);
			if (captchaDraft != null && captchaDraft.loadedCaptchaType == null) {
				captchaLoadTime = captchaDraft.loadTime;
				ChanConfiguration.Captcha.Validity captchaValidity = captcha.validity;
				if (captchaValidity == null) {
					captchaValidity = ChanConfiguration.Captcha.Validity.SHORT_LIFETIME;
				}
				if (captchaDraft.loadedValidity != null) {
					ChanConfiguration.Captcha.Validity loadedCaptchaValidity = captchaDraft.loadedValidity;
					if (captchaDraft.captchaState != ChanPerformer.CaptchaState.CAPTCHA ||
							captchaValidity.compareTo(loadedCaptchaValidity) >= 0) {
						// Allow only reducing of validity
						captchaValidity = loadedCaptchaValidity;
					}
				}
				boolean canLoadState = false;
				switch (captchaValidity) {
					case SHORT_LIFETIME: {
						canLoadState = false;
						break;
					}
					case IN_THREAD: {
						canLoadState = StringUtils.equals(boardName, captchaDraft.boardName)
								&& StringUtils.equals(threadNumber, captchaDraft.threadNumber);
						break;
					}
					case IN_BOARD_SEPARATELY: {
						canLoadState = StringUtils.equals(boardName, captchaDraft.boardName)
								&& ((threadNumber == null) == (captchaDraft.threadNumber == null));
						break;
					}
					case IN_BOARD: {
						canLoadState = StringUtils.equals(boardName, captchaDraft.boardName);
						break;
					}
					case LONG_LIFETIME: {
						canLoadState = true;
						break;
					}
				}
				if (canLoadState && StringUtils.equals(captchaType, captchaDraft.captchaType)) {
					if (captchaDraft.captchaState == ChanPerformer.CaptchaState.CAPTCHA && captchaDraft.image != null) {
						showCaptcha(ChanPerformer.CaptchaState.CAPTCHA, captchaDraft.captchaData, null,
								captchaDraft.loadedInput, captchaDraft.loadedValidity,
								captchaDraft.image, captchaDraft.large, captchaDraft.blackAndWhite);
						captchaForm.setText(captchaDraft.text);
						captchaRestoreSuccess = true;
					} else if (canLoadState && (captchaDraft.captchaState == ChanPerformer.CaptchaState.SKIP
							|| captchaDraft.captchaState == ChanPerformer.CaptchaState.PASS)) {
						showCaptcha(captchaDraft.captchaState, captchaDraft.captchaData, null, null,
								captchaDraft.loadedValidity, null, false, false);
						captchaRestoreSuccess = true;
					}
				}
			}
		}

		Parcelable[] parcelableArray = savedInstanceState != null ? null
				: getIntent().getParcelableArrayExtra(C.EXTRA_REPLY_DATA);
		if (parcelableArray != null) {
			boolean onlyLinks = true;
			for (Parcelable parcelable : parcelableArray) {
				Replyable.ReplyData data = (Replyable.ReplyData) parcelable;
				if (!StringUtils.isEmpty(data.comment)) {
					onlyLinks = false;
					break;
				}
			}
			for (int i = 0; i < parcelableArray.length; i++) {
				boolean lastLink = i == parcelableArray.length - 1;
				Replyable.ReplyData data = (Replyable.ReplyData) parcelableArray[i];
				String postNumber = data.postNumber;
				String comment = data.comment;
				if (!StringUtils.isEmpty(postNumber)) {
					String link = ">>" + postNumber;
					// Check if user replies to the same post
					int index = builder.lastIndexOf(link, commentCarriage);
					if (index < 0 || index < commentCarriage && commentCarriage <= builder.length() &&
							builder.substring(index, commentCarriage).contains("\n>>")) {
						boolean afterSpace = false; // If user wants to add link at the same line
						if (commentCarriage > 0 && commentCarriage <= builder.length()) {
							char charBefore = builder.charAt(commentCarriage - 1);
							if (charBefore != '\n') {
								if (charBefore == ' ' && onlyLinks) {
									afterSpace = true;
								} else {
									// Ensure free line before link
									builder.insert(commentCarriage++, '\n');
								}
							}
						}
						builder.insert(commentCarriage, link);
						commentCarriage += link.length();
						if (afterSpace) {
							if (!lastLink) {
								builder.insert(commentCarriage, ", ");
								commentCarriage += 2;
							}
						} else {
							builder.insert(commentCarriage++, '\n');
							if (commentCarriage < builder.length()) {
								if (builder.charAt(commentCarriage) != '\n') {
									// Ensure free line for typing
									builder.insert(commentCarriage, '\n');
								}
							}
						}
					}
				}
				if (!StringUtils.isEmpty(comment)) {
					if (commentCarriage > 0 && commentCarriage <= builder.length() &&
							builder.charAt(commentCarriage - 1) != '\n') {
						builder.insert(commentCarriage++, '\n');
					}
					// Remove links in the beginning of the post
					comment = comment.replaceAll("(^|\n)(>>\\d+(\n|\\s)?)+", "$1");
					comment = comment.replaceAll("(\n+)", "$1> ");
					builder.insert(commentCarriage, "> ");
					commentCarriage += 2;
					builder.insert(commentCarriage, comment);
					commentCarriage += comment.length();
					builder.insert(commentCarriage++, '\n');
				}
			}
		}

		commentView.setText(builder);
		commentView.setSelection(commentCarriage);
		commentView.requestFocus();
		setTitle(StringUtils.isEmpty(threadNumber) ? R.string.text_new_thread : R.string.text_new_post);
		if (!captchaRestoreSuccess) {
			refreshCaptcha(false, true, false);
		}

		PostingService.FailResult failResult = savedInstanceState != null ? null
				: (PostingService.FailResult) getIntent().getSerializableExtra(C.EXTRA_FAIL_RESULT);
		if (failResult != null) {
			onSendPostFail(failResult.errorItem, failResult.extra, failResult.captchaError, failResult.keepCaptcha);
		}

		PostingDialog.bindCallback(this, AttachmentOptionsDialog.TAG, this);
		PostingDialog.bindCallback(this, AttachmentRatingDialog.TAG, this);
		PostingDialog.bindCallback(this, AttachmentWarningDialog.TAG, this);
		PostingDialog.bindCallback(this, ReencodingDialog.TAG, this);
		PostingDialog.bindCallback(this, SendPostFailDetailsDialog.TAG, this);
	}

	private DraftsStorage.PostDraft obtainPostDraft() {
		ArrayList<DraftsStorage.AttachmentDraft> attachmentDrafts = null;
		if (attachments.size() > 0) {
			attachmentDrafts = new ArrayList<>(attachments.size());
			for (AttachmentHolder holder : attachments) {
				attachmentDrafts.add(new DraftsStorage.AttachmentDraft(holder.hash, holder.name, holder.rating,
						holder.optionUniqueHash, holder.optionRemoveMetadata, holder.optionRemoveFileName,
						holder.optionSpoiler, holder.reencoding));
			}
		}
		String subject = subjectView.getText().toString();
		String comment = commentView.getText().toString();
		int commentCarriage = commentView.getSelectionEnd();
		String name = nameView.getText().toString();
		String email = emailView.getText().toString();
		String password = passwordView.getText().toString();
		boolean optionSage = sageCheckBox.isChecked();
		boolean optionSpoiler = spoilerCheckBox.isChecked();
		boolean optionOriginalPoster = originalPosterCheckBox.isChecked();
		String userIcon = getUserIcon();
		return new DraftsStorage.PostDraft(chanName, boardName, threadNumber, name, email, password,
				subject, comment, commentCarriage, attachmentDrafts,
				optionSage, optionSpoiler, optionOriginalPoster, userIcon);
	}

	private DraftsStorage.CaptchaDraft obtainCaptchaDraft() {
		String input = captchaForm.getInput();
		return new DraftsStorage.CaptchaDraft(captchaType, captchaState, captchaData, loadedCaptchaType,
				loadedCaptchaInput, loadedCaptchaValidity, input, captchaImage, captchaLarge,
				captchaBlackAndWhite, captchaLoadTime, boardName, threadNumber);
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		try {
			outState.putString(EXTRA_SAVED_POST_DRAFT, obtainPostDraft().toJsonObject().toString());
		} catch (JSONException e) {
			// Invalid data, ignore exception
		}
		outState.putParcelable(EXTRA_SAVED_CAPTCHA, obtainCaptchaDraft());
	}

	@Override
	protected void onResume() {
		super.onResume();
		clickableToastHolder.onResume();
		ForegroundManager.register(this);
		DraftsStorage draftsStorage = DraftsStorage.getInstance();
		ArrayList<DraftsStorage.AttachmentDraft> futureAttachmentDrafts = draftsStorage.getFutureAttachmentDrafts();
		if (!futureAttachmentDrafts.isEmpty()) {
			ArrayList<Pair<String, String>> attachmentsToAdd = new ArrayList<>(futureAttachmentDrafts.size());
			for (DraftsStorage.AttachmentDraft attachmentDraft : futureAttachmentDrafts) {
				attachmentsToAdd.add(new Pair<>(attachmentDraft.hash, attachmentDraft.name));
			}
			handleAttachmentsToAdd(attachmentsToAdd, futureAttachmentDrafts.size());
			DraftsStorage.getInstance().consumeFutureAttachmentDrafts();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		clickableToastHolder.onPause();
		ForegroundManager.unregister(this);
	}

	@Override
	protected void onFinish() {
		super.onFinish();
		if (postingServiceBinder != null) {
			postingServiceBinder.unregister(this);
			postingServiceBinder = null;
		}
		unbindService(this);
		ClickableToast.unregister(clickableToastHolder);
		if (storeDraftOnFinish) {
			DraftsStorage draftsStorage = DraftsStorage.getInstance();
			draftsStorage.store(obtainPostDraft());
			draftsStorage.store(chanName, obtainCaptchaDraft());
		}
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		postingServiceBinder = (PostingService.Binder) service;
		postingServiceBinder.register(this, chanName, boardName, threadNumber);
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		if (postingServiceBinder != null) {
			postingServiceBinder.unregister(this);
			postingServiceBinder = null;
		}
	}

	@Override
	public void onClick(View v) {
		if (v == sendButton) {
			executeSendPost();
		}
	}

	@Override
	public void onRefreshCapctha(boolean forceRefresh) {
		refreshCaptcha(forceRefresh, false, true);
	}

	@Override
	public void onConfirmCaptcha() {
		executeSendPost();
	}

	@Override
	public void onFocusChange(View v, boolean hasFocus) {
		if (v == commentView) {
			updateFocusButtons(hasFocus);
		}
	}

	private void updatePostingConfiguration(boolean views, boolean attachmentOptions, boolean attachmentCount) {
		ChanConfiguration.Posting posting = postingConfiguration;
		if (views) {
			userIconItems = posting.userIcons.size() > 0 ? posting.userIcons : null;
			if (userIconItems != null) {
				String lastUserIcon = getUserIcon();
				int lastUserIconIndex = -1;
				ArrayList<String> items = new ArrayList<>();
				items.add(getString(R.string.text_no_icon));
				for (int i = 0; i < userIconItems.size(); i++) {
					Pair<String, String> iconItem = userIconItems.get(i);
					items.add(iconItem.second);
					if (StringUtils.equals(lastUserIcon, iconItem.first)) {
						lastUserIconIndex = i;
					}
				}
				iconView.setItems(items);
				iconView.setVisibility(View.VISIBLE);
				iconView.setSelection(lastUserIconIndex + 1);
			} else {
				iconView.setVisibility(View.GONE);
			}
			boolean needPassword = false;
			ChanConfiguration chanConfiguration = ChanConfiguration.get(chanName);
			ChanConfiguration.Board board = chanConfiguration.safe().obtainBoard(boardName);
			if (board.allowDeleting) {
				ChanConfiguration.Deleting deleting = chanConfiguration.safe().obtainDeleting(boardName);
				needPassword = deleting != null && deleting.password;
			}
			nameView.setVisibility(posting.allowName ? View.VISIBLE : View.GONE);
			emailView.setVisibility(posting.allowEmail ? View.VISIBLE : View.GONE);
			passwordView.setVisibility(needPassword ? View.VISIBLE : View.GONE);
			subjectView.setVisibility(posting.allowSubject ? View.VISIBLE : View.GONE);
			sageCheckBox.setVisibility(posting.optionSage ? View.VISIBLE : View.GONE);
			spoilerCheckBox.setVisibility(posting.optionSpoiler ? View.VISIBLE : View.GONE);
			originalPosterCheckBox.setVisibility(posting.optionOriginalPoster ? View.VISIBLE : View.GONE);
			checkBoxParent.setVisibility(posting.optionSage || posting.optionSpoiler || posting.optionOriginalPoster
					? View.VISIBLE : View.GONE);
			boolean showPersonalDataBlock = !Preferences.isHidePersonalData();
			if (showPersonalDataBlock) {
				showPersonalDataBlock = posting.allowName || posting.allowEmail || needPassword || userIconItems != null;
			}
			personalDataBlock.setVisibility(showPersonalDataBlock ? View.VISIBLE : View.GONE);
			commentEditWatcher.updateConfiguration(postingConfiguration);
		}
		if (attachmentOptions || attachmentCount) {
			if (attachmentOptions) {
				attachmentRatingItems = posting.attachmentRatings.size() > 0 ? posting.attachmentRatings : null;
			}
			if (attachmentCount) {
				if (attachments.size() > posting.attachmentCount) {
					attachments.subList(posting.attachmentCount, attachments.size()).clear();
				}
			}
			invalidateAttachments(attachmentCount);
			if (attachmentCount) {
				invalidateOptionsMenu();
			}
		}
	}

	private boolean compareListOfPairs(List<Pair<String, String>> first, List<Pair<String, String>> second) {
		if (first.size() != second.size()) {
			return false;
		}
		for (int i = 0; i < first.size(); i++) {
			if (!StringUtils.equals(first.get(i).first, first.get(i).second)
					|| !StringUtils.equals(first.get(i).second, first.get(i).second)) {
				return false;
			}
		}
		return false;
	}

	private void updatePostingConfigurationIfNeeded() {
		ChanConfiguration.Posting oldPosting = postingConfiguration;
		ChanConfiguration.Posting newPosting = ChanConfiguration.get(chanName).safe()
				.obtainPosting(boardName, threadNumber == null);
		if (newPosting == null) {
			return;
		}
		boolean views = oldPosting.allowName != newPosting.allowName || oldPosting.allowEmail != newPosting.allowEmail
				|| oldPosting.allowTripcode != newPosting.allowTripcode
				|| oldPosting.allowSubject != newPosting.allowSubject || oldPosting.optionSage != newPosting.optionSage
				|| oldPosting.optionSpoiler != newPosting.optionSpoiler
				|| oldPosting.optionOriginalPoster != newPosting.optionOriginalPoster
				|| oldPosting.maxCommentLength != newPosting.maxCommentLength
				|| !StringUtils.equals(oldPosting.maxCommentLengthEncoding, newPosting.maxCommentLengthEncoding)
				|| !compareListOfPairs(oldPosting.userIcons, newPosting.userIcons);
		boolean attachmentOptions = oldPosting.attachmentSpoiler != newPosting.attachmentSpoiler
				|| !compareListOfPairs(oldPosting.attachmentRatings, newPosting.attachmentRatings);
		boolean attachmentCount = oldPosting.attachmentCount != newPosting.attachmentCount;
		if (views || attachmentOptions || attachmentCount) {
			postingConfiguration = newPosting;
			updatePostingConfiguration(views, attachmentOptions, attachmentCount);
			scrollView.postResizeComment();
		}
	}

	private String getUserIcon() {
		if (userIconItems != null) {
			int position = iconView.getSelectedItemPosition() - 1;
			if (position >= 0 && position < userIconItems.size()) {
				return userIconItems.get(position).first;
			}
		}
		return null;
	}

	private static final int OPTIONS_MENU_ATTACH = 0;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		ActionIconSet set = new ActionIconSet(this);
		menu.add(0, OPTIONS_MENU_ATTACH, 0, R.string.action_attach).setIcon(set.getId(R.attr.actionAttach))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(OPTIONS_MENU_ATTACH).setVisible(attachments.size() < postingConfiguration.attachmentCount);
		return super.onPrepareOptionsMenu(menu);
	}

	private void handleMimeTypeGroup(ArrayList<String> list, Collection<String> mimeTypes, String mimeTypeGroup) {
		String allSubMimeTypes = mimeTypeGroup + "*";
		if (mimeTypes.contains(allSubMimeTypes)) {
			list.add(allSubMimeTypes);
		}
		for (String mimeType : mimeTypes) {
			if (mimeType.startsWith(mimeTypeGroup) && !allSubMimeTypes.equals(mimeType)) {
				list.add(mimeType);
			}
		}
	}

	private ArrayList<String> buildMimeTypeList(Collection<String> mimeTypes) {
		ArrayList<String> list = new ArrayList<>();
		handleMimeTypeGroup(list, mimeTypes, "image/");
		handleMimeTypeGroup(list, mimeTypes, "video/");
		handleMimeTypeGroup(list, mimeTypes, "audio/");
		for (String mimeType : mimeTypes) {
			if (!list.contains(mimeType)) {
				list.add(mimeType);
			}
		}
		return list;
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home: {
				finish();
				break;
			}
			case OPTIONS_MENU_ATTACH: {
				// SHOW_ADVANCED to show folder navigation
				Intent intent = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE)
						.putExtra("android.content.extra.SHOW_ADVANCED", true);
				StringBuilder typesBuilder = new StringBuilder();
				ArrayList<String> mimeTypes = buildMimeTypeList(postingConfiguration.attachmentMimeTypes);
				for (String type : mimeTypes) {
					if (typesBuilder.length() > 0) {
						typesBuilder.append(',');
					}
					typesBuilder.append(type);
				}
				if (typesBuilder.length() > 0) {
					intent.setType(typesBuilder.toString());
				}
				if (C.API_KITKAT) {
					if (mimeTypes.size() > 0) {
						intent.putExtra(Intent.EXTRA_MIME_TYPES, CommonUtils.toArray(mimeTypes, String.class));
					}
					intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
				}
				startActivityForResult(intent, C.REQUEST_CODE_ATTACH);
				break;
			}
		}
		return true;
	}

	private void updateFocusButtons(boolean commentFocused) {
		if (C.API_LOLLIPOP) {
			for (int i = 0; i < textFormatView.getChildCount(); i++) {
				textFormatView.getChildAt(i).setEnabled(commentFocused);
			}
		}
	}

	private final View.OnClickListener formatButtonClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			int what = (int) v.getTag();
			switch (what) {
				case ChanMarkup.TAG_QUOTE: {
					formatQuote();
					break;
				}
				default: {
					commentEditor.formatSelectedText(commentView, what);
					break;
				}
			}
			InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			if (inputMethodManager != null) {
				inputMethodManager.showSoftInput(commentView, 0);
			}
		}
	};

	private void updateSendButtonState() {
		sendButton.setEnabled(sendButtonEnabled && captchaState != null &&
				captchaState != ChanPerformer.CaptchaState.NEED_LOAD);
	}

	private String getTextIfVisible(EditText editText) {
		return editText.getVisibility() == View.VISIBLE ? StringUtils.nullIfEmpty(editText.getText().toString()) : null;
	}

	private boolean isCheckedIfVisible(CheckBox checkBox) {
		return checkBox.getVisibility() == View.VISIBLE && checkBox.isChecked();
	}

	private void executeSendPost() {
		if (postingServiceBinder == null) {
			return;
		}
		String subject = getTextIfVisible(subjectView);
		String comment = getTextIfVisible(commentView);
		String name = getTextIfVisible(nameView);
		String email = getTextIfVisible(emailView);
		String password = getTextIfVisible(passwordView);
		if (password == null) {
			password = Preferences.getPassword(chanName);
		}
		boolean optionSage = isCheckedIfVisible(sageCheckBox);
		boolean optionSpoiler = isCheckedIfVisible(spoilerCheckBox);
		boolean optionOriginalPoster = isCheckedIfVisible(originalPosterCheckBox);
		String userIcon = iconView.getVisibility() == View.VISIBLE ? getUserIcon() : null;
		ArrayList<ChanPerformer.SendPostData.Attachment> array = new ArrayList<>();
		DraftsStorage draftsStorage = DraftsStorage.getInstance();
		for (int i = 0; i < attachments.size(); i++) {
			AttachmentHolder data = attachments.get(i);
			String rating = data.rating;
			if (rating != null && attachmentRatingItems != null) {
				boolean found = false;
				for (Pair<String, String> pair : attachmentRatingItems) {
					if (rating.equals(pair.first)) {
						found = true;
						break;
					}
				}
				if (!found) {
					rating = null;
				}
			} else {
				rating = null;
			}
			if (attachmentRatingItems != null && rating == null) {
				rating = attachmentRatingItems.get(0).first;
			}
			FileHolder fileHolder = draftsStorage.getAttachmentDraftFileHolder(data.hash);
			if (fileHolder != null) {
				array.add(new ChanPerformer.SendPostData.Attachment(fileHolder, data.name, rating,
						data.optionUniqueHash, data.optionRemoveMetadata, data.optionRemoveFileName,
						postingConfiguration.attachmentSpoiler && data.optionSpoiler, data.reencoding));
			}
		}
		ChanPerformer.SendPostData.Attachment[] attachments = null;
		if (array.size() > 0) {
			attachments = CommonUtils.toArray(array, ChanPerformer.SendPostData.Attachment.class);
		}
		ChanPerformer.CaptchaData captchaData = this.captchaData;
		if (captchaData != null) {
			captchaData.put(ChanPerformer.CaptchaData.INPUT, captchaForm.getInput());
		}
		String captchaType = loadedCaptchaType != null ? loadedCaptchaType : this.captchaType;
		ChanPerformer.SendPostData data = new ChanPerformer.SendPostData(boardName, threadNumber,
				subject, comment, name, email, password, attachments, optionSage, optionSpoiler, optionOriginalPoster,
				userIcon, captchaType, captchaData, 15000, 45000);
		DraftsStorage.getInstance().store(obtainPostDraft());
		postingServiceBinder.executeSendPost(chanName, data);
		sendButtonEnabled = false;
		updateSendButtonState();
	}

	private ProgressDialog progressDialog;

	private final DialogInterface.OnCancelListener sendPostCancelListener = dialog -> {
		progressDialog = null;
		postingServiceBinder.cancelSendPost(chanName, boardName, threadNumber);
	};

	private final DialogInterface.OnClickListener sendPostMinimizeListener = (dialog, which) -> {
		progressDialog = null;
		finish();
	};

	private void dismissSendPost() {
		if (progressDialog != null) {
			progressDialog.dismiss();
		}
		progressDialog = null;
		sendButtonEnabled = true;
		updateSendButtonState();
	}

	@Override
	public void onSendPostStart(boolean progressMode) {
		// TODO Handle deprecation
		progressDialog = new ProgressDialog(this);
		progressDialog.setCancelable(true);
		progressDialog.setCanceledOnTouchOutside(false);
		if (progressMode) {
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setProgressNumberFormat("%1$d / %2$d KB");
		}
		progressDialog.setOnCancelListener(sendPostCancelListener);
		progressDialog.setButton(ProgressDialog.BUTTON_POSITIVE, getString(R.string.action_minimize),
				sendPostMinimizeListener);
		onSendPostChangeProgressState(progressMode, SendPostTask.ProgressState.CONNECTING, -1, -1);
		progressDialog.show();
	}

	@Override
	public void onSendPostChangeProgressState(boolean progressMode, SendPostTask.ProgressState progressState,
			int attachmentIndex, int attachmentsCount) {
		// TODO Handle deprecation
		if (progressDialog != null) {
			switch (progressState) {
				case CONNECTING: {
					progressDialog.setMax(1);
					progressDialog.setIndeterminate(true);
					progressDialog.setMessage(getString(R.string.message_sending));
					break;
				}
				case SENDING: {
					progressDialog.setIndeterminate(false);
					if (progressMode) {
						progressDialog.setMessage(getString(R.string.message_sending_index_format, attachmentIndex + 1,
								attachmentsCount));
					} else {
						progressDialog.setMessage(getString(R.string.message_sending));
					}
					break;
				}
				case PROCESSING: {
					progressDialog.setIndeterminate(false);
					progressDialog.setMessage(getString(R.string.message_processing_data));
					break;
				}
			}
		}
	}

	@Override
	public void onSendPostChangeProgressValue(int progress, int progressMax) {
		// TODO Handle deprecation
		if (progressDialog != null) {
			progressDialog.setMax(progressMax);
			progressDialog.setProgress(progress);
		}
	}

	@Override
	public void onSendPostSuccess() {
		dismissSendPost();
		storeDraftOnFinish = false;
		finish();
	}

	@Override
	public void onSendPostFail(ErrorItem errorItem, Serializable extra, boolean captchaError, boolean keepCaptcha) {
		dismissSendPost();
		if (extra != null) {
			ClickableToast.show(this, errorItem.toString(), getString(R.string.action_details), () -> {
				SendPostFailDetailsDialog dialog = new SendPostFailDetailsDialog(extra);
				dialog.bindCallback(PostingActivity.this);
				dialog.show(getSupportFragmentManager(), SendPostFailDetailsDialog.TAG);
			}, false);
		} else {
			ClickableToast.show(this, errorItem.toString());
		}
		if (errorItem.httpResponseCode == 0 && !keepCaptcha) {
			refreshCaptcha(false, !captchaError, true);
		}
		updatePostingConfigurationIfNeeded();
	}

	@Override
	public void onSendPostCancel() {
		dismissSendPost();
	}

	private static final String EXTRA_FORCE_CAPTCHA = "forceCaptcha";
	private static final String EXTRA_MAY_SHOW_LOAD_BUTTON = "mayShowLoadButton";

	private void refreshCaptcha(boolean forceCaptcha, boolean mayShowLoadButton, boolean restart) {
		captchaState = null;
		loadedCaptchaType = null;
		captchaLoadTime = 0L;
		updateSendButtonState();
		captchaForm.showLoading();
		HashMap<String, Object> extra = new HashMap<>();
		extra.put(EXTRA_FORCE_CAPTCHA, forceCaptcha);
		extra.put(EXTRA_MAY_SHOW_LOAD_BUTTON, mayShowLoadButton);
		AsyncManager.get(this).startTask(TASK_READ_CAPTCHA, this, extra, restart);
	}

	private static class ReadCaptchaHolder extends AsyncManager.Holder implements ReadCaptchaTask.Callback {
		@Override
		public void onReadCaptchaSuccess(ChanPerformer.CaptchaState captchaState, ChanPerformer.CaptchaData captchaData,
				String captchaType, ChanConfiguration.Captcha.Input input, ChanConfiguration.Captcha.Validity validity,
				Bitmap image, boolean large, boolean blackAndWhite) {
			storeResult("onReadCaptchaSuccess", captchaState, captchaData, captchaType, input, validity,
					image, large, blackAndWhite);
		}

		@Override
		public void onReadCaptchaError(ErrorItem errorItem) {
			storeResult("onReadCaptchaError", errorItem);
		}
	}

	@Override
	public AsyncManager.Holder onCreateAndExecuteTask(String name, HashMap<String, Object> extra) {
		boolean forceCaptcha = (boolean) extra.get(EXTRA_FORCE_CAPTCHA);
		boolean mayShowLoadButton = (boolean) extra.get(EXTRA_MAY_SHOW_LOAD_BUTTON);
		String[] captchaPass = forceCaptcha ? null : Preferences.getCaptchaPass(chanName);
		ReadCaptchaHolder holder = new ReadCaptchaHolder();
		ReadCaptchaTask task = new ReadCaptchaTask(holder, null, captchaType, null, captchaPass,
				mayShowLoadButton, chanName, boardName, threadNumber);
		task.executeOnExecutor(ReadCaptchaTask.THREAD_POOL_EXECUTOR);
		return holder.attach(task);
	}

	@Override
	public void onFinishTaskExecution(String name, AsyncManager.Holder holder) {
		String methodName = holder.nextArgument();
		if ("onReadCaptchaSuccess".equals(methodName)) {
			ChanPerformer.CaptchaState captchaState = holder.nextArgument();
			ChanPerformer.CaptchaData captchaData = holder.nextArgument();
			String captchaType = holder.nextArgument();
			ChanConfiguration.Captcha.Input input = holder.nextArgument();
			ChanConfiguration.Captcha.Validity validity = holder.nextArgument();
			Bitmap image = holder.nextArgument();
			boolean large = holder.nextArgument();
			boolean blackAndWhite = holder.nextArgument();
			onReadCaptchaSuccess(captchaState, captchaData, captchaType, input, validity, image, large, blackAndWhite);
		} else if ("onReadCaptchaError".equals(methodName)) {
			ErrorItem errorItem = holder.nextArgument();
			onReadCaptchaError(errorItem);
		}
	}

	@Override
	public void onRequestTaskCancel(String name, Object task) {
		((ReadCaptchaTask) task).cancel();
	}

	@Override
	public void onReadCaptchaSuccess(ChanPerformer.CaptchaState captchaState, ChanPerformer.CaptchaData captchaData,
			String captchaType, ChanConfiguration.Captcha.Input input, ChanConfiguration.Captcha.Validity validity,
			Bitmap image, boolean large, boolean blackAndWhite) {
		captchaLoadTime = System.currentTimeMillis();
		showCaptcha(captchaState, captchaData, captchaType, input, validity, image, large, blackAndWhite);
		updatePostingConfigurationIfNeeded();
	}

	@Override
	public void onReadCaptchaError(ErrorItem errorItem) {
		ClickableToast.show(this, errorItem.toString());
		captchaForm.showError();
		updatePostingConfigurationIfNeeded();
	}

	private void showCaptcha(ChanPerformer.CaptchaState captchaState, ChanPerformer.CaptchaData captchaData,
			String captchaType, ChanConfiguration.Captcha.Input input, ChanConfiguration.Captcha.Validity validity,
			Bitmap image, boolean large, boolean blackAndWhite) {
		this.captchaState = captchaState;
		if (captchaImage != null && captchaImage != image) {
			captchaImage.recycle();
		}
		this.captchaData = captchaData;
		captchaImage = image;
		captchaLarge = large;
		captchaBlackAndWhite = blackAndWhite;
		loadedCaptchaType = captchaType;
		if (captchaType != null) {
			ChanConfiguration.Captcha captcha = ChanConfiguration.get(chanName).safe().obtainCaptcha(captchaType);
			if (input == null) {
				input = captcha.input;
			}
			if (validity == null) {
				validity = captcha.validity;
			}
		}
		loadedCaptchaInput = input;
		loadedCaptchaValidity = validity;
		boolean invertColors = blackAndWhite && !GraphicsUtils.isLight(ResourceUtils.getColor(this,
				android.R.attr.windowBackground));
		captchaForm.showCaptcha(captchaState, input, image, large, invertColors);
		if (scrollView.getScrollY() + scrollView.getHeight() >= scrollView.getChildAt(0).getHeight()) {
			scrollView.post(() -> scrollView.setScrollY(Math.max(scrollView.getChildAt(0).getHeight()
					- scrollView.getHeight(), 0)));
		}
		updateSendButtonState();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
				case C.REQUEST_CODE_ATTACH: {
					LinkedHashSet<Uri> uris = new LinkedHashSet<>();
					Uri dataUri = data.getData();
					if (dataUri != null) {
						uris.add(dataUri);
					}
					if (C.API_KITKAT) {
						ClipData clipData = data.getClipData();
						if (clipData != null) {
							for (int i = 0; i < clipData.getItemCount(); i++) {
								ClipData.Item item = clipData.getItemAt(i);
								Uri uri = item.getUri();
								if (uri != null) {
									uris.add(uri);
								}
							}
						}
					}
					ArrayList<Pair<String, String>> attachmentsToAdd = new ArrayList<>();
					for (Uri uri : uris) {
						FileHolder fileHolder = FileHolder.obtain(this, uri);
						if (fileHolder != null) {
							String hash = DraftsStorage.getInstance().store(fileHolder);
							if (hash != null) {
								attachmentsToAdd.add(new Pair<>(hash, fileHolder.getName()));
							}
						}
					}
					handleAttachmentsToAdd(attachmentsToAdd, uris.size());
					break;
				}
			}
		}
	}

	private void handleAttachmentsToAdd(ArrayList<Pair<String, String>> attachmentsToAdd, int addedCount) {
		int oldCount = attachments.size();
		for (Pair<String, String> attachmentToAdd : attachmentsToAdd) {
			if (attachments.size() < postingConfiguration.attachmentCount) {
				addAttachment(attachmentToAdd.first, attachmentToAdd.second);
			}
		}
		int newCount = attachments.size() - oldCount;
		if (newCount > 0) {
			DraftsStorage.getInstance().store(obtainPostDraft());
		}
		int errorCount = addedCount - newCount;
		if (errorCount > 0) {
			ClickableToast.show(this, getResources().getQuantityString(R.plurals
					.message_file_attach_error_format, errorCount, errorCount));
		}
	}

	@Override
	public AttachmentHolder getAttachmentHolder(int index) {
		return index >= 0 && index < attachments.size() ? attachments.get(index) : null;
	}

	@Override
	public List<Pair<String, String>> getAttachmentRatingItems() {
		return attachmentRatingItems;
	}

	@Override
	public ChanConfiguration.Posting getPostingConfiguration() {
		return postingConfiguration;
	}

	private final View.OnClickListener attachmentOptionsListener = v -> {
		AttachmentHolder holder = (AttachmentHolder) v.getTag();
		int attachmentIndex = attachments.indexOf(holder);
		AttachmentOptionsDialog dialog = new AttachmentOptionsDialog(attachmentIndex);
		dialog.bindCallback(this).show(getSupportFragmentManager(), AttachmentOptionsDialog.TAG);
	};

	private final View.OnClickListener attachmentWarningListener = v -> {
		AttachmentHolder holder = (AttachmentHolder) v.getTag();
		int attachmentIndex = attachments.indexOf(holder);
		AttachmentWarningDialog dialog = new AttachmentWarningDialog(attachmentIndex);
		dialog.bindCallback(this).show(getSupportFragmentManager(), AttachmentWarningDialog.TAG);
	};

	private final View.OnClickListener attachmentRatingListener = v -> {
		AttachmentHolder holder = (AttachmentHolder) v.getTag();
		int attachmentIndex = attachments.indexOf(holder);
		AttachmentRatingDialog dialog = new AttachmentRatingDialog(attachmentIndex);
		dialog.bindCallback(this).show(getSupportFragmentManager(), AttachmentRatingDialog.TAG);
	};

	private final View.OnClickListener attachmentRemoveListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			AttachmentHolder holder = (AttachmentHolder) v.getTag();
			if (attachments.remove(holder)) {
				if (attachmentColumnCount == 1) {
					attachmentContainer.removeView(holder.view);
				} else {
					invalidateAttachments(true);
				}
				invalidateOptionsMenu();
				scrollView.postResizeComment();
				DraftsStorage.getInstance().store(obtainPostDraft());
			}
		}
	};

	private void invalidateAttachments(boolean clearContainer) {
		if (clearContainer) {
			attachmentContainer.removeAllViews();
		}
		for (int i = 0; i < attachments.size(); i++) {
			AttachmentHolder holder = attachments.get(i);
			if (clearContainer) {
				ViewUtils.removeFromParent(holder.view);
				addAttachmentViewToContainer(holder.view, i);
			}
			updateAttachmentConfiguration(holder);
		}
	}

	private void addAttachmentViewToContainer(View attachmentView, int position) {
		LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) attachmentView.getLayoutParams();
		if (attachmentColumnCount == 1) {
			layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
			layoutParams.weight = 0;
			layoutParams.leftMargin = 0;
			attachmentContainer.addView(attachmentView);
		} else {
			float density = ResourceUtils.obtainDensity(this);
			float paddingDp = 4f;
			layoutParams.width = 0;
			layoutParams.weight = 1;
			layoutParams.leftMargin = (int) (paddingDp * density);
			int row = position / attachmentColumnCount, column = position % attachmentColumnCount;
			LinearLayout subcontainer;
			View placeholder;
			if (column == 0) {
				subcontainer = new LinearLayout(this);
				attachmentContainer.addView(subcontainer, LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT);
				subcontainer.setOrientation(LinearLayout.HORIZONTAL);
				placeholder = new View(this);
				subcontainer.addView(placeholder, 0, LinearLayout.LayoutParams.MATCH_PARENT);
				subcontainer.setPadding(0, 0, (int) (paddingDp * density), 0);
				subcontainer.setGravity(Gravity.BOTTOM);
			} else {
				subcontainer = (LinearLayout) attachmentContainer.getChildAt(row);
				placeholder = subcontainer.getChildAt(subcontainer.getChildCount() - 1);
			}
			subcontainer.addView(attachmentView, column);
			layoutParams = ((LinearLayout.LayoutParams) placeholder.getLayoutParams());
			layoutParams.weight = attachmentColumnCount - column - 1;
			layoutParams.leftMargin = (int) (paddingDp * density * layoutParams.weight);
			placeholder.setVisibility(attachmentColumnCount == column + 1 ? View.GONE : View.VISIBLE);
		}
	}

	private AttachmentHolder addNewAttachment() {
		FrameLayout view = (FrameLayout) getLayoutInflater().inflate(R.layout.activity_posting_attachment,
				attachmentContainer, false);
		if (C.API_LOLLIPOP) {
			float density = ResourceUtils.obtainDensity(this);
			view.setForeground(new RoundedCornersDrawable((int) (2f * density), ResourceUtils.getColor(this,
					android.R.attr.windowBackground)));
		}
		addAttachmentViewToContainer(view, attachments.size());
		AttachmentHolder holder = new AttachmentHolder();
		holder.view = view;
		holder.fileName = view.findViewById(R.id.attachment_name);
		holder.fileSize = view.findViewById(R.id.attachment_size);
		holder.options = view.findViewById(R.id.attachment_options);
		holder.imageView = view.findViewById(R.id.attachment_preview);
		holder.imageView.setBackground(new TransparentTileDrawable(this, true));
		holder.warningButton = view.findViewById(R.id.attachment_warning);
		holder.warningButton.setOnClickListener(attachmentWarningListener);
		holder.warningButton.setTag(holder);
		holder.ratingButton = view.findViewById(R.id.attachment_rating);
		holder.ratingButton.setOnClickListener(attachmentRatingListener);
		holder.ratingButton.setTag(holder);
		holder.removeButton = view.findViewById(R.id.attachment_remove);
		holder.removeButton.setOnClickListener(attachmentRemoveListener);
		holder.removeButton.setTag(holder);
		holder.options.setOnClickListener(attachmentOptionsListener);
		holder.options.setTag(holder);
		attachments.add(holder);
		invalidateOptionsMenu();
		scrollView.postResizeComment();
		return holder;
	}

	private void addAttachment(String hash, String name) {
		addAttachment(hash, name, null, false, false, false, false, null);
	}

	private void addAttachment(String hash, String name, String rating, boolean optionUniqueHash,
			boolean optionRemoveMetadata, boolean optionRemoveFileName, boolean optionSpoiler,
			GraphicsUtils.Reencoding reencoding) {
		FileHolder fileHolder = DraftsStorage.getInstance().getAttachmentDraftFileHolder(hash);
		JpegData jpegData = fileHolder != null ? fileHolder.getJpegData() : null;
		AttachmentHolder holder = addNewAttachment();
		holder.hash = hash;
		holder.name = name;
		holder.rating = rating;
		holder.optionUniqueHash = optionUniqueHash;
		holder.optionRemoveMetadata = optionRemoveMetadata;
		holder.optionRemoveFileName = optionRemoveFileName;
		holder.optionSpoiler = optionSpoiler;
		holder.reencoding = reencoding;
		holder.fileName.setText(name);
		int size = fileHolder != null ? fileHolder.getSize() : 0;
		String fileSize = String.format(Locale.US, "%.2f", size / 1024f) + " KB";
		Bitmap bitmap = null;
		ChanLocator locator = ChanLocator.getDefault();
		DisplayMetrics metrics = getResources().getDisplayMetrics();
		int targetImageSize = Math.max(metrics.widthPixels, metrics.heightPixels);
		if (fileHolder != null) {
			if (fileHolder.isImage()) {
				try {
					bitmap = fileHolder.readImageBitmap(targetImageSize, false, false);
				} catch (OutOfMemoryError e) {
					// Ignore exception
				}
				fileSize += " " + fileHolder.getImageWidth() + '×' + fileHolder.getImageHeight();
			}
			if (bitmap == null) {
				if (locator.isVideoExtension(fileHolder.getName())) {
					MediaMetadataRetriever retriever = new MediaMetadataRetriever();
					FileHolder.Descriptor descriptor = null;
					try {
						descriptor = fileHolder.openDescriptor();
						retriever.setDataSource(descriptor.getFileDescriptor());
						Bitmap fullBitmap = retriever.getFrameAtTime(-1);
						if (fullBitmap != null) {
							bitmap = GraphicsUtils.reduceBitmapSize(fullBitmap, targetImageSize, true);
						}
					} catch (Exception | OutOfMemoryError e) {
						// Ignore exception
					} finally {
						retriever.release();
						IOUtils.close(descriptor);
					}
				}
			}
		}
		if (bitmap != null) {
			holder.imageView.setVisibility(View.VISIBLE);
			holder.imageView.setImageBitmap(bitmap);
			holder.view.getLayoutParams().height = (int) (128f * ResourceUtils.obtainDensity(this));
		}
		holder.fileSize.setText(fileSize);
		if (jpegData == null || !jpegData.hasExif) {
			holder.warningButton.setVisibility(View.GONE);
		}
		updateAttachmentConfiguration(holder);
	}

	private void updateAttachmentConfiguration(AttachmentHolder holder) {
		if (attachmentRatingItems != null) {
			if (holder.rating == null) {
				holder.rating = attachmentRatingItems.get(0).first;
			}
			holder.ratingButton.setVisibility(View.VISIBLE);
		} else {
			holder.ratingButton.setVisibility(View.GONE);
		}
	}

	private void formatQuote() {
		Editable editable = commentView.getText();
		String text = editable.toString();
		int selectionStart = commentView.getSelectionStart();
		int selectionEnd = commentView.getSelectionEnd();
		String selectedText = text.substring(selectionStart, selectionEnd);
		String oneSymbolBefore = text.substring(Math.max(selectionStart - 1, 0), selectionStart);
		if (selectedText.startsWith(">")) {
			String unQuotedText = selectedText.replaceFirst("> ?", "").replaceAll("(\n+)> ?", "$1");
			int diff = selectedText.length() - unQuotedText.length();
			editable.replace(selectionStart, selectionEnd, unQuotedText);
			commentView.setSelection(selectionStart, selectionEnd - diff);
		} else {
			String firstSymbol = oneSymbolBefore.length() == 0 || oneSymbolBefore.equals("\n") ? "" : "\n";
			String quotedText = firstSymbol + "> " + selectedText.replaceAll("(\n+)", "$1> ");
			int diff = quotedText.length() - selectedText.length();
			editable.replace(selectionStart, selectionEnd, quotedText);
			int newStart = selectionStart + firstSymbol.length();
			int newEnd = selectionEnd + diff;
			if (newEnd - newStart <= 2) {
				newStart = newEnd;
			}
			commentView.setSelection(newStart, newEnd);
		}
	}

	private void resizeComment(ViewGroup root) {
		View postMain = root.getChildAt(0);
		commentView.setMinLines(4);
		int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(postMain.getWidth(), View.MeasureSpec.EXACTLY);
		int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
		postMain.measure(widthMeasureSpec, heightMeasureSpec);
		int delta = root.getHeight() - postMain.getMeasuredHeight();
		if (delta > 0) {
			commentView.setMinHeight(commentView.getMeasuredHeight() + delta);
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		clickableToastHolder.onWindowFocusChanged(hasFocus);
	}

	private class MarkupButtonsBuilder implements ViewTreeObserver.OnGlobalLayoutListener, Runnable {
		private int lastWidth = -1;

		public MarkupButtonsBuilder() {
			textFormatView.getViewTreeObserver().addOnGlobalLayoutListener(this);
		}

		@Override
		public void onGlobalLayout() {
			int width = textFormatView.getWidth();
			if (lastWidth != width) {
				lastWidth = width;
				textFormatView.removeCallbacks(this);
				textFormatView.post(this);
			}
		}

		@Override
		public void run() {
			textFormatView.removeAllViews();
			fillContainer();
		}

		private void fillContainer() {
			float density = ResourceUtils.obtainDensity(PostingActivity.this);
			int maxButtonsWidth = lastWidth - textFormatView.getPaddingLeft() - textFormatView.getPaddingRight();
			int buttonMarginLeft = (int) ((C.API_LOLLIPOP ? -4f : 0f) * density);
			Pair<Integer, Integer> supportedAndDisplayedTags = MarkupButtonProvider.obtainSupportedAndDisplayedTags
					(ChanMarkup.get(chanName), boardName, density, maxButtonsWidth, buttonMarginLeft);
			int supportedTags = supportedAndDisplayedTags.first;
			int displayedTags = supportedAndDisplayedTags.second;
			if (commentEditor != null) {
				commentEditor.handleSimilar(supportedTags);
			}
			boolean firstMarkupButton = true;
			for (MarkupButtonProvider provider : MarkupButtonProvider.iterable(displayedTags)) {
				Button button = provider.createButton(textFormatView.getContext(),
						android.R.attr.borderlessButtonStyle);
				button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, C.API_LOLLIPOP ? 14 : 18);
				LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams
						((int) (provider.widthDp * density), (int) (40f * density));
				if (!firstMarkupButton) {
					layoutParams.leftMargin = buttonMarginLeft;
				}
				button.setTag(provider.tag);
				button.setOnClickListener(formatButtonClickListener);
				button.setPadding(0, 0, 0, 0);
				if (C.API_LOLLIPOP) {
					button.setAllCaps(false);
				}
				provider.applyTextAndStyle(button);
				textFormatView.addView(button, layoutParams);
				firstMarkupButton = false;
			}
		}
	}

	// Modified ScrollView. Allows comment input resizing to fit screen.
	public static class ResizingScrollView extends ScrollView implements Runnable {
		public ResizingScrollView(Context context, AttributeSet attrs) {
			super(context, attrs);
			setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
		    setFocusable(true);
		    setFocusableInTouchMode(true);
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			int oldHeight = getHeight();
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			int newHeight = getMeasuredHeight();
			if (newHeight != oldHeight) {
				postResizeComment();
			}
		}

		@Override
		public void run() {
			PostingActivity activity = (PostingActivity) getContext();
			activity.resizeComment(ResizingScrollView.this);
		}

		public void postResizeComment() {
			removeCallbacks(this);
			post(this);
		}
	}
}
