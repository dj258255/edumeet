import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
//
// ★ vue-devtools 를 정적 import 하지 않는다. (#115)
//
//   이 플러그인은 모듈을 불러오는 시점에 localStorage 를 건드린다.
//   Node 25 의 localStorage 는 객체로 존재하지만 getItem 이 없어서
//   설정 파일을 읽는 것만으로 죽는다.
//
//     TypeError: localStorage.getItem is not a function
//       at getTimelineLayersStateFromStorage (@vue/devtools-kit)
//
//   그래서 빌드도 테스트도 Node 25 에서 돌지 않았다.
//   Node 22 컨테이너로 빌드해 피해 왔지만, 테스트는 로컬에서 돌아야 의미가 있다.
//
//   devtools 는 개발 서버에서만 필요하다. 그때만 동적으로 불러온다 -
//   그러면 빌드·테스트는 이 플러그인을 아예 로드하지 않으므로 Node 버전과 무관해진다.
//
//   command === 'serve' 만으로는 부족하다. vitest 는 내부적으로 vite 개발 서버를
//   띄우므로 command 가 'serve' 로 온다. 그래서 테스트에서도 devtools 가 로드됐다.
//   VITEST 환경변수로 함께 가른다.
export default defineConfig(async ({ command }) => {
  const plugins = [vue()]

  if (command === 'serve' && !process.env.VITEST) {
    const { default: vueDevTools } = await import('vite-plugin-vue-devtools')
    plugins.push(vueDevTools())
  }

  return {
    plugins,
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          secure: false,
        },
      },
    },
    test: {
      // 순수 로직만 시험한다. 컴포넌트 마운트는 별도 설정이 필요하고
      // 지금 시험하려는 판단(구간 캐싱·스포일러 방지)은 전부 순수 함수에 있다.
      include: ['src/**/*.test.js'],
      environment: 'node',
    },
  }
})
