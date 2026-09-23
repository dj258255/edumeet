/**
 * 하네스 설정 읽기. (#197)
 *
 * ★ 토큰은 저장소에 두지 않는다.
 *   ~/.edumeet-perf.env 에서 읽는다. 저장소 안에 있으면 커밋된다.
 *
 * ★ 토큰을 절대 찍지 않는다.
 *   앞 몇 자리만 남기는 마스킹조차 하지 않는다 - 아예 안 찍는다.
 *   오류 메시지와 산출물에도 넣지 않는다.
 */
import { readFileSync, statSync } from 'node:fs'
import { homedir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

export const BROWSER_DIR = join(dirname(fileURLToPath(import.meta.url)), '..')
export const ENV_PATH = join(homedir(), '.edumeet-perf.env')

const DEFAULTS = {
  SITE: 'https://studywithtymee.com',
  API: 'https://api.studywithtymee.com/api/v1',
  SSH_HOST: 'edumeet-oci',
  DOCKER_NET: 'edumeet_edumeet',
}

export function loadEnv() {
  let raw
  try {
    raw = readFileSync(ENV_PATH, 'utf8')
  } catch {
    throw new Error(
      `${ENV_PATH} 를 읽지 못했다.\n` +
        '  측정 계정 access token 과 방송 회의 번호를 그 파일에 둔다:\n' +
        '    EDUMEET_TOKEN=...\n' +
        '    EDUMEET_MEETING_ID=...\n' +
        `  권한은 600 으로 둔다: chmod 600 ${ENV_PATH}`,
    )
  }

  const mode = statSync(ENV_PATH).mode & 0o777
  if (mode !== 0o600) {
    console.warn(`[env] 경고: ${ENV_PATH} 권한이 ${mode.toString(8)} 다. 600 을 권한다.`)
  }

  const vars = {}
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue
    const eq = trimmed.indexOf('=')
    if (eq < 0) continue
    const key = trimmed.slice(0, eq).trim()
    vars[key] = trimmed.slice(eq + 1).trim().replace(/^["']|["']$/g, '')
  }

  const env = { ...DEFAULTS, ...vars }
  if (!env.EDUMEET_TOKEN) throw new Error(`${ENV_PATH} 에 EDUMEET_TOKEN 이 없다`)
  if (!env.EDUMEET_MEETING_ID) throw new Error(`${ENV_PATH} 에 EDUMEET_MEETING_ID 가 없다`)
  return env
}

export function outDir(run) {
  if (!run) throw new Error('--run <이름> 이 필요하다')
  return join(BROWSER_DIR, 'out', run)
}

/** `--key value` 를 그대로 객체로. 값이 없으면 true. */
export function parseArgs(argv) {
  const out = {}
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i]
    if (!token.startsWith('--')) continue
    const key = token.slice(2)
    const next = argv[i + 1]
    if (next && !next.startsWith('--')) {
      out[key] = next
      i += 1
    } else {
      out[key] = true
    }
  }
  return out
}
