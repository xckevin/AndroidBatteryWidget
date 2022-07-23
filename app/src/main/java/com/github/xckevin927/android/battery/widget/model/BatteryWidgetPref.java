package com.github.xckevin927.android.battery.widget.model;

import com.github.xckevin927.android.battery.widget.utils.Utils;

import java.io.Serializable;

public class BatteryWidgetPref implements Serializable {
    private static final long serialVersionUID = 7328661497078024390L;

    private boolean showWallpaper = false;

    private boolean showBackground = true;

    private int backgroundColor = Utils.getDefaultBackgroundColor();

    private int backgroundColorInDarkMode = Utils.getDefaultBackgroundColorInNightMode();

    private int round = 16;

    private boolean showBackgroundProgress = false;

    private int lineWidth = 4;

    public BatteryWidgetPref() {
    }

    public boolean isShowWallpaper() {
        return showWallpaper;
    }

    public void setShowWallpaper(boolean showWallpaper) {
        this.showWallpaper = showWallpaper;
    }

    public boolean isShowBackground() {
        return showBackground;
    }

    public void setShowBackground(boolean showBackground) {
        this.showBackground = showBackground;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public int getBackgroundColorInDarkMode() {
        return backgroundColorInDarkMode;
    }

    public void setBackgroundColorInDarkMode(int backgroundColorInDarkMode) {
        this.backgroundColorInDarkMode = backgroundColorInDarkMode;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public boolean isShowBackgroundProgress() {
        return showBackgroundProgress;
    }

    public void setShowBackgroundProgress(boolean showBackgroundProgress) {
        this.showBackgroundProgress = showBackgroundProgress;
    }

    public int getLineWidth() {
        return lineWidth;
    }

    public void setLineWidth(int lineWidth) {
        this.lineWidth = lineWidth;
    }
}
