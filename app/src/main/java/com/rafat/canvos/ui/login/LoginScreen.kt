package com.rafat.canvos.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rafat.canvos.BuildConfig
import com.rafat.canvos.CanvosApp
import com.rafat.canvos.R
import com.rafat.canvos.data.Repository
import com.rafat.canvos.data.Session
import com.rafat.canvos.data.api.CanvasApi
import com.rafat.canvos.ui.common.userMessage
import com.rafat.canvos.ui.nav.openInBrowser
import com.rafat.canvos.ui.theme.Brand
import kotlinx.coroutines.launch

/** The only Canvas site this app signs in to; the user just supplies a token. */
// Set in gradle.properties (canvos.schoolUrl / canvos.schoolName / canvos.schoolShortName).
val SCHOOL_URL: String = BuildConfig.SCHOOL_URL
val SCHOOL_NAME: String = BuildConfig.SCHOOL_NAME
val SCHOOL_SHORT_NAME: String = BuildConfig.SCHOOL_SHORT_NAME

/** Personal tokens look like "1234~abc..."; also accept plain long tokens. */
fun cleanToken(input: String): String = input.trim().removePrefix("Bearer ").trim()

@Composable
fun LoginScreen() {
    val app = CanvosApp.instance
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var token by rememberSaveable { mutableStateOf("") }
    var showToken by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        focus.clearFocus()
        val d = SCHOOL_URL
        val t = cleanToken(token)
        if (t.length < 20) {
            error = "That doesn't look like a complete access token."
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                val api = CanvasApi(d, t, readOnly = BuildConfig.DEBUG && app.settings.state.value.readOnly)
                val repo = Repository(api)
                val me = repo.profile()
                app.signIn(Session(d, t), me)
            } catch (e: Exception) {
                error = if (e is com.rafat.canvos.data.api.UnauthorizedException)
                    "Canvas rejected this token. It may be mistyped, expired or deleted."
                else e.userMessage()
            } finally {
                busy = false
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Brand, Color(0xFF02548A), MaterialTheme.colorScheme.background), endY = 1400f),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            Image(painterResource(R.drawable.logo_canvos), contentDescription = null, modifier = Modifier.size(84.dp))
            Spacer(Modifier.height(14.dp))
            Text("Canvos", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "Your Canvas courses, without the login hassle",
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Card(
                Modifier.fillMaxWidth().widthIn(max = 520.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(6.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Sign in with an access token", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    // Fixed school: shown so it's clear where the token goes, but not editable.
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.School, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(12.dp))
                        Column {
                            Text(SCHOOL_NAME, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                SCHOOL_URL.removePrefix("https://"), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = token, onValueChange = { token = it; error = null },
                        label = { Text("Access token") },
                        leadingIcon = { Icon(Icons.Outlined.Key, null) },
                        trailingIcon = {
                            Row {
                                IconButton(onClick = {
                                    scope.launch {
                                        clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                                            ?.let { token = it.trim(); error = null }
                                    }
                                }, enabled = !busy) {
                                    Icon(Icons.Outlined.ContentPaste, "Paste")
                                }
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(if (showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showToken) "Hide" else "Show")
                                }
                            }
                        },
                        singleLine = true, enabled = !busy,
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onGo = { if (!busy) submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AnimatedVisibility(error != null) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Sign in", style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = { showHelp = !showHelp }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (showHelp) "Hide help" else "How do I get a token?")
                    }
                    AnimatedVisibility(showHelp) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val steps = listOf(
                                "Open $SCHOOL_SHORT_NAME Canvas in a browser and sign in.",
                                "Go to Account → Settings.",
                                "Under “Approved Integrations”, tap “+ New Access Token”.",
                                "Give it a purpose (e.g. “Canvos”) and an expiry date, then Generate.",
                                "Copy the token and paste it here. Canvas shows it only once.",
                            )
                            steps.forEachIndexed { i, s ->
                                Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                "The token is stored encrypted on this phone and only sent to your Canvas site.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { openInBrowser(ctx, "$SCHOOL_URL/profile/settings") }) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Open token settings on $SCHOOL_SHORT_NAME Canvas")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
