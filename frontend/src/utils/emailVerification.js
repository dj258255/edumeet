// 이메일 인증 관련 유틸리티 함수들

// 더미 데이터에서 인증 코드 검증
export async function verifyEmailCode(email, code) {
  try {
    const response = await fetch('/data/email-verification.json');
    const data = await response.json();
    
    const verification = data.verificationCodes.find(
      item => item.email === email && item.code === code
    );
    
    if (!verification) {
      return { success: false, message: '인증 코드가 올바르지 않습니다.' };
    }
    
    // 만료 시간 확인
    const now = new Date();
    const expiresAt = new Date(verification.expiresAt);
    
    if (now > expiresAt) {
      return { success: false, message: '인증 코드가 만료되었습니다.' };
    }
    
    return { 
      success: true, 
      message: '이메일 인증이 완료되었습니다.',
      isVerified: verification.isVerified
    };
  } catch (error) {
    console.error('이메일 인증 오류:', error);
    return { success: false, message: '인증 처리 중 오류가 발생했습니다.' };
  }
}

// 인증 코드 발송 (더미 데이터)
export async function sendVerificationCode(email) {
  try {
    const response = await fetch('/data/email-verification.json');
    const data = await response.json();
    
    // 테스트 이메일인지 확인
    const isTestEmail = data.testEmails.includes(email);
    
    if (!isTestEmail) {
      return { success: false, message: '테스트 이메일이 아닙니다.' };
    }
    
    // 기존 인증 코드 찾기
    const existingVerification = data.verificationCodes.find(
      item => item.email === email
    );
    
    if (existingVerification) {
      return { 
        success: true, 
        message: '인증 코드가 발송되었습니다.',
        code: existingVerification.code
      };
    }
    
    // 새로운 인증 코드 생성 (8자리 랜덤)
    const newCode = Math.floor(10000000 + Math.random() * 90000000).toString();
    
    return { 
      success: true, 
      message: '인증 코드가 발송되었습니다.',
      code: newCode
    };
  } catch (error) {
    console.error('인증 코드 발송 오류:', error);
    return { success: false, message: '인증 코드 발송 중 오류가 발생했습니다.' };
  }
}

// 인증 코드 재발송
export async function resendVerificationCode(email) {
  try {
    const response = await fetch('/data/email-verification.json');
    const data = await response.json();
    
    // 테스트 이메일인지 확인
    const isTestEmail = data.testEmails.includes(email);
    
    if (!isTestEmail) {
      return { success: false, message: '테스트 이메일이 아닙니다.' };
    }
    
    // 새로운 인증 코드 생성 (8자리 랜덤)
    const newCode = Math.floor(10000000 + Math.random() * 90000000).toString();
    
    return { 
      success: true, 
      message: '새로운 인증 코드가 발송되었습니다.',
      code: newCode
    };
  } catch (error) {
    console.error('인증 코드 재발송 오류:', error);
    return { success: false, message: '인증 코드 재발송 중 오류가 발생했습니다.' };
  }
}

// 이메일 형식 검증
export function validateEmail(email) {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email);
}

// 인증 코드 형식 검증 (8자리 숫자)
export function validateVerificationCode(code) {
  const codeRegex = /^\d{8}$/;
  return codeRegex.test(code);
}

// 인증 설정 가져오기
export async function getVerificationSettings() {
  try {
    const response = await fetch('/data/email-verification.json');
    const data = await response.json();
    return data.verificationSettings;
  } catch (error) {
    console.error('인증 설정 로드 오류:', error);
    return {
      codeLength: 8,
      expirationMinutes: 10,
      maxAttempts: 3,
      resendCooldownMinutes: 1
    };
  }
} 