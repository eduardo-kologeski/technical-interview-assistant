# Governança técnica de dados

Este documento descreve o comportamento técnico atual e os controles necessários para operar a aplicação. Ele não define base legal, prazo jurídico ou política corporativa; essas decisões precisam de aprovação das áreas responsáveis por privacidade e segurança.

## Dados armazenados

| Domínio | Dados | Local |
|---|---|---|
| Usuários | nome, e-mail normalizado, hash BCrypt, datas | PostgreSQL |
| Sessões | hash SHA-256 do refresh token, dispositivo, user-agent, IP, validade e revogação | PostgreSQL |
| Vagas | título, senioridade, descrição, requisitos, estado e auditoria | PostgreSQL |
| Entrevistas | candidato, empresa, snapshot da vaga, entrevistador, estado e auditoria | PostgreSQL |
| Avaliação | resultados, comentários, experiências complementares, habilidades, anotações, pontos fortes e de atenção | documento versionado da entrevista no PostgreSQL |
| Perfil | conhecimentos técnicos e disponibilidade do entrevistador | PostgreSQL |
| PDF | conteúdo gerado no navegador e baixado pelo usuário | dispositivo do usuário; não persistido pelo backend atualmente |

Não devem ser inseridos documentos pessoais, dados de saúde, origem racial, religião, orientação sexual ou outros dados sensíveis que não sejam necessários para a avaliação profissional.

## Acesso atual

Todas as APIs de negócio exigem autenticação. A baseline atual ainda não diferencia capacidades entre usuários autenticados; portanto, a implantação deve ser restrita a um grupo operacional confiável até existir autorização granular. Identificadores de autoria são derivados do `SecurityContext`, nunca aceitos do frontend.

## Logs

Logs podem conter identificadores técnicos, endpoint, status, duração e correlation ID. Não registrar senha, token, header `Authorization`, anotações, feedback, parecer integral ou o documento de avaliação. Erros devolvidos ao cliente não devem incluir SQL ou stack trace.

## Retenção

O prazo é uma decisão externa pendente. Antes do go-live, a organização deve aprovar períodos para entrevistas concluídas, candidatos não contratados, refresh tokens revogados, auditoria, logs e backups. A futura rotina de retenção deve ser configurável, auditável e operar por status/data, sem apagar registros fora do escopo aprovado.

## Desativação e exclusão

- Vagas utilizadas por entrevistas devem ser desativadas, não removidas fisicamente.
- Usuários devem ser desativados preservando autoria histórica; exclusão física não pode quebrar auditoria.
- Exclusão de candidato exige fluxo administrativo autenticado ainda não implementado, com seleção exata da entrevista, registro da solicitação e tratamento do PDF já baixado fora do sistema.
- Quando a política exigir anonimização em vez de exclusão, campos identificadores e texto livre precisam ser tratados em conjunto; alterar apenas o nome não é suficiente.

Até esse fluxo existir e ser testado, pedidos de exclusão devem ser executados por procedimento operacional aprovado e revisão de banco, nunca por SQL improvisado em produção.

## Backups

Backups herdam a sensibilidade do banco e precisam de criptografia, acesso mínimo, retenção e descarte controlado. Uma exclusão no banco primário não remove imediatamente cópias históricas: a política deve determinar expiração das cópias e impedir restauração operacional de dados expirados sem reaplicar exclusões registradas.

Os scripts em `scripts/` criam dump customizado e checksum, e recusam restore sobre banco não vazio. Credenciais são fornecidas somente por variáveis de ambiente. Um volume Docker não é backup.

## Decisões externas obrigatórias

- responsável pelo tratamento e aprovadores de acesso;
- base legal e aviso de privacidade;
- prazos de retenção por categoria;
- RPO/RTO e periodicidade do restore drill;
- processo para acesso, correção, portabilidade e exclusão;
- destino e proteção de PDFs baixados;
- resposta a incidente e comunicação.

Sem essas decisões e sem um teste de restore datado, a governança permanece `PARTIAL` e o uso com candidatos reais continua bloqueado.
