# Planos e entitlements

O módulo `billing` controla planos, assinatura da empresa, consumo agregado e permissões comerciais. Nesta fase não há cobrança, cartão, fatura nem integração com um provedor de pagamento.

## Comportamento

- Toda empresa nova recebe explicitamente o plano `STARTER` em avaliação por 30 dias.
- Em `ON_PREMISE`, o módulo fica desabilitado pela capability `billingEnabled`: operações não dependem de assinatura e a API não expõe plano.
- As quotas são aplicadas por empresa na criação de vagas, entrevistas e convites.
- O consumo mensal de entrevistas é atualizado de forma atômica no PostgreSQL e não armazena dados pessoais.
- Ações pagas falham de forma segura quando a assinatura não existe, expirou ou não oferece o entitlement.
- Consultas, histórico e exportações continuam acessíveis após downgrade ou expiração, preservando a portabilidade dos dados.

## Evolução para cobrança real

Um futuro adaptador de provedor deve traduzir eventos externos para alterações em `saas_subscriptions`; o domínio não deve importar SDKs de Stripe, Mercado Pago ou outro fornecedor. Webhooks precisam ser idempotentes e confirmar o estado persistido antes de liberar entitlements. Preços, meios de pagamento, faturas e dados financeiros devem permanecer fora de `saas_usage`, que contém somente métricas agregadas por empresa e período.

Para criar um plano, inclua o registro em `saas_plans`, associe suas capacidades em `saas_entitlements` e altere a `plan_code` da assinatura. Novas ações comerciais devem chamar `EntitlementService` no serviço de aplicação responsável, mantendo endpoints de leitura livres de bloqueio comercial.
