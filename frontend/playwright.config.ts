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
    baseURL: 'http://127.0.0.1:4300',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: backendCommand,
      cwd: __dirname,
      url: 'http://127.0.0.1:8181/actuator/health',
      reuseExistingServer: false,
      timeout: 120_000,
      env: {
        DATABASE_URL: process.env['E2E_DATABASE_URL'] || 'jdbc:postgresql://127.0.0.1:5432/interview_assistant_e2e',
        DATABASE_USER: process.env['E2E_DATABASE_USER'] || 'postgres',
        DATABASE_PASSWORD: process.env['E2E_DATABASE_PASSWORD'] || 'postgres',
        SERVER_PORT: '8181',
        SPRING_DATASOURCE_DRIVER_CLASS_NAME: 'org.postgresql.Driver',
        SPRING_JPA_HIBERNATE_DDL_AUTO: 'validate',
        SPRING_FLYWAY_ENABLED: 'true',
        SPRING_SQL_INIT_MODE: 'never',
        JWT_SECRET: 'E2eOnlySecretThatIsLongEnoughForHmacSha256AndNeverUsedOutsideAutomatedTests',
        CORS_ALLOWED_ORIGINS: 'http://127.0.0.1:4300',
        AUTH_LOGIN_RATE_LIMIT: '1000',
        AUTH_REGISTER_RATE_LIMIT: '1000',
        AUTH_REFRESH_RATE_LIMIT: '1000',
        INVITATION_EXPOSE_DEVELOPMENT_LINK: 'true',
        IDENTITY_EXPOSE_DEVELOPMENT_LINK: 'true',
      },
    },
    {
      command: 'npm start -- --host 127.0.0.1 --port 4300 --proxy-config proxy.e2e.conf.json',
      cwd: __dirname,
      url: 'http://127.0.0.1:4300/auth/login',
      reuseExistingServer: false,
      timeout: 120_000,
    },
  ],
});
