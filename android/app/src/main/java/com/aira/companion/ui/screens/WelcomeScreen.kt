package com.aira.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aira.companion.ui.components.BrandOrb
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SafetyBadge
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.SageMist

@Composable
fun WelcomeScreen(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Paper, Ivory, LilacMist.copy(alpha = 0.65f)),
                    ),
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(
                        bottom =
                            WindowInsets.navigationBars
                                .asPaddingValues()
                                .calculateBottomPadding() + 24.dp,
                    ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandOrb(compact = true)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Aira",
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink,
                )
                Spacer(modifier = Modifier.weight(1f))
                SafetyBadge(text = "Private by design")
            }

            Spacer(modifier = Modifier.height(56.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.34f),
                    shape = CircleShape,
                    shadowElevation = 18.dp,
                ) {
                    BrandOrb(modifier = Modifier.padding(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(38.dp))

            Text(
                text = "Your private AI companion for motherhood.",
                style = MaterialTheme.typography.displayMedium,
                color = Ink,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "One calm conversation for pregnancy, postpartum, and everything between.",
                style = MaterialTheme.typography.bodyLarge,
                color = InkMuted,
            )

            Spacer(modifier = Modifier.height(30.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Paper.copy(alpha = 0.76f),
                shape = RoundedCornerShape(22.dp),
                border =
                    androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.9f),
                    ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                    TrustRow(
                        icon = Icons.Outlined.Security,
                        title = "Safety checked",
                        subtitle = "Clear support and human handoff",
                    )
                    HorizontalDivider(color = Ivory)
                    TrustRow(
                        icon = Icons.Outlined.Lock,
                        title = "Your context, your control",
                        subtitle = "Review what Aira remembers",
                    )
                    HorizontalDivider(color = Ivory)
                    TrustRow(
                        icon = Icons.Outlined.VisibilityOff,
                        title = "Never used for advertising",
                        subtitle = "Sensitive health data is not an ad product",
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            PrimaryButton(
                label = "Start with Aira",
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = Icons.Outlined.ArrowForward,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Wellness support—not diagnosis or emergency care.",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
        }
    }
}

@Composable
private fun TrustRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .background(SageMist, RoundedCornerShape(13.dp))
                    .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Plum,
            )
        }
        Spacer(modifier = Modifier.width(13.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
        }
    }
}
