package com.termux.x11.input;

import android.app.Activity;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;

import com.termux.x11.InputHostRegistry;
import com.termux.x11.InputPreferencesRegistry;
import com.termux.x11.LorieView;
import com.termux.x11.Prefs;

/** Connects the unmodified upstream gesture pipeline to an embedded LorieView. */
public final class EmbeddedInputController implements AutoCloseable {
    private final Activity activity;
    private final LorieView view;
    private final Prefs prefs;
    private final TouchInputHandler handler;

    public EmbeddedInputController(Activity activity, LorieView view) {
        this.activity = activity;
        this.view = view;
        this.prefs = new Prefs(activity);
        InputPreferencesRegistry.set(prefs);
        InputHost host = new InputHost() {
            @Override public Activity activity() { return activity; }
            @Override public LorieView view() { return view; }
            @Override public void toggleKeyboard() {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                view.requestFocus();
            }
        };
        InputHostRegistry.set(host);
        handler = new TouchInputHandler(host, new InputEventSender(view));
        handler.reloadPreferences(prefs);
        installListeners();
        view.setPointerIcon(PointerIcon.getSystemIcon(activity, PointerIcon.TYPE_NULL));
    }

    private void installListeners() {
        view.setFocusableInTouchMode(true);
        view.setOnTouchListener(this::handleMotion);
        view.setOnHoverListener(this::handleMotion);
        view.setOnGenericMotionListener(this::handleMotion);
        view.setOnCapturedPointerListener(this::handleMotion);
        view.setOnKeyListener((v, keyCode, event) -> handler.sendKeyEvent(event));
        view.setCallback(handler::handleInputTransformChanged);
    }

    private boolean handleMotion(View source, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) source.requestUnbufferedDispatch(event);
        return handler.handleTouchEvent(source, view, event);
    }

    public void reloadPreferences() {
        handler.reloadPreferences(prefs);
        view.applyPreferences();
    }

    @Override public void close() {
        handler.setCapturingEnabled(false);
        view.setOnTouchListener(null);
        view.setOnHoverListener(null);
        view.setOnGenericMotionListener(null);
        view.setOnCapturedPointerListener(null);
        view.setOnKeyListener(null);
        InputHostRegistry.clear();
        InputPreferencesRegistry.clear();
    }
}