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
	private final byte[] mBytes;
	private String mData;

	private String mCharsetName;

	@Public
	public HttpResponse(byte[] bytes) {
		mBytes = bytes;
		mCharsetName = "UTF-8";
	}

	@Public
	public void setEncoding(String charsetName) {
		mData = null;
		mCharsetName = charsetName;
	}

	@Public
	public byte[] getBytes() {
		return mBytes;
	}

	private void obtainString() {
		if (mData == null && mBytes != null) {
			try {
				mData = new String(mBytes, mCharsetName);
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Public
	public String getString() {
		obtainString();
		return mData;
	}

	@Public
	public Bitmap getBitmap() {
		return BitmapFactory.decodeByteArray(mBytes, 0, mBytes.length);
	}

	@Public
	public JSONObject getJsonObject() {
		obtainString();
		if (mData != null) {
			try {
				return new JSONObject(mData);
			} catch (JSONException e) {
				// Ignore
			}
		}
		return null;
	}

	@Public
	public JSONArray getJsonArray() {
		obtainString();
		if (mData != null) {
			try {
				return new JSONArray(mData);
			} catch (JSONException e) {
				// Ignore
			}
		}
		return null;
	}
}