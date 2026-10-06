@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.compose.foundation.lazy
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@DslMarker annotation class LazyScopeMarker

@LazyScopeMarker interface LazyListScope {
    fun item(key: Any? = null, contentType: Any? = null, content: @Composable LazyItemScope.() -> Unit)
    fun items(count: Int, key: ((index: Int) -> Any)? = null, contentType: (index: Int) -> Any? = { null }, itemContent: @Composable LazyItemScope.(index: Int) -> Unit)
    fun stickyHeader(key: Any? = null, contentType: Any? = null, content: @Composable LazyItemScope.(Int) -> Unit)
}
@Stable @LazyScopeMarker interface LazyItemScope {
    fun Modifier.fillParentMaxWidth(fraction: Float = 1f): Modifier = TODO()
}
inline fun <T> LazyListScope.items(items: List<T>, noinline key: ((item: T) -> Any)? = null, noinline contentType: (item: T) -> Any? = { null }, crossinline itemContent: @Composable LazyItemScope.(item: T) -> Unit) {}
inline fun <T> LazyListScope.items(items: Array<T>, noinline key: ((item: T) -> Any)? = null, noinline contentType: (item: T) -> Any? = { null }, crossinline itemContent: @Composable LazyItemScope.(item: T) -> Unit) {}

class LazyListState(firstVisibleItemIndex: Int = 0, firstVisibleItemScrollOffset: Int = 0) {
    suspend fun animateScrollToItem(index: Int, scrollOffset: Int = 0) {}
    suspend fun scrollToItem(index: Int, scrollOffset: Int = 0) {}
}
@Composable fun rememberLazyListState(initialFirstVisibleItemIndex: Int = 0, initialFirstVisibleItemScrollOffset: Int = 0): LazyListState = TODO()
inline fun <T> LazyListScope.itemsIndexed(items: List<T>, noinline key: ((index: Int, item: T) -> Any)? = null, crossinline contentType: (index: Int, item: T) -> Any? = { _, _ -> null }, crossinline itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit) {}
@Composable fun LazyColumn(modifier: Modifier = Modifier, state: LazyListState = TODO(), contentPadding: PaddingValues = PaddingValues(0.dp), reverseLayout: Boolean = false, verticalArrangement: Arrangement.Vertical = Arrangement.Top, horizontalAlignment: Alignment.Horizontal = Alignment.Start, flingBehavior: FlingBehavior = TODO(), userScrollEnabled: Boolean = true, overscrollEffect: OverscrollEffect? = TODO(), content: LazyListScope.() -> Unit) {}
@Composable fun LazyRow(modifier: Modifier = Modifier, state: LazyListState = TODO(), contentPadding: PaddingValues = PaddingValues(0.dp), reverseLayout: Boolean = false, horizontalArrangement: Arrangement.Horizontal = Arrangement.Start, verticalAlignment: Alignment.Vertical = Alignment.Top, flingBehavior: FlingBehavior = TODO(), userScrollEnabled: Boolean = true, overscrollEffect: OverscrollEffect? = TODO(), content: LazyListScope.() -> Unit) {}
