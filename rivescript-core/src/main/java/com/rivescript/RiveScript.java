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

package com.rivescript;

import com.rivescript.ast.ObjectMacro;
import com.rivescript.ast.Root;
import com.rivescript.ast.Topic;
import com.rivescript.ast.Trigger;
import com.rivescript.config.Config;
import com.rivescript.lang.Java;
import com.rivescript.macro.ObjectHandler;
import com.rivescript.macro.Subroutine;
import com.rivescript.parser.Parser;
import com.rivescript.parser.ParserConfig;
import com.rivescript.parser.ParserException;
import com.rivescript.session.MapSessionManager;
import com.rivescript.session.SessionManager;
import com.rivescript.session.ThawAction;
import com.rivescript.session.UserData;
import com.rivescript.sorting.SortBuffer;
import com.rivescript.sorting.SortedTriggerEntry;
import com.rivescript.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * A RiveScript interpreter written in Java.
 * <p>
 * SYNOPSIS:
 * <p>
 * <pre>
 * <code>
 * import com.rivescript.RiveScript;
 *
 * // Create a new interpreter<br>
 * RiveScript rs = new RiveScript();
 *
 * // Load a directory full of replies in *.rive files
 * rs.loadDirectory("./replies");
 *
 * // Sort replies
 * rs.sortReplies();
 *
 * // Get a reply for the user
 * String reply = rs.reply("user", "Hello bot!");
 * </code>
 * </pre>
 *
 * @author Noah Petherbridge
 * @author Marcel Overdijk
 */
public class RiveScript {

	private static final String[] DEFAULT_FILE_EXTENSIONS = new String[] {".rive", ".rs"};

	private static Logger logger = LoggerFactory.getLogger(RiveScript.class);

	private boolean strict;
	private boolean utf8;
	private boolean forceCase;
	private int depth;
	private Pattern unicodePunctuation;

	private Parser parser;

	private SessionManager sessionManager;

	private Map<String, String> global;                           // 'global' variables
	private Map<String, String> var;                              // 'var' bot variables
	private Map<String, String> sub;                              // 'sub' substitutions
	private Map<String, String> person;                           // 'person' substitutions
	private Map<String, List<String>> array;                      // 'array' definitions
	private Map<String, Map<String, Boolean>> includes;           // included topics
	private Map<String, Map<String, Boolean>> inherits;           // inherited topics
	private Map<String, String> objectLanguages;                  // object macro languages
	private Map<String, ObjectHandler> handlers;                  // object language handlers
	private Map<String, Subroutine> subroutines;                  // Java object handlers
	private Map<String, Topic> topics;                            // main topic structure
	private Map<String, Map<String, Map<String, Trigger>>> thats; // %Previous mapper
	private SortBuffer sorted;                                    // Sorted data from sortReplies()

	// State information.
	private ThreadLocal<String> currentUser = new ThreadLocal<>();

	/*------------------*/
	/*-- Constructors --*/
	/*------------------*/

	/**
	 * Creates a new {@link RiveScript} interpreter.
	 */
	public RiveScript() {
		this(null);
	}

	/**
	 * Creates a new {@link RiveScript} interpreter with the given {@link Config}.
	 *
	 * @param config the config
	 */
	public RiveScript(Config config) {
		if (config == null) {
			config = Config.basic();
		}

		this.strict = config.isStrict();
		this.utf8 = config.isUtf8();
		this.forceCase = config.isForceCase();
		this.depth = config.getDepth();
		this.sessionManager = config.getSessionManager();

		String unicodePunctuation = config.getUnicodePunctuation();
		if (unicodePunctuation == null) {
			unicodePunctuation = Config.DEFAULT_UNICODE_PUNCTUATION_PATTERN;
		}
		this.unicodePunctuation = Pattern.compile(unicodePunctuation);

		if (this.depth <= 0) {
			this.depth = Config.DEFAULT_DEPTH;
			logger.debug("No depth config: using default {}", Config.DEFAULT_DEPTH);
		}

		if (this.sessionManager == null) {
			this.sessionManager = new MapSessionManager();
			logger.debug("No SessionManager config: using default MapSessionManager");
		}

		// Initialize the parser.
		this.parser = new Parser(ParserConfig.newBuilder()
				.strict(this.strict)
				.utf8(this.utf8)
				.forceCase(this.forceCase)
				.build());

		// Initialize all the data structures.
		this.global = new HashMap<>();
		this.var = new HashMap<>();
		this.sub = new HashMap<>();
		this.person = new HashMap<>();
		this.array = new HashMap<>();
		this.includes = new HashMap<>();
		this.inherits = new HashMap<>();
		this.objectLanguages = new HashMap<>();
		this.handlers = new HashMap<>();
		this.subroutines = new HashMap<>();
		this.topics = new HashMap<>();
		this.thats = new HashMap<>();
		this.sorted = new SortBuffer();

		// Set the default Java macro handler.
		this.setHandler("java", new Java(this));
	}

	/**
	 * Returns the RiveScript library version, or {@code null} if it cannot be determined.
	 *
	 * @return the version
	 * @see Package#getImplementationVersion()
	 */
	public static String getVersion() {
		Package pkg = RiveScript.class.getPackage();
		return (pkg != null ? pkg.getImplementationVersion() : null);
	}

	/*---------------------------*/
	/*-- Configuration Methods --*/
	/*---------------------------*/

	/**
	 * Sets a custom language handler for RiveScript object macros.
	 *
	 * @param name    the name of the programming language
	 * @param handler the implementation
	 */
	public void setHandler(String name, ObjectHandler handler) {
		handlers.put(name, handler);
	}

	/**
	 * Removes an object macro language handler.
	 *
	 * @param name the name of the programming language
	 */
	public void removeHandler(String name) {
		handlers.remove(name);
	}

	/**
	 * Defines a Java object macro.
	 * <p>
	 * Because Java is a compiled language, this method must be used to create an object macro written in Java.
	 *
	 * @param name       the name of the object macro for the `<call>` tag
	 * @param subroutine the subroutine
	 */
	public void setSubroutine(String name, Subroutine subroutine) {
		subroutines.put(name, subroutine);
	}

	/**
	 * Removes a Java object macro.
	 *
	 * @param name the name of the object macro
	 */
	public void removeSubroutine(String name) {
		subroutines.remove(name);
	}

	/**
	 * Sets a global variable.
	 * <p>
	 * This is equivalent to {@code ! global} in RiveScript. Set the value to {@code null} to delete a global.
	 *
	 * @param name  the variable name
	 * @param value the variable value or {@code null}
	 */
	public void setGlobal(String name, String value) {
		if (value == null) {
			global.remove(name);
		} else {
			global.put(name, value);
		}
	}

	/**
	 * Returns a global variable.
	 * <p>
	 * This is equivalent to {@code <env>} in RiveScript. Returns {@code null} if the variable isn't defined.
	 *
	 * @param name the variable name
	 * @return the variable value or {@code null}
	 */
	public String getGlobal(String name) {
		return global.get(name);
	}

	/**
	 * Sets a bot variable.
	 * <p>
	 * This is equivalent to {@code ! var} in RiveScript. Set the value to {@code null} to delete a bot variable.
	 *
	 * @param name  the variable name
	 * @param value the variable value or {@code null}
	 */
	public void setVariable(String name, String value) {
		if (value == null) {
			var.remove(name);
		} else {
			var.put(name, value);
		}
	}

	/**
	 * Returns a bot variable.
	 * <p>
	 * This is equivalent to {@code <bot>} in RiveScript. Returns {@code null} if the variable isn't defined.
	 *
	 * @param name the variable name
	 * @return the variable value or {@code null}
	 */
	public String getVariable(String name) {
		return var.get(name);
	}

	/**
	 * Sets a substitution pattern.
	 * <p>
	 * This is equivalent to {@code ! sub} in RiveScript. Set the value to {@code null} to delete a substitution.
	 *
	 * @param name  the substitution name
	 * @param value the substitution pattern or {@code null}
	 */
	public void setSubstitution(String name, String value) {
		if (value == null) {
			sub.remove(name);
		} else {
			sub.put(name, value);
		}
	}

	/**
	 * Returns a substitution pattern.
	 * <p>
	 * Returns {@code null} if the substitution isn't defined.
	 *
	 * @param name the substitution name
	 * @return the substitution pattern or {@code null}
	 */
	public String getSubstitution(String name) {
		return sub.get(name);
	}

	/**
	 * Sets a person substitution pattern.
	 * <p>
	 * This is equivalent to {@code ! person} in RiveScript. Set the value to {@code null} to delete a person substitution.
	 *
	 * @param name  the person substitution name
	 * @param value the person substitution pattern or {@code null}
	 */
	public void setPerson(String name, String value) {
		if (value == null) {
			person.remove(name);
		} else {
			person.put(name, value);
		}
	}

	/**
	 * Returns a person substitution pattern.
	 * <p>
	 * This is equivalent to {@code <person>} in RiveScript. Returns {@code null} if the person substitution isn't defined.
	 *
	 * @param name the person substitution name
	 * @return the person substitution pattern or {@code null}
	 */
	public String getPerson(String name) {
		return person.get(name);
	}

	/*---------------------*/
	/*-- Loading Methods --*/
	/*---------------------*/

	/**
	 * Loads a single RiveScript document from disk.
	 *
	 * @param file the RiveScript file
	 * @throws RiveScriptException in case of a loading error
	 * @throws ParserException     in case of a parsing error
	 */
	public void loadFile(File file) throws RiveScriptException, ParserException {
		requireNonNull(file, "'file' must not be null");
		logger.debug("Loading RiveScript file: {}", file);

		// Run some sanity checks on the file.
		if (!file.exists()) {
			throw new RiveScriptException("File '" + file + "' not found");
		} else if (!file.isFile()) {
			throw new RiveScriptException("File '" + file + "' is not a regular file");
		} else if (!file.canRead()) {
			throw new RiveScriptException("File '" + file + "' cannot be read");
		}

		List<String> code = new ArrayList<>();

		// Slurp the file's contents.
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = br.readLine()) != null) {
				code.add(line);
			}
		} catch (IOException e) {
			throw new RiveScriptException("Error reading file '" + file + "'", e);
		}

		parse(file.toString(), code.toArray(new String[0]));
	}

	/**
	 * Loads a single RiveScript document from disk.
	 *
	 * @param path the path to the RiveScript document
	 * @throws RiveScriptException in case of a loading error
	 * @throws ParserException     in case of a parsing error
	 */
	public void loadFile(Path path) throws RiveScriptException, ParserException {
		requireNonNull(path, "'path' must not be null");
		loadFile(path.toFile());
	}

	/**
	 * Loads a single RiveScript document from disk.
	 *
	 * @param path the path to the RiveScript document
	 * @throws RiveScriptException in case of a loading error
	 * @throws ParserException     in case of a parsing error
	 */
	public void loadFile(String path) throws RiveScriptException, ParserException {
		requireNonNull(path, "'path' must not be null");
		loadFile(new File(path));
	}

	/**
	 * Loads multiple RiveScript documents from a directory on disk.
	 *
	 * @param directory the directory containing the RiveScript documents
	 * @throws RiveScriptException in case of a loading error
	 * @throws ParserException     in case of a parsing error
	 */
	public void loadDirectory(File directory, String... extensions) throws RiveScriptException, ParserException {
		requireNonNull(directory, "'directory' must not be null");
		logger.debug("Loading RiveScript files from directory: {}", directory);

		if (extensions.length == 0) {
			extensions = DEFAULT_FILE_EXTENSIONS;
		}
		final String[] exts = extensions;

		// Run some sanity checks on the directory.
		if (!directory.exists()) {
			throw new RiveScriptException("Directory '" + directory + "' not found");
		} else if (!directory.isDirectory()) {
			throw new RiveScriptException("Directory '" + directory + "' is not a directory");
		}

		// Search for the files.
		File[] files = directory.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				for (String ext : exts) {
					if (name.endsWith(ext)) {
						return true;
					}
				}
				return false;
			}
		});

		// No results?
		if (files.length == 0) {
			logger.info("No files found in directory: {}", directory);
		}

		// Parse each file.
		for (File file : files) {
			loadFile(file);
		}
	}

	/**
	 * Loads multiple RiveScript documents from a directory on disk.
	 *
	 * @param path The path to the directory containing the RiveScript documents
	 * @throws RiveScriptException in case of a loading error
	 * @throws ParserException     in case of a parsing error
	 */
	public void loadDirectory(Path path, String... extensions) throws RiveScriptException, ParserException {
		requireNonNull(path, "'path' must not be null");
		loadDirectory(path.toFile(), extensions);
	}

	/**
	 * Loads multiple RiveScript documents from a directory on disk.
	 *
	 * @param path The path to the directory containing the RiveScript documents
	 * @throws RiveScriptException in case of a loading error
	 * @throws ParserException     in case of a parsing error
	 */
	public void loadDirectory(String path, String... extensions) throws RiveScriptException, ParserException {
		requireNonNull(path, "'path' must not be null");
		loadDirectory(new File(path), extensions);
	}

	/**
	 * Loads RiveScript source code from a text buffer, with line breaks after each line.
	 *
	 * @param code the RiveScript source code
	 * @throws ParserException in case of a parsing error
	 */
	public void stream(String code) throws ParserException {
		String[] lines = code.split("\n");
		stream(lines);
	}

	/**
	 * Loads RiveScript source code from a {@link String} array, one line per item.
	 *
	 * @param code the lines of RiveScript source code
	 * @throws ParserException in case of a parsing error
	 */
	public void stream(String[] code) throws ParserException {
		parse("stream()", code);
	}

	/*---------------------*/
	/*-- Parsing Methods --*/
	/*---------------------*/

	/**
	 * Parses the RiveScript source code into the bot's memory.
	 *
	 * @param filename the arbitrary name for the source code being parsed
	 * @param code     the lines of RiveScript source code
	 * @throws ParserException in case of a parsing error
	 */
	private void parse(String filename, String[] code) throws ParserException {
		logger.debug("Parsing code...");

		// Get the abstract syntax tree of this file.
		Root ast = parser.parse(filename, code);

		// Get all of the "begin" type variables.
		for (Map.Entry<String, String> entry : ast.getBegin().getGlobal().entrySet()) {
			if (entry.getValue().equals("<undef>")) {
				global.remove(entry.getKey());
			} else {
				global.put(entry.getKey(), entry.getValue());
			}
		}
		for (Map.Entry<String, String> entry : ast.getBegin().getVar().entrySet()) {
			if (entry.getValue().equals("<undef>")) {
				var.remove(entry.getKey());
			} else {
				var.put(entry.getKey(), entry.getValue());
			}
		}
		for (Map.Entry<String, String> entry : ast.getBegin().getSub().entrySet()) {
			if (entry.getValue().equals("<undef>")) {
				sub.remove(entry.getKey());
			} else {
				sub.put(entry.getKey(), entry.getValue());
			}
		}
		for (Map.Entry<String, String> entry : ast.getBegin().getPerson().entrySet()) {
			if (entry.getValue().equals("<undef>")) {
				person.remove(entry.getKey());
			} else {
				person.put(entry.getKey(), entry.getValue());
			}
		}
		for (Map.Entry<String, List<String>> entry : ast.getBegin().getArray().entrySet()) {
			if (entry.getValue().equals("<undef>")) {
				array.remove(entry.getKey());
			} else {
				array.put(entry.getKey(), entry.getValue());
			}
		}

		// Consume all the parsed triggers.
		for (Map.Entry<String, Topic> entry : ast.getTopics().entrySet()) {
			String topic = entry.getKey();
			Topic data = entry.getValue();

			// Keep a map of the topics that are included/inherited under this topic.
			if (!includes.containsKey(topic)) {
				includes.put(topic, new HashMap<String, Boolean>());
			}
			if (!inherits.containsKey(topic)) {
				inherits.put(topic, new HashMap<String, Boolean>());
			}

			// Merge in the topic inclusions/inherits.
			for (String included : data.getIncludes().keySet()) {
				includes.get(topic).put(included, true);
			}
			for (String inherited : data.getInherits().keySet()) {
				inherits.get(topic).put(inherited, true);
			}

			// Initialize the topic structure.
			if (!topics.containsKey(topic)) {
				topics.put(topic, new Topic());
			}

			// Consume the AST triggers into the brain.
			for (Trigger trig : data.getTriggers()) {
				// Convert this AST trigger into an internal trigger.
				Trigger trigger = new Trigger();
				trigger.setTrigger(trig.getTrigger());
				trigger.setReply(new ArrayList<>(trig.getReply()));
				trigger.setCondition(new ArrayList<>(trig.getCondition()));
				trigger.setRedirect(trig.getRedirect());
				trigger.setPrevious(trig.getPrevious());

				topics.get(topic).addTrigger(trigger);

				// Does this one have a %Previous? If so, make a pointer to this exact trigger in this.thats.
				if (trigger.getPrevious() != null) {
					// Initialize the structure first.
					if (!thats.containsKey(topic)) {
						thats.put(topic, new HashMap<String, Map<String, Trigger>>());
					}
					if (!thats.get(topic).containsKey(trigger.getTrigger())) {
						thats.get(topic).put(trigger.getTrigger(), new HashMap<String, Trigger>());
					}
					thats.get(topic).get(trigger.getTrigger()).put(trigger.getPrevious(), trigger);
				}
			}
		}

		// Load all the parsed objects.
		for (ObjectMacro object : ast.getObjects()) {
			// Have a language handler for this?
			if (handlers.containsKey(object.getLanguage())) {
				handlers.get(object.getLanguage()).load(object.getName(), object.getCode().toArray(new String[0]));
				objectLanguages.put(object.getName(), object.getLanguage());
			} else {
				logger.warn("Object '{}' not loaded as no handler was found for programming language '{}'", object.getName(),
						object.getLanguage());
			}
		}
	}

	/*---------------------*/
	/*-- Sorting Methods --*/
	/*---------------------*/

	/**
	 * Sorts the reply structures in memory for optimal matching.
	 * <p>
	 * After finishing loading the RiveScript code, this method needs to be called to populate the various sort buffers.
	 * This is absolutely necessary for reply matching to work efficiently!
	 */
	public void sortReplies() {
		// (Re)initialize the sort cache.
		sorted.getTopics().clear();
		sorted.getThats().clear();
		logger.debug("Sorting triggers...");

		// Loop through all the topics.
		for (String topic : topics.keySet()) {
			logger.debug("Analyzing topic {}", topic);

			// TODO

			//			// Collect a list of all the triggers we're going to worry about. If this
			//			// topic inherits another topic, we need to recursively add those to the
			//			// list as well.
			//			allTriggers := rs.getTopicTriggers(topic, rs.topics, nil)
			//
			//			// Sort these triggers.
			//			rs.sorted.topics[topic] = rs.sortTriggerSet(allTriggers, true)
			//
			//			// Get all of the %Previous triggers for this topic.
			//			thatTriggers := rs.getTopicTriggers(topic, nil, rs.thats)
			//
			//			// And sort them, too.
			//			rs.sorted.thats[topic] = rs.sortTriggerSet(thatTriggers, false)
		}

		// Sort the substitution lists.
		sorted.setSub(sortList(sub.keySet()));
		sorted.setPerson(sortList(person.keySet()));
	}

	/**
	 * Sorts a list of strings by their word counts and lengths.
	 *
	 * @param list the list to sort
	 * @return the sorted list
	 */
	private List<String> sortList(Iterable<String> list) {
		List<String> output = new ArrayList<>();

		// Track by number of words.
		Map<Integer, List<String>> track = new HashMap<>();

		// Loop through each item.
		for (String item : list) {
			int count = StringUtils.wordCount(item, true);
			if (!track.containsKey(count)) {
				track.put(count, new ArrayList<String>());
			}
			track.get(count).add(item);

		}

		// Sort them by word count, descending.
		List<Integer> sortedCounts = new ArrayList<>();
		for (Integer count : track.keySet()) {
			sortedCounts.add(count);
		}
		Collections.sort(sortedCounts);
		Collections.reverse(sortedCounts);

		for (Integer count : sortedCounts) {
			// Sort the strings of this word-count by their lengths.
			List<String> sortedLengths = track.get(count);
			Collections.sort(sortedLengths, new Comparator<String>() {

				@Override
				public int compare(String o1, String o2) {
					return Integer.compare(o2.length(), o1.length());
				}
			});
			// Collections.reverse(sortedLengths);
			for (String item : sortedLengths) {
				output.add(item);
			}
		}

		return output;
	}

	/*---------------------*/
	/*-- Reply Methods   --*/
	/*---------------------*/

	/**
	 * Returns a reply from the bot for a user's message.
	 *
	 * @param username the username
	 * @param message  the user's message
	 * @return
	 */
	public String reply(String username, String message) {
		// TODO
		return null;
	}

	/*------------------*/
	/*-- User Methods --*/
	/*------------------*/

	/**
	 * Sets a user variable.
	 * <p>
	 * This is equivalent to {@code <set>} in RiveScript. Set the value to {@code null} to delete a user variable.
	 *
	 * @param username the username
	 * @param name     the variable name
	 * @param value    the variable value
	 */
	public void setUservar(String username, String name, String value) {
		Map<String, String> vars = new HashMap<>();
		vars.put(name, value);
		sessionManager.set(username, vars);
	}

	/**
	 * Set a user's variables.
	 * <p>
	 * Set multiple user variables by providing a {@link Map} of key/value pairs.
	 * Equivalent to calling {@link #setUservar(String, String, String)} for each pair in the map.
	 *
	 * @param username the name
	 * @param vars     the user variables
	 */
	public void setUservars(String username, Map<String, String> vars) {
		sessionManager.set(username, vars);
	}

	/**
	 * Returns a user variable.
	 * <p>
	 * This is equivalent to {@code <get name>} in RiveScript. Returns {@code "undefined"} if the variable isn't defined.
	 *
	 * @param username the username
	 * @param name     the variable name
	 * @return the variable value
	 */
	public String getUservar(String username, String name) {
		return sessionManager.get(username, name);
	}

	/**
	 * Returns all variables for a user.
	 *
	 * @param username the username
	 * @return the variables
	 */
	public UserData getUservars(String username) {
		return sessionManager.getAny(username);
	}

	/**
	 * Clears all variables for all users.
	 */
	public void clearAllUservars() {
		sessionManager.clearAll();
	}

	/**
	 * Clears a user's variables.
	 *
	 * @param username the username
	 */
	public void clearUservars(String username) {
		sessionManager.clear(username);
	}

	/**
	 * Makes a snapshot of a user's variables.
	 *
	 * @param username the username
	 */
	public void freezeUservars(String username) {
		sessionManager.freeze(username);
	}

	/**
	 * Unfreezes a user's variables.
	 *
	 * @param username the username
	 * @param action   the thaw action
	 * @see ThawAction
	 */
	public void thawUservars(String username, ThawAction action) {
		sessionManager.thaw(username, action);
	}

	/**
	 * Returns a user's last matched trigger.
	 *
	 * @param username the username
	 * @return the last matched trigger
	 */
	public String lastMatch(String username) {
		return sessionManager.getLastMatch(username);
	}

	/**
	 * Returns the current user's ID.
	 * <p>
	 * This is only useful from within a (Java) object macro, to get the ID of the user who invoked the macro.
	 * This value is set at the beginning of {@link #reply(String, String)} and unset at the end, so this method will return {@code null}
	 * outside of a reply context.
	 *
	 * @return the user's ID or {@code null}
	 */
	public String currentUser() {
		return this.currentUser.get();
	}

	/*-----------------------*/
	/*-- Developer Methods --*/
	/*-----------------------*/

	/**
	 * Dumps the trigger sort buffers to the standard output stream.
	 */
	public void dumpSorted() {
		dumpSorted(sorted.getTopics(), "Topics");
		dumpSorted(sorted.getThats(), "Thats");
		dumpSortedList(sorted.getSub(), "Substitutions");
		dumpSortedList(sorted.getPerson(), "Person Substitutions");
	}

	private void dumpSorted(Map<String, List<SortedTriggerEntry>> tree, String label) {
		System.out.println("Sort buffer: " + label);
		for (Map.Entry<String, List<SortedTriggerEntry>> entry : tree.entrySet()) {
			String topic = entry.getKey();
			List<SortedTriggerEntry> data = entry.getValue();
			System.out.println("  Topic: " + topic);
			for (SortedTriggerEntry trigger : data) {
				System.out.println("    + " + trigger.getTrigger());
			}
		}
	}

	private void dumpSortedList(List<String> list, String label) {
		System.out.println("Sort buffer: " + label);
		for (String item : list) {
			System.out.println("  " + item);
		}
	}

	/**
	 * Dumps the entire topic/trigger/reply structure to the standard output stream.
	 */
	public void dumpTopics() {
		for (Map.Entry<String, Topic> entry : topics.entrySet()) {
			String topic = entry.getKey();
			Topic data = entry.getValue();
			System.out.println("Topic: " + topic);
			for (Trigger trigger : data.getTriggers()) {
				System.out.println("  + " + trigger.getTrigger());
				if (trigger.getPrevious() != null) {
					System.out.println("    % " + trigger.getPrevious());
				}
				for (String condition : trigger.getCondition()) {
					System.out.println("    * " + condition);
				}
				for (String reply : trigger.getReply()) {
					System.out.println("    - " + reply);
				}
				if (trigger.getRedirect() != null) {
					System.out.println("    @ " + trigger.getRedirect());
				}
			}
		}
	}

	// OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD
	// OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD
	// OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD OLD

	//	/*---------------------------*/
	//	/*-- Configuration Methods --*/
	//	/*---------------------------*/
	//
	//	/**
	//	 * Adds an {@link ZzObjectHandler} for a programming language to be used with RiveScript object calls.
	//	 *
	//	 * @param name    The name of the programming language.
	//	 * @param handler The instance of a class that implements an ZzObjectHandler.
	//	 */
	//	public void setHandler(String name, ZzObjectHandler handler) {
	//		this.handlers.put(name, handler);
	//	}
	//
	//	/**
	//	 * Defines a Java {@link ZzObjectMacro} from your program.
	//	 * <p>
	//	 * Because Java is a compiled language, this method must be used to create
	//	 * an object macro written in Java.
	//	 *
	//	 * @param name The name of the object macro.
	//	 * @param impl The object macro.
	//	 */
	//	public void setSubroutine(String name, ZzObjectMacro impl) {
	//		// Is the Java handler available?
	//		ZzObjectHandler handler = this.handlers.get("java");
	//		if (handler == null) {
	//			this.error("The Java macro handler is unavailable!");
	//			return;
	//		}
	//
	//		handler.setClass(name, impl);
	//		this.objects.put(name, "java");
	//	}
	//
	//	/**
	//	 * Sets a global variable for the interpreter (equivalent to {@code ! global}).
	//	 * Set the value to {@code null} to delete the variable.<p>
	//	 * <p>
	//	 * There are two special globals that require certain data types:<p>
	//	 * <p>
	//	 * {@code debug} is boolean-like and its value must be a string value containing
	//	 * "true", "yes", "1", "false", "no" or "0".<p>
	//	 * <p>
	//	 * {@code depth} is integer-like and its value must be a quoted integer like "50".
	//	 * The "depth" variable controls how many levels deep RiveScript will go when
	//	 * following reply redirections.<p>
	//	 * <p>
	//	 * Returns {@code true} on success, {@code false} on error.
	//	 *
	//	 * @param name  The variable name.
	//	 * @param value The variable's value.
	//	 */
	//	public boolean setGlobal(String name, String value) {
	//		boolean delete = false;
	//		if (value == null || value == "<undef>") {
	//			delete = true;
	//		}
	//
	//		// Special globals
	//		if (name.equals("debug")) {
	//			// Debug is a boolean.
	//			if (value.equals("true") || value.equals("1") || value.equals("yes")) {
	//				this.debug = true;
	//			} else if (value.equals("false") || value.equals("0") || value.equals("no") || delete) {
	//				this.debug = false;
	//			} else {
	//				return error("Global variable \"debug\" needs a boolean value");
	//			}
	//		} else if (name.equals("depth")) {
	//			// Depth is an integer.
	//			try {
	//				this.depth = Integer.parseInt(value);
	//			} catch (NumberFormatException e) {
	//				return error("Global variable \"depth\" needs an integer value");
	//			}
	//		}
	//
	//		// It's a user-defined global. OK.
	//		if (delete) {
	//			globals.remove(name);
	//		} else {
	//			globals.put(name, value);
	//		}
	//
	//		return true;
	//	}
	//
	//	/**
	//	 * Sets a bot variable for the interpreter (equivalent to {@code ! var}). A bot
	//	 * variable is data about the chatbot, like its name or favorite color.<p>
	//	 * <p>
	//	 * A {@code null} value will delete the variable.
	//	 *
	//	 * @param name  The variable name.
	//	 * @param value The variable's value.
	//	 */
	//	public boolean setVariable(String name, String value) {
	//		if (value == null || value == "<undef>") {
	//			vars.remove(name);
	//		} else {
	//			vars.put(name, value);
	//		}
	//
	//		return true;
	//	}
	//
	//	/**
	//	 * Sets a substitution pattern (equivalent to {@code ! sub}). The user's input (and
	//	 * the bot's reply, in {@code %Previous}) get substituted using these rules.<p>
	//	 * <p>
	//	 * A {@code null} value for the output will delete the substitution.
	//	 *
	//	 * @param pattern The pattern to match in the message.
	//	 * @param output  The text to replace it with (must be lowercase, no special characters).
	//	 */
	//	public boolean setSubstitution(String pattern, String output) {
	//		if (output == null || output == "<undef>") {
	//			subs.remove(pattern);
	//		} else {
	//			subs.put(pattern, output);
	//		}
	//
	//		return true;
	//	}
	//
	//	/**
	//	 * Sets a person substitution pattern (equivalent to {@code ! person}). Person
	//	 * substitutions swap first- and second-person pronouns, so the bot can
	//	 * safely echo the user without sounding too mechanical.<p>
	//	 * <p>
	//	 * A {@code null} value for the output will delete the substitution.
	//	 *
	//	 * @param pattern The pattern to match in the message.
	//	 * @param output  The text to replace it with (must be lowercase, no special characters).
	//	 */
	//	public boolean setPersonSubstitution(String pattern, String output) {
	//		if (output == null || output == "<undef>") {
	//			person.remove(pattern);
	//		} else {
	//			person.put(pattern, output);
	//		}
	//
	//		return true;
	//	}
	//
	//	/**
	//	 * Sets a variable for one of the bot's users. A {@code null} value will delete a
	//	 * variable.
	//	 *
	//	 * @param user  The user's id.
	//	 * @param name  The name of the variable to set.
	//	 * @param value The value to set.
	//	 */
	//	public boolean setUservar(String user, String name, String value) {
	//		if (value == null || value == "<undef>") {
	//			clients.client(user).delete(name);
	//		} else {
	//			clients.client(user).set(name, value);
	//		}
	//
	//		return true;
	//	}
	//
	//	/**
	//	 * Sets -all- user vars for a user. This will replace the internal hash for
	//	 * the user. So your hash should at least contain a key/value pair for the
	//	 * user's current "topic". This could be useful if you used {@link #getUservars(String)}
	//	 * to store their entire profile somewhere and want to restore it later.
	//	 *
	//	 * @param user The user's ID.
	//	 * @param data The full hash of the user's data.
	//	 */
	//	public boolean setUservars(String user, HashMap<String, String> data) {
	//		// TODO: this should be handled more sanely. ;)
	//		clients.client(user).setData(data);
	//		return true;
	//	}
	//
	//	/**
	//	 * Gets a list of all the user id's the bot knows about.
	//	 */
	//	public String[] getUsers() {
	//		// Get the user list from the clients object.
	//		return clients.listClients();
	//	}
	//
	//	/**
	//	 * Returns a listing of all the uservars for a user as a {@link HashMap}.
	//	 * Returns {@code null} if the user doesn't exist.
	//	 *
	//	 * @param user The user ID to get the vars for.
	//	 */
	//	public HashMap<String, String> getUservars(String user) {
	//		if (clients.clientExists(user)) {
	//			return clients.client(user).getData();
	//		} else {
	//			return null;
	//		}
	//	}
	//
	//	/**
	//	 * Returns a single variable from a user's profile.
	//	 * <p>
	//	 * Returns {@code null} if the user doesn't exist. Returns the string "undefined"
	//	 * if the variable doesn't exist.
	//	 *
	//	 * @param user The user id to get data from.
	//	 * @param name The name of the variable to get.
	//	 */
	//	public String getUservar(String user, String name) {
	//		if (clients.clientExists(user)) {
	//			return clients.client(user).get(name);
	//		} else {
	//			return null;
	//		}
	//	}
	//	/*---------------------*/
	//	/*-- Parsing Methods --*/
	//	/*---------------------*/
	//
	//		/**
	//		 * Parses RiveScript code and load it into internal memory.
	//		 *
	//		 * @param filename The file name to associate with this code (for error reporting).
	//		 * @param code     The string array of all the code to parse.
	//		 */
	//		protected boolean parse(String filename, String[] code) {
	//			// Track some state variables for this parsing round.
	//			String topic = "random";        // Default topic = random
	//			int lineno = 0;
	//			boolean comment = false;        // In a multi-line comment
	//			boolean inobj = false;          // In an object
	//			String objName = "";            // Name of the current object
	//			String objLang = "";            // Programming language of the object
	//			Vector<String> objBuff = null;  // Buffer for the current object
	//			String onTrig = "";             // ZzTrigger we're on
	//			String lastcmd = "";            // Last command code
	//			String isThat = "";             // Is a %Previous trigger
	//
	//			// File scoped parser options.
	//			HashMap<String, String> local_options = new HashMap<>();
	//			local_options.put("concat", "none");
	//
	//			// The given "code" is an array of lines, so jump right in.
	//			for (int i = 0; i < code.length; i++) {
	//				lineno++; // Increment the line counter.
	//				String line = code[i];
	//				say("Line: " + line);
	//
	//				// Trim the line of whitespaces.
	//				line = line.trim();
	//
	//				// Are we inside an object?
	//				if (inobj) {
	//					if (line.startsWith("<object") || line.startsWith("< object")) { // TODO regexp
	//						// End of the object. Did we have a handler?
	//						if (handlers.containsKey(objLang)) {
	//							// Yes, call the handler's onLoad function.
	//							handlers.get(objLang).onLoad(objName, ZzUtil.Sv2s(objBuff));
	//
	//							// Map the name to the language.
	//							objects.put(objName, objLang);
	//						}
	//
	//						objName = "";
	//						objLang = "";
	//						objBuff = null;
	//						inobj = false;
	//						continue;
	//					}
	//
	//					// Collect the code.
	//					objBuff.add(line);
	//					continue;
	//				}
	//
	//				// Look for comments.
	//				if (line.startsWith("/*")) {
	//					// Beginning a multi-line comment.
	//					if (line.indexOf("*/") > -1) {
	//						// It ends on the same line.
	//						continue;
	//					}
	//					comment = true;
	//				} else if (line.startsWith("/")) {
	//					// A single line comment.
	//					continue;
	//				} else if (line.indexOf("*/") > -1) {
	//					// End a multi-line comment.
	//					comment = false;
	//					continue;
	//				}
	//				if (comment) {
	//					continue;
	//				}
	//
	//				// Skip any blank lines.
	//				if (line.length() < 2) {
	//					continue;
	//				}
	//
	//				// Separate the command from the rest of the line.
	//				String cmd = line.substring(0, 1);
	//				line = line.substring(1).trim();
	//				say("\tCmd: " + cmd);
	//
	//				// Ignore inline comments.
	//				if (line.indexOf(" // ") > -1) {
	//					String[] split = line.split(" // ");
	//					line = split[0];
	//				}
	//
	//				// Reset the %Previous if this is a new +ZzTrigger.
	//				if (cmd.equals(CMD_TRIGGER)) {
	//					isThat = "";
	//				}
	//
	//				// Do a look-ahead to see ^Continue and %Previous.
	//				for (int j = (i + 1); j < code.length; j++) {
	//					// Peek ahead.
	//					String peek = code[j].trim();
	//
	//					// Skip blank.
	//					if (peek.length() == 0) {
	//						continue;
	//					}
	//
	//					// Get the command.
	//					String peekCmd = peek.substring(0, 1);
	//					peek = peek.substring(1).trim();
	//
	//					// Only continue if the lookahead line has any data.
	//					if (peek.length() > 0) {
	//						// The lookahead command has to be a % or a ^
	//						if (peekCmd.equals(CMD_CONTINUE) == false && peekCmd.equals(CMD_PREVIOUS) == false) {
	//							break;
	//						}
	//
	//						// If the current command is a +, see if the following is a %.
	//						if (cmd.equals(CMD_TRIGGER)) {
	//							if (peekCmd.equals(CMD_PREVIOUS)) {
	//								// It has a %Previous!
	//								isThat = peek;
	//								break;
	//							} else {
	//								isThat = "";
	//							}
	//						}
	//
	//						// If the current command is a ! and the next command(s) are
	//						// ^, we'll tack each extension on as a "line break".
	//						if (cmd.equals(CMD_DEFINE)) {
	//							if (peekCmd.equals(CMD_CONTINUE)) {
	//								line += "<crlf>" + peek;
	//							}
	//						}
	//
	//						// If the current command is not a ^ and the line after is
	//						// not a %, but the line after IS a ^, then tack it onto the
	//						// end of the current line.
	//						if (cmd.equals(CMD_CONTINUE) == false && cmd.equals(CMD_PREVIOUS) == false && cmd.equals(CMD_DEFINE) == false) {
	//							if (peekCmd.equals(CMD_CONTINUE)) {
	//								// Concatenation character?
	//								String concat = "";
	//								if (local_options.get("concat").equals("space")) {
	//									concat = " ";
	//								} else if (local_options.get("concat").equals("newline")) {
	//									concat = "\n";
	//								}
	//								line += concat + peek;
	//							} else {
	//								break;
	//							}
	//						}
	//					}
	//				}
	//
	//				// Start handling command types.
	//				if (cmd.equals(CMD_DEFINE)) {
	//					say("\t! DEFINE");
	//					String[] whatis = line.split("\\s*=\\s*", 2);
	//					String[] left = whatis[0].split("\\s+", 2);
	//					String type = left[0];
	//					String var = "";
	//					String value = "";
	//					boolean delete = false;
	//					if (left.length == 2) {
	//						var = left[1].trim().toLowerCase();
	//					}
	//					if (whatis.length == 2) {
	//						value = whatis[1].trim();
	//					}
	//
	//					// Remove line breaks unless this is an array.
	//					if (!type.equals("array")) {
	//						value = value.replaceAll("<crlf>", "");
	//					}
	//
	//					// Version is the only type that doesn't have a var.
	//					if (type.equals("version")) {
	//						say("\tUsing RiveScript version " + value);
	//
	//						// Convert the value into a double, catch exceptions.
	//						double version = 0;
	//						try {
	//							version = Double.valueOf(value).doubleValue();
	//						} catch (NumberFormatException e) {
	//							cry("RiveScript version \"" + value + "\" not a valid floating number", filename, lineno);
	//							continue;
	//						}
	//
	//						if (version > RS_VERSION) {
	//							cry("We can't parse RiveScript v" + value + " documents", filename, lineno);
	//							return false;
	//						}
	//
	//						continue;
	//					} else {
	//						// All the other types require a variable and value.
	//						if (var.equals("")) {
	//							cry("Missing a " + type + " variable name", filename, lineno);
	//							continue;
	//						}
	//						if (value.equals("")) {
	//							cry("Missing a " + type + " value", filename, lineno);
	//							continue;
	//						}
	//						if (value.equals("<undef>")) {
	//							// Deleting its value.
	//							delete = true;
	//						}
	//					}
	//
	//					// Handle the variable set types.
	//					if (type.equals("local")) {
	//						// Local file scoped parser options
	//						say("\tSet local parser option " + var + " = " + value);
	//						local_options.put(var, value);
	//					} else if (type.equals("global")) {
	//						// Is it a special global? (debug or depth or etc).
	//						say("\tSet global " + var + " = " + value);
	//						this.setGlobal(var, value);
	//					} else if (type.equals("var")) {
	//						// Set a bot variable.
	//						say("\tSet bot variable " + var + " = " + value);
	//						this.setVariable(var, value);
	//					} else if (type.equals("array")) {
	//						// Set an array.
	//						say("\tSet array " + var);
	//
	//						// Deleting it?
	//						if (delete) {
	//							arrays.remove(var);
	//							continue;
	//						}
	//
	//						// Did the array have multiple lines?
	//						String[] parts = value.split("<crlf>");
	//						Vector<String> items = new Vector<String>();
	//						for (int a = 0; a < parts.length; a++) {
	//							// Split at pipes or spaces?
	//							String[] pieces;
	//							if (parts[a].indexOf("|") > -1) {
	//								pieces = parts[a].split("\\|");
	//							} else {
	//								pieces = parts[a].split("\\s+");
	//							}
	//
	//							// Add the pieces to the final array.
	//							for (int b = 0; b < pieces.length; b++) {
	//								items.add(pieces[b]);
	//							}
	//						}
	//
	//						// Store this array.
	//						arrays.put(var, items);
	//					} else if (type.equals("sub")) {
	//						// Set a substitution.
	//						say("\tSubstitution " + var + " => " + value);
	//						this.setSubstitution(var, value);
	//					} else if (type.equals("person")) {
	//						// Set a person substitution.
	//						say("\tPerson substitution " + var + " => " + value);
	//						this.setPersonSubstitution(var, value);
	//					} else {
	//						cry("Unknown definition type \"" + type + "\"", filename, lineno);
	//						continue;
	//					}
	//				} else if (cmd.equals(CMD_LABEL)) {
	//					// > LABEL
	//					say("\t> LABEL");
	//					String label[] = line.split("\\s+");
	//					String type = "";
	//					String name = "";
	//					if (label.length >= 1) {
	//						type = label[0].trim().toLowerCase();
	//					}
	//					if (label.length >= 2) {
	//						name = label[1].trim();
	//					}
	//
	//					// Handle the label types.
	//					if (type.equals("begin")) {
	//						// The BEGIN statement.
	//						say("\tFound the BEGIN Statement.");
	//
	//						// A BEGIN is just a special topic.
	//						type = "topic";
	//						name = "__begin__";
	//					}
	//					if (type.equals("topic")) {
	//						// Starting a new topic.
	//						say("\tSet topic to " + name);
	//						onTrig = "";
	//						topic = name;
	//
	//						// Does this topic include or inherit another one?
	//						if (label.length >= 3) {
	//							final int mode_includes = 1;
	//							final int mode_inherits = 2;
	//							int mode = 0;
	//							for (int a = 2; a < label.length; a++) {
	//								if (label[a].toLowerCase().equals("includes")) {
	//									mode = mode_includes;
	//								} else if (label[a].toLowerCase().equals("inherits")) {
	//									mode = mode_inherits;
	//								} else if (mode > 0) {
	//									// This topic is either inherited or included.
	//									if (mode == mode_includes) {
	//										topics.topic(topic).includes(label[a]);
	//									} else if (mode == mode_inherits) {
	//										topics.topic(topic).inherits(label[a]);
	//									}
	//								}
	//							}
	//						}
	//					}
	//					if (type.equals("object")) {
	//						// If a field was provided, it should be the programming language.
	//						String lang = "";
	//						if (label.length >= 3) {
	//							lang = label[2].toLowerCase();
	//						}
	//
	//						// Only try to parse a language we support.
	//						onTrig = "";
	//						if (lang.length() == 0) {
	//							cry("Trying to parse unknown programming language (assuming it's JavaScript)", filename, lineno);
	//							lang = "javascript"; // Assume it's JavaScript
	//						}
	//						if (!handlers.containsKey(lang)) {
	//							// We don't have a handler for this language.
	//							say("We can't handle " + lang + " object code!");
	//							continue;
	//						}
	//
	//						// Start collecting its code!
	//						objName = name;
	//						objLang = lang;
	//						objBuff = new Vector<String>();
	//						inobj = true;
	//					}
	//				} else if (cmd.equals(CMD_ENDLABEL)) {
	//					// < ENDLABEL
	//					say("\t< ENDLABEL");
	//					String type = line.trim().toLowerCase();
	//
	//					if (type.equals("begin") || type.equals("topic")) {
	//						say("\t\tEnd topic label.");
	//						topic = "random";
	//					} else if (type.equals("object")) {
	//						say("\t\tEnd object label.");
	//						inobj = false;
	//					} else {
	//						cry("Unknown end topic type \"" + type + "\"", filename, lineno);
	//					}
	//				} else if (cmd.equals(CMD_TRIGGER)) {
	//					// + TRIGGER
	//					say("\t+ TRIGGER: " + line);
	//
	//					if (isThat.length() > 0) {
	//						// This trigger had a %Previous. To prevent conflict, tag the
	//						// trigger with the "that" text.
	//						onTrig = line + "{previous}" + isThat;
	//						topics.topic(topic).trigger(line).hasPrevious(true);
	//						topics.topic(topic).addPrevious(line, isThat);
	//					} else {
	//						// Set the current trigger to this.
	//						onTrig = line;
	//					}
	//				} else if (cmd.equals(CMD_REPLY)) {
	//					// - REPLY
	//					say("\t- REPLY: " + line);
	//
	//					// This can't come before a trigger!
	//					if (onTrig.length() == 0) {
	//						cry("Reply found before trigger", filename, lineno);
	//						continue;
	//					}
	//
	//					// Add the reply to the trigger.
	//					topics.topic(topic).trigger(onTrig).addReply(line);
	//				} else if (cmd.equals(CMD_PREVIOUS)) {
	//					// % PREVIOUS
	//					// This was handled above.
	//				} else if (cmd.equals(CMD_CONTINUE)) {
	//					// ^ CONTINUE
	//					// This was handled above.
	//				} else if (cmd.equals(CMD_REDIRECT)) {
	//					// @ REDIRECT
	//					say("\t@ REDIRECT: " + line);
	//
	//					// This can't come before a trigger!
	//					if (onTrig.length() == 0) {
	//						cry("Redirect found before trigger", filename, lineno);
	//						continue;
	//					}
	//
	//					// Add the redirect to the trigger.
	//					// TODO: this extends RiveScript, not compat w/ Perl yet
	//					topics.topic(topic).trigger(onTrig).addRedirect(line);
	//				} else if (cmd.equals(CMD_CONDITION)) {
	//					// * CONDITION
	//					say("\t* CONDITION: " + line);
	//
	//					// This can't come before a trigger!
	//					if (onTrig.length() == 0) {
	//						cry("Redirect found before trigger", filename, lineno);
	//						continue;
	//					}
	//
	//					// Add the condition to the trigger.
	//					topics.topic(topic).trigger(onTrig).addCondition(line);
	//				} else {
	//					cry("Unrecognized command \"" + cmd + "\"", filename, lineno);
	//				}
	//			}
	//
	//			return true;
	//		}
	//
	//	/*---------------------*/
	//	/*-- Sorting Methods --*/
	//	/*---------------------*/
	//
	//		/**
	//		 * Sorts the replies. This should be called after loading the replies in memory
	//		 * to (re)initialize internal sort buffers. This is necessary for accurate trigger matching.
	//		 */
	//		public void sortReplies() {
	//			// We need to make sort buffers under each topic.
	//			String[] topics = this.topics.listTopics();
	//			say("There are " + topics.length + " topics to sort replies for.");
	//
	//			// Tell the topic manager to sort its topics' replies.
	//			this.topics.sortReplies();
	//
	//			// Sort the substitutions.
	//			subs_s = ZzUtil.sortByLength(ZzUtil.SSh2s(subs));
	//			person_s = ZzUtil.sortByLength(ZzUtil.SSh2s(person));
	//		}
	//
	//	/*---------------------*/
	//	/*-- Reply Methods   --*/
	//	/*---------------------*/
	//
	//	/**
	//	 * Returns a reply from the RiveScript interpreter.
	//	 *
	//	 * @param username The unique user id for the user chatting with the bot.
	//	 * @param message  The user's message to the bot.
	//	 */
	//	public String reply(String username, String message) {
	//		say("Get reply to [" + username + "] " + message);
	//
	//		// Store the current ID in case an object macro wants it.
	//		this.currentUser.set(username);
	//
	//		try {
	//
	//			// Format their message first.
	//			message = formatMessage(message);
	//
	//			// This will hold the final reply.
	//			String reply;
	//
	//			// If the BEGIN statement exists, consult it first.
	//			if (topics.exists("__begin__")) {
	//				String begin = this.reply(username, "request", true, 0);
	//
	//				// OK to continue?
	//				if (begin.indexOf("{ok}") > -1) {
	//					// Get a reply then.
	//					reply = this.reply(username, message, false, 0);
	//					begin = begin.replaceAll("\\{ok\\}", reply);
	//					reply = begin;
	//				} else {
	//					reply = begin;
	//				}
	//
	//				// Run final substitutions.
	//				reply = processTags(username, clients.client(username), message, reply,
	//						new Vector<String>(), new Vector<String>(),
	//						0);
	//			} else {
	//				// No BEGIN, just continue.
	//				reply = this.reply(username, message, false, 0);
	//			}
	//
	//			// Save their chat history.
	//			clients.client(username).addInput(message);
	//			clients.client(username).addReply(reply);
	//
	//			// Return their reply.
	//			return reply;
	//
	//		} finally {
	//			// Clear the current user.
	//			this.currentUser.remove();
	//		}
	//	}
	//
	//	/**
	//	 * Internal method for getting a reply.
	//	 *
	//	 * @param user    The username of the calling user.
	//	 * @param message The (formatted!) message sent by the user.
	//	 * @param begin   Whether the context is that we're in the BEGIN statement or not.
	//	 * @param step    The recursion depth that we're at so far.
	//	 */
	//	private String reply(String user, String message, boolean begin, int step) {
	//		/*-----------------------*/
	//		/*-- Collect User Info --*/
	//		/*-----------------------*/
	//
	//		String topic = "random";                  // Default topic = random
	//		Vector<String> stars = new Vector<>();    // Wildcard matches
	//		Vector<String> botstars = new Vector<>(); // Wildcards in %Previous
	//		String reply = "";                        // The eventual reply
	//		ZzClient profile;                           // The user's profile object
	//
	//		// Get the user's profile.
	//		profile = clients.client(user);
	//
	//		// Update their topic.
	//		topic = profile.get("topic");
	//
	//		// Avoid letting the user fall into a missing topic.
	//		if (topics.exists(topic) == false) {
	//			cry("User " + user + " was in a missing topic named \"" + topic + "\"!");
	//			topic = "random";
	//			profile.set("topic", "random");
	//		}
	//
	//		// Avoid deep recursion.
	//		if (step > depth) {
	//			reply = "ERR: Deep Recursion Detected!";
	//			cry(reply);
	//			return reply;
	//		}
	//
	//		// Are we in the BEGIN statement?
	//		if (begin) {
	//			// This implies the begin topic.
	//			topic = "__begin__";
	//		}
	//
	//		/*------------------*/
	//		/*-- Find a Reply --*/
	//		/*------------------*/
	//
	//		// Create a pointer for the matched data.
	//		ZzTrigger matched = null;
	//		boolean foundMatch = false;
	//		String matchedTrigger = "";
	//
	//		// See if there are any %previous's in this topic, or any topic related to it. This
	//		// should only be done the first time -- not during a recursive redirection.
	//		if (step == 0) {
	//			say("Looking for a %Previous");
	//			String[] allTopics = {topic};
	//			if (this.topics.topic(topic).includes().length > 0 || this.topics.topic(topic).inherits().length > 0) {
	//				// We need to walk the topic tree.
	//				allTopics = this.topics.getTopicTree(topic, 0);
	//			}
	//			for (int i = 0; i < allTopics.length; i++) {
	//				// Does this topic have a %Previous anywhere?
	//				say("Seeing if " + allTopics[i] + " has a %Previous");
	//				if (this.topics.topic(allTopics[i]).hasPrevious()) {
	//					say("zzTopic " + allTopics[i] + " has at least one %Previous");
	//
	//					// Get them.
	//					String[] previous = this.topics.topic(allTopics[i]).listPrevious();
	//					for (int j = 0; j < previous.length; j++) {
	//						say("Candidate: " + previous[j]);
	//
	//						// Try to match the bot's last reply against this.
	//						String lastReply = formatMessage(profile.getReply(1));
	//						String regexp = triggerRegexp(user, profile, previous[j]);
	//						say("Compare " + lastReply + " <=> " + previous[j] + " (" + regexp + ")");
	//
	//						// Does it match?
	//						Pattern re = Pattern.compile("^" + regexp + "$");
	//						Matcher m = re.matcher(lastReply);
	//						while (m.find() == true) {
	//							say("OMFG the lastReply matches!");
	//
	//							// Harvest the botstars.
	//							for (int s = 1; s <= m.groupCount(); s++) {
	//								say("Add botstar: " + m.group(s));
	//								botstars.add(m.group(s));
	//							}
	//
	//							// Now see if the user matched this trigger too!
	//							String[] candidates = this.topics.topic(allTopics[i]).listPreviousTriggers(previous[j]);
	//							for (int k = 0; k < candidates.length; k++) {
	//								say("Does the user's message match " + candidates[k] + "?");
	//								String humanside = triggerRegexp(user, profile, candidates[k]);
	//								say("Compare " + message + " <=> " + candidates[k] + " (" + humanside + ")");
	//
	//								Pattern reH = Pattern.compile("^" + humanside + "$");
	//								Matcher mH = reH.matcher(message);
	//								while (mH.find() == true) {
	//									say("It's a match!!!");
	//
	//									// Make sure it's all valid.
	//									String realTrigger = candidates[k] + "{previous}" + previous[j];
	//									if (this.topics.topic(allTopics[i]).triggerExists(realTrigger)) {
	//										// Seems to be! Collect the stars.
	//										for (int s = 1; s <= mH.groupCount(); s++) {
	//											say("Add star: " + mH.group(s));
	//											stars.add(mH.group(s));
	//										}
	//
	//										foundMatch = true;
	//										matchedTrigger = candidates[k];
	//										matched = this.topics.topic(allTopics[i]).trigger(realTrigger);
	//									}
	//
	//									break;
	//								}
	//
	//								if (foundMatch) {
	//									break;
	//								}
	//							}
	//							if (foundMatch) {
	//								break;
	//							}
	//						}
	//					}
	//				}
	//			}
	//		}
	//
	//		// Search their topic for a match to their trigger.
	//		if (foundMatch == false) {
	//			// Go through the sort buffer for their topic.
	//			String[] triggers = topics.topic(topic).listTriggers();
	//			for (int a = 0; a < triggers.length; a++) {
	//				String trigger = triggers[a];
	//
	//				// Prepare the trigger for the regular expression engine.
	//				String regexp = triggerRegexp(user, profile, trigger);
	//				say("Try to match \"" + message + "\" against \"" + trigger + "\" (" + regexp + ")");
	//
	//				// Is it a match?
	//				Pattern re = Pattern.compile("^" + regexp + "$");
	//				Matcher m = re.matcher(message);
	//				if (m.find() == true) {
	//					say("The trigger matches! Star count: " + m.groupCount());
	//
	//					// Harvest the stars.
	//					int starcount = m.groupCount();
	//					for (int s = 1; s <= starcount; s++) {
	//						String star = m.group(s);
	//						if (star == null) {
	//							star = "";
	//						}
	//						say("Add star: " + star);
	//						stars.add(star);
	//					}
	//
	//					// We found a match, but what if the trigger we matched belongs to
	//					// an inherited topic? Check for that.
	//					if (this.topics.topic(topic).triggerExists(trigger)) {
	//						// No, the trigger does belong to us.
	//						matched = this.topics.topic(topic).trigger(trigger);
	//					} else {
	//						say("ZzTrigger doesn't exist under this topic, trying to find it!");
	//						matched = this.topics.findTriggerByInheritance(topic, trigger, 0);
	//					}
	//
	//					foundMatch = true;
	//					matchedTrigger = trigger;
	//					break;
	//				}
	//			}
	//		}
	//
	//		// Store what trigger they matched on (matchedTrigger can be blank if they didn't match).
	//		profile.set("__lastmatch__", matchedTrigger);
	//
	//		// Did they match anything?
	//		if (foundMatch) {
	//			say("They were successfully matched to a trigger!");
	//
	//			/*---------------------------------*/
	//			/*-- Process Their Matched Reply --*/
	//			/*---------------------------------*/
	//
	//			// Make a dummy once loop so we can break out anytime.
	//			for (int n = 0; n < 1; n++) {
	//				// Exists?
	//				if (matched == null) {
	//					cry("Unknown error: they matched trigger " + matchedTrigger + ", but it doesn't exist?");
	//					foundMatch = false;
	//					break;
	//				}
	//
	//				// Get the trigger object.
	//				ZzTrigger trigger = matched;
	//				say("The trigger matched belongs to topic " + trigger.topic());
	//
	//				// Check for conditions.
	//				String[] conditions = trigger.listConditions();
	//				if (conditions.length > 0) {
	//					say("This trigger has some conditions!");
	//
	//					// See if any conditions are true.
	//					boolean truth = false;
	//					for (int c = 0; c < conditions.length; c++) {
	//						// Separate the condition from the potential reply.
	//						String[] halves = conditions[c].split("\\s*=>\\s*");
	//						String condition = halves[0].trim();
	//						String potreply = halves[1].trim();
	//
	//						// Split up the condition.
	//						Pattern reCond = Pattern.compile("^(.+?)\\s+(==|eq|\\!=|ne|<>|<|<=|>|>=)\\s+(.+?)$");
	//						Matcher mCond = reCond.matcher(condition);
	//						while (mCond.find()) {
	//							String left = mCond.group(1).trim();
	//							String eq = mCond.group(2).trim();
	//							String right = mCond.group(3).trim();
	//
	//							// Process tags on both halves.
	//							left = processTags(user, profile, message, left, stars, botstars, step + 1);
	//							right = processTags(user, profile, message, right, stars, botstars, step + 1);
	//							say("Compare: " + left + " " + eq + " " + right);
	//
	//							// Defaults
	//							if (left.length() == 0) {
	//								left = "undefined";
	//							}
	//							if (right.length() == 0) {
	//								right = "undefined";
	//							}
	//
	//							// Validate the expression.
	//							if (eq.equals("eq") || eq.equals("ne") || eq.equals("==") || eq.equals("!=") || eq.equals("<>")) {
	//								// String equality comparing.
	//								if ((eq.equals("eq") || eq.equals("==")) && left.equals(right)) {
	//									truth = true;
	//									break;
	//								} else if ((eq.equals("ne") || eq.equals("!=") || eq.equals("<>")) && !left.equals(right)) {
	//									truth = true;
	//									break;
	//								}
	//							}
	//
	//							// Numeric comparing.
	//							int lt = 0;
	//							int rt = 0;
	//
	//							// Turn the two sides into numbers.
	//							try {
	//								lt = Integer.parseInt(left);
	//								rt = Integer.parseInt(right);
	//							} catch (NumberFormatException e) {
	//								// Oh well!
	//								break;
	//							}
	//
	//							// Run the remaining equality checks.
	//							if (eq.equals("==") || eq.equals("!=") || eq.equals("<>")) {
	//								// Equality checks.
	//								if (eq.equals("==") && lt == rt) {
	//									truth = true;
	//									break;
	//								} else if ((eq.equals("!=") || eq.equals("<>")) && lt != rt) {
	//									truth = true;
	//									break;
	//								}
	//							} else if (eq.equals("<") && lt < rt) {
	//								truth = true;
	//								break;
	//							} else if (eq.equals("<=") && lt <= rt) {
	//								truth = true;
	//								break;
	//							} else if (eq.equals(">") && lt > rt) {
	//								truth = true;
	//								break;
	//							} else if (eq.equals(">=") && lt >= rt) {
	//								truth = true;
	//								break;
	//							}
	//						}
	//
	//						// True condition?
	//						if (truth) {
	//							reply = potreply;
	//							break;
	//						}
	//					}
	//				}
	//
	//				// Break if we got a reply from the conditions.
	//				if (reply.length() > 0) {
	//					break;
	//				}
	//
	//				// Return one of the replies at random. We lump any redirects in as well.
	//				String[] redirects = trigger.listRedirects();
	//				String[] replies = trigger.listReplies();
	//
	//				// Take into account their weights.
	//				Vector<Integer> bucket = new Vector<>();
	//				Pattern reWeight = Pattern.compile("\\{weight=(\\d+?)\\}");
	//
	//				// Look at weights on redirects.
	//				for (int i = 0; i < redirects.length; i++) {
	//					if (redirects[i].indexOf("{weight=") > -1) {
	//						Matcher mWeight = reWeight.matcher(redirects[i]);
	//						while (mWeight.find()) {
	//							int weight = Integer.parseInt(mWeight.group(1));
	//
	//							// Add to the bucket this many times.
	//							if (weight > 1) {
	//								for (int j = 0; j < weight; j++) {
	//									say("ZzTrigger has a redirect (weight " + weight + "): " + redirects[i]);
	//									bucket.add(i);
	//								}
	//							} else {
	//								say("ZzTrigger has a redirect (weight " + weight + "): " + redirects[i]);
	//								bucket.add(i);
	//							}
	//
	//							// Only one weight is supported.
	//							break;
	//						}
	//					} else {
	//						say("ZzTrigger has a redirect: " + redirects[i]);
	//						bucket.add(i);
	//					}
	//				}
	//
	//				// Look at weights on replies.
	//				for (int i = 0; i < replies.length; i++) {
	//					if (replies[i].indexOf("{weight=") > -1) {
	//						Matcher mWeight = reWeight.matcher(replies[i]);
	//						while (mWeight.find()) {
	//							int weight = Integer.parseInt(mWeight.group(1));
	//
	//							// Add to the bucket this many times.
	//							if (weight > 1) {
	//								for (int j = 0; j < weight; j++) {
	//									say("ZzTrigger has a reply (weight " + weight + "): " + replies[i]);
	//									bucket.add(redirects.length + i);
	//								}
	//							} else {
	//								say("ZzTrigger has a reply (weight " + weight + "): " + replies[i]);
	//								bucket.add(redirects.length + i);
	//							}
	//
	//							// Only one weight is supported.
	//							break;
	//						}
	//					} else {
	//						say("ZzTrigger has a reply: " + replies[i]);
	//						bucket.add(redirects.length + i);
	//					}
	//				}
	//
	//				// Pull a random value out.
	//				int[] choices = ZzUtil.Iv2s(bucket);
	//				if (choices.length > 0) {
	//					int choice = choices[rand.nextInt(choices.length)];
	//					say("Possible choices: " + choices.length + "; chosen: " + choice);
	//					if (choice < redirects.length) {
	//						// The choice was a redirect!
	//						String redirect = redirects[choice].replaceAll("\\{weight=\\d+\\}", "");
	//						redirect = processTags(user, profile, message, redirect, stars, botstars, step);
	//						say("Chosen a redirect to " + redirect + "!");
	//						reply = reply(user, redirect, begin, step + 1);
	//					} else {
	//						// The choice was a reply!
	//						choice -= redirects.length;
	//						if (choice < replies.length) {
	//							say("Chosen a reply: " + replies[choice]);
	//							reply = replies[choice];
	//						}
	//					}
	//				}
	//			}
	//		}
	//
	//		// Still no reply?
	//		if (!foundMatch) {
	//			reply = "ERR: No Reply Matched";
	//		} else if (reply.length() == 0) {
	//			reply = "ERR: No Reply Found";
	//		}
	//
	//		say("Final reply: " + reply + " (begin: " + begin + ")");
	//
	//		// Special tag processing for the BEGIN statement.
	//		if (begin) {
	//			// The BEGIN block may have {topic} or <set> tags and that's all.
	//			// <set> tag
	//			if (reply.indexOf("<set") > -1) {
	//				Pattern reSet = Pattern.compile("<set (.+?)=(.+?)>");
	//				Matcher mSet = reSet.matcher(reply);
	//				while (mSet.find()) {
	//					String tag = mSet.group(0);
	//					String var = mSet.group(1);
	//					String value = mSet.group(2);
	//
	//					// Set the uservar.
	//					profile.set(var, value);
	//					reply = reply.replace(tag, "");
	//				}
	//			}
	//
	//			// {topic} tag
	//			if (reply.indexOf("{topic=") > -1) {
	//				Pattern reTopic = Pattern.compile("\\{topic=(.+?)\\}");
	//				Matcher mTopic = reTopic.matcher(reply);
	//				while (mTopic.find()) {
	//					String tag = mTopic.group(0);
	//					topic = mTopic.group(1);
	//					say("Set user's topic to: " + topic);
	//					profile.set("topic", topic);
	//					reply = reply.replace(tag, "");
	//				}
	//			}
	//		} else {
	//			// Process tags.
	//			reply = processTags(user, profile, message, reply, stars, botstars, step);
	//		}
	//
	//		return reply;
	//	}
	//
	//	/**
	//	 * Formats a trigger for the regular expression engine.
	//	 *
	//	 * @param user    The user id of the caller.
	//	 * @param trigger The raw trigger text.
	//	 */
	//	private String triggerRegexp(String user, ZzClient profile, String trigger) {
	//		// If the trigger is simply '*', it needs to become (.*?) so it catches the empty string.
	//		String regexp = trigger.replaceAll("^\\*$", "<zerowidthstar>");
	//
	//		// Simple regexps are simple.
	//		regexp = regexp.replaceAll("\\*", "(.+?)");                  // *  ->  (.+?)
	//		regexp = regexp.replaceAll("#", "(\\\\d+?)");                // #  ->  (\d+?)
	//		regexp = regexp.replaceAll("(?<!\\\\)_", "(\\\\w+?)");       // _  ->  ([A-Za-z ]+?)
	//		regexp = regexp.replaceAll("\\\\_", "_");                    // \_ ->  _
	//		regexp = regexp.replaceAll("\\s*\\{weight=\\d+\\}\\s*", ""); // Remove {weight} tags
	//		regexp = regexp.replaceAll("<zerowidthstar>", "(.*?)");      // *  ->  (.*?)
	//
	//		// Handle optionals.
	//		if (regexp.indexOf("[") > -1) {
	//			Pattern reOpts = Pattern.compile("\\s*\\[(.+?)\\]\\s*");
	//			Matcher mOpts = reOpts.matcher(regexp);
	//			while (mOpts.find() == true) {
	//				String optional = mOpts.group(0);
	//				String contents = mOpts.group(1);
	//
	//				// Split them at the pipes.
	//				String[] parts = contents.split("\\|");
	//
	//				// Construct a regexp part.
	//				StringBuffer re = new StringBuffer();
	//				for (int i = 0; i < parts.length; i++) {
	//					// See: https://github.com/aichaos/rivescript-js/commit/02f236e78c5d237cb046d2347fe704f5f70231c9
	//					re.append("(?:\\s|\\b)+" + parts[i] + "(?:\\s|\\b)+");
	//					if (i < parts.length - 1) {
	//						re.append("|");
	//					}
	//				}
	//				String pipes = re.toString();
	//
	//				// If this optional had a star or anything in it, e.g. [*],
	//				// make it non-matching.
	//				pipes = pipes.replaceAll("\\(\\.\\+\\?\\)", "(?:.+?)");
	//				pipes = pipes.replaceAll("\\(\\d\\+\\?\\)", "(?:\\\\d+?)");
	//				pipes = pipes.replaceAll("\\(\\w\\+\\?\\)", "(?:\\\\w+?)");
	//
	//				// Put the new text in.
	//				pipes = "(?:" + pipes + "|(?:\\b|\\s)+)";
	//				regexp = regexp.replace(optional, pipes);
	//			}
	//		}
	//
	//		// Make \w more accurate for our purposes.
	//		regexp = regexp.replaceAll("\\\\w", "[A-Za-z]");
	//
	//		// Filter in arrays.
	//		if (regexp.indexOf("@") > -1) {
	//			// Match the array's name.
	//			Pattern reArray = Pattern.compile("\\@(.+?)\\b");
	//			Matcher mArray = reArray.matcher(regexp);
	//			while (mArray.find() == true) {
	//				String array = mArray.group(0);
	//				String name = mArray.group(1);
	//
	//				// Do we have an array by this name?
	//				if (arrays.containsKey(name)) {
	//					String[] values = ZzUtil.Sv2s(arrays.get(name));
	//					StringBuffer joined = new StringBuffer();
	//
	//					// Join the array.
	//					for (int i = 0; i < values.length; i++) {
	//						joined.append(values[i]);
	//						if (i < values.length - 1) {
	//							joined.append("|");
	//						}
	//					}
	//
	//					// Final contents...
	//					String rep = "(?:" + joined.toString() + ")";
	//					regexp = regexp.replace(array, rep);
	//				} else {
	//					// No array by this name.
	//					regexp = regexp.replace(array, "");
	//				}
	//			}
	//		}
	//
	//		// Filter in bot variables.
	//		if (regexp.indexOf("<bot") > -1) {
	//			Pattern reBot = Pattern.compile("<bot (.+?)>");
	//			Matcher mBot = reBot.matcher(regexp);
	//			while (mBot.find()) {
	//				String tag = mBot.group(0);
	//				String var = mBot.group(1);
	//				String value = vars.get(var).toLowerCase().replace("[^a-z0-9 ]+", "");
	//
	//				// Have this?
	//				if (vars.containsKey(var)) {
	//					regexp = regexp.replace(tag, value);
	//				} else {
	//					regexp = regexp.replace(tag, "undefined");
	//				}
	//			}
	//		}
	//
	//		// Filter in user variables.
	//		if (regexp.indexOf("<get") > -1) {
	//			Pattern reGet = Pattern.compile("<get (.+?)>");
	//			Matcher mGet = reGet.matcher(regexp);
	//			while (mGet.find()) {
	//				String tag = mGet.group(0);
	//				String var = mGet.group(1);
	//				String value = profile.get(var).toLowerCase().replaceAll("[^a-z0-9 ]+", "");
	//
	//				// Have this?
	//				regexp = regexp.replace(tag, value);
	//			}
	//		}
	//
	//		// Input and reply tags.
	//		regexp = regexp.replaceAll("<input>", "<input1>");
	//		regexp = regexp.replaceAll("<reply>", "<reply1>");
	//		if (regexp.indexOf("<input") > -1) {
	//			Pattern reInput = Pattern.compile("<input([0-9])>");
	//			Matcher mInput = reInput.matcher(regexp);
	//			while (mInput.find()) {
	//				String tag = mInput.group(0);
	//				int index = Integer.parseInt(mInput.group(1));
	//				String text = profile.getInput(index).toLowerCase().replaceAll("[^a-z0-9 ]+", "");
	//				regexp = regexp.replace(tag, text);
	//			}
	//		}
	//		if (regexp.indexOf("<reply") > -1) {
	//			Pattern reReply = Pattern.compile("<reply([0-9])>");
	//			Matcher mReply = reReply.matcher(regexp);
	//			while (mReply.find()) {
	//				String tag = mReply.group(0);
	//				int index = Integer.parseInt(mReply.group(1));
	//				String text = profile.getReply(index).toLowerCase().replaceAll("[^a-z0-9 ]+", "");
	//				regexp = regexp.replace(tag, text);
	//			}
	//		}
	//
	//		return regexp;
	//	}
	//
	//	/**
	//	 * Process reply tags.
	//	 *
	//	 * @param user    The name of the end user.
	//	 * @param profile The RiveScript client object holding the user's profile
	//	 * @param message The message sent by the user.
	//	 * @param reply   The bot's original reply including tags.
	//	 * @param vst     The vector of wildcards the user's message matched.
	//	 * @param vbst    The vector of wildcards in any @{code %Previous}.
	//	 * @param step    The current recursion depth limit.
	//	 */
	//	private String processTags(String user, ZzClient profile, String message, String reply,
	//			Vector<String> vst, Vector<String> vbst, int step) {
	//		// Pad the stars.
	//		Vector<String> vstars = new Vector<>();
	//		vstars.add("");
	//		vstars.addAll(vst);
	//		Vector<String> vbotstars = new Vector<>();
	//		vbotstars.add("");
	//		vbotstars.addAll(vbst);
	//
	//		// Set a default first star.
	//		if (vstars.size() == 1) {
	//			vstars.add("undefined");
	//		}
	//		if (vbotstars.size() == 1) {
	//			vbotstars.add("undefined");
	//		}
	//
	//		// Convert the stars into simple arrays.
	//		String[] stars = ZzUtil.Sv2s(vstars);
	//		String[] botstars = ZzUtil.Sv2s(vbotstars);
	//
	//		// Turn arrays into randomized sets.
	//		if (reply.indexOf("(@") > -1) {
	//			Pattern reArray = Pattern.compile("\\(@([A-Za-z0-9_]+)\\)");
	//			Matcher mArray = reArray.matcher(reply);
	//			while (mArray.find()) {
	//				String tag = mArray.group(0);
	//				String name = mArray.group(1);
	//				String result;
	//				if (arrays.containsKey(name)) {
	//					String[] values = ZzUtil.Sv2s(arrays.get(name));
	//					StringBuffer joined = new StringBuffer();
	//					// Join the array.
	//					for (int i = 0; i < values.length; i++) {
	//						joined.append(values[i]);
	//						if (i < values.length - 1) {
	//							joined.append("|");
	//						}
	//					}
	//					result = "{random}" + joined.toString() + "{/random}";
	//					reply = reply.replace(tag, result);
	//				}
	//			}
	//		}
	//
	//		// Shortcut tags.
	//		reply = reply.replaceAll("<person>", "{person}<star>{/person}");
	//		reply = reply.replaceAll("<@>", "{@<star>}");
	//		reply = reply.replaceAll("<formal>", "{formal}<star>{/formal}");
	//		reply = reply.replaceAll("<sentence>", "{sentence}<star>{/sentence}");
	//		reply = reply.replaceAll("<uppercase>", "{uppercase}<star>{/uppercase}");
	//		reply = reply.replaceAll("<lowercase>", "{lowercase}<star>{/lowercase}");
	//
	//		// Weight and star tags.
	//		reply = reply.replaceAll("\\{weight=\\d+\\}", ""); // Remove {weight}s
	//		reply = reply.replaceAll("<star>", stars[1]);
	//		reply = reply.replaceAll("<botstar>", botstars[1]);
	//		for (int i = 1; i < stars.length; i++) {
	//			reply = reply.replaceAll("<star" + i + ">", stars[i]);
	//		}
	//		for (int i = 1; i < botstars.length; i++) {
	//			reply = reply.replaceAll("<botstar" + i + ">", botstars[i]);
	//		}
	//		reply = reply.replaceAll("<(star|botstar)\\d+>", "");
	//
	//		// Input and reply tags.
	//		reply = reply.replaceAll("<input>", "<input1>");
	//		reply = reply.replaceAll("<reply>", "<reply1>");
	//		if (reply.indexOf("<input") > -1) {
	//			Pattern reInput = Pattern.compile("<input([0-9])>");
	//			Matcher mInput = reInput.matcher(reply);
	//			while (mInput.find()) {
	//				String tag = mInput.group(0);
	//				int index = Integer.parseInt(mInput.group(1));
	//				String text = profile.getInput(index).toLowerCase().replaceAll("[^a-z0-9 ]+", "");
	//				reply = reply.replace(tag, text);
	//			}
	//		}
	//		if (reply.indexOf("<reply") > -1) {
	//			Pattern reReply = Pattern.compile("<reply([0-9])>");
	//			Matcher mReply = reReply.matcher(reply);
	//			while (mReply.find()) {
	//				String tag = mReply.group(0);
	//				int index = Integer.parseInt(mReply.group(1));
	//				String text = profile.getReply(index).toLowerCase().replaceAll("[^a-z0-9 ]+", "");
	//				reply = reply.replace(tag, text);
	//			}
	//		}
	//
	//		// <id> and escape codes
	//		reply = reply.replaceAll("<id>", user);
	//		reply = reply.replaceAll("\\\\s", " ");
	//		reply = reply.replaceAll("\\\\n", "\n");
	//		reply = reply.replaceAll("\\\\", "\\");
	//		reply = reply.replaceAll("\\#", "#");
	//
	//		// {random} tag
	//		if (reply.indexOf("{random}") > -1) {
	//			Pattern reRandom = Pattern.compile("\\{random\\}(.+?)\\{\\/random\\}");
	//			Matcher mRandom = reRandom.matcher(reply);
	//			while (mRandom.find()) {
	//				String tag = mRandom.group(0);
	//				String[] candidates = mRandom.group(1).split("\\|");
	//				String chosen = candidates[rand.nextInt(candidates.length)];
	//				reply = reply.replace(tag, chosen);
	//			}
	//		}
	//
	//		// {!stream} tag
	//		if (reply.indexOf("{!") > -1) {
	//			Pattern reStream = Pattern.compile("\\{\\!(.+?)\\}");
	//			Matcher mStream = reStream.matcher(reply);
	//			while (mStream.find()) {
	//				String tag = mStream.group(0);
	//				String code = mStream.group(1);
	//				say("Stream new code in: " + code);
	//
	//				// Stream it.
	//				this.stream(code);
	//				reply = reply.replace(tag, "");
	//			}
	//		}
	//
	//		// Person substitutions & string formatting
	//		if (reply.indexOf("{person}") > -1 || reply.indexOf("{formal}") > -1 || reply.indexOf("{sentence}") > -1 ||
	//				reply.indexOf("{uppercase}") > -1 || reply.indexOf("{lowercase}") > -1) {
	//			String[] tags = {"person", "formal", "sentence", "uppercase", "lowercase"};
	//			for (int i = 0; i < tags.length; i++) {
	//				Pattern reTag = Pattern.compile("\\{" + tags[i] + "\\}(.+?)\\{\\/" + tags[i] + "\\}");
	//				Matcher mTag = reTag.matcher(reply);
	//				while (mTag.find()) {
	//					String tag = mTag.group(0);
	//					String text = mTag.group(1);
	//					if (tags[i].equals("person")) {
	//						// Run person substitutions.
	//						say("Run person substitutions: before: " + text);
	//						text = ZzUtil.substitute(person_s, person, text);
	//						say("After: " + text);
	//						reply = reply.replace(tag, text);
	//					} else {
	//						// String transform.
	//						text = stringTransform(tags[i], text);
	//						reply = reply.replace(tag, text);
	//					}
	//				}
	//			}
	//		}
	//
	//		// Handle all variable-related tags with an iterative regexp approach, to
	//		// allow for nesting of tags in arbitrary ways (think <set a=<get b>>)
	//		// Dummy out the <call> tags first, because we don't handle them right here.
	//		reply = reply.replaceAll("<call>", "{__call__}");
	//		reply = reply.replaceAll("</call>", "{/__call__}");
	//
	//		while (true) {
	//			// This regexp will match a <tag> which contains no other tag inside it,
	//			// i.e. in the case of <set a=<get b>> it will match <get b> but not the
	//			// <set> tag, on the first pass. The second pass will get the <set> tag,
	//			// and so on.
	//			Pattern reTag = Pattern.compile("<([^<]+?)>");
	//			Matcher mTag = reTag.matcher(reply);
	//			if (!mTag.find()) {
	//				break; // No remaining tags!
	//			}
	//
	//			String match = mTag.group(1);
	//			String[] parts = match.split(" ");
	//			String tag = parts[0].toLowerCase();
	//			String data = "";
	//			if (parts.length > 1) {
	//				data = ZzUtil.join(Arrays.copyOfRange(parts, 1, parts.length), " ");
	//			}
	//			String insert = "";
	//
	//			// Handle the tags.
	//			if (tag.equals("bot") || tag.equals("env")) {
	//				// <bot> and <env> tags are similar
	//				HashMap<String, String> target = tag.equals("bot") ? vars : globals;
	//				if (data.indexOf("=") > -1) {
	//					// Assigning a variable
	//					parts = data.split("=", 2);
	//					String name = parts[0];
	//					String value = parts[1];
	//					say("Set " + tag + " variable " + name + " = " + value);
	//					target.put(name, value);
	//				} else {
	//					// Getting a bot/env variable
	//					if (target.containsKey(data)) {
	//						insert = target.get(data);
	//					} else {
	//						insert = "undefined";
	//					}
	//				}
	//			} else if (tag.equals("set")) {
	//				// <set> user vars
	//				parts = data.split("=", 2);
	//				String name = parts[0];
	//				String value = parts[1];
	//				say("Set user var " + name + "=" + value);
	//				// Set the uservar.
	//				profile.set(name, value);
	//			} else if (tag.equals("add") || tag.equals("sub") || tag.equals("mult") || tag.equals("div")) {
	//				// Math operator tags
	//				parts = data.split("=");
	//				String name = parts[0];
	//				int result = 0;
	//
	//				// Initialize the variable?
	//				if (profile.get(name).equals("undefined")) {
	//					profile.set(name, "0");
	//				}
	//
	//				try {
	//					int value = Integer.parseInt(parts[1]);
	//					try {
	//						result = Integer.parseInt(profile.get(name));
	//
	//						// Run the operation.
	//						if (tag.equals("add")) {
	//							result += value;
	//						} else if (tag.equals("sub")) {
	//							result -= value;
	//						} else if (tag.equals("mult")) {
	//							result *= value;
	//						} else {
	//							// Don't divide by zero.
	//							if (value == 0) {
	//								insert = "[ERR: Can't divide by zero!]";
	//							}
	//							result /= value;
	//						}
	//					} catch (NumberFormatException e) {
	//						insert = "[ERR: Math can't \"" + tag + "\" non-numeric variable " + name + "]";
	//					}
	//				} catch (NumberFormatException e) {
	//					insert = "[ERR: Math can't \"" + tag + "\" non-numeric value " + parts[1] + "]";
	//				}
	//
	//				// No errors?
	//				if (insert.equals("")) {
	//					profile.set(name, Integer.toString(result));
	//				}
	//			} else if (tag.equals("get")) {
	//				// Get the user var.
	//				insert = profile.get(data);
	//			} else {
	//				// Unrecognized tag, preserve it
	//				insert = "\\x00" + match + "\\x01";
	//			}
	//
	//			reply = reply.replace(mTag.group(0), insert);
	//		}
	//
	//		// Recover mangled HTML-like tags
	//		reply = reply.replaceAll("\\\\x00", "<");
	//		reply = reply.replaceAll("\\\\x01", ">");
	//
	//		// {topic} tag
	//		if (reply.indexOf("{topic=") > -1) {
	//			Pattern reTopic = Pattern.compile("\\{topic=(.+?)\\}");
	//			Matcher mTopic = reTopic.matcher(reply);
	//			while (mTopic.find()) {
	//				String tag = mTopic.group(0);
	//				String topic = mTopic.group(1);
	//				say("Set user's topic to: " + topic);
	//				profile.set("topic", topic);
	//				reply = reply.replace(tag, "");
	//			}
	//		}
	//
	//		// {@redirect} tag
	//		if (reply.indexOf("{@") > -1) {
	//			Pattern reRed = Pattern.compile("\\{@([^\\}]*?)\\}");
	//			Matcher mRed = reRed.matcher(reply);
	//			while (mRed.find()) {
	//				String tag = mRed.group(0);
	//				String target = mRed.group(1).trim();
	//
	//				// Do the reply redirect.
	//				String subreply = this.reply(user, target, false, step + 1);
	//				reply = reply.replace(tag, subreply);
	//			}
	//		}
	//
	//		// <call> tag
	//		reply = reply.replaceAll("\\{__call__}", "<call>");
	//		reply = reply.replaceAll("\\{/__call__}", "</call>");
	//		if (reply.indexOf("<call>") > -1) {
	//			Pattern reCall = Pattern.compile("<call>(.+?)<\\/call>");
	//			Matcher mCall = reCall.matcher(reply);
	//			while (mCall.find()) {
	//				String tag = mCall.group(0);
	//				String data = mCall.group(1);
	//				String[] parts = data.split(" ");
	//				String name = parts[0];
	//				Vector<String> args = new Vector<String>();
	//				for (int i = 1; i < parts.length; i++) {
	//					args.add(parts[i]);
	//				}
	//
	//				// See if we know of this object.
	//				if (objects.containsKey(name)) {
	//					// What language handles it?
	//					String lang = objects.get(name);
	//					String result = handlers.get(lang).onCall(name, user, ZzUtil.Sv2s(args));
	//					reply = reply.replace(tag, result);
	//				} else {
	//					reply = reply.replace(tag, "[ERR: Object Not Found]");
	//				}
	//			}
	//		}
	//
	//		return reply;
	//	}
	//
	//	/**
	//	 * Reformats a {@link String} in a certain way: formal, uppercase, lowercase, sentence.
	//	 *
	//	 * @param format The format you want the string in.
	//	 * @param text   The text to format.
	//	 */
	//	private String stringTransform(String format, String text) {
	//		if (format.equals("uppercase")) {
	//			return text.toUpperCase();
	//		} else if (format.equals("lowercase")) {
	//			return text.toLowerCase();
	//		} else if (format.equals("formal")) {
	//			// Capitalize Each First Letter
	//			String[] words = text.split(" ");
	//			say("wc: " + words.length);
	//			for (int i = 0; i < words.length; i++) {
	//				say("word: " + words[i]);
	//				String[] letters = words[i].split("");
	//				say("cc: " + letters.length);
	//				if (letters.length > 1) {
	//					letters[0] = letters[0].toUpperCase();
	//					words[i] = ZzUtil.join(letters, "");
	//					say("new word: " + words[i]);
	//				}
	//			}
	//			return ZzUtil.join(words, " ");
	//		} else if (format.equals("sentence")) {
	//			// Uppercase the first letter of the first word.
	//			String[] letters = text.split("");
	//			if (letters.length > 1) {
	//				letters[0] = letters[0].toUpperCase();
	//			}
	//			return ZzUtil.join(letters, "");
	//		} else {
	//			return "[ERR: Unknown String Transform " + format + "]";
	//		}
	//	}
	//
	//	/**
	//	 * Formats the user's message to begin reply matching. Lowercases it, runs substitutions,
	//	 * and neutralizes what's left.
	//	 *
	//	 * @param message The input message to format.
	//	 */
	//	private String formatMessage(String message) {
	//		// Lowercase it first.
	//		message = message.toLowerCase();
	//
	//		// Run substitutions.
	//		message = ZzUtil.substitute(subs_s, subs, message);
	//
	//		// Sanitize what's left.
	//		message = message.replaceAll("[^a-z0-9_ ]", "");
	//		return message;
	//	}
}
