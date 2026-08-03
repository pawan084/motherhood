package com.aira.companion.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Urgent
import com.aira.companion.ui.theme.UrgentMist

@Composable
fun UrgentHelpDialog(
    onDismiss: () -> Unit,
    onOpenEmergencyProfile: () -> Unit,
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Ivory)
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close urgent help")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    modifier = Modifier.size(90.dp),
                    color = UrgentMist,
                    contentColor = Urgent,
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Urgent.copy(alpha = 0.32f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.LocalHospital,
                            contentDescription = null,
                            modifier = Modifier.size(42.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                Text(
                    text = "Urgent help",
                    style = MaterialTheme.typography.labelLarge,
                    color = Urgent,
                )
                Spacer(modifier = Modifier.height(9.dp))
                Text(
                    text = "Please contact your care team now.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Do not wait for an AI response if you feel seriously unwell or are worried about your baby.",
                    modifier = Modifier.fillMaxWidth(0.9f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(34.dp))

                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:+911140001234")),
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Urgent,
                            contentColor = Paper,
                        ),
                    shape = RoundedCornerShape(17.dp),
                ) {
                    Icon(Icons.Outlined.Phone, contentDescription = null)
                    Spacer(modifier = Modifier.size(9.dp))
                    Text("Call care team")
                }

                Spacer(modifier = Modifier.height(11.dp))

                OutlinedButton(
                    onClick = onOpenEmergencyProfile,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                    shape = RoundedCornerShape(17.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Urgent),
                ) {
                    Icon(Icons.Outlined.HealthAndSafety, contentDescription = null, tint = Urgent)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Open emergency profile", color = Urgent)
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 26.dp),
            ) {
                Text("I’m safe for now", color = InkMuted)
            }
        }
    }
}
