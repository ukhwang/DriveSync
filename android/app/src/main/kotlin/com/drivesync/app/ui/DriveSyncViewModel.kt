package com.drivesync.app.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.drivesync.app.BuildConfig
import com.drivesync.app.DemoSeed
import com.drivesync.app.di.AppContainer
import com.drivesync.core.action.ActionOutcome
import com.drivesync.core.action.isOpen
import com.drivesync.core.ai.BriefingRequest
import com.drivesync.core.ai.EdgeResult
import com.drivesync.core.ai.ExtractActionsRequest
import com.drivesync.core.ai.InsightRequest
import com.drivesync.core.model.Briefing
import com.drivesync.core.model.BriefingSentence
import com.drivesync.core.model.Customer
import com.drivesync.core.model.EmailDraft
import com.drivesync.core.model.FollowUpAction
import com.drivesync.core.model.Insight
import com.drivesync.core.model.Note
import com.drivesync.core.model.NoteInputMethod
import com.drivesync.core.model.SessionPhase
import com.drivesync.core.model.VehicleContext
import com.drivesync.core.model.Visit
import com.drivesync.core.model.valueOrNull
import com.drivesync.core.safety.DrivingMode
import com.drivesync.core.safety.Feature
import com.drivesync.core.safety.PlatformCapabilities
import com.drivesync.core.safety.PolicyState
import com.drivesync.core.session.BriefingPlan
import com.drivesync.core.session.BriefingPlanner
import com.drivesync.core.session.SessionEvent
import com.drivesync.core.session.SessionState
import com.drivesync.core.session.SessionStateMachine
import com.drivesync.core.session.Transition
import com.drivesync.core.voice.CaptureResult
import com.drivesync.core.voice.SpeechState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Async status for one-off server operations (client/UI state only). */
sealed interface Async<out T> {
    data object Idle : Async<Nothing>
    data object Loading : Async<Nothing>
    data class Ready<T>(val value: T) : Async<T>
    data class Failed(val message: String) : Async<Nothing>
}

enum class MemoCapture { IDLE, LISTENING, SAVED, NOTHING_HEARD, UNAVAILABLE }

/**
 * Client/UI state only. Persisted/server data (visits, notes, insight, actions) is observed
 * from Room through the dedicated flows below and is never copied in here.
 */
data class UiState(
    val vehicle: VehicleContext? = null,
    val policy: PolicyState,
    val session: SessionState = SessionState(),
    val sessionId: String? = null,
    val selectedVisitId: String? = null,
    val plan: BriefingPlan? = null,
    val briefing: Async<Briefing> = Async.Idle,
    val insightStatus: Async<Unit> = Async.Idle,
    val debriefStatus: Async<Unit> = Async.Idle,
    val showDebrief: Boolean = false,
    /** After a completed session: user left the report to pick the next visit. */
    val browsingNext: Boolean = false,
    val memo: MemoCapture = MemoCapture.IDLE,
    val speech: SpeechState = SpeechState.IDLE,
    val consentNeeded: Boolean = false,
    val message: String? = null,
)

/**
 * Orchestrates the visit session: vehicle -> safety policy -> session state machine,
 * plus ETA-aware voice briefing, notes, AI calls and the approval engine.
 * Pure rules live in core modules; this class only wires them to I/O.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveSyncViewModel(private val c: AppContainer) : ViewModel() {

    /** Re-read every tick: the microphone permission can be granted after launch. */
    private fun capabilities() = PlatformCapabilities(
        ttsPermitted = c.speechOutput.state.value != SpeechState.UNAVAILABLE,
        sttPermitted = c.speechInput.isAvailable(),
        // DO allowance for STT while driving must be confirmed on the contest platform; off until then.
        sttAllowedWhileDriving = false,
    )

    private val mutableState = MutableStateFlow(UiState(policy = c.safetyPolicy.evaluate(null, capabilities(), 0)))
    val state: StateFlow<UiState> = mutableState.asStateFlow()

    val isSimulated: Boolean = c.simulator != null
    val simulator = c.simulator

    // ---- Persisted/server state: observed, never duplicated ----
    val visits: StateFlow<List<Visit>> = c.visits.observeVisits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val customer: StateFlow<Customer?> = state.map { it.selectedVisitId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else customerFor(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    val notes: StateFlow<List<Note>> = state.map { it.sessionId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else c.notes.observeSession(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val insight: StateFlow<Insight?> = state.map { it.selectedVisitId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else c.insights.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    val actions: StateFlow<List<FollowUpAction>> = state.map { it.sessionId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else c.actionStore.observeSession(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    private var briefingStartedAt: Long? = null
    private var briefingQueue: List<BriefingSentence> = emptyList()
    private var recapPlayed = false
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME) }.getOrNull()

    init {
        viewModelScope.launch { bootstrap() }
        viewModelScope.launch { observeVehicle() }
        viewModelScope.launch { c.speechOutput.state.collect { s -> mutableState.update { it.copy(speech = s) } } }
    }

    private suspend fun bootstrap() {
        if (c.visits.isEmpty()) c.visits.seed(DemoSeed.customers, DemoSeed.visits(System.currentTimeMillis()))
        if (c.auth.accessToken() == null && BuildConfig.DEMO_EMAIL.isNotEmpty()) {
            c.auth.signIn(BuildConfig.DEMO_EMAIL, BuildConfig.DEMO_PASSWORD)
        }
        c.sessionSnapshots.load()?.let { (sessionId, snapshot) ->
            if (snapshot.phase != SessionPhase.COMPLETED && snapshot.phase != SessionPhase.IDLE) {
                mutableState.update { it.copy(sessionId = sessionId, selectedVisitId = snapshot.visitId) }
                dispatch(SessionEvent.RecoveredFromLocal(snapshot))
            }
        }
    }

    /** Vehicle + 1 s ticker -> policy (stale check needs the clock) -> session events. */
    private suspend fun observeVehicle() {
        val ticker = flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(TICK_MILLIS)
            }
        }
        combine(c.vehicleSource.contexts, ticker) { ctx, now ->
            ctx to c.safetyPolicy.evaluate(ctx, capabilities(), now)
        }.collect { (ctx, policy) ->
            val previous = mutableState.value
            mutableState.update { it.copy(vehicle = ctx, policy = policy) }
            val eta = ctx.etaMinutes.valueOrNull()
            if (previous.policy != policy || previous.session.etaMinutes != eta) {
                dispatch(SessionEvent.VehicleUpdated(policy, eta))
                onEtaChanged(previous.session.etaMinutes, eta)
            }
            if (policy.mode == DrivingMode.LIMITED && previous.policy.mode == DrivingMode.PARKED_WORK) {
                // Leaving the work screen: never keep a text field or editor open under the driver.
                mutableState.update { it.copy(showDebrief = false) }
            }
        }
    }

    private fun dispatch(event: SessionEvent): Transition {
        val before = mutableState.value.session
        val transition = SessionStateMachine.reduce(before, event)
        mutableState.update { it.copy(session = transition.state) }
        if (transition is Transition.Rejected) toast(transition.reason)
        if (transition.state.phase != before.phase) onPhaseEntered(transition.state.phase)
        persistSnapshot()
        return transition
    }

    private fun onPhaseEntered(phase: SessionPhase) {
        when (phase) {
            SessionPhase.DRIVING_LIMITED -> startVoiceBriefing()
            SessionPhase.ARRIVAL_PENDING -> playRecap()
            SessionPhase.PARKED_REVIEW -> {
                c.speechOutput.stop()
                requestInsight()
            }
            SessionPhase.COMPLETED -> viewModelScope.launch { c.sessionSnapshots.clear() }
            else -> Unit
        }
    }

    private fun persistSnapshot() {
        val s = mutableState.value
        val id = s.sessionId ?: return
        if (s.session.phase == SessionPhase.COMPLETED) return
        viewModelScope.launch { c.sessionSnapshots.save(id, s.session) }
    }

    // ---------------- Stage 1: visit selection + prepare ----------------

    fun selectVisit(visitId: String) {
        if (!mutableState.value.policy.allows(Feature.LIST_BROWSING)) return
        mutableState.update { it.copy(selectedVisitId = visitId, briefing = Async.Idle, plan = null) }
        viewModelScope.launch {
            val visit = c.visits.visit(visitId) ?: return@launch
            // Simulator only: set the navigation destination the way NaviHelper would.
            c.simulator?.setDestination(DemoSeed.destinationFor(visit), DEMO_ETA_MINUTES)
            loadBriefing(visitId)
        }
    }

    fun destinationMatches(visit: Visit?): Boolean {
        val destination = mutableState.value.vehicle?.destination?.valueOrNull() ?: return false
        return visit != null && destination.name == visit.placeName
    }

    fun startSession() {
        val visitId = mutableState.value.selectedVisitId ?: return
        if (mutableState.value.session.phase == SessionPhase.COMPLETED) {
            mutableState.update { it.copy(session = SessionState(drivingMode = it.session.drivingMode)) }
        }
        val sessionId = UUID.randomUUID().toString()
        mutableState.update {
            it.copy(sessionId = sessionId, insightStatus = Async.Idle, debriefStatus = Async.Idle, browsingNext = false)
        }
        recapPlayed = false
        dispatch(SessionEvent.SessionStarted(visitId))
    }

    private suspend fun loadBriefing(visitId: String) {
        val eta = mutableState.value.vehicle?.etaMinutes?.valueOrNull() ?: DEMO_ETA_MINUTES
        val plan = BriefingPlanner.plan(eta)
        mutableState.update { it.copy(plan = plan, briefing = Async.Loading) }
        val result = c.ai.briefing(BriefingRequest(visitId, eta, plan.tier))
        mutableState.update { it.copy(briefing = result.toAsync()) }
    }

    // ---------------- Stage 2: driving (one-way voice + one-shot memo) ----------------

    private fun startVoiceBriefing() {
        val s = mutableState.value
        val briefing = (s.briefing as? Async.Ready)?.value ?: return
        val plan = s.plan ?: return
        if (!s.policy.allows(Feature.ONEWAY_TTS_BRIEFING)) return // fallback: screen briefing while parked
        val eta = s.session.etaMinutes ?: return
        briefingQueue = BriefingPlanner.replan(briefing.voiceScript, plan, elapsedVoiceSeconds = 0, remainingEtaMinutes = eta)
        briefingStartedAt = System.currentTimeMillis()
        c.speechOutput.enqueue(briefingQueue.map { it.text })
    }

    private fun onEtaChanged(previous: Int?, current: Int?) {
        val started = briefingStartedAt ?: return
        val plan = mutableState.value.plan ?: return
        if (previous == null || current == null || !BriefingPlanner.isSignificantChange(previous, current)) return
        if (mutableState.value.speech != SpeechState.SPEAKING) return
        val elapsed = ((System.currentTimeMillis() - started) / 1_000).toInt()
        var spoken = 0
        val remaining = briefingQueue.dropWhile { sentence ->
            spoken += BriefingPlanner.estimateSeconds(sentence)
            spoken <= elapsed
        }
        val replanned = BriefingPlanner.replan(remaining, plan, elapsed, current)
        c.speechOutput.stop()
        briefingQueue = replanned
        briefingStartedAt = System.currentTimeMillis()
        c.speechOutput.enqueue(replanned.map { it.text })
    }

    private fun playRecap() {
        val s = mutableState.value
        val recap = (s.briefing as? Async.Ready)?.value?.preArrivalRecap ?: return
        if (recapPlayed || s.plan?.preArrivalRecapSeconds == null || !s.policy.allows(Feature.ONEWAY_TTS_BRIEFING)) return
        recapPlayed = true
        c.speechOutput.enqueue(listOf(recap))
    }

    fun stopAudio() {
        c.speechOutput.stop()
        briefingStartedAt = null
    }

    fun captureMemo() {
        val s = mutableState.value
        val sessionId = s.sessionId ?: return
        if (!s.policy.allows(Feature.ONE_SHOT_VOICE_MEMO)) return
        c.speechOutput.stop()
        mutableState.update { it.copy(memo = MemoCapture.LISTENING) }
        viewModelScope.launch {
            val outcome = when (val result = c.speechInput.captureOnce(BuildConfig.MEMO_MAX_SECONDS)) {
                is CaptureResult.Text -> {
                    c.notes.add(sessionId, result.text, NoteInputMethod.VOICE, mutableState.value.session.phase)
                    tone?.startTone(ToneGenerator.TONE_PROP_ACK, TONE_MILLIS)
                    MemoCapture.SAVED
                }
                CaptureResult.NothingHeard -> MemoCapture.NOTHING_HEARD
                is CaptureResult.Unavailable -> MemoCapture.UNAVAILABLE
            }
            mutableState.update { it.copy(memo = outcome) }
            delay(MEMO_FEEDBACK_MILLIS)
            mutableState.update { it.copy(memo = MemoCapture.IDLE) }
        }
    }

    fun addTextNote(text: String) {
        val s = mutableState.value
        val sessionId = s.sessionId ?: return
        if (text.isBlank() || !s.policy.allows(Feature.TEXT_INPUT)) return
        viewModelScope.launch { c.notes.add(sessionId, text.trim(), NoteInputMethod.TEXT, s.session.phase) }
    }

    // ---------------- Stage 3: cheat sheet ----------------

    fun requestInsight() {
        val s = mutableState.value
        val visitId = s.selectedVisitId ?: return
        val sessionId = s.sessionId ?: return
        mutableState.update { it.copy(insightStatus = Async.Loading) }
        viewModelScope.launch {
            val travelNotes = c.notes.observeSession(sessionId).first().map { it.text }
            when (val result = c.ai.insight(InsightRequest(visitId, travelNotes))) {
                is EdgeResult.Success -> {
                    c.insights.save(visitId, result.value)
                    mutableState.update { it.copy(insightStatus = Async.Ready(Unit)) }
                }
                else -> mutableState.update { it.copy(insightStatus = result.toAsync<Insight>().asFailure()) }
            }
        }
    }

    // ---------------- Stage 4: debrief ----------------

    fun openDebrief() {
        if (mutableState.value.policy.allows(Feature.DEBRIEF)) mutableState.update { it.copy(showDebrief = true) }
    }

    fun dismissDebrief() = mutableState.update { it.copy(showDebrief = false) }

    fun submitDebrief(text: String) {
        val s = mutableState.value
        val sessionId = s.sessionId ?: return
        val visitId = s.selectedVisitId ?: return
        if (text.isBlank() || !s.policy.allows(Feature.DEBRIEF)) return
        mutableState.update { it.copy(debriefStatus = Async.Loading) }
        viewModelScope.launch {
            // Raw debrief is saved first: AI failure never loses what the user said.
            c.notes.add(sessionId, text.trim(), NoteInputMethod.TEXT, SessionPhase.PARKED_REVIEW)
            val customerId = c.visits.visit(visitId)?.customerId ?: return@launch
            when (val result = c.ai.extractActions(ExtractActionsRequest(sessionId, visitId, text.trim()), customerId)) {
                is EdgeResult.Success -> {
                    result.value.forEach { c.actionStore.upsert(it) }
                    mutableState.update { it.copy(debriefStatus = Async.Ready(Unit), showDebrief = false) }
                    dispatch(SessionEvent.DebriefCompleted(result.value.size))
                }
                else -> mutableState.update { it.copy(debriefStatus = result.toAsync<List<FollowUpAction>>().asFailure()) }
            }
        }
    }

    // ---------------- Stage 5: approval & execution ----------------

    fun review(actionId: String) = runAction { c.actionEngine.startReview(actionId) }

    fun editDraft(actionId: String, draft: EmailDraft) = runAction { c.actionEngine.editDraft(actionId, draft) }

    fun approve(actionId: String) = runAction { c.actionEngine.approve(actionId, mutableState.value.policy) }

    fun scheduleRetry(actionId: String) = runAction { c.actionEngine.scheduleRetry(actionId) }

    fun execute(actionId: String) {
        viewModelScope.launch {
            if (dispatch(SessionEvent.ExecutionStarted) is Transition.Rejected) return@launch
            val outcome = c.actionEngine.execute(actionId)
            if (outcome is ActionOutcome.Rejected) toast(outcome.reason)
            val open = actions.value.count { it.isOpen && it.id != actionId } +
                if ((outcome as? ActionOutcome.Ok)?.action?.isOpen == true) 1 else 0
            dispatch(SessionEvent.ExecutionFinished(open))
        }
    }

    fun closeSession() {
        dispatch(SessionEvent.SessionClosed)
    }

    fun recoverFromError() {
        viewModelScope.launch {
            dispatch(SessionEvent.RecoveredFromLocal(c.sessionSnapshots.load()?.second))
        }
    }

    fun grantConsent() {
        viewModelScope.launch {
            when (c.ai.recordConsent(granted = true)) {
                is EdgeResult.Success -> {
                    mutableState.update { it.copy(consentNeeded = false) }
                    toast("동의가 저장되었습니다. 다시 시도해 주세요")
                }
                else -> toast("동의를 저장하지 못했습니다")
            }
        }
    }

    fun declineConsent() = mutableState.update {
        it.copy(consentNeeded = false, message = "동의 전에는 AI 브리핑 없이 메모만 저장됩니다")
    }

    fun browseNextVisit() = mutableState.update {
        it.copy(browsingNext = true, selectedVisitId = null, sessionId = null, plan = null, briefing = Async.Idle)
    }

    fun dismissMessage() = mutableState.update { it.copy(message = null) }

    private fun runAction(block: suspend () -> ActionOutcome) {
        viewModelScope.launch {
            val outcome = block()
            if (outcome is ActionOutcome.Rejected) toast(outcome.reason)
        }
    }

    private fun customerFor(visitId: String): Flow<Customer?> = flow {
        val customerId = c.visits.visit(visitId)?.customerId
        if (customerId == null) emit(null) else emitAll(c.visits.observeCustomer(customerId))
    }

    private fun toast(message: String) = mutableState.update { it.copy(message = message) }

    private fun <T> EdgeResult<T>.toAsync(): Async<T> = when (this) {
        is EdgeResult.Success -> Async.Ready(value)
        is EdgeResult.SchemaRejected -> Async.Failed("AI 결과가 검증을 통과하지 못했습니다. 원문 메모는 보존됩니다")
        EdgeResult.ConsentRequired -> {
            mutableState.update { it.copy(consentNeeded = true) }
            Async.Failed("고객 정보 외부 전송 동의가 필요합니다")
        }
        EdgeResult.Unauthorized -> Async.Failed("로그인이 필요합니다")
        is EdgeResult.RateLimited -> Async.Failed("요청이 많습니다. 잠시 후 다시 시도하세요")
        is EdgeResult.Network -> Async.Failed("오프라인입니다. 입력은 저장되었고 연결되면 다시 시도할 수 있습니다")
        is EdgeResult.Server -> Async.Failed("서버 오류(${status})")
    }

    private fun <T> Async<T>.asFailure(): Async<Nothing> = this as? Async.Failed ?: Async.Failed("알 수 없는 오류")

    override fun onCleared() {
        c.speechOutput.stop()
        tone?.release()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DriveSyncViewModel(container) as T
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val TICK_MILLIS = 1_000L
        const val DEMO_ETA_MINUTES = 18
        const val MEMO_FEEDBACK_MILLIS = 2_000L
        const val TONE_VOLUME = 80
        const val TONE_MILLIS = 200
    }
}
