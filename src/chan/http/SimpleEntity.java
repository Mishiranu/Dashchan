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

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import chan.annotation.Extendable;
import chan.annotation.Public;
import chan.util.StringUtils;

@Extendable
public class SimpleEntity implements RequestEntity {
	private byte[] data;
	private String contentType = "text/plain";

	@Public
	public SimpleEntity() {}

	@Override
	public void add(String name, String value) {
		throw new UnsupportedOperationException();
	}

	@Extendable
	public void setData(String data) {
		setData(data, "UTF-8");
	}

	@Extendable
	public void setData(String data, String charsetName) {
		try {
			setData(data != null ? data.getBytes(charsetName) : null);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	@Extendable
	public void setData(byte[] data) {
		this.data = data;
	}

	@Extendable
	public void setContentType(String contentType) {
		if (StringUtils.isEmpty(contentType)) {
			throw new IllegalArgumentException("Invalid content type");
		}
		this.contentType = contentType;
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public long getContentLength() {
		return data != null ? data.length : 0;
	}

	@Override
	public void write(OutputStream output) throws IOException {
		if (data != null) {
			output.write(data);
		}
	}

	@Override
	public RequestEntity copy() {
		SimpleEntity entity = new SimpleEntity();
		entity.setData(data);
		entity.setContentType(contentType);
		return entity;
	}
}