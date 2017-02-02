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

package com.rivescript;

import com.rivescript.config.Config;
import org.junit.Test;

/**
 * Tests for {@link RiveScript}.
 *
 * @author Marcel Overdijk
 */
public class RiveScriptTests {

	@Test
	public void testLoadFileTestSuite() throws Exception {
		String path = getClass().getClassLoader().getResource("testsuite.rive").getFile();
		RiveScript rs = new RiveScript(Config.basic());
		rs.loadFile(path);
		rs.sortReplies();

//		System.out.println("**** START DUMP TOPICS ****");
//		rs.dumpTopics();
//		System.out.println("**** DONE DUMP TOPICS ****");

		System.out.println("**** START DUMP SORTED ****");
		rs.dumpSorted();
		System.out.println("**** START DUMP SORTED ****");

		System.out.println(rs.reply("human", "hello bot"));
	}

	@Test
	public void testLoadDirectory() throws Exception {
		String path = getClass().getClassLoader().getResource("fixtures/begin").getFile();
		RiveScript rs = new RiveScript(Config.basic());
		rs.loadDirectory(path);
	}

	@Test
	public void testStreamCustom() {
		RiveScript rs = new RiveScript(Config.basic());
		rs.stream(new String[] {
				"! version = 2.00",
				"! global debug = 1",
				"! var name      = RiveScript Bot",
				"! var age       = 0",
				"! var gender    = androgynous",
				"! var location  = Cyberspace",
				"! var generator = RiveScript",
				"! array be      = is are was were",
				"! array whatis  = what is|what are|what was|what were,",
				"! array blues   = azure blue aqua cyan baby\\sblue sky\\sblue",
				"! array colors  = red green blue cyan magenta yellow",
				"^ light red|light green|light blue|light cyan|light magenta|light yellow",
				"^ dark red|dark green|dark blue|dark cyan|dark magenta|dark yellow",
				"^ white orange teal brown pink",
				"^ dark white|dark orange|dark teal|dark brown|dark pink",
				"! sub what's  = what is",
				"! sub what're = what are",
				"! sub what'd  = what did",
				"! sub a/s/l   = age sex location",
				"! sub brb     = be right back",
				"! sub afk     = away from keyboard",
				"! sub l o l   = lol",
				"! person you are = I am",
				"! person i am    = you are",
				"! person you     = I",
				"! person i       = you",
				"t",
				"t test",
				"+ hi bot",
				"- hi human",
				"- hi there",
				"+ how are you doing?",
				"* <get mood> eq great => I'm great",
				"* <get mood> eq bad   => I'm not feeling so well",
				"- I'm fine",
		});
	}
}
