package com.messagebackup.app.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.messagebackup.app.ui.util.HapticHelper

data class OnboardingStep(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badge: String,
    val highlights: List<String>
)

/**
 * First-run interactive onboarding walkthrough highlighting:
 * 1. 100% Offline Privacy Guarantee
 * 2. Multi-Format Backups & Evidentiary Legal PDF
 * 3. Media Vault & Lossless SMS Restoration
 * 4. O.D.I.N. AI Navigator & Multi-Cloud Sync
 */
@Composable
fun OnboardingWalkthroughDialog(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var currentStepIndex by remember { mutableIntStateOf(0) }

    val steps = listOf(
        OnboardingStep(
            title = "Privacy-First Architecture",
            subtitle = "Your personal conversations never touch external servers without your permission.",
            icon = Icons.Default.Security,
            badge = "100% OFFLINE BY DEFAULT",
            highlights = listOf(
                "Zero third-party tracking or mandatory logins",
                "AES-256 password encryption for backup files",
                "Optional Biometric / PIN App Lock protection"
            )
        ),
        OnboardingStep(
            title = "Multi-Format & Legal Export",
            subtitle = "Export your text history to beautiful documents ready for archival, print, or legal evidence.",
            icon = Icons.Default.FolderZip,
            badge = "7 EXPORT FORMATS",
            highlights = listOf(
                "Court-admissible PDF with SHA-256 forensic verification",
                "Lossless JSON & XML for full phone SMS restoration",
                "HTML, Excel/CSV, Markdown, and Plain Text"
            )
        ),
        OnboardingStep(
            title = "Media Vault & Cloud Sync",
            subtitle = "Keep all your photos, voice notes, and videos organized and safely preserved.",
            icon = Icons.Default.PermMedia,
            badge = "COMPLETE PRESERVATION",
            highlights = listOf(
                "Media Vault gallery with instant filtering & bulk ZIP",
                "Automatic nightly backup while phone charges",
                "Sync to Google Drive, OneDrive, Dropbox, or WebDAV/NAS"
            )
        ),
        OnboardingStep(
            title = "Meet O.D.I.N. Copilot",
            subtitle = "Omniscient Document & Insights Navigator is your private assistant across messages.",
            icon = Icons.Default.AutoAwesome,
            badge = "SMART AI COPILOT",
            highlights = listOf(
                "Instantly summarize long conversation threads",
                "Find door codes, flight info, addresses & dates",
                "Runs on local Ollama or personal Gemini key"
            )
        )
    )

    val currentStep = steps[currentStepIndex]
    val isLastStep = currentStepIndex == steps.size - 1

    Dialog(
        onDismissRequest = { /* Require user to finish or skip */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header badge
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = currentStep.badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Icon container
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = currentStep.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "OnboardingContent"
                ) { step ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = step.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                step.highlights.forEach { item ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Step indicator dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == currentStepIndex) 18.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (index == currentStepIndex) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            HapticHelper.performTick(context)
                            onComplete()
                        }
                    ) {
                        Text("Skip")
                    }

                    Button(
                        onClick = {
                            if (isLastStep) {
                                HapticHelper.performSuccess(context)
                                onComplete()
                            } else {
                                HapticHelper.performTick(context)
                                currentStepIndex++
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isLastStep) "Get Started" else "Next")
                    }
                }
            }
        }
    }
}
