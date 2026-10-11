package org.opensources.courses.feature.voice.presentation.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.opensources.courses.R

@Composable
fun VoiceHoldButton(enabled: Boolean, listening: Boolean, processing: Boolean, onStart: () -> Unit, onStop: () -> Unit, onCancel: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val active = pressed || listening
    val scale by animateFloatAsState(if (active) 1.8f else 1f, label = "microphoneScale")
    val description = when {
        processing -> stringResource(R.string.voice_processing)
        listening -> stringResource(R.string.voice_listening)
        else -> stringResource(R.string.voice_hold)
    }
    // Reserve the enlarged size in every state so the screen edge never clips the button.
    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        if (active) VoiceListeningHalo()
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier.size(56.dp).graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = description
                if (!enabled || processing) disabled()
                // A semantic click supports accessibility without consuming the physical press.
                onClick {
                    if (listening) onStop() else if (enabled && !processing) onStart()
                    true
                }
            }.voicePress(enabled, processing, onStart, onStop, onCancel) { pressed = it },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_microphone), stringResource(R.string.voice_hold), Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun Modifier.voicePress(
    enabled: Boolean, processing: Boolean,
    onStart: () -> Unit, onStop: () -> Unit, onCancel: () -> Unit,
    onPressed: (Boolean) -> Unit,
): Modifier {
    val start by rememberUpdatedState(onStart)
    val stop by rememberUpdatedState(onStop)
    val cancel by rememberUpdatedState(onCancel)
    val busy by rememberUpdatedState(processing)
    val pressed by rememberUpdatedState(onPressed)
    return pointerInput(enabled) {
        detectTapGestures(onPress = {
            if (!enabled || busy) return@detectTapGestures
            pressed(true)
            start()
            var released = false
            try {
                released = tryAwaitRelease()
            } finally {
                pressed(false)
                if (released) stop() else cancel()
            }
        })
    }
}
