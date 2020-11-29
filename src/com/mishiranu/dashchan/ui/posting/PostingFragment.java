package com.mishiranu.dashchan.ui.posting;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.ViewModelProvider;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanMarkup;
import chan.content.ChanPerformer;
import chan.text.CommentEditor;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ReadCaptchaTask;
import com.mishiranu.dashchan.content.async.SendPostTask;
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.content.service.PostingService;
import com.mishiranu.dashchan.content.storage.DraftsStorage;
import com.mishiranu.dashchan.graphics.RoundedCornersDrawable;
import com.mishiranu.dashchan.graphics.TransparentTileDrawable;
import com.mishiranu.dashchan.media.JpegData;
import com.mishiranu.dashchan.ui.CaptchaForm;
import com.mishiranu.dashchan.ui.ContentFragment;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.posting.dialog.AttachmentOptionsDialog;
import com.mishiranu.dashchan.ui.posting.dialog.AttachmentRatingDialog;
import com.mishiranu.dashchan.ui.posting.dialog.AttachmentWarningDialog;
import com.mishiranu.dashchan.ui.posting.dialog.SendPostFailDetailsDialog;
import com.mishiranu.dashchan.ui.posting.text.CommentEditWatcher;
import com.mishiranu.dashchan.ui.posting.text.MarkupButtonProvider;
import com.mishiranu.dashchan.ui.posting.text.NameEditWatcher;
import com.mishiranu.dashchan.ui.posting.text.QuoteEditWatcher;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DropdownView;
import com.mishiranu.dashchan.widget.ExpandedLayout;
import com.mishiranu.dashchan.widget.ProgressDialog;
import com.mishiranu.dashchan.widget.ThemeEngine;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class PostingFragment extends ContentFragment implements FragmentHandler.Callback, CaptchaForm.Callback,
		ReadCaptchaTask.Callback, PostingDialogCallback {
	private static final String EXTRA_CHAN_NAME = "chanName";
	private static final String EXTRA_BOARD_NAME = "boardName";
	private static final String EXTRA_THREAD_NUMBER = "threadNumber";
	private static final String EXTRA_REPLY_DATA_LIST = "replyDataList";

	private static final String EXTRA_CAPTCHA_DRAFT = "captchaDraft";

	public PostingFragment() {}

	public PostingFragment(String chanName, String boardName, String threadNumber,
			List<Replyable.ReplyData> replyDataList) {
		Bundle args = new Bundle();
		args.putString(EXTRA_CHAN_NAME, chanName);
		args.putString(EXTRA_BOARD_NAME, boardName);
		args.putString(EXTRA_THREAD_NUMBER, threadNumber);
		args.putParcelableArrayList(EXTRA_REPLY_DATA_LIST, new ArrayList<>(replyDataList));
		setArguments(args);
	}

	private String getChanName() {
		return requireArguments().getString(EXTRA_CHAN_NAME);
	}

	private String getBoardName() {
		return requireArguments().getString(EXTRA_BOARD_NAME);
	}

	private String getThreadNumber() {
		return requireArguments().getString(EXTRA_THREAD_NUMBER);
	}

	public boolean check(String chanName, String boardName, String threadNumber) {
		return CommonUtils.equals(getChanName(), chanName) &&
				CommonUtils.equals(getBoardName(), boardName) &&
				CommonUtils.equals(getThreadNumber(), threadNumber);
	}

	private boolean allowPosting;
	private boolean sendSuccess;
	private boolean draftSaved;
	private PostingService.FailResult failResult;

	private CommentEditor commentEditor;

	private ChanConfiguration.Posting postingConfiguration;
	private List<Pair<String, String>> userIconItems;
	private List<Pair<String, String>> attachmentRatingItems;

	private String captchaType;
	private ReadCaptchaTask.CaptchaState captchaState;
	private ChanPerformer.CaptchaData captchaData;
	private String loadedCaptchaType;
	private ChanConfiguration.Captcha.Input loadedCaptchaInput;
	private ChanConfiguration.Captcha.Validity loadedCaptchaValidity;
	private Bitmap captchaImage;
	private boolean captchaLarge;
	private boolean captchaBlackAndWhite;
	private long captchaLoadTime;

	private ScrollView scrollView;
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
	private ViewGroup personalDataBlock;
	private ViewGroup textFormatView;
	private CommentEditWatcher commentEditWatcher;
	private CaptchaForm captchaForm;
	private Button sendButton;
	private int attachmentColumnCount;

	private final ArrayList<AttachmentHolder> attachments = new ArrayList<>();

	private boolean allowDialog = true;
	private boolean sendButtonEnabled = true;

	private PostingService.Binder postingBinder;
	private final ServiceConnection postingConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			postingBinder = (PostingService.Binder) service;
			postingBinder.register(postingCallback, getChanName(), getBoardName(), getThreadNumber());
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			if (postingBinder != null) {
				postingBinder.unregister(postingCallback);
				postingBinder = null;
			}
		}
	};

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		ExpandedLayout rootView = new ExpandedLayout(container.getContext(), true);
		rootView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		inflater.inflate(R.layout.activity_posting, rootView);
		return rootView;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		Chan chan = Chan.get(getChanName());
		postingConfiguration = chan.configuration.safe().obtainPosting(getBoardName(), getThreadNumber() == null);
		if (postingConfiguration != null) {
			allowPosting = chan.configuration.safe().obtainBoard(getBoardName()).allowPosting;
		} else {
			postingConfiguration = new ChanConfiguration.Posting();
			allowPosting = false;
		}

		DraftsStorage draftsStorage = DraftsStorage.getInstance();
		captchaType = chan.configuration.getCaptchaType();
		if (allowPosting) {
			commentEditor = chan.markup.safe().obtainCommentEditor(getBoardName());
		}
		float density = ResourceUtils.obtainDensity(view);
		int screenWidthDp = getResources().getConfiguration().screenWidthDp;
		boolean hugeCaptcha = Preferences.isHugeCaptcha();
		boolean longLayout = screenWidthDp >= 480;

		scrollView = view.findViewById(R.id.scroll_view);
		ViewGroup postingLayout = view.findViewById(R.id.posting_layout);
		LinearLayout commentParent = view.findViewById(R.id.comment_parent);
		LinearLayout commentFormat = view.findViewById(R.id.comment_format);
		commentView = view.findViewById(R.id.comment);
		sageCheckBox = view.findViewById(R.id.sage_checkbox);
		spoilerCheckBox = view.findViewById(R.id.spoiler_checkbox);
		originalPosterCheckBox = view.findViewById(R.id.original_poster_checkbox);
		checkBoxParent = view.findViewById(R.id.checkbox_parent);
		nameView = view.findViewById(R.id.name);
		emailView = view.findViewById(R.id.email);
		passwordView = view.findViewById(R.id.password);
		subjectView = view.findViewById(R.id.subject);
		iconView = view.findViewById(R.id.icon);
		personalDataBlock = view.findViewById(R.id.personal_data_block);
		attachmentContainer = view.findViewById(R.id.attachment_container);
		FrameLayout footerContainer = view.findViewById(R.id.footer_container);
		int[] oldScrollViewHeight = {-1};
		scrollView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
			if (scrollView != null) {
				int scrollViewHeight = scrollView.getHeight();
				if (scrollViewHeight != oldScrollViewHeight[0]) {
					oldScrollViewHeight[0] = scrollViewHeight;
					resizeComment(false);
				}
			}
		});
		if (C.API_LOLLIPOP) {
			postingLayout.setPadding((int) (8f * density), 0, (int) (8f * density), 0);
		}
		addHeader(personalDataBlock, 0, R.string.personal_data);
		addHeader(postingLayout, postingLayout.indexOfChild(subjectView), R.string.message_data);
		addHeader(postingLayout, postingLayout.indexOfChild(footerContainer), R.string.confirmation);
		TextView tripcodeWarning = view.findViewById(R.id.personal_tripcode_warning);
		TextView remainingCharacters = view.findViewById(R.id.remaining_characters);
		if (C.API_LOLLIPOP) {
			ViewUtils.setTextSizeScaled(tripcodeWarning, 12);
			tripcodeWarning.setPadding((int) (4f * density), 0, (int) (4f * density), (int) (4f * density));
			ViewUtils.setTextSizeScaled(remainingCharacters, 12);
			ViewUtils.setNewMargin(remainingCharacters, 0, (int) (-2f * density), 0, 0);
		} else {
			tripcodeWarning.setPadding((int) (12f * density), (int) (4f * density),
					(int) (12f * density), (int) (4f * density));
		}
		nameView.addTextChangedListener(new NameEditWatcher(postingConfiguration.allowName &&
				!postingConfiguration.allowTripcode, nameView, tripcodeWarning, () -> resizeComment(true)));
		if (!C.API_LOLLIPOP) {
			ViewUtils.setNewMargin(iconView, (int) (4f * density), 0, (int) (4f * density), 0);
		}
		commentEditWatcher = new CommentEditWatcher(postingConfiguration, commentView, remainingCharacters,
				() -> resizeComment(true), () -> DraftsStorage.getInstance().store(obtainPostDraft()));
		commentView.setOnFocusChangeListener((v, hasFocus) -> updateFocusButtons(hasFocus));
		commentView.addTextChangedListener(commentEditWatcher);
		commentView.addTextChangedListener(new QuoteEditWatcher(requireContext()));
		boolean addPaddingToRoot = false;
		if (C.API_LOLLIPOP) {
			boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
			ViewGroup extra = landscape ? ((FragmentHandler) requireActivity()).getToolbarView()
					: ((FragmentHandler) requireActivity()).getToolbarExtra();
			LinearLayout textFormatView = new LinearLayout(extra.getContext());
			textFormatView.setOrientation(LinearLayout.HORIZONTAL);
			this.textFormatView = textFormatView;
			if (landscape) {
				boolean rtl = textFormatView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
				textFormatView.setPadding(rtl ? 0 : (int) (8f * density), 0, rtl ? (int) (8f * density) : 0, 0);
			} else {
				textFormatView.setPadding((int) (8f * density), 0, (int) (8f * density), (int) (4f * density));
				addPaddingToRoot = true;
			}
			extra.addView(textFormatView, ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT);
			commentParent.removeView(commentView);
			postingLayout.addView(commentView, postingLayout.indexOfChild(commentParent));
			postingLayout.removeView(commentParent);
			ViewUtils.setNewMargin(checkBoxParent, 0, (int) (4f * density), 0, 0);
		} else {
			commentParent.setDividerDrawable(ResourceUtils.getDrawable(commentParent.getContext(),
					android.R.attr.dividerHorizontal, 0));
			commentParent.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
			textFormatView = commentFormat;
			ViewUtils.setNewMargin(checkBoxParent, (int) (4f * density), (int) (4f * density),
					(int) (4f * density), (int) (4f * density));
		}
		updatePostingConfiguration(true, false, false);
		new MarkupButtonsBuilder(addPaddingToRoot, (int) (getResources().getConfiguration().screenWidthDp *
				ResourceUtils.obtainDensity(getResources())));

		boolean longFooter = longLayout && !hugeCaptcha;
		int resId = longFooter ? R.layout.activity_posting_footer_long : R.layout.activity_posting_footer_common;
		getLayoutInflater().inflate(resId, footerContainer);
		LinearLayout captchaInputRootView = footerContainer.findViewById(R.id.captcha_input_root);
		LinearLayout captchaInputParentView = footerContainer.findViewById(R.id.captcha_input_parent);
		EditText captchaInputView = footerContainer.findViewById(R.id.captcha_input);
		if (C.API_LOLLIPOP) {
			captchaInputParentView.setPadding(0, longFooter ? (int) (8f * density) : 0, 0, (int) (8f * density));
			ViewUtils.setNewMarginRelative(captchaInputView, null, null, (int) (4f * density), null);
		} else {
			int[] attrs = {android.R.attr.dividerHorizontal, android.R.attr.dividerVertical};
			TypedArray typedArray = view.getContext().obtainStyledAttributes(null, attrs);
			if (captchaInputRootView != null) {
				captchaInputRootView.setDividerDrawable(typedArray.getDrawable(0));
				captchaInputRootView.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
			}
			captchaInputParentView.setDividerDrawable(typedArray.getDrawable(1));
			captchaInputParentView.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
			typedArray.recycle();
			ViewUtils.setNewMargin(captchaInputView, (int) (8f * density), null, (int) (8f * density), null);
		}
		ChanConfiguration.Captcha captcha = chan.configuration.safe().obtainCaptcha(captchaType);
		captchaForm = new CaptchaForm(this, true, !longFooter,
				footerContainer, captchaInputParentView, captchaInputView, captcha);
		if (C.API_LOLLIPOP) {
			float maxTranslationZ = (int) (2f * density);
			sendButton = new Button(captchaInputParentView.getContext(), null, 0, C.API_MARSHMALLOW
					? android.R.style.Widget_Material_Button_Colored : android.R.style.Widget_Material_Button) {
				@Override
				public void setTranslationZ(float translationZ) {
					super.setTranslationZ(Math.min(translationZ, maxTranslationZ));
				}
			};
			if (!C.API_MARSHMALLOW) {
				if (!C.API_LOLLIPOP_MR1) {
					// GradientDrawable doesn't support tints
					float radius = 2f * density;
					float[] radiusArray = {radius, radius, radius, radius, radius, radius, radius, radius};
					ShapeDrawable background = new ShapeDrawable() {
						@Override
						public void getOutline(Outline outline) {
							// Lollipop has broken RoundRectShape.getOutline
							Rect bounds = getBounds();
							outline.setRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom, radius);
						}
					};
					background.setShape(new RoundRectShape(radiusArray, null, null));
					sendButton.setBackground(new InsetDrawable(background, (int) (4f * density),
							(int) (6f * density), (int) (4f * density), (int) (6f * density)));
				}
				sendButton.setTextColor(ResourceUtils.getColorStateList(sendButton.getContext(),
						android.R.attr.textColorPrimaryInverse));
			}
			if (C.API_LOLLIPOP_MR1) {
				Rect rect = new Rect();
				// Limit elevation height since the shadow looks ugly when the view is at the bottom
				sendButton.setOutlineProvider(new ViewOutlineProvider() {
					@Override
					public void getOutline(View view, Outline outline) {
						view.getBackground().getOutline(outline);
						if (ViewUtils.getOutlineRect(outline, rect)) {
							float radius = ViewUtils.getOutlineRadius(outline);
							rect.bottom -= (int) (2f * density);
							outline.setRoundRect(rect, radius);
						}
					}
				});
			}
			ThemeEngine.Theme theme = ThemeEngine.getTheme(sendButton.getContext());
			int colorControlDisabled = GraphicsUtils.applyAlpha(theme.controlNormal21, theme.disabledAlpha21);
			int[][] states = {{-android.R.attr.state_enabled}, {}};
			int[] colors = {colorControlDisabled, theme.accent};
			sendButton.setBackgroundTintList(new ColorStateList(states, colors));
		} else {
			sendButton = new Button(captchaInputParentView.getContext(), null, android.R.attr.borderlessButtonStyle);
		}
		sendButton.setSingleLine(true);
		if (C.API_LOLLIPOP) {
			// setSingleLine breaks capitalization
			sendButton.setAllCaps(true);
		}
		captchaInputParentView.addView(sendButton, 0, LinearLayout.LayoutParams.WRAP_CONTENT);
		sendButton.setText(R.string.send);
		sendButton.setOnClickListener(v -> executeSendPost());
		if (longFooter) {
			((LinearLayout.LayoutParams) sendButton.getLayoutParams()).weight = 2f;
			boolean[] lastAddWeight = {true};
			captchaInputParentView.addOnLayoutChangeListener((v, left, top, right, bottom,
					oldLeft, oldTop, oldRight, oldBottom) -> {
				boolean addWeight = captchaInputView.getVisibility() == View.GONE;
				if (addWeight != lastAddWeight[0]) {
					lastAddWeight[0] = addWeight;
					((LinearLayout.LayoutParams) sendButton.getLayoutParams()).weight = addWeight ? 2f : 1f;
					sendButton.requestLayout();
				}
			});
		} else {
			((LinearLayout.LayoutParams) sendButton.getLayoutParams()).weight = 1f;
		}
		attachmentColumnCount = screenWidthDp >= 960 ? 4 : screenWidthDp >= 480 ? 2 : 1;

		StringBuilder builder = new StringBuilder();
		int commentCarriage = 0;

		attachments.clear();
		DraftsStorage.PostDraft postDraft = draftsStorage
				.getPostDraft(getChanName(), getBoardName(), getThreadNumber());
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
		if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_CAPTCHA_DRAFT)) {
			DraftsStorage.CaptchaDraft captchaDraft = savedInstanceState.getParcelable(EXTRA_CAPTCHA_DRAFT);
			if (captchaDraft.captchaState != null) {
				captchaLoadTime = captchaDraft.loadTime;
				showCaptcha(captchaDraft.captchaState, captchaDraft.captchaData, captchaDraft.loadedCaptchaType,
						captchaDraft.loadedInput, captchaDraft.loadedValidity, captchaDraft.image,
						captchaDraft.large, captchaDraft.blackAndWhite);
				captchaForm.setText(captchaDraft.text);
				captchaRestoreSuccess = true;
			}
		} else {
			DraftsStorage.CaptchaDraft captchaDraft = draftsStorage.getCaptchaDraft(getChanName());
			if (captchaDraft != null && captchaDraft.loadedCaptchaType == null) {
				captchaLoadTime = captchaDraft.loadTime;
				ChanConfiguration.Captcha.Validity captchaValidity = captcha.validity;
				if (captchaValidity == null) {
					captchaValidity = ChanConfiguration.Captcha.Validity.SHORT_LIFETIME;
				}
				if (captchaDraft.loadedValidity != null) {
					ChanConfiguration.Captcha.Validity loadedCaptchaValidity = captchaDraft.loadedValidity;
					if (captchaDraft.captchaState != ReadCaptchaTask.CaptchaState.CAPTCHA ||
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
						canLoadState = CommonUtils.equals(getBoardName(), captchaDraft.boardName)
								&& CommonUtils.equals(getThreadNumber(), captchaDraft.threadNumber);
						break;
					}
					case IN_BOARD_SEPARATELY: {
						canLoadState = CommonUtils.equals(getBoardName(), captchaDraft.boardName)
								&& ((getThreadNumber() == null) == (captchaDraft.threadNumber == null));
						break;
					}
					case IN_BOARD: {
						canLoadState = CommonUtils.equals(getBoardName(), captchaDraft.boardName);
						break;
					}
					case LONG_LIFETIME: {
						canLoadState = true;
						break;
					}
				}
				if (canLoadState && CommonUtils.equals(captchaType, captchaDraft.captchaType)) {
					if (captchaDraft.captchaState == ReadCaptchaTask.CaptchaState.CAPTCHA &&
							captchaDraft.image != null) {
						showCaptcha(ReadCaptchaTask.CaptchaState.CAPTCHA, captchaDraft.captchaData, null,
								captchaDraft.loadedInput, captchaDraft.loadedValidity,
								captchaDraft.image, captchaDraft.large, captchaDraft.blackAndWhite);
						captchaForm.setText(captchaDraft.text);
						captchaRestoreSuccess = true;
					} else if (canLoadState && (captchaDraft.captchaState == ReadCaptchaTask.CaptchaState.SKIP
							|| captchaDraft.captchaState == ReadCaptchaTask.CaptchaState.PASS)) {
						showCaptcha(captchaDraft.captchaState, captchaDraft.captchaData, null, null,
								captchaDraft.loadedValidity, null, false, false);
						captchaRestoreSuccess = true;
					}
				}
			}
		}

		List<Replyable.ReplyData> replyDataList = savedInstanceState != null ? Collections.emptyList()
				: requireArguments().getParcelableArrayList(EXTRA_REPLY_DATA_LIST);
		if (!replyDataList.isEmpty()) {
			boolean onlyLinks = true;
			for (Replyable.ReplyData data : replyDataList) {
				if (!StringUtils.isEmpty(data.comment)) {
					onlyLinks = false;
					break;
				}
			}
			for (int i = 0; i < replyDataList.size(); i++) {
				boolean lastLink = i == replyDataList.size() - 1;
				Replyable.ReplyData data = replyDataList.get(i);
				PostNumber postNumber = data.postNumber;
				String comment = data.comment;
				if (postNumber != null) {
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
		if (!captchaRestoreSuccess) {
			refreshCaptcha(false, true, false);
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		if (postingBinder != null) {
			postingBinder.unregister(postingCallback);
			postingBinder = null;
		}
		requireActivity().unbindService(postingConnection);

		dismissSendPost();
		saveDraft();
		if (C.API_LOLLIPOP) {
			ViewUtils.removeFromParent(textFormatView);
		}
		scrollView = null;
		commentView = null;
		sageCheckBox = null;
		spoilerCheckBox = null;
		originalPosterCheckBox = null;
		checkBoxParent = null;
		attachmentContainer = null;
		nameView = null;
		emailView = null;
		passwordView = null;
		subjectView = null;
		iconView = null;
		personalDataBlock = null;
		textFormatView = null;
		commentEditWatcher = null;
		captchaForm = null;
		sendButton = null;
		attachments.clear();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		setHasOptionsMenu(true);
		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(StringUtils.isEmpty(getThreadNumber())
				? R.string.new_thread : R.string.new_post), null);
		requireActivity().bindService(new Intent(requireContext(), PostingService.class),
				postingConnection, Context.BIND_AUTO_CREATE);

		CaptchaViewModel viewModel = new ViewModelProvider(this).get(CaptchaViewModel.class);
		viewModel.observe(getViewLifecycleOwner(), this);
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
		return new DraftsStorage.PostDraft(getChanName(), getBoardName(), getThreadNumber(), name, email, password,
				subject, comment, commentCarriage, attachmentDrafts,
				optionSage, optionSpoiler, optionOriginalPoster, userIcon);
	}

	private DraftsStorage.CaptchaDraft obtainCaptchaDraft() {
		String input = captchaForm.getInput();
		return new DraftsStorage.CaptchaDraft(captchaType, captchaState, captchaData, loadedCaptchaType,
				loadedCaptchaInput, loadedCaptchaValidity, input, captchaImage, captchaLarge,
				captchaBlackAndWhite, captchaLoadTime, getBoardName(), getThreadNumber());
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		DraftsStorage.CaptchaDraft captchaDraft = obtainCaptchaDraft();
		outState.putParcelable(EXTRA_CAPTCHA_DRAFT, captchaDraft);
		saveDraft();
	}

	@Override
	public void onResume() {
		super.onResume();

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

		PostingService.FailResult failResult = this.failResult;
		this.failResult = null;
		if (failResult != null) {
			handleFailResult(failResult);
		}
		draftSaved = false;
		if (!allowPosting || sendSuccess) {
			((FragmentHandler) requireActivity()).removeFragment();
		}
	}

	@Override
	public void onChansChanged(Collection<String> changed, Collection<String> removed) {
		if (changed.contains(getChanName()) || removed.contains(getChanName())) {
			updatePostingConfigurationIfNeeded();
			if (!allowPosting) {
				((FragmentHandler) requireActivity()).removeFragment();
			}
		}
	}

	// Should be called both from onDestroyView (1) and onSaveInstanceState (2).
	// 1: Ensures draft is saved when user leaves posting screen.
	// 2: Ensures draft is saved when activity is recreated.
	private void saveDraft() {
		if (!sendSuccess && !draftSaved) {
			draftSaved = true;
			DraftsStorage draftsStorage = DraftsStorage.getInstance();
			draftsStorage.store(obtainPostDraft());
			draftsStorage.store(getChanName(), obtainCaptchaDraft());
		}
	}

	@Override
	public void onRefreshCaptcha(boolean forceRefresh) {
		refreshCaptcha(forceRefresh, false, true);
	}

	@Override
	public void onConfirmCaptcha() {
		executeSendPost();
	}

	private static void addHeader(ViewGroup layout, int index, int textResId) {
		TextView textView = ViewFactory.makeListTextHeader(layout);
		textView.setText(textResId);
		layout.addView(textView, index);
		if (C.API_LOLLIPOP) {
			float density = ResourceUtils.obtainDensity(textView);
			textView.setPadding((int) (4f * density), 0, (int) (4f * density), 0);
			ViewUtils.setNewMargin(textView, 0, 0, 0, (int) (-8f * density));
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
				items.add(getString(R.string.no_icon));
				for (int i = 0; i < userIconItems.size(); i++) {
					Pair<String, String> iconItem = userIconItems.get(i);
					items.add(iconItem.second);
					if (CommonUtils.equals(lastUserIcon, iconItem.first)) {
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
			Chan chan = Chan.get(getChanName());
			ChanConfiguration.Board board = chan.configuration.safe().obtainBoard(getChanName());
			if (board.allowDeleting) {
				ChanConfiguration.Deleting deleting = chan.configuration.safe().obtainDeleting(getChanName());
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
				showPersonalDataBlock = posting.allowName || posting.allowEmail ||
						needPassword || userIconItems != null;
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
				requireActivity().invalidateOptionsMenu();
			}
		}
	}

	private boolean compareListOfPairs(List<Pair<String, String>> first, List<Pair<String, String>> second) {
		if (first.size() != second.size()) {
			return false;
		}
		for (int i = 0; i < first.size(); i++) {
			if (!CommonUtils.equals(first.get(i).first, first.get(i).second)
					|| !CommonUtils.equals(first.get(i).second, first.get(i).second)) {
				return false;
			}
		}
		return false;
	}

	private void updatePostingConfigurationIfNeeded() {
		Chan chan = Chan.get(getChanName());
		ChanConfiguration.Posting oldPosting = postingConfiguration;
		ChanConfiguration.Posting newPosting = chan.configuration
				.safe().obtainPosting(getBoardName(), getThreadNumber() == null);
		if (newPosting == null) {
			allowPosting = false;
			newPosting = new ChanConfiguration.Posting();
		} else {
			allowPosting = chan.configuration.safe().obtainBoard(getBoardName()).allowPosting;
		}
		boolean views = oldPosting.allowName != newPosting.allowName || oldPosting.allowEmail != newPosting.allowEmail
				|| oldPosting.allowTripcode != newPosting.allowTripcode
				|| oldPosting.allowSubject != newPosting.allowSubject || oldPosting.optionSage != newPosting.optionSage
				|| oldPosting.optionSpoiler != newPosting.optionSpoiler
				|| oldPosting.optionOriginalPoster != newPosting.optionOriginalPoster
				|| oldPosting.maxCommentLength != newPosting.maxCommentLength
				|| !CommonUtils.equals(oldPosting.maxCommentLengthEncoding, newPosting.maxCommentLengthEncoding)
				|| !compareListOfPairs(oldPosting.userIcons, newPosting.userIcons);
		boolean attachmentOptions = oldPosting.attachmentSpoiler != newPosting.attachmentSpoiler
				|| !compareListOfPairs(oldPosting.attachmentRatings, newPosting.attachmentRatings);
		boolean attachmentCount = oldPosting.attachmentCount != newPosting.attachmentCount;
		if (views || attachmentOptions || attachmentCount) {
			postingConfiguration = newPosting;
			updatePostingConfiguration(views, attachmentOptions, attachmentCount);
			resizeComment(true);
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

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		menu.add(0, R.id.menu_attach, 0, R.string.attach)
				.setIcon(((FragmentHandler) requireActivity()).getActionBarIcon(R.attr.iconActionAttach))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.menu_attach).setVisible(attachments.size() < postingConfiguration.attachmentCount);
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

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_attach: {
				// SHOW_ADVANCED to show folder navigation
				Intent intent = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE)
						.putExtra("android.content.extra.SHOW_ADVANCED", true);
				ArrayList<String> mimeTypes = buildMimeTypeList(postingConfiguration.attachmentMimeTypes);
				if (C.API_KITKAT) {
					if (mimeTypes.size() >= 2) {
						intent.setType("*/*");
						intent.putExtra(Intent.EXTRA_MIME_TYPES, CommonUtils.toArray(mimeTypes, String.class));
					} else if (mimeTypes.size() == 1) {
						intent.setType(mimeTypes.get(0));
					}
					intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
				} else {
					StringBuilder typesBuilder = new StringBuilder();
					for (String type : mimeTypes) {
						if (typesBuilder.length() > 0) {
							typesBuilder.append(',');
						}
						typesBuilder.append(type);
					}
					if (typesBuilder.length() > 0) {
						intent.setType(typesBuilder.toString());
					}
				}
				try {
					startActivityForResult(intent, C.REQUEST_CODE_ATTACH);
				} catch (ActivityNotFoundException e) {
					ClickableToast.show(R.string.unknown_address);
				}
				break;
			}
		}
		return true;
	}

	private void updateFocusButtons(boolean commentFocused) {
		if (C.API_LOLLIPOP) {
			for (int i = 0; i < textFormatView.getChildCount(); i++) {
				textFormatView.getChildAt(i).setClickable(commentFocused);
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
			InputMethodManager inputMethodManager = (InputMethodManager) requireContext()
					.getSystemService(Context.INPUT_METHOD_SERVICE);
			if (inputMethodManager != null) {
				inputMethodManager.showSoftInput(commentView, 0);
			}
		}
	};

	private void updateSendButtonState() {
		sendButton.setEnabled(sendButtonEnabled && captchaState != null &&
				captchaState != ReadCaptchaTask.CaptchaState.NEED_LOAD);
	}

	private String getTextIfVisible(EditText editText) {
		return editText.getVisibility() == View.VISIBLE ? StringUtils.nullIfEmpty(editText.getText().toString()) : null;
	}

	private boolean isCheckedIfVisible(CheckBox checkBox) {
		return checkBox.getVisibility() == View.VISIBLE && checkBox.isChecked();
	}

	private void executeSendPost() {
		if (postingBinder == null) {
			return;
		}
		String subject = getTextIfVisible(subjectView);
		String comment = getTextIfVisible(commentView);
		String name = getTextIfVisible(nameView);
		String email = getTextIfVisible(emailView);
		String password = getTextIfVisible(passwordView);
		if (password == null) {
			password = Preferences.getPassword(Chan.get(getChanName()));
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
		String captchaType = loadedCaptchaType != null ? loadedCaptchaType : this.captchaType;
		ChanPerformer.CaptchaData captchaData = this.captchaData;
		if (captchaData != null) {
			captchaData = captchaData.copy();
			captchaData.put(ChanPerformer.CaptchaData.INPUT, captchaForm.getInput());
		}
		boolean captchaNeedLoad = captchaState == ReadCaptchaTask.CaptchaState.MAY_LOAD ||
				captchaState == ReadCaptchaTask.CaptchaState.MAY_LOAD_SOLVING;
		ChanPerformer.SendPostData data = new ChanPerformer.SendPostData(getBoardName(), getThreadNumber(),
				subject, comment, name, email, password, attachments, optionSage, optionSpoiler, optionOriginalPoster,
				userIcon, captchaType, captchaData, captchaNeedLoad, 15000, 45000);
		DraftsStorage.getInstance().store(obtainPostDraft());
		allowDialog = false;
		if (postingBinder.executeSendPost(getChanName(), data)) {
			sendButtonEnabled = false;
			updateSendButtonState();
			if (progressDialog != null) {
				progressDialog.dismiss();
				progressDialog = null;
			}
			onSendPostMinimize();
		} else {
			allowDialog = true;
		}
	}

	private ProgressDialog progressDialog;

	private void onSendPostCancel() {
		progressDialog = null;
		postingBinder.cancelSendPost(getChanName(), getBoardName(), getThreadNumber());
	}

	private void onSendPostMinimize() {
		progressDialog = null;
		((FragmentHandler) requireActivity()).removeFragment();
	}

	private void dismissSendPost() {
		if (progressDialog != null) {
			progressDialog.dismiss();
		}
		progressDialog = null;
		sendButtonEnabled = true;
		if (sendButton != null) {
			updateSendButtonState();
		}
	}

	private final PostingService.Callback postingCallback = new PostingService.Callback() {
		@Override
		public void onState(boolean progressMode, SendPostTask.ProgressState progressState,
				int attachmentIndex, int attachmentsCount) {
			if (allowDialog && progressDialog == null) {
				progressDialog = new ProgressDialog(requireContext(), progressMode ? "%1$d / %2$d kB" : null);
				progressDialog.setOnCancelListener(d -> onSendPostCancel());
				progressDialog.setButton(ProgressDialog.BUTTON_POSITIVE, getString(R.string.minimize),
						(d, w) -> onSendPostMinimize());
				progressDialog.setButton(ProgressDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel),
						(d, w) -> onSendPostCancel());
				progressDialog.show();
			}
			if (progressDialog == null) {
				return;
			}
			switch (progressState) {
				case CONNECTING: {
					progressDialog.setMax(1);
					progressDialog.setIndeterminate(true);
					progressDialog.setMessage(getString(R.string.sending__ellipsis));
					break;
				}
				case SENDING: {
					progressDialog.setIndeterminate(false);
					if (progressMode) {
						progressDialog.setMessage(getString(R.string.sending_number_of_number__ellipsis_format,
								attachmentIndex + 1, attachmentsCount));
					} else {
						progressDialog.setMessage(getString(R.string.sending__ellipsis));
					}
					break;
				}
				case PROCESSING: {
					progressDialog.setIndeterminate(false);
					progressDialog.setMessage(getString(R.string.processing_data__ellipsis));
					break;
				}
			}
		}

		@Override
		public void onProgress(long progress, long progressMax) {
			if (progressDialog != null) {
				progressDialog.setMax((int) (progressMax / 1000));
				progressDialog.setValue((int) (progress / 1000));
			}
		}

		@Override
		public void onStop(boolean success) {
			dismissSendPost();
			if (success) {
				sendSuccess = true;
				if (isResumed()) {
					((FragmentHandler) requireActivity()).removeFragment();
				}
			}
		}
	};

	public void handleFailResult(PostingService.FailResult failResult) {
		if (isResumed()) {
			if (failResult.extra != null) {
				ClickableToast.show(failResult.errorItem.toString(), null, new ClickableToast
						.Button(R.string.details, false, () -> new SendPostFailDetailsDialog(failResult.extra)
						.show(getChildFragmentManager(), null)));
			} else {
				ClickableToast.show(failResult.errorItem);
			}
			if (failResult.errorItem.httpResponseCode == 0 && !failResult.keepCaptcha) {
				refreshCaptcha(false, !failResult.captchaError, true);
			}
			updatePostingConfigurationIfNeeded();
		} else {
			this.failResult = failResult;
		}
	}

	private void refreshCaptcha(boolean forceCaptcha, boolean mayShowLoadButton, boolean restart) {
		boolean allowSolveAutomatically = !forceCaptcha ||
				captchaState != ReadCaptchaTask.CaptchaState.MAY_LOAD_SOLVING;
		captchaState = null;
		loadedCaptchaType = null;
		captchaLoadTime = 0L;
		updateSendButtonState();
		captchaForm.showLoading();
		CaptchaViewModel viewModel = new ViewModelProvider(this).get(CaptchaViewModel.class);
		if (restart || !viewModel.hasTaskOrValue()) {
			Chan chan = Chan.get(getChanName());
			List<String> captchaPass = forceCaptcha ? null : Preferences.getCaptchaPass(chan);
			ReadCaptchaTask task = new ReadCaptchaTask(viewModel.callback, null, captchaType, null, captchaPass,
					mayShowLoadButton, allowSolveAutomatically, chan, getBoardName(), getThreadNumber());
			task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
			viewModel.attach(task);
		}
	}

	public static class CaptchaViewModel extends TaskViewModel.Proxy<ReadCaptchaTask, ReadCaptchaTask.Callback> {}

	@Override
	public void onReadCaptchaSuccess(ReadCaptchaTask.Result result) {
		captchaLoadTime = SystemClock.elapsedRealtime();
		showCaptcha(result.captchaState, result.captchaData, result.captchaType, result.input, result.validity,
				result.image, result.large, result.blackAndWhite);
		updatePostingConfigurationIfNeeded();
	}

	@Override
	public void onReadCaptchaError(ErrorItem errorItem) {
		ClickableToast.show(errorItem);
		captchaForm.showError();
		updatePostingConfigurationIfNeeded();
	}

	private void showCaptcha(ReadCaptchaTask.CaptchaState captchaState, ChanPerformer.CaptchaData captchaData,
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
			ChanConfiguration.Captcha captcha = Chan.get(getChanName()).configuration
					.safe().obtainCaptcha(captchaType);
			if (input == null) {
				input = captcha.input;
			}
			if (validity == null) {
				validity = captcha.validity;
			}
		}
		loadedCaptchaInput = input;
		loadedCaptchaValidity = validity;
		boolean invertColors = blackAndWhite && !GraphicsUtils
				.isLight(ResourceUtils.getColor(requireContext(), android.R.attr.colorBackground));
		captchaForm.showCaptcha(captchaState, input, image, large, invertColors);
		if (scrollView.getScrollY() + scrollView.getHeight() >= scrollView.getChildAt(0).getHeight()) {
			scrollView.post(() -> {
				if (scrollView != null) {
					scrollView.setScrollY(Math.max(scrollView.getChildAt(0).getHeight() - scrollView.getHeight(), 0));
				}
			});
		}
		updateSendButtonState();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
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
						FileHolder fileHolder = FileHolder.obtain(requireContext(), uri);
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
			ClickableToast.show(getResources().getQuantityString(R.plurals
					.number_files_havent_been_attached__format, errorCount, errorCount));
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
		new AttachmentOptionsDialog(attachmentIndex).show(getChildFragmentManager(), AttachmentOptionsDialog.TAG);
	};

	private final View.OnClickListener attachmentWarningListener = v -> {
		AttachmentHolder holder = (AttachmentHolder) v.getTag();
		int attachmentIndex = attachments.indexOf(holder);
		new AttachmentWarningDialog(attachmentIndex).show(getChildFragmentManager(), AttachmentWarningDialog.TAG);
	};

	private final View.OnClickListener attachmentRatingListener = v -> {
		AttachmentHolder holder = (AttachmentHolder) v.getTag();
		int attachmentIndex = attachments.indexOf(holder);
		new AttachmentRatingDialog(attachmentIndex).show(getChildFragmentManager(), AttachmentRatingDialog.TAG);
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
				requireActivity().invalidateOptionsMenu();
				resizeComment(true);
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
				subcontainer = new LinearLayout(requireContext());
				attachmentContainer.addView(subcontainer, LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT);
				subcontainer.setOrientation(LinearLayout.HORIZONTAL);
				placeholder = new View(requireContext());
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

	private static View addAttachmentButton(LinearLayout parent, int width,
			int attrResId, View.OnClickListener listener) {
		float density = ResourceUtils.obtainDensity(parent);
		ImageView imageView;
		if (C.API_LOLLIPOP) {
			imageView = new ImageView(parent.getContext(), null, android.R.attr.borderlessButtonStyle);
		} else {
			imageView = new ImageView(parent.getContext());
			ViewUtils.setSelectableItemBackground(imageView);
		}
		parent.addView(imageView, width, LinearLayout.LayoutParams.MATCH_PARENT);
		LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) imageView.getLayoutParams();
		layoutParams.gravity = Gravity.CENTER_VERTICAL;
		if (C.API_LOLLIPOP) {
			ViewUtils.setNewMarginRelative(imageView, (int) (-8f * density), 0, 0, 0);
		}
		imageView.setScaleType(ImageView.ScaleType.CENTER);
		imageView.setImageDrawable(ResourceUtils.getDrawable(imageView.getContext(), attrResId, 0));
		if (C.API_LOLLIPOP) {
			imageView.setImageTintList(ResourceUtils.getColorStateList(imageView.getContext(),
					android.R.attr.textColorPrimary));
		}
		imageView.setOnClickListener(listener);
		return imageView;
	}

	private AttachmentHolder addNewAttachment() {
		float density = ResourceUtils.obtainDensity(getResources());
		int minHeight = (int) (48f * density);
		FrameLayout view = new FrameLayout(attachmentContainer.getContext());
		view.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, minHeight));
		ViewUtils.setNewMargin(view, 0, (int) (4f * density), 0, 0);
		view.setBackgroundColor(0xff000000);
		if (C.API_LOLLIPOP) {
			view.setForeground(new RoundedCornersDrawable((int) (2f * density),
					ThemeEngine.getTheme(view.getContext()).window));
		}
		addAttachmentViewToContainer(view, attachments.size());
		ImageView imageView = new ImageView(view.getContext());
		imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
		imageView.setBackground(new TransparentTileDrawable(imageView.getContext(), true));
		imageView.setVisibility(View.GONE);
		view.addView(imageView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
		View overlay = new View(view.getContext());
		overlay.setBackgroundColor(ResourceUtils.getColor(overlay.getContext(), R.attr.colorBlockBackground));
		view.addView(overlay, FrameLayout.LayoutParams.MATCH_PARENT, minHeight);
		((FrameLayout.LayoutParams) overlay.getLayoutParams()).gravity = Gravity.BOTTOM;
		View options = new View(view.getContext());
		ViewUtils.setSelectableItemBackground(options);
		options.setOnClickListener(attachmentOptionsListener);
		view.addView(options, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);

		LinearLayout controls = new LinearLayout(view.getContext());
		controls.setOrientation(LinearLayout.HORIZONTAL);
		view.addView(controls, FrameLayout.LayoutParams.MATCH_PARENT, minHeight);
		((FrameLayout.LayoutParams) controls.getLayoutParams()).gravity = Gravity.BOTTOM;
		if (C.API_LOLLIPOP) {
			controls.setPaddingRelative((int) (8f * density), 0, 0, 0);
		} else {
			controls.setPadding((int) (8f * density), 0, (int) (8f * density), 0);
		}
		LinearLayout textLayout = new LinearLayout(controls.getContext());
		textLayout.setOrientation(LinearLayout.VERTICAL);
		textLayout.setGravity(Gravity.CENTER_VERTICAL);
		controls.addView(textLayout, 0, LinearLayout.LayoutParams.MATCH_PARENT);
		((LinearLayout.LayoutParams) textLayout.getLayoutParams()).weight = 1f;
		ViewCompat.setPaddingRelative(textLayout, (int) (4f * density), 0, (int) (8f * density), 0);
		TextView fileName = new TextView(controls.getContext());
		textLayout.addView(fileName, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		TextViewCompat.setTextAppearance(fileName, ResourceUtils.getResourceId(fileName.getContext(),
				C.API_LOLLIPOP ? android.R.attr.textAppearanceListItem : android.R.attr.textAppearanceSmall, 0));
		fileName.setSingleLine(true);
		fileName.setEllipsize(TextUtils.TruncateAt.END);
		if (C.API_LOLLIPOP) {
			ViewUtils.setTextSizeScaled(fileName, 12);
			fileName.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
		}
		TextView fileSize = new TextView(controls.getContext());
		textLayout.addView(fileSize, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		TextViewCompat.setTextAppearance(fileSize, ResourceUtils.getResourceId(fileSize.getContext(),
				C.API_LOLLIPOP ? android.R.attr.textAppearanceListItem : android.R.attr.textAppearanceSmall, 0));
		fileSize.setSingleLine(true);
		fileSize.setEllipsize(TextUtils.TruncateAt.END);
		if (C.API_LOLLIPOP) {
			ViewUtils.setTextSizeScaled(fileSize, 12);
		}
		View warningButton = addAttachmentButton(controls, minHeight,
				R.attr.iconButtonWarning, attachmentWarningListener);
		View ratingButton = addAttachmentButton(controls, minHeight,
				R.attr.iconButtonRating, attachmentRatingListener);
		View removeButton = addAttachmentButton(controls, minHeight,
				R.attr.iconButtonCancel, attachmentRemoveListener);

		AttachmentHolder holder = new AttachmentHolder(view, fileName, fileSize, imageView,
				warningButton, ratingButton);
		warningButton.setTag(holder);
		ratingButton.setTag(holder);
		removeButton.setTag(holder);
		options.setTag(holder);
		attachments.add(holder);
		requireActivity().invalidateOptionsMenu();
		resizeComment(true);
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
		String fileSize = StringUtils.formatFileSize(size, false);
		Bitmap bitmap = null;
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
				if (Chan.getFallback().locator.isVideoExtension(fileHolder.getName())) {
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

	private void resizeComment(boolean post) {
		scrollView.removeCallbacks(resizeComment);
		if (post) {
			scrollView.post(resizeComment);
		} else {
			resizeComment.run();
		}
	}

	private final Runnable resizeComment = () -> {
		if (scrollView != null) {
			View postMain = scrollView.getChildAt(0);
			commentView.setMinLines(4);
			int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(postMain.getWidth(), View.MeasureSpec.EXACTLY);
			int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
			postMain.measure(widthMeasureSpec, heightMeasureSpec);
			int delta = scrollView.getHeight() - postMain.getMeasuredHeight();
			if (delta > 0) {
				commentView.setMinHeight(commentView.getMeasuredHeight() + delta);
			}
		}
	};

	private class MarkupButtonsBuilder implements ViewTreeObserver.OnGlobalLayoutListener, Runnable {
		private final boolean addPaddingToRoot;
		private int lastWidth;

		public MarkupButtonsBuilder(boolean addPaddingToRoot, int initialWidth) {
			this.addPaddingToRoot = addPaddingToRoot;
			textFormatView.getViewTreeObserver().addOnGlobalLayoutListener(this);
			lastWidth = initialWidth;
			fillContainer();
		}

		@Override
		public void onGlobalLayout() {
			if (textFormatView != null) {
				int width = textFormatView.getWidth();
				if (lastWidth != width) {
					lastWidth = width;
					textFormatView.removeCallbacks(this);
					textFormatView.post(this);
				}
			}
		}

		@Override
		public void run() {
			if (textFormatView != null) {
				fillContainer();
			}
		}

		private int lastSupportedTags;
		private int lastDisplayedTags;

		private void fillContainer() {
			float density = ResourceUtils.obtainDensity(getResources());
			int maxButtonsWidth = lastWidth - textFormatView.getPaddingLeft() - textFormatView.getPaddingRight();
			int buttonMarginLeft = (int) ((C.API_LOLLIPOP ? -4f : 0f) * density);
			Pair<Integer, Integer> supportedAndDisplayedTags = MarkupButtonProvider
					.obtainSupportedAndDisplayedTags(allowPosting ? Chan.get(getChanName()).markup : null,
							getBoardName(), density, maxButtonsWidth, buttonMarginLeft);
			int supportedTags = supportedAndDisplayedTags.first;
			int displayedTags = supportedAndDisplayedTags.second;
			if (lastSupportedTags == supportedTags && lastDisplayedTags == displayedTags) {
				return;
			}

			lastSupportedTags = supportedTags;
			lastDisplayedTags = displayedTags;
			if (commentEditor != null) {
				commentEditor.handleSimilar(supportedTags);
			}
			textFormatView.removeAllViews();
			boolean firstMarkupButton = true;
			for (MarkupButtonProvider provider : MarkupButtonProvider.iterable(displayedTags)) {
				Button button = provider.createButton(textFormatView.getContext(),
						android.R.attr.borderlessButtonStyle);
				ViewUtils.setTextSizeScaled(button, C.API_LOLLIPOP ? 14 : 18);
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
			textFormatView.setVisibility(textFormatView.getChildCount() > 0 ? View.VISIBLE : View.GONE);

			if (addPaddingToRoot) {
				int padding;
				if (textFormatView.getVisibility() != View.GONE) {
					int measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
					textFormatView.measure(measureSpec, measureSpec);
					padding = textFormatView.getMeasuredHeight();
				} else {
					padding = 0;
				}
				((ExpandedLayout) getView()).setExtraTop(padding);
			}
		}
	}
}
