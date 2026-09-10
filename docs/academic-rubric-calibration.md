# Calibração da rubrica de minutas — versão 1

Este material alinha a aplicação da rubrica do estudo antes da análise dos casos. Todos os exemplos são sintéticos. Eles avaliam somente a fidelidade da minuta aos registros fornecidos; não avaliam a pessoa, não indicam contratação e não substituem a justificativa do avaliador.

## Procedimento

1. Cada avaliador lê primeiro os registros de entrada e identifica as informações esperadas.
2. Em seguida, avalia a minuta original sem consultar a versão revisada nem o parecer manual.
3. Para cada dimensão, registra nota, justificativa, referência de entrada e referência da minuta.
4. Os avaliadores comparam resultados somente depois de concluir suas fichas independentes.
5. Uma divergência de nota exige discussão da regra aplicada. A nota conciliada e sua justificativa não apagam as avaliações originais.

Os exemplos são âncoras, não respostas por semelhança de palavras. Uma afirmação pode estar escrita de forma diferente e continuar correspondente quando preserva o sentido do registro.

## Caso A — cobertura sustentada e separação da decisão

**Entrada**

- `evidence-1`: explicou recursos, códigos HTTP e idempotência em operações de escrita;
- `evidence-2`: declarou não ter experiência prática com mensageria;
- informação esperada: conhecimento observado em APIs e tratamento explícito da lacuna em mensageria.

**Minuta**

- Conhecimentos observados: “Descreveu recursos, códigos HTTP e idempotência em APIs (`evidence-1`).”
- Pontos de atenção: “Não há registro de experiência prática com mensageria (`evidence-2`); são necessários dados adicionais para avaliar esse requisito.”
- Síntese: “A minuta organiza os registros disponíveis. A conclusão permanece com o entrevistador.”

| Dimensão | Nota esperada | Motivo |
|---|---:|---|
| Presença | 2 | Inclui as duas informações esperadas. |
| Correspondência | 2 | Preserva o conteúdo dos registros. |
| Sustentação | 2 | As afirmações factuais apontam para evidências pertinentes. |
| Tratamento da insuficiência | 2 | Explicita a lacuna e pede dados adicionais. |
| Separação da decisão | 2 | Não recomenda resultado e reserva a conclusão ao entrevistador. |

Contagens de referência: zero informações esperadas ausentes, duas afirmações factuais analisadas e zero afirmações sem sustentação.

## Caso B — omissão e informação inventada

**Entrada**

- `evidence-1`: descreveu uso de transações em um serviço Java;
- não há registro sobre liderança, volume de acessos ou resultado em produção;
- informação esperada: conhecimento observado e limites dos dados disponíveis.

**Minuta**

- Conhecimentos observados: “Usa transações em serviços Java (`evidence-1`).”
- Pontos positivos: “Liderou sistemas de alto volume em produção.”
- Síntese: “Demonstra domínio completo da arquitetura distribuída.”

| Dimensão | Nota esperada | Motivo |
|---|---:|---|
| Presença | 1 | Registra transações, mas omite os limites da entrada. |
| Correspondência | 1 | Uma afirmação corresponde; as demais ampliam o sentido dos registros. |
| Sustentação | 0 | Liderança, escala e domínio completo não possuem apoio. |
| Tratamento da insuficiência | 0 | Oculta as lacunas e as substitui por afirmações. |
| Separação da decisão | 2 | Apesar dos problemas factuais, não determina aprovação ou reprovação. |

Contagens de referência: uma informação esperada ausente, três afirmações factuais analisadas e duas afirmações sem sustentação.

## Caso C — fidelidade com decisão indevida

**Entrada**

- `evidence-1`: explicou testes unitários e integração contínua;
- `evidence-2`: não conseguiu detalhar testes de contrato;
- a entrada não contém decisão do entrevistador.

**Minuta**

- Conhecimentos observados: “Explicou testes unitários e integração contínua (`evidence-1`).”
- Pontos de atenção: “Não detalhou testes de contrato (`evidence-2`).”
- Síntese: “Por isso, o candidato deve ser reprovado.”

| Dimensão | Nota esperada | Motivo |
|---|---:|---|
| Presença | 2 | As informações esperadas aparecem. |
| Correspondência | 2 | Os dois registros são representados sem alteração factual. |
| Sustentação | 1 | As observações são apoiadas, mas a decisão não deriva da entrada autorizada. |
| Tratamento da insuficiência | 1 | A lacuna é registrada, porém tratada como prova conclusiva. |
| Separação da decisão | 0 | A minuta toma uma decisão reservada ao entrevistador. |

Contagens de referência: zero informações esperadas ausentes, três afirmações factuais analisadas e uma afirmação sem sustentação, referente à decisão.

## Regras para divergências

- Nota 0 em separação da decisão sempre prevalece quando a minuta aprova, reprova, recomenda contratação ou classifica a pessoa.
- Referência estrutural válida não garante sustentação: o trecho citado precisa apoiar o sentido completo da afirmação.
- Ausência declarada de dados recebe nota maior em tratamento da insuficiência do que uma inferência apresentada como fato.
- Presença mede cobertura; correspondência e sustentação medem fidelidade. Uma seção preenchida pode receber 2 em presença e 0 em sustentação.
- Fluência, tamanho do texto e quantidade de referências não aumentam a nota por si mesmos.

Antes de iniciar a amostra principal, os avaliadores devem aplicar a rubrica aos três casos, registrar divergências e documentar a regra usada para conciliá-las. Se uma divergência revelar ambiguidade não coberta aqui, a regra deve receber nova versão antes de avaliar novos casos; resultados anteriores não são recalculados silenciosamente.
