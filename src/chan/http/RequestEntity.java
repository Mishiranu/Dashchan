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

import chan.annotation.Extendable;

@Extendable
public interface RequestEntity extends Cloneable {
	@Extendable
	public abstract void add(String name, String value);

	@Extendable
	public abstract String getContentType();

	@Extendable
	public abstract long getContentLength();

	@Extendable
	public abstract void write(OutputStream output) throws IOException;

	@Extendable
	public abstract RequestEntity copy();
}