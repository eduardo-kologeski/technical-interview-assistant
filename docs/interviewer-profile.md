# Perfil e disponibilidade de entrevistadores

O módulo `profile` centraliza os dados profissionais usados para apoiar a escolha humana de entrevistadores técnicos.

## Modelo

- `UserTechnicalSkill`: conhecimento declarado pelo usuário, normalizado para impedir duplicidade, com nível `BEGINNER`, `INTERMEDIATE` ou `ADVANCED`.
- `InterviewAvailability`: intervalo associado a uma data real, com início e fim. Um usuário pode cadastrar múltiplos períodos na mesma data, desde que não se sobreponham.

A tela carrega somente a semana visível (segunda a domingo) e permite navegar para qualquer semana ou ir diretamente a uma data. Datas passadas ficam disponíveis apenas para consulta e novos horários são validados também pelo backend.

## Migração do modelo recorrente

A migração `V6__migrate_availability_to_dates.sql` converte cada disponibilidade semanal legada para a próxima ocorrência da mesma semana civil a partir da data da migração. A coluna legada `day_of_week` é preservada, agora opcional, para manter rastreabilidade; novas gravações usam exclusivamente `availability_date`. Não há recorrência automática depois da migração.

As gravações em `/api/profile/**` derivam o usuário do `SecurityContext`; o frontend não envia `userId`. A consulta autenticada `/api/interviewers` retorna somente nome, conhecimentos, níveis e disponibilidade.

## Compatibilidade com vagas

A busca normaliza nomes sem diferenciar maiúsculas, minúsculas ou acentos. O resultado mostra `X de Y requisitos relacionados` e ordena primeiro pela quantidade de correspondências, depois pelos níveis declarados e, por fim, pelo nome. O indicador é informativo e não seleciona automaticamente um entrevistador.

Filtros disponíveis: vaga, tecnologia, data da entrevista e horário. Sem uma data selecionada, a consulta apresenta uma prévia limitada às próximas disponibilidades. Integrações com calendários e convites permanecem fora desta versão.
