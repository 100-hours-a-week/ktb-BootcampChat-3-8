const { test, expect } = require('@playwright/test');
const { loginAction, registerAction, logoutAction } = require('../actions/auth.actions');

const BASE_URL = process.env.BASE_URL || 'http://localhost:3000';

test.describe.serial('인증 E2E 테스트', () => {
  let testUser;

  test.beforeAll(async ({ browser }) => {
    // 테스트용 계정 생성
    const context = await browser.newContext();
    const page = await context.newPage();
    
    testUser = {
      email: `testuser_${Date.now()}@example.com`,
      password: 'Password123!',
      passwordConfirm: 'Password123!',
      name: 'Test User',
    };
    
    // 회원가입 API 응답 대기
    const registerResponse = page.waitForResponse(
      response => response.url().includes('/api/auth/register') && (response.status() === 200 || response.status() === 201),
      { timeout: 10000 }
    );
    
    await registerAction(page, testUser);
    
    // API 응답 완료 대기
    await registerResponse;
    // 네트워크 요청 완료 대기
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);
    
    await page.close();
    await context.close();
  });

  test.describe('로그인', () => {
    test('올바른 계정 정보로 로그인 성공', async ({ page }) => {
      // 액션 실행
      await loginAction(page, testUser);

      // 검증 (이미 loginAction에서 waitForURL과 waitForLoadState를 수행함)
      await expect(page).toHaveURL(`${BASE_URL}/chat`, { timeout: 10000 });
      // 채팅 페이지의 주요 요소가 로드되었는지 확인
      await page.waitForSelector('[data-testid="chat-message-input"], [data-testid="chat-room-list"]', { timeout: 5000 }).catch(() => null);
    });

    test('잘못된 비밀번호로 로그인 실패', async ({ page }) => {
      // API 에러 응답 대기
      const errorResponse = page.waitForResponse(
        response => response.url().includes('/api/auth/login') && response.status() >= 400,
        { timeout: 10000 }
      ).catch(() => null);
      
      // 액션 실행
      await loginAction(page, {
        email: testUser.email,
        password: 'WrongPassword123!',
      }, false);

      // API 응답 완료 대기
      await errorResponse;
      // 네트워크 요청 완료 대기
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // 검증
      await expect(page).toHaveURL(`${BASE_URL}`);
      const errorElement = page.getByTestId('login-error-message');
      await expect(errorElement).toBeVisible({ timeout: 5000 });
    });

    test('존재하지 않는 이메일로 로그인 실패', async ({ page }) => {
      // API 에러 응답 대기
      const errorResponse = page.waitForResponse(
        response => response.url().includes('/api/auth/login') && response.status() >= 400,
        { timeout: 10000 }
      ).catch(() => null);
      
      // 액션 실행
      await loginAction(page, {
        email: 'nonexistent@example.com',
        password: 'password123',
      }, false);

      // API 응답 완료 대기
      await errorResponse;
      // 네트워크 요청 완료 대기
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // 검증
      await expect(page).toHaveURL(`${BASE_URL}/`);
      const errorElement = page.getByTestId('login-error-message');
      await expect(errorElement).toBeVisible({ timeout: 5000 });
    });

    test('빈 필드로 로그인 시도 시 검증 오류', async ({ page }) => {
      // 액션 실행
      await loginAction(page, {
        email: '',
        password: '',
      }, false);

      // 검증
      await expect(page).toHaveURL(`${BASE_URL}/`);
    });
  });

  test.describe('회원가입', () => {
    test('새로운 계정으로 회원가입 성공', async ({ page }) => {
      const newUser = {
        email: `newuser_${Date.now()}@example.com`,
        password: 'Password123!',
        passwordConfirm: 'Password123!',
        name: 'New User',
      };

      // 회원가입 API 응답 대기
      const registerResponse = page.waitForResponse(
        response => response.url().includes('/api/auth/register') && (response.status() === 200 || response.status() === 201),
        { timeout: 10000 }
      );

      // 액션 실행
      await registerAction(page, newUser);
      await registerResponse;
      
      // 회원가입 후 잠시 대기 (리다이렉트 또는 상태 업데이트)
      await page.waitForTimeout(500);
      
      await loginAction(page, newUser);

      // 검증
      await expect(page).toHaveURL(`${BASE_URL}/chat`, { timeout: 10000 });
      // 채팅 페이지의 주요 요소가 로드되었는지 확인
      await page.waitForSelector('[data-testid="chat-message-input"], [data-testid="chat-room-list"]', { timeout: 5000 }).catch(() => null);
    });

    test('중복된 이메일로 회원가입 실패', async ({ page }) => {
      // API 에러 응답 대기
      const errorResponse = page.waitForResponse(
        response => response.url().includes('/api/auth/register') && response.status() >= 400,
        { timeout: 10000 }
      ).catch(() => null);
      
      // 중복 이메일로 회원가입 시도
      await registerAction(page, testUser);
      
      // API 응답 완료 대기
      await errorResponse;
      // 네트워크 요청 완료 대기
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // 검증
      await expect(page).toHaveURL(`${BASE_URL}/register`, { timeout: 10000 });
      const errorElement = page.getByTestId('register-error-message');
      await expect(errorElement).toBeVisible({ timeout: 5000 });
    });
  });

  test.describe('로그아웃', () => {
    test.beforeEach(async ({ page }) => {
      // 로그인 상태로 시작
      await loginAction(page, testUser);
      await expect(page).toHaveURL(`${BASE_URL}/chat`);
    });

    test('로그아웃 성공', async ({ page }) => {
      // 로그아웃 API 응답 대기 (있는 경우)
      const logoutResponse = page.waitForResponse(
        response => response.url().includes('/api/auth/logout'),
        { timeout: 5000 }
      ).catch(() => null);
      
      // 액션 실행
      await logoutAction(page);
      
      // API 응답 완료 대기
      await logoutResponse;
      // 네트워크 요청 완료 대기
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => null);

      // 검증
      await expect(page).toHaveURL(new RegExp(`^${BASE_URL}/`), { timeout: 10000 });
      await expect(page.getByTestId('login-email-input')).toBeVisible({ timeout: 5000 });
    });
  });

  test.describe('인증 필요한 페이지 접근', () => {
    test('로그인하지 않은 상태에서 채팅 페이지 접근 시 리다이렉트', async ({ page }) => {
      // 액션 실행
      await page.goto(`${BASE_URL}/chat`);

      // 검증
      await expect(page).toHaveURL(new RegExp(`^${BASE_URL}/`));
      await expect(page.getByTestId('login-email-input')).toBeVisible();
    });

    test('로그인하지 않은 상태에서 프로필 페이지 접근 시 리다이렉트', async ({ page }) => {
      // 액션 실행
      await page.goto(`${BASE_URL}/profile`);

      // 검증
      await expect(page).toHaveURL(new RegExp(`^${BASE_URL}/`));
      await expect(page.getByTestId('login-email-input')).toBeVisible();
    });
  });
});
