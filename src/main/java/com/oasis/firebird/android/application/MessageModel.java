package com.oasis.firebird.android.application;


public class MessageModel {

	private String title;
	private String message;
	private String positive;
	private String negative;
	private String neutral;
	private Boolean allowSkip;
	private String preference;

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getPositive() {
		return positive;
	}

	public void setPositive(String positive) {
		this.positive = positive;
	}

	public String getNegative() {
		return negative;
	}

	public void setNegative(String negative) {
		this.negative = negative;
	}

	public String getNeutral() {
		return neutral;
	}

	public void setNeutral(String neutral) {
		this.neutral = neutral;
	}

	public Boolean getAllowSkip() {
		return allowSkip;
	}

	public void setAllowSkip(Boolean showSkip) {
		this.allowSkip = showSkip;
	}

	public String getPreference() {
		return preference;
	}

	public void setPreference(String preference) {
		this.preference = preference;
	}

	@Override
	public String toString() {
		return "MessageModel [title=" + title + ", message=" + message + ", positive=" + positive + ", negative=" + negative + ", neutral=" + neutral + ", allowSkip=" + allowSkip + ", preference="
				+ preference + "]";
	}

}
