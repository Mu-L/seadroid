package com.seafile.seadroid2.util;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * Utils that depend on JellyBean API (16)
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class UtilsJellyBean {

    public static List<Uri> extractUriListFromIntent(Intent intent) {
        ClipData clipdata = intent.getClipData();
        List<Uri> list = new ArrayList<Uri>();
        for (int i=0; i< clipdata.getItemCount(); i++) {
            Uri uri = clipdata.getItemAt(i).getUri();
            list.add(uri);
        }
        return list;
    }

}
