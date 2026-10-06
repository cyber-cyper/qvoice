@file:Suppress("unused")
package androidx.compose.ui.text
class TextStyle {
    val fontSize: androidx.compose.ui.unit.TextUnit get() = TODO()
    val lineHeight: androidx.compose.ui.unit.TextUnit get() = TODO()
}
class AnnotatedString(val text: String, spanStyles: List<Range<SpanStyle>> = listOf(), paragraphStyles: List<Range<ParagraphStyle>> = listOf()) {
    constructor(text: String, annotations: List<Range<out Annotation>> = listOf()) : this(text, listOf<Range<SpanStyle>>(), listOf<Range<ParagraphStyle>>())
    class Range<T>(val item: T, val start: Int, val end: Int)
    interface Annotation
}
class ParagraphStyle
// The real constructor's parameters up to the ones QVoice uses, in order; the
// types this stub set doesn't declare are Any?.
class SpanStyle(
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
    fontStyle: androidx.compose.ui.text.font.FontStyle? = null,
    fontSynthesis: Any? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    fontFeatureSettings: String? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    baselineShift: Any? = null,
    textGeometricTransform: Any? = null,
    localeList: Any? = null,
    background: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
)
class TextLayoutResult {
    fun getLineForOffset(offset: Int): Int = TODO()
    fun getLineTop(lineIndex: Int): Float = TODO()
}
class InlineTextContent
