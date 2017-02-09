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

package com.rivescript.sorting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sort buffer data.
 *
 * @author Noah Petherbridge
 * @author Marcel Overdijk
 */
public class SortBuffer {

	private Map<String, List<SortedTriggerEntry>> topics = new HashMap<>();
	private Map<String, List<SortedTriggerEntry>> thats = new HashMap<>();
	private List<String> sub = new ArrayList<>();
	private List<String> person = new ArrayList<>();

	public Map<String, List<SortedTriggerEntry>> getTopics() {
		return topics;
	}

	public void setTopics(Map<String, List<SortedTriggerEntry>> topics) {
		this.topics = topics;
	}

	public Map<String, List<SortedTriggerEntry>> getThats() {
		return thats;
	}

	public void setThats(Map<String, List<SortedTriggerEntry>> thats) {
		this.thats = thats;
	}

	public List<String> getSub() {
		return sub;
	}

	public void setSub(List<String> sub) {
		this.sub = sub;
	}

	public List<String> getPerson() {
		return person;
	}

	public void setPerson(List<String> person) {
		this.person = person;
	}
}
