package com.google.adk.demo.ui.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HtmlSanitizerTest {

    @Test
    fun `script blocks are removed`() {
        val out = HtmlSanitizer.sanitize(
            "<p>hi</p><script>alert(1)</script><script src=\"x.js\" />",
        )
        assertFalse(out.contains("script", ignoreCase = true))
        assertFalse(out.contains("alert", ignoreCase = true))
        assertTrue(out.contains("<p>hi</p>"))
    }

    @Test
    fun `event handler attributes are stripped`() {
        val out = HtmlSanitizer.sanitize(
            "<div onclick=\"evil()\" onerror='boom()' onMouseOver='x()' data-ok=\"1\">t</div>",
        )
        assertFalse(out.contains("onclick", ignoreCase = true))
        assertFalse(out.contains("onerror", ignoreCase = true))
        assertFalse(out.contains("onmouseover", ignoreCase = true))
        assertTrue(out.contains("data-ok"))
    }

    @Test
    fun `script urls are stripped`() {
        val out = HtmlSanitizer.sanitize(
            "<a href=\"javascript:alert(1)\">x</a><img src='javascript:alert(2)'>",
        )
        assertFalse(out.contains("javascript:", ignoreCase = true))
    }

    @Test
    fun `document structure and dangerous tags are removed`() {
        val out = HtmlSanitizer.sanitize(
            "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head>" +
                "<body><iframe src=\"x\"></iframe><style>body{}</style><h1>Title</h1></body></html>",
        )
        assertFalse(out.contains("doctype", ignoreCase = true))
        assertFalse(out.contains("<html", ignoreCase = true))
        assertFalse(out.contains("<head", ignoreCase = true))
        assertFalse(out.contains("<body", ignoreCase = true))
        assertFalse(out.contains("<iframe", ignoreCase = true))
        assertFalse(out.contains("<style", ignoreCase = true))
        assertTrue(out.contains("<h1>Title</h1>"))
    }

    @Test
    fun `safe html survives unchanged`() {
        val html = "<h1>T</h1><ul><li>a</li></ul>" +
            "<table><thead><tr><th>h</th></tr></thead></table>" +
            "<pre><code>x = 1</code></pre><p>ok <strong>bold</strong></p>"
        assertEquals(html, HtmlSanitizer.sanitize(html))
    }

    @Test
    fun `looksLikeHtml detects markup only`() {
        assertTrue(HtmlSanitizer.looksLikeHtml("<p>hi</p>"))
        assertTrue(HtmlSanitizer.looksLikeHtml("<TABLE><tr><td>1</td></tr></TABLE>"))
        assertFalse(HtmlSanitizer.looksLikeHtml("plain text reply, 1 < 2"))
        assertFalse(HtmlSanitizer.looksLikeHtml("**markdown bold** and - lists"))
    }

    @Test
    fun `isRenderableHtml falls back when sanitizing empties content`() {
        assertTrue(HtmlSanitizer.isRenderableHtml("<p>hi</p>"))
        // Only malicious content: after sanitization nothing remains, so the
        // caller must fall back to plain text instead of an empty WebView.
        assertFalse(HtmlSanitizer.isRenderableHtml("<script>alert(1)</script>"))
        assertFalse(HtmlSanitizer.isRenderableHtml("plain text"))
    }
}
