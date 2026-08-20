package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.LoadingScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoadingScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingScreenShowsProgressAndTitle() {
        composeRule.setContent {
            LoadingScreen(progress = 42)
        }

        composeRule.onNodeWithText("42%").assertIsDisplayed()
        composeRule.onNodeWithText("Загрузка АУДИОframes…").assertIsDisplayed()
    }
}
