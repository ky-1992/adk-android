// adk-core/src/main/java/com/google/adk/runner/RunConfig.kt
package com.google.adk.runner

data class RunConfig(
    val maxToolIterations: Int = 10,
    val streaming: Boolean = true,
    val temperature: Float? = null,
    val topP: Float? = null,
)

data class UserMessage(
    val content: String,
    val images: List<ImagePart> = emptyList(),
)

data class ImagePart(
    val mimeType: String,
    val data: String, // base64 encoded
)