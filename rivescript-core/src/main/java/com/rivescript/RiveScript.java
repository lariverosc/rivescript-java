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
import com.rivescript.sorting.SortTrack;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.rivescript.regexp.Regexp.RE_INHERITS;
import static com.rivescript.regexp.Regexp.RE_WEIGHT;
import static com.rivescript.util.StringUtils.byLengthReverse;
import static com.rivescript.util.StringUtils.wordCount;
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

			// Collect a list of all the triggers we're going to worry about.
			// If this topic inherits another topic, we need to recursively add those to the list as well.
			List<SortedTriggerEntry> allTriggers = getTopicTriggers(topic);

			// Sort these triggers.
			sorted.getTopics().put(topic, sortTriggerSet(allTriggers));

			// Get all of the %Previous triggers for this topic.
			List<SortedTriggerEntry> thatTriggers = getTopicTriggers(topic, true);

			// And sort them, too.
			sorted.getThats().put(topic, sortTriggerSet(thatTriggers, false));
		}

		// Sort the substitution lists.
		sorted.setSub(sortList(sub.keySet()));
		sorted.setPerson(sortList(person.keySet()));
	}

	private List<SortedTriggerEntry> getTopicTriggers(String topic) {
		return getTopicTriggers(topic, false, 0, 0, false);
	}

	private List<SortedTriggerEntry> getTopicTriggers(String topic, boolean thats) {
		return getTopicTriggers(topic, thats, 0, 0, false);
	}

	/**
	 * TODO
	 * <p>
	 * Keep in mind here that there is a difference between 'includes' and
	 * 'inherits' -- topics that inherit other topics are able to OVERRIDE
	 * triggers that appear in the inherited topic. This means that if the top
	 * topic has a trigger of simply '*', then NO triggers are capable of
	 * matching in ANY inherited topic, because even though * has the lowest
	 * priority, it has an automatic priority over all inherited topics.
	 * <p>
	 * The getTopicTriggers method takes this into account. All topics that
	 * inherit other topics will have their triggers prefixed with a fictional
	 * {inherits} tag, which would start at {inherits=0} and increment if this
	 * topic has other inheriting topics. So we can use this tag to make sure
	 * topics that inherit things will have their triggers always be on top of
	 * the stack, from inherits=0 to inherits=n.
	 * <p>
	 * Important info about the depth vs. inheritance params to this function:
	 * depth increments by 1 each time this function recursively calls itself.
	 * inheritance increments by 1 only when this topic inherits another topic.
	 * <p>
	 * This way, '> topic alpha includes beta inherits gamma' will have this
	 * effect:
	 * alpha and beta's triggers are combined together into one matching pool,
	 * and then those triggers have higher priority than gamma's.
	 * <p>
	 * The inherited option is true if this is a recursive call, from a topic
	 * that inherits other topics. This forces the {inherits} tag to be added to
	 * the triggers. This only applies when the top topic 'includes' another
	 * topic.
	 *
	 * @param topic
	 * @param thats
	 * @param depth
	 * @param inheritance
	 * @param inherited
	 * @return
	 */
	private List<SortedTriggerEntry> getTopicTriggers(String topic, boolean thats, int depth, int inheritance, boolean inherited) {
		// Break if we're in too deep.
		if (depth > this.depth) {
			logger.warn("Deep recursion while scanning topic inheritance!");
			return new ArrayList<>();
		}

		logger.debug("Collecting trigger list for topic {} (depth={}; inheritance={}; inherited={})", topic, depth, inheritance, inherited);

		// Collect an array of triggers to return.
		List<SortedTriggerEntry> triggers = new ArrayList<>();

		// Get those that exist in this topic directly.
		List<SortedTriggerEntry> inThisTopic = new ArrayList<>();
		if (!thats) {
			// The non-thats structure is: {topics}->[ array of triggers ]
			if (topics.containsKey(topic)) {
				for (Trigger trigger : topics.get(topic).getTriggers()) {
					SortedTriggerEntry entry = new SortedTriggerEntry(trigger.getTrigger(), trigger);
					inThisTopic.add(entry);
				}
			}
		} else {
			// The 'thats' structure is: {topic}->{cur trig}->{prev trig}->{trigger info}
			if (this.thats.containsKey(topic)) {
				for (Map<String, Trigger> currentTrigger : this.thats.get(topic).values()) {
					for (Trigger previous : currentTrigger.values()) {
						SortedTriggerEntry entry = new SortedTriggerEntry(previous.getTrigger(), previous);
						inThisTopic.add(entry);
					}
				}
			}
		}

		// Does this topic include others?
		if (includes.containsKey(topic)) {
			for (String includes : this.includes.get(topic).keySet()) {
				logger.debug("Topic {} includes {}", topic, includes);
				triggers.addAll(getTopicTriggers(includes, thats, depth + 1, inheritance + 1, false));
			}
		}

		// Does this topic inherit others?
		if (inherits.containsKey(topic)) {
			for (String inherits : this.inherits.get(topic).keySet()) {
				logger.debug("Topic {} inherits {}", topic, inherits);
				triggers.addAll(getTopicTriggers(inherits, thats, depth + 1, inheritance + 1, true));
			}
		}

		// Collect the triggers for *this* topic. If this topic inherits any other topics, it means that this topic's triggers have higher
		// priority than those in any inherited topics. Enforce this with an {inherits} tag.
		if ((inherits.containsKey(topic) && this.inherits.get(topic).size() > 0) || inherited) {
			for (SortedTriggerEntry trigger : inThisTopic) {
				logger.debug("Prefixing trigger with {inherits={}} {}", inheritance, trigger.getTrigger());
				String label = String.format("{inherits=%d}%s", inheritance, trigger.getTrigger());
				triggers.add(new SortedTriggerEntry(label, trigger.getPointer()));
			}
		} else {
			for (SortedTriggerEntry trigger : inThisTopic) {
				triggers.add(new SortedTriggerEntry(trigger.getTrigger(), trigger.getPointer()));
			}
		}

		return triggers;
	}

	/**
	 * Sorts a group of triggers in an optimal sorting order.
	 *
	 * @param triggers the triggers to sort
	 * @return the sorted triggers
	 * @see #sortTriggerSet(List, boolean)
	 */
	private List<SortedTriggerEntry> sortTriggerSet(List<SortedTriggerEntry> triggers) {
		return sortTriggerSet(triggers, true);
	}

	/**
	 * Sorts a group of triggers in an optimal sorting order.
	 * <p>
	 * This function has two use cases:
	 * <p>
	 * <ol>
	 * <li>Create a sort buffer for "normal" (matchable) triggers, which are triggers that are NOT accompanied by a %Previous tag.
	 * <li>Create a sort buffer for triggers that had %Previous tags.
	 * </ol>
	 * <p>
	 * Use the {@code excludePrevious} parameter to control which one is being done.
	 * This function will return a list of {@link SortedTriggerEntry} items, and it's intended to have no duplicate trigger patterns
	 * (unless the source RiveScript code explicitly uses the same duplicate pattern twice, which is a user error).
	 *
	 * @param triggers        the triggers to sort
	 * @param excludePrevious TODO
	 * @return the sorted triggers
	 */
	private List<SortedTriggerEntry> sortTriggerSet(List<SortedTriggerEntry> triggers, boolean excludePrevious) {
		// Create a priority map, of priority numbers -> their triggers.
		Map<Integer, List<SortedTriggerEntry>> prior = new HashMap<>();

		// Go through and bucket each trigger by weight (priority).
		for (SortedTriggerEntry trigger : triggers) {
			if (excludePrevious && trigger.getPointer().getPrevious() != null) {
				continue;
			}

			// Check the trigger text for any {weight} tags, default being 0.
			int weight = 0;
			Matcher m = RE_WEIGHT.matcher(trigger.getTrigger());
			if (m.find()) {
				weight = Integer.parseInt(m.group(1));
			}

			// First trigger of this priority? Initialize the weight map.
			if (!prior.containsKey(weight)) {
				prior.put(weight, new ArrayList<SortedTriggerEntry>());
			}

			prior.get(weight).add(trigger);
		}

		// Keep a running list of sorted triggers for this topic.
		List<SortedTriggerEntry> running = new ArrayList<>();

		// Sort the priorities with the highest number first.
		List<Integer> sortedPriorities = new ArrayList<>();
		for (Integer k : prior.keySet()) {
			sortedPriorities.add(k);
		}
		Collections.sort(sortedPriorities);
		Collections.reverse(sortedPriorities);

		// Go through each priority set.
		for (Integer p : sortedPriorities) {
			logger.debug("Sorting triggers with priority {}", p);

			// So, some of these triggers may include an {inherits} tag, if they came from a topic which inherits another topic.
			// Lower inherits values mean higher priority on the stack.
			// Triggers that have NO inherits value at all (which will default to -1),
			// will be moved to the END of the stack at the end (have the highest number/lowest priority).
			int inherits = -1;        // -1 means no {inherits} tag
			int highestInherits = -1; // Highest number seen so far

			// Loop through and categorize these triggers.
			Map<Integer, SortTrack> track = new HashMap<>();
			track.put(inherits, new SortTrack());

			// Loop through all the triggers.
			for (SortedTriggerEntry trigger : prior.get(p)) {
				String pattern = trigger.getTrigger();
				logger.debug("Looking at trigger: {}", pattern);

				// See if the trigger has an {inherits} tag.
				Matcher m = RE_INHERITS.matcher(pattern);
				if (m.find()) {
					inherits = Integer.parseInt(m.group(1));
					if (inherits > highestInherits) {
						highestInherits = inherits;
					}
					logger.debug("Trigger belongs to a topic that inherits other topics. Level={}", inherits);
					pattern = pattern.replaceAll("\\{inherits=\\d+\\}", "");
					trigger.setTrigger(pattern);
				} else {
					inherits = -1;
				}

				// If this is the first time we've seen this inheritance level, initialize its sort track structure.
				if (!track.containsKey(inherits)) {
					track.put(inherits, new SortTrack());
				}

				// Start inspecting the trigger's contents.
				if (pattern.contains("_")) {
					// Alphabetic wildcard included.
					int count = wordCount(pattern, false);
					logger.debug("Has a _ wildcard with {} words", count);
					if (count > 0) {
						if (!track.get(inherits).getAlpha().containsKey(count)) {
							track.get(inherits).getAlpha().put(count, new ArrayList<SortedTriggerEntry>());
						}
						track.get(inherits).getAlpha().get(count).add(trigger);
					} else {
						track.get(inherits).getUnder().add(trigger);
					}
				} else if (pattern.contains("#")) {
					// Numeric wildcard included.
					int count = wordCount(pattern, false);
					logger.debug("Has a # wildcard with {} words", count);
					if (count > 0) {
						if (!track.get(inherits).getNumber().containsKey(count)) {
							track.get(inherits).getNumber().put(count, new ArrayList<SortedTriggerEntry>());
						}
						track.get(inherits).getNumber().get(count).add(trigger);
					} else {
						track.get(inherits).getPound().add(trigger);
					}
				} else if (pattern.contains("*")) {
					// Wildcard included.
					int count = wordCount(pattern, false);
					logger.debug("Has a * wildcard with {} words", count);
					if (count > 0) {
						if (!track.get(inherits).getWild().containsKey(count)) {
							track.get(inherits).getWild().put(count, new ArrayList<SortedTriggerEntry>());
						}
						track.get(inherits).getWild().get(count).add(trigger);
					} else {
						track.get(inherits).getStar().add(trigger);
					}
				} else if (pattern.contains("[")) {
					// Optionals included.
					int count = wordCount(pattern, false);
					logger.debug("Has optionals with {} words", count);
					if (!track.get(inherits).getOption().containsKey(count)) {
						track.get(inherits).getOption().put(count, new ArrayList<SortedTriggerEntry>());
					}
					track.get(inherits).getOption().get(count).add(trigger);
				} else {
					// Totally atomic.
					int count = wordCount(pattern, false);
					logger.debug("Totally atomic trigger with {} words", count);
					if (!track.get(inherits).getAtomic().containsKey(count)) {
						track.get(inherits).getAtomic().put(count, new ArrayList<SortedTriggerEntry>());
					}
					track.get(inherits).getAtomic().get(count).add(trigger);
				}
			}

			// Move the no-{inherits} triggers to the bottom of the stack.
			track.put(highestInherits + 1, track.get(-1));
			track.remove(-1);

			// Sort the track from the lowest to the highest.
			List<Integer> trackSorted = new ArrayList<>();
			for (Integer k : track.keySet()) {
				trackSorted.add(k);
			}
			Collections.sort(trackSorted);

			// Go through each priority level from greatest to smallest.
			for (Integer ip : trackSorted) {
				logger.debug("ip={}", ip);

				// Sort each of the main kinds of triggers by their word counts.
				running.addAll(sortByWords(track.get(ip).getAtomic()));
				running.addAll(sortByWords(track.get(ip).getOption()));
				running.addAll(sortByWords(track.get(ip).getAlpha()));
				running.addAll(sortByWords(track.get(ip).getNumber()));
				running.addAll(sortByWords(track.get(ip).getWild()));

				// Add the single wildcard triggers, sorted by length.
				running.addAll(sortByLength(track.get(ip).getUnder()));
				running.addAll(sortByLength(track.get(ip).getPound()));
				running.addAll(sortByLength(track.get(ip).getStar()));
			}
		}

		return running;
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
			Collections.sort(sortedLengths, byLengthReverse());
			for (String item : sortedLengths) {
				output.add(item);
			}
		}

		return output;
	}

	/**
	 * Sorts a set of triggers by word count and overall length.
	 * <p>
	 * This is a helper function for sorting the {@code atomic}, {@code option}, {@code alpha}, {@code number} and
	 * {@code wild} attributes of the {@link SortTrack} and adding them to the running sort buffer in that specific order.
	 *
	 * @param triggers the triggers to sort
	 * @return the sorted triggers
	 */
	private List<SortedTriggerEntry> sortByWords(Map<Integer, List<SortedTriggerEntry>> triggers) {
		// Sort the triggers by their word counts from greatest to smallest.
		List<Integer> sortedWords = new ArrayList<>();
		for (Integer wc : triggers.keySet()) {
			sortedWords.add(wc);
		}
		Collections.sort(sortedWords);
		Collections.reverse(sortedWords);

		List<SortedTriggerEntry> sorted = new ArrayList<>();

		for (Integer wc : sortedWords) {
			// Triggers with equal word lengths should be sorted by overall trigger length.
			List<String> sortedPatterns = new ArrayList<>();
			Map<String, List<SortedTriggerEntry>> patternMap = new HashMap<>();

			for (SortedTriggerEntry trigger : triggers.get(wc)) {
				sortedPatterns.add(trigger.getTrigger());
				if (!patternMap.containsKey(trigger.getTrigger())) {
					patternMap.put(trigger.getTrigger(), new ArrayList<SortedTriggerEntry>());
				}
				patternMap.get(trigger.getTrigger()).add(trigger);
			}
			Collections.sort(sortedPatterns, byLengthReverse());

			// Add the triggers to the sorted triggers bucket.
			for (String pattern : sortedPatterns) {
				sorted.addAll(patternMap.get(pattern));
			}
		}

		return sorted;
	}

	/**
	 * Sorts a set of triggers purely by character length.
	 * <p>
	 * This is like {@link #sortByWords(Map)}, but it's intended for triggers that consist solely of wildcard-like symbols with no real words.
	 * For example a trigger of {@code * * *} qualifies for this, and it has no words,
	 * so we sort by length so it gets a higher priority than simply {@code *}.
	 *
	 * @param triggers the triggers to sort
	 * @return the sorted triggers
	 */
	private List<SortedTriggerEntry> sortByLength(List<SortedTriggerEntry> triggers) {
		List<String> sortedPatterns = new ArrayList<>();
		Map<String, List<SortedTriggerEntry>> patternMap = new HashMap<>();
		for (SortedTriggerEntry trigger : triggers) {
			sortedPatterns.add(trigger.getTrigger());
			if (!patternMap.containsKey(trigger.getTrigger())) {
				patternMap.put(trigger.getTrigger(), new ArrayList<SortedTriggerEntry>());
			}
			patternMap.get(trigger.getTrigger()).add(trigger);
		}
		Collections.sort(sortedPatterns, byLengthReverse());

		// Only loop through unique patterns.
		Map<String, Boolean> patternSet = new HashMap<>();

		List<SortedTriggerEntry> sorted = new ArrayList<>();

		// Add them to the sorted triggers bucket.
		for (String pattern : sortedPatterns) {
			if (patternSet.containsKey(pattern) && patternSet.get(pattern)) {
				continue;
			}
			patternSet.put(pattern, true);
			sorted.addAll(patternMap.get(pattern));
		}

		return sorted;
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
	//		// Special globals // TODO MARCEL NEED TO COPY THIS BEHAVIOUR!
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
}
