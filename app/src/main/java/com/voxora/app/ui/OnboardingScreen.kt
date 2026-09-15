package com.voxora.app.ui

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxora.app.R
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onOpenSettingsForKey: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    val titles = listOf(
        stringResource(R.string.onboarding_page1_title),
        stringResource(R.string.onboarding_page2_title),
        stringResource(R.string.onboarding_page3_title),
    )
    val bodies = listOf(
        stringResource(R.string.onboarding_page1_body),
        stringResource(R.string.onboarding_page2_body),
        stringResource(R.string.onboarding_page3_body),
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onFinished) {
                Text(stringResource(R.string.onboarding_skip), color = colors.onSurfaceVariant)
            }
        }

        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("V", color = colors.primary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.app_name),
            color = colors.primary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(24.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    titles[page],
                    color = colors.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    bodies[page],
                    color = colors.onSurfaceVariant,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 16.dp),
        ) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .size(if (pagerState.currentPage == i) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == i) colors.primary
                            else colors.onSurfaceVariant.copy(alpha = 0.35f),
                        ),
                )
            }
        }

        if (pagerState.currentPage < 2) {
            Button(
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.onboarding_next), fontWeight = FontWeight.SemiBold)
            }
        } else {
            Button(
                onClick = onFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.onboarding_get_started), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenSettingsForKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.onboarding_add_api_key), color = colors.primary)
            }
        }
    }
}
