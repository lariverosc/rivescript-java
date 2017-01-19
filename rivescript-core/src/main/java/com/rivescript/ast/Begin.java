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

import java.util.List;
import java.util.Map;

/**
 * TODO
 *
 * @author Marcel Overdijk
 * @since 0.8
 */
public class Begin {

	private Map<String, String> global;
	private Map<String, String> var;
	private Map<String, String> sub;
	private Map<String, String> person;
	private Map<String, List<String>> array;

	public Map<String, String> getGlobal() {
		return global;
	}

	public void setGlobal(Map<String, String> global) {
		this.global = global;
	}

	public Map<String, String> getVar() {
		return var;
	}

	public void setVar(Map<String, String> var) {
		this.var = var;
	}

	public Map<String, String> getSub() {
		return sub;
	}

	public void setSub(Map<String, String> sub) {
		this.sub = sub;
	}

	public Map<String, String> getPerson() {
		return person;
	}

	public void setPerson(Map<String, String> person) {
		this.person = person;
	}

	public Map<String, List<String>> getArray() {
		return array;
	}

	public void setArray(Map<String, List<String>> array) {
		this.array = array;
	}
}
