package com.github.xckevin927.android.battery.widget.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.view.WindowManager;

import com.github.xckevin927.android.battery.widget.R;

public class Utils {

    public static int getScreenWidth(Context context) {
        WindowManager wm = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return wm.getCurrentWindowMetrics().getBounds().width();
        } else {
            return wm.getDefaultDisplay().getWidth();
        }
    }

    public static Bitmap generateBatteryBitmap(Context context, BatteryUtil.BatteryState batteryState) {
        final int width = 800;
        final int height = 800;
        final int strokeWidth = 6;

        final Bitmap lightning = BitmapFactory.decodeResource(context.getResources(), R.drawable.lightning);
        final int lightningHeight = lightning.getHeight();

        Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(width/4F);
        paint.setStrokeWidth(strokeWidth);
        paint.setStyle(Paint.Style.STROKE);

        RectF rect = new RectF(strokeWidth + lightningHeight/2F,
                strokeWidth + lightningHeight/2F,
                width - strokeWidth - lightningHeight/2F,
                height - strokeWidth - lightningHeight/2F);

        final boolean isCharging = batteryState.isAcCharge() || batteryState.isUsbCharge();



        int battery = batteryState.getLevel();
        if (isCharging || battery >= 20) {
            paint.setColor(Color.parseColor("#19bd3e"));
        } else if (battery >= 5) {
            paint.setColor(Color.parseColor("#fdf35f"));
        } else {
            paint.setColor(Color.parseColor("#e0260e"));
        }

        canvas.drawArc(rect, -90F, 360F * battery / 100F, false, paint);

        if (isCharging) {
            canvas.drawBitmap(lightning, width / 2F - lightning.getWidth() / 2F, 0, paint);
        }

//        paint.setColor(Color.YELLOW);
        String batteryText = battery + "%";
        float batteryTextWidth = paint.measureText(batteryText);
        float batteryTextHeight = paint.getTextSize();
        canvas.drawText(batteryText, width / 2F - batteryTextWidth / 2, height / 2F + batteryTextHeight / 2, paint);
        return b;
    }
}
