<template>
    <div class="mypage-view">
      <!-- 헤더 섹션 -->
      <section class="mypage-header">
        <div class="header-container">
          <div class="header-content">
            <div class="header-badge">마이페이지</div>
            <h1 class="header-title">
              안녕하세요, <span class="highlight">{{ userInfo.nickname }}</span>님!
            </h1>
            <p class="header-description">
              EduMeet에서의 학습 활동을 확인하고 관리하세요.
            </p>
          </div>
          <div class="header-visual">
            <div class="profile-card">
              <div class="profile-avatar">
                <img 
                  src="@/assets/member/1.png" 
                  alt="프로필 이미지" 
                  class="avatar-image"
                />
                <div class="avatar-overlay">
                  <span class="edit-icon">✏️</span>
                </div>
              </div>
              <div class="profile-info">
                <h3 class="profile-name">{{ userInfo.nickname }}</h3>
                <p class="profile-email">{{ userInfo.email }}</p>
                <div class="profile-role">
                  <span class="role-badge">{{ userInfo.role === 'tutor' ? '튜터' : '학생' }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
  
      <!-- 통계 섹션 -->
      <section class="stats-section">
        <div class="section-container">
          <div class="section-header">
            <h2 class="section-title">학습 통계</h2>
            <p class="section-subtitle">이번 달 학습 활동을 확인해보세요</p>
          </div>
          <div class="stats-grid">
            <div class="stat-card">
              <div class="stat-icon">📚</div>
              <div class="stat-content">
                <div class="stat-number">{{ stats.totalClasses }}</div>
                <div class="stat-label">참여한 수업</div>
              </div>
            </div>
            <div class="stat-card">
              <div class="stat-icon">⏱️</div>
              <div class="stat-content">
                <div class="stat-number">{{ stats.totalHours }}h</div>
                <div class="stat-label">총 학습 시간</div>
              </div>
            </div>
            <div class="stat-card">
              <div class="stat-icon">🎯</div>
              <div class="stat-content">
                <div class="stat-number">{{ stats.completionRate }}%</div>
                <div class="stat-label">수강 완료율</div>
              </div>
            </div>
            <div class="stat-card">
              <div class="stat-icon">⭐</div>
              <div class="stat-content">
                <div class="stat-number">{{ stats.rating }}</div>
                <div class="stat-label">평균 평점</div>
              </div>
            </div>
          </div>
        </div>
      </section>
  
      <!-- 활동 섹션 -->
      <section class="activity-section">
        <div class="section-container">
          <div class="section-header">
            <h2 class="section-title">최근 활동</h2>
            <p class="section-subtitle">최근 참여한 수업과 활동 내역입니다</p>
          </div>
          <div class="activity-list">
            <div 
              v-for="activity in recentActivities" 
              :key="activity.id"
              class="activity-card"
            >
              <div class="activity-icon">
                <span v-if="activity.type === 'class'">📖</span>
                <span v-else-if="activity.type === 'assignment'">📝</span>
                <span v-else>🎯</span>
              </div>
              <div class="activity-content">
                <h3 class="activity-title">{{ activity.title }}</h3>
                <p class="activity-description">{{ activity.description }}</p>
                <div class="activity-meta">
                  <span class="activity-time">{{ activity.time }}</span>
                  <span class="activity-status" :class="activity.status">
                    {{ getStatusText(activity.status) }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
  
      <!-- 설정 섹션 -->
      <section class="settings-section">
        <div class="section-container">
          <div class="section-header">
            <h2 class="section-title">계정 설정</h2>
            <p class="section-subtitle">프로필과 계정 정보를 관리하세요</p>
          </div>
          <div class="settings-grid">
            <div class="setting-card">
              <div class="setting-icon">👤</div>
              <div class="setting-content">
                <h3 class="setting-title">프로필 수정</h3>
                <p class="setting-description">닉네임과 프로필 정보를 변경하세요</p>
                <button class="setting-btn">수정하기</button>
              </div>
            </div>
            <div class="setting-card">
              <div class="setting-icon">🔒</div>
              <div class="setting-content">
                <h3 class="setting-title">비밀번호 변경</h3>
                <p class="setting-description">계정 보안을 위해 비밀번호를 변경하세요</p>
                <button class="setting-btn">변경하기</button>
              </div>
            </div>
            <div class="setting-card">
              <div class="setting-icon">🔔</div>
              <div class="setting-content">
                <h3 class="setting-title">알림 설정</h3>
                <p class="setting-description">수업 알림과 메시지 설정을 관리하세요</p>
                <button class="setting-btn">설정하기</button>
              </div>
            </div>
            <div class="setting-card">
              <div class="setting-icon">📊</div>
              <div class="setting-content">
                <h3 class="setting-title">학습 데이터</h3>
                <p class="setting-description">학습 기록과 통계를 확인하세요</p>
                <button class="setting-btn">확인하기</button>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </template>
  
  <script>
  import { ref, computed, onMounted } from 'vue'
  import { useAuthStore } from '@/stores/auth'
  
  export default {
    name: 'MyPageView',
    setup() {
      const authStore = useAuthStore()
      
      // 사용자 정보
      const userInfo = computed(() => {
        return authStore.currentUser || {
          nickname: '사용자',
          email: 'user@example.com',
          role: 'student'
        }
      })
  
      // 통계 데이터
      const stats = ref({
        totalClasses: 12,
        totalHours: 48,
        completionRate: 85,
        rating: 4.8
      })
  
      // 최근 활동
      const recentActivities = ref([
        {
          id: 1,
          type: 'class',
          title: 'JavaScript 기초 강의',
          description: '변수와 함수에 대한 기본 개념을 학습했습니다.',
          time: '2시간 전',
          status: 'completed'
        },
        {
          id: 2,
          type: 'assignment',
          title: 'Vue.js 프로젝트 제출',
          description: 'Todo 앱 프로젝트를 완성하여 제출했습니다.',
          time: '1일 전',
          status: 'completed'
        },
        {
          id: 3,
          type: 'class',
          title: 'React Hooks 심화',
          description: 'useEffect와 useCallback에 대해 학습했습니다.',
          time: '3일 전',
          status: 'in-progress'
        }
      ])
  
      // 상태 텍스트 변환
      const getStatusText = (status) => {
        switch (status) {
          case 'completed':
            return '완료'
          case 'in-progress':
            return '진행중'
          case 'pending':
            return '대기중'
          default:
            return '알 수 없음'
        }
      }
  
      onMounted(() => {
        // 페이지 로드 시 필요한 데이터 로드
        console.log('마이페이지 로드됨')
      })
  
      return {
        userInfo,
        stats,
        recentActivities,
        getStatusText
      }
    }
  }
  </script>
  
  <style scoped>
  .mypage-view {
    min-height: 100vh;
    background-color: var(--bg-secondary);
    transition: background-color var(--transition-normal);
  }
  
  /* 헤더 섹션 */
  .mypage-header {
    padding: var(--spacing-2xl) 0;
    background: linear-gradient(135deg, var(--bg-tertiary) 0%, var(--border-color) 100%);
    transition: background var(--transition-normal);
  }
  
  .header-container {
    max-width: 1200px;
    margin: 0 auto;
    padding: 0 var(--spacing-xl);
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--spacing-2xl);
    align-items: center;
  }
  
  .header-content {
    text-align: left;
  }
  
  .header-badge {
    display: inline-block;
    background: linear-gradient(135deg, var(--brand-main) 0%, var(--brand-sub) 100%);
    color: var(--text-inverse);
    font-size: var(--font-size-sm);
    font-weight: 600;
    padding: 8px 16px;
    border-radius: 20px;
    margin-bottom: var(--spacing-md);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }
  
  .header-title {
    font-size: var(--font-size-4xl);
    font-weight: 700;
    color: var(--text-primary);
    margin-bottom: var(--spacing-md);
    line-height: 1.2;
    transition: color var(--transition-normal);
  }
  
  .header-title .highlight {
    color: var(--brand-main);
  }
  
  .header-description {
    font-size: var(--font-size-lg);
    color: var(--text-secondary);
    line-height: 1.6;
    transition: color var(--transition-normal);
  }
  
  .header-visual {
    display: flex;
    justify-content: center;
  }
  
  .profile-card {
    background: var(--bg-card);
    border-radius: var(--radius-lg);
    padding: var(--spacing-xl);
    box-shadow: var(--shadow-card);
    transition: all var(--transition-normal);
    border: 1px solid var(--border-color);
  }
  
  .profile-card:hover {
    transform: translateY(-4px);
    box-shadow: var(--shadow-lg);
  }
  
  .profile-avatar {
    position: relative;
    width: 120px;
    height: 120px;
    margin: 0 auto var(--spacing-lg);
    border-radius: 50%;
    overflow: hidden;
    border: 4px solid var(--brand-main);
  }
  
  .avatar-image {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  
  .avatar-overlay {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    opacity: 0;
    transition: opacity var(--transition-normal);
  }
  
  .profile-avatar:hover .avatar-overlay {
    opacity: 1;
  }
  
  .edit-icon {
    font-size: 24px;
    color: white;
  }
  
  .profile-info {
    text-align: center;
  }
  
  .profile-name {
    font-size: var(--font-size-xl);
    font-weight: 600;
    color: var(--text-primary);
    margin-bottom: var(--spacing-sm);
    transition: color var(--transition-normal);
  }
  
  .profile-email {
    font-size: var(--font-size-sm);
    color: var(--text-secondary);
    margin-bottom: var(--spacing-md);
    transition: color var(--transition-normal);
  }
  
  .profile-role {
    display: inline-block;
  }
  
  .role-badge {
    background: linear-gradient(135deg, var(--brand-main) 0%, var(--brand-sub) 100%);
    color: var(--text-inverse);
    font-size: var(--font-size-sm);
    font-weight: 600;
    padding: 4px 12px;
    border-radius: 12px;
  }
  
  /* 통계 섹션 */
  .stats-section {
    padding: var(--spacing-2xl) 0;
    background-color: var(--bg-secondary);
    transition: background-color var(--transition-normal);
  }
  
  .section-container {
    max-width: 1200px;
    margin: 0 auto;
    padding: 0 var(--spacing-xl);
  }
  
  .section-header {
    text-align: center;
    margin-bottom: var(--spacing-xl);
  }
  
  .section-title {
    font-size: var(--font-size-3xl);
    font-weight: 700;
    color: var(--text-primary);
    margin-bottom: var(--spacing-md);
    transition: color var(--transition-normal);
  }
  
  .section-subtitle {
    font-size: var(--font-size-lg);
    color: var(--text-secondary);
    line-height: 1.6;
    transition: color var(--transition-normal);
  }
  
  .stats-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
    gap: var(--spacing-lg);
  }
  
  .stat-card {
    background: var(--bg-card);
    border-radius: var(--radius-lg);
    padding: var(--spacing-xl);
    box-shadow: var(--shadow-card);
    transition: all var(--transition-normal);
    border: 1px solid var(--border-color);
    display: flex;
    align-items: center;
    gap: var(--spacing-lg);
  }
  
  .stat-card:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-lg);
  }
  
  .stat-icon {
    font-size: 2.5rem;
    width: 60px;
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, var(--brand-main) 0%, var(--brand-sub) 100%);
    border-radius: 50%;
    color: var(--text-inverse);
  }
  
  .stat-content {
    flex: 1;
  }
  
  .stat-number {
    font-size: var(--font-size-3xl);
    font-weight: 700;
    color: var(--text-primary);
    margin-bottom: var(--spacing-xs);
    transition: color var(--transition-normal);
  }
  
  .stat-label {
    font-size: var(--font-size-sm);
    color: var(--text-secondary);
    font-weight: 500;
    transition: color var(--transition-normal);
  }
  
  /* 활동 섹션 */
  .activity-section {
    padding: var(--spacing-2xl) 0;
    background: linear-gradient(135deg, var(--bg-tertiary) 0%, var(--border-color) 100%);
    transition: background var(--transition-normal);
  }
  
  .activity-list {
    display: flex;
    flex-direction: column;
    gap: var(--spacing-md);
  }
  
  .activity-card {
    background: var(--bg-card);
    border-radius: var(--radius-lg);
    padding: var(--spacing-lg);
    box-shadow: var(--shadow-card);
    transition: all var(--transition-normal);
    border: 1px solid var(--border-color);
    display: flex;
    align-items: center;
    gap: var(--spacing-lg);
  }
  
  .activity-card:hover {
    transform: translateX(4px);
    box-shadow: var(--shadow-lg);
  }
  
  .activity-icon {
    font-size: 2rem;
    width: 60px;
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--bg-tertiary);
    border-radius: 50%;
    flex-shrink: 0;
  }
  
  .activity-content {
    flex: 1;
  }
  
  .activity-title {
    font-size: var(--font-size-lg);
    font-weight: 600;
    color: var(--text-primary);
    margin-bottom: var(--spacing-xs);
    transition: color var(--transition-normal);
  }
  
  .activity-description {
    font-size: var(--font-size-sm);
    color: var(--text-secondary);
    margin-bottom: var(--spacing-sm);
    transition: color var(--transition-normal);
  }
  
  .activity-meta {
    display: flex;
    align-items: center;
    gap: var(--spacing-md);
  }
  
  .activity-time {
    font-size: var(--font-size-xs);
    color: var(--text-tertiary);
    transition: color var(--transition-normal);
  }
  
  .activity-status {
    font-size: var(--font-size-xs);
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 8px;
    text-transform: uppercase;
  }
  
  .activity-status.completed {
    background: #d4edda;
    color: #155724;
  }
  
  .activity-status.in-progress {
    background: #fff3cd;
    color: #856404;
  }
  
  .activity-status.pending {
    background: #f8d7da;
    color: #721c24;
  }
  
  /* 설정 섹션 */
  .settings-section {
    padding: var(--spacing-2xl) 0;
    background-color: var(--bg-secondary);
    transition: background-color var(--transition-normal);
  }
  
  .settings-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: var(--spacing-lg);
  }
  
  .setting-card {
    background: var(--bg-card);
    border-radius: var(--radius-lg);
    padding: var(--spacing-xl);
    box-shadow: var(--shadow-card);
    transition: all var(--transition-normal);
    border: 1px solid var(--border-color);
  }
  
  .setting-card:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-lg);
  }
  
  .setting-icon {
    font-size: 2rem;
    width: 60px;
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, var(--brand-main) 0%, var(--brand-sub) 100%);
    border-radius: 50%;
    color: var(--text-inverse);
    margin-bottom: var(--spacing-md);
  }
  
  .setting-title {
    font-size: var(--font-size-lg);
    font-weight: 600;
    color: var(--text-primary);
    margin-bottom: var(--spacing-sm);
    transition: color var(--transition-normal);
  }
  
  .setting-description {
    font-size: var(--font-size-sm);
    color: var(--text-secondary);
    margin-bottom: var(--spacing-lg);
    line-height: 1.5;
    transition: color var(--transition-normal);
  }
  
  .setting-btn {
    background: var(--brand-main);
    color: var(--text-inverse);
    border: none;
    padding: var(--spacing-sm) var(--spacing-md);
    border-radius: var(--radius-md);
    font-size: var(--font-size-sm);
    font-weight: 600;
    cursor: pointer;
    transition: all var(--transition-normal);
  }
  
  .setting-btn:hover {
    background: var(--brand-sub);
    transform: translateY(-1px);
  }
  
  /* 반응형 디자인 */
  @media (max-width: 768px) {
    .header-container {
      grid-template-columns: 1fr;
      gap: var(--spacing-lg);
      text-align: center;
    }
  
    .header-title {
      font-size: var(--font-size-3xl);
    }
  
    .stats-grid {
      grid-template-columns: repeat(2, 1fr);
    }
  
    .settings-grid {
      grid-template-columns: 1fr;
    }
  
    .activity-card {
      flex-direction: column;
      text-align: center;
    }
  
    .activity-meta {
      justify-content: center;
    }
  }
  
  @media (max-width: 480px) {
    .stats-grid {
      grid-template-columns: 1fr;
    }
  
    .header-title {
      font-size: var(--font-size-2xl);
    }
  
    .section-title {
      font-size: var(--font-size-2xl);
    }
  }
  </style>