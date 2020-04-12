package com.courseracapstone.android.https;

/* 
 **
 ** Copyright 2014, Jules White
 **
 ** 
 */
import com.courseracapstone.common.SecuredRestException;

import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.client.Client.Provider;

/**
 * A Builder class for a Retrofit REST Adapter. Extends the default implementation by providing logic to
 * handle an OAuth 2.0 password grant login flow. The RestAdapter that it produces uses an interceptor
 * to automatically obtain a bearer token from the authorization server and insert it into all client
 * requests.
 * 
 * @author Jules, Mitchell
 * 
 * @author xxx xxx: Modified to work with Account Manager auth token. This class no longer handles
 * account registration on the client, it only acts as an injection mechanism for the Auth Token.
 *
 */
public class AuthTokenSecuredRestBuilder extends RestAdapter.Builder {

	private class OAuthHandler implements RequestInterceptor {

		private String accessToken;

		public OAuthHandler(String authToken) {
			this.accessToken = authToken;
		}

		/**
		 * Every time a method on the client interface is invoked, this method is
		 * going to get called. All invocations will
		 * automatically insert the bearer token as the "Authorization" header in 
		 * outgoing HTTP requests.
		 * 
		 */
		@Override
		public void intercept(RequestFacade request) {
			request.addHeader("Authorization", "Bearer " + accessToken );
		}

	}

	private Client client;
	private String accessToken;

	public AuthTokenSecuredRestBuilder setAccessToken(String accessToken) {
		this.accessToken = accessToken;
		return this;
	}

	@Override
	public AuthTokenSecuredRestBuilder setClient(Client client) {
		this.client = client;
		return (AuthTokenSecuredRestBuilder) super.setClient(client);
	}

	@Override
	public AuthTokenSecuredRestBuilder setClient(Provider clientProvider) {
		client = clientProvider.get();
		return (AuthTokenSecuredRestBuilder) super.setClient(clientProvider);
	}

	@Override
	public RestAdapter build() {
		if (client == null || accessToken == null) {
			throw new SecuredRestException(
					"You must specify both a client and valid auth token for a "
							+ "SecuredRestBuilder before calling the build() method.");
		}

		OAuthHandler hdlr = new OAuthHandler(accessToken);
		setRequestInterceptor(hdlr);

		return super.build();
	}
}