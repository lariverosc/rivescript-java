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

import java.util.HashMap;
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

	/**
	 * Returns whether strict syntax checking is enabled.
	 *
	 * @return whether strict syntax checking is enabled
	 */
	public boolean isStrict() {
		return strict;
	}

	/**
	 * Returns whether UTF-8 mode is enabled for user messages and triggers.
	 *
	 * @return whether UTF-8 mode is enabled for user messages and triggers
	 */
	public boolean isUtf8() {
		return utf8;
	}

	/**
	 * Returns whether forcing triggers to lowercase is enabled.
	 *
	 * @return whether forcing triggers to lowercase is enabled
	 */
	public boolean isForceCase() {
		return forceCase;
	}

	/**
	 * Returns the recursion depth limit.
	 *
	 * @return the recursion depth limit
	 */
	public int getDepth() {
		return depth;
	}

	/**
	 * Returns the {@link SessionManager} for user variables.
	 *
	 * @return the session manager for user variables
	 */
	public SessionManager getSessionManager() {
		return sessionManager;
	}

	/**
	 * Returns the custom error message overrides.
	 *
	 * @return the custom error message overrides
	 */
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

	@Override
	public int hashCode() {
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

	/**
	 * Converts this {@link Config} instance to a {@link Builder}.
	 *
	 * @return the config builder
	 */
	public Builder toBuilder() {
		return newBuilder()
				.strict(this.strict)
				.utf8(this.utf8)
				.forceCase(this.forceCase)
				.depth(this.depth)
				.sessionManager(this.sessionManager)
				.errors(this.errors);
	}

	/**
	 * Creates a basic config.
	 *
	 * @return the config
	 */
	public static Config basic() {
		return Builder.basic().build();
	}

	/**
	 * Creates a basic config with UTF-8 mode enabled.
	 *
	 * @return the config
	 */
	public static Config utf8() {
		return Builder.utf8().build();
	}

	/**
	 * Creates a new {@link Builder}.
	 *
	 * @return the config builder
	 */
	public static Builder newBuilder() {
		return new Builder();
	}

	/**
	 * Builder for {@link Config}.
	 */
	public static final class Builder {

		private boolean strict;
		private boolean utf8;
		private boolean forceCase;
		private int depth = DEFAULT_DEPTH;
		private SessionManager sessionManager;
		private Map<String, String> errors;

		private Builder() {
		}

		/**
		 * Sets whether strict syntax checking is enabled.
		 *
		 * @param strict whether strict syntax checking is enabled
		 * @return this config builder
		 */
		public Builder strict(boolean strict) {
			this.strict = strict;
			return this;
		}

		/**
		 * Sets whether UTF-8 mode is enabled.
		 *
		 * @param utf8 whether UTF-8 is enabled
		 * @return this config builder
		 */
		public Builder utf8(boolean utf8) {
			this.utf8 = utf8;
			return this;
		}

		/**
		 * Sets whether forcing triggers to lowercase is enabled.
		 *
		 * @param forceCase whether forcing triggers to lowercase is enabled
		 * @return this config builder
		 */
		public Builder forceCase(boolean forceCase) {
			this.forceCase = forceCase;
			return this;
		}

		/**
		 * Sets the recursion depth limit.
		 *
		 * @param depth the recursion depth limit
		 * @return this config builder
		 */
		public Builder depth(int depth) {
			this.depth = depth;
			return this;
		}

		/**
		 * Sets the {@link SessionManager} for user variables.
		 *
		 * @param sessionManager the session manager
		 * @return this config builder
		 */
		public Builder sessionManager(SessionManager sessionManager) {
			this.sessionManager = sessionManager;
			return this;
		}

		/**
		 * Sets the custom error message overrides.
		 *
		 * @param errors the custom error message overrides
		 * @return this config builder
		 */
		public Builder errors(Map<String, String> errors) {
			this.errors = errors;
			return this;
		}

		/**
		 * Adds a custom error message override.
		 *
		 * @param key the key of the error message
		 * @param value the custom error message
		 * @return this config builder
		 */
		public Builder addError(String key, String value) {
			if (this.errors == null) {
				this.errors = new HashMap<>();
			}
			this.errors.put(key, value);
			return this;
		}

		/**
		 * Builds the config.
		 *
		 * @return the config
		 */
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

		/**
		 * Creates a basic config builder.
		 *
		 * @return the config builder
		 */
		public static Builder basic() {
			return new Builder().strict(true);
		}

		/**
		 * Creates a basic config builder with UTF-8 mode enabled.
		 *
		 * @return the config builder
		 */
		public static Builder utf8() {
			return basic().utf8(true);
		}
	}
}
