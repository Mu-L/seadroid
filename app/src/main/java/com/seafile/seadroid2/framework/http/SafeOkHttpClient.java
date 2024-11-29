package com.seafile.seadroid2.framework.http;

import com.blankj.utilcode.util.CollectionUtils;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.ssl.SSLSeafileSocketFactory;
import com.seafile.seadroid2.ssl.SSLTrustManager;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.ConnectionSpec;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class SafeOkHttpClient extends BaseOkHttpClient {
    public SafeOkHttpClient(Account account) {
        super(account);
    }

    public static TrustManager[] getTrustManagers() {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                throw new IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustManagers));
            }
            return trustManagers;
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    public SSLSocketFactory getTLSSocketFactory(TrustManager[] trustManagers) {
        try {
            X509TrustManager trustManager = (X509TrustManager) trustManagers[0];

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public OkHttpClient getOkClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        //https
        if (account.getServer().startsWith("https://")) {
            //ssl

            SSLSocketFactory factory = SSLTrustManager.instance().getSSLSocketFactory(account);
            TrustManager[] trustManagers = SSLTrustManager.instance().getTrustManagers(account);
            X509TrustManager trustManager = (X509TrustManager) trustManagers[0];

            builder.sslSocketFactory(factory, trustManager);

            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    //check host
                    if (account.getServerDomainName().equals(hostname)) {
                        return true;
                    }

                    //check by default verifier
                    HostnameVerifier verifier = HttpsURLConnection.getDefaultHostnameVerifier();
                    return verifier.verify(hostname, session);
                }
            });
        }

        // Add an interceptor to set SSL only for HTTPS
//        builder.addInterceptor(chain -> {
//            Request request = chain.request();
//            if (request.isHttps()) {
//                //ssl
//                TrustManager[] trustManagers = getTrustManagers();
//                X509TrustManager trustManager = (X509TrustManager) trustManagers[0];
//                SSLSocketFactory sslSocketFactory = getTLSSocketFactory(trustManagers);
//
//                builder.sslSocketFactory(sslSocketFactory, trustManager);
//
//                builder.hostnameVerifier(new HostnameVerifier() {
//                    @Override
//                    public boolean verify(String hostname, SSLSession session) {
//                        //check host
//                        if (account.getServerDomainName().equals(hostname)) {
//                            return true;
//                        }
//
//                        //check by default verifier
//                        HostnameVerifier verifier = HttpsURLConnection.getDefaultHostnameVerifier();
//                        return verifier.verify(hostname, session);
//                    }
//                });
//            }
//            return chain.proceed(request);
//        });

        builder.connectionSpecs(Arrays.asList(
                ConnectionSpec.MODERN_TLS,
                ConnectionSpec.COMPATIBLE_TLS,
                ConnectionSpec.CLEARTEXT));
        builder.cache(cache);
        //cache control
        builder.interceptors().add(REWRITE_CACHE_CONTROL_INTERCEPTOR);
        builder.networkInterceptors().add(REWRITE_CACHE_CONTROL_INTERCEPTOR);

        //add interceptors
        List<Interceptor> interceptors = getInterceptors();
        if (!CollectionUtils.isEmpty(interceptors)) {
            for (Interceptor i : interceptors) {
                builder.interceptors().add(i);
            }
        }

        //timeout
        builder.writeTimeout(DEFAULT_TIME_OUT, TimeUnit.MILLISECONDS);
        builder.readTimeout(DEFAULT_TIME_OUT, TimeUnit.MILLISECONDS);
        builder.connectTimeout(DEFAULT_TIME_OUT, TimeUnit.MILLISECONDS);

        return builder.build();
    }
}