package com.mishiranu.dashchan.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Browser;
import android.util.Pair;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.http.CookieBuilder;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.FileProvider;
import com.mishiranu.dashchan.content.net.RelayBlockResolver;
import com.mishiranu.dashchan.content.service.AudioPlayerService;
import com.mishiranu.dashchan.media.VideoPlayer;
import com.mishiranu.dashchan.preference.AdvancedPreferences;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.LauncherActivity;
import com.mishiranu.dashchan.ui.navigator.NavigatorActivity;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class NavigationUtils {
	public static final int FLAG_FROM_CACHE = 0x00000001;
	public static final int FLAG_RETURNABLE = 0x00000002;

	private static Intent obtainMainIntent(Context context, int flags, int allowFlags) {
		return new Intent().setComponent(new ComponentName(context, NavigatorActivity.class))
				.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
				.putExtra(C.EXTRA_NAVIGATION_FLAGS, flags & allowFlags);
	}

	public static Intent obtainThreadsIntent(Context context, String chanName, String boardName, int flags) {
		int allowFlags = FLAG_FROM_CACHE | FLAG_RETURNABLE;
		return obtainMainIntent(context, flags, allowFlags).putExtra(C.EXTRA_CHAN_NAME, chanName)
				.putExtra(C.EXTRA_BOARD_NAME, boardName);
	}

	public static Intent obtainPostsIntent(Context context, String chanName, String boardName, String threadNumber,
			String postNumber, int flags) {
		int allowFlags = FLAG_FROM_CACHE | FLAG_RETURNABLE;
		return obtainMainIntent(context, flags, allowFlags).putExtra(C.EXTRA_CHAN_NAME, chanName)
				.putExtra(C.EXTRA_BOARD_NAME, boardName).putExtra(C.EXTRA_THREAD_NUMBER, threadNumber)
				.putExtra(C.EXTRA_POST_NUMBER, postNumber);
	}

	public static Intent obtainSearchIntent(Context context, String chanName, String boardName, String searchQuery,
			int flags) {
		int allowFlags = FLAG_RETURNABLE;
		return obtainMainIntent(context, flags, allowFlags).putExtra(C.EXTRA_CHAN_NAME, chanName)
				.putExtra(C.EXTRA_BOARD_NAME, boardName).putExtra(C.EXTRA_SEARCH_QUERY, searchQuery);
	}

	public static Intent obtainTargetIntent(Context context, String chanName, ChanLocator.NavigationData data,
			int flags) {
		switch (data.target) {
			case ChanLocator.NavigationData.TARGET_THREADS: {
				return obtainThreadsIntent(context, chanName, data.boardName, flags);
			}
			case ChanLocator.NavigationData.TARGET_POSTS: {
				return obtainPostsIntent(context, chanName, data.boardName, data.threadNumber, data.postNumber, flags);
			}
			case ChanLocator.NavigationData.TARGET_SEARCH: {
				return obtainSearchIntent(context, chanName, data.boardName, data.searchQuery, flags);
			}
			default: {
				throw new IllegalStateException();
			}
		}
	}

	public enum BrowserType {AUTO, INTERNAL, EXTERNAL}

	public static void handleUri(Context context, String chanName, Uri uri, BrowserType browserType) {
		if (chanName != null) {
			uri = ChanLocator.get(chanName).convert(uri);
		}
		boolean isWeb = ChanLocator.getDefault().isWebScheme(uri);
		Intent intent;
		boolean internalBrowser = isWeb && (browserType == BrowserType.INTERNAL || browserType == BrowserType.AUTO &&
				Preferences.isUseInternalBrowser());
		if (internalBrowser && browserType != BrowserType.INTERNAL) {
			ChanManager manager = ChanManager.getInstance();
			PackageManager packageManager = context.getPackageManager();
			HashSet<ComponentName> names = new HashSet<>();
			List<ResolveInfo> infos = packageManager.queryIntentActivities(new Intent(Intent.ACTION_VIEW, uri),
					PackageManager.MATCH_DEFAULT_ONLY);
			for (ResolveInfo info : infos) {
				ActivityInfo activityInfo = info.activityInfo;
				String packageName = activityInfo.applicationInfo.packageName;
				if (!manager.isExtensionPackage(packageName)) {
					names.add(new ComponentName(packageName, activityInfo.name));
				}
			}
			infos = packageManager.queryIntentActivities(new Intent(Intent.ACTION_VIEW, Uri.parse("http://google.com")),
					PackageManager.MATCH_DEFAULT_ONLY);
			for (ResolveInfo info : infos) {
				ActivityInfo activityInfo = info.activityInfo;
				names.remove(new ComponentName(activityInfo.applicationInfo.packageName, activityInfo.name));
			}
			internalBrowser = names.size() == 0;
		}
		if (internalBrowser) {
			intent = new Intent(context, NavigatorActivity.class).setAction(C.ACTION_BROWSER).setData(uri);
		} else {
			intent = new Intent(Intent.ACTION_VIEW, uri);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
			intent.putExtra(C.EXTRA_FROM_CLIENT, true);
			if (chanName != null && ChanLocator.get(chanName).safe(false).isAttachmentUri(uri)) {
				Map<String, String> cookies = RelayBlockResolver.getInstance().getCookies(chanName);
				if (!cookies.isEmpty()) {
					// For MX Player, see https://sites.google.com/site/mxvpen/api
					String userAgent = AdvancedPreferences.getUserAgent(chanName);
					CookieBuilder cookieBuilder = new CookieBuilder();
					for (Map.Entry<String, String> cookie : cookies.entrySet()) {
						cookieBuilder.append(cookie.getKey(), cookie.getValue());
					}
					intent.putExtra("headers", new String[] {"User-Agent", userAgent,
							"Cookie", cookieBuilder.build()});
				}
			}
			if (!isWeb) {
				intent = Intent.createChooser(intent, null);
			}
		}
		try {
			context.startActivity(intent);
		} catch (ActivityNotFoundException e) {
			ToastUtils.show(context, R.string.message_unknown_address);
		} catch (Exception e) {
			ToastUtils.show(context, e.getMessage());
		}
	}

	public static void handleUriInternal(Context context, String chanName, Uri uri) {
		String uriChanName = ChanManager.getInstance().getChanNameByHost(uri.getAuthority());
		if (uriChanName != null) {
			chanName = uriChanName;
		}
		ChanLocator locator = ChanLocator.get(chanName);
		boolean handled = false;
		if (chanName != null && locator.safe(false).isAttachmentUri(uri)) {
			Uri internalUri = locator.convert(uri);
			String fileName = locator.createAttachmentFileName(internalUri);
			if (locator.isImageUri(internalUri) || locator.isVideoUri(internalUri) && isOpenableVideoPath(fileName)) {
				openImageVideo(context, internalUri);
				handled = true;
			} else if (locator.isAudioUri(internalUri)) {
				AudioPlayerService.start(context, chanName, internalUri, fileName);
				handled = true;
			}
		}
		if (!handled && locator.isWebScheme(uri)) {
			String path = uri.getPath();
			if (locator.isImageExtension(path) || locator.isVideoExtension(path) && isOpenableVideoPath(path)) {
				openImageVideo(context, uri);
				handled = true;
			}
		}
		if (!handled) {
			handleUri(context, chanName, uri, BrowserType.AUTO);
		}
	}

	public static void openImageVideo(Context context, Uri uri) {
		context.startActivity(new Intent(context, NavigatorActivity.class).setAction(C.ACTION_GALLERY).setData(uri));
	}

	public static boolean isOpenableVideoPath(String path) {
		return isOpenableVideoExtension(StringUtils.getFileExtension(path));
	}

	public static boolean isOpenableVideoExtension(String extension) {
		return Preferences.isUseVideoPlayer() && VideoPlayer.isLoaded() &
				C.OPENABLE_VIDEO_EXTENSIONS.contains(extension);
	}

	public static void searchImage(Context context, ConfigurationLock configurationLock,
			final String chanName, Uri uri) {
		final String imageUriString = ChanLocator.get(chanName).convert(uri).toString();
		new DialogMenu(context, id -> {
			ChanLocator locator = ChanLocator.getDefault();
			Uri searchUri;
			switch (id) {
				case 0: {
					searchUri = locator.buildQueryWithHost("www.google.com", "searchbyimage",
							"image_url", imageUriString);
					break;
				}
				case 1: {
					searchUri = locator.buildQueryWithHost("yandex.ru", "images/search", "rpt", "imageview",
							"img_url", imageUriString);
					break;
				}
				case 2: {
					searchUri = locator.buildQueryWithHost("www.tineye.com", "search", "url", imageUriString);
					break;
				}
				case 3: {
					searchUri = locator.buildQueryWithHost("saucenao.com", "search.php", "url", imageUriString);
					break;
				}
				case 4: {
					searchUri = locator.buildQueryWithSchemeHost(false, "iqdb.org", null, "url", imageUriString);
					break;
				}
				case 5: {
					searchUri = locator.buildQueryWithHost("whatanime.ga", "/", "url", imageUriString);
					break;
				}
				default: {
					return;
				}
			}
			handleUri(context, null, searchUri, BrowserType.EXTERNAL);
		})
		.addItem(0, "Google")
		.addItem(1, "Yandex")
		.addItem(2, "TinEye")
		.addItem(3, "SauceNAO")
		.addItem(4, "iqdb")
		.addItem(5, "whatanime")
		.show(configurationLock);
	}

	public static void shareText(Context context, String subject, String text, Uri uri) {
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_SUBJECT, subject);
		intent.putExtra(Intent.EXTRA_TEXT, text);
		intent = Intent.createChooser(intent, null);
		if (uri != null) {
			List<ResolveInfo> activities = context.getPackageManager().queryIntentActivities
					(new Intent(Intent.ACTION_VIEW).setData(uri), PackageManager.MATCH_DEFAULT_ONLY);
			if (activities != null && !activities.isEmpty()) {
				HashSet<String> filterPackageNames = new HashSet<>();
				filterPackageNames.add(context.getPackageName());
				for (ChanManager.ExtensionItem extensionItem : ChanManager.getInstance().getExtensionItems()) {
					filterPackageNames.add(extensionItem.packageInfo.packageName);
				}
				ArrayList<Intent> browserIntents = new ArrayList<>();
				for (ResolveInfo resolveInfo : activities) {
					if (!filterPackageNames.contains(resolveInfo.activityInfo.packageName)) {
						browserIntents.add(new Intent(Intent.ACTION_VIEW).setData(uri)
								.setComponent(new ComponentName(resolveInfo.activityInfo.packageName,
								resolveInfo.activityInfo.name)));
					}
				}
				if (!browserIntents.isEmpty()) {
					intent.putExtra(Intent.EXTRA_INITIAL_INTENTS, CommonUtils.toArray(browserIntents, Intent.class));
				}
			}
		}
		context.startActivity(intent);
	}

	public static void shareLink(Context context, String subject, Uri uri) {
		shareText(context, subject, uri.toString(), uri);
	}

	public static void shareFile(Context context, File file, String fileName) {
		Pair<Uri, String> data = CacheManager.getInstance().prepareFileForShare(file, fileName);
		if (data == null) {
			ToastUtils.show(context, R.string.message_cache_unavailable);
			return;
		}
		int intentFlags = FileProvider.getIntentFlags();
		context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
				.setType(data.second).setFlags(intentFlags).putExtra(Intent.EXTRA_STREAM, data.first), null));
	}

	public static void restartApplication(Context context) {
		try {
			CacheManager.getInstance().waitSerializationFinished();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}
		Intent intent = new Intent(context, LauncherActivity.class).setAction(Intent.ACTION_MAIN)
				.addCategory(Intent.CATEGORY_LAUNCHER);
		PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 500, pendingIntent);
		System.exit(0);
	}
}
