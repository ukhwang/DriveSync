package com.drivesync.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drivesync.app.ui.theme.DS
import com.drivesync.core.model.ActionStatus
import com.drivesync.core.model.ItemSource
import com.drivesync.core.model.SourcedText
import com.drivesync.core.safety.DrivingMode
import com.drivesync.core.safety.PolicyState

/** Top status bar: vehicle state chip, phase label, simulation badge. */
@Composable
fun StatusBar(policy: PolicyState, phaseLabel: String, simulated: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DS.Gutter, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DS.Gap),
    ) {
        val (label, color) = when {
            policy.failSafe != null -> "정책 일시정지" to DS.Danger
            policy.mode == DrivingMode.LIMITED -> "주행 제한" to DS.Warning
            policy.parkConfirmed -> "정차 · P" to DS.Success
            else -> "정차" to DS.Success
        }
        Chip(label, color)
        Text(phaseLabel, style = MaterialTheme.typography.labelLarge, color = DS.TextMuted)
        Spacer(Modifier.weight(1f))
        if (simulated) SimulationBadge()
    }
}

@Composable
fun Chip(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** 기획서: 시뮬레이션 데이터가 쓰인 화면에는 '시뮬레이션' 배지를 붙입니다. */
@Composable
fun SimulationBadge() {
    Text(
        "시뮬레이션",
        style = MaterialTheme.typography.labelMedium,
        color = DS.Bg,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DS.Simulation)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun SourceBadge(source: ItemSource) {
    val (label, color) = when (source) {
        ItemSource.USER -> "내가 말함" to DS.SourceUser
        ItemSource.AI -> "AI 추론 · 확인 필요" to DS.SourceAi
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun SourcedLine(line: SourcedText) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("•", style = MaterialTheme.typography.bodyLarge, color = DS.TextMuted)
        Column(Modifier.weight(1f)) {
            Text(line.text, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.size(4.dp))
            SourceBadge(line.source)
        }
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(DS.Radius))
            .background(DS.Surface)
            .border(1.dp, DS.Border, RoundedCornerShape(DS.Radius))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, color = DS.TextMuted)
        content()
    }
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = DS.TouchTarget),
        shape = RoundedCornerShape(DS.Radius),
        colors = ButtonDefaults.buttonColors(containerColor = DS.Primary, contentColor = DS.OnPrimary),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = DS.TouchTarget),
        shape = RoundedCornerShape(DS.Radius),
    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = DS.Text) }
}

/** Single large one-tap target for the driving screen. */
@Composable
fun MemoButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(DS.MemoButton)
            .clip(CircleShape)
            .background(color)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium, color = DS.OnPrimary, textAlign = TextAlign.Center)
    }
}

fun statusColor(status: ActionStatus): Color = when (status) {
    ActionStatus.DRAFT -> DS.TextMuted
    ActionStatus.REVIEW -> DS.Primary
    ActionStatus.APPROVED -> DS.Warning
    ActionStatus.EXECUTED -> DS.Success
    ActionStatus.FAILED -> DS.Danger
    ActionStatus.RETRY -> DS.Warning
}

fun statusLabel(status: ActionStatus): String = when (status) {
    ActionStatus.DRAFT -> "Draft · 초안"
    ActionStatus.REVIEW -> "Review · 검토 중"
    ActionStatus.APPROVED -> "Approved · 실행 대기"
    ActionStatus.EXECUTED -> "Executed · 발송 완료"
    ActionStatus.FAILED -> "Failed · 실패"
    ActionStatus.RETRY -> "Retry · 재승인 필요"
}
