package com.java.myapplication;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

public final class DoroTerminalPane extends FrameLayout implements TerminalSessionClient, TerminalViewClient {
    private static final String TAG = "DoroTerminal";
    private final TerminalView terminalView;
    private final TerminalSession session;
    private final int touchSlop;
    private float touchDownX;
    private float touchDownY;
    private boolean touchMoved;

    public DoroTerminalPane(Context context, String shellPath, String cwd, String[] args, String[] env) {
        super(context);
        setBackgroundColor(Color.rgb(35, 35, 35));
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        terminalView = new TerminalView(context, null);
        terminalView.setTerminalViewClient(this);
        terminalView.setFocusable(true);
        terminalView.setFocusableInTouchMode(true);
        terminalView.setTextSize(22);
        terminalView.setBackgroundColor(Color.rgb(35, 35, 35));
        addView(terminalView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        session = new TerminalSession(shellPath, cwd, args, env, 5000, this);
        terminalView.attachSession(session);
        terminalView.requestFocus();
        terminalView.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchDownX = event.getX();
                    touchDownY = event.getY();
                    touchMoved = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getX() - touchDownX) > touchSlop ||
                        Math.abs(event.getY() - touchDownY) > touchSlop) touchMoved = true;
                    break;
                case MotionEvent.ACTION_UP:
                    if (!touchMoved) showKeyboard();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    touchMoved = true;
                    break;
            }
            return false;
        });
    }

    public void showKeyboard() {
        terminalView.requestFocusFromTouch();
        terminalView.post(() -> {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.restartInput(terminalView);
            imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    public void sendKey(int keyCode) {
        terminalView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        terminalView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    public void toggleCtrl() {
        terminalView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT));
        terminalView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT));
    }

    public void close() {
        session.finishIfRunning();
    }

    @Override public void onTextChanged(@NonNull TerminalSession changedSession) { terminalView.onScreenUpdated(); }
    @Override public void onTitleChanged(@NonNull TerminalSession changedSession) { }
    @Override public void onSessionFinished(@NonNull TerminalSession finishedSession) { terminalView.onScreenUpdated(); }
    @Override public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("terminal", text));
    }
    @Override public void onPasteTextFromClipboard(@Nullable TerminalSession session) {
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0)
            this.session.write(clipboard.getPrimaryClip().getItemAt(0).coerceToText(getContext()).toString());
    }
    @Override public void onBell(@NonNull TerminalSession session) { }
    @Override public void onColorsChanged(@NonNull TerminalSession session) { terminalView.invalidate(); }
    @Override public void onTerminalCursorStateChange(boolean state) { terminalView.invalidate(); }
    @Override public void setTerminalShellPid(@NonNull TerminalSession session, int pid) { }
    @Override public Integer getTerminalCursorStyle() { return TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE; }

    @Override public float onScale(float scale) {
        int size = Math.max(16, Math.min(42, Math.round(22 * scale)));
        terminalView.setTextSize(size);
        return 1f;
    }
    @Override public void onSingleTapUp(MotionEvent e) { }
    @Override public boolean shouldBackButtonBeMappedToEscape() { return false; }
    @Override public boolean shouldEnforceCharBasedInput() { return true; }
    @Override public boolean shouldUseCtrlSpaceWorkaround() { return false; }
    @Override public boolean isTerminalViewSelected() { return terminalView.hasFocus(); }
    @Override public void copyModeChanged(boolean copyMode) { }
    @Override public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) { return false; }
    @Override public boolean onKeyUp(int keyCode, KeyEvent e) { return false; }
    @Override public boolean onLongPress(MotionEvent event) { return false; }
    @Override public boolean readControlKey() { return false; }
    @Override public boolean readAltKey() { return false; }
    @Override public boolean readShiftKey() { return false; }
    @Override public boolean readFnKey() { return false; }
    @Override public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) { return false; }
    @Override public void onEmulatorSet() { terminalView.invalidate(); }

    @Override public void logError(String tag, String message) { Log.e(tag, message); }
    @Override public void logWarn(String tag, String message) { Log.w(tag, message); }
    @Override public void logInfo(String tag, String message) { Log.i(tag, message); }
    @Override public void logDebug(String tag, String message) { Log.d(tag, message); }
    @Override public void logVerbose(String tag, String message) { Log.v(tag, message); }
    @Override public void logStackTraceWithMessage(String tag, String message, Exception e) { Log.e(tag, message, e); }
    @Override public void logStackTrace(String tag, Exception e) { Log.e(tag, "Terminal failure", e); }
}