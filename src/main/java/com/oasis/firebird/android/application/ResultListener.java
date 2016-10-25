package com.oasis.firebird.android.application;


public interface ResultListener {
	
	enum Result {POSISTIVE, NEGATIVE, NEUTRAL}
	void onResultReceived(Result result);

}
