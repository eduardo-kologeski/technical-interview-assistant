import { expect, test } from '@playwright/test';
import { stat } from 'node:fs/promises';

test('persists the authenticated interview flow, recovers autosave and exports the PDF', async ({
  page,
}) => {
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
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

  await page.getByRole('link', { name: 'Vagas' }).first().click();
  await page.getByRole('button', { name: '+ Nova vaga' }).click();
  await page.getByLabel('Nome da vaga *').fill(vacancy);
  await page.getByLabel('Senioridade *').selectOption('Pleno');
  await page.getByPlaceholder('Pesquisar ou adicionar requisito...').fill('Java');
  await page.getByRole('button', { name: 'Adicionar' }).click();
  await page.getByRole('button', { name: 'Salvar vaga' }).click();
  await expect(page.getByRole('heading', { name: vacancy })).toBeVisible();

  await page.getByRole('link', { name: 'Entrevistas' }).click();
  await page.getByLabel('Nome do candidato *').fill('Pessoa Candidata E2E');
  await page.getByLabel('Vaga *').selectOption({ label: vacancy });
  await page.getByRole('button', { name: /Iniciar entrevista/ }).click();
  await expect(page).toHaveURL(/\/interviews\/[0-9a-f-]+$/);

  await page.getByRole('radio', { name: 'Java: 5 de 5' }).click();
  const technologySearch = page.getByLabel('Pesquisar ou adicionar experiência complementar');
  await technologySearch.fill('PHP');
  await page.getByRole('button', { name: 'PHP', exact: true }).click();
  await page.getByRole('radio', { name: 'PHP: 4 de 5' }).click();
  const behavioralSearch = page.getByLabel('Pesquisar ou adicionar habilidade comportamental');
  await behavioralSearch.fill('Comunicação');
  await page.getByRole('button', { name: 'Comunicação', exact: true }).click();

  let failNextAutosave = true;
  await page.route('**/api/interviews/*/assessment', async (route) => {
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
  await expect(page.getByRole('button', { name: 'Remover habilidade Comunicação' })).toBeVisible();

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
