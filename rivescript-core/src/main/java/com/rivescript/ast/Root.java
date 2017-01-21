/*
 * Copyright (c) 2016-2017 the original author or authors.
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

package com.rivescript.ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TODO
 *
 * @author Marcel Overdijk
 * @since 0.8
 */
public class Root {

	private Begin begin = new Begin();
	private Map<String, Topic> topics = new HashMap<>();
	private List<ObjectMacro> objects = new ArrayList<>();

	public Root() {
		addTopic("random");
	}

	public Begin getBegin() {
		return begin;
	}

	public void setBegin(Begin begin) {
		this.begin = begin;
	}

	public Topic getTopic(String name) {
		return topics.get(name);
	}

	public Map<String, Topic> getTopics() {
		return topics;
	}

	public void setTopics(Map<String, Topic> topics) {
		this.topics = topics;
	}

	public List<ObjectMacro> getObjects() {
		return objects;
	}

	public void setObjects(List<ObjectMacro> objects) {
		this.objects = objects;
	}

	public void addTopic(String name) {
		topics.put(name, new Topic());
	}

	public void addObject(ObjectMacro object) {
		objects.add(object);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Root that = (Root) o;
		if (begin != null ? !begin.equals(that.begin) : that.begin != null) {
			return false;
		}
		if (topics != null ? !topics.equals(that.topics) : that.topics != null) {
			return false;
		}
		return objects != null ? objects.equals(that.objects) : that.objects == null;
	}

	@Override
	public int hashCode() {
		int result = begin != null ? begin.hashCode() : 0;
		result = 31 * result + (topics != null ? topics.hashCode() : 0);
		result = 31 * result + (objects != null ? objects.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return "Root{" +
				"begin=" + begin +
				", topics=" + topics +
				", objects=" + objects +
				'}';
	}
}
