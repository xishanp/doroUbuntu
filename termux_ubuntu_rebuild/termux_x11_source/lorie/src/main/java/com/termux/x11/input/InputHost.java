package com.termux.x11.input;

import android.app.Activity;
import android.util.DisplayMetrics;

import com.termux.x11.LorieView;

/** Host boundary used by the upstream input stack outside its original activity. */
public interface InputHost {
    Activity activity();
    LorieView view();

    default boolean connected() { return LorieView.connected(); }
    default void getRealMetrics(DisplayMetrics metrics) {
        if (view().getDisplay() != null) view().getDisplay().getRealMetrics(metrics);
        else activity().getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
    }
    default void setExternalKeyboardConnected(boolean connected) {}
    default void toggleKeyboard() {}
    default void toggleExtraKeys() {}
    default void toggleFullscreen() {}
    default void finish() { activity().finish(); }
}
