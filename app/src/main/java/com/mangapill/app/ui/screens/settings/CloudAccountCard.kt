package com.mangapill.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Sync
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mangapill.app.data.sync.CloudSync
import com.mangapill.app.ui.components.IconTile
import com.mangapill.app.ui.components.PillButton
import com.mangapill.app.ui.components.SectionLabel
import com.mangapill.app.ui.components.SettingsCard
import com.mangapill.app.ui.components.glass.liquidGlass
import com.mangapill.app.ui.components.glass.rememberPressScale
import com.mangapill.app.ui.theme.PurpleAccent
import com.mangapill.app.ui.theme.TextMuted
import com.mangapill.app.ui.theme.TextPrimary
import com.mangapill.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** Small YouTube-style avatar button on the top corner: shows user initial or person icon. */
@Composable
fun AccountButton(cloud: CloudSync, modifier: Modifier = Modifier) {
    val status by cloud.status.collectAsState()
    if (!status.configured) return

    var openDialog by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, 0.88f)
    val shape = CircleShape
    val initial = status.email?.trim()?.firstOrNull()?.uppercase()

    Box(
        modifier = modifier
            .size(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .then(
                if (initial != null) Modifier.background(PurpleAccent, shape)
                else Modifier.liquidGlass(shape, 0.08f, base = Color(0x66000000))
            )
            .clickable(interactionSource = interaction, indication = null) { openDialog = true },
        contentAlignment = Alignment.Center
    ) {
        if (initial != null) {
            Text(
                initial,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
        } else {
            Icon(
                Icons.Outlined.Person,
                contentDescription = "Account",
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (openDialog) {
        Dialog(onDismissRequest = { openDialog = false }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF161622))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (status.email != null) "Account & Sync" else "Sign In",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { openDialog = false }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close", tint = TextMuted)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    CloudAccountCard(cloud, inDialog = true)
                }
            }
        }
    }
}

/** Settings & Dialog: sign in to keep reading progress, My List and settings the same on every device. */
@Composable
fun CloudAccountCard(cloud: CloudSync, inDialog: Boolean = false) {
    val status by cloud.status.collectAsState()
    if (!status.configured) return

    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otpCode by rememberSaveable { mutableStateOf("") }
    var showOtp by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun submit(create: Boolean) {
        if (busy) return
        if (!Regex("^\\S+@\\S+\\.\\S+$").matches(email.trim())) { message = "Enter your email address."; return }
        if (password.length < 6) { message = "The password needs at least 6 characters."; return }
        busy = true
        message = null
        scope.launch {
            try {
                if (create) {
                    if (cloud.signUp(email, password)) {
                        showOtp = true
                        message = "Confirmation code sent to your email. Enter the 6-digit code below:"
                    }
                } else {
                    cloud.signIn(email, password)
                }
                password = ""
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "Something went wrong. Try again."
            } finally {
                busy = false
            }
        }
    }

    fun verifyCode() {
        if (busy) return
        if (!Regex("^\\S+@\\S+\\.\\S+$").matches(email.trim())) { message = "Enter your email address."; return }
        if (otpCode.trim().isEmpty()) { message = "Enter the 6-digit verification code."; return }
        busy = true
        message = null
        scope.launch {
            try {
                cloud.verifyOtp(email, otpCode.trim())
                showOtp = false
                otpCode = ""
                password = ""
                message = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "Verification failed. Check the code and try again."
            } finally {
                busy = false
            }
        }
    }

    fun resend() {
        if (busy) return
        if (!Regex("^\\S+@\\S+\\.\\S+$").matches(email.trim())) { message = "Enter your email address."; return }
        busy = true
        message = null
        scope.launch {
            try {
                cloud.resendOtp(email)
                message = "A new verification code was sent to your email."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "Could not resend code."
            } finally {
                busy = false
            }
        }
    }

    val content = @Composable {
        Column(if (inDialog) Modifier else Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.CloudSync)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        status.email ?: "Sync between devices",
                        color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            status.email == null -> "Sign in to keep your reading progress, My List and settings the same on every device."
                            status.syncing -> "Syncing…"
                            else -> syncedLabel(status.lastSyncedAt)
                        },
                        color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (status.email != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        "Sync now", { scope.launch { cloud.syncNow() } }, Modifier.weight(1f),
                        icon = Icons.Outlined.Sync, background = PurpleAccent, contentColor = Color.White
                    )
                    PillButton("Sign out", { scope.launch { cloud.signOut() } }, Modifier.weight(1f), icon = Icons.Outlined.Logout)
                }
            } else if (showOtp) {
                val colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PurpleAccent,
                    unfocusedBorderColor = TextMuted.copy(alpha = 0.35f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = PurpleAccent
                )
                Text(
                    "Enter the 6-digit confirmation code sent to $email:",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = otpCode, onValueChange = { otpCode = it }, singleLine = true,
                    placeholder = { Text("123456", color = TextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = colors
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        if (busy) "Verifying…" else "Verify & Sign In", { verifyCode() }, Modifier.weight(1f),
                        icon = Icons.Outlined.Key, background = PurpleAccent, contentColor = Color.White
                    )
                    PillButton("Resend", { resend() }, Modifier.weight(1f), icon = Icons.Outlined.Send)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "← Back to Sign In",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { showOtp = false; message = null }
                        .padding(vertical = 4.dp)
                )
            } else {
                val colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PurpleAccent,
                    unfocusedBorderColor = TextMuted.copy(alpha = 0.35f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = PurpleAccent
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, singleLine = true,
                    placeholder = { Text("Email", color = TextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = colors
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, singleLine = true,
                    placeholder = { Text("Password", color = TextMuted) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = colors
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        if (busy) "Please wait…" else "Sign in", { submit(create = false) }, Modifier.weight(1f),
                        background = PurpleAccent, contentColor = Color.White
                    )
                    PillButton("Create account", { submit(create = true) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Have a confirmation code? Verify here",
                    color = TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showOtp = true; message = null }
                        .padding(vertical = 4.dp)
                )
            }

            val problem = status.error?.let { "Sync problem: $it" }
            (message ?: problem)?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = if (message != null && status.error == null) PurpleAccent else Color(0xFFE5484D), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }

    if (inDialog) {
        content()
    } else {
        SectionLabel("Account & Sync")
        SettingsCard { content() }
        Spacer(Modifier.height(26.dp))
    }
}

private fun syncedLabel(at: Long?): String {
    if (at == null) return "Not synced yet"
    val minutes = (System.currentTimeMillis() - at) / 60_000
    return when {
        minutes < 1 -> "Synced just now"
        minutes < 60 -> "Synced $minutes min ago"
        else -> "Synced " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(at))
    }
}
