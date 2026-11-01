# Estoque e falha de receitas no chat — 01/11/2026

## Evidências verificadas

O trecho de Logcat das 08:52 enviado pelo usuário contém teclado, janela e diálogo; não contém status HTTP nem exceção da integração. Não permite atribuir a falha ao teclado ou ao Android.

Em consulta de banco somente leitura, o lote de **Suco De Frutas** está no estoque **1813**, com quantidade **43**, categoria BEVERAGE, local FRIDGE, validade **2028-07-28** e status FRESH. A ferramenta real `consultar_estoque(1813, {})` e `query_stock` com filtro Geladeira retornam esse produto. Portanto não é estoque vazio, vencimento, exclusão de bebidas ou join que perde esse lote.

A inspeção somente leitura do tablet confirmou `activeStockId=1813` e origem `https://frigus-bff.onrender.com/`. Não foram extraídos tokens, credenciais ou perfil pessoal. Isso comprova a seleção persistida, mas não fornece o status HTTP do envio das 08:52.

O Core público `https://frigus-core-api.onrender.com/v3/api-docs` ainda publica o contrato antigo: request sem `foodPreferences`, response sem `recipeIds`. A saúde responde UP, mas não informa SHA de deploy ou configuração do provedor. O upstream efetivo configurado no BFF e as variáveis da IA em produção não são expostos por esses endpoints públicos.

No código committed Core `origin/dev` consultado, o cliente usa `/v1/chats` e envia somente `content` à mensagem da IA: o `stockId` recebido do mobile é descartado. O contrato legado da IA resolve estoque pelo próprio usuário autenticado e não pelo contexto mobile. No HEAD local antigo, o envio de `stock_id` como campo extra também é incompatível com o schema estrito legado. A bridge já preparada em WIP usa `/internal/core/chat` com usuário e estoque explícitos e validados; isso ainda depende de publicação/configuração no ambiente usado pelo aparelho.

## Correções e provas locais

- Python: o prompt do especialista de estoque agora distingue `query_stock` do formato JSON da resposta. Antes, um teste real do Groq retornou HTTP400 `tool_use_failed`, tentando chamar ferramenta inexistente `JSON`. Após a correção, o especialista consultou o banco e respondeu corretamente Suco/43/28-07-2028. Todas as transações desse teste foram forçadas a somente leitura; não foi usado o serviço que grava conversas.
- Python: falhas SDK de provedor no router e nos especialistas passam a erro de domínio503 seguro. Não viram resposta bem-sucedida de intenção desconhecida/estoque vazio e não gravam um turno bem-sucedido fictício.
- Configuração local: o teste de receitas encontrou limite temporário Groq HTTP429 de tokens por minuto; o fallback gratuito OpenRouter configurado retornou404. Não foram alterados modelos, chaves ou planos, nem ativado modelo pago. Esses testes não comprovam os limites ou credenciais da produção.
- Core: diagnóstico interno registra estoque/contagens, status upstream e classe da causa, sem prompt, corpo ou chaves. As respostas públicas continuam amigáveis e os POSTs não recebem replay automático.
- Android: logs DEBUG com tag `FrigusAi` registram apenas fase M19, estoque, status/código técnico saneado, duração e existência de sessão reaproveitada. Não registram mensagem, resposta, preferências, token ou identificador da conversa. Os fluxos visuais e o estoque selecionado permanecem iguais.

## Publicação e preservação

O WIP Core está baseado em revisão anterior à extração NFC-e. Não deve substituir integralmente o cliente da versão atual. A adaptação seletiva para `origin/dev`, preservando `extractInvoice`, credenciais por operação e a URL `/v1/invoices/extract`, está nos artefatos `docs/ai-chat-origin-dev.patch` e `docs/ai-chat-origin-dev.md` do repositório Core. Não houve commit, push, deploy nem mudança das variáveis do Render nesta investigação.

A consulta ao painel do Render pelo navegador integrado não concluiu; nenhuma alteração foi feita pelo navegador. Logs do envio real continuam necessários para distinguir com certeza qual resposta ocorreu às 08:52. O contrato público antigo e o descarte de contexto no código publicado são evidências concretas de incompatibilidade com a integração atual, não prova do SHA exato ou de quota em produção.

## Validação

Core WIP: baseline22 e depois **33 testes focados aprovados** (24IA+9financeiros), sem falhas ou erros. A adaptação seletiva sobre `origin/dev` passou em **38 testes** (23clienteIA+12serviçoIA+3notasfiscais); aplicação e reversão do patch foram verificadas em checkouts isolados. Python: **470 testes aprovados**, incluindo sete regressões novas de falha de provedor no router/especialistas; Ruff dos quatro arquivos Python modificados nesta rodada aprovado. Nenhuma credencial ou modelo pago foi alterado. Android: `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` concluíram juntos com sucesso em 16m26s, com **73 testes unitários**, zero falhas e zero erros de lint (765 avisos). Os testes instrumentados foram compilados, não executados no aparelho.

## Imagem adicional dos logs Core

A imagem enviada depois mostra `IncorrectResultSizeDataAccessException` / `NonUniqueResultException`: **dois registros retornados para uma consulta que esperava um**. O caminho é `TransactionProcessorService.handleSubscription` → `SubscriptionRepository.findByUserId`, executado pelo `TransactionWorkerService` agendado. Esse método não filtra registros soft-deleted; a imagem, isoladamente, não informa quais são as duas assinaturas encontradas.

O caminho de IA auditado usa controller/serviço de IA e autorização por estoque/grupo (`StockRepository` / `UserGroupRepository`), sem `SubscriptionRepository` ou resolução de plano nesse ponto. Portanto essa stack trace é um problema concreto do processamento de assinaturas, mas não comprova a causa do pedido de receita. Não foram alterados os registros de assinatura, fila de transações ou regras financeiras nesta investigação.

A repetição com IDs diferentes levou a uma correção de código separada no Core, documentada em `docs/subscription-queue-recovery.md`: o rollback também desfazia o contador de tentativas, permitindo reenvio indefinido pela fila. A correção preserva registros e não executa pagamentos reais; não transforma falha técnica em inadimplência.
