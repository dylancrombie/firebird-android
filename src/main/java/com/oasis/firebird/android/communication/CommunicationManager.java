package com.oasis.firebird.android.communication;

import android.util.Log;

import com.oasis.firebird.communication.ClientException;
import com.oasis.firebird.communication.CommunicationInterface;
import com.oasis.firebird.core.ErrorMessage;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@Deprecated
public class CommunicationManager implements CommunicationInterface {

	public static final String ERROR_TAG = "Error";
	public static final String CONNECTION_TAG = "Connection failed";
	public static final String CONNECTION_MESSAGE = "Connection time out";
	public static final String JSON_PARSE_EXCEPTION = "An error occured while reading the information from the server.";
	public static final String IO_EXEPTION = "An unexpected error has occured, please try again.";

	private String type = "application/json";
	private static final int TIMEOUT_MILLISEC = 120000;
//	private static final int TIMEOUT_MILLISEC = 1000;
	private String baseUrl;
	private DefaultHttpClient httpclient;
	private BasicCookieStore cookieStore;
	private BasicHttpContext localContext;

	public CommunicationManager() {

		try {

			HttpParams httpParams = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(httpParams, TIMEOUT_MILLISEC);
			HttpConnectionParams.setSoTimeout(httpParams, TIMEOUT_MILLISEC);

	        SSLSocketFactory sslSocketFactory = SSLSocketFactory.getSocketFactory();
	        sslSocketFactory.setHostnameVerifier((X509HostnameVerifier) SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

			SchemeRegistry registry = new SchemeRegistry();
			registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
			registry.register(new Scheme("https", sslSocketFactory, 443));

			ClientConnectionManager mgr = new ThreadSafeClientConnManager(httpParams, registry);
			httpclient = new DefaultHttpClient(mgr, httpParams);

			cookieStore = new BasicCookieStore();
			localContext = new BasicHttpContext();
			localContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void setupConfigurationSettings(String url) {

		baseUrl = url;

	}

	public <T> T putRequest(Serializable webRequest, Class<T> clazz, Map<String, String> headers, String... paths) throws ClientException {

		HttpPut httpPut = new HttpPut(getUrl(paths));
		httpPut.setHeader(HTTP.CONTENT_TYPE, type);

		addHeaders(httpPut, headers);

		return put(httpPut, webRequest, clazz, paths);

	}

	public <T> T put(Serializable webRequest, Class<T> clazz, String... paths) throws ClientException {

		HttpPut httpPut = new HttpPut(getUrl(paths));
		httpPut.setHeader(HTTP.CONTENT_TYPE, type);

		return put(httpPut, webRequest, clazz, paths);

	}

	@SuppressWarnings("unchecked")
	private <T> T put(HttpPut httpPut, Serializable webRequest, Class<T> clazz, String... paths) throws ClientException {

	    HttpResponse response;

	    try {

	    	httpPut.setEntity(new ByteArrayEntity(convertStreamToString(webRequest).toString().getBytes("UTF8")));

	    	longInfo("HTTP PUT {}", getUrl(paths));
	    	longInfo("REQUEST {}", convertStreamToString(webRequest).toString());

	        response = httpclient.execute(httpPut, localContext);

	        checkStatus(response);

	        HttpEntity entity = response.getEntity();
	        if (entity != null) {

	        	longInfo("ENTITY {}", entity.toString());

	            InputStream instream = entity.getContent();

	            String value = convertStreamToString(instream);

	            checkErrors(value);

	            if (clazz.getSimpleName().equals("String")) {
	            	return (T) value;
	            }

	            ObjectMapper objectMapper = new ObjectMapper();

				try {

					longInfo("HTTP RESPONSE {}", value);

					return objectMapper.readValue(value, clazz);

				} catch (JsonParseException e) {
					e.printStackTrace();
					throw new ClientException(new ErrorMessage(ERROR_TAG, JSON_PARSE_EXCEPTION));
				} catch (JsonMappingException e) {
					e.printStackTrace();
					throw new ClientException(new ErrorMessage(ERROR_TAG, JSON_PARSE_EXCEPTION));
				} catch (IOException e) {
					e.printStackTrace();
					throw new ClientException(new ErrorMessage(ERROR_TAG, IO_EXEPTION));
				} finally {
					entity.consumeContent();
					instream.close();
				}

	        }

	    } catch (ClientProtocolException e1) {
	    	e1.printStackTrace();
			throw new ClientException(new ErrorMessage(CONNECTION_TAG, CONNECTION_MESSAGE));
		} catch (IOException e1) {
			e1.printStackTrace();
			throw new ClientException(new ErrorMessage(CONNECTION_TAG, CONNECTION_MESSAGE));
		}

		return null;

	}

	public <T> T deleteRequest(Class<T> clazz, Map<String, String> headers, String ... paths) throws ClientException {

		HttpDelete httpDelete = new HttpDelete(getUrl(paths));
		httpDelete.setHeader(HTTP.CONTENT_TYPE, type);

		addHeaders(httpDelete, headers);

		return delete(httpDelete, clazz, paths);

	}

	public <T> T delete(Class<T> clazz, String ... paths) throws ClientException {

		HttpDelete httpDelete = new HttpDelete(getUrl(paths));
	    httpDelete.setHeader(HTTP.CONTENT_TYPE, type);

		return delete(httpDelete, clazz, paths);

	}

	@SuppressWarnings("unchecked")
	private <T> T delete(HttpDelete httpDelete, Class<T> clazz, String ... paths) throws ClientException {

	    HttpResponse response;

	    try {

	    	longInfo("HTTP DELETE {}", getUrl(paths));

	        response = httpclient.execute(httpDelete, localContext);

	        checkStatus(response);

			localContext.setAttribute(ClientContext.COOKIE_STORE, httpclient.getCookieStore());

	        HttpEntity entity = response.getEntity();
	        if (entity != null) {

	        	longInfo("ENTITY {}", entity.toString());

	            InputStream instream = entity.getContent();
	            String value = convertStreamToString(instream);

	            longInfo("CONTENT {}", value);
	            longInfo("TYPE {}", entity.getContentType().toString());
	            longInfo("VALUE {}", entity.getContentType().getValue());

	            checkErrors(value);

	            if (clazz.getSimpleName().equals("String")) {
	            	return (T) value;
	            }

	            ObjectMapper objectMapper = new ObjectMapper();

				try {

					longInfo("HTTP RESPONSE {}", value);

					return objectMapper.readValue(value, clazz);

				} catch (JsonParseException e) {
					e.printStackTrace();
					throw new ClientException(new ErrorMessage(JSON_PARSE_EXCEPTION, JSON_PARSE_EXCEPTION));
				} catch (JsonMappingException e) {
					e.printStackTrace();
					throw new ClientException(new ErrorMessage(ERROR_TAG, JSON_PARSE_EXCEPTION));
				} catch (IOException e) {
					e.printStackTrace();
					throw new ClientException(new ErrorMessage(ERROR_TAG, IO_EXEPTION));
				} finally {
					entity.consumeContent();
					instream.close();
				}

	        }

	    } catch (ClientProtocolException e1) {
	    	e1.printStackTrace();
			throw new ClientException(new ErrorMessage(CONNECTION_TAG, CONNECTION_MESSAGE));
		} catch (IOException e1) {
			e1.printStackTrace();
			throw new ClientException(new ErrorMessage(CONNECTION_TAG, CONNECTION_MESSAGE));
		}

		return null;

	}

	public <T> T postRequest(Serializable webRequest, Class<T> clazz, Map<String, String> headers, String... paths) throws ClientException {

		HttpPost httpPost = new HttpPost(getUrl(paths));
		httpPost.setHeader(HTTP.CONTENT_TYPE, type);

		addHeaders(httpPost, headers);

		return post(httpPost, webRequest, clazz, paths);

	}

	public <T> T post(Serializable webRequest, Class<T> clazz, String... paths) throws ClientException {

		HttpPost httpPost = new HttpPost(getUrl(paths));
		httpPost.setHeader(HTTP.CONTENT_TYPE, type);

		return post(httpPost, webRequest, clazz, paths);

	}

	@SuppressWarnings("unchecked")
	private <T> T post(HttpPost httpPost, Serializable webRequest, Class<T> clazz, String... paths) throws ClientException {

	    HttpResponse response;

	    try {

	    	httpPost.setEntity(new ByteArrayEntity(convertStreamToString(webRequest).toString().getBytes("UTF8")));

	    	longInfo("HTTP POST {}", getUrl(paths));
	    	longInfo("REQUEST {}", convertStreamToString(webRequest).toString());

	        response = httpclient.execute(httpPost, localContext);

	        checkStatus(response);

	        HttpEntity entity = response.getEntity();

	        if (entity != null) {

	        	longInfo("ENTITY {}", entity.toString());

	            InputStream instream = entity.getContent();
	            String value = convertStreamToString(instream);

		    	longInfo("CONTENT {}", value);
	            longInfo("TYPE {}", entity.getContentType().toString());
	            longInfo("VALUE {}", entity.getContentType().getValue());

	            checkErrors(value);

	            if (clazz.getSimpleName().equals("String")) {
	            	return (T) value;
	            }

	            ObjectMapper objectMapper = new ObjectMapper();

				try {

					longInfo("HTTP RESPONSE {}", value);

					return objectMapper.readValue(value, clazz);

				} catch (JsonParseException e) {
					e.printStackTrace();
					throw new ClientException(new ErrorMessage(JSON_PARSE_EXCEPTION, JSON_PARSE_EXCEPTION));
				} catch (JsonMappingException e) {
					e.printStackTrace();
					throw new ClientException(new ErrorMessage(ERROR_TAG, JSON_PARSE_EXCEPTION));
				} catch (IOException e) {
					e.printStackTrace();
					throw new ClientException(new ErrorMessage(ERROR_TAG, IO_EXEPTION));
				} finally {
					entity.consumeContent();
					instream.close();
				}

	        }

	    } catch (ClientProtocolException e1) {
	    	e1.printStackTrace();
			throw new ClientException(new ErrorMessage(CONNECTION_TAG, CONNECTION_MESSAGE));
		} catch (IOException e1) {
			e1.printStackTrace();
			throw new ClientException(new ErrorMessage(CONNECTION_TAG, CONNECTION_MESSAGE));
		}

		return null;

	}

	public <T> T get(Class<T> clazz, String... paths) throws ClientException {

		HttpGet httpget = new HttpGet(getUrl(paths));
	    httpget.setHeader("Accept", type);

		return get(httpget, clazz, paths);

	}

	public <T> T getRequest(Class<T> clazz, Map<String, String> headers, String... paths) throws ClientException {

		HttpGet httpget = new HttpGet(getUrl(paths));
	    httpget.setHeader("Accept", type);

		addHeaders(httpget, headers);

		return get(httpget, clazz, paths);

	}

	public <T> T get(Map<String, String> parameters, Class<T> clazz, String... paths) throws ClientException {

		String url = getUrl(paths);

		if (parameters != null && !parameters.isEmpty()) {

			url = addParametersToUrl(getUrl(paths), parameters);

		}

    	longInfo("HTTP GET {}", url);

		HttpGet httpget = new HttpGet(url);
	    httpget.setHeader("Accept", type);

		return get(httpget, clazz, paths);

	}

	public <T> T getRequest(Map<String, String> parameters, Class<T> clazz, Map<String, String> headers, String... paths) throws ClientException {

		String url = getUrl(paths);

		if (parameters != null && !parameters.isEmpty()) {

			url = addParametersToUrl(getUrl(paths), parameters);

		}

    	longInfo("HTTP GET {}", url);

		HttpGet httpget = new HttpGet(url);
	    httpget.setHeader("Accept", type);

		addHeaders(httpget, headers);

		return get(httpget, clazz, paths);

	}

	@SuppressWarnings("unchecked")
	private <T> T get(HttpGet httpget, Class<T> clazz, String ... paths) throws ClientException {

	    HttpResponse response;

	    try {

	    	longInfo("HTTP GET {}", getUrl(paths));

	        response = httpclient.execute(httpget, localContext);

			localContext.setAttribute(ClientContext.COOKIE_STORE, httpclient.getCookieStore());

	        HttpEntity entity = response.getEntity();

	        if (entity != null) {

	        	longInfo("ENTITY {}", entity.toString());

	            InputStream instream = entity.getContent();
	            String value = convertStreamToString(instream);

	            longInfo("CONTENT {}", value);
	            longInfo("TYPE {}", entity.getContentType().toString());
	            longInfo("VALUE {}", entity.getContentType().getValue());

		        checkStatus(response);

	            checkErrors(value);

	            if (clazz.getSimpleName().equals("String")) {
	            	return (T) value;
	            }

	            ObjectMapper objectMapper = new ObjectMapper();

				try {

					longInfo("HTTP RESPONSE {}", value);

					return objectMapper.readValue(value, clazz);

				} catch (JsonParseException e) {
					e.printStackTrace();
					throw new ClientException(new ErrorMessage(JSON_PARSE_EXCEPTION, JSON_PARSE_EXCEPTION));
				} catch (JsonMappingException e) {
					e.printStackTrace();
					throw new ClientException(new ErrorMessage(ERROR_TAG, JSON_PARSE_EXCEPTION));
				} catch (IOException e) {
					e.printStackTrace();
					throw new ClientException(new ErrorMessage(ERROR_TAG, IO_EXEPTION));
				} finally {
					entity.consumeContent();
					instream.close();
				}

	        }

	    } catch (ClientProtocolException e1) {
	    	e1.printStackTrace();
			throw new ClientException(new ErrorMessage(CONNECTION_TAG, CONNECTION_MESSAGE));
		} catch (IOException e1) {
			e1.printStackTrace();
			throw new ClientException(new ErrorMessage(CONNECTION_TAG, CONNECTION_MESSAGE));
		}

		return null;

	}

	private void checkStatus(HttpResponse response) throws ClientException {

		if (response.getStatusLine().getStatusCode() >= 500) {

			throw new ClientException(new ErrorMessage("Server Error", "An unexpected error has occured on the server.\n" + response.getStatusLine().getStatusCode() + " - " + response.getStatusLine().getReasonPhrase()));

		}

	}

	protected void checkErrors(String value) throws ClientException {

	}

	private String getUrl(String[] paths) {

		StringBuilder builder = new StringBuilder();

		for(String s : paths) {
		    builder.append(s);
		}

		return baseUrl + builder.toString();

	}

	private static String convertStreamToString(InputStream is) {

	    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
	    StringBuilder sb = new StringBuilder();

	    String line = null;
	    try {
	        while ((line = reader.readLine()) != null) {
	            sb.append(line + "\n");
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    } finally {
	        try {
	            is.close();
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	    }
	    return sb.toString();
	}



	private String convertStreamToString(Serializable webRequest) {

		ObjectMapper mapper = new ObjectMapper();
		String jasonEntityValue = null;
		try {
			jasonEntityValue = mapper.writeValueAsString(webRequest);
		} catch (JsonGenerationException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return jasonEntityValue;

	}

	protected void addHeaders(AbstractHttpMessage httpMessage, Map<String, String> headers) {

	    Iterator<Entry<String, String>> it = headers.entrySet().iterator();
	    while (it.hasNext()) {
	        Entry<String, String> pairs = (Entry<String, String>) it.next();
	        httpMessage.setHeader(pairs.getKey(), pairs.getValue());
	        it.remove();
	    }

	}

	protected String addParametersToUrl(String url, Map<String, String> parameters) {

	    if (!url.endsWith("?")) {
	    	url += "?";
	    }

	    List<NameValuePair> params = new LinkedList<NameValuePair>();

	    Iterator<Entry<String, String>> it = parameters.entrySet().iterator();
	    while (it.hasNext()) {
	        Entry<String, String> pairs = (Entry<String, String>) it.next();
	        params.add(new BasicNameValuePair(pairs.getKey(), pairs.getValue()));
	        it.remove();
	    }

	    String paramString = URLEncodedUtils.format(params, "utf-8");

	    url += paramString;
	    return url;

	}

	public <T> T getEntityFromString(Class<T> clazz, String value) throws ClientException {

		ObjectMapper objectMapper = new ObjectMapper();

		try {

			return objectMapper.readValue(value, clazz);

		} catch (JsonParseException e) {
			e.printStackTrace();
        	longInfo("ERROR {}", JSON_PARSE_EXCEPTION);
			throw new ClientException(new ErrorMessage(ERROR_TAG, JSON_PARSE_EXCEPTION));
		} catch (JsonMappingException e) {
			e.printStackTrace();
        	longInfo("ERROR {}", JSON_PARSE_EXCEPTION);
			throw new ClientException(new ErrorMessage(ERROR_TAG, JSON_PARSE_EXCEPTION));
		} catch (IOException e) {
			e.printStackTrace();
        	longInfo("ERROR {}", IO_EXEPTION);
			throw new ClientException(new ErrorMessage(ERROR_TAG, IO_EXEPTION));
		}

	}

	public static void longInfo(String tag, String str) {
		try {

//			if(str.length() > 4000) {
//				Log.d(tag, str.substring(0, 4000));
//				longInfo(tag, str.substring(4000));
//			} else
				Log.d(tag, str);

		} catch (StackOverflowError error) {
			error.printStackTrace();
		}
	}

}
