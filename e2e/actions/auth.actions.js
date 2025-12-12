const BASE_URL = process.env.BASE_URL || 'http://localhost:3000';

/**
 * 로그인 액션
 * @param {import('@playwright/test').Page} page
 * @param {Object} credentials - { email: string, password: string }
 * @param waitForRedirect
 */
async function loginAction(page, credentials, waitForRedirect = true) {
  // 페이지 이동 및 로드 대기
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  
  // URL이 올바른지 확인 (waitForRedirect가 false일 때는 더 유연하게 처리)
  if (waitForRedirect) {
    await page.waitForURL(`${BASE_URL}/login`, { timeout: 10000 }).catch(() => {
      // 이미 다른 페이지에 있을 수 있으므로 무시
    });
  } else {
    // 실패 시나리오의 경우 현재 URL이 로그인 페이지인지 확인만
    const currentUrl = page.url();
    if (!currentUrl.includes('/login')) {
      // 로그인 페이지가 아니면 다시 이동
      await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded', timeout: 30000 });
    }
  }
  
  // 페이지 로드 완료 대기
  await page.waitForLoadState('load', { timeout: 10000 }).catch(() => {
    // 타임아웃이어도 계속 진행
  });
  
  // 요소가 나타날 때까지 대기 (더 유연한 대기)
  const emailInput = page.getByTestId('login-email-input');
  await emailInput.waitFor({ state: 'visible', timeout: 20000 });
  
  await emailInput.fill(credentials.email);
  await page.getByTestId('login-password-input').fill(credentials.password);
  await page.getByTestId('login-submit-button').click();
  
  if (waitForRedirect) {
    await page.waitForURL(`${BASE_URL}/chat`, { timeout: 10000 });
  } else {
    // 실패 시나리오의 경우 로그인 페이지에 머물러 있어야 함
    await page.waitForTimeout(1000); // API 응답 대기
  }
}

/**
 * 회원가입 액션
 * @param {import('@playwright/test').Page} page
 * @param {Object} userData - { email: string, password: string, passwordConfirm: string, name: string }
 */
async function registerAction(page, userData) {
  try {
    // 페이지 이동 및 로드 대기
    const response = await page.goto(`${BASE_URL}/register`, { 
      waitUntil: 'domcontentloaded',
      timeout: 30000 
    });
    
    // 응답 상태 확인
    if (response && response.status() >= 400) {
      throw new Error(`Page load failed with status ${response.status()}`);
    }
    
    // URL이 올바른지 확인 (리다이렉트 허용)
    const currentUrl = page.url();
    if (!currentUrl.includes('/register') && !currentUrl.includes('/login')) {
      console.warn(`Unexpected URL after navigation: ${currentUrl}`);
    }
    
    // 페이지 로드 완료 대기
    await page.waitForLoadState('load', { timeout: 10000 }).catch(() => {
      console.warn('Page load state timeout, continuing...');
    });
    
    // 요소가 나타날 때까지 대기 (더 유연한 대기)
    const emailInput = page.getByTestId('register-email-input');
    await emailInput.waitFor({ state: 'visible', timeout: 20000 });
    
    await emailInput.fill(userData.email);
    await page.getByTestId('register-password-input').fill(userData.password);
    await page.getByTestId('register-password-confirm-input').fill(userData.passwordConfirm);
    await page.getByTestId('register-name-input').fill(userData.name);
    await page.getByTestId('register-submit-button').click();
  } catch (error) {
    // 디버깅 정보 출력
    const currentUrl = page.url();
    const pageContent = await page.content().catch(() => 'Unable to get page content');
    console.error(`Register action failed at URL: ${currentUrl}`);
    console.error(`Error: ${error.message}`);
    throw error;
  }
}

/**
 * 로그아웃 액션
 * @param {import('@playwright/test').Page} page
 */
async function logoutAction(page) {
  await page.getByTestId('logout-link').click();
}

module.exports = {
  loginAction,
  registerAction,
  logoutAction,
};