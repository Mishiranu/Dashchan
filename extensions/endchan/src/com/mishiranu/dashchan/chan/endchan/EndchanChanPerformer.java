package com.mishiranu.dashchan.chan.endchan;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Base64;
import chan.content.ApiException;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.http.MultipartEntity;
import chan.http.SimpleEntity;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class EndchanChanPerformer extends ChanPerformer {
	private static final String[] BOARDS_GENERAL = {"operate"};

	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException {
		EndchanChanLocator locator = EndchanChanLocator.get(this);
		if (locator.usesDoubleplusOptApi()) {
			return onReadThreadsDoubleplus(data, locator);
		}
		if (data.isCatalog()) {
			Uri uri = locator.buildPath(data.boardName, "catalog.json");
			try {
				JSONArray jsonArray = new JSONArray(new HttpRequest(uri, data)
						.setValidator(data.validator).perform().readString());
				if (jsonArray.length() == 0) {
					return null;
				}
				return new ReadThreadsResult(EndchanModelMapper.createThreads(jsonArray, locator));
			} catch (JSONException | ParseException e) {
				throw new InvalidResponseException(e);
			}
		} else {
			Uri uri = locator.buildPath(data.boardName, (data.pageNumber + 1) + ".json");
			try {
				JSONObject jsonObject = new JSONObject(new HttpRequest(uri, data)
						.setValidator(data.validator).perform().readString());
				if (data.pageNumber == 0) {
					EndchanChanConfiguration configuration = EndchanChanConfiguration.get(this);
					configuration.updateFromThreadsJson(data.boardName, jsonObject, true);
				}
				JSONArray jsonArray = jsonObject.optJSONArray("threads");
				if (jsonArray == null || jsonObject.length() == 0) {
					return null;
				}
				return new ReadThreadsResult(EndchanModelMapper.createThreads(jsonArray, locator));
			} catch (JSONException | ParseException e) {
				throw new InvalidResponseException(e);
			}
		}
	}

	private ReadThreadsResult onReadThreadsDoubleplus(ReadThreadsData data, EndchanChanLocator locator)
			throws HttpException, InvalidResponseException {
		try {
			if (data.isCatalog()) {
				Uri uri = locator.buildPathWithHost(locator.getLynxphpApiAuthority(),
						"opt", data.boardName, "catalog.json");
				JSONObject root = new JSONObject(new HttpRequest(uri, data)
						.setValidator(data.validator).perform().readString());
				DoubleplusBridgeJson.requireMetaOk(root);
				Object rawData = DoubleplusBridgeJson.unwrapData(root);
				if (!(rawData instanceof JSONObject)) {
					throw new InvalidResponseException();
				}
				JSONArray threads = DoubleplusBridgeJson.mergeCatalogPages((JSONObject) rawData);
				if (threads.length() == 0) {
					return null;
				}
				return new ReadThreadsResult(EndchanModelMapper.createThreads(threads, locator));
			} else {
				Uri uri = locator.buildPathWithHost(locator.getLynxphpApiAuthority(),
						"opt", "boards", data.boardName, Integer.toString(data.pageNumber));
				JSONObject root = new JSONObject(new HttpRequest(uri, data)
						.setValidator(data.validator).perform().readString());
				DoubleplusBridgeJson.requireMetaOk(root);
				Object rawData = DoubleplusBridgeJson.unwrapData(root);
				if (!(rawData instanceof JSONObject)) {
					throw new InvalidResponseException();
				}
				JSONObject dataObj = (JSONObject) rawData;
				JSONArray page1 = dataObj.optJSONArray("page1");
				if (page1 == null || page1.length() == 0) {
					return null;
				}
				if (data.pageNumber == 0) {
					JSONObject boardMeta = null;
					JSONObject meta = root.optJSONObject("meta");
					if (meta != null) {
						JSONObject boards = meta.optJSONObject("boards");
						if (boards != null) {
							boardMeta = boards.optJSONObject(data.boardName);
						}
					}
					if (boardMeta != null) {
						EndchanChanConfiguration configuration = EndchanChanConfiguration.get(this);
						configuration.updateFromThreadsJson(data.boardName,
								boardMetaToThreadsJson(data.boardName, boardMeta), true);
					}
				}
				JSONArray threads = new JSONArray();
				for (int i = 0; i < page1.length(); i++) {
					threads.put(DoubleplusBridgeJson.boardPageThreadToLynx(page1.getJSONObject(i)));
				}
				return new ReadThreadsResult(EndchanModelMapper.createThreads(threads, locator));
			}
		} catch (JSONException | ParseException e) {
			throw new InvalidResponseException(e);
		}
	}

	private static JSONObject boardMetaToThreadsJson(String boardUri, JSONObject boardMeta) throws JSONException {
		JSONObject o = new JSONObject();
		o.put("boardName", CommonUtils.optJsonString(boardMeta, "title", boardUri));
		o.put("boardDescription", CommonUtils.optJsonString(boardMeta, "description", ""));
		o.put("settings", boardMeta.optJSONArray("settings"));
		return o;
	}

	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException {
		EndchanChanLocator locator = EndchanChanLocator.get(this);
		if (locator.usesDoubleplusOptApi()) {
			try {
				Uri uri = locator.buildPathWithHost(locator.getLynxphpApiAuthority(),
						"opt", data.boardName, "thread", data.threadNumber);
				JSONObject root = new JSONObject(new HttpRequest(uri, data)
						.setValidator(data.validator).perform().readString());
				DoubleplusBridgeJson.requireMetaOk(root);
				Object rawData = DoubleplusBridgeJson.unwrapData(root);
				if (!(rawData instanceof JSONArray)) {
					throw new InvalidResponseException();
				}
				JSONObject threadJson = DoubleplusBridgeJson.threadFromFlatPostArray((JSONArray) rawData);
				return new ReadPostsResult(EndchanModelMapper.createPosts(threadJson, locator));
			} catch (JSONException | ParseException e) {
				throw new InvalidResponseException(e);
			}
		}
		Uri uri = locator.buildPath(data.boardName, "res", data.threadNumber + ".json");
		try {
			JSONObject jsonObject = new JSONObject(new HttpRequest(uri, data)
					.setValidator(data.validator).perform().readString());
			return new ReadPostsResult(EndchanModelMapper.createPosts(jsonObject, locator));
		} catch (JSONException | ParseException e) {
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException {
		EndchanChanLocator locator = EndchanChanLocator.get(this);
		if (locator.usesDoubleplusOptApi()) {
			return onReadBoardsDoubleplus(data, locator);
		}
		EndchanChanConfiguration configuration = EndchanChanConfiguration.get(this);
		Board[] boards = new Board[BOARDS_GENERAL.length];
		try {
			for (int i = 0; i < BOARDS_GENERAL.length; i++) {
				Uri uri = locator.buildPath(BOARDS_GENERAL[i], "1.json");
				JSONObject jsonObject = new JSONObject(new HttpRequest(uri, data).perform().readString());
				String title = CommonUtils.getJsonString(jsonObject, "boardName");
				String description = CommonUtils.optJsonString(jsonObject, "boardDescription");
				configuration.updateFromThreadsJson(BOARDS_GENERAL[i], jsonObject, false);
				boards[i] = new Board(BOARDS_GENERAL[i], title, description);
			}
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
		return new ReadBoardsResult(new BoardCategory("General", boards));
	}

	private ReadBoardsResult onReadBoardsDoubleplus(ReadBoardsData data, EndchanChanLocator locator)
			throws HttpException, InvalidResponseException {
		try {
			Uri uri = locator.buildPathWithHost(locator.getLynxphpApiAuthority(), "opt", "boards.json");
			JSONObject root = new JSONObject(new HttpRequest(uri, data).perform().readString());
			DoubleplusBridgeJson.requireMetaOk(root);
			Object raw = DoubleplusBridgeJson.unwrapData(root);
			JSONArray list = raw instanceof JSONArray ? (JSONArray) raw
					: ((JSONObject) raw).optJSONArray("boards");
			if (list == null) {
				throw new InvalidResponseException();
			}
			ArrayList<Board> boards = new ArrayList<>();
			HashSet<String> ignore = new HashSet<>();
			Collections.addAll(ignore, BOARDS_GENERAL);
			for (int i = 0; i < list.length(); i++) {
				JSONObject b = list.getJSONObject(i);
				String boardName = boardUriFromBoardJson(b);
				if (boardName != null && !ignore.contains(boardName)) {
					String title = CommonUtils.optJsonString(b, "title", boardName);
					String description = CommonUtils.optJsonString(b, "description", "");
					boards.add(new Board(boardName, title, description));
				}
			}
			int n = Math.min(boards.size(), BOARDS_GENERAL.length);
			Board[] out = new Board[n];
			for (int i = 0; i < n; i++) {
				out[i] = boards.get(i);
			}
			return new ReadBoardsResult(new BoardCategory("General", out));
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}

	private static String boardUriFromBoardJson(JSONObject b) {
		String uri = CommonUtils.optJsonString(b, "uri");
		if (!StringUtils.isEmpty(uri)) {
			return uri;
		}
		return CommonUtils.optJsonString(b, "boardUri");
	}

	@Override
	public ReadUserBoardsResult onReadUserBoards(ReadUserBoardsData data) throws HttpException,
			InvalidResponseException {
		EndchanChanLocator locator = EndchanChanLocator.get(this);
		if (locator.usesDoubleplusOptApi()) {
			return onReadUserBoardsDoubleplus(data, locator);
		}
		Uri uri = locator.buildQuery("boards.js", "json", "1");
		try {
			JSONObject jsonObject = new JSONObject(new HttpRequest(uri, data).perform().readString());
			HashSet<String> ignoreBoardNames = new HashSet<>();
			Collections.addAll(ignoreBoardNames, BOARDS_GENERAL);
			ArrayList<Board> boards = new ArrayList<>();
			for (int i = 0, count = jsonObject.getInt("pageCount"); i < count; i++) {
				if (i > 0) {
					uri = locator.buildQuery("boards.js", "json", "1", "page", Integer.toString(i + 1));
					jsonObject = new JSONObject(new HttpRequest(uri, data).perform().readString());
				}
				JSONArray jsonArray = jsonObject.getJSONArray("boards");
				for (int j = 0; j < jsonArray.length(); j++) {
					jsonObject = jsonArray.getJSONObject(j);
					String boardName = CommonUtils.getJsonString(jsonObject, "boardUri");
					if (!ignoreBoardNames.contains(boardName)) {
						String title = CommonUtils.getJsonString(jsonObject, "boardName");
						String description = CommonUtils.getJsonString(jsonObject, "boardDescription");
						boards.add(new Board(boardName, title, description));
					}
				}
			}
			return new ReadUserBoardsResult(boards);
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}

	private ReadUserBoardsResult onReadUserBoardsDoubleplus(ReadUserBoardsData data, EndchanChanLocator locator)
			throws HttpException, InvalidResponseException {
		try {
			Uri uri = locator.buildPathWithHost(locator.getLynxphpApiAuthority(), "opt", "boards.json");
			JSONObject root = new JSONObject(new HttpRequest(uri, data).perform().readString());
			DoubleplusBridgeJson.requireMetaOk(root);
			Object raw = DoubleplusBridgeJson.unwrapData(root);
			JSONArray list = raw instanceof JSONArray ? (JSONArray) raw
					: ((JSONObject) raw).optJSONArray("boards");
			if (list == null) {
				throw new InvalidResponseException();
			}
			HashSet<String> ignoreBoardNames = new HashSet<>();
			Collections.addAll(ignoreBoardNames, BOARDS_GENERAL);
			ArrayList<Board> boards = new ArrayList<>();
			for (int j = 0; j < list.length(); j++) {
				JSONObject jsonObject = list.getJSONObject(j);
				String boardName = boardUriFromBoardJson(jsonObject);
				if (boardName == null || ignoreBoardNames.contains(boardName)) {
					continue;
				}
				String title = CommonUtils.optJsonString(jsonObject, "title",
						CommonUtils.optJsonString(jsonObject, "boardName", boardName));
				String description = CommonUtils.optJsonString(jsonObject, "description",
						CommonUtils.optJsonString(jsonObject, "boardDescription", ""));
				boards.add(new Board(boardName, title, description));
			}
			return new ReadUserBoardsResult(boards);
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}

	private static final String REQUIRE_REPORT = "report";

	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException {
		EndchanChanLocator locator = EndchanChanLocator.get(this);
		if (locator.usesDoubleplusOptApi()) {
			return onReadCaptchaDoubleplus(data, locator);
		}
		boolean needCaptcha = false;
		if (REQUIRE_REPORT.equals(data.requirement)) {
			needCaptcha = true;
		} else if (data.threadNumber == null) {
			Uri uri = locator.createBoardUri(data.boardName, 0);
			String responseText = new HttpRequest(uri, data).perform().readString();
			if (responseText.contains(" ")) {
				needCaptcha = true;
			}
		}

		if (needCaptcha) {
			Uri uri = locator.buildPath("captcha.js");
			HttpResponse response = new HttpRequest(uri, data).perform();
			Bitmap image = response.readBitmap();
			String captchaId = response.getCookieValue("captchaid");
			if (image == null || captchaId == null) {
				throw new InvalidResponseException();
			}

			int[] pixels = new int[image.getWidth() * image.getHeight()];
			image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
			for (int i = 0; i < pixels.length; i++) {
				pixels[i] = (0xff - Math.max(Color.red(pixels[i]), Color.blue(pixels[i]))) << 24;
			}
			Bitmap newImage = Bitmap.createBitmap(pixels, image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
			image.recycle();
			image = CommonUtils.trimBitmap(newImage, 0x00000000);
			if (image == null) {
				image = newImage;
			} else if (image != newImage) {
				newImage.recycle();
			}

			CaptchaData captchaData = new CaptchaData();
			captchaData.put(CaptchaData.CHALLENGE, captchaId);
			return new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(image);
		} else {
			return new ReadCaptchaResult(CaptchaState.SKIP, null);
		}
	}

	private ReadCaptchaResult onReadCaptchaDoubleplus(ReadCaptchaData data, EndchanChanLocator locator)
			throws HttpException, InvalidResponseException {
		boolean needCaptcha = REQUIRE_REPORT.equals(data.requirement);
		if (!needCaptcha && data.threadNumber == null) {
			Uri uri = locator.createBoardUri(data.boardName, 0);
			String responseText = new HttpRequest(uri, data).perform().readString();
			if (responseText.contains(" ")) {
				needCaptcha = true;
			}
		}
		if (!needCaptcha) {
			return new ReadCaptchaResult(CaptchaState.SKIP, null);
		}
		SimpleEntity entity = new SimpleEntity();
		entity.setContentType("application/json; charset=utf-8");
		entity.setData("{}");
		Uri uri = locator.buildPathWithHost(locator.getLynxphpApiAuthority(), "doubleplus", "CAPTCHA", "json");
		try {
			JSONObject root = new JSONObject(new HttpRequest(uri, data).setPostMethod(entity).perform().readString());
			if (root.has("meta") && root.optJSONObject("data") != null) {
				root = root.getJSONObject("data");
			}
			String id = CommonUtils.optJsonString(root, "id");
			String imgB64 = CommonUtils.optJsonString(root, "img");
			if (StringUtils.isEmpty(id) || StringUtils.isEmpty(imgB64)) {
				throw new InvalidResponseException();
			}
			byte[] raw = Base64.decode(imgB64, Base64.DEFAULT);
			Bitmap image = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
			if (image == null) {
				throw new InvalidResponseException();
			}
			CaptchaData captchaData = new CaptchaData();
			captchaData.put(CaptchaData.CHALLENGE, id);
			return new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(image);
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}

	private String trimPassword(String password) {
		return password != null && password.length() > 8 ? password.substring(0, 8) : password;
	}

	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException {
		EndchanChanLocator locator = EndchanChanLocator.get(this);
		if (locator.usesDoubleplusOptApi()) {
			return onSendPostDoubleplus(data, locator);
		}
		SimpleEntity entity = new SimpleEntity();
		entity.setContentType("application/json; charset=utf-8");
		JSONObject jsonObject = new JSONObject();
		JSONObject parametersObject = new JSONObject();
		try {
			try {
				jsonObject.put("parameters", parametersObject);
				parametersObject.put("boardUri", data.boardName);
				if (data.threadNumber != null) {
					parametersObject.put("threadId", data.threadNumber);
				}
				if (data.name != null) {
					parametersObject.put("name", data.name);
				}
				if (data.subject != null) {
					parametersObject.put("subject", data.subject);
				}
				if (data.password != null) {
					parametersObject.put("password", trimPassword(data.password));
				}
				parametersObject.put("message", StringUtils.emptyIfNull(data.comment));
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}

			String captchaId = data.captchaData != null ? data.captchaData.get(CaptchaData.CHALLENGE) : null;
			if (captchaId != null) {
				try {
					jsonObject.put("captchaId", captchaId);
					parametersObject.put("captcha", StringUtils.emptyIfNull(data.captchaData.get(CaptchaData.INPUT)));
				} catch (JSONException e) {
					throw new RuntimeException(e);
				}
			}

			if (data.attachments != null) {
				JSONArray jsonArray = new JSONArray();
				MessageDigest messageDigest;
				try {
					messageDigest = MessageDigest.getInstance("MD5");
				} catch (NoSuchAlgorithmException e) {
					throw new RuntimeException(e);
				}
				for (int i = 0; i < data.attachments.length; i++) {
					SendPostData.Attachment attachment = data.attachments[i];
					byte[] bytes = new byte[(int) attachment.getSize()];
					try (InputStream inputStream = attachment.openInputSteam()) {
						int offset = 0;
						while (offset < bytes.length) {
							int count = inputStream.read(bytes, offset, bytes.length - offset);
							if (count < 0) {
								throw new IOException("Unexpected end of attachment stream");
							}
							offset += count;
						}
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
					messageDigest.reset();
					byte[] digest = messageDigest.digest(bytes);
					StringBuilder digestBuilder = new StringBuilder();
					for (byte b : digest) {
						digestBuilder.append(String.format(Locale.US, "%02x", b));
					}
					Uri uri = locator.buildQuery("checkFileIdentifier.js", "identifier", digestBuilder + "-"
							+ attachment.getMimeType().replace("/", ""));
					String responseText = new HttpRequest(uri, data).perform().readString();
					JSONObject fileObject = new JSONObject();
					try {
						if ("false".equals(responseText)) {
							fileObject.put("content", "data:" + attachment.getMimeType() + ";base64,"
									+ Base64.encodeToString(bytes, Base64.NO_WRAP));
						} else if ("true".equals(responseText)) {
							fileObject.put("mime", attachment.getMimeType());
							fileObject.put("md5", digestBuilder.toString());
						} else {
							throw new InvalidResponseException();
						}
						fileObject.put("name", attachment.getFileName());
						if (attachment.optionSpoiler) {
							fileObject.put("spoiler", true);
						}
						jsonArray.put(fileObject);
					} catch (JSONException e) {
						throw new RuntimeException(e);
					}
				}
				try {
					parametersObject.put("files", jsonArray);
				} catch (JSONException e) {
					throw new RuntimeException(e);
				}
			}
			entity.setData(jsonObject.toString());
		} catch (OutOfMemoryError e) {
			throw new RuntimeException(e);
		}

		Uri uri = locator.buildPath(".api", data.threadNumber != null ? "replyThread" : "newThread");
		try {
			jsonObject = new JSONObject(new HttpRequest(uri, data).setPostMethod(entity)
					.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString());
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}

		return parseSendPostResponse(jsonObject, data);
	}

	private SendPostResult onSendPostDoubleplus(SendPostData data, EndchanChanLocator locator)
			throws HttpException, ApiException, InvalidResponseException {
		MultipartEntity entity = new MultipartEntity();
		entity.add("boardUri", data.boardName);
		entity.add("threadId", data.threadNumber);
		entity.add("name", data.name);
		entity.add("email", data.email);
		entity.add("subject", data.subject);
		entity.add("password", data.password != null ? trimPassword(data.password) : null);
		entity.add("message", StringUtils.emptyIfNull(data.comment));
		entity.add("files", buildBridgeUploadedFilesJson(data, locator));
		if (data.optionSage) {
			entity.add("sage", "1");
		}
		String captchaId = data.captchaData != null ? data.captchaData.get(CaptchaData.CHALLENGE) : null;
		if (captchaId != null) {
			entity.add("captcha_id", captchaId);
			entity.add("captcha", StringUtils.emptyIfNull(data.captchaData.get(CaptchaData.INPUT)));
		}
		Uri uri = locator.buildPathWithHost(locator.getLynxphpApiAuthority(), "lynx",
				data.threadNumber != null ? "replyThread" : "newThread");
		try {
			JSONObject jsonObject = new JSONObject(new HttpRequest(uri, data).setPostMethod(entity)
					.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString());
			return parseBridgePostResponse(jsonObject, data);
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}

	private String buildBridgeUploadedFilesJson(SendPostData data, EndchanChanLocator locator)
			throws HttpException, InvalidResponseException {
		JSONArray files = new JSONArray();
		if (data.attachments == null) {
			return files.toString();
		}
		for (SendPostData.Attachment attachment : data.attachments) {
			MultipartEntity uploadEntity = new MultipartEntity();
			uploadEntity.add("files", new MultipartEntity.Openable() {
				@Override
				public String getFileName() {
					return attachment.getFileName();
				}

				@Override
				public String getMimeType() {
					return attachment.getMimeType();
				}

				@Override
				public InputStream openInputStream() throws IOException {
					return attachment.openInputSteam();
				}

				@Override
				public long getSize() {
					return attachment.getSize();
				}
			}, null);
			Uri uri = locator.buildPathWithHost(locator.getLynxphpApiAuthority(), "lynx", "files");
			try {
				JSONObject root = new JSONObject(new HttpRequest(uri, data).setPostMethod(uploadEntity)
						.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString());
				DoubleplusBridgeJson.requireMetaOk(root);
				Object raw = DoubleplusBridgeJson.unwrapData(root);
				if (!(raw instanceof JSONObject)) {
					throw new InvalidResponseException();
				}
				JSONObject uploaded = (JSONObject) raw;
				uploaded.put("spoiler", attachment.optionSpoiler);
				files.put(uploaded);
			} catch (JSONException e) {
				throw new InvalidResponseException(e);
			}
		}
		return files.toString();
	}

	private JSONArray buildAttachmentJsonArray(SendPostData data, EndchanChanLocator locator)
			throws JSONException, NoSuchAlgorithmException, IOException, HttpException, InvalidResponseException {
		JSONArray jsonArray = new JSONArray();
		MessageDigest messageDigest = MessageDigest.getInstance("MD5");
		for (int i = 0; i < data.attachments.length; i++) {
			SendPostData.Attachment attachment = data.attachments[i];
			byte[] bytes = new byte[(int) attachment.getSize()];
			try (InputStream inputStream = attachment.openInputSteam()) {
				inputStream.read(bytes);
			}
			messageDigest.reset();
			byte[] digest = messageDigest.digest(bytes);
			StringBuilder digestBuilder = new StringBuilder();
			for (byte b : digest) {
				digestBuilder.append(String.format(Locale.US, "%02x", b));
			}
			Uri uri = locator.buildQueryWithHost(locator.getLynxphpApiAuthority(), "checkFileIdentifier.js",
					"identifier", digestBuilder + "-" + attachment.getMimeType().replace("/", ""));
			String responseText = new HttpRequest(uri, data).perform().readString();
			JSONObject fileObject = new JSONObject();
			if ("false".equals(responseText)) {
				fileObject.put("content", "data:" + attachment.getMimeType() + ";base64,"
						+ Base64.encodeToString(bytes, Base64.NO_WRAP));
			} else if ("true".equals(responseText)) {
				fileObject.put("mime", attachment.getMimeType());
				fileObject.put("md5", digestBuilder.toString());
			} else {
				throw new InvalidResponseException();
			}
			fileObject.put("name", attachment.getFileName());
			if (attachment.optionSpoiler) {
				fileObject.put("spoiler", true);
			}
			jsonArray.put(fileObject);
		}
		return jsonArray;
	}

	private SendPostResult parseSendPostResponse(JSONObject jsonObject, SendPostData data)
			throws ApiException, InvalidResponseException {
		String status = jsonObject.optString("status");
		if ("ok".equals(status)) {
			String postNumber = Integer.toString(jsonObject.optInt("data"));
			String threadNumber = data.threadNumber;
			if (threadNumber == null) {
				threadNumber = postNumber;
				postNumber = null;
			}
			CommonUtils.sleepMaxRealtime(SystemClock.elapsedRealtime(), 2000);
			return new SendPostResult(threadNumber, postNumber);
		}
		if (!"error".equals(status) && !"blank".equals(status)) {
			CommonUtils.writeLog("Endchan send message", jsonObject.toString());
			throw new InvalidResponseException();
		}
		String errorMessage = jsonObject.optString("data");
		int errorType = 0;
		if (errorMessage.contains("Wrong captcha") || errorMessage.contains("Expired captcha")) {
			errorType = ApiException.SEND_ERROR_CAPTCHA;
		} else if (errorMessage.contains("Flood detected")) {
			errorType = ApiException.SEND_ERROR_TOO_FAST;
		} else if (errorMessage.contains("Either a message or a file is required")
				|| "message".equals(errorMessage)) {
			errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
		} else if (errorMessage.contains("Board not found")) {
			errorType = ApiException.SEND_ERROR_NO_BOARD;
		} else if (errorMessage.contains("Thread not found")) {
			errorType = ApiException.SEND_ERROR_NO_THREAD;
		}
		if (errorType != 0) {
			throw new ApiException(errorType);
		}
		CommonUtils.writeLog("Endchan send message", status, errorMessage);
		throw new ApiException(status + ": " + errorMessage);
	}

	private SendPostResult parseBridgePostResponse(JSONObject jsonObject, SendPostData data)
			throws ApiException, InvalidResponseException {
		JSONObject meta = jsonObject.optJSONObject("meta");
		if (meta != null && meta.optInt("code", 200) != 200) {
			throw new InvalidResponseException();
		}
		Object rawData = jsonObject.opt("data");
		if (rawData instanceof JSONObject) {
			JSONObject dataObject = (JSONObject) rawData;
			JSONArray issues = dataObject.optJSONArray("issues");
			if (issues != null && issues.length() > 0) {
				throwSendError(issues.optString(0));
			}
			int id = dataObject.optInt("id");
			if (id <= 0) {
				throw new InvalidResponseException();
			}
			return data.threadNumber == null ? new SendPostResult(Integer.toString(id), null)
					: new SendPostResult(data.threadNumber, Integer.toString(id));
		}
		String raw = rawData != null ? rawData.toString() : null;
		if (StringUtils.isEmpty(raw) || "0".equals(raw) || "null".equals(raw)) {
			throw new InvalidResponseException();
		}
		throwSendError(raw);
		if (!raw.matches("\\d+")) {
			throw new ApiException(raw);
		}
		return data.threadNumber == null ? new SendPostResult(raw, null) : new SendPostResult(data.threadNumber, raw);
	}

	private void throwSendError(String errorMessage) throws ApiException {
		if (errorMessage == null) {
			return;
		}
		if (errorMessage.contains("Wrong captcha") || errorMessage.contains("Expired captcha")
				|| errorMessage.contains("id length problem")) {
			throw new ApiException(ApiException.SEND_ERROR_CAPTCHA);
		} else if (errorMessage.contains("Flood detected")) {
			throw new ApiException(ApiException.SEND_ERROR_TOO_FAST);
		} else if (errorMessage.contains("Either a message or a file is required")
				|| "message".equals(errorMessage)) {
			throw new ApiException(ApiException.SEND_ERROR_EMPTY_COMMENT);
		} else if (errorMessage.contains("Board not found")) {
			throw new ApiException(ApiException.SEND_ERROR_NO_BOARD);
		} else if (errorMessage.contains("Thread not found")) {
			throw new ApiException(ApiException.SEND_ERROR_NO_THREAD);
		}
	}

	private static void fillDeleteReportPostings(JSONObject parametersObject, String boardName, String threadNumber,
			Collection<String> postNumbers) throws JSONException {
		JSONArray jsonArray = new JSONArray();
		for (String postNumber : postNumbers) {
			JSONObject postObject = new JSONObject();
			postObject.put("board", boardName);
			postObject.put("thread", threadNumber);
			if (!postNumber.equals(threadNumber)) {
				postObject.put("post", postNumber);
			}
			jsonArray.put(postObject);
		}
		parametersObject.put("postings", jsonArray);
	}

	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException {
		EndchanChanLocator locator = EndchanChanLocator.get(this);
		if (locator.usesDoubleplusOptApi()) {
			return onSendDeletePostsDoubleplus(data, locator);
		}
		JSONObject jsonObject = new JSONObject();
		JSONObject parametersObject = new JSONObject();
		try {
			jsonObject.put("parameters", parametersObject);
			parametersObject.put("password", trimPassword(data.password));
			parametersObject.put("deleteMedia", true);
			if (data.optionFilesOnly) {
				parametersObject.put("deleteUploads", true);
			}
			fillDeleteReportPostings(parametersObject, data.boardName, data.threadNumber, data.postNumbers);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		SimpleEntity entity = new SimpleEntity();
		entity.setContentType("application/json; charset=utf-8");
		entity.setData(jsonObject.toString());
		Uri uri = locator.buildPath(".api", "deleteContent");
		try {
			jsonObject = new JSONObject(new HttpRequest(uri, data).setPostMethod(entity)
					.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString());
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
		if ("error".equals(CommonUtils.optJsonString(jsonObject, "status"))) {
			String errorMessage = CommonUtils.optJsonString(jsonObject, "data");
			if (errorMessage != null) {
				if (errorMessage.contains("Invalid account")) {
					throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
				}
				CommonUtils.writeLog("Endchan delete message", errorMessage);
				throw new ApiException(errorMessage);
			}
		}
		try {
			jsonObject = jsonObject.getJSONObject("data");
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
		if (jsonObject.optInt("removedThreads") + jsonObject.optInt("removedPosts") > 0) {
			return new SendDeletePostsResult();
		} else {
			throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
		}
	}

	private SendDeletePostsResult onSendDeletePostsDoubleplus(SendDeletePostsData data, EndchanChanLocator locator)
			throws HttpException, ApiException, InvalidResponseException {
		MultipartEntity entity = createBridgeContentActionsEntity(data.boardName, data.threadNumber, data.postNumbers);
		Uri uri = locator.buildQueryWithHost(locator.getLynxphpApiAuthority(), "lynx/contentActions.js",
				"action", "delete",
				"password", StringUtils.emptyIfNull(trimPassword(data.password)),
				"filesOnly", data.optionFilesOnly ? "1" : "0");
		JSONObject response = readBridgeContentActionsResponse(uri, entity, data);
		if ("ok".equals(CommonUtils.optJsonString(response, "status"))) {
			return new SendDeletePostsResult();
		}
		String errorMessage = CommonUtils.optJsonString(response, "data");
		if (errorMessage != null) {
			if (errorMessage.contains("Invalid account") || errorMessage.contains("password")) {
				throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
			}
			throw new ApiException(errorMessage);
		}
		throw new InvalidResponseException();
	}

	@Override
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
			InvalidResponseException {
		EndchanChanLocator locator = EndchanChanLocator.get(this);
		if (locator.usesDoubleplusOptApi()) {
			return onSendReportPostsDoubleplus(data, locator);
		}
		JSONObject jsonObject = new JSONObject();
		JSONObject parametersObject = new JSONObject();
		try {
			jsonObject.put("parameters", parametersObject);
			parametersObject.put("reason", StringUtils.emptyIfNull(data.comment));
			if (data.options.contains("global")) {
				parametersObject.put("global", true);
			}
			fillDeleteReportPostings(parametersObject, data.boardName, data.threadNumber, data.postNumbers);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		boolean retry = false;
		while (true) {
			CaptchaData captchaData = requireUserCaptcha(REQUIRE_REPORT, data.boardName, data.threadNumber, retry);
			if (captchaData == null) {
				throw new ApiException(ApiException.REPORT_ERROR_NO_ACCESS);
			}
			try {
				jsonObject.put("captchaId", captchaData.get(CaptchaData.CHALLENGE));
				parametersObject.put("captcha", StringUtils.emptyIfNull(captchaData.get(CaptchaData.INPUT)));
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
			retry = true;
			SimpleEntity entity = new SimpleEntity();
			entity.setContentType("application/json; charset=utf-8");
			entity.setData(jsonObject.toString());
			Uri uri = locator.buildPath(".api", "reportContent");
			JSONObject responseJsonObject;
			try {
				responseJsonObject = new JSONObject(new HttpRequest(uri, data).setPostMethod(entity)
						.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString());
			} catch (JSONException e) {
				throw new InvalidResponseException(e);
			}
			String status = CommonUtils.optJsonString(responseJsonObject, "status");
			if ("ok".equals(status)) {
				return null;
			}
			String errorMessage = CommonUtils.optJsonString(responseJsonObject, "data");
			if (errorMessage != null) {
				if (errorMessage.contains("Wrong captcha") || errorMessage.contains("Expired captcha")) {
					continue;
				}
				CommonUtils.writeLog("Endchan report message", status, errorMessage);
				throw new ApiException(status + ": " + errorMessage);
			}
			throw new InvalidResponseException();
		}
	}

	private SendReportPostsResult onSendReportPostsDoubleplus(SendReportPostsData data, EndchanChanLocator locator)
			throws HttpException, ApiException, InvalidResponseException {
		boolean retry = false;
		while (true) {
			CaptchaData captchaData = requireUserCaptcha(REQUIRE_REPORT, data.boardName, data.threadNumber, retry);
			if (captchaData == null) {
				throw new ApiException(ApiException.REPORT_ERROR_NO_ACCESS);
			}
			MultipartEntity entity = createBridgeContentActionsEntity(data.boardName, data.threadNumber, data.postNumbers);
			Uri uri = locator.buildQueryWithHost(locator.getLynxphpApiAuthority(), "lynx/contentActions.js",
					"action", "report",
					"captcha_id", captchaData.get(CaptchaData.CHALLENGE),
					"captcha", StringUtils.emptyIfNull(captchaData.get(CaptchaData.INPUT)),
					"report_reason", StringUtils.emptyIfNull(data.comment),
					"global", data.options.contains("global") ? "1" : "0");
			JSONObject response = readBridgeContentActionsResponse(uri, entity, data);
			String status = CommonUtils.optJsonString(response, "status");
			if ("ok".equals(status)) {
				return null;
			}
			String errorMessage = CommonUtils.optJsonString(response, "data");
			if (errorMessage != null) {
				if (errorMessage.contains("Wrong captcha") || errorMessage.contains("Expired captcha")
						|| errorMessage.contains("id length problem")) {
					retry = true;
					continue;
				}
				throw new ApiException(status + ": " + errorMessage);
			}
			throw new InvalidResponseException();
		}
	}

	private MultipartEntity createBridgeContentActionsEntity(String boardName, String threadNumber,
			Collection<String> postNumbers) {
		MultipartEntity entity = new MultipartEntity();
		for (String postNumber : postNumbers) {
			entity.add(boardName + '-' + threadNumber + '-' + postNumber, "on");
		}
		return entity;
	}

	private JSONObject readBridgeContentActionsResponse(Uri uri, MultipartEntity entity, HttpRequest.Preset preset)
			throws HttpException, InvalidResponseException {
		try {
			JSONObject response = new JSONObject(new HttpRequest(uri, preset).setPostMethod(entity)
					.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString());
			if (response.has("meta")) {
				DoubleplusBridgeJson.requireMetaOk(response);
				Object raw = DoubleplusBridgeJson.unwrapData(response);
				if (raw instanceof JSONObject) {
					return (JSONObject) raw;
				}
			}
			return response;
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}
}
