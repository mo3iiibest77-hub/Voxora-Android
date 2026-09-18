package com.voxora.app.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxora.app.R
import com.voxora.app.ui.theme.VoxoraColors
import com.voxora.app.ui.theme.VoxoraTheme
import com.voxora.core.prefs.UsagePrefs
import com.voxora.core.prefs.UserPrefs
import com.voxora.core.cloud.CloudProjectUsage
import com.voxora.core.usage.ApiUsageSnapshot
import com.voxora.core.usage.GeminiKeyProbe
import com.voxora.core.usage.GeminiKeyProbeResult
import com.voxora.core.usage.GeminiKeyStatus
import com.voxora.core.usage.GeminiUsageLedger
import com.voxora.core.usage.GeminiUsageMetadata
import com.voxora.core.usage.UsageMetric
import com.voxora.core.usage.UsageUnavailable
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Google's own rate-limit page. The documented rule is that limits apply **per project, not per
 * API key**, so this is the only place a real limit can be read — an API key cannot read it and
 * Voxora must never estimate it.
 */
private const val AI_STUDIO_RATE_LIMIT_URL = "https://aistudio.google.com/rate-limit?timeRange=last-28-days"

/**
 * What Voxora knows about the configured Gemini API key, and what it honestly cannot know.
 *
 * ## Why this screen is explicit about gaps
 * Gemini quota, billing and project identity belong to the **Google Cloud project**, not to an API
 * key. An API key cannot be used to read them. Rather than printing a reassuring `0`, this screen
 * names each unavailable figure and says why, and it never implies that a signed-in Google account
 * owns the configured key.
 *
 * Rate limits are the same story: the documented rule is that they apply **per project, not per
 * API key**, so a real limit can only be read in AI Studio. The project card links there rather
 * than estimating, and it states the rule so a second key is never mistaken for a second quota.
 *
 * Everything shown as a number was observed: either by Voxora itself (its own request counts and
 * whatever token usage the server reported) or by the key check, which calls the documented
 * `models.list` endpoint — a non-generative call that consumes no tokens.
 */
@Composable
fun ApiUsageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { UserPrefs(context) }
    val usagePrefs = remember { UsagePrefs(context) }
    val probe = remember { GeminiKeyProbe() }
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf("") }
    var probeResult by remember { mutableStateOf<GeminiKeyProbeResult?>(null) }
    var probing by remember { mutableStateOf(false) }
    val ledger by usagePrefs.ledger.collectAsStateWithLifecycle(initialValue = GeminiUsageLedger())
    // The same singleton Settings uses, so the account, project and key shown here are the ones
    // the user actually selected.
    val repository = rememberCloudRepository(context)
    val cloudAuth by repository.auth.collectAsStateWithLifecycle()
    val cloudSelection by repository.selection.collectAsStateWithLifecycle()
    val cloudUsage by repository.usage.collectAsStateWithLifecycle()

    // Read the project's usage when the selection changes, and only while a Cloud grant is held:
    // an API key cannot read project usage, and asking would only produce a refusal.
    LaunchedEffect(cloudAuth, cloudSelection.selectedProject?.projectId) {
        if (cloudAuth.isAuthorized && cloudSelection.selectedProject != null) repository.refreshUsage()
    }

    LaunchedEffect(Unit) {
        apiKey = prefs.apiKey.first()
    }

    ApiUsageContent(
        snapshot = ApiUsageSnapshot.assemble(
            apiKey = apiKey,
            // The account shown is the one Cloud authorization actually established, so it can
            // never be a leftover address from a sign-out.
            accountEmail = cloudSelection.accountEmail,
            probe = probeResult,
            ledger = ledger,
            nowMillis = System.currentTimeMillis(),
        ),
        probing = probing,
        cloudProjectLabel = cloudSelection.selectedProject?.label,
        cloudUsage = cloudUsage,
        onRefreshCloudUsage = repository::refreshUsage,
        onTest = {
            // Ignore a second tap while a check is in flight, so overlapping probes cannot race
            // each other's results onto the screen.
            if (!probing) {
                scope.launch {
                    probing = true
                    probeResult = try {
                        probe.probe(apiKey)
                    } finally {
                        probing = false
                    }
                }
            }
        },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun ApiUsageContent(
    snapshot: ApiUsageSnapshot,
    probing: Boolean,
    cloudProjectLabel: String?,
    cloudUsage: CloudProjectUsage?,
    onRefreshCloudUsage: () -> Unit,
    onTest: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "top-bar") {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = colors.onSurface,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.usage_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
            }
        }

        item(key = "key-header") { UsageSectionHeader(stringResource(R.string.usage_section_key)) }
        item(key = "key") {
            UsageCard {
                UsageRow(
                    label = stringResource(R.string.usage_key_label),
                    value = snapshot.maskedKey.ifBlank { stringResource(R.string.usage_key_missing) },
                    emphasised = snapshot.keyConfigured,
                )
                UsageNote(stringResource(R.string.usage_key_help))
            }
        }

        item(key = "connection-header") { UsageSectionHeader(stringResource(R.string.usage_section_connection)) }
        item(key = "connection") {
            UsageCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UsageStatusDot(status = snapshot.connection)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = connectionLabel(snapshot.connection),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                }
                UsageMetricRow(
                    label = stringResource(R.string.usage_models_available),
                    metric = snapshot.modelsAvailable,
                )
                OutlinedButton(
                    onClick = onTest,
                    enabled = !probing && snapshot.keyConfigured,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (probing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.usage_testing))
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.usage_test_connection))
                    }
                }
            }
        }

        item(key = "observed-header") { UsageSectionHeader(stringResource(R.string.usage_section_observed)) }
        item(key = "observed-ring") {
            // The headline: the observed request count, with the one ratio the dashboard can
            // actually measure drawn around it. Nothing here is compared against a Google quota,
            // because Google does not report one for this figure.
            UsageCard {
                UsageRing(
                    requests = snapshot.requestsThisMonth,
                    successes = snapshot.successesThisMonth,
                    share = snapshot.successShare(),
                )
            }
        }
        item(key = "observed") {
            UsageCard {
                UsageMetricRow(stringResource(R.string.usage_requests_today), snapshot.requestsToday)
                UsageMetricRow(stringResource(R.string.usage_requests_month), snapshot.requestsThisMonth)
                UsageMetricRow(stringResource(R.string.usage_tokens_input), snapshot.inputTokens)
                UsageMetricRow(stringResource(R.string.usage_tokens_output), snapshot.outputTokens)
                UsageMetricRow(stringResource(R.string.usage_tokens_total), snapshot.totalTokens)
                UsageMetricRow(
                    label = stringResource(R.string.usage_token_coverage),
                    metric = snapshot.tokenCoverage,
                    knownText = { reported ->
                        stringResource(
                            R.string.usage_coverage_value,
                            reported.toInt(),
                            (snapshot.requestsThisMonth.knownValue ?: 0L).toInt(),
                        )
                    },
                )
                UsageNote(stringResource(R.string.usage_observed_help))
            }
        }

        item(key = "activity-header") { UsageSectionHeader(stringResource(R.string.usage_section_activity)) }
        item(key = "activity") {
            UsageCard {
                UsageRow(
                    label = stringResource(R.string.usage_last_success),
                    value = snapshot.lastSuccessAtMillis?.let(::formatTimestamp)
                        ?: stringResource(R.string.usage_never),
                )
                UsageRow(
                    label = stringResource(R.string.usage_last_failure),
                    value = snapshot.lastFailureAtMillis?.let(::formatTimestamp)
                        ?: stringResource(R.string.usage_never),
                )
                UsageRow(
                    label = stringResource(R.string.usage_last_error),
                    value = snapshot.lastErrorCategory ?: stringResource(R.string.usage_never),
                )
            }
        }

        item(key = "project-header") { UsageSectionHeader(stringResource(R.string.usage_google_section)) }
        item(key = "project") {
            UsageCard {
                if (cloudProjectLabel != null && cloudUsage != null) {
                    // Google's own report for the selected project. Every figure is present only
                    // when the API actually returned it; a gap names its reason instead of a zero.
                    UsageRow(
                        label = stringResource(R.string.settings_project_section),
                        value = cloudProjectLabel,
                        emphasised = true,
                    )
                    UsageNote(stringResource(R.string.usage_google_source, cloudProjectLabel))
                    UsageMetricRow(
                        label = stringResource(R.string.usage_google_requests),
                        metric = cloudUsage.requests,
                    )
                    UsageMetricRow(
                        label = stringResource(R.string.usage_google_tokens_input),
                        metric = cloudUsage.inputTokens,
                    )
                    UsageMetricRow(
                        label = stringResource(R.string.usage_google_tokens_output),
                        metric = cloudUsage.outputTokens,
                    )
                    UsageMetricRow(
                        label = stringResource(R.string.usage_project_quota),
                        metric = cloudUsage.quotaLimit,
                    )
                    UsageMetricRow(
                        label = stringResource(R.string.usage_google_quota_remaining),
                        metric = cloudUsage.quotaRemaining,
                    )
                    UsageMetricRow(
                        label = stringResource(R.string.usage_billing),
                        metric = cloudUsage.billing,
                    )
                    OutlinedButton(
                        onClick = onRefreshCloudUsage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.usage_google_refresh))
                    }
                    UsageNote(stringResource(R.string.usage_billing_help))
                } else if (cloudProjectLabel != null) {
                    // A project is selected but Google's report has not arrived yet. Say that
                    // rather than showing a figure that was never read.
                    UsageRow(
                        label = stringResource(R.string.settings_project_section),
                        value = cloudProjectLabel,
                    )
                    UsageNote(stringResource(R.string.usage_google_loading))
                } else {
                    // No project selected: say so, and keep the API-key limits honest rather
                    // than pretending a key can read project quota.
                    UsageRow(
                        label = stringResource(R.string.usage_section_project),
                        value = stringResource(R.string.usage_project_unknown),
                    )
                    UsageNote(stringResource(R.string.usage_google_no_project))
                    UsageNote(stringResource(R.string.usage_project_help))
                    UsageMetricRow(
                        label = stringResource(R.string.usage_project_quota),
                        metric = snapshot.projectQuota,
                    )
                    UsageNote(stringResource(R.string.usage_project_quota_help))
                    UsageMetricRow(
                        label = stringResource(R.string.usage_billing),
                        metric = snapshot.billing,
                    )
                    UsageNote(stringResource(R.string.usage_billing_help))
                    if (snapshot.accountEmail != null) {
                        UsageNote(stringResource(R.string.usage_account_not_linked))
                    }
                }
                // The real rate limits live in AI Studio and are applied per project, not per key;
                // an API key cannot read them. Link to the official page rather than invent a
                // number, and say the rule plainly so "a second key" is never mistaken for a
                // second quota.
                UsageNote(stringResource(R.string.settings_key_rate_limit_note))
                OutlinedButton(
                    onClick = { uriHandler.openUri(AI_STUDIO_RATE_LIMIT_URL) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_ai_studio_usage))
                }
            }
        }

        item(key = "privacy") {
            Text(
                text = stringResource(R.string.usage_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

// ---- building blocks ---------------------------------------------------------------

@Composable
private fun UsageSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun UsageCard(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun UsageRow(label: String, value: String, emphasised: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasised) colors.primary else colors.onSurface,
        )
    }
}

/**
 * A figure that may be a real observation or an explicit reason it is missing.
 *
 * [knownText] lets a caller render a known value in a richer form than a bare number, which is how
 * the token-coverage row shows "2 of 12".
 */
@Composable
private fun UsageMetricRow(
    label: String,
    metric: UsageMetric,
    knownText: (@Composable (Long) -> String)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val value = when (metric) {
        is UsageMetric.Known -> knownText?.invoke(metric.value) ?: metric.value.toString()
        is UsageMetric.Missing -> reasonLabel(metric.reason)
    }
    val color = when (metric) {
        is UsageMetric.Known -> colors.onSurface
        is UsageMetric.Missing -> toneColor(UsageStatusVisual.tone(metric.reason))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

@Composable
private fun UsageNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = VoxoraColors.explanation,
    )
}

/** Diameter and stroke of the usage ring, in dp. */
private const val RING_SIZE_DP = 148
private const val RING_STROKE_DP = 12

/** At or above this share the ring reads as healthy rather than as a warning. */
private const val SUCCESS_RING_THRESHOLD = 0.99f

/**
 * The usage ring.
 *
 * A ring only means something when there is a real fraction to draw, so this one is fed the single
 * ratio the dashboard can measure: how many of Voxora's **own observed requests** this month
 * succeeded. The centre carries the observed request count — "current usage" — and the arc carries
 * the share of those that succeeded.
 *
 * It is deliberately **not** a quota gauge. Google does not report a quota for this figure, so the
 * ring never divides the count by a limit that was never read; when either figure is unknown the
 * arc is not drawn at all and the caption says nothing was recorded, because an arc at zero (or at
 * full) would be a percentage nobody measured.
 */
@Composable
private fun UsageRing(
    requests: UsageMetric,
    successes: UsageMetric,
    share: Float?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val track = colors.outlineVariant
    val arc = when {
        share == null -> track
        share >= SUCCESS_RING_THRESHOLD -> VoxoraColors.success
        share > 0f -> VoxoraColors.warning
        else -> VoxoraColors.danger
    }
    val centre = requests.knownValue?.toString() ?: stringResource(R.string.usage_unavailable_unknown)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(RING_SIZE_DP.dp)) {
                val stroke = RING_STROKE_DP.dp.toPx()
                val inset = stroke / 2f
                val diameter = size.minDimension - stroke
                drawArc(
                    color = track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (share != null) {
                    drawArc(
                        color = arc,
                        startAngle = -90f,
                        sweepAngle = 360f * share,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = centre,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Text(
                    text = stringResource(R.string.usage_requests_month),
                    style = MaterialTheme.typography.labelSmall,
                    color = VoxoraColors.explanation,
                )
            }
        }
        Text(
            text = if (share == null) {
                stringResource(R.string.usage_ring_empty)
            } else {
                stringResource(
                    R.string.usage_ring_success,
                    (successes.knownValue ?: 0L).toInt(),
                    (requests.knownValue ?: 0L).toInt(),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = VoxoraColors.explanation,
        )
    }
}

/**
 * Connection light.
 *
 * Reuses the product's semantic roles, so a healthy key is the same green as active narration and a
 * refused one is the same red as a stopped Reader. The breath runs only while the connection is
 * healthy — it is not composed for any other state.
 */
@Composable
private fun UsageStatusDot(status: GeminiKeyStatus?) {
    val color = toneColor(UsageStatusVisual.tone(status))
    Box(
        // Decorative: the connection label beside it already states the status, so a screen
        // reader should announce the words rather than an empty indicator.
        modifier = Modifier
            .size(18.dp)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (UsageStatusVisual.pulses(status)) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.25f)),
            )
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
private fun toneColor(tone: UsageTone): Color = when (tone) {
    UsageTone.OK -> VoxoraColors.success
    UsageTone.WARNING -> VoxoraColors.warning
    UsageTone.ERROR -> VoxoraColors.danger
    UsageTone.NEUTRAL -> VoxoraColors.neutral
}

@Composable
private fun connectionLabel(status: GeminiKeyStatus?): String = when (status) {
    null -> stringResource(R.string.usage_not_checked)
    GeminiKeyStatus.CONNECTED -> stringResource(R.string.usage_status_connected)
    GeminiKeyStatus.INVALID_KEY -> stringResource(R.string.usage_status_invalid_key)
    GeminiKeyStatus.UNAUTHORIZED -> stringResource(R.string.usage_status_unauthorized)
    GeminiKeyStatus.QUOTA_LIMITED -> stringResource(R.string.usage_status_quota_limited)
    GeminiKeyStatus.NETWORK_UNAVAILABLE -> stringResource(R.string.usage_status_network)
    GeminiKeyStatus.SERVICE_ERROR -> stringResource(R.string.usage_status_service_error)
    GeminiKeyStatus.CONFIGURATION_INCOMPLETE -> stringResource(R.string.usage_status_not_configured)
    GeminiKeyStatus.UNKNOWN -> stringResource(R.string.usage_status_unknown)
}

@Composable
private fun reasonLabel(reason: UsageUnavailable): String = when (reason) {
    UsageUnavailable.NOT_CONFIGURED -> stringResource(R.string.usage_unavailable_not_configured)
    UsageUnavailable.AUTH_REQUIRED -> stringResource(R.string.usage_unavailable_auth_required)
    UsageUnavailable.PERMISSION_DENIED -> stringResource(R.string.usage_unavailable_permission_denied)
    UsageUnavailable.NETWORK_ERROR -> stringResource(R.string.usage_unavailable_network)
    UsageUnavailable.NOT_OFFERED -> stringResource(R.string.usage_unavailable_not_offered)
    UsageUnavailable.UNSUPPORTED -> stringResource(R.string.usage_unavailable_unsupported)
    UsageUnavailable.UNKNOWN -> stringResource(R.string.usage_unavailable_unknown)
}

/** Locale-aware so the timestamp reads correctly in Persian and under RTL. */
private fun formatTimestamp(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

// ---- previews ----------------------------------------------------------------------

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ApiUsageScreenPreview(modifier: Modifier = Modifier) {
    val now = 1_760_000_000_000L
    VoxoraTheme {
        Surface(modifier = modifier) {
            ApiUsageContent(
                snapshot = ApiUsageSnapshot.assemble(
                    apiKey = "AIzaSyD1234567890ABCD",
                    accountEmail = null,
                    probe = GeminiKeyProbeResult(GeminiKeyStatus.CONNECTED, modelCount = 48),
                    ledger = GeminiUsageLedger().apply {
                        recordSuccess(now, GeminiUsageMetadata(120, 480, 600))
                        recordSuccess(now, null)
                        recordFailure(now - 60_000, "network")
                    },
                    nowMillis = now,
                ),
                probing = false,
                cloudProjectLabel = "Alpha project",
                cloudUsage = CloudProjectUsage.observed(
                    projectId = "alpha-123",
                    requests = 1_284,
                    quotaLimit = 3_000,
                ),
                onRefreshCloudUsage = {},
                onTest = {},
                onBack = {},
            )
        }
    }
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ApiUsageScreenUnconfiguredPreview(modifier: Modifier = Modifier) {
    VoxoraTheme {
        Surface(modifier = modifier) {
            ApiUsageContent(
                snapshot = ApiUsageSnapshot.assemble(
                    apiKey = null,
                    accountEmail = "owner@example.com",
                    probe = null,
                    ledger = GeminiUsageLedger(),
                    nowMillis = 1_760_000_000_000L,
                ),
                probing = false,
                cloudProjectLabel = null,
                cloudUsage = null,
                onRefreshCloudUsage = {},
                onTest = {},
                onBack = {},
            )
        }
    }
}
