package com.github.xckevin927.android.battery.widget.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Pair;
import android.util.Size;
import android.view.Display;
import android.view.WindowManager;

import com.github.xckevin927.android.battery.widget.model.BatteryWidgetPref;
import com.github.xckevin927.android.battery.widget.model.BtDeviceState;
import com.github.xckevin927.android.battery.widget.model.PhoneBatteryState;
import com.github.xckevin927.android.battery.widget.R;

import androidx.core.content.ContextCompat;

public class Utils {

    public static Size getScreenWidth(Context context) {
        WindowManager wm = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect rect = wm.getCurrentWindowMetrics().getBounds();
            return new Size(rect.width(), rect.height());

        } else {
            Display display = wm.getDefaultDisplay();
            return new Size(display.getWidth(), display.getHeight());
        }
    }

    public static boolean isNightMode(Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int getDefaultBackgroundColor() {
        return Color.parseColor("#ddffffff");
    }

    public static int getDefaultBackgroundColorInNightMode() {
        return Color.parseColor("#dd333333");
    }


    public static Bitmap generateBatteryBitmap(Context context, PhoneBatteryState batteryState, BatteryWidgetPref widgetPref) {
        final boolean isCharging = batteryState.isAcCharge() || batteryState.isUsbCharge() || batteryState.isWirelessCharge();

        final int width = 800;
        final int height = 800;
        final float density = context.getResources().getDisplayMetrics().density;
        final float strokeWidth = widgetPref.getLineWidth() * density;

        final Bitmap indicatorIcon = BitmapFactory.decodeResource(context.getResources(),
                                                                  isCharging ? R.drawable.lightning : R.drawable.battery_saver);
        final int indicatorIconHeight = indicatorIcon.getHeight();

        Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(width / 4F);
        paint.setStrokeWidth(strokeWidth);

        if (widgetPref.isShowBackground()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Utils.isNightMode(context) ? widgetPref.getBackgroundColorInDarkMode() : widgetPref.getBackgroundColor());

            float radius = widgetPref.getRound() * density;
            canvas.drawRoundRect(0, 0, width, height, radius, radius, paint);
        }

        RectF rect = new RectF(strokeWidth + indicatorIconHeight / 2F,
                               strokeWidth + indicatorIconHeight / 2F,
                               width - strokeWidth - indicatorIconHeight / 2F,
                               height - strokeWidth - indicatorIconHeight / 2F);

        paint.setStyle(Paint.Style.STROKE);

        if (widgetPref.isShowBackgroundProgress()) {
            paint.setColor(Color.parseColor("#cccccc"));
            canvas.drawArc(rect, -90F, 360F, false, paint);
        }

        int battery = batteryState.getLevel();
        if (isCharging) {
            paint.setColor(Color.parseColor("#19bd3e"));
        } else if (batteryState.isInPowerSaveMode()) {
            paint.setColor(Color.parseColor("#fdf35f"));
        } else if (battery >= 20) {
            paint.setColor(Color.parseColor("#19bd3e"));
        } else if (battery >= 5) {
            paint.setColor(Color.parseColor("#fdf35f"));
        } else {
            paint.setColor(Color.parseColor("#e0260e"));
        }

        canvas.drawArc(rect, -90F, 360F * battery / 100F, false, paint);

        if (isCharging || batteryState.isInPowerSaveMode()) {
            canvas.drawBitmap(indicatorIcon, width / 2F - indicatorIcon.getWidth() / 2F, 0, paint);
        }

//        paint.setColor(Color.YELLOW);
        final String batteryText = battery + "%";
        float batteryTextWidth = paint.measureText(batteryText);
        float batteryTextHeight = paint.getTextSize();
        canvas.drawText(batteryText, width / 2F - batteryTextWidth / 2, height / 2F + batteryTextHeight / 2, paint);
        return b;
    }

    public static Bitmap generateBtBitmap(Context context, BtDeviceState btDeviceState, BatteryWidgetPref widgetPref) {
        final int width = 800;
        final int height = 800;
        final float density = context.getResources().getDisplayMetrics().density;

        Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        if (widgetPref.isShowBackground()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Utils.isNightMode(context) ? widgetPref.getBackgroundColorInDarkMode() : widgetPref.getBackgroundColor());

            float radius = widgetPref.getRound() * density;
            canvas.drawRoundRect(0, 0, width, height, radius, radius, paint);
        }

        Pair<Drawable, String> info = BtUtil.getBtClassDrawableWithDescription(context, btDeviceState.getBluetoothDevice());

        Rect rect = new Rect(width / 4, height / 8, width * 3 / 4, height * 5 / 8);

        canvas.drawBitmap(UiUtil.drawableToBitmap(info.first), null, rect, paint);

        TextPaint textPaint = new TextPaint();
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(width / 8F);


        textPaint.setColor(Utils.isNightMode(context) ? Color.WHITE : Color.parseColor("#333333"));
        @SuppressLint("MissingPermission") String name = btDeviceState.getBluetoothDevice().getName();
        StaticLayout layout;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            layout = StaticLayout.Builder.obtain(name, 0, name.length(), textPaint, width * 3 / 4)
                                         .setAlignment(Layout.Alignment.ALIGN_CENTER)
                                         .setMaxLines(1)
                                         .build();
        } else {
            layout = new StaticLayout(name, textPaint, width * 3 /4, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
        }
        canvas.save();
        canvas.translate((width - layout.getWidth()) / 2f, height * 5 / 8f);
        layout.draw(canvas);
        canvas.restore();

        final int nameTextHeight = layout.getHeight();

        final String batteryText = btDeviceState.getBatteryLevel() > 0 ? btDeviceState.getBatteryLevel() + "%" : "-";
        textPaint.setColor(Color.parseColor("#19bd3e"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            layout = StaticLayout.Builder.obtain(batteryText, 0, batteryText.length(), textPaint, width * 2 / 4)
                                         .setAlignment(Layout.Alignment.ALIGN_CENTER)
                                         .setMaxLines(1)
                                         .build();
        } else {
            layout = new StaticLayout(batteryText, textPaint, width * 2 /4, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
        }
        canvas.save();
        canvas.translate((width - layout.getWidth()) / 2f, height * 5 / 8f + nameTextHeight);
        layout.draw(canvas);
        canvas.restore();


        Bitmap indicatorIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_battery);
        if (indicatorIcon == null) {
            indicatorIcon = UiUtil.drawableToBitmap(ContextCompat.getDrawable(context, R.drawable.ic_battery));
        }
        final int indicatorIconHeight = indicatorIcon.getHeight();
        final int indicatorIconWidth = indicatorIcon.getWidth();

        final int destHeight = layout.getHeight();
        final int destWidth = (int) (indicatorIconWidth / (indicatorIconHeight * 1.0f / destHeight));

        Rect destRect = new Rect();
        new RectF((width - layout.getWidth() ) / 2f - destWidth/2F, height * 5 / 8f + nameTextHeight,
                  (width - layout.getWidth()) / 2f + destWidth/2F, height * 5 / 8f + nameTextHeight + destHeight)
                .round(destRect);

        canvas.drawBitmap(indicatorIcon, null, destRect, paint);

        return b;
    }
}
