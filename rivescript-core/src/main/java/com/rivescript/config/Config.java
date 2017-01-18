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

package com.rivescript.config;

import com.rivescript.RiveScript;
import com.rivescript.session.SessionManager;

import java.util.Map;

/**
 * User-configurable properties of the {@link RiveScript} interpreter.
 *
 * @author Marcel Overdijk
 */
public class Config {

	/**
	 * The default recursion depth limit.
	 */
	public static int DEFAULT_DEPTH = 50;

	private boolean strict;
	private boolean utf8;
	private boolean forceCase;
	private int depth = DEFAULT_DEPTH;
	private SessionManager sessionManager;
	private Map<String, String> errors;

	private Config() {
	}

	public boolean isStrict() {
		return strict;
	}

	public boolean isUtf8() {
		return utf8;
	}

	public boolean isForceCase() {
		return forceCase;
	}

	public int getDepth() {
		return depth;
	}

	public SessionManager getSessionManager() {
		return sessionManager;
	}

	public Map<String, String> getErrors() {
		return errors;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Config config = (Config) o;
		if (strict != config.strict) {
			return false;
		}
		if (utf8 != config.utf8) {
			return false;
		}
		if (forceCase != config.forceCase) {
			return false;
		}
		if (depth != config.depth) {
			return false;
		}
		if (sessionManager != null ? !sessionManager.equals(config.sessionManager) : config.sessionManager != null) {
			return false;
		}
		return errors != null ? errors.equals(config.errors) : config.errors == null;
	}

	@Override public int hashCode() {
		int result = (strict ? 1 : 0);
		result = 31 * result + (utf8 ? 1 : 0);
		result = 31 * result + (forceCase ? 1 : 0);
		result = 31 * result + depth;
		result = 31 * result + (sessionManager != null ? sessionManager.hashCode() : 0);
		result = 31 * result + (errors != null ? errors.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return "Config{" +
				"strict=" + strict +
				", utf8=" + utf8 +
				", forceCase=" + forceCase +
				", depth=" + depth +
				", sessionManager=" + sessionManager +
				", errors=" + errors +
				'}';
	}

	public ConfigBuilder toBuilder() {
		return newBuilder()
				.strict(this.strict)
				.utf8(this.utf8)
				.forceCase(this.forceCase)
				.depth(this.depth)
				.sessionManager(this.sessionManager)
				.errors(this.errors);
	}

	public static Config basic() {
		return ConfigBuilder.basic().build();
	}

	public static Config utf8() {
		return ConfigBuilder.utf8().build();
	}

	public static ConfigBuilder newBuilder() {
		return new ConfigBuilder();
	}

	public static final class ConfigBuilder {

		private boolean strict;
		private boolean utf8;
		private boolean forceCase;
		private int depth = DEFAULT_DEPTH;
		private SessionManager sessionManager;
		private Map<String, String> errors;

		private ConfigBuilder() {
		}

		public ConfigBuilder strict(boolean strict) {
			this.strict = strict;
			return this;
		}

		public ConfigBuilder utf8(boolean utf8) {
			this.utf8 = utf8;
			return this;
		}

		public ConfigBuilder forceCase(boolean forceCase) {
			this.forceCase = forceCase;
			return this;
		}

		public ConfigBuilder depth(int depth) {
			this.depth = depth;
			return this;
		}

		public ConfigBuilder sessionManager(SessionManager sessionManager) {
			this.sessionManager = sessionManager;
			return this;
		}

		public ConfigBuilder errors(Map<String, String> errors) {
			this.errors = errors;
			return this;
		}

		public Config build() {
			Config config = new Config();
			config.utf8 = this.utf8;
			config.strict = this.strict;
			config.depth = this.depth;
			config.sessionManager = this.sessionManager;
			config.errors = this.errors;
			config.forceCase = this.forceCase;
			return config;
		}

		public static ConfigBuilder basic() {
			return new ConfigBuilder().strict(true);
		}

		public static ConfigBuilder utf8() {
			return basic().utf8(true);
		}
	}
}
