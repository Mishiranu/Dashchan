package com.mishiranu.dashchan.content.net;

import android.net.Uri;
import chan.http.HttpException;
import chan.http.HttpHolder;
import java.util.Collections;
import java.util.Map;

public class RelayBlockResolver {
	private static final RelayBlockResolver INSTANCE = new RelayBlockResolver();

	public static RelayBlockResolver getInstance() {
		return INSTANCE;
	}

	private RelayBlockResolver() {}

	public static class Result {
		public final boolean blocked;
		public final boolean resolved;
		public final HttpHolder replaceHolder;

		public Result(boolean blocked, boolean resolved, HttpHolder replaceHolder) {
			this.blocked = blocked;
			this.resolved = resolved;
			this.replaceHolder = replaceHolder;
		}
	}

	public Result checkResponse(String chanName, Uri uri, HttpHolder holder) throws HttpException {
		Result result = CloudFlarePasser.getInstance().checkResponse(chanName, uri, holder);
		if (!result.blocked) {
			result = StormWallResolver.getInstance().checkResponse(chanName, uri, holder);
		}
		return result;
	}

	public Map<String, String> getCookies(String chanName) {
		String cookie = CloudFlarePasser.getInstance().getCookie(chanName);
		if (cookie != null) {
			return Collections.singletonMap(CloudFlarePasser.COOKIE_CLOUDFLARE, cookie);
		}
		return Collections.emptyMap();
	}
}
