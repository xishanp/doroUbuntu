package com.termux.x11;

import org.junit.Test;
import static org.junit.Assert.*;

public class CursorUploadValidationTest {
    @Test public void cursorDimensionsMustBeInsideSharedBuffer() {
        assertTrue(CursorUploadValidation.isValid(1, 1));
        assertTrue(CursorUploadValidation.isValid(511, 511));
        assertFalse(CursorUploadValidation.isValid(0, 10));
        assertFalse(CursorUploadValidation.isValid(512, 10));
        assertFalse(CursorUploadValidation.isValid(10, 512));
    }
}