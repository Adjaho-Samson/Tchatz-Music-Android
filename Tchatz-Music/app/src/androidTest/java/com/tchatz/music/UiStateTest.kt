package com.tchatz.music
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.tchatz.music.ui.Empty
import org.junit.Rule
import org.junit.Test
class UiStateTest {
 @get:Rule val compose=createComposeRule()
 @Test fun emptyStateExplainsWhatToDo() {
  compose.setContent { MaterialTheme { Empty("Bibliothèque vide","Autorisez l’accès audio puis actualisez.") } }
  compose.onNodeWithText("Bibliothèque vide").assertIsDisplayed()
  compose.onNodeWithText("Autorisez l’accès audio puis actualisez.").assertIsDisplayed()
 }
}
