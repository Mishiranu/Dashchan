package chan.http;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import chan.annotation.Public;
import chan.util.StringUtils;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.util.IOUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Public
public final class HttpResponse {
	final HttpSession session;
	private final HttpValidator validator;
	private String charsetName;

	InputStream input;
	private byte[] bytes;
	private String string;

	HttpResponse(HttpSession session, HttpValidator validator, String charsetName) {
		this.session = session;
		this.validator = validator;
		this.charsetName = charsetName;
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
	}

	@Public
	public String getEncoding() {
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
			session.checkThread();
			return session.requestedUri;
		} else {
			return null;
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

	private InputStream prepareOrGetInput() throws HttpException {
		if (input == null && session != null) {
			session.checkThread();
			if (session.connection != null) {
				// Will set input (so this path will be unavailable anymore) or throw exception
				input = session.client.open(this);
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

	// TODO CHAN
	// Remove this method after updating
	// erlach
	// Added: 18.10.20 18:54
	@Public
	@Deprecated
	public byte[] getBytes() throws HttpException {
		return readBytes();
	}

	private void obtainString() throws HttpException {
		if (string == null && bytes != null) {
			try {
				string = new String(bytes, getEncoding());
			} catch (UnsupportedEncodingException e) {
				throw new HttpException(ErrorItem.Type.DOWNLOAD, false, false, e);
			}
		}
	}

	@Public
	public String readString() throws HttpException {
		readBytes();
		obtainString();
		return string;
	}

	// TODO CHAN
	// Remove this method after updating
	// allchan alphachan alterchan anonfm archiverbt arhivach brchan bunbunmaru candydollchan chaosach chiochan
	// chuckdfwk cirno dangeru desustorage diochan dobrochan dvach endchan exach fiftyfive fourchan fourplebs haibane
	// kropyvach kurisach lainchan lolifox nowere nulltirech onechanca owlchan ponyach ponychan princessluna
	// randomarchive sevenchan shanachan sharechan synch taima tiretirech tumbach twentyseven uboachan valkyria
	// wizardchan yakujimoe
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
	// allchan alphachan alterchan anonfm arhivach bunbunmaru chaosach chiochan chuckdfwk cirno dobrochan dvach endchan
	// fourplebs haibane kurisach nowere nulldvachin onechanca tiretirech tumbach yakujimoe
	// Added: 18.10.20 18:54
	@Public
	public Bitmap getBitmap() throws HttpException {
		return readBitmap();
	}

	// TODO CHAN
	// Remove this method after updating
	// allchan anonfm arhivach brchan dangeru dobrochan dvach endchan fiftyfive fourchan horochan kropyvach kurisach
	// lainchan lolifox nulldvachin nulltirech onechanca princessluna synch taima tiretirech tumbach twentyseven
	// uboachan wizardchan
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
	// anonfm brchan dangeru dvach endchan fiftyfive fourchan kropyvach lainchan lolifox nulltirech onechanca
	// princessluna synch taima twentyseven uboachan wizardchan
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
}
