import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8081';
const email = __ENV.LOAD_USER_EMAIL;
const password = __ENV.LOAD_USER_PASSWORD;
const interviewId = __ENV.LOAD_INTERVIEW_ID;

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 5),
      duration: __ENV.DURATION || '2m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    'http_req_duration{journey:history}': ['p(95)<750'],
    'http_req_duration{journey:pdf-registration}': ['p(95)<1000'],
  },
};

export function setup() {
  if (!email || !password || !interviewId) {
    throw new Error('Defina LOAD_USER_EMAIL, LOAD_USER_PASSWORD e LOAD_INTERVIEW_ID com dados sintéticos.');
  }

  const response = http.post(`${baseUrl}/api/auth/login`, JSON.stringify({ email, password, rememberMe: false }), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(response, { 'login succeeds': (result) => result.status === 200 });
  return { token: response.json('accessToken') };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}`, 'Content-Type': 'application/json' };

  check(http.get(`${baseUrl}/api/vacancies?page=0&size=20`, { headers }), {
    'vacancies succeed': (response) => response.status === 200,
  });

  check(http.get(`${baseUrl}/api/interviews?page=0&size=20`, {
    headers,
    tags: { journey: 'history' },
  }), {
    'interview history succeeds': (response) => response.status === 200,
  });

  const interview = http.get(`${baseUrl}/api/interviews/${interviewId}`, { headers });
  check(interview, { 'interview succeeds': (response) => response.status === 200 });

  const payload = interview.json();
  check(http.patch(`${baseUrl}/api/interviews/${interviewId}/assessment`, JSON.stringify({
    version: payload.version,
    assessmentData: payload.assessmentData,
  }), { headers }), {
    'autosave succeeds or conflicts safely': (response) => response.status === 200 || response.status === 409,
  });

  check(http.post(`${baseUrl}/api/interviews/${interviewId}/pdf-generation`, null, {
    headers,
    tags: { journey: 'pdf-registration' },
  }), {
    'PDF generation registration succeeds': (response) => response.status === 200,
  });

  sleep(1);
}
