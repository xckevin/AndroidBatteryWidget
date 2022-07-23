package com.github.xckevin927.android.battery.widget.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;

import com.github.xckevin927.android.battery.widget.R;

public class ShareUtil {

    public static void share(Context context) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_title));
        intent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_content, context.getPackageName()));
        intent = Intent.createChooser(intent, context.getString(R.string.share_title));
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException ignore) {}
    }
}
