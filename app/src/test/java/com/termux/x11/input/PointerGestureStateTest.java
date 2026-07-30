package com.termux.x11.input;

import org.junit.Test;

import static org.junit.Assert.*;

public class PointerGestureStateTest {
    @Test public void twoFingerGestureSuppressesCursorUntilAllPointersAreReleased() {
        PointerGestureState state = new PointerGestureState();
        state.onDown();
        assertTrue(state.canMoveCursor(1));

        state.onPointerCountChanged(2);
        assertTrue(state.shouldScroll(2));
        assertFalse(state.canMoveCursor(2));

        state.onPointerCountChanged(1);
        assertFalse(state.canMoveCursor(1));

        state.onGestureFinished();
        state.onDown();
        assertTrue(state.canMoveCursor(1));
    }

    @Test public void draggingMovesWithOneFingerAndReleasesAtGestureEnd() {
        PointerGestureState state = new PointerGestureState();
        state.onDown();
        state.startDragging();
        assertTrue(state.isDragging());
        assertTrue(state.canMoveCursor(1));
        assertFalse(state.shouldScroll(2));
        assertTrue(state.onGestureFinished());
        assertFalse(state.isDragging());
    }

    @Test public void finishingNormalGestureDoesNotRequestButtonRelease() {
        PointerGestureState state = new PointerGestureState();
        state.onDown();
        assertFalse(state.onGestureFinished());
    }
}
