package com.drivesync.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivesync.app.ui.components.SecondaryButton
import com.drivesync.app.ui.components.StatusBar
import com.drivesync.app.ui.screens.ApprovalScreen
import com.drivesync.app.ui.screens.CheatSheetScreen
import com.drivesync.app.ui.screens.DebriefScreen
import com.drivesync.app.ui.screens.DrivingScreen
import com.drivesync.app.ui.screens.ErrorScreen
import com.drivesync.app.ui.screens.FailSafeScreen
import com.drivesync.app.ui.screens.PrepareScreen
import com.drivesync.app.ui.screens.ReportScreen
import com.drivesync.app.ui.screens.VisitListScreen
import com.drivesync.app.ui.theme.DS
import com.drivesync.core.model.DataOrigin
import com.drivesync.core.model.SessionPhase
import com.drivesync.core.model.valueOrNull
import com.drivesync.core.safety.DrivingMode
import com.drivesync.core.safety.FailSafeReason
import com.drivesync.core.safety.Feature
import com.drivesync.core.vehicle.MockVehicleContextSource
import kotlinx.coroutines.delay

/**
 * Top-level router. Safety first: whenever the policy is LIMITED, the minimum driving screen
 * (or the Fail-Safe screen) replaces whatever work screen the session phase would show.
 */
@Composable
fun DriveSyncRoot(vm: DriveSyncViewModel) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val visits by vm.visits.collectAsStateWithLifecycle()
    val customer by vm.customer.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val insight by vm.insight.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()

    val simulated = ui.vehicle?.origin == DataOrigin.SIMULATED || vm.isOfflineDemo
    val phase = ui.session.phase

    Row(Modifier.fillMaxSize().background(DS.Bg).safeDrawingPadding()) {
        Column(Modifier.weight(1f)) {
            StatusBar(ui.policy, phaseLabel(phase, ui.session.approvalsOnHold), simulated)
            Box(Modifier.weight(1f)) {
                when {
                    phase == SessionPhase.PAUSED_BY_POLICY || ui.policy.failSafe != null ->
                        FailSafeScreen(failSafeText(ui.policy.failSafe))

                    ui.policy.mode == DrivingMode.LIMITED -> DrivingScreen(
                        destination = ui.vehicle?.destination?.valueOrNull()?.name,
                        etaMinutes = ui.vehicle?.etaMinutes?.valueOrNull(),
                        statusLine = drivingStatus(phase, ui.session.approvalsOnHold, notes.size),
                        speech = ui.speech,
                        memo = ui.memo,
                        memoAllowed = ui.policy.allows(Feature.ONE_SHOT_VOICE_MEMO) && ui.sessionId != null,
                        onStopAudio = vm::stopAudio,
                        onMemo = vm::captureMemo,
                    )

                    phase == SessionPhase.ERROR -> ErrorScreen(vm::recoverFromError)

                    phase == SessionPhase.PREPARE_SESSION -> PrepareScreen(customer, ui.plan, ui.briefing)

                    phase == SessionPhase.PARKED_REVIEW && ui.showDebrief ->
                        DebriefScreen(ui.debriefStatus, vm::submitDebrief) { vm.dismissDebrief() }

                    phase == SessionPhase.PARKED_REVIEW -> CheatSheetScreen(
                        customer = customer,
                        insight = insight,
                        insightStatus = ui.insightStatus,
                        travelNotes = notes,
                        cheatSheetAllowed = ui.policy.allows(Feature.CHEAT_SHEET),
                        onRetryInsight = vm::requestInsight,
                        onMeetingDone = vm::openDebrief,
                    )

                    phase == SessionPhase.AWAITING_APPROVAL || phase == SessionPhase.EXECUTING -> ApprovalScreen(
                        actions = actions,
                        executing = phase == SessionPhase.EXECUTING,
                        onReview = vm::review,
                        onEdit = vm::editDraft,
                        onApprove = vm::approve,
                        onExecute = vm::execute,
                        onRetry = vm::scheduleRetry,
                        onClose = vm::closeSession,
                    )

                    phase == SessionPhase.COMPLETED && ui.selectedVisitId != null && !ui.browsingNext ->
                        ReportScreen(customer, actions, notes) { vm.browseNextVisit() }

                    else -> VisitListScreen(
                        visits = visits,
                        selectedVisitId = ui.selectedVisitId,
                        customer = customer,
                        destinationMatches = vm.destinationMatches(visits.firstOrNull { it.id == ui.selectedVisitId }),
                        plan = ui.plan,
                        briefing = ui.briefing,
                        onSelect = vm::selectVisit,
                        onStartSession = vm::startSession,
                    )
                }
                ui.message?.let { message ->
                    LaunchedEffect(message) {
                        delay(4_000)
                        vm.dismissMessage()
                    }
                    Snackbar(Modifier.align(Alignment.BottomCenter).padding(DS.Gutter)) {
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        vm.simulator?.let { SimulatorPanel(it) }
    }

    if (ui.consentNeeded && ui.policy.mode == DrivingMode.PARKED_WORK) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("고객 정보 외부 전송 동의") },
            text = {
                Text(
                    "브리핑·치트시트·후속 업무 생성을 위해 방문 메모와 고객사 정보가 DriveSync 서버를 거쳐 외부 LLM으로 전송됩니다. " +
                        "서버는 처리 후 원문을 보관하지 않습니다.",
                )
            },
            confirmButton = { TextButton(onClick = vm::grantConsent) { Text("동의") } },
            dismissButton = { TextButton(onClick = vm::declineConsent) { Text("나중에") } },
        )
    }
}

/**
 * Operator panel for the emulator demo only (not present with the real car API).
 * Stands in for the emulator's vehicle-data controls and NaviHelper.
 */
@Composable
private fun SimulatorPanel(sim: MockVehicleContextSource) {
    Column(
        Modifier.width(220.dp).background(DS.Surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("시뮬레이터 (운영자)", style = MaterialTheme.typography.labelMedium, color = DS.Simulation)
        SecondaryButton("정차 · P", { sim.park() })
        SecondaryButton("주행 시작", { sim.startDriving() })
        SecondaryButton("ETA 18분", { sim.setEta(18) })
        SecondaryButton("ETA 9분", { sim.setEta(9) })
        SecondaryButton("ETA 4분", { sim.setEta(4) })
        SecondaryButton("도착 0분", { sim.setEta(0) })
        SecondaryButton("신호 끊김", { sim.loseRestrictionSignal() })
        SecondaryButton("신호 복구", { sim.restoreRestrictionSignal(com.drivesync.core.model.UxRestriction.LIMITED) })
    }
}

private fun phaseLabel(phase: SessionPhase, onHold: Boolean): String = when (phase) {
    SessionPhase.IDLE -> "방문 선택"
    SessionPhase.PREPARE_SESSION -> "1 · 방문 전"
    SessionPhase.DRIVING_LIMITED -> "2 · 이동 중"
    SessionPhase.PAUSED_BY_POLICY -> "정책 일시정지"
    SessionPhase.ARRIVAL_PENDING -> "도착 임박"
    SessionPhase.PARKED_REVIEW -> "3·4 · 미팅"
    SessionPhase.AWAITING_APPROVAL -> if (onHold) "5 · 승인 보류(주행 중)" else "5 · 후속 승인"
    SessionPhase.EXECUTING -> "5 · 실행 중"
    SessionPhase.COMPLETED -> "세션 완료"
    SessionPhase.ERROR -> "오류"
}

private fun drivingStatus(phase: SessionPhase, onHold: Boolean, memoCount: Int): String = when {
    phase == SessionPhase.AWAITING_APPROVAL && onHold -> "승인 대기 작업은 정차 후 확인합니다"
    phase == SessionPhase.ARRIVAL_PENDING -> "곧 도착 · 정차하면 치트시트가 열립니다"
    phase == SessionPhase.DRIVING_LIMITED -> "방문 세션 진행 중 · 메모 ${memoCount}건"
    else -> "주행 중 최소 모드"
}

private fun failSafeText(reason: FailSafeReason?): String = when (reason) {
    is FailSafeReason.SignalUnavailable -> "원인: ${reason.reason}"
    FailSafeReason.SignalUnsupported -> "원인: UX 제한 신호 미지원"
    is FailSafeReason.SignalStale -> "원인: 차량 신호 ${reason.ageMillis / 1000}초 동안 갱신 없음"
    FailSafeReason.NoSignalYet -> "차량 서비스 연결 중"
    null -> "세션 상태 복구 중"
}
