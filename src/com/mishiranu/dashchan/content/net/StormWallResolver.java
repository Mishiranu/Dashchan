package com.mishiranu.dashchan.content.net;

import android.net.Uri;
import chan.http.HttpException;
import chan.http.HttpHolder;
import java.util.List;

public class StormWallResolver {
	private static final StormWallResolver INSTANCE = new StormWallResolver();

	public static StormWallResolver getInstance() {
		return INSTANCE;
	}

	private StormWallResolver() {}

	public RelayBlockResolver.Result checkResponse(String chanName, Uri uri, HttpHolder holder) throws HttpException {
		List<String> contentType = holder.getHeaderFields().get("Content-Type");
		if (contentType != null && contentType.size() == 1 && contentType.get(0).startsWith("text/html")) {
			String responseText = holder.read().getString();
			if (responseText.contains("<script src=\"https://static.stormwall.pro")) {
				// TODO Bypass protection
				return new RelayBlockResolver.Result(true, false, null);
			}
		}
		return new RelayBlockResolver.Result(false, false, null);
	}
}
