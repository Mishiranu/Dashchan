package com.mishiranu.dashchan.chan.endchan;

import android.net.Uri;
import chan.content.model.FileAttachment;
import chan.content.model.Icon;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class EndchanModelMapper {
	public static FileAttachment createFileAttachment(JSONObject jsonObject, EndchanChanLocator locator)
			throws JSONException {
		FileAttachment attachment = new FileAttachment();
		attachment.setSize(jsonObject.optInt("size"));
		attachment.setWidth(jsonObject.optInt("width"));
		attachment.setHeight(jsonObject.optInt("height"));
		String path = CommonUtils.getJsonString(jsonObject, "path");
		String thumb = CommonUtils.optJsonString(jsonObject, "thumb");
		String originalName = CommonUtils.optJsonString(jsonObject, "originalName");
		attachment.setFileUri(locator, locator.buildPath(path));
		if ("/spoiler.png".equals(thumb)) {
			attachment.setSpoiler(true);
		} else if (!StringUtils.isEmpty(thumb)) {
			attachment.setThumbnailUri(locator, locator.buildPath(thumb));
		}
		if (!StringUtils.isEmpty(originalName)) {
			attachment.setOriginalName(StringUtils.clearHtml(originalName));
		}
		return attachment;
	}

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);

	static {
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	private static final Pattern PATTERN_BROKEN_LINK = Pattern.compile("(<a [^>]*?href=\"/[^/]+/res/)" +
			"(\\d+)(.html#\\2\")");
	private static final Pattern PATTERN_COLORED_TEXT = Pattern.compile("<span class=\"(\\w+)Text\">");

	public static Post createPost(JSONObject jsonObject, EndchanChanLocator locator, String threadNumber)
			throws JSONException, ParseException {
		Post post = new Post();
		if (jsonObject.optInt("pinned") != 0) {
			post.setSticky(true);
		}
		if (jsonObject.optInt("locked") != 0) {
			post.setClosed(true);
		}
		if (jsonObject.optInt("cyclic") != 0) {
			post.setCyclical(true);
		}
		if (threadNumber != null) {
			post.setParentPostNumber(threadNumber);
			post.setPostNumber(CommonUtils.getJsonString(jsonObject, "postId"));
		} else {
			post.setPostNumber(CommonUtils.getJsonString(jsonObject, "threadId"));
		}
		post.setTimestamp(DATE_FORMAT.parse(CommonUtils.getJsonString(jsonObject, "creation")).getTime());
		String name = CommonUtils.optJsonString(jsonObject, "name");
		if (!StringUtils.isEmpty(name)) {
			name = StringUtils.clearHtml(name).trim();
			if (!name.isEmpty()) {
				int index = name.indexOf('#');
				if (index >= 0) {
					post.setTripcode(name.substring(index).replace('#', '!'));
					name = index > 0 ? name.substring(0, index) : null;
				}
				post.setName(name);
			}
		}
		String identifier = CommonUtils.optJsonString(jsonObject, "id");
		if (!StringUtils.isEmpty(identifier)) {
			post.setIdentifier(StringUtils.nullIfEmpty(StringUtils.clearHtml(identifier).trim()));
		}
		String signedRole = CommonUtils.optJsonString(jsonObject, "signedRole");
		if (!StringUtils.isEmpty(signedRole)) {
			post.setCapcode(StringUtils.nullIfEmpty(StringUtils.clearHtml(signedRole).trim()));
		}
		String email = CommonUtils.optJsonString(jsonObject, "email");
		if ("sage".equals(email)) {
			post.setSage(true);
		} else {
			post.setEmail(StringUtils.nullIfEmpty(StringUtils.clearHtml(email).trim()));
		}
		String flag = CommonUtils.optJsonString(jsonObject, "flag");
		String flagName = CommonUtils.optJsonString(jsonObject, "flagName");
		if (flag != null) {
			Uri uri = locator.buildPath(flag);
			if (StringUtils.isEmpty(flagName)) {
				flagName = uri.getLastPathSegment();
				flagName = flagName.substring(0, flagName.indexOf('.')).toLowerCase(Locale.US);
			} else {
				flagName = StringUtils.clearHtml(flagName);
			}
			post.setIcons(new Icon(locator, uri, flagName));
		}
		String subject = CommonUtils.optJsonString(jsonObject, "subject");
		if (subject != null) {
			post.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(subject).trim()));
		}
		String comment = CommonUtils.getJsonString(jsonObject, "markdown");
		if (!StringUtils.isEmpty(comment)) {
			comment = comment.replaceAll("(<a class=\"quoteLink\".*?>)&gt&gt", "$1&gt;&gt;"); // Fix html
			if (threadNumber != null) {
				comment = StringUtils.replaceAll(comment, PATTERN_BROKEN_LINK,
						m -> m.group(1) + threadNumber + m.group(3));
			}
			comment = StringUtils.replaceAll(comment, PATTERN_COLORED_TEXT, matcher -> {
				String color = matcher.group(1);
				switch (color) {
					case "green":
					case "red": {
						// Green text used for quotes, red text used for headers
						return matcher.group();
					}
					case "meme": {
						return "<span colored=\"true\" style=\"color: #ff0000\">";
					}
					case "autism": {
						return "<span colored=\"true\" style=\"color: #aa44ff\">";
					}
					case "orange": {
						return "<span colored=\"true\" style=\"color: #ffaa00\">";
					}
					case "pink": {
						return "<span colored=\"true\" style=\"color: #ff66bb\">";
					}
					case "brown": {
						return "<span colored=\"true\" style=\"color: #aa6600\">";
					}
					default: {
						return "<span colored=\"true\" style=\"color: " + color + "\">";
					}
				}
			});
		}
		post.setComment(comment);
		post.setCommentMarkup(CommonUtils.optJsonString(jsonObject, "message"));
		JSONArray jsonArray = jsonObject.optJSONArray("files");
		if (jsonArray != null && jsonArray.length() > 0) {
			ArrayList<FileAttachment> attachments = new ArrayList<>();
			for (int i = 0; i < jsonArray.length(); i++) {
				attachments.add(createFileAttachment(jsonArray.getJSONObject(i), locator));
			}
			post.setAttachments(attachments);
		}
		return post;
	}

	public static Posts createPosts(JSONObject jsonObject, EndchanChanLocator locator) throws JSONException,
			ParseException {
		Post originalPost = createPost(jsonObject, locator, null);
		JSONArray jsonArray = jsonObject.optJSONArray("posts");
		ArrayList<Post> posts = new ArrayList<>(1 + (jsonArray != null ? jsonArray.length() : 0));
		posts.add(originalPost);
		if (jsonArray != null && jsonArray.length() > 0) {
			String threadNumber = originalPost.getPostNumber();
			for (int i = 0; i < jsonArray.length(); i++) {
				posts.add(createPost(jsonArray.getJSONObject(i), locator, threadNumber));
			}
		}
		return new Posts(posts);
	}

	public static Posts[] createThreads(JSONArray jsonArray, EndchanChanLocator locator) throws JSONException,
			ParseException {
		if (jsonArray != null && jsonArray.length() > 0) {
			Posts[] threads = new Posts[jsonArray.length()];
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject jsonObject = jsonArray.getJSONObject(i);
				Posts posts = createPosts(jsonArray.getJSONObject(i), locator);
				posts.addPostsCount(jsonObject.optInt("ommitedPosts") + posts.getPosts().length);
				threads[i] = posts;
			}
			return threads;
		}
		return null;
	}
}
