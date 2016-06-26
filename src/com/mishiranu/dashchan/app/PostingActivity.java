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

package com.mishiranu.dashchan.app;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toolbar;

import chan.content.ApiException;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanMarkup;
import chan.content.ChanPerformer;
import chan.text.CommentEditor;
import chan.util.CommonUtils;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.app.service.PostingService;
import com.mishiranu.dashchan.async.AsyncManager;
import com.mishiranu.dashchan.async.ReadCaptchaTask;
import com.mishiranu.dashchan.async.SendPostTask;
import com.mishiranu.dashchan.content.CaptchaManager;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.content.storage.DraftsStorage;
import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.graphics.RoundedCornersDrawable;
import com.mishiranu.dashchan.graphics.TransparentTileDrawable;
import com.mishiranu.dashchan.net.RecaptchaReader;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.text.style.HeadingSpan;
import com.mishiranu.dashchan.text.style.MonospaceSpan;
import com.mishiranu.dashchan.text.style.ScriptSpan;
import com.mishiranu.dashchan.ui.CaptchaController;
import com.mishiranu.dashchan.ui.Replyable;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.PostDateFormatter;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.StringBlockBuilder;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DropdownView;
import com.mishiranu.dashchan.widget.ErrorEditTextSetter;

public class PostingActivity extends StateActivity implements View.OnClickListener, View.OnFocusChangeListener,
		ServiceConnection, PostingService.Callback, CaptchaController.Callback, AsyncManager.Callback,
		ReadCaptchaTask.Callback
{
	private String mChanName;
	private String mBoardName;
	private String mThreadNumber;
	private ChanConfiguration.Posting mPostingConfiguration;
	private CommentEditor mCommentEditor;
	
	private String mCaptchaType;
	private ChanPerformer.CaptchaState mCaptchaState;
	private ChanPerformer.CaptchaData mCaptchaData;
	private ChanConfiguration.Captcha mCaptchaConfiguration;
	private String mLoadedCaptchaType;
	private ChanConfiguration.Captcha.Input mLoadedCaptchaInput;
	private ChanConfiguration.Captcha.Validity mLoadedCaptchaValidity;
	private Bitmap mCaptchaImage;
	private boolean mCaptchaLarge;
	private boolean mCaptchaBlackAndWhite;
	private long mCaptchaLoadTime;
	private List<Pair<String, String>> mUserIconItems;
	private List<Pair<String, String>> mAttachmentRatingItems;
	
	private boolean mSaveDraftOnFinish = true;
	private boolean mWatchTripcodeWarning = false;
	
	private ResizingScrollView mScrollView;
	private EditText mCommentView;
	private CheckBox mSageCheckBox;
	private CheckBox mSpoilerCheckBox;
	private CheckBox mOriginalPosterCheckBox;
	private LinearLayout mAttachmentContainer;
	private EditText mNameView;
	private EditText mEmailView;
	private EditText mPasswordView;
	private EditText mSubjectView;
	private DropdownView mIconView;
	private ViewGroup mTextFormatView;
	private TextView mTripcodeWarning;
	private TextView mRemainingCharacters;
	
	private final CaptchaController mCaptchaController = new CaptchaController(this);
	private FrameLayout mFooterContainer;
	private Button mSendButton;
	private int mAttachmentColumnCount;
	
	private ArrayList<AttachmentHolder> mAttachments = new ArrayList<>();
	
	private final ClickableToast.Holder mClickableToastHolder = new ClickableToast.Holder(this);
	
	private PostingService.PostingBinder mBinder;
	
	private static abstract class AbsMarkupButton
	{
		public final int what;
		public final int widthDp;
		public final int priority;
		public final String text;
		
		public AbsMarkupButton(int what, int widthDp, int priority, String text)
		{
			this.what = what;
			this.widthDp = widthDp;
			this.priority = priority;
			this.text = text;
		}
		
		public abstract Object getSpan(Context context);
	}
	
	private static class MarkupButton extends AbsMarkupButton
	{
		public final Object span;
		
		public MarkupButton(int what, int widthDp, int priority, String text, Object span)
		{
			super(what, widthDp, priority, text);
			this.span = span;
		}
		
		@Override
		public Object getSpan(Context context)
		{
			return span;
		}
	}
	
	private static final ArrayList<AbsMarkupButton> MARKUP_BUTTONS = new ArrayList<>();
	
	static
	{
		MARKUP_BUTTONS.add(new MarkupButton(ChanMarkup.TAG_BOLD, 40, 0, "B", new StyleSpan(Typeface.BOLD)));
		MARKUP_BUTTONS.add(new MarkupButton(ChanMarkup.TAG_ITALIC, 40, 1, "I", new StyleSpan(Typeface.ITALIC)));
		MARKUP_BUTTONS.add(new MarkupButton(ChanMarkup.TAG_UNDERLINE, 40, 2, "U", new UnderlineSpan()));
		MARKUP_BUTTONS.add(new MarkupButton(ChanMarkup.TAG_STRIKE, 40, 3, "S", new StrikethroughSpan()));
		MARKUP_BUTTONS.add(new MarkupButton(ChanMarkup.TAG_CODE, 40, 6, "#", new MonospaceSpan(true)));
		MARKUP_BUTTONS.add(new MarkupButton(ChanMarkup.TAG_ASCII_ART, 44, 9, "AA", new MonospaceSpan(false)));
		MARKUP_BUTTONS.add(new MarkupButton(ChanMarkup.TAG_HEADING, 44, 7, "H", new HeadingSpan()));
		MARKUP_BUTTONS.add(new MarkupButton(ChanMarkup.TAG_SUBSCRIPT, 44, 8, "SUB", new ScriptSpan(false)));
		MARKUP_BUTTONS.add(new MarkupButton(ChanMarkup.TAG_SUPERSCRIPT, 44, 8, "SUP", new ScriptSpan(true)));
		MARKUP_BUTTONS.add(new AbsMarkupButton(ChanMarkup.TAG_SPOILER, 44, 5, "SP")
		{
			@Override
			public Object getSpan(Context context)
			{
				return new BackgroundColorSpan(ResourceUtils.getColor(context, R.attr.backgroundSpoiler));
			}
		});
		MARKUP_BUTTONS.add(new AbsMarkupButton(ChanMarkup.TAG_QUOTE, 40, 4, ">")
		{
			@Override
			public Object getSpan(Context context)
			{
				return C.API_LOLLIPOP ? null : new ForegroundColorSpan(ResourceUtils.getColor(context,
						R.attr.colorTextQuote));
			}
		});
	}

	private static final String EXTRA_SAVED_POST_DRAFT = "ExtraSavedPostDraft";
	private static final String EXTRA_SAVED_CAPTCHA = "ExtraSavedCaptcha";
	
	private static final String TASK_READ_CAPTCHA = "read_captcha";
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		if (C.API_LOLLIPOP)
		{
			requestWindowFeature(Window.FEATURE_NO_TITLE);
			Configuration configuration = getResources().getConfiguration();
			if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT
					|| configuration.smallestScreenWidthDp <= 360 && !C.API_MARSHMALLOW)
			{
				requestWindowFeature(Window.FEATURE_ACTION_MODE_OVERLAY);
			}
		}
		ResourceUtils.applyPreferredTheme(this);
		super.onCreate(savedInstanceState);
		bindService(new Intent(this, PostingService.class), this, BIND_AUTO_CREATE);
		mChanName = getIntent().getStringExtra(C.EXTRA_CHAN_NAME);
		mBoardName = getIntent().getStringExtra(C.EXTRA_BOARD_NAME);
		mThreadNumber = getIntent().getStringExtra(C.EXTRA_THREAD_NUMBER);
		if (mChanName == null) throw new IllegalStateException();
		ChanConfiguration chanConfiguration = ChanConfiguration.get(mChanName);
		ChanConfiguration.Posting posting = chanConfiguration.safe().obtainPosting(mBoardName, mThreadNumber == null);
		if (posting == null)
		{
			finish();
			return;
		}
		DraftsStorage draftsStorage = DraftsStorage.getInstance();
		mPostingConfiguration = posting;
		mCaptchaType = chanConfiguration.getCaptchaType();
		if (ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_JAVASCRIPT.equals(mCaptchaType) ||
				ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_1_JAVASCRIPT.equals(mCaptchaType))
		{
			boolean noCaptchaPass = true;
			String[] captchaPass = Preferences.getCaptchaPass(mChanName);
			if (captchaPass != null)
			{
				for (String value : captchaPass)
				{
					if (value != null)
					{
						noCaptchaPass = false;
						break;
					}
				}
			}
			if (noCaptchaPass) RecaptchaReader.getInstance().preloadWebView();
		}
		ChanMarkup markup = ChanMarkup.get(mChanName);
		mCommentEditor = markup.safe().obtainCommentEditor(mBoardName);
		Configuration configuration = getResources().getConfiguration();
		boolean needPassword = false;
		ChanConfiguration.Board board = chanConfiguration.safe().obtainBoard(mBoardName);
		if (board.allowDeleting)
		{
			ChanConfiguration.Deleting deleting = chanConfiguration.safe().obtainDeleting(mBoardName);
			needPassword = deleting != null && deleting.password;
		}
		boolean hugeCaptcha = Preferences.isHugeCaptcha();
		boolean longLayout = configuration.screenWidthDp >= 480;
		
		setContentView(R.layout.activity_posting);
		ClickableToast.register(mClickableToastHolder);
		if (C.API_LOLLIPOP)
		{
			Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
			setActionBar(toolbar);
		}
		getActionBar().setDisplayHomeAsUpEnabled(true);
		mScrollView = (ResizingScrollView) findViewById(R.id.scroll_view);
		mCommentView = (EditText) findViewById(R.id.comment);
		mSageCheckBox = (CheckBox) findViewById(R.id.sage_checkbox);
		mSpoilerCheckBox = (CheckBox) findViewById(R.id.spoiler_checkbox);
		mOriginalPosterCheckBox = (CheckBox) findViewById(R.id.original_poster_checkbox);
		mNameView = (EditText) findViewById(R.id.name);
		mEmailView = (EditText) findViewById(R.id.email);
		mPasswordView = (EditText) findViewById(R.id.password);
		mSubjectView = (EditText) findViewById(R.id.subject);
		mIconView = (DropdownView) findViewById(R.id.icon);
		mAttachmentContainer = (LinearLayout) findViewById(R.id.attachment_container);
		mFooterContainer = (FrameLayout) findViewById(R.id.footer_container);
		mTripcodeWarning = (TextView) findViewById(R.id.personal_tripcode_warning);
		mRemainingCharacters = (TextView) findViewById(R.id.remaining_characters);
		mCommentView.setOnFocusChangeListener(this);
		mNameView.addTextChangedListener(mNameEditListener);
		mCommentView.addTextChangedListener(mCommentEditListener);
		mWatchTripcodeWarning = posting.allowName && !posting.allowTripcode;
		mTextFormatView = (ViewGroup) findViewById(R.id.text_format_view);
		mUserIconItems = posting.userIcons.size() > 0 ? posting.userIcons : null;
		mAttachmentRatingItems = posting.attachmentRatings.size() > 0 ? posting.attachmentRatings : null;
		if (mUserIconItems != null)
		{
			ArrayList<String> items = new ArrayList<>();
			items.add(getString(R.string.text_no_icon));
			for (Pair<String, String> iconItem : mUserIconItems) items.add(iconItem.second);
			mIconView.setItems(items);
		}
		else mIconView.setVisibility(View.GONE);
		
		float density = ResourceUtils.obtainDensity(this);
		int handledButtons = 0;
		int maxButtonsWidth = getResources().getDisplayMetrics().widthPixels - mTextFormatView.getPaddingLeft()
				- mTextFormatView.getPaddingRight();
		int buttonsWidth = 0;
		int buttonMarginLeft = (int) ((C.API_LOLLIPOP ? -4f : 0f) * density);
		boolean[] allowMarkupButton = new boolean[MARKUP_BUTTONS.size()];
		int supportedTags = 0;
		for (int i = 0; handledButtons != MARKUP_BUTTONS.size(); i++)
		{
			boolean[] futureMarkupButton = new boolean[MARKUP_BUTTONS.size()];
			int futureWidth = buttonsWidth;
			for (int j = 0; j < MARKUP_BUTTONS.size(); j++)
			{
				AbsMarkupButton markupButton = MARKUP_BUTTONS.get(j);
				if (markupButton.priority == i)
				{
					if (markup.safe().isTagSupported(mBoardName, markupButton.what) ||
							markupButton.what == ChanMarkup.TAG_QUOTE)
					{
						int width = (int) (markupButton.widthDp * density);
						if (futureWidth > 0) width += buttonMarginLeft;
						futureWidth += width;
						futureMarkupButton[j] = true;
						supportedTags |= markupButton.what;
					}
					handledButtons++;
				}
			}
			if (futureWidth <= maxButtonsWidth)
			{
				buttonsWidth = futureWidth;
				for (int j = 0; j < allowMarkupButton.length; j++) allowMarkupButton[j] |= futureMarkupButton[j];
			}
		}
		if (mCommentEditor != null) mCommentEditor.handleSimilar(supportedTags);
		boolean firstMarkupButton = true;
		for (int i = 0; i < allowMarkupButton.length; i++)
		{
			if (allowMarkupButton[i])
			{
				AbsMarkupButton markupButton = MARKUP_BUTTONS.get(i);
				Button button = new Button(mTextFormatView.getContext(), null, android.R.attr.borderlessButtonStyle);
				button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, C.API_LOLLIPOP ? 14 : 18);
				LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams
						((int) (markupButton.widthDp * density), (int) (40f * density));
				if (!firstMarkupButton) layoutParams.leftMargin = buttonMarginLeft;
				button.setTag(markupButton.what);
				button.setOnClickListener(mFormatButtonClickListener);
				button.setPadding(0, 0, 0, 0);
				if (C.API_LOLLIPOP) button.setAllCaps(false);
				Object span = markupButton.getSpan(this);
				if (span != null)
				{
					SpannableString spannable = new SpannableString(markupButton.text);
					spannable.setSpan(span, 0, spannable.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
					button.setText(spannable);
				}
				else button.setText(markupButton.text);
				mTextFormatView.addView(button, layoutParams);
				firstMarkupButton = false;
			}
		}
		
		boolean longFooter = longLayout && !hugeCaptcha;
		int resId = longFooter ? R.layout.activity_posting_footer_long : R.layout.activity_posting_footer_common;
		getLayoutInflater().inflate(resId, mFooterContainer);
		mCaptchaConfiguration = chanConfiguration.safe().obtainCaptcha(mCaptchaType);
		View captchaInputParentView = mFooterContainer.findViewById(R.id.captcha_input_parent);
		EditText captchaInputView = (EditText) mFooterContainer.findViewById(R.id.captcha_input);
		mCaptchaController.setupViews(mFooterContainer, captchaInputParentView, captchaInputView,
				!longFooter, mCaptchaConfiguration);
		mSendButton = (Button) mFooterContainer.findViewById(R.id.send_button);
		mSendButton.setOnClickListener(this);
		int columnCount = configuration.screenWidthDp >= 960 ? 4 : configuration.screenWidthDp >= 480 ? 2 : 1;
		mAttachmentColumnCount = columnCount;
		
		mNameView.setVisibility(posting.allowName ? View.VISIBLE : View.GONE);
		mEmailView.setVisibility(posting.allowEmail ? View.VISIBLE : View.GONE);
		mPasswordView.setVisibility(needPassword ? View.VISIBLE : View.GONE);
		mSubjectView.setVisibility(posting.allowSubject ? View.VISIBLE : View.GONE);
		mSageCheckBox.setVisibility(posting.optionSage ? View.VISIBLE : View.GONE);
		mSpoilerCheckBox.setVisibility(posting.optionSpoiler ? View.VISIBLE : View.GONE);
		mOriginalPosterCheckBox.setVisibility(posting.optionOriginalPoster ? View.VISIBLE : View.GONE);
		if (!posting.optionSage && !posting.optionSpoiler && !posting.optionOriginalPoster)
		{
			findViewById(R.id.checkbox_parent).setVisibility(View.GONE);
		}
		boolean hidePersonalDataBlock = Preferences.isHidePersonalData();
		if (!hidePersonalDataBlock)
		{
			hidePersonalDataBlock = !posting.allowName && !posting.allowEmail &&
					!needPassword && mUserIconItems == null;
		}
		if (hidePersonalDataBlock) findViewById(R.id.personal_data_block).setVisibility(View.GONE);
		
		DraftsStorage.ThreadDraft threadDraft = draftsStorage.getThreadDraft(mChanName, mBoardName, mThreadNumber);
		if (threadDraft != null)
		{
			if (!Preferences.isHidePersonalData())
			{
				if (posting.allowName) mNameView.setText(threadDraft.name);
				mEmailView.setText(threadDraft.email);
				mPasswordView.setText(threadDraft.password);
			}
			if (posting.optionSage) mSageCheckBox.setChecked(threadDraft.optionSage);
			mOriginalPosterCheckBox.setChecked(threadDraft.optionOriginalPoster);
			applyUserIcon(threadDraft.userIcon);
		}
		
		StringBuilder builder = new StringBuilder();
		int commentCarriage = 0;
		
		DraftsStorage.PostDraft postDraft;
		if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_SAVED_POST_DRAFT))
		{
			try
			{
				postDraft = DraftsStorage.PostDraft.fromJsonObject(new JSONObject(savedInstanceState
						.getString(EXTRA_SAVED_POST_DRAFT)));
			}
			catch (JSONException e)
			{
				postDraft = null;
			}
		}
		else postDraft = draftsStorage.getPostDraft(mChanName, mBoardName, mThreadNumber);
		if (postDraft != null)
		{
			if (!StringUtils.isEmpty(postDraft.comment))
			{
				builder.append(postDraft.comment);
				commentCarriage = postDraft.commentCarriage;
			}
			DraftsStorage.AttachmentDraft[] attachmentDrafts = postDraft.attachmentDrafts;
			if (attachmentDrafts != null)
			{
				for (DraftsStorage.AttachmentDraft attachmentDraft : attachmentDrafts)
				{
					addAttachment(attachmentDraft.fileHolder, attachmentDraft.rating, attachmentDraft.optionUniqueHash,
							attachmentDraft.optionRemoveMetadata, attachmentDraft.optionRemoveFileName,
							attachmentDraft.optionSpoiler);
				}
			}
			if (!Preferences.isHidePersonalData())
			{
				if (posting.allowName) mNameView.setText(postDraft.name);
				mEmailView.setText(postDraft.email);
				mPasswordView.setText(postDraft.password);
			}
			if (posting.allowSubject) mSubjectView.setText(postDraft.subject);
			if (posting.optionSage) mSageCheckBox.setChecked(postDraft.optionSage);
			if (posting.optionSpoiler) mSageCheckBox.setChecked(postDraft.optionSpoiler);
			mOriginalPosterCheckBox.setChecked(postDraft.optionOriginalPoster);
			applyUserIcon(postDraft.userIcon);
		}
		
		boolean captchaRestoreSuccess = false;
		if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_SAVED_CAPTCHA))
		{
			DraftsStorage.CaptchaDraft captchaDraft = savedInstanceState.getParcelable(EXTRA_SAVED_CAPTCHA);
			if (captchaDraft.captchaState != null)
			{
				mCaptchaLoadTime = captchaDraft.loadTime;
				showCaptcha(captchaDraft.captchaState, captchaDraft.captchaData, captchaDraft.loadedCaptchaType,
						captchaDraft.loadedInput, captchaDraft.loadedValidity, captchaDraft.image,
						captchaDraft.large, captchaDraft.blackAndWhite);
				mCaptchaController.setText(captchaDraft.text);
				captchaRestoreSuccess = true;
			}
		}
		else
		{
			DraftsStorage.CaptchaDraft captchaDraft = draftsStorage.getCaptchaDraft(mChanName);
			if (captchaDraft != null && captchaDraft.loadedCaptchaType == null)
			{
				mCaptchaLoadTime = captchaDraft.loadTime;
				ChanConfiguration.Captcha.Validity captchaValidity = mCaptchaConfiguration.validity;
				if (captchaValidity == null) captchaValidity = ChanConfiguration.Captcha.Validity.SHORT_LIFETIME;
				if (captchaDraft.loadedValidity != null)
				{
					ChanConfiguration.Captcha.Validity loadedCaptchaValidity = captchaDraft.loadedValidity;
					if (captchaDraft.captchaState != ChanPerformer.CaptchaState.CAPTCHA ||
							captchaValidity.compareTo(loadedCaptchaValidity) >= 0)
					{
						// Allow only reducing of validity
						captchaValidity = loadedCaptchaValidity;
					}
				}
				boolean canLoadState = false;
				switch (captchaValidity)
				{
					case SHORT_LIFETIME:
					{
						canLoadState = false;
						break;
					}
					case IN_THREAD:
					{
						canLoadState = StringUtils.equals(mBoardName, captchaDraft.boardName)
								&& StringUtils.equals(mThreadNumber, captchaDraft.threadNumber);
						break;
					}
					case IN_BOARD_SEPARATELY:
					{
						canLoadState = StringUtils.equals(mBoardName, captchaDraft.boardName)
								&& ((mThreadNumber == null) == (captchaDraft.threadNumber == null));
						break;
					}
					case IN_BOARD:
					{
						canLoadState = StringUtils.equals(mBoardName, captchaDraft.boardName);
						break;
					}
					case LONG_LIFETIME:
					{
						canLoadState = true;
						break;
					}
				}
				if (canLoadState && StringUtils.equals(mCaptchaType, captchaDraft.captchaType))
				{
					if (captchaDraft.captchaState == ChanPerformer.CaptchaState.CAPTCHA && captchaDraft.image != null)
					{
						showCaptcha(ChanPerformer.CaptchaState.CAPTCHA, captchaDraft.captchaData, null,
								captchaDraft.loadedInput, captchaDraft.loadedValidity,
								captchaDraft.image, captchaDraft.large, captchaDraft.blackAndWhite);
						mCaptchaController.setText(captchaDraft.text);
						captchaRestoreSuccess = true;
					}
					else if (canLoadState && (captchaDraft.captchaState == ChanPerformer.CaptchaState.SKIP
							|| captchaDraft.captchaState == ChanPerformer.CaptchaState.PASS))
					{
						showCaptcha(captchaDraft.captchaState, captchaDraft.captchaData, null, null,
								captchaDraft.loadedValidity, null, false, false);
						captchaRestoreSuccess = true;
					}
				}
			}
		}
		
		Parcelable[] parcelableArray = savedInstanceState != null ? null
				: getIntent().getParcelableArrayExtra(C.EXTRA_REPLY_DATA);
		if (parcelableArray != null)
		{
			boolean onlyLinks = true;
			for (int i = 0; i < parcelableArray.length; i++)
			{
				Replyable.ReplyData data = (Replyable.ReplyData) parcelableArray[i];
				if (!StringUtils.isEmpty(data.comment))
				{
					onlyLinks = false;
				}
			}
			for (int i = 0; i < parcelableArray.length; i++)
			{
				boolean lastLink = i == parcelableArray.length - 1;
				Replyable.ReplyData data = (Replyable.ReplyData) parcelableArray[i];
				String postNumber = data.postNumber;
				String comment = data.comment;
				if (!StringUtils.isEmpty(postNumber))
				{
					String link = ">>" + postNumber;
					// Check if user replies to the same post
					int index = builder.lastIndexOf(link, commentCarriage);
					if (index < 0 || index < commentCarriage && commentCarriage <= builder.length() &&
							builder.substring(index, commentCarriage).indexOf("\n>>") >= 0)
					{
						boolean afterSpace = false; // If user wants to add link at the same line
						if (commentCarriage > 0 && commentCarriage <= builder.length())
						{
							char charBefore = builder.charAt(commentCarriage - 1);
							if (charBefore != '\n')
							{
								if (charBefore == ' ' && onlyLinks) afterSpace = true; else
								{
									// Ensure free line before link 
									builder.insert(commentCarriage++, '\n');
								}
							}
						}
						builder.insert(commentCarriage, link);
						commentCarriage += link.length();
						if (afterSpace)
						{
							if (!lastLink)
							{
								builder.insert(commentCarriage, ", ");
								commentCarriage += 2;
							}
						}
						else
						{
							builder.insert(commentCarriage++, '\n');
							if (commentCarriage < builder.length())
							{
								if (builder.charAt(commentCarriage) != '\n')
								{
									// Ensure free line for typing
									builder.insert(commentCarriage, '\n');
								}
							}
						}
					}
				}
				if (!StringUtils.isEmpty(comment))
				{
					if (commentCarriage > 0 && commentCarriage <= builder.length() &&
							builder.charAt(commentCarriage - 1) != '\n')
					{
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
		
		mCommentView.setText(builder);
		mCommentView.setSelection(commentCarriage);
		mCommentView.requestFocus();
		setTitle(StringUtils.isEmpty(mThreadNumber) ? R.string.text_new_thread : R.string.text_new_post);
		if (!captchaRestoreSuccess) refreshCaptcha(false, true, false);
		
		PostingService.FailResult failResult = savedInstanceState != null ? null
				: (PostingService.FailResult) getIntent().getSerializableExtra(C.EXTRA_FAIL_RESULT);
		if (failResult != null)
		{
			onSendPostFail(failResult.errorItem, failResult.extra, failResult.captchaError, failResult.keepCaptcha);
		}
	}
	
	private DraftsStorage.PostDraft obtainPostDraft()
	{
		DraftsStorage.AttachmentDraft[] attachmentDrafts = null;
		if (mAttachments.size() > 0)
		{
			attachmentDrafts = new DraftsStorage.AttachmentDraft[mAttachments.size()];
			for (int i = 0; i < attachmentDrafts.length; i++)
			{
				AttachmentHolder holder = mAttachments.get(i);
				attachmentDrafts[i] = new DraftsStorage.AttachmentDraft(holder.fileHolder, holder.rating,
						holder.optionUniqueHash, holder.optionRemoveMetadata, holder.optionRemoveFileName,
						holder.optionSpoiler);
			}
		}
		String subject = mSubjectView.getText().toString();
		String comment = mCommentView.getText().toString();
		int commentCarriage = mCommentView.getSelectionEnd();
		String name = mNameView.getText().toString();
		String email = mEmailView.getText().toString();
		String password = mPasswordView.getText().toString();
		boolean optionSage = mSageCheckBox.isChecked();
		boolean optionSpoiler = mSpoilerCheckBox.isChecked();
		boolean optionOriginalPoster = mOriginalPosterCheckBox.isChecked();
		String userIcon = getUserIcon();
		return new DraftsStorage.PostDraft(mChanName, mBoardName, mThreadNumber, subject, comment, commentCarriage,
				attachmentDrafts, name, email, password, optionSage, optionSpoiler, optionOriginalPoster, userIcon);
	}
	
	private DraftsStorage.CaptchaDraft obtainCaptchaDraft()
	{
		String input = mCaptchaController.getInput();
		return new DraftsStorage.CaptchaDraft(mCaptchaType, mCaptchaState, mCaptchaData, mLoadedCaptchaType,
				mLoadedCaptchaInput, mLoadedCaptchaValidity, input, mCaptchaImage, mCaptchaLarge,
				mCaptchaBlackAndWhite, mCaptchaLoadTime, mBoardName, mThreadNumber);
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		try
		{
			outState.putString(EXTRA_SAVED_POST_DRAFT, obtainPostDraft().toJsonObject().toString());
		}
		catch (JSONException e)
		{
			
		}
		outState.putParcelable(EXTRA_SAVED_CAPTCHA, obtainCaptchaDraft());
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();
		mClickableToastHolder.onResume();
		CaptchaManager.registerForeground(this);
	}
	
	@Override
	protected void onPause()
	{
		super.onPause();
		mClickableToastHolder.onPause();
	}
	
	@Override
	protected void onFinish()
	{
		super.onFinish();
		if (mBinder != null)
		{
			mBinder.unregister(this);
			mBinder = null;
		}
		unbindService(this);
		CaptchaManager.unregisterForeground(this);
		ClickableToast.unregister(mClickableToastHolder);
		if (mSaveDraftOnFinish)
		{
			DraftsStorage draftsStorage = DraftsStorage.getInstance();
			draftsStorage.store(obtainPostDraft());
			draftsStorage.store(mChanName, obtainCaptchaDraft());
		}
	}
	
	@Override
	public void onServiceConnected(ComponentName name, IBinder service)
	{
		mBinder = (PostingService.PostingBinder) service;
		mBinder.register(this, mChanName, mBoardName, mThreadNumber);
	}
	
	@Override
	public void onServiceDisconnected(ComponentName name)
	{
		if (mBinder != null)
		{
			mBinder.unregister(this);
			mBinder = null;
		}
	}
	
	@Override
	public void onClick(View v)
	{
		if (v == mSendButton)
		{
			executeSendPost();
		}
	}
	
	@Override
	public void onRefreshCapctha(boolean forceRefresh)
	{
		refreshCaptcha(forceRefresh, false, true);
	}
	
	@Override
	public void onConfirmCaptcha()
	{
		executeSendPost();
	}
	
	@Override
	public void onFocusChange(View v, boolean hasFocus)
	{
		if (v == mCommentView)
		{
			updateFocusButtons(hasFocus);
		}
	}
	
	private final TextWatcher mNameEditListener = new TextWatcher()
	{
		private ForegroundColorSpan mTripcodeSpan;
		
		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count)
		{
			if (mWatchTripcodeWarning)
			{
				mTripcodeWarning.setVisibility(s.toString().indexOf('#') >= 0 ? View.VISIBLE : View.GONE);
			}
		}
		
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after)
		{
			
		}
		
		@Override
		public void afterTextChanged(Editable s)
		{
			if (!mWatchTripcodeWarning)
			{
				if (mTripcodeSpan != null) s.removeSpan(mTripcodeSpan);
				int index = s.toString().indexOf('#');
				if (index >= 0)
				{
					if (mTripcodeSpan == null)
					{
						mTripcodeSpan = new ForegroundColorSpan(ResourceUtils.getColor(PostingActivity.this,
								R.attr.colorTextTripcode));
					}
					s.setSpan(mTripcodeSpan, index, s.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
			}
		}
	};
	
	private final TextWatcher mCommentEditListener = new TextWatcher()
	{
		private boolean mShow = false;
		private boolean mError = false;
		
		private ErrorEditTextSetter mErrorSetter;
		
		private CharsetEncoder mEncoder;
		private boolean mEncoderReady = false;
		private ByteBuffer mByteBuffer = null;
		
		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count)
		{
			int length = 0;
			int maxCommentLength = 0;
			boolean show = false;
			if (mPostingConfiguration != null)
			{
				maxCommentLength = mPostingConfiguration.maxCommentLength;
				int threshold = Math.min(maxCommentLength / 2, 1000);
				length = s.length();
				if (!mEncoderReady)
				{
					mEncoderReady = true;
					String encoding = mPostingConfiguration.maxCommentLengthEncoding;
					if (encoding != null)
					{
						try
						{
							mEncoder = Charset.forName(encoding).newEncoder();
						}
						catch (Exception e)
						{
							
						}
					}
				}
				if (mEncoder != null)
				{
					int capacity = (int) (Math.max(100, length) * mEncoder.maxBytesPerChar());
					if (mByteBuffer == null || mByteBuffer.capacity() < capacity)
					{
						mByteBuffer = ByteBuffer.allocate(4 * capacity);
					}
					else mByteBuffer.rewind();
					mEncoder.reset();
					mEncoder.encode(CharBuffer.wrap(s), mByteBuffer, true);
					length = mByteBuffer.position();
				}
				show = threshold > 0 && length >= threshold;
			}
			boolean error = show && length > maxCommentLength;
			if (mShow != show)
			{
				mRemainingCharacters.setVisibility(show ? View.VISIBLE : View.GONE);
				mScrollView.postResizeComment();
				mShow = show;
			}
			if (mError != error || mRemainingCharacters.getText().length() == 0)
			{
				int color = ResourceUtils.getColor(PostingActivity.this, error ? R.attr.colorTextError
						: android.R.attr.textColorSecondary);
				mRemainingCharacters.setTextColor(color);
				if (C.API_LOLLIPOP)
				{
					if (mErrorSetter == null) mErrorSetter = new ErrorEditTextSetter(mCommentView);
					mErrorSetter.setError(error);
				}
				mError = error;
			}
			if (show) mRemainingCharacters.setText(length + " / " + maxCommentLength);
			if (before == 0 && count == 1)
			{
				char c = s.charAt(start);
				if (c == '\n' || c == '.') DraftsStorage.getInstance().store(obtainPostDraft());
			}
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
	
	private void applyUserIcon(String icon)
	{
		if (mUserIconItems != null && icon != null)
		{
			for (int i = 0; i < mUserIconItems.size(); i++)
			{
				if (icon.equals(mUserIconItems.get(i).second))
				{
					mIconView.setSelection(i + 1);
					break;
				}
			}
		}
	}
	
	private String getUserIcon()
	{
		if (mUserIconItems != null)
		{
			int position = mIconView.getSelectedItemPosition() - 1;
			if (position >= 0) return mUserIconItems.get(position).second;
		}
		return null;
	}
	
	private static final int OPTIONS_MENU_ATTACH = 0;
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		ActionIconSet set = new ActionIconSet(this);
		menu.add(0, OPTIONS_MENU_ATTACH, 0, R.string.action_attach).setIcon(set.getId(R.attr.actionAttach))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		menu.findItem(OPTIONS_MENU_ATTACH).setVisible(mAttachments.size() < mPostingConfiguration.attachmentCount);
		return super.onPrepareOptionsMenu(menu);
	}
	
	private void handleMimeTypeGroup(ArrayList<String> list, Collection<String> mimeTypes, String mimeTypeGroup)
	{
		String allSubMimeTypes = mimeTypeGroup + "*";
		if (mimeTypes.contains(allSubMimeTypes)) list.add(allSubMimeTypes);
		for (String mimeType : mimeTypes)
		{
			if (mimeType.startsWith(mimeTypeGroup) && !allSubMimeTypes.equals(mimeType)) list.add(mimeType);
		}
	}
	
	private ArrayList<String> buildMimeTypeList(Collection<String> mimeTypes)
	{
		ArrayList<String> list = new ArrayList<>();
		handleMimeTypeGroup(list, mimeTypes, "image/");
		handleMimeTypeGroup(list, mimeTypes, "video/");
		handleMimeTypeGroup(list, mimeTypes, "audio/");
		for (String mimeType : mimeTypes)
		{
			if (!list.contains(mimeType)) list.add(mimeType);
		}
		return list;
	}
	
	@TargetApi(Build.VERSION_CODES.KITKAT)
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
			{
				finish();
				break;
			}
			case OPTIONS_MENU_ATTACH:
			{
				// SHOW_ADVANCED to show folder navigation
				Intent intent = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE)
						.putExtra("android.content.extra.SHOW_ADVANCED", true);
				StringBuilder typesBuilder = new StringBuilder();
				ArrayList<String> mimeTypes = buildMimeTypeList(mPostingConfiguration.attachmentMimeTypes);
				for (String type : mimeTypes)
				{
					if (typesBuilder.length() > 0) typesBuilder.append(',');
					typesBuilder.append(type);
				}
				if (typesBuilder.length() > 0) intent.setType(typesBuilder.toString());
				if (C.API_KITKAT)
				{
					if (mimeTypes.size() > 0)
					{
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
	
	private void updateFocusButtons(boolean commentFocused)
	{
		if (C.API_LOLLIPOP)
		{
			for (int i = 0; i < mTextFormatView.getChildCount(); i++)
			{
				mTextFormatView.getChildAt(i).setEnabled(commentFocused);
			}
		}
	}
	
	private final View.OnClickListener mFormatButtonClickListener = new View.OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			int what = (int) v.getTag();
			switch (what)
			{
				case ChanMarkup.TAG_BOLD:
				case ChanMarkup.TAG_ITALIC:
				case ChanMarkup.TAG_UNDERLINE:
				case ChanMarkup.TAG_STRIKE:
				case ChanMarkup.TAG_SUBSCRIPT:
				case ChanMarkup.TAG_SUPERSCRIPT:
				case ChanMarkup.TAG_SPOILER:
				case ChanMarkup.TAG_CODE:
				case ChanMarkup.TAG_ASCII_ART:
				case ChanMarkup.TAG_HEADING:
				{
					mCommentEditor.formatSelectedText(mCommentView, what);
					break;
				}
				case ChanMarkup.TAG_QUOTE:
				{
					formatQuote();
					break;
				}
			}
		}
	};
	
	private void executeSendPost()
	{
		if (mBinder == null) return;
		String subject = StringUtils.nullIfEmpty(mSubjectView.getText().toString());
		String comment = StringUtils.nullIfEmpty(mCommentView.getText().toString());
		String name = StringUtils.nullIfEmpty(mNameView.getText().toString());
		String email = StringUtils.nullIfEmpty(mEmailView.getText().toString());
		String password = StringUtils.nullIfEmpty(mPasswordView.getText().toString());
		if (password == null) password = Preferences.getPassword(mChanName);
		boolean optionSage = mSageCheckBox.isChecked();
		boolean optionSpoiler = mSpoilerCheckBox.isChecked();
		boolean optionOriginalPoster = mOriginalPosterCheckBox.isChecked();
		ArrayList<ChanPerformer.SendPostData.Attachment> array = new ArrayList<>();
		for (int i = 0; i < mAttachments.size(); i++)
		{
			AttachmentHolder data = mAttachments.get(i);
			array.add(new ChanPerformer.SendPostData.Attachment(data.fileHolder, data.rating, data.optionUniqueHash,
					data.optionRemoveMetadata, data.optionRemoveFileName, data.optionSpoiler));
		}
		ChanPerformer.SendPostData.Attachment[] attachments = null;
		if (array.size() > 0) attachments = CommonUtils.toArray(array, ChanPerformer.SendPostData.Attachment.class);
		String userIcon = null;
		if (mUserIconItems != null)
		{
			int position = mIconView.getSelectedItemPosition() - 1;
			if (position >= 0) userIcon = mUserIconItems.get(position).first;
		}
		ChanPerformer.CaptchaData captchaData = mCaptchaData;
		if (captchaData != null) captchaData.put(ChanPerformer.CaptchaData.INPUT, mCaptchaController.getInput());
		String captchaType = mLoadedCaptchaType != null ? mLoadedCaptchaType : mCaptchaType;
		ChanPerformer.SendPostData data = new ChanPerformer.SendPostData(mBoardName, mThreadNumber,
				subject, comment, name, email, password, attachments, optionSage, optionSpoiler, optionOriginalPoster,
				userIcon, captchaType, captchaData, 15000, 45000);
		DraftsStorage.getInstance().store(obtainPostDraft());
		mBinder.executeSendPost(mChanName, data);
		mSendButton.setEnabled(false);
	}
	
	private ProgressDialog mProgressDialog;
	
	private final DialogInterface.OnCancelListener mSendPostCancelListener = new DialogInterface.OnCancelListener()
	{
		@Override
		public void onCancel(DialogInterface dialog)
		{
			mProgressDialog = null;
			mBinder.cancelSendPost(mChanName, mBoardName, mThreadNumber);
		}
	};
	
	private final DialogInterface.OnClickListener mSendPostMinimizeListener = new DialogInterface.OnClickListener()
	{
		@Override
		public void onClick(DialogInterface dialog, int which)
		{
			mProgressDialog = null;
			finish();
		}
	};
	
	private void dismissSendPost()
	{
		if (mProgressDialog != null) mProgressDialog.dismiss();
		mProgressDialog = null;
		mSendButton.setEnabled(true);
	}
	
	@Override
	public void onSendPostStart(boolean progressMode)
	{
		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setCancelable(true);
		mProgressDialog.setCanceledOnTouchOutside(false);
		if (progressMode)
		{
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgressDialog.setProgressNumberFormat("%1$d / %2$d KB");
		}
		mProgressDialog.setOnCancelListener(mSendPostCancelListener);
		mProgressDialog.setButton(ProgressDialog.BUTTON_POSITIVE, getString(R.string.action_minimize),
				mSendPostMinimizeListener);
		onSendPostChangeProgressState(progressMode, SendPostTask.ProgressState.CONNECTING, -1, -1);
		mProgressDialog.show();
	}
	
	@Override
	public void onSendPostChangeProgressState(boolean progressMode, SendPostTask.ProgressState progressState,
			int attachmentIndex, int attachmentsCount)
	{
		if (mProgressDialog != null)
		{
			switch (progressState)
			{
				case CONNECTING:
				{
					mProgressDialog.setMax(1);
					mProgressDialog.setIndeterminate(true);
					mProgressDialog.setMessage(getString(R.string.message_sending));
					break;
				}
				case SENDING:
				{
					mProgressDialog.setIndeterminate(false);
					if (progressMode)
					{
						mProgressDialog.setMessage(getString(R.string.message_sending_index_format, attachmentIndex + 1,
								attachmentsCount));
					}
					else mProgressDialog.setMessage(getString(R.string.message_sending));
					break;
				}
				case PROCESSING:
				{
					mProgressDialog.setIndeterminate(false);
					mProgressDialog.setMessage(getString(R.string.message_processing_data));
					break;
				}
			}
		}
	}
	
	@Override
	public void onSendPostChangeProgressValue(int progress, int progressMax)
	{
		if (mProgressDialog != null)
		{
			mProgressDialog.setMax(progressMax);
			mProgressDialog.setProgress(progress);
		}
	}
	
	@Override
	public void onSendPostSuccess(Intent intent)
	{
		dismissSendPost();
		mSaveDraftOnFinish = false;
		setResult(RESULT_OK, intent);
		finish();
	}
	
	@Override
	public void onSendPostFail(ErrorItem errorItem, final Object extra, boolean captchaError, boolean keepCaptcha)
	{
		dismissSendPost();
		if (extra != null)
		{
			ClickableToast.show(this, errorItem.toString(), getString(R.string.action_details), new Runnable()
			{
				@Override
				public void run()
				{
					String message = null;
					if (extra instanceof ApiException.BanExtra)
					{
						StringBlockBuilder builder = new StringBlockBuilder();
						PostDateFormatter formatter = new PostDateFormatter(PostingActivity.this);
						ApiException.BanExtra banExtra = (ApiException.BanExtra) extra;
						if (!StringUtils.isEmpty(banExtra.id))
						{
							builder.appendLine(getString(R.string.text_ban_id_format, banExtra.id));
						}
						if (banExtra.startDate > 0L)
						{
							builder.appendLine(getString(R.string.text_ban_start_date_format,
									formatter.format(banExtra.startDate)));
						}
						if (banExtra.expireDate > 0L)
						{
							builder.appendLine(getString(R.string.text_ban_expires_format,
									banExtra.expireDate == Long.MAX_VALUE ? getString(R.string.text_ban_expires_never)
									: formatter.format(banExtra.expireDate)));
						}
						if (!StringUtils.isEmpty(banExtra.message))
						{
							builder.appendLine(getString(R.string.text_ban_reason_format, banExtra.message));
						}
						message = builder.toString();
					}
					else if (extra instanceof ApiException.WordsExtra)
					{
						StringBuilder builder = new StringBuilder();
						ApiException.WordsExtra words = (ApiException.WordsExtra) extra;
						builder.append(getString(R.string.text_rejected_words)).append(": ");
						boolean first = true;
						for (String word : words.words)
						{
							if (first) first = false; else builder.append(", ");
							builder.append(word);
						}
						message = builder.toString();
					}
					AlertDialog dialog = new AlertDialog.Builder(PostingActivity.this).setTitle(R.string.action_details)
							.setMessage(message).setPositiveButton(android.R.string.ok, null).create();
					dialog.setOnShowListener(ViewUtils.ALERT_DIALOG_MESSAGE_SELECTABLE);
					dialog.show();
				}
			}, false);
		}
		else ClickableToast.show(this, errorItem.toString());
		if (errorItem.httpResponseCode == 0 && !keepCaptcha) refreshCaptcha(false, !captchaError, true);
	}
	
	@Override
	public void onSendPostCancel()
	{
		dismissSendPost();
	}
	
	private static final String EXTRA_FORCE_CAPTCHA = "forceCaptcha";
	private static final String EXTRA_MAY_SHOW_LOAD_BUTTON = "mayShowLoadButton";
	
	private void refreshCaptcha(boolean forceCaptcha, boolean mayShowLoadButton, boolean restart)
	{
		mCaptchaState = null;
		mLoadedCaptchaType = null;
		mSendButton.setEnabled(false);
		mCaptchaController.showLoading();
		HashMap<String, Object> extra = new HashMap<>();
		extra.put(EXTRA_FORCE_CAPTCHA, forceCaptcha);
		extra.put(EXTRA_MAY_SHOW_LOAD_BUTTON, mayShowLoadButton);
		AsyncManager.get(this).startTask(TASK_READ_CAPTCHA, this, extra, restart);
	}
	
	private static class ReadCaptchaHolder extends AsyncManager.Holder implements ReadCaptchaTask.Callback
	{
		@Override
		public void onReadCaptchaSuccess(ChanPerformer.CaptchaState captchaState, ChanPerformer.CaptchaData captchaData,
				String captchaType, ChanConfiguration.Captcha.Input input, ChanConfiguration.Captcha.Validity validity,
				Bitmap image, boolean large, boolean blackAndWhite)
		{
			storeResult("onReadCaptchaSuccess", captchaState, captchaData, captchaType, input, validity,
					image, large, blackAndWhite);
		}
		
		@Override
		public void onReadCaptchaError(ErrorItem errorItem)
		{
			storeResult("onReadCaptchaError", errorItem);
		}
	}
	
	@Override
	public Pair<Object, AsyncManager.Holder> onCreateAndExecuteTask(String name, HashMap<String, Object> extra)
	{
		boolean forceCaptcha = (boolean) extra.get(EXTRA_FORCE_CAPTCHA);
		boolean mayShowLoadButton = (boolean) extra.get(EXTRA_MAY_SHOW_LOAD_BUTTON);
		String[] captchaPass = forceCaptcha ? null : Preferences.getCaptchaPass(mChanName);
		String requirement = mThreadNumber != null ? ChanPerformer.ReadCaptchaData.REQUIREMENT_REPLY_TO_THREAD
				: ChanPerformer.ReadCaptchaData.REQUIREMENT_NEW_THREAD;
		ReadCaptchaHolder holder = new ReadCaptchaHolder();
		ReadCaptchaTask task = new ReadCaptchaTask(this, holder, null, mCaptchaType, requirement, captchaPass,
				mayShowLoadButton, mChanName, mBoardName, mThreadNumber);
		task.executeOnExecutor(ReadCaptchaTask.THREAD_POOL_EXECUTOR);
		return new Pair<Object, AsyncManager.Holder>(task, holder);
	}
	
	@Override
	public void onFinishTaskExecution(String name, AsyncManager.Holder holder)
	{
		String methodName = holder.nextArgument();
		if ("onReadCaptchaSuccess".equals(methodName))
		{
			ChanPerformer.CaptchaState captchaState = holder.nextArgument();
			ChanPerformer.CaptchaData captchaData = holder.nextArgument();
			String captchaType = holder.nextArgument();
			ChanConfiguration.Captcha.Input input = holder.nextArgument();
			ChanConfiguration.Captcha.Validity validity = holder.nextArgument();
			Bitmap image = holder.nextArgument();
			boolean large = holder.nextArgument();
			boolean blackAndWhite = holder.nextArgument();
			onReadCaptchaSuccess(captchaState, captchaData, captchaType, input, validity, image, large, blackAndWhite);
		}
		else if ("onReadCaptchaError".equals(methodName))
		{
			ErrorItem errorItem = holder.nextArgument();
			onReadCaptchaError(errorItem);
		}
	}
	
	@Override
	public void onRequestTaskCancel(String name, Object task)
	{
		((ReadCaptchaTask) task).cancel();
	}
	
	@Override
	public void onReadCaptchaSuccess(ChanPerformer.CaptchaState captchaState, ChanPerformer.CaptchaData captchaData,
			String captchaType, ChanConfiguration.Captcha.Input input, ChanConfiguration.Captcha.Validity validity,
			Bitmap image, boolean large, boolean blackAndWhite)
	{
		mCaptchaLoadTime = System.currentTimeMillis();
		showCaptcha(captchaState, captchaData, captchaType, input, validity, image, large, blackAndWhite);
	}
	
	@Override
	public void onReadCaptchaError(ErrorItem errorItem)
	{
		ClickableToast.show(this, errorItem.toString());
		mCaptchaLoadTime = 0L;
		mSendButton.setEnabled(true);
		mCaptchaController.showError();
	}
	
	private void showCaptcha(ChanPerformer.CaptchaState captchaState, ChanPerformer.CaptchaData captchaData,
			String captchaType, ChanConfiguration.Captcha.Input input, ChanConfiguration.Captcha.Validity validity,
			Bitmap image, boolean large, boolean blackAndWhite)
	{
		mCaptchaState = captchaState;
		if (mCaptchaImage != null && mCaptchaImage != image) mCaptchaImage.recycle();
		mCaptchaData = captchaData;
		mCaptchaImage = image;
		mCaptchaLarge = large;
		mCaptchaBlackAndWhite = blackAndWhite;
		mLoadedCaptchaType = captchaType;
		if (captchaType != null)
		{
			ChanConfiguration.Captcha captcha = ChanConfiguration.get(mChanName).safe().obtainCaptcha(captchaType);
			if (input == null) input = captcha.input;
			if (validity == null) validity = captcha.validity;
		}
		mLoadedCaptchaInput = input;
		mLoadedCaptchaValidity = validity;
		boolean invertColors = blackAndWhite && !GraphicsUtils.isLight(ResourceUtils.getColor(this,
				android.R.attr.windowBackground));
		boolean canSend = mCaptchaController.showCaptcha(captchaState, input, image, large,
				blackAndWhite, invertColors);
		mSendButton.setEnabled(canSend);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if (resultCode == RESULT_OK)
		{
			switch (requestCode)
			{
				case C.REQUEST_CODE_ATTACH:
				{
					LinkedHashSet<Uri> uris = new LinkedHashSet<>();
					Uri dataUri = data.getData();
					if (dataUri != null) uris.add(dataUri);
					if (C.API_KITKAT)
					{
						ClipData clipData = data.getClipData();
						if (clipData != null)
						{
							for (int i = 0; i < clipData.getItemCount(); i++)
							{
								ClipData.Item item = clipData.getItemAt(i);
								Uri uri = item.getUri();
								if (uri != null) uris.add(uri);
							}
						}
					}
					int count = Math.min(uris.size(), mPostingConfiguration.attachmentCount - mAttachments.size());
					int error = uris.size() - count;
					for (Uri uri : uris)
					{
						FileHolder fileHolder = FileHolder.obtain(this, uri);
						grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
						if (fileHolder != null) addAttachment(fileHolder); else error++;
					}
					if (error > 0)
					{
						ClickableToast.show(this, getResources().getQuantityString(R.plurals
								.message_file_attach_error_format, error, error));
					}
					break;
				}
			}
		}
	}
	
	private class AttachmentHolder
	{
		public View self;
		public TextView fileName;
		public TextView fileSize;
		public View options;
		public ImageView imageView;
		public FileHolder fileHolder;
		public String rating;
		public boolean optionUniqueHash = false;
		public boolean optionRemoveMetadata = false;
		public boolean optionRemoveFileName = false;
		public boolean optionSpoiler = false;
	}
	
	public static class AttachmentOptionsDialog extends DialogFragment implements AdapterView.OnItemClickListener
	{
		private static final String EXTRA_ATTACHMENT_INDEX = "attachmentIndex";
		
		private static final int OPTION_TYPE_UNIQUE_HASH = 0;
		private static final int OPTION_TYPE_REMOVE_METADATA = 1;
		private static final int OPTION_TYPE_REMOVE_FILE_NAME = 2;
		private static final int OPTION_TYPE_SPOILER = 3;
		
		private static class OptionItem
		{
			public final String title;
			public final int type;
			public final boolean enabled;
			
			public OptionItem(String title, int type, boolean enabled)
			{
				this.title = title;
				this.type = type;
				this.enabled = enabled;
			}
		}
		
		private final ArrayList<OptionItem> mOptionItems = new ArrayList<>();
		
		public AttachmentOptionsDialog()
		{
			
		}
		
		public AttachmentOptionsDialog(int attachmentIndex)
		{
			Bundle args = new Bundle();
			args.putInt(EXTRA_ATTACHMENT_INDEX, attachmentIndex);
			setArguments(args);
		}
		
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState)
		{
			PostingActivity activity = (PostingActivity) getActivity();
			AttachmentHolder holder = activity.mAttachments.get(getArguments().getInt(EXTRA_ATTACHMENT_INDEX));
			ChanConfiguration.Posting postingConfiguration = activity.mPostingConfiguration;
			ArrayList<OptionItem> optionItems = mOptionItems;
			optionItems.add(new OptionItem(getString(R.string.text_unique_hash), OPTION_TYPE_UNIQUE_HASH,
					holder.optionUniqueHash));
			optionItems.add(new OptionItem(getString(R.string.text_remove_metadata), OPTION_TYPE_REMOVE_METADATA,
					holder.optionRemoveMetadata));
			optionItems.add(new OptionItem(getString(R.string.text_remove_file_name), OPTION_TYPE_REMOVE_FILE_NAME,
					holder.optionRemoveFileName));
			if (postingConfiguration.attachmentSpoiler)
			{
				optionItems.add(new OptionItem(getString(R.string.text_spoiler), OPTION_TYPE_SPOILER,
						holder.optionSpoiler));
			}
			ArrayList<String> items = new ArrayList<>();
			for (OptionItem optionItem : optionItems) items.add(optionItem.title);
			LinearLayout linearLayout = new LinearLayout(activity);
			linearLayout.setOrientation(LinearLayout.VERTICAL);
			ImageView imageView = new ImageView(activity);
			imageView.setBackground(new TransparentTileDrawable(activity, true));
			imageView.setImageDrawable(holder.imageView.getDrawable());
			imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
			linearLayout.addView(imageView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
					0, 1));
			ListView listView = new ListView(activity);
			linearLayout.addView(listView, LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
			int resId = ResourceUtils.obtainAlertDialogLayoutResId(activity, ResourceUtils.DIALOG_LAYOUT_MULTI_CHOICE);
			if (C.API_LOLLIPOP) listView.setDividerHeight(0);
			listView.setAdapter(new ArrayAdapter<>(activity, resId, android.R.id.text1, items));
			for (int i = 0; i < optionItems.size(); i++) listView.setItemChecked(i, optionItems.get(i).enabled);
			listView.setOnItemClickListener(this);
			AlertDialog dialog = new AlertDialog.Builder(activity).setView(linearLayout).create();
			dialog.setCanceledOnTouchOutside(true);
			return dialog;
		}
		
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id)
		{
			PostingActivity activity = (PostingActivity) getActivity();
			AttachmentHolder holder = activity.mAttachments.get(getArguments().getInt(EXTRA_ATTACHMENT_INDEX));
			int type = mOptionItems.get(position).type;
			boolean checked = ((ListView) parent).isItemChecked(position);
			switch (type)
			{
				case OPTION_TYPE_UNIQUE_HASH: holder.optionUniqueHash = checked; break;
				case OPTION_TYPE_REMOVE_METADATA: holder.optionRemoveMetadata = checked; break;
				case OPTION_TYPE_REMOVE_FILE_NAME: holder.optionRemoveFileName = checked; break;
				case OPTION_TYPE_SPOILER: holder.optionSpoiler = checked; break;
			}
		}
	}
	
	public static class AttachmentRatingDialog extends DialogFragment implements DialogInterface.OnClickListener
	{
		private static final String EXTRA_ATTACHMENT_INDEX = "attachmentIndex";
		
		public AttachmentRatingDialog()
		{
			
		}
		
		public AttachmentRatingDialog(int attachmentIndex)
		{
			Bundle args = new Bundle();
			args.putInt(EXTRA_ATTACHMENT_INDEX, attachmentIndex);
			setArguments(args);
		}
		
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState)
		{
			PostingActivity activity = (PostingActivity) getActivity();
			AttachmentHolder holder = activity.mAttachments.get(getArguments().getInt(EXTRA_ATTACHMENT_INDEX));
			String[] items = new String[activity.mAttachmentRatingItems.size()];
			int checkedItem = 0;
			for (int i = 0; i < items.length; i++)
			{
				Pair<String, String> ratingItem = activity.mAttachmentRatingItems.get(i);
				items[i] = ratingItem.second;
				if (ratingItem.first.equals(holder.rating)) checkedItem = i;
			}
			return new AlertDialog.Builder(activity).setTitle(R.string.text_rating).setSingleChoiceItems(items,
					checkedItem, this).setNegativeButton(android.R.string.cancel, null).create();
		}
		
		@Override
		public void onClick(DialogInterface dialog, int which)
		{
			dismiss();
			PostingActivity activity = (PostingActivity) getActivity();
			AttachmentHolder holder = activity.mAttachments.get(getArguments().getInt(EXTRA_ATTACHMENT_INDEX));
			holder.rating = activity.mAttachmentRatingItems.get(which).first;
		}
	}
	
	private final View.OnClickListener mAttachmentOptionsListener = new View.OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			AttachmentHolder holder = (AttachmentHolder) v.getTag();
			int attachmentIndex = mAttachments.indexOf(holder);
			AttachmentOptionsDialog dialog = new AttachmentOptionsDialog(attachmentIndex);
			dialog.show(getFragmentManager(), AttachmentOptionsDialog.class.getName());
		}
	};
	
	private final View.OnClickListener mAttachmentRatingListener = new View.OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			AttachmentHolder holder = (AttachmentHolder) v.getTag();
			int attachmentIndex = mAttachments.indexOf(holder);
			AttachmentRatingDialog dialog = new AttachmentRatingDialog(attachmentIndex);
			dialog.show(getFragmentManager(), AttachmentRatingDialog.class.getName());
		}
	};
	
	private final View.OnClickListener mAttachmentRemoveListener = new View.OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			AttachmentHolder holder = (AttachmentHolder) v.getTag();
			if (mAttachments.remove(holder))
			{
				if (mAttachmentColumnCount == 1) mAttachmentContainer.removeView(holder.self); else
				{
					mAttachmentContainer.removeAllViews();
					for (int i = 0; i < mAttachments.size(); i++)
					{
						View attachmentView = mAttachments.get(i).self;
						ViewUtils.removeFromParent(attachmentView);
						addAttachmentViewToContainer(attachmentView, i);
					}
				}
				invalidateOptionsMenu();
				mScrollView.postResizeComment();
			}
		}
	};
	
	private void addAttachmentViewToContainer(View attachmentView, int position)
	{
		LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) attachmentView.getLayoutParams();
		if (mAttachmentColumnCount == 1)
		{
			layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
			layoutParams.weight = 0;
			layoutParams.leftMargin = 0;
			mAttachmentContainer.addView(attachmentView);
		}
		else
		{
			float density = ResourceUtils.obtainDensity(this);
			float paddingDp = 4f;
			layoutParams.width = 0;
			layoutParams.weight = 1;
			layoutParams.leftMargin = (int) (paddingDp * density);
			int row = position / mAttachmentColumnCount, column = position % mAttachmentColumnCount;
			LinearLayout subcontainer;
			View placeholder;
			if (column == 0)
			{
				subcontainer = new LinearLayout(this);
				mAttachmentContainer.addView(subcontainer, LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT);
				subcontainer.setOrientation(LinearLayout.HORIZONTAL);
				placeholder = new View(this);
				subcontainer.addView(placeholder, 0, LinearLayout.LayoutParams.MATCH_PARENT);
				subcontainer.setPadding(0, 0, (int) (paddingDp * density), 0);
				subcontainer.setGravity(Gravity.BOTTOM);
			}
			else
			{
				subcontainer = (LinearLayout) mAttachmentContainer.getChildAt(row);
				placeholder = subcontainer.getChildAt(subcontainer.getChildCount() - 1);
			}
			subcontainer.addView(attachmentView, column);
			layoutParams = ((LinearLayout.LayoutParams) placeholder.getLayoutParams());
			layoutParams.weight = mAttachmentColumnCount - column - 1;
			layoutParams.leftMargin = (int) (paddingDp * density * layoutParams.weight);
			placeholder.setVisibility(mAttachmentColumnCount == column + 1 ? View.GONE : View.VISIBLE);
		}
	}
	
	private AttachmentHolder addNewAttachment(View.OnClickListener optionsListener)
	{
		FrameLayout layout = (FrameLayout) getLayoutInflater().inflate(R.layout.activity_posting_attachment,
				mAttachmentContainer, false);
		if (C.API_LOLLIPOP)
		{
			float density = ResourceUtils.obtainDensity(this);
			layout.setForeground(new RoundedCornersDrawable((int) (2f * density), ResourceUtils.getColor(this,
					android.R.attr.windowBackground)));
		}
		addAttachmentViewToContainer(layout, mAttachments.size());
		AttachmentHolder holder = new AttachmentHolder();
		holder.self = layout;
		holder.fileName = (TextView) layout.findViewById(R.id.attachment_name);
		holder.fileSize = (TextView) layout.findViewById(R.id.attachment_size);
		holder.options = layout.findViewById(R.id.attachment_options);
		holder.imageView = (ImageView) layout.findViewById(R.id.attachment_preview);
		holder.imageView.setBackground(new TransparentTileDrawable(this, true));
		View rating = layout.findViewById(R.id.attachment_rating);
		if (mAttachmentRatingItems == null) rating.setVisibility(View.GONE); else
		{
			rating.setOnClickListener(mAttachmentRatingListener);
			rating.setTag(holder);
		}
		View remove = layout.findViewById(R.id.attachment_remove);
		remove.setOnClickListener(mAttachmentRemoveListener);
		remove.setTag(holder);
		if (optionsListener != null)
		{
			holder.options.setOnClickListener(optionsListener);
			holder.options.setTag(holder);
		}
		else holder.options.setVisibility(View.GONE);
		mAttachments.add(holder);
		invalidateOptionsMenu();
		mScrollView.postResizeComment();
		return holder;
	}
	
	private void addAttachment(FileHolder fileHolder)
	{
		addAttachment(fileHolder, null, false, false, false, false);
	}
	
	private void addAttachment(FileHolder fileHolder, String rating, boolean optionUniqueHash,
			boolean optionRemoveMetadata, boolean optionRemoveFileName, boolean optionSpoiler)
	{
		AttachmentHolder holder = addNewAttachment(mAttachmentOptionsListener);
		holder.fileHolder = fileHolder;
		holder.optionUniqueHash = optionUniqueHash;
		holder.optionRemoveMetadata = optionRemoveMetadata;
		holder.optionRemoveFileName = optionRemoveFileName;
		holder.optionSpoiler = optionSpoiler;
		holder.fileName.setText(fileHolder.getName());
		if (mAttachmentRatingItems != null)
		{
			holder.rating = rating != null ? rating : mAttachmentRatingItems.get(0).first;
		}
		String fileSize = String.format(Locale.US, "%.2f", fileHolder.getSize() / 1024f) + " KB";
		Bitmap bitmap = null;
		ChanLocator locator = ChanLocator.getDefault();
		DisplayMetrics metrics = getResources().getDisplayMetrics();
		int targetImageSize = Math.max(metrics.widthPixels, metrics.heightPixels);
		if (fileHolder.isImage())
		{
			try
			{
				bitmap = fileHolder.readImageBitmap(targetImageSize, false, false);
			}
			catch (OutOfMemoryError e)
			{
				
			}
			fileSize += " " + fileHolder.getImageWidth() + "x" + fileHolder.getImageHeight();
		}
		if (bitmap == null)
		{
			if (locator.isVideoExtension(fileHolder.getName()))
			{
				MediaMetadataRetriever retriever = new MediaMetadataRetriever();
				FileHolder.Descriptor descriptor = null;
				try
				{
					descriptor = fileHolder.openDescriptor();
					retriever.setDataSource(descriptor.getFileDescriptor());
					Bitmap fullBitmap = retriever.getFrameAtTime(-1);
					if (fullBitmap != null) bitmap = GraphicsUtils.reduceBitmapSize(fullBitmap, targetImageSize, true);
				}
				catch (Exception | OutOfMemoryError e)
				{
					
				}
				finally
				{
					retriever.release();
					IOUtils.close(descriptor);
				}
			}
		}
		if (bitmap != null)
		{
			holder.imageView.setVisibility(View.VISIBLE);
			holder.imageView.setImageBitmap(bitmap);
			holder.self.getLayoutParams().height = (int) (128f * ResourceUtils.obtainDensity(this));
		}
		holder.fileSize.setText(fileSize);
	}
	
	private void formatQuote()
	{
		Editable editable = mCommentView.getText();
		String text = editable.toString();
		int selectionStart = mCommentView.getSelectionStart();
		int selectionEnd = mCommentView.getSelectionEnd();
		String selectedText = text.substring(selectionStart, selectionEnd);
		String oneSymbolBefore = text.substring(Math.max(selectionStart - 1, 0), selectionStart);
		if (selectedText.startsWith(">"))
		{
			String unQuotedText = selectedText.replaceFirst("> ?", "").replaceAll("(\n+)> ?", "$1");
			int diff = selectedText.length() - unQuotedText.length();
			editable.replace(selectionStart, selectionEnd, unQuotedText);
			mCommentView.setSelection(selectionStart, selectionEnd - diff);
		}
		else
		{
			String firstSymbol = oneSymbolBefore.length() == 0 || oneSymbolBefore.equals("\n") ? "" : "\n";
			String quotedText = firstSymbol + "> " + selectedText.replaceAll("(\n+)", "$1> ");
			int diff = quotedText.length() - selectedText.length();
			editable.replace(selectionStart, selectionEnd, quotedText);
			int newStart = selectionStart + firstSymbol.length();
			int newEnd = selectionEnd + diff;
			if (newEnd - newStart <= 2) newStart = newEnd;
			mCommentView.setSelection(newStart, newEnd);
		}
	}
	
	private void resizeComment(ViewGroup root)
	{
		View postMain = root.getChildAt(0);
		mCommentView.setMinLines(4);
		int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(postMain.getWidth(), View.MeasureSpec.EXACTLY);
		int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
		postMain.measure(widthMeasureSpec, heightMeasureSpec);
		int delta = root.getHeight() - postMain.getMeasuredHeight();
		if (delta > 0) mCommentView.setMinHeight(mCommentView.getMeasuredHeight() + delta);
	}
	
	@Override
	public void onWindowFocusChanged(boolean hasFocus)
	{
		super.onWindowFocusChanged(hasFocus);
		mClickableToastHolder.onWindowFocusChanged(hasFocus);
	}
	
	/*
	 * Modified ScrollView. Allows comment input resizing to fit screen.
	 */
	public static class ResizingScrollView extends ScrollView implements Runnable
	{
		public ResizingScrollView(Context context, AttributeSet attrs)
		{
			super(context, attrs);
			setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
		    setFocusable(true);
		    setFocusableInTouchMode(true);
		}
		
		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
		{
			int oldHeight = getHeight();
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			int newHeight = getMeasuredHeight();
			if (newHeight != oldHeight) postResizeComment();
		}
		
		@Override
		public void run()
		{
			PostingActivity activity = (PostingActivity) getContext();
			activity.resizeComment(ResizingScrollView.this);
		}
		
		public void postResizeComment()
		{
			removeCallbacks(this);
			post(this);
		}
	}
}