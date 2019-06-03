package io.rnkit.sensor;

import android.os.Build;

import com.yanzhenjie.kalle.Headers;
import com.yanzhenjie.kalle.Request;
import com.yanzhenjie.kalle.RequestMethod;
import com.yanzhenjie.kalle.connect.ConnectFactory;
import com.yanzhenjie.kalle.connect.Connection;
import com.yanzhenjie.kalle.urlconnect.URLConnection;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import okhttp3.OkHttpClient;
import okhttp3.internal.huc.OkHttpURLConnection;
import okhttp3.internal.huc.OkHttpsURLConnection;

/**
 * Created on 2019/5/31.
 *
 * @author 老潘
 */

public class OkHttpConnectFactory implements ConnectFactory {
    private static final String KEY_HTTP  = "http";
    private static final String KEY_HTTPS = "https";

    public static Builder newBuilder() {
        return new Builder();
    }

    private final OkHttpClient mClient;

    private OkHttpConnectFactory(Builder builder) {
        this.mClient = builder.mClient == null ? new OkHttpClient.Builder().build() : builder.mClient;
    }

    private HttpURLConnection open(URL url, Proxy proxy) {
        OkHttpClient newClient = mClient.newBuilder().proxy(proxy).build();
        String protocol = url.getProtocol();
        if (protocol.equalsIgnoreCase(KEY_HTTP)) {
            return new OkHttpURLConnection(url, newClient);
        }
        if (protocol.equalsIgnoreCase(KEY_HTTPS)) {
            return new OkHttpsURLConnection(url, newClient);
        }
        throw new IllegalArgumentException("Unexpected protocol: " + protocol);
    }

    @Override
    public Connection connect(Request request) throws IOException {
        URL url = new URL(request.url().toString());
        Proxy proxy = request.proxy();
        HttpURLConnection connection = open(url, proxy);

        connection.setConnectTimeout(request.connectTimeout());
        connection.setReadTimeout(request.readTimeout());
        connection.setInstanceFollowRedirects(false);

        if (connection instanceof HttpsURLConnection) {
            SSLSocketFactory sslSocketFactory = request.sslSocketFactory();
            if (sslSocketFactory != null) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(sslSocketFactory);
            }
            HostnameVerifier hostnameVerifier = request.hostnameVerifier();
            if (hostnameVerifier != null) {
                ((HttpsURLConnection) connection).setHostnameVerifier(hostnameVerifier);
            }
        }

        RequestMethod method = request.method();
        connection.setRequestMethod(method.toString());
        connection.setDoInput(true);
        boolean isAllowBody = method.allowBody();
        connection.setDoOutput(isAllowBody);

        Headers headers = request.headers();

        if (isAllowBody) {
            long contentLength = headers.getContentLength();
            if (contentLength <= Integer.MAX_VALUE) {
                connection.setFixedLengthStreamingMode((int) contentLength);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                connection.setFixedLengthStreamingMode(contentLength);
            } else {
                connection.setChunkedStreamingMode(256 * 1024);
            }
        }

        Map<String, String> requestHeaders = Headers.getRequestHeaders(headers);
        for (Map.Entry<String, String> headerEntry : requestHeaders.entrySet()) {
            String headKey = headerEntry.getKey();
            String headValue = headerEntry.getValue();
            connection.setRequestProperty(headKey, headValue);
        }

        connection.connect();
        return new URLConnection(connection);
    }

    public static class Builder {

        private OkHttpClient mClient;

        private Builder() {
        }

        public Builder client(OkHttpClient client) {
            this.mClient = client;
            return this;
        }

        public OkHttpConnectFactory build() {
            return new OkHttpConnectFactory(this);
        }
    }

}
