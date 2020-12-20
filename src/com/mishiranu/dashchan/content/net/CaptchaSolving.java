package com.mishiranu.dashchan.content.net;

import android.net.Uri;
import android.os.SystemClock;
import android.util.Pair;
import chan.content.Chan;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.SimpleEntity;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.ErrorItem;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public class CaptchaSolving {
	private static final CaptchaSolving INSTANCE = new CaptchaSolving();

	public static CaptchaSolving getInstance() {
		return INSTANCE;
	}

	public static class UnsupportedServiceException extends Exception {}
	public static class InvalidTokenException extends Exception {}

	private static class TimeoutException extends Exception {
		public TimeoutException(String message) {
			super(message);
		}
	}

	private static class Configuration {
		public final String endpoint;
		public final String token;
		public final int timeout;

		public Configuration(String endpoint, String token, int timeout) {
			this.endpoint = endpoint;
			this.token = token;
			this.timeout = timeout;
		}
	}

	private Uri createUri(String endpoint) {
		Uri uri = Uri.parse(endpoint);
		if (StringUtils.isEmpty(uri.getScheme())) {
			uri = uri.buildUpon().scheme(Chan.getFallback().locator.isUseHttps() ? "https" : "http").build();
		}
		return uri;
	}

	private Configuration getConfiguration() {
		Map<String, String> map = Preferences.getCaptchaSolving();
		if (map == null) {
			return null;
		}
		String endpoint = map.get(Preferences.SUB_KEY_CAPTCHA_SOLVING_ENDPOINT);
		String token = map.get(Preferences.SUB_KEY_CAPTCHA_SOLVING_TOKEN);
		if (StringUtils.isEmpty(endpoint) || StringUtils.isEmpty(token)) {
			return null;
		}
		String timeoutString = map.get(Preferences.SUB_KEY_CAPTCHA_SOLVING_TIMEOUT);
		int timeout = -1;
		if (timeoutString != null) {
			try {
				timeout = Integer.parseInt(timeoutString);
			} catch (NumberFormatException e) {
				// Ignore
			}
		}
		return new Configuration(endpoint, token, timeout);
	}

	public boolean hasConfiguration() {
		return getConfiguration() != null;
	}

	public Map<String, String> checkService(HttpHolder holder) throws HttpException,
			UnsupportedServiceException, InvalidTokenException {
		Configuration configuration = getConfiguration();
		if (configuration == null) {
			throw new UnsupportedServiceException();
		}
		LinkedHashMap<String, String> extra = new LinkedHashMap<>();
		try {
			checkServiceInternal(holder, configuration.endpoint, configuration.token, extra);
		} catch (UnsupportedServiceException e) {
			// Check for HTTP exception
			new HttpRequest(createUri(configuration.endpoint), holder).setHeadMethod()
					.setSuccessOnly(false).perform().cleanupAndDisconnect();
			throw e;
		}
		return extra;
	}

	public boolean checkActive(HttpHolder holder) throws HttpException {
		Configuration configuration = getConfiguration();
		if (configuration == null) {
			return false;
		}
		try {
			checkServiceInternal(holder, configuration.endpoint, configuration.token, null);
			return true;
		} catch (HttpException e) {
			if (!e.isHttpException() && !e.isSocketException()) {
				throw e;
			} else {
				return false;
			}
		} catch (UnsupportedServiceException | InvalidTokenException e) {
			return false;
		}
	}

	private Service checkServiceInternal(HttpHolder holder, String endpoint, String token,
			Map<String, String> outExtra) throws HttpException, UnsupportedServiceException, InvalidTokenException {
		Service service = checkService(holder, endpoint);
		if (service == null) {
			throw new UnsupportedServiceException();
		}
		if (outExtra != null) {
			outExtra.put("protocol", service.name());
		}
		if (!checkServiceAuthorization(holder, service, endpoint, token, outExtra)) {
			throw new InvalidTokenException();
		}
		return service;
	}

	private Pair<String, Service> lastService;

	private Service checkService(HttpHolder holder, String endpoint) throws HttpException {
		synchronized (this) {
			if (lastService != null && endpoint.equals(lastService.first)) {
				return lastService.second;
			}
		}
		Uri endpointUri = createUri(endpoint);
		for (Service service : SERVICES) {
			boolean success = false;
			try {
				success = service.checkService(holder, endpointUri);
			} catch (HttpException e) {
				if (!e.isHttpException() && !e.isSocketException()) {
					throw e;
				}
			}
			if (success) {
				synchronized (this) {
					lastService = new Pair<>(endpoint, service);
				}
				return service;
			}
		}
		return null;
	}

	private Pair<String, String> lastAuthorization;

	private boolean checkServiceAuthorization(HttpHolder holder, Service service,
			String endpoint, String token, Map<String, String> outExtra) throws HttpException {
		Uri endpointUri = createUri(endpoint);
		Pair<String, String> authorization = new Pair<>(endpoint, token);
		if (outExtra == null) {
			synchronized (this) {
				if (authorization.equals(lastAuthorization)) {
					return true;
				}
			}
		}
		if (service.checkAuth(holder, endpointUri, token, outExtra)) {
			synchronized (this) {
				lastAuthorization = authorization;
			}
			return true;
		}
		return false;
	}

	private static void waitOrThrow(long start, int timeout, int ms) throws HttpException, TimeoutException {
		if (timeout > 0) {
			long now = SystemClock.elapsedRealtime();
			long cancel = start + timeout * 1000L;
			if (now >= cancel) {
				if (Thread.currentThread().isInterrupted()) {
					throw new HttpException(ErrorItem.Type.UNKNOWN, false, false);
				} else {
					throw new TimeoutException("Timeout after " + (now - start) + " ms");
				}
			}
			ms = (int) Math.max(Math.min(ms, cancel - now), ms / 2);
		}
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			throw new HttpException(ErrorItem.Type.UNKNOWN, false, false, e);
		}
	}

	private static HttpException createInvalidResponse(Exception cause) {
		return new HttpException(ErrorItem.Type.INVALID_RESPONSE, true, false, cause);
	}

	private static HttpException createInvalidResponse(String message) {
		return createInvalidResponse(new Exception(message));
	}

	public enum CaptchaType {RECAPTCHA_2, RECAPTCHA_2_INVISIBLE, HCAPTCHA}

	public String solveCaptcha(HttpHolder holder, CaptchaType captchaType,
			String apiKey, String referer) throws HttpException {
		try {
			Configuration configuration = getConfiguration();
			if (configuration == null) {
				return null;
			}
			Service service;
			try {
				service = checkServiceInternal(holder, configuration.endpoint, configuration.token, null);
			} catch (UnsupportedServiceException | InvalidTokenException e) {
				return null;
			}
			Uri endpointUri = createUri(configuration.endpoint);
			while (true) {
				try {
					return service.solveCaptcha(holder, endpointUri, configuration.token, configuration.timeout,
							captchaType, apiKey, referer);
				} catch (TimeoutException e) {
					e.printStackTrace();
				}
			}
		} catch (HttpException e) {
			if (e.isHttpException() || e.isSocketException()) {
				e.printStackTrace();
				return null;
			} else {
				throw e;
			}
		}
	}

	private interface Service {
		String name();
		boolean checkService(HttpHolder holder, Uri endpointUri) throws HttpException;
		boolean checkAuth(HttpHolder holder, Uri endpointUri, String token,
				Map<String, String> outExtra) throws HttpException;
		String solveCaptcha(HttpHolder holder, Uri endpointUri, String token, int timeout,
				CaptchaType captchaType, String apiKey, String referer) throws HttpException, TimeoutException;
	}

	private static final List<Service> SERVICES = Arrays.asList(new AntigateLegacyService(),
			new AntigateModernService());

	private static class AntigateLegacyService implements Service {
		@Override
		public String name() {
			return "Antigate Legacy";
		}

		@Override
		public boolean checkService(HttpHolder holder, Uri endpointUri) throws HttpException {
			Uri uri = endpointUri.buildUpon().appendPath("res.php")
					.appendQueryParameter("key", "")
					.appendQueryParameter("action", "getbalance")
					.build();
			String response = new HttpRequest(uri, holder).setSuccessOnly(false).perform().readString();
			return response != null && (response.startsWith("OK|") ||
					response.startsWith("ERROR_") && response.contains("KEY"));
		}

		@Override
		public boolean checkAuth(HttpHolder holder, Uri endpointUri, String token,
				Map<String, String> outExtra) throws HttpException {
			Uri uri = endpointUri.buildUpon().appendPath("res.php")
					.appendQueryParameter("key", token)
					.appendQueryParameter("action", "getbalance")
					.build();
			String response = new HttpRequest(uri, holder).setSuccessOnly(true).perform().readString();
			if (response != null && response.startsWith("OK|")) {
				if (outExtra != null) {
					outExtra.put("balance", response.substring(3));
				}
				return true;
			} else if (response != null && response.startsWith("ERROR_") && response.contains("KEY")) {
				return false;
			} else {
				try {
					Float.parseFloat(StringUtils.emptyIfNull(response));
					if (outExtra != null) {
						outExtra.put("balance", response);
					}
					return true;
				} catch (NumberFormatException e) {
					throw createInvalidResponse(response);
				}
			}
		}

		@Override
		public String solveCaptcha(HttpHolder holder, Uri endpointUri, String token, int timeout,
				CaptchaType captchaType, String apiKey, String referer) throws HttpException, TimeoutException {
			Uri.Builder builder = endpointUri.buildUpon().appendPath("in.php");
			builder.appendQueryParameter("key", token);
			switch (captchaType) {
				case RECAPTCHA_2: {
					builder.appendQueryParameter("method", "userrecaptcha");
					builder.appendQueryParameter("googlekey", apiKey);
					builder.appendQueryParameter("invisible", "0");
					break;
				}
				case RECAPTCHA_2_INVISIBLE: {
					builder.appendQueryParameter("method", "userrecaptcha");
					builder.appendQueryParameter("googlekey", apiKey);
					builder.appendQueryParameter("invisible", "1");
					break;
				}
				case HCAPTCHA: {
					builder.appendQueryParameter("method", "hcaptcha");
					builder.appendQueryParameter("sitekey", apiKey);
					break;
				}
			}
			builder.appendQueryParameter("pageurl", referer);
			String response = new HttpRequest(builder.build(), holder).perform().readString();
			if (response != null && response.startsWith("OK|")) {
				response = response.substring(3);
			} else {
				throw createInvalidResponse(response);
			}
			Uri uri = endpointUri.buildUpon().appendPath("res.php")
					.appendQueryParameter("key", token)
					.appendQueryParameter("action", "get")
					.appendQueryParameter("id", response)
					.build();
			long start = SystemClock.elapsedRealtime();
			int wait = 0;
			while (true) {
				if (wait < 5) {
					wait++;
				}
				waitOrThrow(start, timeout, wait * 1000);
				response = new HttpRequest(uri, holder).perform().readString();
				if (response != null && response.startsWith("OK|")) {
					return response.substring(3);
				} else if (!"CAPCHA_NOT_READY".equals(response)) {
					throw createInvalidResponse(response);
				}
			}
		}
	}

	private static class AntigateModernService implements Service {
		@Override
		public String name() {
			return "Antigate Modern";
		}

		private JSONObject run(HttpHolder holder, Uri endpointUri, String method, String token,
				JSONObject task, Long taskId) throws HttpException, InvalidResponseException {
			JSONObject request = new JSONObject();
			try {
				request.put("clientKey", token);
				if (task != null) {
					request.put("task", task);
				}
				if (taskId != null) {
					request.put("taskId", taskId.longValue());
				}
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
			SimpleEntity entity = new SimpleEntity();
			entity.setContentType("application/json");
			entity.setData(request.toString());
			String responseText = new HttpRequest(endpointUri.buildUpon().appendPath(method).build(), holder)
					.setPostMethod(entity).setSuccessOnly(false).perform().readString();
			try {
				return new JSONObject(responseText);
			} catch (JSONException e) {
				throw new InvalidResponseException();
			}
		}

		@Override
		public boolean checkService(HttpHolder holder, Uri endpointUri) throws HttpException {
			JSONObject response;
			try {
				response = run(holder, endpointUri, "getBalance", "", null, null);
			} catch (InvalidResponseException e) {
				return false;
			}
			if (!response.has("errorId")) {
				return false;
			}
			if (response.has("balance")) {
				return true;
			} else {
				String errorCode = response.optString("errorCode");
				return errorCode.startsWith("ERROR_") && errorCode.contains("KEY");
			}
		}

		@Override
		public boolean checkAuth(HttpHolder holder, Uri endpointUri, String token,
				Map<String, String> outExtra) throws HttpException {
			JSONObject response;
			try {
				response = run(holder, endpointUri, "getBalance", token, null, null);
			} catch (InvalidResponseException e) {
				throw createInvalidResponse(e);
			}
			String balance = response.optString("balance");
			if (!StringUtils.isEmpty(balance)) {
				if (outExtra != null) {
					outExtra.put("balance", balance);
				}
				return true;
			} else {
				String errorCode = response.optString("errorCode");
				if (errorCode.startsWith("ERROR_") && errorCode.contains("KEY")) {
					return false;
				} else {
					throw createInvalidResponse(response.toString());
				}
			}
		}

		@Override
		public String solveCaptcha(HttpHolder holder, Uri endpointUri, String token, int timeout,
				CaptchaType captchaType, String apiKey, String referer) throws HttpException, TimeoutException {
			JSONObject task = new JSONObject();
			try {
				switch (captchaType) {
					case RECAPTCHA_2: {
						task.put("type", "NoCaptchaTaskProxyless");
						task.put("isInvisible", false);
						break;
					}
					case RECAPTCHA_2_INVISIBLE: {
						task.put("type", "NoCaptchaTaskProxyless");
						task.put("isInvisible", true);
						break;
					}
					case HCAPTCHA: {
						task.put("type", "HCaptchaTaskProxyless");
						break;
					}
				}
				task.put("websiteURL", referer);
				task.put("websiteKey", apiKey);
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
			JSONObject response;
			try {
				response = run(holder, endpointUri, "createTask", token, task, null);
			} catch (InvalidResponseException e) {
				throw createInvalidResponse(e);
			}
			long taskId;
			try {
				taskId = response.getLong("taskId");
			} catch (JSONException e) {
				throw createInvalidResponse(response.toString());
			}
			long start = SystemClock.elapsedRealtime();
			int wait = 0;
			while (true) {
				if (wait < 5) {
					wait++;
				}
				waitOrThrow(start, timeout, wait * 1000);
				try {
					response = run(holder, endpointUri, "getTaskResult", token, null, taskId);
				} catch (InvalidResponseException e) {
					throw createInvalidResponse(e);
				}
				String status = response.optString("status");
				if ("ready".equals(status)) {
					try {
						return response.getJSONObject("solution").getString("gRecaptchaResponse");
					} catch (JSONException e) {
						throw createInvalidResponse(response.toString());
					}
				} else if (!"processing".equals(status)) {
					throw createInvalidResponse(response.toString());
				}
			}
		}
	}
}
