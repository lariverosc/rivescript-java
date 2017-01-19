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

package com.rivescript.parser;

import com.rivescript.ast.Root;

import java.util.List;

/**
 * Parser for RiveScript source code.
 *
 * @author Noah Petherbridge
 * @author Marcel Overdijk
 * @since 0.8
 */
public class Parser {

	public static final double RS_VERSION = 2.0;

	private ParserConfig config;

	/**
	 * Creates a new {@link Parser} with a default {@link ParserConfig}.
	 */
	public Parser() {
		this(null);
	}

	/**
	 * Creates a new {@link Parser} with the given {@link ParserConfig}.
	 */
	public Parser(ParserConfig config) {
		this.config = config != null ? config : new ParserConfig();
	}

	/**
	 * Parses the RiveScript source code.
	 * <p>
	 * This will return an AST {@link Root} object containing all of the relevant information parsed from the source code.
	 * <p>
	 * In case of errors (e.g. a syntax error while strict mode is enabled) a {@link ParserException} will be thrown.
	 *
	 * @param filename the arbitrary name for the source code being parsed
	 * @param code the list of lines of RiveScript source code
	 * @return the AST root object
	 * @throws ParserException if the parsing fails
	 */
	public Root parse(String filename, List<String> code) throws ParserException {

		// TODO

		return null;
	}
}
