package com.example.smscosttracker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smscosttracker.ui.components.LedgerCard
import com.example.smscosttracker.ui.components.LedgerTextButton
import com.example.smscosttracker.ui.components.PageHeader
import com.example.smscosttracker.ui.components.PrimaryButton
import com.example.smscosttracker.ui.components.ScreenBackground
import com.example.smscosttracker.ui.components.SecondaryButton
import com.example.smscosttracker.ui.theme.Ledger

@Composable
fun CrashReportScreen(
    stackTrace: String,
    onTryAgain: () -> Unit
) {
    val context = LocalContext.current
    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            PageHeader("Crash report", "Previous session")
            Spacer(Modifier.height(12.dp))
            Text(
                "The app closed unexpectedly. Share this report so the crash can be fixed.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ledger.Muted
            )
            Spacer(Modifier.height(12.dp))
            LedgerCard {
                SelectionContainer {
                    Text(
                        text = stackTrace,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        ),
                        color = Ledger.Ink
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = "Share crash report",
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "SMS Cost Tracker crash report")
                        putExtra(Intent.EXTRA_TEXT, stackTrace)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share crash report"))
                }
            )
            SecondaryButton(
                text = "Copy stack trace",
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("SMS Cost Tracker crash", stackTrace))
                },
                modifier = Modifier.padding(top = 8.dp)
            )
            LedgerTextButton(
                text = "Try again",
                onClick = onTryAgain,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
