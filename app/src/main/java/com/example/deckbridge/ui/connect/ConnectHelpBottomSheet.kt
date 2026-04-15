@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.deckbridge.ui.connect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deckbridge.R
import com.example.deckbridge.ui.onboarding.OnboardingPrimaryCta
import com.example.deckbridge.ui.onboarding.OnboardingTheme

@Composable
fun ConnectHelpBottomSheet(
    onDismiss: () -> Unit,
    addAnotherHostContext: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OnboardingTheme.card,
        contentColor = OnboardingTheme.textPrimary,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(
                    if (addAnotherHostContext) {
                        R.string.connect_help_title_add_host
                    } else {
                        R.string.connect_help_title
                    },
                ),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(
                    if (addAnotherHostContext) {
                        R.string.connect_help_body_add_host
                    } else {
                        R.string.connect_help_body
                    },
                ),
                color = OnboardingTheme.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(20.dp))
            OnboardingPrimaryCta(
                text = stringResource(R.string.connect_help_close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
