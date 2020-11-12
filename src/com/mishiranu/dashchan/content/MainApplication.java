package com.mishiranu.dashchan.content;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Process;
import androidx.annotation.NonNull;
import chan.content.ChanManager;
import chan.http.HttpClient;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.database.ChanDatabase;
import com.mishiranu.dashchan.content.database.CommonDatabase;
import com.mishiranu.dashchan.content.database.PagesDatabase;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import java.io.File;
import java.util.Collections;
import java.util.List;

public class MainApplication extends Application {
	private static final String PROCESS_WEB_VIEW = "webview";

	private static MainApplication instance;

	public MainApplication() {
		instance = this;
	}

	private boolean checkProcess(String suffix) {
		return CommonUtils.equals(suffix, processSuffix);
	}

	public boolean isMainProcess() {
		return checkProcess(null);
	}

	private String processSuffix;

	@Override
	public void onCreate() {
		super.onCreate();

		ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
		if (processes == null) {
			processes = Collections.emptyList();
		}
		int pid = Process.myPid();
		for (ActivityManager.RunningAppProcessInfo process : processes) {
			if (process.pid == pid) {
				int index = process.processName.indexOf(':');
				if (index >= 0) {
					processSuffix = StringUtils.nullIfEmpty(process.processName.substring(index + 1));
				}
				break;
			}
		}

		LocaleManager.getInstance().updateConfiguration(getResources().getConfiguration());
		if (isMainProcess()) {
			Log.init();
			ChanManager.getInstance();
			HttpClient.getInstance();
			CommonDatabase.getInstance();
			PagesDatabase.getInstance();
			ChanDatabase.getInstance();
			CacheManager.getInstance();
			ChanManager.getInstance().loadLibraries();
		} else if (checkProcess(PROCESS_WEB_VIEW)) {
			IOUtils.deleteRecursive(getWebViewCacheDir());
		}
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		LocaleManager.getInstance().updateConfiguration(newConfig);
	}

	public static MainApplication getInstance() {
		return instance;
	}

	public Context getLocalizedContext() {
		return LocaleManager.getInstance().applyApplication(this);
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	public boolean isLowRam() {
		if (C.API_KITKAT) {
			ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
			return activityManager != null && activityManager.isLowRamDevice();
		} else {
			return Runtime.getRuntime().maxMemory() <= 64 * 1024 * 1024;
		}
	}

	private File getWebViewCacheDir() {
		return new File(super.getCacheDir(), "webview");
	}

	@Override
	public File getCacheDir() {
		if (checkProcess(PROCESS_WEB_VIEW)) {
			File dir = new File(getWebViewCacheDir(), "cache");
			dir.mkdirs();
			return dir;
		}
		return super.getCacheDir();
	}

	@Override
	public File getDir(String name, int mode) {
		if (checkProcess(PROCESS_WEB_VIEW)) {
			File dir = new File(getWebViewCacheDir(), name);
			dir.mkdirs();
			return dir;
		} else {
			return super.getDir(name, mode);
		}
	}
}
