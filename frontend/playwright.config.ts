import { defineConfig, devices } from '@playwright/test';

const backendCommand =
  process.platform === 'win32'
    ? 'cd ..\\backend && mvnw.cmd -q spring-boot:run'
    : 'cd ../backend && ./mvnw -q spring-boot:run';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: process.env['CI'] ? 1 : 0,
  reporter: process.env['CI'] ? [['html', { open: 'never' }], ['github']] : 'list',
  use: {
    baseURL: 'http://127.0.0.1:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: backendCommand,
      cwd: __dirname,
      url: 'http://127.0.0.1:8081/actuator/health',
      reuseExistingServer: !process.env['CI'],
      timeout: 120_000,
      env: {
        DATABASE_URL: 'jdbc:h2:mem:e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE',
        DATABASE_USER: 'sa',
        DATABASE_PASSWORD: '',
        SPRING_DATASOURCE_DRIVER_CLASS_NAME: 'org.h2.Driver',
        SPRING_JPA_HIBERNATE_DDL_AUTO: 'create-drop',
        SPRING_FLYWAY_ENABLED: 'false',
        JWT_SECRET: 'E2eOnlySecretThatIsLongEnoughForHmacSha256AndNeverUsedOutsideAutomatedTests',
        CORS_ALLOWED_ORIGINS: 'http://127.0.0.1:4200',
        AUTH_LOGIN_RATE_LIMIT: '1000',
        AUTH_REGISTER_RATE_LIMIT: '1000',
        AUTH_REFRESH_RATE_LIMIT: '1000',
      },
    },
    {
      command: 'npm start -- --host 127.0.0.1 --port 4200',
      cwd: __dirname,
      url: 'http://127.0.0.1:4200/auth/login',
      reuseExistingServer: !process.env['CI'],
      timeout: 120_000,
    },
  ],
});
