<template>
  <div v-if="isVisible" class="modal-overlay" @click="closeModal">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h2 class="modal-title">👥 {{ className }} 학생 목록</h2>
        <button class="close-btn" @click="closeModal">✕</button>
      </div>
      
      <div class="modal-body">
        <div v-if="loading" class="loading">
          <div class="spinner"></div>
          <p>학생 목록을 불러오는 중...</p>
        </div>
        
        <div v-else-if="error" class="error">
          <p>❌ {{ error }}</p>
          <button @click="fetchMembers" class="retry-btn">다시 시도</button>
        </div>
        
        <div v-else-if="members.length === 0" class="empty">
          <p>아직 등록된 학생이 없습니다.</p>
        </div>
        
        <div v-else class="members-list">
          <div 
            class="member-item" 
            v-for="(member, index) in members" 
            :key="index"
            @click="handleMemberClick(member)"
            :class="{ 'clickable': isTeacher && member.email !== currentUserEmail }"
          >
            <div class="member-avatar">
              <span class="avatar-text">{{ member.nickname.charAt(0) }}</span>
            </div>
            <div class="member-info">
              <h3 class="member-name">{{ member.nickname }}</h3>
              <p class="member-email">{{ member.email }}</p>
            </div>
            <div class="member-role">
              <span class="role-badge" :class="{ 'teacher': member.email === teacherEmail }">
                {{ member.email === teacherEmail ? '선생님' : '학생' }}
              </span>
            </div>
            
            <!-- 강제 퇴장 버튼 (선생님이고 학생을 클릭했을 때만 표시) -->
            <div v-if="isTeacher && selectedMember && selectedMember.email === member.email" class="kick-actions">
              <button 
                @click.stop="() => { console.log('🔘 강제 퇴장 버튼 클릭됨:', member); showKickConfirm(member); }" 
                class="kick-btn"
                :disabled="kickingMember === member.email"
              >
                {{ kickingMember === member.email ? '처리중...' : '강제 퇴장' }}
              </button>
            </div>
          </div>
        </div>
      </div>
      
      <div class="modal-footer">
        <p class="member-count">총 {{ members.length }}명</p>
        <button class="close-modal-btn" @click="closeModal">닫기</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, computed } from 'vue';
import { useAuthStore } from '@/stores/auth';
import apiClient from '@/utils/apiClient';

const props = defineProps({
  isVisible: {
    type: Boolean,
    default: false
  },
  classId: {
    type: [String, Number],
    required: true
  },
  className: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['close']);

const authStore = useAuthStore();
const members = ref([]);
const loading = ref(false);
const error = ref(null);
const teacherEmail = ref('');
const selectedMember = ref(null);
const kickingMember = ref(null);

// 현재 사용자 정보
const currentUserEmail = computed(() => {
  return authStore.currentUser?.email || '';
});

// 현재 사용자가 선생님인지 확인
const isTeacher = computed(() => {
  return currentUserEmail.value === teacherEmail.value;
});

// 모달 닫기
const closeModal = () => {
  selectedMember.value = null;
  emit('close');
};

// 학생 클릭 처리
const handleMemberClick = (member) => {
  // 선생님이고 학생을 클릭한 경우에만 선택
  if (isTeacher.value && member.email !== currentUserEmail.value) {
    selectedMember.value = selectedMember.value?.email === member.email ? null : member;
  }
};

// 강제 퇴장 확인 다이얼로그
const showKickConfirm = (member) => {
  console.log('💬 showKickConfirm 호출됨:', member);
  if (confirm(`정말로 ${member.nickname}님을 강제 퇴장시키시겠습니까?`)) {
    console.log('✅ 사용자가 확인함, kickMember 호출');
    kickMember(member);
  } else {
    console.log('❌ 사용자가 취소함');
  }
};

// 강제 퇴장 API 호출
const kickMember = async (member) => {
  console.log('🚀 kickMember 함수 시작');
  kickingMember.value = member.email;
  
  try {
    const accessToken = localStorage.getItem('token');
    if (!accessToken) {
      throw new Error('로그인이 필요합니다.');
    }

    // 디버깅을 위한 로그 추가
    console.log('🔍 강제 퇴장 API 호출 정보:');
    console.log('🔍 classId:', props.classId, '타입:', typeof props.classId);
    console.log('🔍 member.email:', member.email, '타입:', typeof member.email);
    console.log('🔍 요청 URL:', `/classroom/eviction`);
    console.log('🔍 요청 본문:', {
      classId: Number(props.classId),
      email: member.email
    });
    console.log('🔍 accessToken 존재:', !!accessToken);
    console.log('🔍 apiClient baseURL:', apiClient.defaults?.baseURL);

    // 백엔드 API 명세에 맞게 수정
    const response = await apiClient.delete(`/classroom/eviction`, {
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      },
      data: {
        classId: Number(props.classId),
        email: member.email
      }
    });

    console.log('강제 퇴장 성공:', response.data);
    
    // 성공 시 목록에서 제거
    members.value = members.value.filter(m => m.email !== member.email);
    selectedMember.value = null;
    
    // 성공 메시지 표시 (선택사항)
    alert(`${member.nickname}님이 강제 퇴장되었습니다.`);
    
  } catch (err) {
    console.error('❌ 강제 퇴장 실패:', err);
    console.error('❌ 에러 상세 정보:', {
      message: err.message,
      status: err.response?.status,
      statusText: err.response?.statusText,
      data: err.response?.data,
      config: err.config
    });
    
    let errorMessage = '강제 퇴장에 실패했습니다.';
    
    if (err.response) {
      const status = err.response.status;
      console.log('❌ HTTP 상태 코드:', status);
      console.log('❌ 에러 응답 데이터:', err.response.data);
      
      if (status === 400) {
        errorMessage = '잘못된 요청입니다.';
      } else if (status === 401) {
        errorMessage = '인증이 필요합니다.';
      } else if (status === 403) {
        errorMessage = '강제 퇴장 권한이 없습니다.';
      } else if (status === 404) {
        errorMessage = '클래스 또는 멤버를 찾을 수 없습니다.';
      } else if (status === 500) {
        errorMessage = '서버 오류가 발생했습니다.';
      }
    }
    
    alert(errorMessage);
  } finally {
    console.log('🏁 kickMember 함수 종료');
    kickingMember.value = null;
  }
};

// 학생 목록 조회
const fetchMembers = async () => {
  loading.value = true;
  error.value = null;

  try {
    const accessToken = localStorage.getItem('token');
    if (!accessToken) {
      throw new Error('로그인이 필요합니다.');
    }
    
    // apiClient의 baseURL이 이미 설정되어 있으므로 경로만 사용
    const apiUrl = `/classroom/${props.classId}/members`;
    console.log('📋 API 호출 시작:', apiUrl);
    console.log('📋 classId:', props.classId);
    console.log('📋 accessToken 존재:', !!accessToken);

    // fetch 대신 apiClient.get 사용
    const response = await apiClient.get(apiUrl);

    // axios는 2xx 응답인 경우에만 이 블록을 실행하며, 데이터는 response.data에 있음
    console.log('📋 API 응답 상태:', response.status);
    console.log('📋 API 응답 헤더:', response.headers); // axios의 헤더 객체
    
    const membersData = response.data;
    console.log('📋 학생 목록 조회 성공:', membersData);
    
    members.value = membersData;
    
    // 선생님 이메일 찾기 (첫 번째 사용자를 선생님으로 가정)
    if (membersData && membersData.length > 0) {
      teacherEmail.value = membersData[0].email;
      console.log('📋 teacherEmail 설정:', teacherEmail.value);
    }

  } catch (err) {
    console.error('📋 학생 목록 조회 실패:', err);
    
    // axios 에러 처리
    if (err.response) {
      const status = err.response.status;
      const errorText = err.response.data?.message || err.message; // 서버의 에러 메시지
      
      console.error('📋 API 에러 응답 전체:', errorText);

      // 백엔드 명세에 따른 구체적인 에러 처리
      if (status === 400) {
        error.value = '잘못된 요청입니다.';
      } else if (status === 401) {
        error.value = '인증이 필요합니다.';
      } else if (status === 403) {
        error.value = '접근 권한이 없습니다.';
      } else if (status === 404) {
        error.value = '클래스를 찾을 수 없습니다.';
      } else if (status === 500) {
        error.value = '서버 오류가 발생했습니다.';
      } else {
        error.value = `멤버 목록 조회 실패 (${status})`;
      }
    } else {
      // 네트워크 오류 또는 기타 예상치 못한 에러
      error.value = err.message || '네트워크 오류가 발생했습니다.';
    }
    
    // 백엔드가 준비되지 않았거나 권한이 없는 경우 Mock 데이터 사용
    if (error.value && ['클래스를 찾을 수 없습니다.', '접근 권한이 없습니다.', '서버 오류가 발생했습니다.'].includes(error.value)) {
      console.log('📋 백엔드 미준비 또는 권한 없음 - Mock 데이터 사용');
      const mockMembers = [
        { email: "teacher@example.com", nickname: "김선생님" },
        { email: "student1@example.com", nickname: "김철수" },
        { email: "student2@example.com", nickname: "이영희" },
        { email: "student3@example.com", nickname: "박민수" },
        { email: "student4@example.com", nickname: "정수진" }
      ];
      
      members.value = mockMembers;
      teacherEmail.value = mockMembers[0].email;
      error.value = null; // Mock 데이터를 사용하면 오류 메시지 초기화
    }
  } finally {
    loading.value = false;
  }
};

// 모달이 열릴 때 학생 목록 조회
onMounted(() => {
  if (props.isVisible) {
    fetchMembers();
  }
});

// props.isVisible이 변경될 때마다 학생 목록 조회
watch(() => props.isVisible, (newValue) => {
  if (newValue) {
    fetchMembers();
  } else {
    selectedMember.value = null;
  }
}, { immediate: true }); // 컴포넌트가 마운트될 때 즉시 실행되도록 immediate 옵션 추가
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 12px;
  width: 90%;
  max-width: 500px;
  max-height: 80vh;
  overflow: hidden;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e5e7eb;
}

.modal-title {
  font-size: 18px;
  font-weight: 600;
  color: #111827;
  margin: 0;
}

.close-btn {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  color: #6b7280;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.close-btn:hover {
  background: #f3f4f6;
  color: #374151;
}

.modal-body {
  padding: 24px;
  max-height: 400px;
  overflow-y: auto;
}

.loading {
  text-align: center;
  padding: 40px 20px;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f3f4f6;
  border-top: 4px solid #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 16px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.error {
  text-align: center;
  padding: 40px 20px;
  color: #ef4444;
}

.retry-btn {
  background: #3b82f6;
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  margin-top: 12px;
}

.empty {
  text-align: center;
  padding: 40px 20px;
  color: #6b7280;
}

.members-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.member-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  background: #f9fafb;
  transition: background 0.2s;
  position: relative;
}

.member-item.clickable {
  cursor: pointer;
}

.member-item.clickable:hover {
  background: #f3f4f6;
}

.member-item.clickable {
  cursor: pointer;
}

.member-item.clickable:hover {
  background: #f3f4f6;
}

.member-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar-text {
  color: white;
  font-weight: 600;
  font-size: 16px;
}

.member-info {
  flex: 1;
  min-width: 0;
}

.member-name {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 4px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-email {
  font-size: 12px;
  color: #6b7280;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-role {
  flex-shrink: 0;
}

.role-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: 12px;
  background: #e5e7eb;
  color: #374151;
}

.role-badge.teacher {
  background: #fef3c7;
  color: #92400e;
}

.kick-actions {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
}

.kick-btn {
  background: #ef4444;
  color: white;
  border: none;
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  transition: background 0.2s;
}

.kick-btn:hover:not(:disabled) {
  background: #dc2626;
}

.kick-btn:disabled {
  background: #9ca3af;
  cursor: not-allowed;
}

.modal-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-top: 1px solid #e5e7eb;
  background: #f9fafb;
}

.member-count {
  font-size: 14px;
  color: #6b7280;
  margin: 0;
}

.close-modal-btn {
  background: #3b82f6;
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: background 0.2s;
}

.close-modal-btn:hover {
  background: #2563eb;
}

/* 다크 모드 대응 */
@media (prefers-color-scheme: dark) {
  .modal-content {
    background: #1f2937;
    color: #f9fafb;
  }
  
  .modal-header {
    border-bottom-color: #374151;
  }
  
  .modal-title {
    color: #f9fafb;
  }
  
  .member-item {
    background: #374151;
  }
  
  .member-item:hover {
    background: #4b5563;
  }
  
  .member-name {
    color: #f9fafb;
  }
  
  .member-email {
    color: #9ca3af;
  }
  
  .modal-footer {
    border-top-color: #374151;
    background: #374151;
  }
  
  .member-count {
    color: #9ca3af;
  }
}
</style>
