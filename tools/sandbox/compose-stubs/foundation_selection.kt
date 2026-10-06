@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.compose.foundation.selection
import androidx.compose.foundation.Indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
fun Modifier.selectable(selected: Boolean, enabled: Boolean = true, role: Role? = null, interactionSource: MutableInteractionSource? = null, onClick: () -> Unit): Modifier = TODO()
fun Modifier.selectable(selected: Boolean, interactionSource: MutableInteractionSource?, indication: Indication?, enabled: Boolean = true, role: Role? = null, onClick: () -> Unit): Modifier = TODO()
