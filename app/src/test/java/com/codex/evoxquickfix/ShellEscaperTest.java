package com.codex.evoxquickfix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ShellEscaperTest {
    @Test
    public void quotesSimpleValue() {
        assertEquals("'com.tk.quicksearch'", ShellEscaper.quote("com.tk.quicksearch"));
    }

    @Test
    public void escapesSingleQuote() {
        assertEquals("'a'\\''b'", ShellEscaper.quote("a'b"));
    }

    @Test
    public void rejectsNewlinesAndNul() {
        assertThrows(IllegalArgumentException.class, () -> ShellEscaper.quote("a\nb"));
        assertThrows(IllegalArgumentException.class, () -> ShellEscaper.quote("a\0b"));
    }
}
