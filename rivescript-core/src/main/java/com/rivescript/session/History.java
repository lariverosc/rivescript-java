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

import java.util.ArrayList;
import java.util.List;

/**
 * {@code History} keeps track of recent input and reply history.
 *
 * @author Noah Petherbridge
 * @author Marcel Overdijk
 */
public class History {

	/**
	 * The number of entries stored in the history.
	 */
	public static final int HISTORY_SIZE = 9;

	private List<String> input;
	private List<String> reply;

	public History() {
		this.input = new ArrayList<>();
		this.reply = new ArrayList<>();
		for (int i = 0; i < HISTORY_SIZE; i++) {
			addInput("undefined");
			addReply("undefined");
		}
	}

	public List<String> getInput() {
		return input;
	}

	public String getInput(int index) {
		return input.get(index);
	}

	public void addInput(String input) {
		this.input.add(0, input);
	}

	public List<String> getReply() {
		return reply;
	}

	public String getReply(int index) {
		return reply.get(index);
	}

	public void addReply(String reply) {
		this.reply.add(0, reply);
	}
}
