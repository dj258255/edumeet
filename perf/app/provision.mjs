#!/usr/bin/env node
/**
 * 측정용 계정·수업·방송 회의를 만든다. (#200)
 *
 *   node perf/app/provision.mjs
 *   eval "$(node perf/app/provision.mjs)"      # TOKEN·MEETING_ID 를 환경에 넣는다
 *
 * ★ 왜 필요한가.
 *   perf 프로파일은 ddl-auto=create 라 시작할 때마다 스키마가 비어 있다. #200 게이트는
 *   발표자 토큰과 방송 회의 하나가 있어야 돌아간다. 그 둘을 만드는 일을 손으로 하면
 *   (가입 → 로그인 → 수업 → 회의 → 목록에서 id 찾기) 매번 같은 실수를 한다.
 *
 * ★ 토큰은 **stdout 으로만** 나간다. 사람이 읽는 진행 상황은 stderr 로 보낸다 -
 *   그래야 eval "$(...)" 로 받을 수 있고, 로그 파일에 토큰이 섞이지 않는다.
 *   (하네스 공통 규칙: 토큰을 로그·산출물에 남기지 않는다 - perf/app/lib 의 env.mjs 주석)
 *
 * ★ 비밀번호는 perf 전용 고정값이다. 운영 계정과 아무 관계가 없다 -
 *   이 앱은 perf 프로파일로만 뜨고 DB 는 ddl-auto=create 로 매번 새로 만들어진다.
 *
 * 환경변수: BASE_URL(기본 http://localhost:8081) · PERF_EMAIL · PERF_PASSWORD ·
 *           PERF_CLASS_TITLE · PERF_MEETING_TITLE
 */

const BASE = process.env.BASE_URL ?? 'http://localhost:8081'
const EMAIL = process.env.PERF_EMAIL ?? 'perf-broadcaster@edumeet.test'
const PASSWORD = process.env.PERF_PASSWORD ?? 'perf-only-pass-200'
const NICKNAME = process.env.PERF_NICKNAME ?? 'perf 발표자'
const CLASS_TITLE = process.env.PERF_CLASS_TITLE ?? 'perf-200 측정 수업'
const MEETING_TITLE = process.env.PERF_MEETING_TITLE ?? 'perf-200 측정 방송'

const log = (...args) => console.error(...args)

async function api(path, { method = 'GET', token = null, body = null } = {}) {
  const headers = {}
  if (body !== null) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body === null ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  let parsed = null
  try {
    parsed = text ? JSON.parse(text) : null
  } catch {
    parsed = text
  }
  return { status: res.status, ok: res.ok, body: parsed }
}

/** 실패하면 무엇을 부르다 실패했는지 남기고 끝낸다. */
function must(step, res, expected) {
  if (!expected.includes(res.status)) {
    log(`   ✗ ${step}: HTTP ${res.status} ${JSON.stringify(res.body)?.slice(0, 200)}`)
    process.exit(1)
  }
  log(`   ✓ ${step}: HTTP ${res.status}`)
  return res.body
}

log(`측정 환경 준비 · ${BASE}`)

// 1) 계정. 이미 있으면 가입은 건너뛰고 로그인한다 (재실행이 자연스럽게 되도록).
const signup = await api('/api/v1/members/signup', {
  method: 'POST',
  body: { email: EMAIL, password: PASSWORD, nickname: NICKNAME },
})
if (signup.status === 201) log(`   ✓ 회원가입: ${EMAIL}`)
else log(`   · 회원가입 건너뜀 (HTTP ${signup.status}) - 이미 있는 계정으로 본다`)

const login = must('로그인', await api('/api/v1/members/login', {
  method: 'POST',
  body: { email: EMAIL, password: PASSWORD },
}), [200])
const token = login?.accessToken
if (!token) {
  log('   ✗ 로그인 응답에 accessToken 이 없다')
  process.exit(1)
}

// 2) 수업. 내 수업 목록에서 제목으로 찾고, 없으면 만든다.
const myClasses = must('내 수업 목록', await api('/api/v1/classroom', { token }), [200])
let classId = (myClasses ?? []).find((c) => c.title === CLASS_TITLE)?.classId ?? null
if (classId === null) {
  must('수업 생성', await api('/api/v1/classroom', {
    method: 'POST',
    token,
    body: { title: CLASS_TITLE, description: 'perf 전용 측정 수업', limit: 500, tags: ['perf'] },
  }), [201])
  const again = must('내 수업 목록(재조회)', await api('/api/v1/classroom', { token }), [200])
  classId = (again ?? []).find((c) => c.title === CLASS_TITLE)?.classId ?? null
}
if (classId === null) {
  log('   ✗ 수업을 만들었는데 목록에서 찾지 못했다')
  process.exit(1)
}
log(`   ✓ classId=${classId}`)

// 3) 방송 회의. 그 수업의 회의 목록에서 방송형을 찾고, 없으면 만든다.
const listMeetings = async () => must('회의 목록', await api(`/api/v1/meetingroom/${classId}`, { token }), [200])
let meetings = await listMeetings()
let meeting = (meetings ?? []).find((m) => m.title === MEETING_TITLE && m.sessionType === 'BROADCAST') ?? null
if (!meeting) {
  // /token 이 회의 생성과 LiveKit 토큰 발급을 같이 한다 (MeetingController).
  must('방송 회의 생성', await api('/api/v1/meetingroom/token', {
    method: 'POST',
    token,
    body: {
      title: MEETING_TITLE,
      description: 'perf 전용 측정 방송',
      classId,
      sessionType: 'BROADCAST',   // 이게 없으면 화상강의가 만들어진다 (#76)
    },
  }), [200])
  meetings = await listMeetings()
  meeting = (meetings ?? []).find((m) => m.title === MEETING_TITLE && m.sessionType === 'BROADCAST') ?? null
}
if (!meeting) {
  log('   ✗ 방송 회의를 만들었는데 목록에서 찾지 못했다')
  process.exit(1)
}
log(`   ✓ meetingId=${meeting.meetingId} (sessionType=${meeting.sessionType})`)

// stdout 은 기계가 읽는다 - 사람이 읽는 것은 전부 stderr 로 나갔다.
process.stdout.write(`export TOKEN=${token}\n`)
process.stdout.write(`export MEETING_ID=${meeting.meetingId}\n`)
