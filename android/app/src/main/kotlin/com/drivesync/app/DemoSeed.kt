package com.drivesync.app

import com.drivesync.core.model.Customer
import com.drivesync.core.model.Destination
import com.drivesync.core.model.GeoPoint
import com.drivesync.core.model.Visit

/**
 * Demo data for the emulator walkthrough. All of it is fictional and every screen that
 * shows it carries the "시뮬레이션" badge. Mirrors supabase/seed.sql.
 */
object DemoSeed {
    private const val HOUR = 3_600_000L

    val customers = listOf(
        Customer(
            id = "c-hanbit",
            companyName = "한빛메디칼",
            contactName = "김도윤 구매팀장",
            contactEmail = "buyer@hanbit-medical.example",
            historySummary = "1차 방문: 초음파 프로브 교체 수요 확인, 납기 4주 이내 요구. 경쟁사 견적 대비 8% 높다는 피드백.",
        ),
        Customer(
            id = "c-seoyeon",
            companyName = "서연정형외과",
            contactName = "박서연 원장",
            contactEmail = "director@seoyeon-clinic.example",
            historySummary = "첫 방문 예정. 소개처: 한빛메디칼.",
        ),
        Customer(
            id = "c-daon",
            companyName = "다온제약 물류센터",
            contactName = "이준호 과장",
            contactEmail = "logistics@daon-pharma.example",
            historySummary = "2차 방문: 콜드체인 모니터링 시범 운영 합의, 계약서 초안 미전달(후속 작업 이월).",
        ),
    )

    fun visits(now: Long) = listOf(
        Visit("v-1", "c-hanbit", "2차 견적 협의 · 납기 조건 확정", now + HOUR, "한빛메디칼 본사", GeoPoint(37.5133, 127.1028)),
        Visit("v-2", "c-seoyeon", "신규 소개 미팅", now + 3 * HOUR, "서연정형외과", GeoPoint(37.4979, 127.0276)),
        Visit("v-3", "c-daon", "시범 운영 리뷰 · 계약서 전달", now + 5 * HOUR, "다온제약 물류센터", GeoPoint(37.2636, 127.0286)),
    )

    fun destinationFor(visit: Visit) = Destination(visit.placeName, visit.place)
}
