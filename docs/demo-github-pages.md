# Demonstração com endereço fixo

O endereço fixo da demonstração é:

<https://eduardo-kologeski.github.io/tech-interview-assistant-demo/>

Ele é uma página estática independente que redireciona para o Cloudflare Quick Tunnel criado na máquina local. O repositório privado do TIA permanece privado e nenhum código-fonte ou segredo é copiado para o GitHub Pages.

## Primeira configuração

1. No GitHub, crie um repositório **público** vazio chamado `tech-interview-assistant-demo` em `eduardo-kologeski`. Não adicione README, `.gitignore` nem licença pela interface, pois o repositório local já está preparado.
2. No diretório que contém o projeto TIA, publique o repositório local preparado:

   ```powershell
   Set-Location ..\tech-interview-assistant-demo
   git remote add origin https://github.com/eduardo-kologeski/tech-interview-assistant-demo.git
   git push -u origin main
   ```

3. Em `tech-interview-assistant-demo`, abra **Settings → Pages**. Em **Build and deployment**, selecione **Deploy from a branch**, branch **main**, pasta **/ (root)** e clique em **Save**.
4. Confirme que Docker Desktop, `cloudflared` e Git estão instalados e disponíveis no `PATH`.
5. Confirme a autenticação Git com `git ls-remote origin`. Se solicitado, autentique pelo Git Credential Manager; não use nem grave PAT no projeto.

Se o clone ficar em outro local, informe-o ao script com `-DemoRepositoryPath 'C:\caminho\tech-interview-assistant-demo'`.

## Inicialização diária

No projeto privado do TIA, execute:

```powershell
.\scripts\start-demo.ps1
```

O fluxo é:

```text
start-demo.ps1
  → Docker
  → TIA em localhost:4200
  → Cloudflare Quick Tunnel
  → captura da URL
  → atualização de tech-interview-assistant-demo/index.html
  → git commit e push, somente se a URL mudou
  → GitHub Pages
  → URL fixa para usuários
```

O script inicia `docker compose up -d`, aguarda o frontend, cria um único Quick Tunnel, lê a URL dos dois fluxos de saída do `cloudflared`, atualiza o meta refresh e o link fallback, publica a mudança e mantém o túnel ativo.

Se houver um `cloudflared` manual já apontando para `localhost:4200`, encerre-o antes. O script recusa abrir um túnel duplicado.

## Parar e reiniciar

Pressione `Ctrl+C` no terminal do script para parar o `cloudflared`. Os containers Docker permanecem ativos. Para reiniciar a demonstração e publicar a nova URL temporária, execute novamente `.\scripts\start-demo.ps1`.

## Limitações

- O endereço do GitHub Pages é fixo, mas cada Quick Tunnel é temporário.
- A demonstração só funciona enquanto a máquina, os containers, a conexão e o processo `cloudflared` estiverem ativos.
- Após cada reinício do túnel, o GitHub Pages pode levar alguns instantes para publicar o novo commit; durante esse intervalo, o redirecionamento anterior pode falhar.
- GitHub Pages realiza apenas o redirecionamento. Ele não hospeda frontend, backend, banco ou segredos.
