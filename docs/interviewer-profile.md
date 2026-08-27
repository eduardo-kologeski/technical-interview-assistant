# Perfil e disponibilidade de entrevistadores

O módulo `profile` centraliza os dados profissionais usados para apoiar a escolha humana de entrevistadores técnicos.

## Modelo

- `UserTechnicalSkill`: conhecimento declarado pelo usuário, normalizado para impedir duplicidade, com nível `BEGINNER`, `INTERMEDIATE` ou `ADVANCED`.
- `InterviewAvailability`: intervalo semanal recorrente com dia, início e fim. Um usuário pode cadastrar múltiplos períodos no mesmo dia, desde que não se sobreponham.

As gravações em `/api/profile/**` derivam o usuário do `SecurityContext`; o frontend não envia `userId`. A consulta autenticada `/api/interviewers` retorna somente nome, conhecimentos, níveis e disponibilidade.

## Compatibilidade com vagas

A busca normaliza nomes sem diferenciar maiúsculas, minúsculas ou acentos. O resultado mostra `X de Y requisitos relacionados` e ordena primeiro pela quantidade de correspondências, depois pelos níveis declarados e, por fim, pelo nome. O indicador é informativo e não seleciona automaticamente um entrevistador.

Filtros disponíveis: vaga, tecnologia, dia da semana e horário. Integrações com calendários, convites e exceções por data permanecem fora desta versão.
