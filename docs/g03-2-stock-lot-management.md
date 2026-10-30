# G03-2 — Detalhe, edição e remoção de lotes

## Branch e base

- Branch: `feat/mobile-stock-lot-management`.
- Base: `dev@1e3a67e99dd3393a860628bc9132a52fea61e5fd`.
- Worktree: `C:\Users\gustavopm-ieg\Frigus repo\frigus-mobile-g03-2`.
- Dependências incorporadas: G01, G02-1/G02-2, G03-1 e camada BFF.
- G05-1/G09 também estão incorporadas. Build e lint da dev integrada passaram antes desta implementação.
- Somente esta parte foi implementada. G03-3, criação, QR, preferências e outras funcionalidades permanecem fora do escopo.

## Reaproveitamento da implementação anterior

Detalhe, edição de quantidade/validade/local e remoção já tinham chamadas reais M07–M09 antes desta PR. As Activities haviam sido desabilitadas na G01 (f99c7a5), e o clique de acesso havia sido removido na G03-1 (ba7622a). Sua habilitação é recuperação de funcionalidade existente.

Os layouts anteriores foram reconectados à ViewModel atual: cabeçalho, ícones de categoria, chips, cartões, ações e botão fixo de salvar foram preservados. Os campos realmente ausentes foram acrescentados no formulário existente. Nome/categoria já eram exibidos, mas antes não eram persistidos pelo handler; quantidade inicial, mínimo e batch são novos controles.

DTO, endpoints, Bearer, erros estruturados e validação de 204 eram preexistentes. O validador de 204 agora é compartilhado entre a chamada síncrona existente e a chamada cancelável. Lista/detalhe/edição compartilham a apresentação de categoria e unidade. O normalizador anterior de data foi reaproveitado com parsing estrito, e as mensagens de sucesso voltaram a aparecer somente após confirmação.

O diálogo anterior repetia a operação somente por ação explícita do usuário. A proteção nova trata o resultado incerto antes de permitir outra mutação; não deve ser descrita como remoção de um replay automático daquele diálogo.

## Comportamento e contratos

| Uso | Contrato | Regra |
| --- | --- | --- |
| Abrir e recarregar detalhe | M07 GET /mobile/inventory/stock-products/:id | ID real do lote; nunca productId ou stockId |
| Salvar alterações | M08 PUT /mobile/inventory/stock-products/:id | Atualização parcial; somente campos modificados |
| Remover lote | M09 DELETE /mobile/inventory/stock-products/:id | Confirmação explícita; sucesso somente em 204, sem JSON |

O adapter recebe um callback; a MainActivity abre o detalhe apenas pelo ID inteiro positivo e recebe seu resultado. O registro de resultados é preservado separadamente da restauração dos destinos seguros, evitando depender do ciclo de vida do Fragment. As Activities de detalhe e edição são não exportadas e exigem sessão validada. A edição consulta seu próprio M07: o objeto de uma listagem não é tratado como estado definitivo.

O formulário permite name, category, storagePlace, quantity, initialQuantity, minimalQuantity, expireDate e batch. Nome é aparado, 1–255 caracteres; categoria/local usam os enums publicados; números são inteiros de zero até Int.MAX_VALUE; validade aceita dd/MM/aaaa ou yyyy-MM-dd na interface, normaliza para yyyy-MM-dd no request e rejeita datas inexistentes; batch tem até 120 caracteres e aceita string vazia. Campos sem alteração são omitidos. Não se envia corpo vazio, null indiscriminadamente, unidade, preço, marca, productId ou stockId.

Saldo é quantity, e quantidade original é initialQuantity. Não se usa minimalQuantity como quantidade original. O cartão anterior de quantidade foi preservado: percentual e barra só aparecem quando o saldo e uma quantidade inicial positiva foram informados; nenhum denominador é inventado. As sete unidades publicadas são apenas exibidas. Categorias/locais desconhecidos não são convertidos automaticamente para Geladeira. Mínimo existente não pode ser apagado pelo formulário: vazio não limpa o valor pelo contrato; zero pode ser informado explicitamente.

Quantidade enviada é saldo absoluto e gera ADJUSTMENT no backend pela própria M08; não se envia M11 adicional. DELETE remove o lote logicamente e não o produto do catálogo. Não há restauração mobile nesta entrega.

## Sessão, erros e resultado incerto

- Leituras e mutações usam chamadas canceláveis, sem dependências ou rotas novas.
- Token Bearer, restrição de origem/caminho e bloqueio de repetição HTTP são os já existentes.
- 401 encerra a sessão; 403 preserva a sessão e exibe a falta de permissão.
- Respostas de outra geração da sessão são descartadas.
- IDs e status HTTP de M07/M08 são validados; M09 exige exatamente 204.
- ApiError mantém status, code, displayMessage e fields. A tela não mostra corpos técnicos, senha ou tokens.
- Botões de mutação ficam bloqueados enquanto há requisição em andamento.
- Timeout, falha de transporte, 408/5xx ou resposta inválida de uma mutação apresentam resultado incerto. Não há retry automático de PUT/DELETE.
- Uma nova mutação só é liberada após Recarregar dados concluir M07. O formulário é descartado intencionalmente nessa reconciliação.
- Um GET 404 mostra que o lote não está disponível, sem afirmar que a operação anterior foi aplicada.
- Rascunho e marcador da tentativa pendente usam SavedStateHandle durante a restauração normal da Activity. A recriação com tentativa pendente exige consulta explícita e não reenvia a mutação.
- O marcador não é um comprovante de operação do servidor nem garante recuperação de todo crash; estado remoto definitivo exige contrato adicional do BFF.
- A lista é invalidada após confirmação, resultado incerto, saída com requisição ainda pendente ou confirmação de ausência (404). Uma rejeição definitiva, como 400/403, não marca o recurso como alterado nem reinicia a navegação.
- Retorno da edição recarrega o detalhe; retorno à lista consulta M04/M05/M06 novamente, preservando filtros e reiniciando a página zero.
- Home tem seu resumo invalidado e consulta novamente ao voltar. Não há atualização fictícia de contagens.
- O retorno sem alteração preserva a consulta, páginas e rolagem. O callback pertence à MainActivity e seu registro é salvo/restaurado; não há fallback incondicional que recarregue a lista quando um Fragment é recriado.

## Validação

A seção abaixo registrava o resultado da primeira versão de cab286c. A correção de reaproveitamento tem validação própria em `g03-2-reuse-review.md`; não considerar estes checks antigos como prova das alterações posteriores.


- dev integrada: assembleDebug e lintDebug passaram.
- G03-2: assembleDebug, lintDebug e assembleDebugAndroidTest passaram, incluindo a confirmação após os ajustes finais.
- Lint: zero erros e 676 avisos. git diff --check passou; índice vazio.
- Unit tests não foram criados nem executados, conforme solicitado.
- Testes instrumentados foram apenas compilados/empacotados.
- Não há dispositivo conectado; não foram executadas mutações reais no BFF.

Roteiro manual em ambiente de teste:
1. Abrir lotes de diferentes estoques; conferir M07 pelo ID correto, campos opcionais e unidades.
2. Abrir edição, alterar somente nome ou somente saldo; verificar payload parcial e que zero é aceito.
3. Conferir initialQuantity separado de minimalQuantity; mínimo em branco não pode apagar um valor existente.
4. Testar todas as categorias/locais, datas inválidas/bissextas, números negativos/acima do limite e batch vazio/mais de 120 caracteres.
5. Salvar com nenhum campo alterado: conferir ausência de request.
6. Validar erros estruturados de campos e 403 sem encerrar a sessão.
7. Confirmar/cancelar remoção e conferir sucesso apenas após 204.
8. Simular timeout/resposta inválida de PUT/DELETE; conferir bloqueio de reenvio e consulta explícita antes de outra tentativa.
9. Após resultado incerto, retornar 404 em M07: mostrar lote indisponível e atualizar a lista sem sucesso fictício.
10. Rotacionar durante GET, PUT e DELETE: conferir uma tentativa e rascunho preservado.
11. Recriar o processo após tentativa pendente: não repetir a mutação; exigir leitura do estado real.
12. Sair durante uma resposta lenta e entrar com outra conta: ignorar a resposta antiga.
13. Conferir lista/resumo Home após edição, remoção ou tentativa incerta.
14. Validar teclado, TalkBack, nomes longos, fontes ampliadas e rolagem no dispositivo/tablet.

## O que pedir ao backend

Não é necessário novo endpoint para esta parte. Para testes reais: URL BFF acessível, conta de teste com permissões de escrita e lotes descartáveis em um grupo autorizado. Não compartilhar senha/token no chat.

Propostas futuras, sem paths ou implementação inventados:
- Idempotência e/ou operação consultável: chave/identidade da tentativa, regras de deduplicação, resultado e erros, para distinguir timeout de aplicação confirmada.
- Limpeza explícita de mínimo, se o produto exigir: distinguir ausência de campo de intenção de limpar e retornar o mínimo persistido.
- Concorrência, se necessário: versão do recurso e resposta de conflito para evitar sobrescrita entre clientes.
- Criação direta de entidades, compras, preferências e demais lacunas seguem dependentes de seus contratos próprios.

## Arquivos e comandos desta revisão

A lista exata dos arquivos corrigidos em relação a cab286c, incluindo a remoção do recurso redundante, e os comandos individuais de staging estão em [g03-2-reuse-review.md](g03-2-reuse-review.md). Usar a entrega desse documento para esta rodada.

## Mensagem de commit

```text
fix(inventory): preserve existing lot screens and navigation state
```

## Título da PR

G03-2: reconnect existing stock lot screens to BFF state

## Descrição da PR — template preenchido

# 🚀 Pull Request

## 📌 Contexto

> Recuperar os fluxos existentes de detalhe, edição e remoção, preservando as telas anteriores e completando somente as lacunas de M07–M09.
> Base: dev@1e3a67e99dd3393a860628bc9132a52fea61e5fd.
> G01, G02-2, G03-1 e integração BFF já incorporadas; movimentos ficam para G03-3.

## 🛠️ O que foi feito?

- [x] Nova tela ou funcionalidade adicionada
- [x] Correção de bug / crash
- [x] Refatoração de arquitetura (MVVM / Clean Architecture)
- [ ] Atualização de dependências ou configurações do Gradle / CI

- Telas anteriores reconectadas ao estado por ID real via M07, com cartões, chips, ícones e botão fixo de salvar preservados; quantidade inicial correta e opcionais sem dados inventados.
- Formulário com os oito campos aceitos por M08 e envio somente dos modificados.
- Remoção confirmada por M09 com tratamento de 204 sem corpo.
- Estado protegido por sessão e rotação; nenhuma repetição automática de mutações.
- Resultado incerto exige consulta do estado real; 404 não simula sucesso.
- Atualização de estoque e Home após confirmação ou necessidade real de reconciliação; rejeições definitivas e retorno sem alteração preservam a lista.
- Nenhum endpoint, dependência, criação de entidade ou movimento adicional foi implementado.

## 🧪 Como validar?

1. Executar ` .\gradlew.bat assembleDebug lintDebug assembleDebugAndroidTest `.
2. Em dispositivo/emulador com BFF e conta de teste:
   - Abrir lote, editar um campo e conferir payload parcial, detalhe e lista atualizados.
   - Testar saldo zero, mínimo vazio, enums, datas, campos nulos e erros estruturados.
   - Cancelar/confirmar remoção e conferir 204.
   - Simular timeout, rotação e resposta tardia de outra sessão; confirmar ausência de reenvio automático.
   - Recarregar após resultado incerto e conferir tratamento de 404.
3. Seguir o roteiro completo deste documento.

Build, lint e compilação dos testes instrumentados passaram na confirmação final; lint com zero erros e 676 avisos.
Unit tests não foram criados nem executados. Validação visual e com BFF real permanece pendente.

## 🔍 Checklist de Qualidade

- [x] **Auto-review:** Diff revisado, sem novos logs de debug ou código comentado.
- [x] **Compilação & Testes:** Build, lint e compilação dos testes instrumentados aprovados; testes instrumentados não executados e unit tests dispensados.
- [x] **Arquitetura (MVVM):** Estado e operações na ViewModel/Repository; View reage ao estado.
- [x] **Recursos Android:** Novos textos e dimensões centralizados; cores existentes reutilizadas.
- [ ] **Dispositivo/BFF:** Executar os cenários manuais com lotes descartáveis.

---
### 📸 Evidências Visuais

<details>
<summary>Clique para expandir (Screenshots / Vídeo)</summary>

Pendentes de captura em emulador ou dispositivo físico.

</details>
