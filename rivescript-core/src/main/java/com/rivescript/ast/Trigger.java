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

import java.util.ArrayList;
import java.util.List;

/**
 * TODO
 *
 * @author Marcel Overdijk
 * @since 0.8
 */
public class Trigger {

	private String trigger;
	private List<String> reply = new ArrayList<>();
	private List<String> condition = new ArrayList<>();
	private String redirect;
	private String previous;

	public String getTrigger() {
		return trigger;
	}

	public void setTrigger(String trigger) {
		this.trigger = trigger;
	}

	public List<String> getReply() {
		return reply;
	}

	public void setReply(List<String> reply) {
		this.reply = reply;
	}

	public List<String> getCondition() {
		return condition;
	}

	public void setCondition(List<String> condition) {
		this.condition = condition;
	}

	public String getRedirect() {
		return redirect;
	}

	public void setRedirect(String redirect) {
		this.redirect = redirect;
	}

	public String getPrevious() {
		return previous;
	}

	public void setPrevious(String previous) {
		this.previous = previous;
	}

	public void addReply(String reply) {
		this.reply.add(reply);
	}

	public void addCondition(String condition) {
		this.condition.add(condition);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Trigger that = (Trigger) o;
		if (trigger != null ? !trigger.equals(that.trigger) : that.trigger != null) {
			return false;
		}
		if (reply != null ? !reply.equals(that.reply) : that.reply != null) {
			return false;
		}
		if (condition != null ? !condition.equals(that.condition) : that.condition != null) {
			return false;
		}
		if (redirect != null ? !redirect.equals(that.redirect) : that.redirect != null) {
			return false;
		}
		return previous != null ? previous.equals(that.previous) : that.previous == null;
	}

	@Override
	public int hashCode() {
		int result = trigger != null ? trigger.hashCode() : 0;
		result = 31 * result + (reply != null ? reply.hashCode() : 0);
		result = 31 * result + (condition != null ? condition.hashCode() : 0);
		result = 31 * result + (redirect != null ? redirect.hashCode() : 0);
		result = 31 * result + (previous != null ? previous.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return "Trigger{" +
				"trigger='" + trigger + '\'' +
				", reply=" + reply +
				", condition=" + condition +
				", redirect='" + redirect + '\'' +
				", previous='" + previous + '\'' +
				'}';
	}
}
