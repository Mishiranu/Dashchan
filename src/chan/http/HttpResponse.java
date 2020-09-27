/*
 * Copyright 2014-2016 Fukurou Mishiranu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chan.http;

import java.io.UnsupportedEncodingException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import chan.annotation.Public;

@Public
public class HttpResponse {
	private final byte[] bytes;
	private String data;

	private String charsetName;

	@Public
	public HttpResponse(byte[] bytes) {
		this.bytes = bytes;
		this.charsetName = "UTF-8";
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