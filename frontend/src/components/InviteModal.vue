<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  open: {
    type: Boolean,
    default: false
  },
  classId: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['close', 'invite'])

const searchEmail = ref('')
const isSearching = ref(false)
const searchResults = ref([])
const selectedUsers = ref([])
const isInviting = ref(false)
const inviteMessage = ref('')

// 임시 회원 데이터 (실제로는 API에서 가져올 데이터)
const mockUsers = [
  { id: 1, email: 'user1@example.com', name: '김철수', avatar: '/src/assets/member/1.png' },
  { id: 2, email: 'user2@example.com', name: '이영희', avatar: '/src/assets/member/2.png' },
  { id: 3, email: 'user3@example.com', name: '박민수', avatar: '/src/assets/member/3.png' },
  { id: 4, email: 'user4@example.com', name: '정수진', avatar: '/src/assets/member/4.png' },
  { id: 5, email: 'user5@example.com', name: '최동현', avatar: '/src/assets/member/5.png' },
  { id: 6, email: 'user6@example.com', name: '한미영', avatar: '/src/assets/member/6.png' }
]

// 이메일 검색 결과
const filteredUsers = computed(() => {
  if (!searchEmail.value.trim()) return []
  
  const searchTerm = searchEmail.value.toLowerCase()
  return mockUsers.filter(user => 
    user.email.toLowerCase().includes(searchTerm) ||
    user.name.toLowerCase().includes(searchTerm)
  )
})

// 이메일 검색
const searchUsers = async () => {
  if (!searchEmail.value.trim()) {
    searchResults.value = []
    return
  }

  isSearching.value = true
  
  try {
    // 실제로는 API 호출
    await new Promise(resolve => setTimeout(resolve, 500))
    searchResults.value = filteredUsers.value
  } catch (error) {
    console.error('사용자 검색 실패:', error)
    searchResults.value = []
  } finally {
    isSearching.value = false
  }
}

// 사용자 선택/해제
const toggleUserSelection = (user) => {
  const index = selectedUsers.value.findIndex(u => u.id === user.id)
  if (index > -1) {
    selectedUsers.value.splice(index, 1)
  } else {
    selectedUsers.value.push(user)
  }
}

// 선택된 사용자 제거
const removeSelectedUser = (userId) => {
  selectedUsers.value = selectedUsers.value.filter(u => u.id !== userId)
}

// 초대 전송
const handleInvite = async () => {
  if (selectedUsers.value.length === 0) {
    alert('초대할 회원을 선택해주세요.')
    return
  }

  isInviting.value = true
  
  try {
    // 각 선택된 사용자별로 초대 처리
    for (const user of selectedUsers.value) {
      try {
        // 여기에 실제 초대 API 호출 로직을 추가할 수 있습니다
        await new Promise(resolve => setTimeout(resolve, 300))
        
        console.log(`${user.name}(${user.email}) 초대 전송 완료`)
        
      } catch (error) {
        console.error(`${user.name} 초대 실패:`, error)
      }
    }
    
    // 백엔드로 전송할 데이터 형식: 이메일 배열
    const inviteData = {
      users: selectedUsers.value.map(user => user.email),
      classId: props.classId
    }
    
    emit('invite', inviteData)
    console.log('초대 데이터:', inviteData)
    inviteMessage.value = `${selectedUsers.value.length}명의 회원에게 초대가 전송되었습니다.`
    
    // 초기화
    selectedUsers.value = []
    searchEmail.value = ''
    searchResults.value = []
    
    setTimeout(() => {
      emit('close')
      inviteMessage.value = ''
    }, 2000)
    console.log(selectedUsers.value)    
  } catch (error) {
    alert('초대 전송에 실패했습니다. 다시 시도해주세요.')
  } finally {
    isInviting.value = false
  }
}

const handleClose = () => {
  searchEmail.value = ''
  searchResults.value = []
  selectedUsers.value = []
  inviteMessage.value = ''
  emit('close')
}

const handleKeyup = (event) => {
  if (event.key === 'Enter') {
    searchUsers()
  }
}

// 이메일 입력 감지
watch(searchEmail, (newValue) => {
  if (newValue.trim()) {
    searchUsers()
  } else {
    searchResults.value = []
  }
})
</script>

<template>
  <div v-if="open" class="invite-modal-overlay" @click="handleClose">
    <div class="invite-modal" @click.stop>
      <div class="invite-modal-header">
        <h3>클래스 초대하기</h3>
        <button class="close-btn" @click="handleClose">×</button>
      </div>
      
             <div class="invite-modal-content">
         <div class="invite-form">
           <label for="search-email">회원 검색</label>
           <div class="search-container">
             <input
               id="search-email"
               v-model="searchEmail"
               type="text"
               placeholder="이메일 또는 이름으로 회원을 검색하세요"
               @keyup="handleKeyup"
               :disabled="isInviting"
             />
             <div v-if="isSearching" class="search-loading">
               <span>검색 중...</span>
             </div>
           </div>
           
           <!-- 검색 결과 -->
           <div v-if="searchResults.length > 0" class="search-results">
             <h4>검색 결과</h4>
             <div class="user-list">
               <div 
                 v-for="user in searchResults" 
                 :key="user.id" 
                 class="user-item"
                 :class="{ selected: selectedUsers.some(u => u.id === user.id) }"
                 @click="toggleUserSelection(user)"
               >
                 <div class="user-avatar">
                   <img :src="user.avatar" :alt="user.name" />
                 </div>
                 <div class="user-info">
                   <span class="user-name">{{ user.name }}</span>
                   <span class="user-email">{{ user.email }}</span>
                 </div>
                 <div class="user-action">
                   <span v-if="selectedUsers.some(u => u.id === user.id)" class="selected-icon">✓</span>
                   <span v-else class="add-icon">+</span>
                 </div>
               </div>
             </div>
           </div>
           
           <!-- 선택된 회원들 -->
           <div v-if="selectedUsers.length > 0" class="selected-users">
             <h4>선택된 회원 ({{ selectedUsers.length }}명)</h4>
             <div class="selected-list">
               <div 
                 v-for="user in selectedUsers" 
                 :key="user.id" 
                 class="selected-item"
               >
                 <div class="user-avatar small">
                   <img :src="user.avatar" :alt="user.name" />
                 </div>
                 <div class="user-info">
                   <span class="user-name">{{ user.name }}</span>
                   <span class="user-email">{{ user.email }}</span>
                 </div>
                 <button 
                   class="remove-btn" 
                   @click="removeSelectedUser(user.id)"
                   :disabled="isInviting"
                 >
                   ×
                 </button>
               </div>
             </div>
           </div>
           
           <div class="invite-actions">
             <button 
               class="btn cancel" 
               @click="handleClose"
               :disabled="isInviting"
             >
               취소
             </button>
             <button 
               class="btn invite" 
               @click="handleInvite"
               :disabled="isInviting || selectedUsers.length === 0"
             >
               <span v-if="isInviting">초대 중...</span>
               <span v-else>초대하기 ({{ selectedUsers.length }}명)</span>
             </button>
           </div>
           
           <div v-if="inviteMessage" class="invite-message">
             {{ inviteMessage }}
           </div>
         </div>
       </div>
    </div>
  </div>
</template>

<style scoped>
.invite-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.invite-modal {
  background: white;
  border-radius: 12px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
  width: 90%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
}

.invite-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 24px 0 24px;
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 16px;
}

.invite-modal-header h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #111827;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #6b7280;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  transition: background-color 0.2s;
}

.close-btn:hover {
  background-color: #f3f4f6;
  color: #374151;
}

.invite-modal-content {
  padding: 24px;
}

.invite-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.invite-form label {
  font-weight: 500;
  color: #374151;
  font-size: 14px;
}

.search-container {
  position: relative;
}

.invite-form input {
  width: 100%;
  padding: 12px 16px;
  border: 2px solid #d1d5db;
  border-radius: 8px;
  font-size: 16px;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.invite-form input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.invite-form input:disabled {
  background-color: #f9fafb;
  color: #6b7280;
  cursor: not-allowed;
}

.search-loading {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: #6b7280;
  font-size: 14px;
}

.search-results {
  margin-top: 16px;
}

.search-results h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.user-list {
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.user-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  transition: background-color 0.2s;
  border-bottom: 1px solid #f3f4f6;
}

.user-item:last-child {
  border-bottom: none;
}

.user-item:hover {
  background-color: #f9fafb;
}

.user-item.selected {
  background-color: #eff6ff;
  border-color: #3b82f6;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
  margin-right: 12px;
  flex-shrink: 0;
}

.user-avatar.small {
  width: 32px;
  height: 32px;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-weight: 500;
  color: #111827;
  font-size: 14px;
}

.user-email {
  color: #6b7280;
  font-size: 12px;
}

.user-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-size: 16px;
  font-weight: bold;
}

.selected-icon {
  color: #10b981;
}

.add-icon {
  color: #6b7280;
}

.selected-users {
  margin-top: 16px;
  padding: 16px;
  background-color: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.selected-users h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.selected-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.selected-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  background-color: white;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
}

.remove-btn {
  background: none;
  border: none;
  color: #ef4444;
  font-size: 18px;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.remove-btn:hover:not(:disabled) {
  background-color: #fef2f2;
}

.remove-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.invite-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 8px;
}

.btn {
  padding: 10px 20px;
  border-radius: 8px;
  font-weight: 500;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
  min-width: 80px;
}

.btn.cancel {
  background-color: #f3f4f6;
  color: #374151;
}

.btn.cancel:hover:not(:disabled) {
  background-color: #e5e7eb;
}

.btn.invite {
  background-color: #3b82f6;
  color: white;
}

.btn.invite:hover:not(:disabled) {
  background-color: #2563eb;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.invite-message {
  padding: 12px 16px;
  background-color: #d1fae5;
  color: #065f46;
  border-radius: 8px;
  font-size: 14px;
  text-align: center;
  margin-top: 12px;
}



/* 다크모드 지원 */
.dark-mode .invite-modal {
  background: #1f2937;
  color: #f9fafb;
}

.dark-mode .invite-modal-header {
  border-bottom-color: #374151;
}

.dark-mode .invite-modal-header h3 {
  color: #f9fafb;
}

.dark-mode .close-btn {
  color: #9ca3af;
}

.dark-mode .close-btn:hover {
  background-color: #374151;
  color: #d1d5db;
}

.dark-mode .invite-form label {
  color: #d1d5db;
}

.dark-mode .invite-form input {
  background-color: #374151;
  border-color: #4b5563;
  color: #f9fafb;
}

.dark-mode .invite-form input:focus {
  border-color: #60a5fa;
  box-shadow: 0 0 0 3px rgba(96, 165, 250, 0.1);
}

.dark-mode .invite-form input:disabled {
  background-color: #1f2937;
  color: #6b7280;
}

.dark-mode .search-loading {
  color: #9ca3af;
}

.dark-mode .search-results h4 {
  color: #d1d5db;
}

.dark-mode .user-list {
  border-color: #374151;
  background-color: #1f2937;
}

.dark-mode .user-item {
  border-bottom-color: #374151;
}

.dark-mode .user-item:hover {
  background-color: #374151;
}

.dark-mode .user-item.selected {
  background-color: #1e3a8a;
  border-color: #3b82f6;
}

.dark-mode .user-name {
  color: #f9fafb;
}

.dark-mode .user-email {
  color: #9ca3af;
}

.dark-mode .add-icon {
  color: #9ca3af;
}

.dark-mode .selected-users {
  background-color: #1f2937;
  border-color: #374151;
}

.dark-mode .selected-users h4 {
  color: #d1d5db;
}

.dark-mode .selected-item {
  background-color: #374151;
  border-color: #4b5563;
}

.dark-mode .remove-btn:hover:not(:disabled) {
  background-color: #7f1d1d;
}

.dark-mode .btn.cancel {
  background-color: #374151;
  color: #d1d5db;
}

.dark-mode .btn.cancel:hover:not(:disabled) {
  background-color: #4b5563;
}

.dark-mode .invite-message {
  background-color: #065f46;
  color: #d1fae5;
}
</style> 