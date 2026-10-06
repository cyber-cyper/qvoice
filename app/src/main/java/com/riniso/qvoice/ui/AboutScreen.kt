package com.riniso.qvoice.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riniso.qvoice.BuildConfig
import com.riniso.qvoice.R

/**
 * One third-party component shown on the About screen. GPL-3.0 and the
 * Apache/MIT licences of the components all require their notices to travel
 * with the app; this list plus the texts in assets/licenses/ is how they do.
 */
data class Component(
    val name: String,
    val holder: String,
    val licence: String,
    val licenceAsset: String,
)

val COMPONENTS = listOf(
    Component("QVoice", "Riniso", "GPL-3.0-or-later", "licenses/GPL-3.0.txt"),
    Component("sherpa-onnx 1.13.8", "Xiaomi Corporation and the k2-fsa contributors", "Apache-2.0", "licenses/Apache-2.0.txt"),
    Component("eSpeak NG", "Jonathan Duddington, Reece H. Dunn and contributors", "GPL-3.0-or-later", "licenses/GPL-3.0.txt"),
    Component("piper-phonemize", "Michael Hansen", "MIT", "licenses/MIT-piper-phonemize.txt"),
    Component("ONNX Runtime", "Microsoft Corporation", "MIT", "licenses/MIT-onnxruntime.txt"),
    Component("KittenTTS nano 0.8 voice", "KittenML", "Apache-2.0", "licenses/Apache-2.0.txt"),
    // Vendored and modified (engine/sonic/Sonic.java notes the changes).
    Component("Sonic (fast speech)", "Bill Cox", "Apache-2.0", "licenses/Apache-2.0.txt"),
    Component("Apache Commons Compress, IO, Lang and Codec", "The Apache Software Foundation", "Apache-2.0", "licenses/Apache-2.0.txt"),
    Component("AndroidX, Jetpack Compose, Kotlin", "Google LLC, JetBrains s.r.o.", "Apache-2.0", "licenses/Apache-2.0.txt"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenHelp: () -> Unit) {
    val context = LocalContext.current
    var shownLicence by remember { mutableStateOf<Component?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = readablePadding(padding, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { AboutHeader() }
                item {
                    Column {
                        LinkItem(
                            icon = { Icon(painterResource(R.drawable.ic_help), contentDescription = null) },
                            title = stringResource(R.string.help_title),
                            detail = stringResource(R.string.about_help_body),
                            onClick = onOpenHelp,
                        )
                        LinkItem(
                            icon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                            title = stringResource(R.string.action_send_feedback),
                            detail = stringResource(R.string.about_feedback_body, Links.SUPPORT_EMAIL),
                            onClick = { sendFeedback(context) },
                        )
                        LinkItem(
                            icon = { Icon(Icons.Outlined.Star, contentDescription = null) },
                            title = stringResource(R.string.action_rate),
                            detail = stringResource(R.string.about_rate_body),
                            onClick = { openPlayListing(context) },
                        )
                    }
                }
                item {
                    Section(stringResource(R.string.about_privacy_title)) {
                        Text(stringResource(R.string.about_privacy_body))
                        // Play asks for the policy inside the app as well as on the listing.
                        TextButton(onClick = { openUrl(context, Links.PRIVACY_POLICY) }) {
                            Text(stringResource(R.string.action_privacy_policy))
                        }
                    }
                }
                item {
                    Section(stringResource(R.string.about_licence_title)) {
                        Text(stringResource(R.string.about_licence_body))
                    }
                }
                item {
                    Section(stringResource(R.string.about_source_title)) {
                        val url = BuildConfig.SOURCE_CODE_URL
                        if (url.isEmpty()) {
                            Text(stringResource(R.string.about_source_missing))
                        } else {
                            Text(
                                url,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(role = Role.Button) { openUrl(context, url) },
                            )
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.about_components_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                }
                items(COMPONENTS, key = { it.name }) { component ->
                    ListItem(
                        headlineContent = { Text(component.name) },
                        supportingContent = { Text("${component.licence} · ${component.holder}") },
                        trailingContent = {
                            TextButton(onClick = { shownLicence = component }) {
                                Text(stringResource(R.string.action_view_licence))
                            }
                        },
                    )
                }
            }
        }
    }

    shownLicence?.let { component ->
        val text = remember(component) { readAsset(context, component.licenceAsset) }
        AlertDialog(
            onDismissRequest = { shownLicence = null },
            confirmButton = {
                TextButton(onClick = { shownLicence = null }) { Text(stringResource(R.string.action_close)) }
            },
            title = { Text(component.name) },
            text = {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
        )
    }
}

/** Logo, name, what it is, version — the one place the full logo appears in the app. */
@Composable
private fun AboutHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.qvoice_logo),
            contentDescription = null, // the name beside it is read instead
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)),
        )
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

private fun readAsset(context: Context, path: String): String =
    runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }
        .getOrDefault("Licence text missing: $path")

/** One tappable row: an icon (decorative), what it does, and where it goes. The whole row is the target. */
@Composable
private fun LinkItem(icon: @Composable () -> Unit, title: String, detail: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        leadingContent = icon,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick),
    )
}
