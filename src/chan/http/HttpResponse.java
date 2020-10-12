package chan.http;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import chan.annotation.Public;
import java.io.UnsupportedEncodingException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Public
public class HttpResponse {
	private final byte[] bytes;
	private String data;

	private String charsetName;

	@Public
	public HttpResponse(byte[] bytes) {
		this.bytes = bytes;
	}

	@Public
	public void setEncoding(String charsetName) {
		this.data = null;
		this.charsetName = charsetName;
	}

	@Public
	public byte[] getBytes() {
		return bytes;
	}

	private void obtainString() {
		if (data == null && bytes != null) {
			try {
				String charsetName = this.charsetName;
				if (charsetName == null) {
					charsetName = "UTF-8";
				}
				data = new String(bytes, charsetName);
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Public
	public String getString() {
		obtainString();
		return data;
	}

	@Public
	public Bitmap getBitmap() {
		return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
	}

	@Public
	public JSONObject getJsonObject() {
		obtainString();
		if (data != null) {
			try {
				return new JSONObject(data);
			} catch (JSONException e) {
				// Invalid data, ignore exception
			}
		}
		return null;
	}

	@Public
	public JSONArray getJsonArray() {
		obtainString();
		if (data != null) {
			try {
				return new JSONArray(data);
			} catch (JSONException e) {
				// Invalid data, ignore exception
			}
		}
		return null;
	}
}
