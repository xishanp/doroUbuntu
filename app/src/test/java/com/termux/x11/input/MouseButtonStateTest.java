package com.termux.x11.input;

import android.view.MotionEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public class MouseButtonStateTest {
    @Test public void reportsOnlyChangedButtons() {
        MouseButtonState state = new MouseButtonState();
        assertArrayEquals(new int[]{1}, state.update(MotionEvent.BUTTON_PRIMARY));
        assertArrayEquals(new int[]{3}, state.update(MotionEvent.BUTTON_PRIMARY | MotionEvent.BUTTON_SECONDARY));
        assertArrayEquals(new int[]{-1}, state.update(MotionEvent.BUTTON_SECONDARY));
        assertArrayEquals(new int[]{-3}, state.releaseAll());
        assertArrayEquals(new int[0], state.releaseAll());
    }
}