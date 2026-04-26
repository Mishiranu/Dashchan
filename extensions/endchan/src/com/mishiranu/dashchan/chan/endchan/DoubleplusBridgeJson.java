package com.mishiranu.dashchan.chan.endchan;

import chan.content.InvalidResponseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Adapts the Node bridge’s {@code /opt/...} response JSON (see {@code ../bridge/index.js} and
 * {@code map.js} post objects), often wrapped as {@code { meta, data }}, into the lynxchan-shaped
 * objects expected by {@link EndchanModelMapper}. Lynxphp adds related {@code doubleplus/}
 * module endpoints; the live route table for :8443 is the bridge.
 */
public final class DoubleplusBridgeJson {
	private static final SimpleDateFormat CREATION_FORMAT =
			new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);

	static {
		CREATION_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	private DoubleplusBridgeJson() {}

	public static void requireMetaOk(JSONObject root) throws InvalidResponseException, JSONException {
		JSONObject meta = root.optJSONObject("meta");
		int code = meta != null ? meta.optInt("code", 200) : 200;
		if (code != 200) {
			throw new InvalidResponseException();
		}
	}

	public static Object unwrapData(JSONObject root) throws JSONException {
		if (!root.has("data")) {
			return null;
		}
		return root.get("data");
	}

	public static JSONObject bridgeFileToLynx(JSONObject f) throws JSONException {
		JSONObject out = new JSONObject();
		String path = CommonUtils.optJsonString(f, "path");
		if (path != null && !path.startsWith("/")) {
			path = "/" + path;
		}
		out.put("path", path != null ? path : "");
		String thumb = CommonUtils.optJsonString(f, "thumbnail_path");
		if (thumb != null && !thumb.isEmpty() && !"/spoiler.png".equals(thumb)) {
			if (!thumb.startsWith("/")) {
				thumb = "/" + thumb;
			}
			out.put("thumb", thumb);
		}
		out.put("originalName", CommonUtils.optJsonString(f, "filename"));
		out.put("size", f.optInt("size"));
		out.put("width", f.optInt("w"));
		out.put("height", f.optInt("h"));
		return out;
	}

	public static JSONObject bridgePostToLynx(JSONObject p, boolean isOriginalPost, String threadNumber)
			throws JSONException {
		JSONObject out = new JSONObject();
		String no = Integer.toString(p.optInt("no"));
		out.put("postId", no);
		if (isOriginalPost || threadNumber == null) {
			out.put("threadId", no);
		} else {
			out.put("threadId", threadNumber);
		}
		out.put("markdown", CommonUtils.optJsonString(p, "com", ""));
		out.put("creation", creationFromUnixSeconds(p.optLong("created_at")));
		out.put("name", CommonUtils.optJsonString(p, "name"));
		out.put("email", CommonUtils.optJsonString(p, "email"));
		out.put("id", CommonUtils.optJsonString(p, "id"));
		out.put("signedRole", CommonUtils.optJsonString(p, "capcode"));
		String sub = CommonUtils.optJsonString(p, "sub");
		if (sub != null) {
			out.put("subject", sub);
		}
		String flag = CommonUtils.optJsonString(p, "flag");
		if (flag != null && !flag.isEmpty()) {
			if (!flag.startsWith("/")) {
				flag = "/" + flag;
			}
			out.put("flag", flag);
		}
		out.put("flagName", CommonUtils.optJsonString(p, "flagName"));
		if (p.optInt("sticky") != 0) {
			out.put("pinned", 1);
		}
		if (p.optInt("closed") != 0) {
			out.put("locked", 1);
		}
		if (p.optInt("cyclic") != 0) {
			out.put("cyclic", 1);
		}
		JSONArray files = p.optJSONArray("files");
		if (files != null && files.length() > 0) {
			JSONArray lynxFiles = new JSONArray();
			for (int i = 0; i < files.length(); i++) {
				JSONObject lynxFile = bridgeFileToLynx(files.getJSONObject(i));
				if (!StringUtils.isEmpty(CommonUtils.optJsonString(lynxFile, "path"))) {
					lynxFiles.put(lynxFile);
				}
			}
			if (lynxFiles.length() > 0) {
				out.put("files", lynxFiles);
			}
		}
		return out;
	}

	public static JSONObject catalogThreadToLynx(JSONObject bridgeOp) throws JSONException {
		JSONObject out = bridgePostToLynx(bridgeOp, true, null);
		int omitted = Math.max(0, bridgeOp.optInt("reply_count"));
		out.put("ommitedPosts", omitted);
		return out;
	}

	public static JSONObject threadFromFlatPostArray(JSONArray posts) throws JSONException {
		if (posts == null || posts.length() == 0) {
			return new JSONObject();
		}
		JSONObject op = posts.getJSONObject(0);
		String threadNo = Integer.toString(op.optInt("no"));
		JSONObject root = bridgePostToLynx(op, true, threadNo);
		JSONArray replies = new JSONArray();
		for (int i = 1; i < posts.length(); i++) {
			replies.put(bridgePostToLynx(posts.getJSONObject(i), false, threadNo));
		}
		root.put("posts", replies);
		return root;
	}

	public static JSONObject boardPageThreadToLynx(JSONObject threadWrapper) throws JSONException {
		JSONArray posts = threadWrapper.optJSONArray("posts");
		if (posts == null || posts.length() == 0) {
			return new JSONObject();
		}
		JSONObject root = threadFromFlatPostArray(posts);
		int total = threadWrapper.optInt("thread_reply_count");
		if (total > 0) {
			int visible = posts.length();
			int omitted = Math.max(0, total - visible);
			root.put("ommitedPosts", omitted);
		}
		return root;
	}

	public static JSONArray mergeCatalogPages(JSONObject data) throws JSONException {
		JSONArray out = new JSONArray();
		JSONArray pages = data.optJSONArray("pages");
		if (pages == null) {
			return out;
		}
		for (int pi = 0; pi < pages.length(); pi++) {
			JSONObject page = pages.getJSONObject(pi);
			JSONArray threads = page.optJSONArray("threads");
			if (threads == null) {
				continue;
			}
			for (int ti = 0; ti < threads.length(); ti++) {
				out.put(catalogThreadToLynx(threads.getJSONObject(ti)));
			}
		}
		return out;
	}

	private static String creationFromUnixSeconds(long sec) {
		return CREATION_FORMAT.format(new Date(sec * 1000L));
	}
}
