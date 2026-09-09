# Protocolo de comparação de pareceres — versão 1

## Pergunta e unidade de análise

O estudo compara um parecer elaborado manualmente com uma minuta gerada de forma assistida a partir do mesmo conjunto congelado de informações de uma avaliação técnica. A unidade de análise é um caso composto por: snapshot de entrada, parecer manual, minuta original, versão revisada e avaliação humana dos artefatos.

O estudo mede apoio à redação, presença das informações esperadas, correspondência com os registros e ocorrência de afirmações não sustentadas. Ele não mede aptidão da pessoa avaliada, qualidade de contratação ou capacidade de prever desempenho profissional.

## Preparação de cada caso

1. Usar dados sintéticos ou dados cujo uso esteja formalmente autorizado.
2. Encerrar a coleta e congelar uma única versão do contexto estruturado.
3. Identificar o snapshot por versão e hash, sem inserir seu conteúdo em logs.
4. Produzir o parecer manual antes de revelar a minuta ao autor, evitando que a saída do modelo influencie o artefato de controle.
5. Gerar uma única minuta original com a versão registrada de prompt, contrato, Ollama e modelo local.
6. Permitir que o entrevistador revise a minuta e escreva sua conclusão separadamente.
7. Tratar qualquer regeneração como uma nova execução vinculada ao mesmo snapshot, com justificativa e metadados próprios; ela não substitui a execução anterior.

O parecer manual nunca integra o prompt, o contexto do modelo ou exemplos de geração do mesmo caso.

O backend serializa o início do modo acadêmico com a captura da geração. Uma reserva persistida e limitada pelo tempo configurado do Ollama impede que a chamada ao modelo comece ao mesmo tempo em que o parecer manual é criado; a reserva é removida no sucesso ou na falha controlada e uma reserva expirada pode ser recuperada.

## Artefatos obrigatórios

| Artefato | Regra de integridade |
|---|---|
| Snapshot de entrada | Imutável, versionado e comum aos dois caminhos |
| Parecer manual | Autoria e término registrados antes da revelação da minuta |
| Minuta original | Resposta imutável do Ollama, sempre identificada como minuta |
| Versão revisada | Alterações humanas preservadas sem sobrescrever a original |
| Conclusão humana | Campo separado que nunca é enviado ao modelo |
| Ficha de avaliação | Avaliador, rubrica, justificativas e referências aos trechos analisados |

## Rubrica

Cada dimensão recebe uma justificativa textual e referências ao snapshot e ao trecho avaliado.

| Dimensão | 0 | 1 | 2 |
|---|---|---|---|
| Presença | ausência de informação essencial | presença parcial | informações esperadas presentes |
| Correspondência | contradiz ou altera registros | relação ambígua ou incompleta | corresponde aos registros citados |
| Sustentação | contém afirmação factual sem apoio | apoio indireto ou insuficiente | afirmações factuais apoiadas por registros |
| Tratamento da insuficiência | inventa ou oculta lacunas | sinaliza parte das lacunas | explicita adequadamente as insuficiências |
| Separação da decisão | decide ou sugere resultado sobre a pessoa | linguagem ambígua | preserva integralmente a decisão humana |

Além dos escores, registrar contagens de informações esperadas ausentes, afirmações factuais analisadas, afirmações sem sustentação e seções alteradas na revisão. Quantidade de edições e fluência textual não representam qualidade por si só.

## Tempos e comparação

Registrar separadamente tempo ativo de redação manual, latência da geração local e tempo ativo de revisão. Pausas e espera pelo modelo não entram como tempo de edição. Comparações usam estatística descritiva por dimensão e por caso; amostras pequenas não sustentam generalização ou inferência causal.

Quando houver mais de um avaliador, selecionar antes da análise uma parcela dos casos para dupla avaliação independente. Divergências devem ser conciliadas com justificativa preservada. A concordância pode ser relatada por dimensão, junto do tamanho da amostra, sem transformar o resultado em ranking de candidatos ou entrevistadores.

## Controle de mudanças

Versões de contexto, prompt, schema, Ollama, modelo, parâmetros e protocolo acompanham cada execução. Mudança em qualquer desses itens cria um novo grupo de comparação. Resultados anteriores permanecem disponíveis e não são recalculados silenciosamente.

## Privacidade e retenção

Aplicam-se a minimização e o ciclo de exclusão descritos em `data-governance.md`. Conteúdo do caso, pareceres e justificativas não entram em logs ou telemetria. Resultados agregados não devem permitir reconstruir uma avaliação. Arquivos exportados e backups seguem suas políticas próprias de retenção.

## Limitações previstas

- dados sintéticos podem não representar a variedade de avaliações reais;
- o resultado depende do modelo local, hardware, parâmetros e versões usados;
- o mesmo autor produzir manual e revisar a minuta introduz efeitos de ordem e memória;
- referências válidas comprovam vínculo estrutural, não fidelidade semântica automática;
- avaliação humana contém julgamento e exige calibração da rubrica;
- tempo e volume de edição não demonstram, isoladamente, melhora de qualidade.

## Estado da implementação

O TIA preserva o parecer manual antes da revelação, hash e versão do snapshot, minuta original, revisão, conclusão e metadados do modelo. A ficha humana registra as cinco dimensões da rubrica com escore 0–2, justificativa e referências separadas para a entrada e para a minuta, além das contagens definidas neste protocolo. A ficha só pode ser preenchida depois da geração, fica vinculada ao mesmo caso e se torna imutável quando finalizada; seu conteúdo nunca integra o contexto do LLM. O resumo CSV agrega apenas fichas finalizadas do usuário, por versão de prompt e modelo, sem IDs, nomes, textos, justificativas ou referências dos casos.

Ainda precisam ser implementadas a temporização ativa e, quando houver mais de um avaliador, a dupla avaliação e conciliação. Esses itens são acompanhados pelas issues acadêmicas #139 e #140.
