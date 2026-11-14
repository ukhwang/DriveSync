-- Local demo seed (supabase db reset). ALL DATA IS FICTIONAL — screens using it show "시뮬레이션".
-- Mirrors android/app/src/main/kotlin/com/drivesync/app/DemoSeed.kt.
-- Demo login: demo@drivesync.example / drivesync-demo  (local stack only; never use on a hosted project)

do $$
declare
  demo_id uuid := '11111111-1111-4111-8111-111111111111';
  demo_email text := 'demo@drivesync.example';
begin
  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, email_change, email_change_token_new, recovery_token
  ) values (
    '00000000-0000-0000-0000-000000000000', demo_id, 'authenticated', 'authenticated', demo_email,
    crypt('drivesync-demo', gen_salt('bf')), now(),
    '{"provider":"email","providers":["email"]}', '{}', now(), now(), '', '', '', ''
  ) on conflict (id) do nothing;

  insert into auth.identities (id, user_id, provider_id, identity_data, provider, last_sign_in_at, created_at, updated_at)
  values (gen_random_uuid(), demo_id, demo_id::text,
          jsonb_build_object('sub', demo_id::text, 'email', demo_email), 'email', now(), now(), now())
  on conflict do nothing;

  insert into public.customers (id, owner_id, company_name, contact_name, contact_email, history_summary) values
    ('c-hanbit', demo_id, '한빛메디칼', '김도윤 구매팀장', 'buyer@hanbit-medical.example',
     '1차 방문: 초음파 프로브 교체 수요 확인, 납기 4주 이내 요구. 경쟁사 견적 대비 8% 높다는 피드백.'),
    ('c-seoyeon', demo_id, '서연정형외과', '박서연 원장', 'director@seoyeon-clinic.example',
     '첫 방문 예정. 소개처: 한빛메디칼.'),
    ('c-daon', demo_id, '다온제약 물류센터', '이준호 과장', 'logistics@daon-pharma.example',
     '2차 방문: 콜드체인 모니터링 시범 운영 합의, 계약서 초안 미전달(후속 작업 이월).')
  on conflict (id) do nothing;

  insert into public.visits (id, owner_id, customer_id, purpose, scheduled_at, place_name, latitude, longitude) values
    ('v-1', demo_id, 'c-hanbit', '2차 견적 협의 · 납기 조건 확정', now() + interval '1 hour', '한빛메디칼 본사', 37.5133, 127.1028),
    ('v-2', demo_id, 'c-seoyeon', '신규 소개 미팅', now() + interval '3 hours', '서연정형외과', 37.4979, 127.0276),
    ('v-3', demo_id, 'c-daon', '시범 운영 리뷰 · 계약서 전달', now() + interval '5 hours', '다온제약 물류센터', 37.2636, 127.0286)
  on conflict (id) do nothing;

  -- Carried-over action from the previous Daon visit (feeds the next briefing).
  insert into public.actions (id, owner_id, session_id, customer_id, type, draft, status) values
    ('a-daon-contract', demo_id, gen_random_uuid(), 'c-daon', 'email',
     '{"to":"logistics@daon-pharma.example","subject":"콜드체인 시범 운영 계약서 초안","body":"이준호 과장님, 지난 미팅에서 말씀드린 계약서 초안을 전달드립니다."}',
     'draft')
  on conflict (id) do nothing;
end $$;
