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

package com.rivescript.util;

import java.util.regex.Pattern;

import static com.rivescript.regexp.Regexp.RE_NASTIES;

/**
 * Miscellaneous {@link String} utility methods.
 *
 * @author Noah Petherbridge
 * @author Marcel Overdijk
 */
public class StringUtils {

	/**
	 * Joins a {@link String} array into a single {@link String}.
	 *
	 * @param array     the array to join
	 * @param delimiter the delimiter character to use, {@code null} treated as {@code ""}
	 */
	public static String join(String[] array, String delimiter) {
		if (array == null) {
			return null;
		}
		if (delimiter == null) {
			delimiter = "";
		}
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < array.length; i++) {
			buf.append(array[i]);
			if (i < array.length - 1) {
				buf.append(delimiter);
			}
		}
		return buf.toString();
	}

	/**
	 * Strips special characters out of a string.
	 *
	 * @param str TODO
	 * @return
	 */
	public static String stripNasties(String str) {
		return RE_NASTIES.matcher(str).replaceAll("");
	}

	/**
	 * Counts the number of words in a {@link String}.
	 *
	 * @param str the string to count
	 * @param all count all
	 * @return the number of words
	 */
	public static int wordCount(String str, boolean all) {
		if (str == null || str.length() == 0) {
			return 0;
		}
		String[] words;
		if (all) {
			words = str.split("\\s+");
		} else {
			words = str.split("[\\s\\*\\#\\_\\|]+");
		}

		int count = 0;
		for (String word : words) {
			if (word.length() > 0) {
				count++;
			}
		}

		return count;
	}

	/**
	 * TODO
	 *
	 * @param str
	 * @return
	 */
	public static String quoteMeta(String str) {
		String[] unsafe = "\\.+*?[^]$(){}=!<>|:".split("");
		for (String c : unsafe) {
			str = str.replaceAll(Pattern.quote(c), "\\\\\\" + c);
		}
		return str;
	}
}
