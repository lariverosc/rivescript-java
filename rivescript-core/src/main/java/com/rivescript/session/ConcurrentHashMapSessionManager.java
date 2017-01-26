/*
 * Copyright (c) 2016 the original author or authors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.rivescript.session;

import java.util.Map;

/**
 * TODO
 *
 * @author Noah Petherbridge
 * @author Marcel Overdijk
 */
public class ConcurrentHashMapSessionManager implements SessionManager {

	@Override
	public UserData init(String username) {
		// TODO
		return null;
	}

	@Override
	public void set(String username, Map<String, String> vars) {
		// TODO
	}

	@Override
	public void addHistory(String username, String input, String reply) {
		// TODO
	}

	@Override
	public void setLastMatch(String username, String trigger) {
		// TODO
	}

	@Override
	public String get(String username, String name) {
		// TODO
		return null;
	}

	@Override
	public UserData getAny(String username) {
		// TODO
		return null;
	}

	@Override
	public Map<String, UserData> getAll() {
		// TODO
		return null;
	}

	@Override
	public String getLastMatch(String username) {
		// TODO
		return null;
	}

	@Override
	public History getHistory(String username) {
		// TODO
		return null;
	}

	@Override
	public void clear(String username) {
		// TODO
	}

	@Override
	public void clearAll() {
		// TODO
	}

	@Override
	public void freeze(String username) {
		// TODO
	}

	@Override
	public void thaw(String username, ThawAction action) {
		// TODO
	}
}
