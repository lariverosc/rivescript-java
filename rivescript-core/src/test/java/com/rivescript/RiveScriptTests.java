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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
				"+ * or something{weight=10}",
				"- Or something. <@>",
				"+ can you run a google search for *",
				"- Sure!",
				"+ hello *{weight=20}",
				"- Hi there!",
				"// Test that spaces before or after the {weight} tag are gobbled up along",
				"// with the {weight} tag itself.",
				"+ something{weight=100}",
				"- Weighted something",
				"+ something",
				"- Unweighted something",
				"+ nothing {weight=100}",
				"- Weighted nothing",
				"+ nothing",
				"- Unweighted nothing",
				"+ {weight=100}everything",
				"- Weighted everything",
				"+ everything",
				"- Unweighted everything",
				"+ {weight=100}   blank",
				"- Weighted blank",
				"+ blank",
				"- Unweighted blank"
		});
		rs.sortReplies();
		System.out.println("\n\n");
		rs.dumpSorted();
		System.out.println("\n\n");

		getReply(rs, "nothing");  // --> "Weighted nothing"
	}

	private void getReply(RiveScript rs, String message) {
		System.out.println(message + " --> " + rs.reply("human", message));
	}
}
