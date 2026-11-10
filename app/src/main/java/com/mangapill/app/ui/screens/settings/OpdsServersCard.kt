package com.mangapill.app.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangapill.app.data.opds.OpdsServer
import com.mangapill.app.data.repository.MangaRepository
import com.mangapill.app.data.scraper.OpdsScraper
import com.mangapill.app.ui.components.glass.PrimaryButton
import com.mangapill.app.ui.components.glass.LiquidGlassCard
import com.mangapill.app.ui.theme.PinkAccent
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

/** Add, test and remove OPDS catalogs (Komga, Kavita, Suwayomi, Stump, Codex, Calibre-Web...). */
@Composable
fun OpdsServersCard(repository: MangaRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers by repository.prefs.opdsServers.collectAsState()

    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<OpdsServer?>(null) }

    fun reset() {
        name = ""; url = ""; username = ""; password = ""; error = null; adding = false
    }

    removing?.let { server ->
        com.mangapill.app.ui.components.ConfirmDialog(
            title = "Remove ${server.name}?",
            message = "Its titles disappear from search, and you would need the address and password to add it again.",
            confirmLabel = "Remove",
            onConfirm = {
                repository.prefs.removeOpdsServer(server.id)
                removing = null
            },
            onDismiss = { removing = null }
        )
    }

    LiquidGlassCard(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("OPDS servers", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "The most stable kind of source: an open standard served by your own comic server, so it does not " +
                    "break when a website changes. Titles need OPDS page streaming (Komga, Kavita, Suwayomi, Stump and " +
                    "Codex provide it).",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            servers.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(s.url, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { removing = s }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove ${s.name}", tint = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (!adding) {
                PrimaryButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add server", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Field(name, { name = it }, "Name (e.g. Komga)")
                Field(url, { url = it }, "Catalog URL", KeyboardType.Uri)
                Text(
                    "Komga: http://host:25600/opds/v1.2/catalog · Kavita: http://host:5000/api/opds/<api key> · " +
                        "Suwayomi: http://host:4567/api/opds/v1.2",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Field(username, { username = it }, "Username (optional)")
                Field(password, { password = it }, "Password (optional)", KeyboardType.Password, secret = true)
                error?.let { Text(it, color = PinkAccent, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        onClick = {
                            val server = OpdsServer(
                                id = UUID.randomUUID().toString().take(8),
                                name = name.trim().ifBlank { OpdsScraper.origin(OpdsServer.normalizeUrl(url)).substringAfter("://") },
                                url = OpdsServer.normalizeUrl(url),
                                username = username.trim(),
                                password = password
                            )
                            testing = true
                            error = null
                            scope.launch {
                                try {
                                    // Credentials must be registered before the test request is sent.
                                    val feed = testServer(repository, server)
                                    Toast.makeText(context, "Connected to ${feed.ifBlank { server.name }}", Toast.LENGTH_SHORT).show()
                                    reset()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    error = friendlyError(e)
                                } finally {
                                    testing = false
                                }
                            }
                        },
                        enabled = !testing && url.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (testing) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Test & save", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    com.mangapill.app.ui.components.PillButton("Cancel", onClick = { if (!testing) reset() })
                }
            }
        }
    }
}

/**
 * Saves [server], then fetches its root feed. A server that does not answer with a valid OPDS feed is
 * removed again, so a typo never leaves a broken source behind. Returns the catalog title.
 */
private suspend fun testServer(repository: MangaRepository, server: OpdsServer): String {
    repository.prefs.saveOpdsServer(server)
    return try {
        OpdsScraper(server).testConnection().title
    } catch (e: Exception) {
        repository.prefs.removeOpdsServer(server.id)
        throw e
    }
}

private fun friendlyError(e: Exception): String {
    val msg = e.message.orEmpty()
    return when {
        "401" in msg || "403" in msg -> "The server refused the credentials (HTTP 401/403). Check username and password."
        "404" in msg -> "Nothing found at that address (HTTP 404). Check the catalog path."
        "CLEARTEXT" in msg.uppercase() -> "Plain HTTP was refused by the system."
        msg.isNotBlank() -> msg
        else -> "Could not reach the server"
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(label, color = TextMuted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PurpleAccent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = PurpleAccent
        )
    )
}
