import { expect, test } from '@playwright/test';
import { stat } from 'node:fs/promises';

test('persists the authenticated interview flow, recovers autosave and exports the PDF', async ({
  page,
}) => {
  test.setTimeout(90_000);
  const unique = Date.now();
  const email = `e2e-${unique}@example.test`;
  const vacancy = `Vaga E2E ${unique}`;

  await page.goto('/auth/register');
  await page.getByLabel('Nome').fill('Pessoa Entrevistadora E2E');
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Senha', { exact: true }).fill('E2ePassword123!');
  await page.getByLabel('Confirmar senha').fill('E2ePassword123!');
  await page.getByLabel('Permanecer logado').check();
  await page.getByRole('button', { name: 'Criar conta' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);

  await page.reload();
  await expect(page.getByRole('heading', { name: /Olá,/ })).toBeVisible();
  await expect(page.locator('header nav a')).toHaveText(['Início', 'Entrevistas', 'Vagas', 'Empresa']);

  await page.getByRole('link', { name: 'Empresa', exact: true }).click();
  await expect(page.getByRole('navigation', { name: 'Visão da empresa' })).toContainText('Candidatos');
  await expect(page.getByRole('navigation', { name: 'Visão da empresa' })).toContainText('Equipe');

  await page.getByRole('link', { name: 'Vagas' }).first().click();
  await page.getByRole('button', { name: '+ Nova vaga' }).click();
  await page.getByLabel('Nome da vaga *').fill(vacancy);
  await page.getByLabel('Senioridade *').selectOption('Pleno');
  await page.getByPlaceholder('Pesquisar ou adicionar requisito...').fill('Java');
  await page.getByRole('button', { name: 'Adicionar' }).click();
  await page.getByRole('button', { name: 'Salvar vaga' }).click();
  await expect(page.getByRole('heading', { name: vacancy })).toBeVisible();

  await page.getByRole('link', { name: 'Entrevistas' }).click();
  await expect(page.getByRole('navigation', { name: 'Áreas de entrevistas' })).toContainText('Agenda');
  await expect(page.getByRole('navigation', { name: 'Áreas de entrevistas' })).toContainText('Minha disponibilidade');
  await page.getByRole('link', { name: '+ Nova entrevista' }).click();
  await page.getByLabel('Nome do candidato *').fill('Pessoa Candidata E2E');
  await page.getByLabel('Vaga *').selectOption({ label: vacancy });
  await page.getByRole('button', { name: /Iniciar entrevista/ }).click();
  await expect(page).toHaveURL(/\/interviews\/[0-9a-f-]+$/);

  await page.getByRole('radio', { name: 'Java: 5 de 5' }).click();
  const technologySearch = page.getByLabel('Pesquisar ou adicionar experiência complementar');
  await technologySearch.fill('PHP');
  await page.getByRole('button', { name: 'PHP', exact: true }).click();
  await page.getByRole('radio', { name: 'PHP: 4 de 5' }).click();
  await page.getByLabel('Anotações para feedback').fill('Comunicação clara e objetiva.');

  let failNextAutosave = true;
  await page.route('**/api/interviews/*/workspace', async (route) => {
    if (failNextAutosave) {
      failNextAutosave = false;
      await route.fulfill({ status: 503, contentType: 'application/problem+json', body: '{}' });
      return;
    }
    await route.continue();
  });
  await page.getByLabel('Anotações da entrevista').fill('Anotação E2E persistida após retry.');
  await expect(page.getByText('Falha ao salvar', { exact: false })).toBeVisible({
    timeout: 10_000,
  });
  await page.getByRole('button', { name: 'Tentar novamente' }).click();
  await expect(page.getByText('● Salvo')).toBeVisible({ timeout: 10_000 });

  await page.reload();
  await expect(page.getByLabel('Anotações da entrevista')).toHaveValue(
    'Anotação E2E persistida após retry.',
  );
  await expect(page.getByRole('radio', { name: 'Java: 5 de 5' })).toHaveAttribute(
    'aria-checked',
    'true',
  );
  await expect(page.getByRole('radio', { name: 'PHP: 4 de 5' })).toHaveAttribute(
    'aria-checked',
    'true',
  );
  await expect(page.getByLabel('Anotações para feedback')).toHaveValue(
    'Comunicação clara e objetiva.',
  );

  await page.getByRole('button', { name: 'Finalizar entrevista' }).click();
  await expect(page.getByText('REVISÃO DO PARECER')).toBeVisible({ timeout: 10_000 });
  await page.getByLabel('Pontos fortes').fill('Boa experiência com Java.');
  await page.getByLabel('Pontos de atenção').fill('Pouca experiência com Kubernetes.');
  await expect(page.getByText('● Salvo')).toBeVisible({ timeout: 10_000 });

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Exportar parecer em PDF' }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe('parecer-pessoa-candidata-e2e.pdf');
  const path = await download.path();
  expect(path).not.toBeNull();
  expect((await stat(path!)).size).toBeGreaterThan(5_000);
});

test('keeps the invitation through registration and enters as a read-only interviewer', async ({ page }) => {
  test.setTimeout(90_000);
  const unique = Date.now();
  const adminEmail = `admin-invite-${unique}@example.test`;
  const interviewerEmail = `interviewer-${unique}@example.test`;
  const sharedVacancy = `Vaga compartilhada ${unique}`;
  const sharedCandidate = `Candidato compartilhado ${unique}`;

  await page.goto('/auth/register');
  await page.getByLabel('Nome').fill('Gestor do convite');
  await page.getByLabel('E-mail').fill(adminEmail);
  await page.getByLabel('Senha', { exact: true }).fill('E2ePassword123!');
  await page.getByLabel('Confirmar senha').fill('E2ePassword123!');
  await page.getByRole('button', { name: 'Criar conta' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);

  await page.getByRole('link', { name: 'Vagas', exact: true }).click();
  await page.getByRole('button', { name: '+ Nova vaga' }).click();
  await page.getByLabel('Nome da vaga *').fill(sharedVacancy);
  await page.getByPlaceholder('Pesquisar ou adicionar requisito...').fill('Java');
  await page.getByRole('button', { name: 'Adicionar' }).click();
  await page.getByRole('button', { name: 'Salvar vaga' }).click();
  await page.getByRole('link', { name: 'Entrevistas', exact: true }).click();
  await page.getByRole('link', { name: '+ Nova entrevista' }).click();
  await page.getByLabel('Nome do candidato *').fill(sharedCandidate);
  await page.getByLabel('Vaga *').selectOption({ label: sharedVacancy });
  await page.getByRole('button', { name: /Iniciar entrevista/ }).click();
  await expect(page.getByRole('heading', { name: sharedCandidate })).toBeVisible();

  await page.getByRole('link', { name: 'Empresa', exact: true }).click();
  await page.getByRole('button', { name: 'Equipe', exact: true }).click();
  await page.getByLabel('E-mail corporativo').fill(interviewerEmail);
  await page.getByRole('button', { name: 'Enviar convite' }).click();
  const inviteUrl = await page.locator('input[readonly]').inputValue();
  expect(inviteUrl).toContain('/invites/');

  await page.getByRole('button', { name: 'Sair' }).click();
  await page.goto(inviteUrl);
  await page.getByRole('link', { name: 'Criar conta' }).click();
  await expect(page.getByLabel('E-mail')).toHaveValue(interviewerEmail);
  await page.getByLabel('Nome').fill('Novo entrevistador');
  await page.getByLabel('Senha', { exact: true }).fill('E2ePassword123!');
  await page.getByLabel('Confirmar senha').fill('E2ePassword123!');
  await page.getByRole('button', { name: 'Criar conta' }).click();

  await expect(page).toHaveURL(/\/dashboard$/, { timeout: 15_000 });
  await page.evaluate(() => localStorage.removeItem('tia.companyId'));
  await page.reload();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.locator('header nav a')).toHaveText(['Início', 'Entrevistas', 'Vagas']);
  await page.getByRole('link', { name: 'Vagas', exact: true }).click();
  await expect(page.getByText('Visualização somente leitura')).toBeVisible();
  await expect(page.getByRole('heading', { name: sharedVacancy })).toBeVisible();
  await expect(page.getByRole('button', { name: '+ Nova vaga' })).toHaveCount(0);
  await page.getByRole('link', { name: 'Entrevistas', exact: true }).click();
  await expect(page.getByRole('heading', { name: sharedCandidate })).toBeVisible();
});

test('uses a responsive work-week grid for interviewer availability', async ({ page }) => {
  test.setTimeout(90_000);
  const unique = Date.now();
  await page.goto('/auth/register');
  await page.getByLabel('Nome').fill('Entrevistador Calendário');
  await page.getByLabel('E-mail').fill(`calendar-${unique}@example.test`);
  await page.getByLabel('Senha', { exact: true }).fill('E2ePassword123!');
  await page.getByLabel('Confirmar senha').fill('E2ePassword123!');
  await page.getByRole('button', { name: 'Criar conta' }).click();
  await page.getByRole('link', { name: 'Entrevistas', exact: true }).click();
  await page.getByRole('button', { name: 'Minha disponibilidade' }).click();

  await expect(page.locator('.schedule-scroll')).toBeVisible();
  await expect(page.locator('.day-header:visible')).toHaveCount(5);
  await page.locator('.date-trigger').click();
  await expect(page.getByRole('region', { name: 'Escolher uma data' })).toBeVisible();
  await page.getByRole('button', { name: 'Minimizar' }).click();
  await page.getByRole('button', { name: 'Próxima semana', exact: true }).click();

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator('.mobile-day-strip')).toBeVisible();
  await expect(page.locator('.day-header:visible')).toHaveCount(1);
  await page.getByRole('button', { name: /Selecionar .* às 09:30/ }).click();
  await expect(page.getByText('HORÁRIOS SELECIONADOS')).toBeVisible();
  await page.getByRole('button', { name: /Selecionar .* às 10:15/ }).click();
  await expect(page.locator('.selection-summary')).toContainText('1 de 2 intervalos pendentes');
  await page.getByRole('button', { name: /Selecionar .* às 16:30/ }).click();
  await page.getByRole('button', { name: /Selecionar .* às 18:00/ }).click();
  await expect(page.locator('.selection-summary')).toContainText('2 de 2 intervalos pendentes');
  await page.getByRole('button', { name: 'Salvar todos' }).click();
  await expect(page.getByText('Disponível', { exact: true })).toHaveCount(2);
});
