# Preparação de identidade corporativa

## Estado e limite desta entrega

Esta entrega prepara contratos e gates para SSO OIDC/SAML e provisionamento SCIM, mas **não ativa autenticação corporativa**. Não há SDK de fornecedor, callback público, endpoint SCIM, segredo, token nem migração de banco. A configuração padrão é fechada e o diagnóstico administrativo informa os requisitos pendentes.

A ativação de produção permanece bloqueada por decisão externa até que exista um IdP de teste aprovado, um processo verificável de propriedade de domínio e uma política de recuperação/break-glass aprovada.

## Contratos internos

- `EnterpriseIdentityProviderPort`: fronteira para um futuro adaptador OIDC ou SAML. Um adaptador somente pode entregar uma `VerifiedProviderAssertion` depois de validar assinatura, issuer, audience, expiração e replay no protocolo.
- `EnterpriseClaimNormalizer`: normaliza subject, e-mail verificado, domínio e grupos. Qualquer ausência ou divergência falha de forma fechada.
- `ScimProvisioningPort`: comandos idempotentes e independentes de fornecedor para criar, atualizar ou desativar identidades. O contrato não publica um endpoint.
- `EnterpriseIdentityReadinessService`: reúne todos os gates. Um fluxo futuro deve chamar `requireReady()` antes de iniciar SSO ou provisioning.
- `EnterpriseIdentityAudit`: registra apenas ação, referências estritamente sanitizadas e fingerprint do subject. Assertions, tokens, segredos e e-mails nunca entram no evento.

Não há mapeamento automático de grupo para administrador. A política futura deve partir do menor privilégio e exigir uma regra explícita e revisada para qualquer elevação.

## Gates obrigatórios

| Gate | Evidência exigida | Comportamento quando ausente |
| --- | --- | --- |
| Funcionalidade habilitada | mudança operacional aprovada | `FEATURE_DISABLED` |
| Fornecedor aprovado | contrato técnico e de segurança | `PROVIDER_DECISION_PENDING` |
| Protocolo escolhido | OIDC ou SAML após homologação | `PROTOCOL_NOT_SELECTED` |
| Domínio verificado | prova externa renovável de propriedade | `DOMAIN_NOT_VERIFIED` |
| Contrato de claims | issuer e audience exatos, homologados | `CLAIM_CONTRACT_INCOMPLETE` |
| Recuperação aprovada | runbook testado e responsáveis definidos | `RECOVERY_POLICY_PENDING` |
| Adaptador homologado | implementação configurada para o provider/protocolo exatos | `ADAPTER_UNAVAILABLE` |

O endpoint autenticado `GET /api/enterprise-identity/readiness` exige papel `COMPANY_ADMIN`; ele retorna somente estado, protocolo e bloqueadores sem divulgar issuer, audience, domínio ou configuração sensível.

## Contrato mínimo de claims

- `issuer`: correspondência exata com o emissor aprovado;
- `audience`: deve conter a audiência desta aplicação;
- `subject`: identificador estável e não reciclável no tenant do IdP;
- `email`: endereço normalizado apenas depois de `email_verified=true`;
- domínio do e-mail: correspondência exata com domínio verificado, sem aceitar subdomínios implicitamente;
- `groups`: coleção opcional; valores desconhecidos não concedem acesso.

O vínculo de conta não pode usar apenas e-mail. A chave externa deverá incluir provider/issuer + subject + tenant. Mudanças de e-mail não podem transferir uma conta silenciosamente.

## Threat model e controles requeridos

### Sequestro e vinculação indevida de conta

Risco: atacante controla um e-mail semelhante ou o IdP recicla identificadores. Controles: subject estável, issuer/audience exatos, domínio comprovado, confirmação de e-mail, vínculo explícito e trilha de auditoria. Nunca fazer merge automático baseado apenas em e-mail.

### OIDC

O adaptador deve validar assinatura e algoritmo permitido, issuer, audience/authorized party, expiração/not-before, nonce e state. Authorization Code deve usar PKCE; redirect URIs precisam ser exatas. IDs de transação devem impedir replay.

### SAML

O adaptador deve exigir assinatura da Response ou Assertion conforme política aprovada e validar audience, recipient/destination, InResponseTo, condições temporais e identificador de replay. Não aceitar algoritmo fraco, resposta unsolicited sem decisão explícita ou certificado fora da cadeia/rotação aprovada.

### SCIM

Credenciais SCIM devem ser armazenadas como hash ou em secret manager, ter escopo mínimo, rotação e revogação. Operações usam `externalId` idempotente. Desprovisionamento desativa sessões e acesso, sem apagar histórico de entrevista. Proteger o último administrador e o acesso de emergência contra remoção automática.

### Lockout e break-glass

Manter ao menos um acesso de emergência fora do domínio federado, com MFA resistente a phishing, segredo protegido offline, aprovação por duas pessoas e uso auditado. Testar recuperação periodicamente. A aplicação não deve impor domínio nem desabilitar senha local antes desse exercício ser aprovado.

### Logs e privacidade

Não registrar authorization codes, access/refresh/ID tokens, assertions SAML, segredos SCIM, e-mails ou mapas de claims. Eventos operacionais usam IDs internos/fingerprints e correlation ID. A retenção deve seguir a política de auditoria do produto.

## Decisões externas pendentes

1. selecionar e homologar um IdP real e tenant de teste;
2. decidir OIDC, SAML ou ambos, incluindo rotação de chaves/certificados e metadados;
3. definir prova e renovação de propriedade de domínio;
4. decidir JIT versus SCIM como fonte de verdade e política de desprovisionamento;
5. aprovar mapeamento de grupos/papéis com menor privilégio;
6. aprovar e exercitar recuperação, break-glass e resposta a incidente;
7. só então implementar o adaptador, testes de integração e E2E contra o IdP homologado.

Por isso, a issue de ativação enterprise deve permanecer aberta com status de decisão externa. A conclusão desta preparação não representa disponibilidade de SSO/SCIM em produção.
