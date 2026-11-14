package com.drivesync.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.drivesync.app.ui.MemoCapture
import com.drivesync.app.ui.components.Chip
import com.drivesync.app.ui.components.MemoButton
import com.drivesync.app.ui.theme.DS
import com.drivesync.core.voice.SpeechState

/**
 * Screens 3-5 (DRIVING_LIMITED / ARRIVAL_PENDING) and the generic minimum mode.
 *
 * Only: destination, ETA, session status, one memo button, tap-to-stop audio.
 * No text input, no lists, no dialogue. This composable takes no callbacks other than
 * stop-audio and memo, so a forbidden interaction cannot be wired here by mistake.
 */
@Composable
fun DrivingScreen(
    destination: String?,
    etaMinutes: Int?,
    statusLine: String,
    speech: SpeechState,
    memo: MemoCapture,
    memoAllowed: Boolean,
    onStopAudio: () -> Unit,
    onMemo: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxSize()
            .clickable(enabled = speech == SpeechState.SPEAKING, onClick = onStopAudio)
            .padding(horizontal = DS.Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DS.Gap)) {
            Text(destination ?: "목적지 정보 없음", style = MaterialTheme.typography.headlineLarge)
            Text(etaMinutes?.let { "${it}분 후 도착" } ?: "ETA 미지원", style = MaterialTheme.typography.displayLarge, color = DS.Primary)
            Text(statusLine, style = MaterialTheme.typography.headlineMedium, color = DS.TextMuted)
            when (speech) {
                SpeechState.SPEAKING -> Chip("음성 브리핑 중 · 화면을 탭하면 정지", DS.Primary)
                SpeechState.PAUSED_FOR_NAVIGATION -> Chip("내비 안내 중 · 브리핑 일시정지", DS.Warning)
                SpeechState.UNAVAILABLE -> Chip("음성 출력 불가 · 정차 후 화면 브리핑", DS.Warning)
                SpeechState.IDLE -> Unit
            }
        }
        Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (memoAllowed) {
                val (label, color) = when (memo) {
                    MemoCapture.LISTENING -> "듣는 중…" to DS.Danger
                    MemoCapture.SAVED -> "저장됨" to DS.Success
                    MemoCapture.NOTHING_HEARD -> "다시 탭" to DS.Warning
                    MemoCapture.UNAVAILABLE -> "메모 불가" to DS.TextMuted
                    MemoCapture.IDLE -> "음성 메모" to DS.Primary
                }
                MemoButton(label, color, onMemo)
            }
        }
    }
}

/** Screen 6 (PAUSED_BY_POLICY): vehicle state is untrustworthy, treated as driving. */
@Composable
fun FailSafeScreen(reason: String) {
    Column(Modifier.fillMaxSize().padding(DS.Gutter), verticalArrangement = Arrangement.spacedBy(DS.Gap)) {
        Chip("Fail-Safe", DS.Danger)
        Text("차량 상태를 확인할 수 없습니다", style = MaterialTheme.typography.headlineLarge)
        Text("안전을 위해 주행 제한 상태로 간주합니다. 세션은 유지되며 신호가 돌아오면 자동으로 이어집니다.", style = MaterialTheme.typography.bodyLarge, color = DS.TextMuted)
        Text(reason, style = MaterialTheme.typography.bodyMedium, color = DS.TextMuted)
    }
}
