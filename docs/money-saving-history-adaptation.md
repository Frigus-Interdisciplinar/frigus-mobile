# Adaptação do histórico MoneySaving

## Escopo confirmado

A revisão parte de `a116b6a` e lê os commits posteriores e a branch original `frigus_mobile/feat/money-saving` por `git show`, sem cherry-pick, checkout ou aplicação integral de commits. A orientação final do usuário foi recuperar funcionalidades que já existiam, sem criar capacidades novas e sem implementar quantidades fracionadas. Nenhuma migração decimal foi aplicada nesta rodada. Quantidades dos eventos e compras são Int, conforme os DTOs atuais do Core; valores monetários mantêm o contrato Double do cliente histórico.

Foram preservados a composição atual de cards, os atalhos, fontes/cores, seletores mensais, filtros e paginação. Não foram alterados Shopping*, StockModels, Gradle, BFF ou Core nesta tarefa. O agente principal integra apenas as rotas explícitas de food-purchases à whitelist e executa o build central.

## Commits e origem

Todos os commits abaixo são de 31/10/2026. Os hashes foram conferidos nos repositórios locais, sem alterar refs.

| Commit/origem | Descrição e efeito histórico | Adaptação no código atual |
| --- | --- | --- |
| `a8ed829` — frigus-mobile-new, pai direto `a116b6a`, GustavoManganelli | `feat(mobile): integrate BFF subscription and MoneySaving`: API, modelos, Repository, ViewModel e tela G12; o commit também substituía/removia diversos módulos fora do domínio | Mantidos summary/events reais pelo BFF e MVVM, sem reaplicar remoções de outras telas |
| `4185242` — frigus-mobile-new, pai `a8ed829`, GustavoManganelli | `fix(money-saving): hide unverified summary values` | hasFinancialData/verifiedValue continuam protegendo valores desconhecidos; ausência de custo não vira zero estimado |
| `4193ab5` — frigus-mobile-new/origin/feat/products-screens-logic, pai `4185242`, GustavoManganelli | `refactor(mobile): separate G12 from G11 integration`: retirou temporariamente a integração G12 para separar a entrega de assinatura | Não aplicada a retirada de MoneySaving; a separação de camadas e domínio é preservada |
| `fee2ee3` — frigus-mobile-new e remota original feat/mobile-money-saving-metrics, pai `29acfee`, GustavoManganelli | `feat(money-saving): add real BFF metrics screens`: reintroduziu summary/events, período, categorias, tipos e páginas | Funcionalidades já presentes foram conferidas e mantidas com o visual recuperado; sem substituir cards por uma tela técnica |
| `8ba9669` — origin/feat/mobile-money-saving-metrics, pai `fee2ee3`, gabriel03013 | `fix(money-saving): preserve BFF forbidden details`: distingue restrição de plano das demais proibições | displayMessage do BFF preservado; apenas código MONEY_SAVING_PLAN_REQUIRED em 403 oferece upgrade; outros 403 não são apresentados como falta de plano |
| `65a5f2d` — frigus-mobile-new/origin/dev, pai `fee2ee3`, GustavoManganelli | `fix(money-saving): keep filters in sync with requests`: bloqueia controles durante carregamento e usa mês completo, com limite final exclusivo | Já presente: controles/snapshots coerentes e intervalo primeiro dia do mês → primeiro dia do próximo mês. Não foi copiada a exposição de códigos/HTTP/e.message técnicos que também aparecia nesse patch |
| `34c9286` — origin/dev, merge de `65a5f2d` e `8ba9669`, GustavoManganelli | `merge: sync remote MoneySaving changes` | Preservadas as duas correções funcionais com ApiError amigável, LiveData readonly e guardas de sessão/grupo/requisição |
| `df96db3` — frigus_mobile/feat/money-saving, pai `60b9196`, Kireway/Erik | `feat(money-saving): implement commercial and domestic plan entitlement with upgrade lock and metrics dashboard` | Recuperada a ação para abrir os planos quando o BFF negar entitlement. A decisão é do contrato allowMoneySaving do BFF/Core, sem conceder acesso automaticamente pelo tipo da conta |
| `4cfa4a0` — frigus_mobile/feat/money-saving, pai `df96db3`, Kireway/Erik | `fix(res): resolve missing color_surface_secondary resource linking in fragment_money_saving` | A tela atual usa recursos centralizados já existentes e não referencia a cor ausente; não é necessário introduzir outra cor global |
| `f942ebc` — frigus_mobile/feat/money-saving, pai `fad8e9a`, Kireway/Erik | `feat(money-saving): add food-purchases endpoints and models for commercial money saving`: alterou somente API/modelos/Repository | Recuperados POST/GET/PUT/DELETE e DTOs de compra/correção. O commit não continha formulário nem navegação para CRUD de compras; esta rodada também não inventa esse fluxo |

`29acfee` e `abdde9d` são os merges de entrega PR29/PR30. Não acrescentam outra implementação de MoneySaving além dos commits acima. Na branch original, `fad8e9a` liberava rotas Core/commercial amplamente; aqui só entram caminhos de compra publicados e específicos, mantendo a whitelist estrita.

## O que mudou nesta rodada

- MoneySavingApiService recupera os quatro métodos de food-purchases de `f942ebc`, com Idempotency-Key explícita no POST.
- MoneySavingModels recupera os DTOs de criação/correção/resposta e alinha quantity do evento a Int. Respostas ausentes ficam null; os defaults históricos de preço/total zero e quantidade um não são usados para fabricar confirmação.
- MoneySavingRepository usa 201 na criação, 200 nas consultas/correções e exatamente 204 na exclusão. Falhas passam por ApiError; transporte, 408/5xx ou resposta de mutação inválida geram MoneySavingUncertainMutationException. Não há retry/replay nem geração automática de outra chave ao repetir uma chamada. A chave é fornecida pelo chamador e deve ser preservada para a mesma intenção.
- Pedidos de compra validam datas, origem, IDs reais, quantidade inteira positiva e preço positivo com até duas casas decimais. Correção transmite versão e IDs de linhas; não transmite mudança de produto/estoque/validade, que o contrato Core proíbe.
- Em 403, a ViewModel limpa métricas/eventos anteriores e preserva a restrição do BFF. O botão Ver planos com MoneySaving aparece somente para MONEY_SAVING_PLAN_REQUIRED e abre a tela de assinatura existente. Não há logout em 403; 401 continua com a invalidação central da sessão.
- A explicação de cálculo agora corresponde ao serviço financeiro real, em vez de confundir estoque conservado com economia. A UI não usa payloads técnicos ou e.message como texto.

Os métodos de food-purchases permanecem contratos de dados, como no commit original: não são chamados automaticamente na tela MoneySaving, na lista offline ou após cadastrar um lote. Isso evita uma segunda compra, já que a criação de lote com preço real tem seu registro financeiro próprio no Core.

## Auditoria do cálculo real no Core

Foram lidos MoneySavingController, MoneySavingDtos, MoneySavingReportService, MoneySavingLedgerService e FoodPurchaseService do Core atual, além das rotas/schemas comerciais e commercial-access.service do BFF. Nenhum desses arquivos foi editado nesta tarefa.

| Métrica | Regra efetiva |
| --- | --- |
| Gasto | Soma quantidade × preço unitário das linhas em food_purchase_lines, ligadas a compras não excluídas, pela purchaseDate dentro do intervalo |
| Economia | Custo confirmado de saídas CONSUMED cuja data local é menor ou igual à validade |
| Perda | Custo confirmado de saídas DISCARDED com reasonCode EXPIRED e data local posterior à validade |
| Percentual de gasto | (gasto atual − gasto anterior) / gasto anterior × 100; gasto anterior zero produz percentual null |
| Categorias | FRUIT/VEGETABLE são HORTIFRUTI; demais grupos alimentares são MEAT, DAIRY, GRAIN e BEVERAGE |
| Período/páginas | from inclusivo, to exclusivo; resumo tem seis meses até o mês selecionado. Categoria/tipo filtram eventos, não o resumo mensal |

Alimento ainda no estoque não comprova economia. Alimento vencido sem descarte registrado não comprova perda. Consumo após a validade e descarte por outro motivo podem aparecer no histórico sem integrar a métrica. Saídas com custo desconhecido têm amount null/verifiedValue false; se uma saída mistura alocações conhecidas e desconhecidas, o Core exclui o valor inteiro dessa saída das métricas. O cliente mantém essas distinções e não recalcula finanças a partir do saldo atual.

O contador actions conta ações/linhas elegíveis do relatório, não quantidade de unidades nem quantidade de cupons fiscais. A data financeira das saídas é produzida pelo Core com o fuso do grupo. O cliente apenas escolhe o mês e formata os dados recebidos.

O fallback de `df96db3`/`f942ebc` usava preço de referência 12,50, meses multiplicados por percentuais, comparação fixa 5,2 e eventos gerados pelo índice do estoque. Esses dados não eram registros financeiros reais e não foram recuperados. Acesso comercial automático, plano FREE presumido em falha de rede e erros brutos também não foram recuperados.

## Contratos e limites da recuperação

POST `/mobile/commercial/groups/{groupId}/food-purchases` recebe Idempotency-Key; GET/PUT/DELETE usam `/mobile/commercial/groups/{groupId}/food-purchases/{purchaseId}`. Os caminhos foram conferidos no BFF e no controller Core. BFF e Core autorizam grupo/plano no servidor; o cliente não substitui essa decisão por código de plano cacheado.

Criar uma compra já adiciona quantidade ao estoque e alimenta o ledger; corrigir altera saldo e reconstrói alocações, com controle de versão e preservação das linhas; excluir exige ausência de saídas vinculadas e saldo suficiente. Não há endpoint publicado para listar compras ou obter compra pela chave de idempotência. O ID P:{lineId} de um evento não é purchaseId; não foi criado um vínculo fictício entre esses identificadores.

Depois de resultado incerto, um futuro fluxo chamador deve exigir GET explícito antes de outra tentativa e conservar a chave/identidade da intenção. Se o POST perdeu a resposta e purchaseId não é conhecido, os contratos atuais não permitem recuperar esse UUID pela chave; não foi introduzido formulário que ocultasse essa limitação. Esta rodada não acrescenta novas regras de autenticação, novos tipos de movimentação ou suporte fracionado.

## Verificação

MoneySavingValueTest mantém a proteção de valores desconhecidos/não finitos e inclui valor negativo inválido. MoneySavingHistoryContractTest usa Retrofit e interceptor local, sem rede real, para conferir: 403 de grupo preservado e distinto de plano; POST com chave do chamador, uma única tentativa e resultado incerto em 503; DELETE estritamente 204; resposta criada incompleta sem falsos defaults; corpo de correção sem trocar produto/estoque/validade; pedido inválido rejeitado antes de rede. Gradle fica a cargo do agente principal, conforme solicitado.

Roteiro manual com BFF: verificar conta sem plano → restrição e link para assinatura; 403 de grupo → mensagem específica sem upgrade indevido nem logout; 401 → login com pilha limpa; alternar meses/categoria/tipo, paginar e rotacionar mantendo estado da Activity; trocar conta/grupo durante GET sem renderizar resposta antiga; conferir compra real, consumo até/depois da validade, descarte EXPIRED após validade e saída sem custo. Em nenhum desses casos a UI deve estimar preço ou gerar economia apenas porque há alimento guardado.
