# Suporte a navegadores

## Matriz

- Chrome e Edge: duas versões estáveis mais recentes, suporte completo.
- Firefox: duas versões estáveis mais recentes, suporte funcional.
- Safari desktop e iOS: versão estável atual e anterior, suporte funcional.
- Navegadores sem suporte a ES2022, cookies seguros ou APIs Web modernas: não suportados.

O CI executa o fluxo crítico em Chromium. Antes do primeiro go-live e em alterações relevantes de UI, executar smoke manual em Edge, Firefox e Safari/iOS real ou serviço equivalente. Registrar versão, sistema operacional e resultado.

## Critérios

Login, restauração de sessão, criação e edição de vaga, entrevista, autosave/retry, revisão e download do PDF precisam funcionar sem perda de dados. Problemas puramente visuais de baixo impacto podem receber prazo; falhas de entrada, foco, navegação por teclado ou persistência bloqueiam a release.
