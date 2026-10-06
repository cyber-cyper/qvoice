@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.compose.material3

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.InlineTextContent
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.ScrollState

@RequiresOptIn("This material API is experimental and is likely to change or to be removed in the future.")
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalMaterial3Api

// ---- theme ----
class ColorScheme {
    val primary: Color get() = TODO(); val onPrimary: Color get() = TODO()
    val primaryContainer: Color get() = TODO(); val onPrimaryContainer: Color get() = TODO()
    val inversePrimary: Color get() = TODO()
    val secondary: Color get() = TODO(); val onSecondary: Color get() = TODO()
    val secondaryContainer: Color get() = TODO(); val onSecondaryContainer: Color get() = TODO()
    val tertiary: Color get() = TODO(); val onTertiary: Color get() = TODO()
    val tertiaryContainer: Color get() = TODO(); val onTertiaryContainer: Color get() = TODO()
    val background: Color get() = TODO(); val onBackground: Color get() = TODO()
    val surface: Color get() = TODO(); val onSurface: Color get() = TODO()
    val surfaceVariant: Color get() = TODO(); val onSurfaceVariant: Color get() = TODO()
    val surfaceTint: Color get() = TODO(); val inverseSurface: Color get() = TODO(); val inverseOnSurface: Color get() = TODO()
    val error: Color get() = TODO(); val onError: Color get() = TODO()
    val errorContainer: Color get() = TODO(); val onErrorContainer: Color get() = TODO()
    val outline: Color get() = TODO(); val outlineVariant: Color get() = TODO(); val scrim: Color get() = TODO()
    val surfaceBright: Color get() = TODO(); val surfaceDim: Color get() = TODO()
    val surfaceContainer: Color get() = TODO(); val surfaceContainerHigh: Color get() = TODO(); val surfaceContainerHighest: Color get() = TODO()
    val surfaceContainerLow: Color get() = TODO(); val surfaceContainerLowest: Color get() = TODO()
}
class Typography {
    val displayLarge: TextStyle get() = TODO(); val displayMedium: TextStyle get() = TODO(); val displaySmall: TextStyle get() = TODO()
    val headlineLarge: TextStyle get() = TODO(); val headlineMedium: TextStyle get() = TODO(); val headlineSmall: TextStyle get() = TODO()
    val titleLarge: TextStyle get() = TODO(); val titleMedium: TextStyle get() = TODO(); val titleSmall: TextStyle get() = TODO()
    val bodyLarge: TextStyle get() = TODO(); val bodyMedium: TextStyle get() = TODO(); val bodySmall: TextStyle get() = TODO()
    val labelLarge: TextStyle get() = TODO(); val labelMedium: TextStyle get() = TODO(); val labelSmall: TextStyle get() = TODO()
}
class Shapes
object MaterialTheme {
    val colorScheme: ColorScheme @Composable @ReadOnlyComposable get() = TODO()
    val typography: Typography @Composable @ReadOnlyComposable get() = TODO()
    val shapes: Shapes @Composable @ReadOnlyComposable get() = TODO()
}
@Composable fun MaterialTheme(colorScheme: ColorScheme = MaterialTheme.colorScheme, shapes: Shapes = MaterialTheme.shapes, typography: Typography = MaterialTheme.typography, content: @Composable () -> Unit) {}

fun lightColorScheme(
    primary: Color = TODO(), onPrimary: Color = TODO(), primaryContainer: Color = TODO(), onPrimaryContainer: Color = TODO(),
    inversePrimary: Color = TODO(), secondary: Color = TODO(), onSecondary: Color = TODO(), secondaryContainer: Color = TODO(),
    onSecondaryContainer: Color = TODO(), tertiary: Color = TODO(), onTertiary: Color = TODO(), tertiaryContainer: Color = TODO(),
    onTertiaryContainer: Color = TODO(), background: Color = TODO(), onBackground: Color = TODO(), surface: Color = TODO(),
    onSurface: Color = TODO(), surfaceVariant: Color = TODO(), onSurfaceVariant: Color = TODO(), surfaceTint: Color = primary,
    inverseSurface: Color = TODO(), inverseOnSurface: Color = TODO(), error: Color = TODO(), onError: Color = TODO(),
    errorContainer: Color = TODO(), onErrorContainer: Color = TODO(), outline: Color = TODO(), outlineVariant: Color = TODO(),
    scrim: Color = TODO(), surfaceBright: Color = TODO(), surfaceContainer: Color = TODO(), surfaceContainerHigh: Color = TODO(),
    surfaceContainerHighest: Color = TODO(), surfaceContainerLow: Color = TODO(), surfaceContainerLowest: Color = TODO(), surfaceDim: Color = TODO(),
): ColorScheme = TODO()
fun darkColorScheme(
    primary: Color = TODO(), onPrimary: Color = TODO(), primaryContainer: Color = TODO(), onPrimaryContainer: Color = TODO(),
    inversePrimary: Color = TODO(), secondary: Color = TODO(), onSecondary: Color = TODO(), secondaryContainer: Color = TODO(),
    onSecondaryContainer: Color = TODO(), tertiary: Color = TODO(), onTertiary: Color = TODO(), tertiaryContainer: Color = TODO(),
    onTertiaryContainer: Color = TODO(), background: Color = TODO(), onBackground: Color = TODO(), surface: Color = TODO(),
    onSurface: Color = TODO(), surfaceVariant: Color = TODO(), onSurfaceVariant: Color = TODO(), surfaceTint: Color = primary,
    inverseSurface: Color = TODO(), inverseOnSurface: Color = TODO(), error: Color = TODO(), onError: Color = TODO(),
    errorContainer: Color = TODO(), onErrorContainer: Color = TODO(), outline: Color = TODO(), outlineVariant: Color = TODO(),
    scrim: Color = TODO(), surfaceBright: Color = TODO(), surfaceContainer: Color = TODO(), surfaceContainerHigh: Color = TODO(),
    surfaceContainerHighest: Color = TODO(), surfaceContainerLow: Color = TODO(), surfaceContainerLowest: Color = TODO(), surfaceDim: Color = TODO(),
): ColorScheme = TODO()

// ---- text / icons ----
@Composable fun Text(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified, fontStyle: FontStyle? = null, fontWeight: FontWeight? = null, fontFamily: FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified, textDecoration: TextDecoration? = null, textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified, overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE, minLines: Int = 1, onTextLayout: ((TextLayoutResult) -> Unit)? = null, style: TextStyle = TODO()) {}
@Composable fun Text(text: AnnotatedString, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified, fontStyle: FontStyle? = null, fontWeight: FontWeight? = null, fontFamily: FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified, textDecoration: TextDecoration? = null, textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified, overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE, minLines: Int = 1, inlineContent: Map<String, InlineTextContent> = mapOf(), onTextLayout: (TextLayoutResult) -> Unit = {}, style: TextStyle = TODO()) {}

@Composable fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = TODO()) {}
@Composable fun Icon(bitmap: ImageBitmap, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = TODO()) {}
@Composable fun Icon(painter: Painter, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = TODO()) {}
@ExperimentalMaterial3Api @Composable fun Icon(painter: Painter, tint: ColorProducer?, contentDescription: String?, modifier: Modifier = Modifier) {}

class IconButtonColors
@Composable fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, colors: IconButtonColors = TODO(), interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit) {}

// ---- buttons ----
class ButtonColors
class ButtonElevation
object ButtonDefaults {
    @Composable fun buttonColors(): ButtonColors = TODO()
    @Composable fun buttonColors(containerColor: Color = Color.Unspecified, contentColor: Color = Color.Unspecified, disabledContainerColor: Color = Color.Unspecified, disabledContentColor: Color = Color.Unspecified): ButtonColors = TODO()
    @Composable fun textButtonColors(): ButtonColors = TODO()
    @Composable fun textButtonColors(containerColor: Color = Color.Unspecified, contentColor: Color = Color.Unspecified, disabledContainerColor: Color = Color.Unspecified, disabledContentColor: Color = Color.Unspecified): ButtonColors = TODO()
}
@Composable fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = TODO(), colors: ButtonColors = TODO(), elevation: ButtonElevation? = TODO(), border: BorderStroke? = null, contentPadding: PaddingValues = TODO(), interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {}
@Composable fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = TODO(), colors: ButtonColors = TODO(), elevation: ButtonElevation? = null, border: BorderStroke? = TODO(), contentPadding: PaddingValues = TODO(), interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {}
@Composable fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = TODO(), colors: ButtonColors = TODO(), elevation: ButtonElevation? = null, border: BorderStroke? = null, contentPadding: PaddingValues = TODO(), interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {}

// ---- cards ----
class CardColors
class CardElevation
object CardDefaults {
    @Composable fun cardColors(): CardColors = TODO()
    @Composable fun cardColors(containerColor: Color = Color.Unspecified, contentColor: Color = Color.Unspecified, disabledContainerColor: Color = Color.Unspecified, disabledContentColor: Color = Color.Unspecified): CardColors = TODO()
}
@Composable fun Card(modifier: Modifier = Modifier, shape: Shape = TODO(), colors: CardColors = TODO(), elevation: CardElevation = TODO(), border: BorderStroke? = null, content: @Composable ColumnScope.() -> Unit) {}
@Composable fun Card(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = TODO(), colors: CardColors = TODO(), elevation: CardElevation = TODO(), border: BorderStroke? = null, interactionSource: MutableInteractionSource? = null, content: @Composable ColumnScope.() -> Unit) {}
@Composable fun ElevatedCard(modifier: Modifier = Modifier, shape: Shape = TODO(), colors: CardColors = TODO(), elevation: CardElevation = TODO(), content: @Composable ColumnScope.() -> Unit) {}
@Composable fun ElevatedCard(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = TODO(), colors: CardColors = TODO(), elevation: CardElevation = TODO(), interactionSource: MutableInteractionSource? = null, content: @Composable ColumnScope.() -> Unit) {}

// ---- chips ----
class SelectableChipColors
class SelectableChipElevation
@Composable fun FilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, leadingIcon: @Composable (() -> Unit)? = null, trailingIcon: @Composable (() -> Unit)? = null, shape: Shape = TODO(), colors: SelectableChipColors = TODO(), elevation: SelectableChipElevation? = TODO(), border: BorderStroke? = TODO(), interactionSource: MutableInteractionSource? = null) {}

// ---- dialogs ----
@Composable fun AlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit, modifier: Modifier = Modifier, dismissButton: @Composable (() -> Unit)? = null, icon: @Composable (() -> Unit)? = null, title: @Composable (() -> Unit)? = null, text: @Composable (() -> Unit)? = null, shape: Shape = TODO(), containerColor: Color = TODO(), iconContentColor: Color = TODO(), titleContentColor: Color = TODO(), textContentColor: Color = TODO(), tonalElevation: Dp = TODO(), properties: DialogProperties = DialogProperties()) {}
@Deprecated("Use BasicAlertDialog instead")
@ExperimentalMaterial3Api @Composable fun AlertDialog(onDismissRequest: () -> Unit, modifier: Modifier = Modifier, properties: DialogProperties = DialogProperties(), content: @Composable () -> Unit) {}

// ---- selection controls ----
class RadioButtonColors
@Composable fun RadioButton(selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true, colors: RadioButtonColors = TODO(), interactionSource: MutableInteractionSource? = null) {}

class SliderColors
class SliderState
@ExperimentalMaterial3Api @Composable fun Slider(state: SliderState, modifier: Modifier = Modifier, enabled: Boolean = true, colors: SliderColors = TODO(), interactionSource: MutableInteractionSource = TODO(), thumb: @Composable (SliderState) -> Unit = {}, track: @Composable (SliderState) -> Unit = {}) {}
@ExperimentalMaterial3Api @Composable fun Slider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, onValueChangeFinished: (() -> Unit)? = null, colors: SliderColors = TODO(), interactionSource: MutableInteractionSource = TODO(), steps: Int = 0, thumb: @Composable (SliderState) -> Unit = {}, track: @Composable (SliderState) -> Unit = {}, valueRange: ClosedFloatingPointRange<Float> = 0f..1f) {}
@Composable fun Slider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0, onValueChangeFinished: (() -> Unit)? = null, colors: SliderColors = TODO(), interactionSource: MutableInteractionSource = TODO()) {}

class TextFieldColors
@Composable fun OutlinedTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, readOnly: Boolean = false, textStyle: TextStyle = TODO(), label: @Composable (() -> Unit)? = null, placeholder: @Composable (() -> Unit)? = null, leadingIcon: @Composable (() -> Unit)? = null, trailingIcon: @Composable (() -> Unit)? = null, prefix: @Composable (() -> Unit)? = null, suffix: @Composable (() -> Unit)? = null, supportingText: @Composable (() -> Unit)? = null, isError: Boolean = false, visualTransformation: VisualTransformation = TODO(), keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default, singleLine: Boolean = false, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE, minLines: Int = 1, interactionSource: MutableInteractionSource? = null, shape: Shape = TODO(), colors: TextFieldColors = TODO()) {}
@Composable fun OutlinedTextField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, readOnly: Boolean = false, textStyle: TextStyle = TODO(), label: @Composable (() -> Unit)? = null, placeholder: @Composable (() -> Unit)? = null, leadingIcon: @Composable (() -> Unit)? = null, trailingIcon: @Composable (() -> Unit)? = null, prefix: @Composable (() -> Unit)? = null, suffix: @Composable (() -> Unit)? = null, supportingText: @Composable (() -> Unit)? = null, isError: Boolean = false, visualTransformation: VisualTransformation = TODO(), keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default, singleLine: Boolean = false, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE, minLines: Int = 1, interactionSource: MutableInteractionSource? = null, shape: Shape = TODO(), colors: TextFieldColors = TODO()) {}

// ---- progress (visible overloads in 1.3.2; the others are DeprecationLevel.HIDDEN) ----
@Composable fun LinearProgressIndicator(progress: () -> Float, modifier: Modifier = Modifier, color: Color = TODO(), trackColor: Color = TODO(), strokeCap: StrokeCap = TODO(), gapSize: Dp = TODO(), drawStopIndicator: DrawScope.() -> Unit = {}) {}
@Composable fun LinearProgressIndicator(modifier: Modifier = Modifier, color: Color = TODO(), trackColor: Color = TODO(), strokeCap: StrokeCap = TODO(), gapSize: Dp = TODO()) {}
@Composable fun CircularProgressIndicator(modifier: Modifier = Modifier, color: Color = TODO(), strokeWidth: Dp = TODO(), trackColor: Color = TODO(), strokeCap: StrokeCap = TODO()) {}
@Deprecated("Use the overload that takes `progress` as a lambda")
@Composable fun LinearProgressIndicator(progress: Float, modifier: Modifier = Modifier, color: Color = TODO(), trackColor: Color = TODO(), strokeCap: StrokeCap = TODO()) {}

// ---- lists / scaffold / app bar ----
class ListItemColors
@Composable fun ListItem(headlineContent: @Composable () -> Unit, modifier: Modifier = Modifier, overlineContent: @Composable (() -> Unit)? = null, supportingContent: @Composable (() -> Unit)? = null, leadingContent: @Composable (() -> Unit)? = null, trailingContent: @Composable (() -> Unit)? = null, colors: ListItemColors = TODO(), tonalElevation: Dp = TODO(), shadowElevation: Dp = TODO()) {}

@JvmInline value class FabPosition(val value: Int) { companion object { val End: FabPosition get() = TODO() } }
@Composable fun Scaffold(modifier: Modifier = Modifier, topBar: @Composable () -> Unit = {}, bottomBar: @Composable () -> Unit = {}, snackbarHost: @Composable () -> Unit = {}, floatingActionButton: @Composable () -> Unit = {}, floatingActionButtonPosition: FabPosition = FabPosition.End, containerColor: Color = TODO(), contentColor: Color = TODO(), contentWindowInsets: WindowInsets = TODO(), content: @Composable (PaddingValues) -> Unit) {}

class TopAppBarColors
interface TopAppBarScrollBehavior
@ExperimentalMaterial3Api @Composable fun TopAppBar(title: @Composable () -> Unit, modifier: Modifier = Modifier, navigationIcon: @Composable () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {}, expandedHeight: Dp = TODO(), windowInsets: WindowInsets = TODO(), colors: TopAppBarColors = TODO(), scrollBehavior: TopAppBarScrollBehavior? = null) {}
class MenuItemColors
@Composable fun BottomAppBar(modifier: Modifier = Modifier, containerColor: Color = TODO(), contentColor: Color = TODO(), tonalElevation: Dp = TODO(), contentPadding: PaddingValues = TODO(), windowInsets: WindowInsets = TODO(), content: @Composable RowScope.() -> Unit) {}
@Composable fun FilledIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = TODO(), colors: IconButtonColors = TODO(), interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit) {}
@Composable fun DropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, offset: DpOffset = TODO(), scrollState: ScrollState = TODO(), properties: PopupProperties = TODO(), shape: Shape = TODO(), containerColor: Color = TODO(), tonalElevation: Dp = TODO(), shadowElevation: Dp = TODO(), border: BorderStroke? = null, content: @Composable ColumnScope.() -> Unit) {}
@Composable fun DropdownMenuItem(text: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier, leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null, enabled: Boolean = true, colors: MenuItemColors = TODO(), contentPadding: PaddingValues = TODO(), interactionSource: MutableInteractionSource? = null) {}
