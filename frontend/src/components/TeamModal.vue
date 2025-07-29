<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

// 팀 멤버 이미지 import
import member1 from '@/assets/member/1.png'
import member2 from '@/assets/member/2.png'
import member3 from '@/assets/member/3.png'
import member4 from '@/assets/member/4.png'
import member5 from '@/assets/member/5.png'
import member6 from '@/assets/member/6.png'

const props = defineProps({
  open: Boolean,
  dark: {
    type: Boolean,
    default: false
  }
})
const emit = defineEmits(['close'])

// 팀 멤버 정보
const teamMembers = ref([
  { name: '이승민', photo: member1 },
  { name: '권시온', photo: member2 },
  { name: '박시은', photo: member3 },
  { name: '범 수', photo: member4 },
  { name: '전준영', photo: member5 },
  { name: '권민환', photo: member6 },
])

// 신고수 정보
const reportCounts = ref({})

// 신고 입력 모달 상태
const showReportModal = ref(false)
const reportTarget = ref('')
const reportReason = ref('')

const fetchReports = async () => {
  try {
    const { data } = await axios.get('https://example.com/api/reports')
    reportCounts.value = data.reduce((acc, item) => {
      acc[item.name] = item.reports
      return acc
    }, {})
  } catch (err) {
    console.error('신고수 로딩 에러:', err)
  }
}

onMounted(() => {
  fetchReports()
})

// 신고 버튼 클릭 시 입력 모달 열기
const openReportModal = (memberName) => {
  reportTarget.value = memberName
  reportReason.value = ''
  showReportModal.value = true
}

// 신고 제출
const submitReport = async () => {
  if (!reportReason.value.trim()) {
    alert('신고 내용을 입력하세요.')
    return
  }
  try {
    await axios.post('https://example.com/api/report', {
      name: reportTarget.value,
      reason: reportReason.value
    })
    await fetchReports()
    alert(`${reportTarget.value}님을 신고했습니다.`)
    showReportModal.value = false
  } catch (err) {
    console.error('신고 에러:', err)
    alert('신고에 실패했습니다. 다시 시도해주세요.')
  }
}
</script>

<template>
  <div v-if="open" class="modal-overlay" @click="$emit('close')">
    <div class="modal-content" :class="{ dark: props.dark }" @click.stop>
      <h2>우리 조원들</h2>

      <div class="member-list">
        <div
          class="member-item"
          :class="{ dark: props.dark }"
          v-for="(member, index) in teamMembers"
          :key="index"
        >
          <img :src="member.photo" alt="사진" />
          <p class="name">{{ member.name }}</p>
          <div class="heart">
            ❤️ <span>{{ reportCounts[member.name] || 0 }}</span>
          </div>
          <button class="report-btn" @click="openReportModal(member.name)">
            신고하기
          </button>
        </div>
      </div>
    </div>

    <!-- 신고 입력 모달 -->
    <div v-if="showReportModal" class="report-overlay" @click="showReportModal = false">
      <div class="report-content" @click.stop>
        <h3>신고 사유 입력</h3>
        <!-- 신고 대상 이름 표시 -->
        <div class="report-target">{{ reportTarget }}님을 신고합니다</div>
        <textarea
          v-model="reportReason"
          placeholder="신고 사유를 입력하세요"
        ></textarea>
        <div class="btns">
          <button @click="submitReport">제출</button>
          <button class="cancel" @click="showReportModal = false">취소</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 기존 모달 스타일 */
.modal-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.7);
  display: flex; justify-content: center; align-items: center;
  z-index: 999;
}
.modal-content {
  background: white;
  color: #333;
  padding: 20px;
  border-radius: 12px;
  width: 600px;
  text-align: center;
  max-height: 80vh;
  overflow-y: auto;
}
.modal-content.dark {
  background: #1f1f1f;
  color: #f2f2f2;
}

.member-list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-top: 20px;
}
.member-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #f8f8f8;
  padding: 10px;
  border-radius: 10px;
}
.member-item.dark {
  background: #2c2c2c;
}
.member-item img {
  width: 100px;
  height: 100px;
  border-radius: 50%;
}
.name {
  margin: 10px 0 5px;
  font-weight: bold;
}
.heart {
  margin-bottom: 8px;
  font-size: 16px;
}
.report-btn {
  background: #e74c3c;
  color: white;
  padding: 6px 10px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

/* 신고 입력 모달 */
.report-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.6);
  display: flex; justify-content: center; align-items: center;
  z-index: 1000;
}
/* 신고 입력 모달 */
.report-content {
  background: white;
  padding: 20px;
  border-radius: 10px;
  width: 400px;
  text-align: center;
}

/* 제목 스타일 수정 */
.report-content h3 {
  font-size: 20px;
  font-weight: bold;
  color: #333;  /* 진한 회색으로 명확하게 */
  margin-bottom: 10px;
  opacity: 1;   /* 혹시 투명도 적용돼 있으면 제거 */
}

/* 신고 대상 이름 */
.report-target {
  font-weight: bold;
  font-size: 18px;
  margin-bottom: 10px;
  color: #e74c3c;
}

.report-content textarea {
  width: 100%;
  height: 80px;
  margin: 10px 0;
  padding: 8px;
}
.report-content .btns {
  display: flex;
  justify-content: space-between;
}
.report-content button {
  flex: 1;
  margin: 5px;
  padding: 8px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}
.report-content button.cancel {
  background: #ccc;
}
.report-content button:not(.cancel) {
  background: #e74c3c;
  color: #fff;
}

/* 신고 대상 이름 스타일 */
.report-target {
  font-weight: bold;
  font-size: 18px;
  margin-bottom: 10px;
  color: #e74c3c;
}
</style>
