package chan.http;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Pair;
import chan.annotation.Public;
import chan.text.GroupParser;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.util.IOUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Public
public final class HttpResponse {
	private enum ExtractCharset {NONE, CHECK_HTML, FROM_HTML}

	final HttpSession session;
	private final HttpValidator validator;
	private String charsetName;
	private ExtractCharset extractCharset = ExtractCharset.NONE;

	private InputStream input;
	private byte[] bytes;
	private String string;

	HttpResponse(HttpSession session, HttpValidator validator, String charsetName) {
		this.session = session;
		this.validator = validator;
		this.charsetName = charsetName;
		List<String> contentTypes = getHeaderFields().get("Content-Type");
		if (contentTypes != null && contentTypes.size() == 1) {
			String contentType = contentTypes.get(0);
			if ("text/html".equals(contentType)) {
				extractCharset = ExtractCharset.FROM_HTML;
			}
		} else if (session != null) {
			Uri uri = session.getCurrentRequestedUri();
			String path = StringUtils.emptyIfNull(uri.getPath()).toLowerCase(Locale.US);
			if (path.endsWith(".html")) {
				extractCharset = ExtractCharset.FROM_HTML;
			} else {
				extractCharset = ExtractCharset.CHECK_HTML;
			}
		}
	}

	@Public
	public HttpResponse(InputStream input) {
		this(null, null, null);
		this.input = input;
	}

	@Public
	public HttpResponse(byte[] bytes) {
		this(null, null, null);
		this.bytes = bytes;
	}

	@Public
	public void setEncoding(String charsetName) {
		this.string = null;
		this.charsetName = charsetName;
		extractCharset = ExtractCharset.NONE;
	}

	@Public
	public String getEncoding() throws HttpException {
		if (extractCharset != ExtractCharset.NONE) {
			prepareOrGetInput();
		}
		return StringUtils.isEmpty(charsetName) ? "ISO-8859-1" : charsetName;
	}

	@Public
	public void checkResponseCode() throws HttpException {
		if (session != null) {
			session.checkResponseCode();
		}
	}

	@Public
	public int getResponseCode() {
		return session != null ? session.getResponseCode() : HttpURLConnection.HTTP_OK;
	}

	@Public
	public Uri getRequestedUri() {
		if (session != null) {
			return session.getRequestedUris().get(0);
		} else {
			return null;
		}
	}

	@Public
	public List<Uri> getRequestedUris() {
		if (session != null) {
			List<Uri> uris = session.getRequestedUris();
			return new ArrayList<>(uris);
		} else {
			return Collections.emptyList();
		}
	}

	@Public
	public Uri getRedirectedUri() {
		if (session != null) {
			session.checkThread();
			return session.redirectedUri;
		}
		return null;
	}

	@Public
	public void setRedirectedUri(Uri redirectedUri) {
		session.checkThread();
		session.redirectedUri = redirectedUri;
	}

	@Public
	public Map<String, List<String>> getHeaderFields() {
		return session != null ? session.getHeaderFields() : Collections.emptyMap();
	}

	@Public
	public String getCookieValue(String name) {
		return session != null ? session.getCookieValue(name) : null;
	}

	public long getLength() {
		return session != null ? session.getLength() : -1;
	}

	@Public
	public HttpValidator getValidator() {
		if (session != null) {
			session.checkThread();
			return validator;
		} else {
			return null;
		}
	}

	private static class ConcatInputStream extends SequenceInputStream {
		public final InputStream tail;

		public ConcatInputStream(ByteArrayInputStream head, InputStream tail) {
			super(head, tail);
			this.tail = tail;
		}
	}

	private static Pair<InputStream, String> extractCharsetFromHtml(InputStream input,
			boolean checkHtml) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		@SuppressWarnings("CharsetObjectCanBeUsed")
		InputStreamReader reader = new InputStreamReader(new InputStream() {
			@Override
			public int read() throws IOException {
				int result = input.read();
				if (result >= 0) {
					output.write(result);
				}
				return result;
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				int result = input.read(b, off, len);
				if (result > 0) {
					output.write(b, off, result);
				}
				return result;
			}
		}, "ISO-8859-1");
		StringBuilder builder = new StringBuilder();
		if (checkHtml) {
			final String minHtmlStart = "<!DOCTYPE html><html><head>";
			char[] buffer = new char[minHtmlStart.length()];
			int count = reader.read(buffer);
			if (count >= 0) {
				builder.append(buffer, 0, count);
				String string = builder.toString().toLowerCase(Locale.US);
				checkHtml = !string.contains("<!doctype html");
			}
		}
		boolean foundHeadClose = false;
		if (!checkHtml) {
			final String headClose = "</head>";
			char[] buffer = new char[1024];
			int count;
			while ((count = reader.read(buffer)) >= 0) {
				for (int i = 0; i < count; i++) {
					buffer[i] = Character.toLowerCase(buffer[i]);
				}
				int checkFrom = Math.max(0, builder.length() - headClose.length());
				builder.append(buffer, 0, count);
				int index = builder.indexOf(headClose, checkFrom);
				if (index >= 0) {
					builder.setLength(index + headClose.length());
					foundHeadClose = true;
					break;
				}
			}
		}
		ByteArrayInputStream headInput = new ByteArrayInputStream(output.toByteArray());
		if (checkHtml) {
			return new Pair<>(new ConcatInputStream(headInput, input), null);
		}
		if (!foundHeadClose) {
			try (InputStream ignored = input) {
				return new Pair<>(headInput, null);
			}
		}
		String charsetName = null;
		int from = 0;
		while (true) {
			int start = builder.indexOf("<meta", from);
			int end = builder.indexOf(">", start + 1);
			if (start < 0 || end <= start) {
				break;
			}
			String attrs = builder.substring(start + 5, end).toLowerCase(Locale.US);
			if ("content-type".equals(GroupParser.extractAttr(attrs, "http-equiv"))) {
				String contentType = GroupParser.extractAttr(attrs, "content");
				charsetName = HttpClient.extractCharsetName(contentType);
				break;
			} else {
				charsetName = GroupParser.extractAttr(attrs, "charset");
				if (charsetName != null) {
					break;
				}
			}
			from = end + 1;
		}
		return new Pair<>(new ConcatInputStream(headInput, input), charsetName);
	}

	private InputStream prepareOrGetInput() throws HttpException {
		if (input == null && session != null) {
			session.checkThread();
			if (session.connection != null) {
				InputStream input = session.client.open(this);
				// Set input to ensure client.open called only once
				this.input = input;
				if (extractCharset != ExtractCharset.NONE) {
					try {
						Pair<InputStream, String> pair = extractCharsetFromHtml(input,
								extractCharset == ExtractCharset.CHECK_HTML);
						this.input = pair.first;
						if (pair.second != null) {
							charsetName = pair.second;
						}
					} catch (IOException e) {
						throw fail(e);
					} finally {
						extractCharset = ExtractCharset.NONE;
					}
				}
			}
		}
		return input;
	}

	@Public
	public InputStream open() throws HttpException {
		InputStream input = prepareOrGetInput();
		if (input != null) {
			return input;
		}
		if (bytes != null) {
			return new ByteArrayInputStream(bytes);
		}
		throw new HttpException(ErrorItem.Type.EMPTY_RESPONSE, false, false);
	}

	@Public
	public HttpException fail(IOException exception) {
		cleanupAndDisconnect();
		if (exception instanceof HttpClient.InterruptedHttpException) {
			return ((HttpClient.InterruptedHttpException) exception).toHttp();
		} else {
			return HttpClient.transformIOException(exception);
		}
	}

	@Public
	public byte[] readBytes() throws HttpException {
		InputStream input = prepareOrGetInput();
		if (input != null) {
			try (InputStream ignored = input) {
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				IOUtils.copyStream(input, output);
				bytes = output.toByteArray();
			} catch (IOException e) {
				throw fail(e);
			} finally {
				this.input = null;
			}
		}
		if (bytes != null) {
			return bytes;
		}
		throw new HttpException(ErrorItem.Type.EMPTY_RESPONSE, false, false);
	}

	@Public
	public String readString() throws HttpException {
		readBytes();
		if (string == null && bytes != null) {
			try {
				string = new String(bytes, getEncoding());
			} catch (UnsupportedEncodingException e) {
				throw new HttpException(ErrorItem.Type.DOWNLOAD, false, false, e);
			}
		}
		return string;
	}

	// TODO CHAN
	// Remove this method after updating
	// allchan alphachan alterchan anonfm archiverbt brchan chaosach chiochan chuckdfwk dangeru desustorage diochan
	// endchan exach fiftyfive fourplebs haibane kropyvach kurisach lainchan lolifox nulltirech onechanca owlchan
	// ponyach ponychan princessluna randomarchive sevenchan shanachan synch taima tiretirech tumbach twentyseven
	// uboachan valkyria wizardchan
	// Added: 18.10.20 18:54
	@Public
	@Deprecated
	public String getString() throws HttpException {
		return readString();
	}

	@Public
	public Bitmap readBitmap() throws HttpException {
		readBytes();
		return bytes != null ? BitmapFactory.decodeByteArray(bytes, 0, bytes.length) : null;
	}

	// TODO CHAN
	// Remove this method after updating
	// allchan alphachan alterchan anonfm chaosach chiochan chuckdfwk endchan fourplebs haibane kurisach nulldvachin
	// onechanca tiretirech tumbach
	// Added: 18.10.20 18:54
	@Public
	public Bitmap getBitmap() throws HttpException {
		return readBitmap();
	}

	// TODO CHAN
	// Remove this method after updating
	// allchan anonfm brchan dangeru endchan fiftyfive horochan kropyvach kurisach lainchan lolifox nulldvachin
	// nulltirech onechanca princessluna synch taima tiretirech tumbach twentyseven uboachan wizardchan
	// Added: 18.10.20 18:54
	@Deprecated
	@Public
	public JSONObject getJsonObject() throws HttpException {
		String string = readString();
		if (string != null) {
			try {
				return new JSONObject(string);
			} catch (JSONException e) {
				// Invalid data, ignore exception
			}
		}
		return null;
	}

	// TODO CHAN
	// Remove this method after updating
	// anonfm brchan dangeru endchan fiftyfive kropyvach lainchan lolifox nulltirech onechanca princessluna synch taima
	// twentyseven uboachan wizardchan
	// Added: 18.10.20 18:54
	@Deprecated
	@Public
	public JSONArray getJsonArray() throws HttpException {
		String string = readString();
		if (string != null) {
			try {
				return new JSONArray(string);
			} catch (JSONException e) {
				// Invalid data, ignore exception
			}
		}
		return null;
	}

	public void cleanupAndDisconnect() {
		if (session != null) {
			session.checkThread();
		}
		IOUtils.close(input);
		input = null;
		if (session != null && session.connection != null) {
			session.disconnectAndClear();
		}
	}

	void cleanupAndDisconnectIfEquals(InputStream input) {
		boolean equals = false;
		InputStream checkInput = this.input;
		while (true) {
			if (checkInput == input) {
				equals = true;
			} else if (checkInput instanceof ConcatInputStream) {
				checkInput = ((ConcatInputStream) checkInput).tail;
				continue;
			}
			break;
		}
		if (equals) {
			this.input = null;
			cleanupAndDisconnect();
		}
	}
}
