package com.google.adk.demo.ui.chat

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ChatScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chatScreen_showsInputBar() {
        composeTestRule.setContent {
            ChatScreen()
        }
        // Input bar placeholder text should be visible
        composeTestRule.onNodeWithText("Type a message...").assertExists()
    }
}