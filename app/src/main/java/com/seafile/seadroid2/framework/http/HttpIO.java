package com.seafile.seadroid2.framework.http;

import android.text.TextUtils;

import com.blankj.utilcode.util.CloneUtils;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.SupportAccountManager;
import com.seafile.seadroid2.framework.http.callback.ProgressCallback;
import com.seafile.seadroid2.framework.http.converter.ConverterFactory;
import com.seafile.seadroid2.framework.http.download.BinaryFileDownloader;
import com.seafile.seadroid2.framework.http.download.BinaryFileWriter;
import com.seafile.seadroid2.framework.util.TokenManager;
import com.seafile.seadroid2.listener.ProgressListener;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

public class HttpIO {
    private static volatile HttpIO INSTANCE;

    private final Account account;
    private BaseOkHttpClient okHttpClient;

    private static final ConcurrentMap<String, HttpIO> IO_MAP = new ConcurrentHashMap<>();

    private HttpIO(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("IO constructor(): account is null.");
        }

        if (TextUtils.isEmpty(account.server)) {
            throw new IllegalArgumentException("IO constructor(): account.server is null.");
        }

        if (TextUtils.isEmpty(account.email)) {
            throw new IllegalArgumentException("IO constructor(): account.email is null.");
        }

        this.account = account;
    }

    /**
     * Logged in
     */
    public static HttpIO getCurrentInstance() {
        if (INSTANCE != null) {
            return INSTANCE;
        }

        // singleton and map
        synchronized (HttpIO.class) {
            if (INSTANCE == null) {

                Account curAccount = SupportAccountManager.getInstance().getCurrentAccount();
                if (curAccount == null) {
                    throw new IllegalStateException("IO instance(): No current account");
                }

                //
                INSTANCE = new HttpIO(curAccount);

                //
                TokenManager.getInstance().setToken(curAccount.token);
            }
        }

        return INSTANCE;
    }

    public static void removeInstanceByAccount(Account account) {
        if (account == null) {
            return;
        }

        IO_MAP.remove(account.getSignature());
    }

    /**
     * Not logged in/Log in to another server
     */
    public static HttpIO getInstanceByAccount(Account account) {
        if (account == null) {
            return null;
        }

        if (IO_MAP.containsKey(account.getSignature())) {
            HttpIO httpIo = IO_MAP.get(account.getSignature());
            if (httpIo == null) {
                return getAndRemoveIO(account);
            }

            if (!Objects.equals(httpIo.account, account)) {
                return getAndRemoveIO(account);
            }

            return httpIo;
        }

        return getAndRemoveIO(account);
    }

    private static HttpIO getAndRemoveIO(Account account) {
        Account newAccount = CloneUtils.deepClone(account, Account.class);
        HttpIO httpIo = new HttpIO(newAccount);

        IO_MAP.remove(newAccount.getSignature());
        IO_MAP.put(newAccount.getSignature(), httpIo);

        return httpIo;
    }

    /**
     * get server url
     */
    public String getServerUrl() {
        return account.getServer();
    }

    /**
     * get client
     */
    public BaseOkHttpClient getOkHttpClient() {
        if (okHttpClient == null) {
            okHttpClient = new SafeOkHttpClient(account);
        }
        return okHttpClient;
    }


    /**
     * When log in again or switch account, you should reset this IO Singleton.
     * <p><b>because it's a SINGLETON, unless kill the APP!<b/></p>
     */
    public static void resetLoggedInInstance() {
        INSTANCE = null;
        IO_MAP.clear();
    }


    public <T> T execute(Class<T> clazz) {
        Retrofit retrofit = createRetrofit();
        return retrofit.create(clazz);
    }

    private Retrofit retrofit;

    private Retrofit createRetrofit() {
        if (retrofit != null) {
            return retrofit;
        }

        Retrofit.Builder rBuilder = new Retrofit.Builder();

        rBuilder.baseUrl(getServerUrl());
        rBuilder.addConverterFactory(ConverterFactory.create());
        rBuilder.addCallAdapterFactory(RxJava2CallAdapterFactory.create());

        rBuilder.client(getOkHttpClient().getOkClient());

        retrofit = rBuilder.build();
        return retrofit;
    }
}
