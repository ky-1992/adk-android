package com.google.adk.demo.ui.chat

/**
 * Minimal, dependency-free HTML sanitizer for model-generated message
 * fragments rendered inside the demo WebView.
 *
 * Defense in depth: the WebView itself runs with JavaScript disabled and
 * blocks every navigation — this sanitizer strips the dangerous markup
 * *before* it ever reaches the WebView.
 */
object HtmlSanitizer {

    // <script ...>...</script> or self-closing <script ... />
    private val scriptBlockRegex = Regex(
        "(?is)<\\s*script\\b[^>]*>.*?<\\s*/\\s*script\\s*>|<\\s*script\\b[^>]*/\\s*>",
    )

    // <!DOCTYPE ...>, <html>/<head>/<body> wrappers — replies must be fragments.
    private val documentStructureRegex = Regex(
        "(?is)<!\\s*doctype[^>]*>|<\\s*/?\\s*(html|head|body)\\b[^>]*>",
    )

    // Scriptable / interactive containers.
    private val dangerousBlockRegex = Regex(
        "(?is)<\\s*(/?)\\s*(script|iframe|object|embed|applet|frame|frameset|form|base|link|meta|style|template)\\b[^>]*>",
    )

    // Any inline event handler attribute: onclick, onerror, onmouseover, ...
    private val eventAttributeRegex = Regex(
        "(?i)\\son[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)",
    )

    // javascript: / vbscript: URLs in navigation-ish attributes.
    private val scriptUrlRegex = Regex(
        "(?i)\\s(href|src|action|formaction|xlink:href)\\s*=\\s*" +
            "(\"\\s*(javascript|vbscript|data\\s*:\\s*text/html)[^\"]*\"|" +
            "'\\s*(javascript|vbscript|data\\s*:\\s*text/html)[^']*'|" +
            "(javascript|vbscript|data\\s*:\\s*text/html)[^\\s>]*)",
    )

    // Heuristic: does the text contain at least one renderable HTML tag?
    private val htmlTagDetectionRegex = Regex(
        "(?i)<\\s*(h[1-6]|p|ul|ol|li|table|thead|tbody|tr|td|th|pre|code|blockquote|" +
            "strong|em|b|i|u|s|a|br|hr|div|span|img|small|sub|sup|caption|figure|figcaption)\\b",
    )

    /** True when [text] contains HTML markup worth rendering in a WebView. */
    fun looksLikeHtml(text: String): Boolean = htmlTagDetectionRegex.containsMatchIn(text)

    /** True when [text] should be rendered through [HtmlMessageBubble]. */
    fun isRenderableHtml(text: String): Boolean =
        looksLikeHtml(text) && sanitize(text).isNotBlank()

    /**
     * Strips scripts, event handlers, script URLs and document wrappers from
     * [html]. Never throws; returns the cleaned fragment (trimmed).
     */
    fun sanitize(html: String): String {
        var out = html
        out = out.replace(scriptBlockRegex, "")
        out = out.replace(documentStructureRegex, "")
        out = out.replace(dangerousBlockRegex, "")
        out = out.replace(eventAttributeRegex, "")
        out = out.replace(scriptUrlRegex, "")
        return out.trim()
    }
}
