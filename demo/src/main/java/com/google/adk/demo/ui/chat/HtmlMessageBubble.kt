package com.google.adk.demo.ui.chat

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.adk.demo.ui.theme.CarMinTouchTargetDp
import com.google.adk.demo.ui.theme.LocalIsCarUi

/**
 * Renders an assistant message as a sanitized HTML fragment inside a WebView.
 *
 * Safety: HTML is passed through [HtmlSanitizer] first and the WebView runs
 * with JavaScript disabled and every navigation blocked, so model output can
 * never execute scripts or navigate away.
 *
 * Styling: the fragment is wrapped in assets/message_template.html whose
 * palette mirrors the Compose car/phone themes (ui/theme/Color.kt) — dark
 * anti-glare car palette with a 1.2x base font on car UI, stock phone palette
 * otherwise. The WebView body is transparent so the bubble color comes from
 * the Compose theme.
 */
@Composable
fun HtmlMessageBubble(
    html: String,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 300.dp,
) {
    val isCarUi = LocalIsCarUi.current
    val context = LocalContext.current
    val sanitized = remember(html) { HtmlSanitizer.sanitize(html) }
    val pageHtml = remember(sanitized, isCarUi) { buildMessagePage(context, sanitized, isCarUi) }

    Box(
        modifier = modifier
            .widthIn(max = maxWidth)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // Automotive spacing: keep the bubble at least at the car touch
            // target height even for one-liners (no interactive controls
            // inside; spacing rule from the car UI guideline).
            .heightIn(min = if (isCarUi) CarMinTouchTargetDp.dp else Dp.Unspecified)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
            factory = { ctx -> createMessageWebView(ctx) },
            update = { webView ->
                webView.loadDataWithBaseURL(null, pageHtml, "text/html", "utf-8", null)
            },
        )
    }
}

private fun createMessageWebView(context: Context): WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
    // Transparent so the Compose bubble background shows through.
    setBackgroundColor(Color.TRANSPARENT)
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
    settings.apply {
        // Hard requirement: model output must never execute scripts.
        javaScriptEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        cacheMode = WebSettings.LOAD_NO_CACHE
        mediaPlaybackRequiresUserGesture = true
    }
    webViewClient = object : WebViewClient() {
        // Block every navigation (links inside the reply must not take over
        // the message view). The initial data: load does not pass through
        // here, so rendering is unaffected.
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            true

        override fun onPageFinished(view: WebView, url: String?) {
            // Nudge Compose to re-measure once content height is known.
            view.requestLayout()
        }
    }
}

/** Style variables derived from the theme palettes in ui/theme/Color.kt. */
private data class MessageStyleVars(
    val baseFontPx: Int,
    val fg: String,
    val accent: String,
    val muted: String,
    val card: String,
    val cardBorder: String,
    val codeBg: String,
)

// Automotive (ui/theme/Color.kt car palette).
private val CarMessageStyle = MessageStyleVars(
    baseFontPx = 19, // ≈ CarTypography.bodyLarge 19sp (1.2x scale)
    fg = "#DFE9EE", // CarOnBackground
    accent = "#7FD8FF", // CarPrimary
    muted = "#BFC9CE", // CarOnSurfaceVariant
    card = "#242D31", // CarSurfaceContainerHigh
    cardBorder = "#40484D", // CarSurfaceVariant / CarOutlineVariant
    codeBg = "#161D20", // CarSurfaceContainerLow
)

// Phone (ui/theme/Color.kt phone palette).
private val PhoneMessageStyle = MessageStyleVars(
    baseFontPx = 16, // ≈ Material3 default bodyLarge 16sp
    fg = "#1A1B1F", // PhoneOnSurface
    accent = "#1565C0", // PhonePrimary
    muted = "#44474F", // PhoneOnSurfaceVariant
    card = "#E0E2EC", // PhoneSurfaceVariant
    cardBorder = "#74777F", // PhoneOutline
    codeBg = "#F0F2F6", // light derived
)

/** Reads the asset template and substitutes theme variables + the body. */
private fun buildMessagePage(context: Context, body: String, isCarUi: Boolean): String {
    val template = try {
        context.assets.open("message_template.html").bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        // The asset ships with the APK; this is only a defensive fallback.
        return "<html><body style=\"color:${(if (isCarUi) CarMessageStyle else PhoneMessageStyle).fg}\">$body</body></html>"
    }
    val vars = if (isCarUi) CarMessageStyle else PhoneMessageStyle
    return template
        .replace("{{BASE_FONT_PX}}", vars.baseFontPx.toString())
        .replace("{{FG}}", vars.fg)
        .replace("{{ACCENT}}", vars.accent)
        .replace("{{MUTED}}", vars.muted)
        .replace("{{CARD}}", vars.card)
        .replace("{{CARD_BORDER}}", vars.cardBorder)
        .replace("{{CODE_BG}}", vars.codeBg)
        .replace("{{BODY}}", body)
}
