package com.github.xckevin927.android.battery.widget.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.util.DisplayMetrics;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

/**
 * Created by cailiming on 2017/6/6.
 */

public class UiUtil {

    public static void hideKeyBoard(Activity context) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (context.getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(context.getCurrentFocus().getWindowToken(), 0);
        }
    }

    public static int dp2px(Context context, int dp) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int px = (int)((double)((float)dp * dm.scaledDensity) + 0.5D);
        return px;
    }

    public static int dp2px(Context context, float dp) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int px = (int)((double)((float)dp * dm.scaledDensity) + 0.5D);
        return px;
    }

    public static float getScreenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    public static float getScreenHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    //输入框最大输入长度
    public static int getMaxInputLength() {return  2000;}

    public static int getTextViewLines(TextView textView, int textViewWidth) {
        int width = textViewWidth - textView.getCompoundPaddingLeft() - textView.getCompoundPaddingRight();
        StaticLayout staticLayout;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            staticLayout = getStaticLayout23(textView, width);
        } else {
            staticLayout = getStaticLayout(textView, width);
        }
        int lines = staticLayout.getLineCount();
        int maxLines = textView.getMaxLines();
        if (maxLines > lines) {
            return lines;
        }
        return maxLines;
    }

    /**
     * sdk>=23
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static StaticLayout getStaticLayout23(TextView textView, int width) {
        StaticLayout.Builder builder = StaticLayout.Builder.obtain(textView.getText(),
            0, textView.getText().length(), textView.getPaint(), width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setLineSpacing(textView.getLineSpacingExtra(), textView.getLineSpacingMultiplier())
            .setIncludePad(textView.getIncludeFontPadding())
            .setBreakStrategy(textView.getBreakStrategy())
            .setHyphenationFrequency(textView.getHyphenationFrequency())
            .setMaxLines(textView.getMaxLines() == -1 ? Integer.MAX_VALUE : textView.getMaxLines());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setJustificationMode(textView.getJustificationMode());
        }
        if (textView.getEllipsize() != null && textView.getKeyListener() == null) {
            builder.setEllipsize(textView.getEllipsize())
                .setEllipsizedWidth(width);
        }
        return builder.build();
    }

    /**
     * sdk<23
     */
    private static StaticLayout getStaticLayout(TextView textView, int width) {
        return new StaticLayout(textView.getText(),
            0, textView.getText().length(),
            textView.getPaint(), width, Layout.Alignment.ALIGN_NORMAL,
            textView.getLineSpacingMultiplier(),
            textView.getLineSpacingExtra(), textView.getIncludeFontPadding(), textView.getEllipsize(),
            width);
    }

}
