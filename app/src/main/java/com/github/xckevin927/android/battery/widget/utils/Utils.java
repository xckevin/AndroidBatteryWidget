package com.github.xckevin927.android.battery.widget.utils;

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
import android.text.BoringLayout;
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

public class Utils {

    private static final int IMAGE_SIZE = 600;

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
        // Being connected to power is not synonymous with actively charging.
        final boolean isCharging = batteryState.isCharging();

        final int width = IMAGE_SIZE;
        final int height = IMAGE_SIZE;
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
        boolean batteryKnown = battery >= 0 && battery <= 100;
        if (isCharging) {
            paint.setColor(Color.parseColor("#19bd3e"));
        } else if (batteryState.isInPowerSaveMode()) {
            paint.setColor(Color.parseColor("#fdf35f"));
        } else if (!batteryKnown) {
            paint.setColor(Color.parseColor("#8a8a8a"));
        } else if (battery >= 20) {
            paint.setColor(Color.parseColor("#19bd3e"));
        } else if (battery >= 5) {
            paint.setColor(Color.parseColor("#fdf35f"));
        } else {
            paint.setColor(Color.parseColor("#e0260e"));
        }

        if (batteryKnown) {
            canvas.drawArc(rect, -90F, 360F * battery / 100F, false, paint);
        }

        if (isCharging || batteryState.isInPowerSaveMode()) {
            canvas.drawBitmap(indicatorIcon, width / 2F - indicatorIcon.getWidth() / 2F, 0, paint);
        }

        // The full unknown-state explanation is in the widget content description; this bitmap needs a compact mark.
        final String batteryText = batteryKnown ? battery + "%" : "—";
        // Ring thickness must not turn the number into thick, overlapping outlines.
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(batteryText, width / 2F,
                height / 2F - (metrics.ascent + metrics.descent) / 2F, paint);
        return b;
    }

    public static Bitmap generateBtBitmap(Context context, BtDeviceState btDeviceState, BatteryWidgetPref widgetPref) {
        final int width = IMAGE_SIZE;
        final int height = IMAGE_SIZE;
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
        Rect rect = new Rect(width / 4, height / 12, width * 3 / 4, height * 5 / 12);

        canvas.drawBitmap(UiUtil.drawableToBitmap(info.first), null, rect, paint);

        TextPaint textPaint = new TextPaint();
        textPaint.setAntiAlias(true);
        final int textWidth = width * 5 / 6;
        textPaint.setColor(Utils.isNightMode(context) ? Color.WHITE : Color.parseColor("#333333"));
        textPaint.setTextSize(width / 9F);
        String name = DevicePreferences.displayName(context, btDeviceState);
        Layout layout = singleLineLayout(name, textPaint, textWidth);
        canvas.save();
        canvas.translate((width - layout.getWidth()) / 2f, height * 6 / 12f);
        layout.draw(canvas);
        canvas.restore();

        final int nameTextHeight = layout.getHeight();
        final String status = btDeviceState.getStatus();
        final boolean usableLevel = btDeviceState.isConnected()
                && btDeviceState.getBatteryLevel() >= 0 && btDeviceState.getBatteryLevel() <= 100
                && ("available".equals(status) || "cached".equals(status) || "stale".equals(status));
        final String batteryText = usableLevel ? btDeviceState.getBatteryLevel() + "%" : "—";
        textPaint.setTextSize(width / 8F);
        textPaint.setColor("available".equals(status) ? Color.parseColor("#19bd3e")
                : "cached".equals(status) ? Color.parseColor("#d58b00")
                : "stale".equals(status) ? Color.parseColor("#c66a00")
                : Color.parseColor("#777777"));
        layout = singleLineLayout(batteryText, textPaint, textWidth);
        canvas.save();
        final float batteryTop = height * 6 / 12f + nameTextHeight;
        canvas.translate((width - layout.getWidth()) / 2f, batteryTop);
        layout.draw(canvas);
        canvas.restore();

        textPaint.setTextSize(width / 15F);
        textPaint.setColor(Utils.isNightMode(context) ? Color.LTGRAY : Color.parseColor("#555555"));
        Layout statusLayout = singleLineLayout(btVisualStatus(context, btDeviceState), textPaint, textWidth);
        canvas.save();
        canvas.translate((width - statusLayout.getWidth()) / 2f, batteryTop + layout.getHeight());
        statusLayout.draw(canvas);
        canvas.restore();

        return b;
    }

    /** Ellipsize before constructing BoringLayout: its width must never be zero in a bitmap widget. */
    private static Layout singleLineLayout(String value, TextPaint paint, int availableWidth) {
        CharSequence text = TextUtils.ellipsize(value == null ? "" : value, paint, availableWidth,
                TextUtils.TruncateAt.END);
        BoringLayout.Metrics metrics = BoringLayout.isBoring(text, paint);
        if (metrics != null) {
            return BoringLayout.make(text, paint, availableWidth, Layout.Alignment.ALIGN_CENTER,
                    1f, 0f, metrics, false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return StaticLayout.Builder.obtain(text, 0, text.length(), paint, availableWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setMaxLines(1)
                    .build();
        }
        return new StaticLayout(text, paint, availableWidth, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
    }

    private static String btVisualStatus(Context context, BtDeviceState state) {
        String status = state.getStatus();
        if ("cached".equals(status)) return context.getString(R.string.widget_bt_cached);
        if ("stale".equals(status)) return context.getString(R.string.widget_bt_stale);
        if ("disconnected".equals(status)) return context.getString(R.string.widget_bt_disconnected);
        if ("unsupported".equals(status)) return context.getString(R.string.widget_bt_unsupported);
        if ("permission_denied".equals(status)) return context.getString(R.string.widget_bt_permission);
        if ("available".equals(status)) {
            if ("framework".equals(state.getSource())) return context.getString(R.string.widget_bt_framework);
            if ("gatt".equals(state.getSource())) return context.getString(R.string.widget_bt_read);
            return context.getString(R.string.widget_bt_available);
        }
        return context.getString(R.string.widget_bt_unknown);
    }
}
