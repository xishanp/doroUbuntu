package com.termux.x11.input;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompositionStateTest {
    @Test public void replacingCompositionDeletesPreviousCodePoints() {
        CompositionState state = new CompositionState();
        assertEquals(0, state.replace("你"));
        assertEquals(1, state.replace("你好"));
        assertEquals(2, state.commit("您好"));
        assertEquals(0, state.commit("!"));
    }

    @Test public void emojiCountsAsOneDeletion() {
        CompositionState state = new CompositionState();
        state.replace("😀");
        assertEquals(1, state.cancel());
        assertEquals(0, state.cancel());
    }
}