# Objetivos acadêmicos e geração assistida de minutas

## Recorte da pesquisa

O Technical Interview Assistant investiga como informações estruturadas, registradas durante uma avaliação técnica, podem apoiar a elaboração de pareceres mais consistentes. A funcionalidade de IA tem um propósito delimitado: **gerar uma minuta de parecer de avaliação técnica a partir das informações registradas durante a avaliação**.

A saída do mecanismo é sempre uma minuta. O entrevistador revisa, altera e decide se o conteúdo pode compor o parecer final. A conclusão humana não deve ser fornecida ao modelo para que ele tome ou reproduza uma decisão.

O TIA não atribui à IA a responsabilidade de:

- aprovar ou reprovar uma pessoa;
- classificar candidatos automaticamente;
- prever desempenho profissional;
- substituir o julgamento do entrevistador;
- acrescentar experiências, competências, evidências ou conclusões não sustentadas pelos registros.

## Objetivos específicos

1. Definir as informações da avaliação técnica que serão utilizadas como base para a elaboração do parecer do entrevistador.
2. Desenvolver um mecanismo para gerar uma minuta de parecer a partir das informações registradas durante a avaliação técnica.
3. Comparar os pareceres elaborados manualmente com as minutas geradas de forma assistida, utilizando as mesmas informações de entrada.
4. Avaliar as minutas geradas quanto à presença das informações esperadas, à correspondência com os registros da avaliação e à ocorrência de informações não sustentadas pelos dados fornecidos.
5. Identificar as limitações e os ajustes necessários no processo de geração assistida de pareceres.

## Fluxo funcional de referência

```text
registrar contexto, critérios, observações e evidências
  -> encerrar a coleta
  -> montar um contexto estruturado somente com os registros da avaliação
  -> gerar uma minuta
  -> revisar e editar a minuta
  -> escrever a conclusão humana
  -> aprovar o parecer final
```

O contexto enviado ao mecanismo de geração deve ser minimizado e estruturado. Prompt, contexto e saída precisam permitir rastrear se cada afirmação da minuta corresponde às informações fornecidas. Conteúdo integral de avaliações e respostas do modelo não devem aparecer em logs.

## Estratégia de comparação e avaliação

Para o estudo, o parecer manual e a minuta assistida devem partir do mesmo conjunto congelado de informações de entrada. A comparação não usa o parecer manual como instrução para o modelo. Os artefatos devem ser identificados como manual, minuta gerada e versão final revisada, preservando autoria e momento de criação.

A análise das minutas considera pelo menos:

| Dimensão | Pergunta de avaliação |
|---|---|
| Presença | A minuta contém as informações esperadas para a estrutura do parecer? |
| Correspondência | As afirmações refletem corretamente critérios, observações e evidências registradas? |
| Sustentação | Há afirmações, inferências ou conclusões sem suporte nos dados fornecidos? |
| Revisão humana | Que alterações foram necessárias antes do parecer final? |
| Limitações | Quais lacunas de entrada, prompt, estrutura ou modelo prejudicaram o resultado? |

Os critérios e o protocolo de análise devem ser definidos antes da comparação dos resultados. Decisões de aprovação, reprovação, contratação ou promoção ficam fora da avaliação automática.

## Rastreabilidade no backlog

O backlog do GitHub separa os cinco objetivos em unidades verificáveis e sequenciais:

1. [#136 — definir o conjunto de informações e o contrato de entrada](https://github.com/eduardo-kologeski/technical-interview-assistant/issues/136);
2. [#137 — gerar e persistir a minuta com revisão humana obrigatória](https://github.com/eduardo-kologeski/technical-interview-assistant/issues/137);
3. [#138 — preservar pares comparáveis com a mesma entrada](https://github.com/eduardo-kologeski/technical-interview-assistant/issues/138);
4. [#139 — aplicar o protocolo de avaliação de presença, correspondência e sustentação](https://github.com/eduardo-kologeski/technical-interview-assistant/issues/139);
5. [#140 — consolidar limitações e ajustes necessários](https://github.com/eduardo-kologeski/technical-interview-assistant/issues/140).

As issues de implementação devem manter essa ordem de dependência. Integrações técnicas com modelos pertencem ao objetivo 2 e não alteram as restrições de decisão humana descritas neste documento.
