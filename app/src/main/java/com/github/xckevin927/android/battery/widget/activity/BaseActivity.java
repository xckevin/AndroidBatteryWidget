package com.github.xckevin927.android.battery.widget.activity;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean light = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                != android.content.res.Configuration.UI_MODE_NIGHT_YES;
        androidx.core.view.WindowInsetsControllerCompat bars =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(light);
        bars.setAppearanceLightNavigationBars(light);
        if (android.os.Build.VERSION.SDK_INT < 23) getWindow().setStatusBarColor(0xff142c27);
        if (android.os.Build.VERSION.SDK_INT < 26) getWindow().setNavigationBarColor(0xff142c27);
        View content = findViewById(android.R.id.content);
        View decorContentParent = findViewById(androidx.appcompat.R.id.decor_content_parent);
        View insetTarget = decorContentParent != null ? decorContentParent : content;
        int initialPaddingLeft = insetTarget.getPaddingLeft();
        int initialPaddingTop = insetTarget.getPaddingTop();
        int initialPaddingRight = insetTarget.getPaddingRight();
        int initialPaddingBottom = insetTarget.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(insetTarget, (view, windowInsets) -> {
            if (windowInsets.isConsumed()) {
                return windowInsets;
            }
            Insets systemBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            Insets keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(
                    initialPaddingLeft + systemBars.left,
                    initialPaddingTop + systemBars.top,
                    initialPaddingRight + systemBars.right,
                    initialPaddingBottom + Math.max(systemBars.bottom, keyboard.bottom)
            );
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        WidgetUpdateService.syncMonitoring(getApplicationContext());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
