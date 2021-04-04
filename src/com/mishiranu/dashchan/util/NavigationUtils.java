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
import chan.content.Chan;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.http.CookieBuilder;
import chan.http.FirewallResolver;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.AdvancedPreferences;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.service.AudioPlayerService;
import com.mishiranu.dashchan.media.VideoPlayer;
import com.mishiranu.dashchan.ui.MainActivity;
import com.mishiranu.dashchan.widget.ClickableToast;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class NavigationUtils {
	public enum BrowserType {AUTO, INTERNAL, EXTERNAL}

	public static void handleUri(Context context, String chanName, Uri uri, BrowserType browserType) {
		if (chanName != null) {
			uri = Chan.get(chanName).locator.convert(uri);
		}
		boolean isWeb = Chan.getFallback().locator.isWebScheme(uri);
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
			intent = new Intent(context, MainActivity.class).setAction(C.ACTION_BROWSER).setData(uri);
		} else {
			intent = new Intent(Intent.ACTION_VIEW, uri);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
			intent.putExtra(C.EXTRA_FROM_CLIENT, true);
			if (chanName != null) {
				Chan chan = Chan.get(chanName);
				if (chan.locator.safe(false).isAttachmentUri(uri)) {
					String userAgent = AdvancedPreferences.getUserAgent(chanName);
					FirewallResolver.Identifier resolverIdentifier = new FirewallResolver
							.Identifier(userAgent, true);
					CookieBuilder cookieBuilder = FirewallResolver.Implementation.getInstance()
							.collectCookies(chan, uri, resolverIdentifier, true);
					if (!cookieBuilder.isEmpty()) {
						// For MX Player, see https://sites.google.com/site/mxvpen/api
						intent.putExtra("headers", new String[] {"User-Agent", userAgent,
								"Cookie", cookieBuilder.build()});
					}
				}
			}
			if (!isWeb) {
				intent = Intent.createChooser(intent, null);
			}
		}
		try {
			context.startActivity(intent);
		} catch (ActivityNotFoundException e) {
			ClickableToast.show(R.string.unknown_address);
		} catch (Exception e) {
			ClickableToast.show(e.getMessage());
		}
	}

	public static void handleUriInternal(Context context, String chanName, Uri uri) {
		Chan uriChan = Chan.getPreferred(null, uri);
		if (uriChan.name != null) {
			chanName = uriChan.name;
		}
		ChanLocator locator = Chan.getFallback().locator;
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
		context.startActivity(new Intent(context, MainActivity.class).setAction(C.ACTION_GALLERY).setData(uri));
	}

	public static boolean isOpenableVideoPath(String path) {
		return isOpenableVideoExtension(StringUtils.getFileExtension(path));
	}

	public static boolean isOpenableVideoExtension(String extension) {
		return Preferences.isUseVideoPlayer() && VideoPlayer.isLoaded() &
				C.OPENABLE_VIDEO_EXTENSIONS.contains(extension);
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
					filterPackageNames.add(extensionItem.packageName);
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
			ClickableToast.show(R.string.cache_is_unavailable);
			return;
		}
		context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
				.setType(data.second).setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
				.putExtra(Intent.EXTRA_STREAM, data.first), null));
	}

	public static void restartApplication(Context context) {
		Intent intent = new Intent(context, MainActivity.class)
				.setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
				.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		long when = SystemClock.elapsedRealtime() + 1000;
		if (C.API_KITKAT) {
			alarmManager.setExact(AlarmManager.ELAPSED_REALTIME, when, pendingIntent);
		} else {
			alarmManager.set(AlarmManager.ELAPSED_REALTIME, when, pendingIntent);
		}
		System.exit(0);
	}
}
