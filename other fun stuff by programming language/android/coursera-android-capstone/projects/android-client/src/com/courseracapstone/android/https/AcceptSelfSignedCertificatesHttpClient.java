package com.courseracapstone.android.https;

import java.io.InputStream;
import java.security.KeyStore;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.content.Context;
import android.util.Log;

import com.courseracapstone.android.accountsetup.PotlatchAccountConstants;
import com.courseracapstone.android.R;

/**
 * A client which accepts self-signed certificates over https provide the
 * certificate can be validated from the keystore in the res/raw folder
 */
public class AcceptSelfSignedCertificatesHttpClient extends DefaultHttpClient {

	private final String LOG_TAG = "Potlatch-" + getClass().getSimpleName();
	final Context context;

	public static HttpParams getTimeoutHttpParams() {
		HttpParams httpParams = new BasicHttpParams();
	    HttpConnectionParams.setConnectionTimeout(httpParams, 3000); // 3 secs to get a connection
	    HttpConnectionParams.setSoTimeout(httpParams, 5000); // 5 secs for data to be returned
		return httpParams;
	}
	
	public AcceptSelfSignedCertificatesHttpClient(Context context) {
		super(getTimeoutHttpParams());
		this.context = context;
	}

	@Override
	protected ClientConnectionManager createClientConnectionManager() {
		SchemeRegistry registry = new SchemeRegistry();
		registry.register(new Scheme("http", PlainSocketFactory
				.getSocketFactory(), 80));
		registry.register(new Scheme("https", newSslSocketFactory()
				, PotlatchAccountConstants.DEFAULT_DEV_PORT)); // 8443
		return new SingleClientConnManager(getParams(), registry);
	}

	private SSLSocketFactory newSslSocketFactory() {
		try {
			KeyStore trusted = KeyStore.getInstance("BKS");
			InputStream in = context.getResources().openRawResource(
					R.raw.bkskeystore);
			try {
				trusted.load(in, "changeit".toCharArray());
				Log.d(LOG_TAG, "keystore loaded contains #" + trusted.size()
						+ " keys");
			} finally {
				in.close();
			}
			return new SSLSocketFactory(trusted);
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}
}
