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
 * Temporary categorization of triggers while sorting.
 *
 * @author Noah Petherbridge
 * @author Marcel Overdijk
 */
public class SortTrack {

	private Map<Integer, List<SortedTriggerEntry>> atomic = new HashMap<>(); // Sort by number of whole words
	private Map<Integer, List<SortedTriggerEntry>> option = new HashMap<>(); // Sort optionals by number of words
	private Map<Integer, List<SortedTriggerEntry>> alpha = new HashMap<>();  // Sort alpha wildcards by no. of words
	private Map<Integer, List<SortedTriggerEntry>> number = new HashMap<>(); // Sort numeric wildcards by no. of words
	private Map<Integer, List<SortedTriggerEntry>> wild = new HashMap<>();   // Sort wildcards by no. of words
	private List<SortedTriggerEntry> pound = new ArrayList<>();              // Triggers of just '#'
	private List<SortedTriggerEntry> under = new ArrayList<>();              // Triggers of just '_'
	private List<SortedTriggerEntry> star = new ArrayList<>();               // Triggers of just '*'

	public Map<Integer, List<SortedTriggerEntry>> getAtomic() {
		return atomic;
	}

	public void setAtomic(Map<Integer, List<SortedTriggerEntry>> atomic) {
		this.atomic = atomic;
	}

	public Map<Integer, List<SortedTriggerEntry>> getOption() {
		return option;
	}

	public void setOption(Map<Integer, List<SortedTriggerEntry>> option) {
		this.option = option;
	}

	public Map<Integer, List<SortedTriggerEntry>> getAlpha() {
		return alpha;
	}

	public void setAlpha(Map<Integer, List<SortedTriggerEntry>> alpha) {
		this.alpha = alpha;
	}

	public Map<Integer, List<SortedTriggerEntry>> getNumber() {
		return number;
	}

	public void setNumber(Map<Integer, List<SortedTriggerEntry>> number) {
		this.number = number;
	}

	public Map<Integer, List<SortedTriggerEntry>> getWild() {
		return wild;
	}

	public void setWild(Map<Integer, List<SortedTriggerEntry>> wild) {
		this.wild = wild;
	}

	public List<SortedTriggerEntry> getPound() {
		return pound;
	}

	public void setPound(List<SortedTriggerEntry> pound) {
		this.pound = pound;
	}

	public List<SortedTriggerEntry> getUnder() {
		return under;
	}

	public void setUnder(List<SortedTriggerEntry> under) {
		this.under = under;
	}

	public List<SortedTriggerEntry> getStar() {
		return star;
	}

	public void setStar(List<SortedTriggerEntry> star) {
		this.star = star;
	}
}
