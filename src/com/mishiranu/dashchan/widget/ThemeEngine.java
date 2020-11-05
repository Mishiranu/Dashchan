package com.mishiranu.dashchan.widget;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsSeekBar;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toolbar;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.storage.ThemesStorage;
import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.graphics.ThemeChoiceDrawable;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.util.WeakIterator;
import com.mishiranu.dashchan.util.WeakObservable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public class ThemeEngine {
	private static final ThemeEngine INSTANCE = new ThemeEngine();

	private static final int STATUS_OVERLAY_LIGHT = 0x22000000;
	private static final int STATUS_OVERLAY_DARK = 0x33000000;

	public static class Theme implements Comparable<Theme> {
		public enum Base {
			LIGHT(R.style.Theme_Main_Light),
			DARK(R.style.Theme_Main_Dark);

			private final int resId;

			Base(int resId) {
				this.resId = resId;
			}
		}

		public final Base base;
		public final String name;
		public final boolean builtIn;
		private final String json;

		public final int window;
		public final int primary;
		public final int accent;
		public final int card;
		public final int thread;
		public final int post;
		public final int meta;
		public final int spoiler;
		public final int link;
		public final int quote;
		public final int tripcode;
		public final int capcode;
		public final float colorGainFactor;

		public final int controlNormal21;
		public final float disabledAlpha21;

		public Theme(Base base, String name, boolean builtIn, String json,
				int window, int primary, int accent, int card, int thread, int post, int meta,
				int spoiler, int link, int quote, int tripcode, int capcode,
				float colorGainFactor, int controlNormal21, float disabledAlpha21) {
			this.base = base;
			this.name = name;
			this.builtIn = builtIn;
			this.json = json;
			this.window = window;
			this.primary = primary;
			this.accent = accent;
			this.card = card;
			this.thread = thread;
			this.post = post;
			this.meta = meta;
			this.spoiler = spoiler;
			this.link = link;
			this.quote = quote;
			this.tripcode = tripcode;
			this.capcode = capcode;
			this.colorGainFactor = colorGainFactor;
			this.controlNormal21 = controlNormal21;
			this.disabledAlpha21 = disabledAlpha21;
		}

		public boolean isBlack4() {
			return !C.API_LOLLIPOP && base == Base.DARK && window == primary && (primary & 0x00ffffff) == 0;
		}

		public ThemeChoiceDrawable createThemeChoiceDrawable() {
			int window = this.window;
			int primary = this.primary == window ? this.accent : this.primary;
			int accent = this.accent == primary ? this.link : this.accent;
			return new ThemeChoiceDrawable(window, primary, accent);
		}

		public JSONObject toJsonObject() {
			try {
				return new JSONObject(json);
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
		}

		private int getColor(String name) {
			switch (name) {
				case "window": return window;
				case "primary": return primary;
				case "accent": return accent;
				case "card": return card;
				case "thread": return thread;
				case "post": return post;
				case "meta": return meta;
				case "spoiler": return spoiler;
				case "link": return link;
				case "quote": return quote;
				case "tripcode": return tripcode;
				case "capcode": return capcode;
				default: throw new IllegalArgumentException();
			}
		}

		@Override
		public int compareTo(Theme theme) {
			return name.compareTo(theme.name);
		}
	}

	public interface OnOverlayFocusListener {
		class MutableItem {
			public View decorView;
			public boolean indirect;
		}

		void onOverlayFocusChanged(Iterable<MutableItem> stack);
	}

	private static class OverlayStack implements Iterable<OnOverlayFocusListener.MutableItem>,
			WeakIterator.Provider<OverlayStack.StackItem, View, OnOverlayFocusListener.MutableItem> {
		private static class StackItem {
			public final WeakReference<View> decorView;
			public final boolean indirect;

			private StackItem(View decorView, boolean indirect) {
				this.decorView = new WeakReference<>(decorView);
				this.indirect = indirect;
			}
		}

		private final ArrayList<StackItem> stackItems = new ArrayList<>();
		private final OnOverlayFocusListener.MutableItem mutableItem = new OnOverlayFocusListener.MutableItem();

		public void handleOverlayFocused(View decorView, boolean direct, boolean dialog) {
			StackItem topStackItem = null;
			Iterator<StackItem> iterator = stackItems.iterator();
			while (iterator.hasNext()) {
				StackItem stackItem = iterator.next();
				View itemDecorView = stackItem.decorView.get();
				if (itemDecorView == null) {
					iterator.remove();
				} else if (itemDecorView == decorView) {
					topStackItem = stackItem;
					iterator.remove();
				}
			}
			if (topStackItem == null) {
				boolean indirect = !direct && !dialog;
				topStackItem = new StackItem(decorView, indirect);
			}
			stackItems.add(topStackItem);
		}

		@NonNull
		@Override
		public Iterator<OnOverlayFocusListener.MutableItem> iterator() {
			return new WeakIterator<>(stackItems.iterator(), this);
		}

		@Override
		public WeakReference<View> getWeakReference(StackItem data) {
			return data.decorView;
		}

		@Override
		public OnOverlayFocusListener.MutableItem transform(StackItem data, View referenced) {
			if (ViewCompat.isAttachedToWindow(referenced)) {
				OnOverlayFocusListener.MutableItem mutableItem = this.mutableItem;
				mutableItem.decorView = referenced;
				mutableItem.indirect = data.indirect;
				return mutableItem;
			} else {
				return null;
			}
		}
	}

	private static class ThemeContext extends ContextWrapper {
		private Theme theme;
		private ColorScheme colorScheme;
		private ThemeLayoutInflater layoutInflater;

		private final OverlayStack overlayStack = new OverlayStack();
		private final WeakObservable<OnOverlayFocusListener> overlayFocusListeners = new WeakObservable<>();

		public ThemeContext(Context base) {
			super(base);
		}

		@Override
		public Object getSystemService(String name) {
			if (LAYOUT_INFLATER_SERVICE.equals(name)) {
				if (layoutInflater == null) {
					layoutInflater = new ThemeLayoutInflater(LayoutInflater
							.from(getBaseContext()), this, true, false, false, false);
				}
				return layoutInflater;
			}
			return super.getSystemService(name);
		}

		public void dispatchOverlayFocused(View decorView, boolean direct, boolean dialog) {
			overlayStack.handleOverlayFocused(decorView, direct, dialog);
			for (OnOverlayFocusListener listener : overlayFocusListeners) {
				listener.onOverlayFocusChanged(overlayStack);
			}
		}

		private ColorStateList checkBoxColors;
		private ColorStateList switchThumbColors;
		private ColorStateList editTextColors;
		private ColorStateList buttonColors;

		public ColorStateList getCheckBoxColors() {
			if (checkBoxColors == null) {
				int colorControlDisabled = GraphicsUtils.applyAlpha(theme.controlNormal21, theme.disabledAlpha21);
				int[][] states = {{-android.R.attr.state_enabled}, {android.R.attr.state_checked}, {}};
				int[] colors = {colorControlDisabled, theme.accent, theme.controlNormal21};
				checkBoxColors = new ColorStateList(states, colors);
			}
			return checkBoxColors;
		}

		public ColorStateList getSwitchThumbColors() {
			if (switchThumbColors == null) {
				int thumbColorNormal;
				int thumbColorNormalDisabled;
				int thumbNormalColorsAttr = getResources().getIdentifier("colorSwitchThumbNormal", "attr", "android");
				ColorStateList thumbColors = thumbNormalColorsAttr != 0
						? ResourceUtils.getColorStateList(this, thumbNormalColorsAttr) : null;
				if (thumbColors != null) {
					thumbColorNormal = thumbColors.getDefaultColor();
					int[] disabledState = {-android.R.attr.state_enabled};
					thumbColorNormalDisabled = thumbColors.getColorForState(disabledState, thumbColorNormal);
				} else {
					thumbColorNormal = theme.controlNormal21;
					thumbColorNormalDisabled = theme.controlNormal21;
				}
				int[][] states = {{-android.R.attr.state_enabled}, {android.R.attr.state_checked}, {}};
				int[] colors = {thumbColorNormalDisabled, theme.accent, thumbColorNormal};
				switchThumbColors = new ColorStateList(states, colors);
			}
			return switchThumbColors;
		}

		public ColorStateList getEditTextColors() {
			if (editTextColors == null) {
				int[][] states = {{-android.R.attr.state_enabled}, {android.R.attr.state_pressed},
						{android.R.attr.state_focused}, {}};
				int[] colors = {theme.controlNormal21, theme.accent, theme.accent, theme.controlNormal21};
				editTextColors = new ColorStateList(states, colors);
			}
			return editTextColors;
		}

		public ColorStateList getButtonColors() {
			if (buttonColors == null) {
				int colorAccentDisabled = GraphicsUtils.applyAlpha(theme.accent, theme.disabledAlpha21);
				int[][] states = {{-android.R.attr.state_enabled}, {}};
				int[] colors = {colorAccentDisabled, theme.accent};
				buttonColors = new ColorStateList(states, colors);
			}
			return buttonColors;
		}
	}

	private static View getDecorView(View view) {
		View decorView = view;
		while (true) {
			ViewParent viewParent = decorView.getParent();
			if (viewParent instanceof View) {
				decorView = (View) viewParent;
			} else {
				break;
			}
		}
		return decorView;
	}

	private interface AttachListener extends View.OnAttachStateChangeListener {
		boolean isProcessed();
		void handleView(View view);

		@Override
		default void onViewAttachedToWindow(View v) {
			v.removeOnAttachStateChangeListener(this);
			handleView(v);
		}

		@Override
		default void onViewDetachedFromWindow(View v) {}
	}

	private static class OverlayAttachListener implements AttachListener {
		private final boolean direct;
		private final boolean dialog;
		private boolean processed = false;

		public OverlayAttachListener(boolean direct, boolean dialog) {
			this.direct = direct;
			this.dialog = dialog;
		}

		@Override
		public boolean isProcessed() {
			return processed;
		}

		@Override
		public void handleView(View view) {
			if (!processed) {
				processed = true;
				View decorView = getDecorView(view);
				if ("DecorView".equals(decorView.getClass().getSimpleName())) {
					ThemeContext themeContext = obtainThemeContext(decorView.getContext());
					if (themeContext != null) {
						if (dialog && C.API_LOLLIPOP && shouldApplyStyle(decorView.getContext())) {
							decorView.setBackgroundTintList(ColorStateList.valueOf(themeContext.theme.card));
						}
						Object tag = decorView.getTag(R.id.tag_theme_engine);
						boolean forceDialog = tag instanceof Boolean && (boolean) tag;
						boolean dialog = this.dialog || forceDialog;
						themeContext.dispatchOverlayFocused(decorView, direct, dialog);
						ViewGroup viewGroup = (ViewGroup) decorView;
						viewGroup.addView(new View(decorView.getContext()) {
							@Override
							public void onWindowFocusChanged(boolean hasWindowFocus) {
								super.onWindowFocusChanged(hasWindowFocus);
								if (hasWindowFocus) {
									themeContext.dispatchOverlayFocused(decorView, direct, dialog);
								}
							}
						}, 0, 0);
					}
				}
			}
		}
	}

	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	private static final AttachListener POPUP_ATTACH_LISTENER = new AttachListener() {
		@Override
		public boolean isProcessed() {
			return false;
		}

		@Override
		public void handleView(View view) {
			View decorView = getDecorView(view);
			Object tag = decorView.getTag(R.id.tag_theme_engine);
			if (tag == null || !((boolean) tag)) {
				// Mark as handled
				decorView.setTag(R.id.tag_theme_engine, true);
				if (shouldApplyStyle(decorView.getContext())) {
					String decorViewName = decorView.getClass().getSimpleName();
					if ("PopupDecorView".equals(decorViewName) || "PopupViewContainer".equals(decorViewName)) {
						View backgroundView = decorView;
						if (backgroundView.getBackground() == null) {
							ViewGroup viewGroup = (ViewGroup) decorView;
							if (viewGroup.getChildCount() > 0) {
								View child = viewGroup.getChildAt(0);
								if (child.getBackground() != null) {
									// More modern Android versions wrap background with decor view
									backgroundView = child;
								}
							}
						}
						ThemeContext themeContext = requireThemeContext(decorView.getContext());
						backgroundView.setBackgroundTintList(ColorStateList.valueOf(themeContext.theme.card));
					}
				}
			}
		}
	};

	private static class ThemeLayoutInflater extends LayoutInflater {
		private static final String[] PREFIXES = {"android.widget.", "android.webkit.", "android.app."};

		private final boolean direct;
		private final AttachListener attachListener;

		private boolean toolbar;

		public boolean isDirect() {
			return direct || toolbar;
		}

		protected ThemeLayoutInflater(LayoutInflater original, Context newContext,
				boolean direct, boolean dialog, boolean overlay, boolean popup) {
			super(original, newContext);
			this.direct = direct;
			attachListener = dialog || overlay ? new OverlayAttachListener(direct, dialog)
					: C.API_LOLLIPOP && popup ? POPUP_ATTACH_LISTENER : null;
		}

		private static final int[] CLONE_ATTRS = {android.R.attr.windowIsFloating, R.attr.isOverlay, R.attr.isPopup};

		@Override
		public LayoutInflater cloneInContext(Context newContext) {
			TypedArray typedArray = newContext.obtainStyledAttributes(CLONE_ATTRS);
			boolean dialog = typedArray.getBoolean(0, false);
			boolean overlay = typedArray.getBoolean(1, false);
			boolean popup = typedArray.getBoolean(2, false);
			typedArray.recycle();
			boolean inheritDirect = dialog || popup;
			boolean forceDirect = newContext instanceof Activity;
			boolean direct = isDirect() && inheritDirect || forceDirect;
			return new ThemeLayoutInflater(this, newContext, direct, dialog, overlay, popup);
		}

		@Override
		protected View onCreateView(String name, AttributeSet attrs) throws ClassNotFoundException {
			View view = createViewInternal(name, attrs);
			if (C.API_LOLLIPOP && view instanceof Toolbar) {
				LayoutInflater layoutInflater = LayoutInflater.from(view.getContext());
				if (layoutInflater instanceof ThemeLayoutInflater) {
					((ThemeLayoutInflater) layoutInflater).toolbar = true;
				}
			}
			applyStyle(view);
			if (attachListener != null && !attachListener.isProcessed()) {
				view.addOnAttachStateChangeListener(attachListener);
			}
			return view;
		}

		private View createViewInternal(String name, AttributeSet attrs) throws ClassNotFoundException {
			for (String prefix : PREFIXES) {
				try {
					return createView(name, prefix, attrs);
				} catch (ClassNotFoundException e) {
					// Skip
				}
			}
			return super.onCreateView(name, attrs);
		}
	}

	private static ThemeContext obtainThemeContext(Context context) {
		while (true) {
			if (context instanceof ThemeContext) {
				return (ThemeContext) context;
			} else if (context instanceof ContextWrapper) {
				context = ((ContextWrapper) context).getBaseContext();
			} else {
				return null;
			}
		}
	}

	private static ThemeContext requireThemeContext(Context context) {
		ThemeContext themeContext = obtainThemeContext(context);
		if (themeContext == null) {
			throw new IllegalArgumentException("Context is not attached to theme engine");
		}
		return themeContext;
	}

	public static Context attach(Context baseContext) {
		return new ThemeContext(baseContext);
	}

	public static void applyTheme(Context context) {
		ThemeContext themeContext = requireThemeContext(context);
		INSTANCE.prepareThemes(context);
		String themeString = Preferences.getTheme();
		Theme theme = INSTANCE.themes.get(themeString);
		if (theme == null) {
			theme = INSTANCE.themes.values().iterator().next();
			Preferences.setTheme(theme.name);
		}
		themeContext.theme = theme;
		context.setTheme(theme.base.resId);
		if (context instanceof Activity) {
			Activity activity = (Activity) context;
			activity.getWindow().getDecorView().setBackgroundColor(theme.window);
			if (C.API_LOLLIPOP) {
				int toolbarColor = theme.primary | 0xff000000;
				float[] hsl = new float[3];
				ColorUtils.colorToHSL(toolbarColor, hsl);
				// Interpolate between 0.25f and 0.5f
				float lightness = Math.max(0f, Math.min(hsl[2] * 4f - 1f, 1f));
				int statusBarColor = GraphicsUtils.mixColors(theme.primary,
						ColorUtils.blendARGB(STATUS_OVERLAY_DARK, STATUS_OVERLAY_LIGHT, lightness));
				activity.getWindow().setStatusBarColor(statusBarColor);
				ActivityManager.TaskDescription taskDescription;
				if (C.API_PIE) {
					taskDescription = new ActivityManager.TaskDescription(null, R.mipmap.ic_launcher, toolbarColor);
				} else {
					@SuppressWarnings("deprecation")
					ActivityManager.TaskDescription deprecatedTaskDescription = new ActivityManager
							.TaskDescription(null, null, toolbarColor);
					taskDescription = deprecatedTaskDescription;
				}
				activity.setTaskDescription(taskDescription);
			}
		}
	}

	public static List<Theme> getThemes() {
		return new ArrayList<>(INSTANCE.themes.values());
	}

	public static Theme attachAndApply(Context context) {
		Context themeContext = attach(context);
		applyTheme(themeContext);
		return getTheme(themeContext);
	}

	private static void ensureTheme(ThemeContext themeContext) {
		if (themeContext.theme == null) {
			themeContext.theme = new ThemeBuilder().create(null, null, true, null, themeContext);
		}
	}

	public static Theme getTheme(Context context) {
		ThemeContext themeContext = requireThemeContext(context);
		ensureTheme(themeContext);
		return themeContext.theme;
	}

	public static ColorScheme getColorScheme(Context context) {
		ThemeContext themeContext = requireThemeContext(context);
		ensureTheme(themeContext);
		if (themeContext.colorScheme == null) {
			themeContext.colorScheme = new ColorScheme(context, themeContext.theme);
		}
		return themeContext.colorScheme;
	}

	private static boolean shouldApplyStyle(Context context) {
		LayoutInflater layoutInflater = LayoutInflater.from(context);
		return layoutInflater instanceof ThemeLayoutInflater && ((ThemeLayoutInflater) layoutInflater).isDirect();
	}

	public static void applyStyle(View view) {
		// Stateful tints are buggy on Android 5.0, so some changes are applied to 5.1+ only
		Context context = view.getContext();
		ThemeContext themeContext = obtainThemeContext(context);
		if (themeContext != null) {
			ensureTheme(themeContext);
			Theme theme = themeContext.theme;
			if (shouldApplyStyle(context)) {
				if (view instanceof CompoundButton) {
					if (C.API_LOLLIPOP_MR1) {
						((CompoundButton) view).setButtonTintList(themeContext.getCheckBoxColors());
					}
					if (view instanceof Switch) {
						if (C.API_MARSHMALLOW) {
							Switch switchView = (Switch) view;
							switchView.setTrackTintList(themeContext.getCheckBoxColors());
							switchView.setThumbTintList(themeContext.getSwitchThumbColors());
						}
					}
				} else if (view instanceof TextView) {
					TextView textView = (TextView) view;
					textView.setLinkTextColor(theme.link);
					if (view instanceof EditText) {
						if (C.API_LOLLIPOP_MR1) {
							view.setBackgroundTintList(themeContext.getEditTextColors());
						}
					} else if (view instanceof CheckedTextView) {
						// Mostly used by alert dialogs to display lists
						if (C.API_LOLLIPOP_MR1) {
							((CheckedTextView) textView).setCheckMarkTintList(themeContext.getCheckBoxColors());
							if (C.API_MARSHMALLOW) {
								// On newer Android versions "compound drawable" is used instead of "check mark"
								textView.setCompoundDrawableTintList(themeContext.getCheckBoxColors());
							}
						}
					} else if (view instanceof Button) {
						if (C.API_LOLLIPOP) {
							if (view instanceof Button) {
								Button button = (Button) view;
								if (button.getTextColors().getDefaultColor() ==
										ResourceUtils.getColor(button.getContext(), android.R.attr.colorAccent)) {
									button.setTextColor(themeContext.getButtonColors());
								}
							}
						}
					}
				} else if (view instanceof ProgressBar) {
					if (C.API_LOLLIPOP) {
						ColorStateList tint = ColorStateList.valueOf(theme.accent);
						ProgressBar progressBar = (ProgressBar) view;
						progressBar.setIndeterminateTintList(tint);
						progressBar.setProgressTintList(tint);
						if (progressBar instanceof AbsSeekBar) {
							AbsSeekBar seekBar = (AbsSeekBar) progressBar;
							seekBar.setThumbTintList(tint);
							if (C.API_NOUGAT) {
								seekBar.setTickMarkTintList(tint);
							}
						}
					}
				} else if (view instanceof ScrollView) {
					ViewUtils.setEdgeEffectColor((ScrollView) view, theme.accent);
				} else if (view instanceof DropdownView) {
					if (C.API_LOLLIPOP_MR1) {
						view.setBackgroundTintList(themeContext.getEditTextColors());
					}
				}
			}
			handleTag(theme, view);
		}
	}

	private static void handleTag(Theme theme, View view) {
		Object tag = view.getTag();
		if (tag instanceof String) {
			String[] options = ((String) tag).split(":");
			if (options != null) {
				StringBuilder unhandledOptions = null;
				for (String option : options) {
					boolean handled = false;
					int index = option.indexOf("=");
					if (index >= 0) {
						String name = option.substring(0, index);
						String value = option.substring(index + 1);
						if (name.startsWith("theme.")) {
							handled = handleTagValue(theme, view, name.substring(6), value);
						}
					}
					if (!handled) {
						if (unhandledOptions == null) {
							unhandledOptions = new StringBuilder();
						} else {
							unhandledOptions.append(':');
						}
						unhandledOptions.append(option);
					}
				}
				view.setTag(unhandledOptions != null ? unhandledOptions.toString() : null);
			}
		}
	}

	private static boolean handleTagValue(Theme theme, View view, String name, String value) {
		switch (name) {
			case "background": {
				view.setBackgroundColor(theme.getColor(value));
				return true;
			}
			case "textColor": {
				if (view instanceof TextView) {
					((TextView) view).setTextColor(theme.getColor(value));
					return true;
				}
				return false;
			}
		}
		return false;
	}

	public static void addWeakOnOverlayFocusListener(Context context, OnOverlayFocusListener listener) {
		requireThemeContext(context).overlayFocusListeners.register(listener);
	}

	/** Dirty hack, see {@link OverlayAttachListener#handleView(View)} **/
	public static void markDecorAsDialog(View decorView) {
		decorView.setTag(R.id.tag_theme_engine, true);
	}

	private static final int[] DEFAULT_THEME_RESOURCES = {R.raw.theme_normie, R.raw.theme_tomorrow};

	private LinkedHashMap<String, Theme> themes;

	private void prepareThemes(Context context) {
		if (themes == null) {
			LinkedHashMap<String, Theme> themes = new LinkedHashMap<>();
			for (int resId : DEFAULT_THEME_RESOURCES) {
				Theme theme;
				try {
					JSONObject jsonObject = new JSONObject(IOUtils
							.readRawResourceString(context.getResources(), resId));
					theme = parseThemeInternal(context, jsonObject, true);
				} catch (JSONException e) {
					throw new RuntimeException(e);
				}
				themes.put(theme.name, theme);
			}
			boolean additionalChanged = false;
			HashMap<String, JSONObject> additionalThemes = ThemesStorage.getInstance().getItems();
			ArrayList<String> additionalThemeNames = new ArrayList<>(additionalThemes.keySet());
			Collections.sort(additionalThemeNames);
			for (String name : additionalThemeNames) {
				Theme theme = null;
				if (!themes.containsKey(name)) {
					JSONObject jsonObject = additionalThemes.get(name);
					theme = parseTheme(context, jsonObject);
				}
				if (theme != null) {
					themes.put(name, theme);
				} else if (theme == null) {
					additionalThemes.remove(name);
					additionalChanged = true;
				}
			}
			if (additionalChanged) {
				ThemesStorage.getInstance().serialize();
			}
			this.themes = themes;
			if (themes.isEmpty()) {
				throw new RuntimeException("No themes found");
			}
		}
	}

	public static boolean addTheme(Theme theme) {
		Theme existingTheme = INSTANCE.themes.get(theme.name);
		if (existingTheme != null && existingTheme.builtIn) {
			return false;
		}
		ThemesStorage.getInstance().getItems().put(theme.name, theme.toJsonObject());
		ThemesStorage.getInstance().serialize();
		HashMap<String, Theme> installedThemesMap = new HashMap<>(INSTANCE.themes);
		Iterator<Theme> iterator = installedThemesMap.values().iterator();
		while (iterator.hasNext()) {
			if (iterator.next().builtIn) {
				iterator.remove();
			}
		}
		INSTANCE.themes.keySet().removeAll(installedThemesMap.keySet());
		installedThemesMap.put(theme.name, theme);
		ArrayList<Theme> installedThemes = new ArrayList<>(installedThemesMap.values());
		Collections.sort(installedThemes);
		for (Theme installedTheme : installedThemes) {
			INSTANCE.themes.put(installedTheme.name, installedTheme);
		}
		return true;
	}

	public static boolean deleteTheme(String name) {
		Theme theme = INSTANCE.themes.get(name);
		if (theme == null || theme.builtIn) {
			return false;
		}
		ThemesStorage.getInstance().getItems().remove(name);
		ThemesStorage.getInstance().serialize();
		INSTANCE.themes.remove(name);
		return true;
	}

	public static Theme fastParseThemeFromText(Context context, String text) {
		if (text.contains("\"base\"") && text.contains("\"name\"")) {
			int start = text.indexOf("{");
			int end = text.lastIndexOf("}") + 1;
			if (start >= 0 && end > start) {
				JSONObject jsonObject;
				try {
					jsonObject = new JSONObject(text.substring(start, end));
				} catch (JSONException e) {
					jsonObject = null;
				}
				return jsonObject != null ? ThemeEngine.parseTheme(context, jsonObject) : null;
			}
		}
		return null;
	}

	public static Theme parseTheme(Context context, JSONObject jsonObject) {
		try {
			return parseThemeInternal(context, jsonObject, false);
		} catch (JSONException e) {
			Log.persistent().stack(e);
			return null;
		}
	}

	private static Theme parseThemeInternal(Context context,
			JSONObject jsonObject, boolean builtIn) throws JSONException {
		String baseString = jsonObject.getString("base");
		Theme.Base base;
		if ("light".equals(baseString)) {
			base = Theme.Base.LIGHT;
		} else if ("dark".equals(baseString)) {
			base = Theme.Base.DARK;
		} else {
			throw new JSONException("Unknown base theme");
		}
		String name = jsonObject.optString("name");
		if (StringUtils.isEmpty(name)) {
			throw new JSONException("Invalid theme name");
		}
		ThemeBuilder builder = new ThemeBuilder();
		for (Map.Entry<String, ThemeBuilder.Value> entry : ThemeBuilder.MAP.entrySet()) {
			HashSet<String> set = new HashSet<>();
			set.add(entry.getKey());
			Integer color = resolveColor(jsonObject, entry.getKey(), set);
			if (color != null) {
				entry.getValue().setter.setColor(builder, color);
			}
		}
		return builder.create(base, name, builtIn, jsonObject.toString(),
				new ContextThemeWrapper(context, base.resId));
	}

	private static Integer resolveColor(JSONObject jsonObject,
			String name, HashSet<String> checked) throws JSONException {
		String value = StringUtils.emptyIfNull(jsonObject.optString(name));
		if (value.startsWith("#")) {
			return parseColor(value.substring(1));
		} else if (value.startsWith("@")) {
			String checkName = value.substring(1);
			if (checked.contains(checkName)) {
				throw new JSONException("Cyclical definition of \"" + name +"\"");
			}
			checked.add(checkName);
			return resolveColor(jsonObject, checkName, checked);
		} else if (!value.isEmpty()) {
			throw new JSONException("Invalid color: " + value);
		}
		return null;
	}

	private static int parseColor(String color) throws JSONException {
		try {
			if (color.length() == 8) {
				int alpha = Integer.parseInt(color.substring(0, 2), 16);
				int red = Integer.parseInt(color.substring(2, 4), 16);
				int green = Integer.parseInt(color.substring(4, 6), 16);
				int blue = Integer.parseInt(color.substring(6, 8), 16);
				return Color.argb(alpha, red, green, blue);
			} else if (color.length() == 6) {
				int red = Integer.parseInt(color.substring(0, 2), 16);
				int green = Integer.parseInt(color.substring(2, 4), 16);
				int blue = Integer.parseInt(color.substring(4, 6), 16);
				return Color.argb(0xff, red, green, blue);
			}
		} catch (NumberFormatException e) {
			// Ignore exception
		}
		throw new JSONException("Invalid color value: " + color);
	}

	private static class ThemeBuilder {
		public interface Setter {
			void setColor(ThemeBuilder builder, int color);
		}

		public interface Getter {
			Integer getColor(ThemeBuilder builder);
		}

		public interface Transform {
			Integer getTransformed(ThemeBuilder builder);
		}

		public static class Value {
			public final Setter setter;
			public final Getter getter;
			public final Transform transform;
			public final int fallback;

			public Value(Setter setter, Getter getter, Transform transform, int fallback) {
				this.getter = getter;
				this.setter = setter;
				this.transform = transform;
				this.fallback = fallback;
			}
		}

		private static final Map<String, Value> MAP;

		static {
			HashMap<String, Value> map = new HashMap<>();
			map.put("window", new Value((b, c) -> b.window = c, b -> b.window,
					null, R.attr.colorWindowBackground));
			map.put("primary", new Value((b, c) -> b.primary = c, b -> b.primary,
					null, R.attr.colorPrimarySupport));
			map.put("accent", new Value((b, c) -> b.accent = c, b -> b.accent,
					b -> b.primary, R.attr.colorAccentSupport));
			map.put("card", new Value((b, c) -> b.card = c, b -> b.card,
					null, R.attr.colorCardBackground));
			map.put("post", new Value((b, c) -> b.post = c, b -> b.post,
					null, R.attr.colorTextPost));
			map.put("meta", new Value((b, c) -> b.meta = c, b -> b.meta,
					null, R.attr.colorTextMeta));
			map.put("spoiler", new Value((b, c) -> b.spoiler = c, b -> b.spoiler,
					null, R.attr.colorSpoilerBackground));
			map.put("link", new Value((b, c) -> b.link = c, b -> b.link,
					b -> b.accent, android.R.attr.textColorLink));
			map.put("quote", new Value((b, c) -> b.quote = c, b -> b.quote,
					null, R.attr.colorTextQuote));
			map.put("tripcode", new Value((b, c) -> b.tripcode = c, b -> b.tripcode,
					null, R.attr.colorTextTripcode));
			map.put("capcode", new Value((b, c) -> b.capcode = c, b -> b.capcode,
					b -> b.tripcode, R.attr.colorTextCapcode));
			MAP = Collections.unmodifiableMap(map);
		}

		Integer window;
		Integer primary;
		Integer accent;
		Integer card;
		Integer post;
		Integer meta;
		Integer spoiler;
		Integer link;
		Integer quote;
		Integer tripcode;
		Integer capcode;

		public Theme create(Theme.Base base, String name, boolean builtIn, String json, Context context) {
			while (true) {
				boolean changed = false;
				for (Value value : MAP.values()) {
					changed |= transform(value);
				}
				if (!changed) {
					break;
				}
			}
			for (Value value : MAP.values()) {
				if (value.getter.getColor(this) == null) {
					value.setter.setColor(this, ResourceUtils.getColor(context, value.fallback));
				}
			}
			TypedArray typedArray = context.obtainStyledAttributes(new int[] {R.attr.colorTextThread,
					R.attr.colorTextPost, R.attr.colorGainFactor});
			float threadAlpha = (float) Color.alpha(typedArray.getColor(0, 0)) / 0xff;
			float postAlpha = (float) Color.alpha(typedArray.getColor(1, 0)) / 0xff;
			float colorGainFactor = typedArray.getFloat(2, 0);
			typedArray.recycle();
			float postToThreadAlpha = Math.min(threadAlpha / postAlpha, 1f);
			int thread = GraphicsUtils.applyAlpha(post, postToThreadAlpha);
			int controlNormal21 = 0;
			float disabledAlpha21 = 1f;
			if (C.API_LOLLIPOP) {
				int[] attrs = {android.R.attr.colorControlNormal, android.R.attr.disabledAlpha};
				typedArray = context.obtainStyledAttributes(attrs);
				ColorStateList colorControlNormal = typedArray.getColorStateList(0);
				disabledAlpha21 = typedArray.getFloat(1, 1f);
				typedArray.recycle();
				controlNormal21 = colorControlNormal.getColorForState(new int[] {android.R.attr.state_enabled},
						colorControlNormal.getDefaultColor());
			}
			return new Theme(base, name, builtIn, json,
					window, primary, accent, card, thread, post, meta,
					spoiler, link, quote, tripcode, capcode, colorGainFactor,
					controlNormal21, disabledAlpha21);
		}

		private boolean transform(Value value) {
			if (value.getter.getColor(this) == null && value.transform != null) {
				Integer transformed = value.transform.getTransformed(this);
				if (transformed != null) {
					value.setter.setColor(this, transformed);
					return true;
				}
			}
			return false;
		}
	}
}
