/* 
 **
 ** Copyright 2014, Jules White
 **
 ** 
 */
package com.courseracapstone.serverside.integration.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.client.Header;
import retrofit.client.Request;
import retrofit.client.Response;
import retrofit.mime.FormUrlEncodedTypedOutput;

import com.courseracapstone.common.SecuredRestException;
import com.google.common.io.BaseEncoding;

//import org.apache.commons.io.IOUtils;
import retrofit.client.OkClient;
import retrofit.Profiler;
import retrofit.RestAdapter.Log;
import retrofit.RestAdapter.LogLevel;
import retrofit.converter.Converter;
import retrofit.ErrorHandler;
import retrofit.client.Client.Provider;
import retrofit.Endpoint;

/**
 * A Builder class for a Retrofit REST Adapter. Basically a dirty hack to let
 * me test the add user functionality as quick as possible. 
 * It's only used for testing server-side.
 *
 */
public class SignupSecuredRestBuilder extends RestAdapter.Builder {

	private class OAuthHandler implements RequestInterceptor {

		private Client client;
		private String tokenIssuingEndpoint;
		private String username;
		private String password;
		private String clientId;
		private String clientSecret;
		@SuppressWarnings("unused")
		private String accessToken;

		public OAuthHandler(Client client, String tokenIssuingEndpoint, String username,
				String password, String clientId, String clientSecret) {
			super();
			this.client = client;
			this.tokenIssuingEndpoint = tokenIssuingEndpoint;
			this.username = username;
			this.password = password;
			this.clientId = clientId;
			this.clientSecret = clientSecret;
		}

		/**
		 * Every time a method on the client interface is invoked, this method is
		 * going to get called. The method checks if the client has previously obtained
		 * an OAuth 2.0 bearer token. If not, the method obtains the bearer token by
		 * sending a password grant request to the server. 
		 * 
		 * Once this method has obtained a bearer token, all future invocations will
		 * automatically insert the bearer token as the "Authorization" header in 
		 * outgoing HTTP requests.
		 * 
		 */
		@Override
		public void intercept(RequestFacade request) {
			try {
				// This code below programmatically builds an OAuth 2.0 password
				// grant request and sends it to the server. 
				
				// Encode the username and password into the body of the request.
				FormUrlEncodedTypedOutput to = new FormUrlEncodedTypedOutput();
				to.addField("username", username);
				to.addField("password", password);
				
				// Add the client ID and client secret to the body of the request.
				to.addField("client_id", clientId);
				to.addField("client_secret", clientSecret);
				
//				to.addField("access", "ROLE_USER");
				
				// The password grant requires BASIC authentication of the client.
				// In order to do BASIC authentication, we need to concatenate the
				// client_id and client_secret values together with a colon and then
				// Base64 encode them. The final value is added to the request as
				// the "Authorization" header and the value is set to "Basic " 
				// concatenated with the Base64 client_id:client_secret value described
				// above.
				String base64Auth = BaseEncoding.base64().encode(new String(clientId + ":" + clientSecret).getBytes());
				// Add the basic authorization header
				List<Header> headers = new ArrayList<Header>();
				headers.add(new Header("Authorization", "Basic " + base64Auth));

				// Create the actual password grant request using the data above
				Request req = new Request("POST", tokenIssuingEndpoint, headers, to);
				
				// Request the password grant.
				Response resp = client.execute(req);
				
				// Make sure the server responded with 200 OK
				if (resp.getStatus() < 200 || resp.getStatus() > 299) {
					// If not, we probably have bad credentials
					throw new SecuredRestException("Login failure: "
							+ resp.getStatus() + " - " + resp.getReason());
				} 
				
			} catch (Exception e) {
				throw new SecuredRestException(e);
			}
		}

	}

	private String username;
	private String password;
	private String loginUrl;
	private String clientId;
	private String clientSecret = "";
	private Client client;
	
	public SignupSecuredRestBuilder setLoginEndpoint(String endpoint){
		loginUrl = endpoint;
		return this;
	}

	@Override
	public SignupSecuredRestBuilder setEndpoint(String endpoint) {
		return (SignupSecuredRestBuilder) super.setEndpoint(endpoint);
	}

	@Override
	public SignupSecuredRestBuilder setEndpoint(Endpoint endpoint) {
		return (SignupSecuredRestBuilder) super.setEndpoint(endpoint);
	}

	@Override
	public SignupSecuredRestBuilder setClient(Client client) {
		this.client = client;
		return (SignupSecuredRestBuilder) super.setClient(client);
	}

	@Override
	public SignupSecuredRestBuilder setClient(Provider clientProvider) {
		client = clientProvider.get();
		return (SignupSecuredRestBuilder) super.setClient(clientProvider);
	}

	@Override
	public SignupSecuredRestBuilder setErrorHandler(ErrorHandler errorHandler) {

		return (SignupSecuredRestBuilder) super.setErrorHandler(errorHandler);
	}

	@Override
	public SignupSecuredRestBuilder setExecutors(Executor httpExecutor,
			Executor callbackExecutor) {

		return (SignupSecuredRestBuilder) super.setExecutors(httpExecutor,
				callbackExecutor);
	}

	@Override
	public SignupSecuredRestBuilder setRequestInterceptor(
			RequestInterceptor requestInterceptor) {

		return (SignupSecuredRestBuilder) super
				.setRequestInterceptor(requestInterceptor);
	}

	@Override
	public SignupSecuredRestBuilder setConverter(Converter converter) {

		return (SignupSecuredRestBuilder) super.setConverter(converter);
	}

	@Override
	public SignupSecuredRestBuilder setProfiler(@SuppressWarnings("rawtypes") Profiler profiler) {

		return (SignupSecuredRestBuilder) super.setProfiler(profiler);
	}

	@Override
	public SignupSecuredRestBuilder setLog(Log log) {

		return (SignupSecuredRestBuilder) super.setLog(log);
	}

	@Override
	public SignupSecuredRestBuilder setLogLevel(LogLevel logLevel) {

		return (SignupSecuredRestBuilder) super.setLogLevel(logLevel);
	}

	public SignupSecuredRestBuilder setUsername(String username) {
		this.username = username;
		return this;
	}

	public SignupSecuredRestBuilder setPassword(String password) {
		this.password = password;
		return this;
	}

	public SignupSecuredRestBuilder setClientId(String clientId) {
		this.clientId = clientId;
		return this;
	}
	
	public SignupSecuredRestBuilder setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
		return this;
	}
	
		

	@Override
	public RestAdapter build() {
		if (username == null || password == null) {
			throw new SecuredRestException(
					"You must specify both a username and password for a "
							+ "SecuredRestBuilder before calling the build() method.");
		}

		if (client == null) {
			client = new OkClient();
		}
		OAuthHandler hdlr = new OAuthHandler(client, loginUrl, username, password, clientId, clientSecret);
		setRequestInterceptor(hdlr);

		return super.build();
	}
}