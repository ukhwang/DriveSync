package com.drivesync.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.drivesync.app.ui.Async
import com.drivesync.app.ui.components.Card
import com.drivesync.app.ui.components.Chip
import com.drivesync.app.ui.components.PrimaryButton
import com.drivesync.app.ui.components.SecondaryButton
import com.drivesync.app.ui.components.SourcedLine
import com.drivesync.app.ui.components.statusColor
import com.drivesync.app.ui.components.statusLabel
import com.drivesync.app.ui.theme.DS
import com.drivesync.core.model.ActionStatus
import com.drivesync.core.model.Briefing
import com.drivesync.core.model.Customer
import com.drivesync.core.model.EmailDraft
import com.drivesync.core.model.FollowUpAction
import com.drivesync.core.model.Insight
import com.drivesync.core.model.Note
import com.drivesync.core.model.NoteInputMethod
import com.drivesync.core.model.Visit
import com.drivesync.core.session.BriefingPlan
import com.drivesync.core.session.ScreenCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* All screens in this file are only reachable in PARKED_WORK mode (see DriveSyncRoot). */

private val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREA)

/** Screen 1 (IDLE) + Screen 2 preview: today's visits and the selected visit's prepare panel. */
@Composable
fun VisitListScreen(
    visits: List<Visit>,
    selectedVisitId: String?,
    customer: Customer?,
    destinationMatches: Boolean,
    plan: BriefingPlan?,
    briefing: Async<Briefing>,
    onSelect: (String) -> Unit,
    onStartSession: () -> Unit,
) {
    Row(Modifier.fillMaxSize().padding(horizontal = DS.Gutter), horizontalArrangement = Arrangement.spacedBy(DS.Gutter)) {
        Column(Modifier.weight(1f)) {
            Text("오늘 방문 ${visits.size}건", style = MaterialTheme.typography.headlineLarge)
            LazyColumn(Modifier.padding(top = DS.Gap), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visits, key = { it.id }) { visit ->
                    val selected = visit.id == selectedVisitId
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = DS.TouchTarget)
                            .clip(RoundedCornerShape(DS.Radius))
                            .background(if (selected) DS.SurfaceRaised else DS.Surface)
                            .border(2.dp, if (selected) DS.Primary else DS.Border, RoundedCornerShape(DS.Radius))
                            .clickable { onSelect(visit.id) }
                            .padding(20.dp),
                    ) {
                        Text("${timeFormat.format(Date(visit.scheduledAtEpochMillis))} · ${visit.placeName}", style = MaterialTheme.typography.titleLarge)
                        Text(visit.purpose, style = MaterialTheme.typography.bodyMedium, color = DS.TextMuted)
                    }
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(DS.Gap)) {
            if (selectedVisitId == null || customer == null) {
                Text("방문을 선택하세요", style = MaterialTheme.typography.headlineMedium, color = DS.TextMuted)
                return@Column
            }
            Text(customer.companyName, style = MaterialTheme.typography.headlineLarge)
            Text(customer.contactName, style = MaterialTheme.typography.bodyLarge, color = DS.TextMuted)
            if (destinationMatches) {
                Chip("내비 목적지 일치", DS.Success)
            } else {
                Chip("내비 목적지 확인 필요", DS.Warning)
            }
            BriefingCard(plan, briefing)
            PrimaryButton(
                "이 방문으로 세션 시작",
                onClick = onStartSession,
                enabled = destinationMatches,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun BriefingCard(plan: BriefingPlan?, briefing: Async<Briefing>) {
    val title = when (plan?.screenCard) {
        ScreenCard.CORE_THREE -> "핵심 3줄"
        ScreenCard.CORE_THREE_WITH_CONCERNS -> "핵심 3줄 + 우려사항"
        ScreenCard.DETAILED -> "상세 브리핑"
        null -> "브리핑"
    }
    Card(title = title) {
        when (briefing) {
            Async.Idle, Async.Loading -> CircularProgressIndicator(color = DS.Primary)
            is Async.Failed -> Text(briefing.message, color = DS.Danger, style = MaterialTheme.typography.bodyMedium)
            is Async.Ready -> {
                val lines = when (plan?.screenCard) {
                    ScreenCard.CORE_THREE -> briefing.value.screenLines.take(3)
                    else -> briefing.value.screenLines
                }
                lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
                plan?.let {
                    Text(
                        "주행 중 음성 브리핑 약 ${it.voiceBudgetSeconds}초" + if (it.preArrivalRecapSeconds != null) " · 도착 5분 전 재요약" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DS.TextMuted,
                    )
                }
            }
        }
    }
}

/** Screen 2 (PREPARE_SESSION): session started, waiting for the car to move. */
@Composable
fun PrepareScreen(customer: Customer?, plan: BriefingPlan?, briefing: Async<Briefing>) {
    Column(Modifier.fillMaxSize().padding(horizontal = DS.Gutter), verticalArrangement = Arrangement.spacedBy(DS.Gap)) {
        Text("${customer?.companyName ?: ""} 방문 세션 준비 완료", style = MaterialTheme.typography.headlineLarge)
        Text("출발하면 화면이 최소 모드로 바뀌고 음성 브리핑이 시작됩니다.", style = MaterialTheme.typography.bodyLarge, color = DS.TextMuted)
        BriefingCard(plan, briefing)
    }
}

/** Screen 7 (PARKED_REVIEW): meeting cheat sheet. */
@Composable
fun CheatSheetScreen(
    customer: Customer?,
    insight: Insight?,
    insightStatus: Async<Unit>,
    travelNotes: List<Note>,
    cheatSheetAllowed: Boolean,
    onRetryInsight: () -> Unit,
    onMeetingDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = DS.Gutter)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${customer?.companyName ?: ""} 미팅 치트시트", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            PrimaryButton("미팅 종료 · 디브리핑", onMeetingDone)
        }
        if (!cheatSheetAllowed) {
            Text("P단 확인 후 치트시트가 표시됩니다", style = MaterialTheme.typography.headlineMedium, color = DS.Warning)
            return@Column
        }
        Row(Modifier.padding(top = DS.Gap).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(DS.Gap)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DS.Gap)) {
                when {
                    insight != null -> {
                        Card(title = "핵심 3줄") { insight.summary.forEach { SourcedLine(it) } }
                        Card(title = "우려사항") { insight.concerns.forEach { SourcedLine(it) } }
                    }
                    insightStatus is Async.Failed -> Card(title = "치트시트를 만들지 못했습니다") {
                        Text(insightStatus.message, color = DS.Danger, style = MaterialTheme.typography.bodyMedium)
                        SecondaryButton("다시 시도", onRetryInsight)
                    }
                    else -> CircularProgressIndicator(color = DS.Primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DS.Gap)) {
                insight?.let { Card(title = "예상 질문") { it.expectedQuestions.forEach { q -> SourcedLine(q) } } }
                Card(title = "이동 중 메모") {
                    if (travelNotes.isEmpty()) Text("메모 없음", color = DS.TextMuted)
                    travelNotes.forEach { note ->
                        val method = if (note.inputMethod == NoteInputMethod.VOICE) "음성" else "텍스트"
                        Text("[$method ${timeFormat.format(Date(note.createdAtMillis))}] ${note.text}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Screen 8 (PARKED_REVIEW): debrief input. */
@Composable
fun DebriefScreen(status: Async<Unit>, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = DS.Gutter), verticalArrangement = Arrangement.spacedBy(DS.Gap)) {
        Text("미팅 디브리핑", style = MaterialTheme.typography.headlineLarge)
        Text("결정사항, 약속, 후속 업무를 편하게 적으세요. 원문은 기기에 먼저 저장됩니다.", style = MaterialTheme.typography.bodyMedium, color = DS.TextMuted)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text("예) 납기 3주로 합의. 수정 견적서 금요일까지 김도윤 팀장에게 메일로 보내기로 함.") },
        )
        if (status is Async.Failed) Text(status.message, color = DS.Danger, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(DS.Gap)) {
            SecondaryButton("취소", onCancel)
            PrimaryButton(
                if (status is Async.Loading) "후속 업무 추출 중…" else "후속 업무 추출",
                onClick = { onSubmit(text) },
                enabled = text.isNotBlank() && status !is Async.Loading,
            )
        }
    }
}

/** Screens 9-12 (AWAITING_APPROVAL / EXECUTING): one-by-one review, approval, execution. */
@Composable
fun ApprovalScreen(
    actions: List<FollowUpAction>,
    executing: Boolean,
    onReview: (String) -> Unit,
    onEdit: (String, EmailDraft) -> Unit,
    onApprove: (String) -> Unit,
    onExecute: (String) -> Unit,
    onRetry: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = DS.Gutter), verticalArrangement = Arrangement.spacedBy(DS.Gap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("후속 작업 승인", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            SecondaryButton("세션 종료 · 남은 작업 이월", onClose, enabled = !executing)
        }
        Text("AI 초안은 승인 전까지 외부로 나가지 않습니다. 발송 결과는 서버 응답만 표시합니다.", style = MaterialTheme.typography.bodyMedium, color = DS.TextMuted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(DS.Gap)) {
            items(actions, key = { it.id }) { action ->
                ActionCard(action, executing, onReview, onEdit, onApprove, onExecute, onRetry)
            }
        }
    }
}

@Composable
private fun ActionCard(
    action: FollowUpAction,
    executing: Boolean,
    onReview: (String) -> Unit,
    onEdit: (String, EmailDraft) -> Unit,
    onApprove: (String) -> Unit,
    onExecute: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    var subject by remember(action.id, action.draftVersion) { mutableStateOf(action.draft.subject) }
    var body by remember(action.id, action.draftVersion) { mutableStateOf(action.draft.body) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("이메일 · ${action.draft.to}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Chip(statusLabel(action.status), statusColor(action.status))
        }
        when (action.status) {
            ActionStatus.REVIEW -> {
                OutlinedTextField(subject, { subject = it }, Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge, label = { Text("제목") })
                OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp), textStyle = MaterialTheme.typography.bodyMedium, label = { Text("본문") })
            }
            else -> {
                Text(action.draft.subject, style = MaterialTheme.typography.bodyLarge)
                Text(action.draft.body, style = MaterialTheme.typography.bodyMedium, color = DS.TextMuted, maxLines = 4)
            }
        }
        action.failureReason?.takeIf { action.status == ActionStatus.FAILED || action.status == ActionStatus.RETRY }?.let {
            Text("실패 원인: $it", color = DS.Danger, style = MaterialTheme.typography.bodyMedium)
        }
        action.log.lastOrNull()?.takeIf { action.status == ActionStatus.EXECUTED }?.let {
            Text("서버 결과: ${it.message}", color = DS.Success, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DS.Gap)) {
            when (action.status) {
                ActionStatus.DRAFT -> PrimaryButton("검토 시작", { onReview(action.id) })
                ActionStatus.REVIEW -> {
                    val edited = subject != action.draft.subject || body != action.draft.body
                    if (edited) SecondaryButton("수정 저장", { onEdit(action.id, action.draft.copy(subject = subject, body = body)) })
                    PrimaryButton("승인", { onApprove(action.id) }, enabled = !edited)
                }
                ActionStatus.APPROVED -> PrimaryButton(if (executing) "발송 중…" else "실행 · 발송", { onExecute(action.id) }, enabled = !executing)
                ActionStatus.FAILED -> SecondaryButton("다음 정차 때 재시도", { onRetry(action.id) })
                ActionStatus.RETRY -> PrimaryButton("다시 승인", { onApprove(action.id) })
                ActionStatus.EXECUTED -> Unit
            }
        }
    }
}

/** Screen 13 (COMPLETED): visit report. */
@Composable
fun ReportScreen(customer: Customer?, actions: List<FollowUpAction>, notes: List<Note>, onNext: () -> Unit) {
    val executed = actions.count { it.status == ActionStatus.EXECUTED }
    val carried = actions.size - executed
    Column(Modifier.fillMaxSize().padding(horizontal = DS.Gutter), verticalArrangement = Arrangement.spacedBy(DS.Gap)) {
        Text("${customer?.companyName ?: ""} 방문 리포트", style = MaterialTheme.typography.headlineLarge)
        Chip("기기에 암호화 저장됨 (SQLCipher)", DS.Success)
        Row(horizontalArrangement = Arrangement.spacedBy(DS.Gap)) {
            Card(Modifier.weight(1f), title = "실행 완료") { Text("$executed 건", style = MaterialTheme.typography.headlineLarge) }
            Card(Modifier.weight(1f), title = "다음 방문으로 이월") { Text("$carried 건", style = MaterialTheme.typography.headlineLarge) }
            Card(Modifier.weight(1f), title = "메모") { Text("${notes.size} 건", style = MaterialTheme.typography.headlineLarge) }
        }
        PrimaryButton("다음 방문 선택", onNext)
    }
}

@Composable
fun ErrorScreen(onRecover: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(DS.Gutter), verticalArrangement = Arrangement.spacedBy(DS.Gap)) {
        Text("세션을 계속할 수 없습니다", style = MaterialTheme.typography.headlineLarge)
        Text("기기에 저장된 마지막 상태로 복구합니다. 실행 중이던 작업은 승인 대기로 돌아갑니다.", style = MaterialTheme.typography.bodyLarge, color = DS.TextMuted)
        PrimaryButton("로컬 저장본으로 복구", onRecover)
    }
}
