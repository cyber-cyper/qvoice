@file:Suppress("unused")
package androidx.compose.ui.text.input
class TextFieldValue(val text: String = "")
fun interface VisualTransformation { fun filter(text: String): String }
