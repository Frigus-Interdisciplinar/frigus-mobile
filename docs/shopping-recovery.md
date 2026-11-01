# Recuperação de compras

> Decisão posteriormente alterada pelo usuário: a lista passa a ser local/offline com outbox Firebase; só o envio confirmado ao estoque usa BFF e preço real. O comportamento atual e a validação desta mudança estão em [shopping-local-offline.md](shopping-local-offline.md). As descrições de CRUD remoto abaixo documentam a etapa anterior e não a implementação atual.

## Referência e preservação

A referência visual e de fluxo é `93933ee`, anterior à resolução incorreta do merge. Permanecem idênticos a esse commit os cinco layouts `fragment_shopping_list.xml`, `fragment_add_shopping_list_item.xml`, `fragment_edit_shopping_list_item.xml`, `item_shopping_list_category.xml` e `item_shopping_list_product.xml` (verificado com `git diff 93933ee -- <paths>`).

Foram preservados busca, agrupamento por categoria, adicionar, editar quantidade/observações/foto, excluir item, marcar/desmarcar e a opção de adicionar ao estoque ao marcar, com seleção Geladeira/Freezer/Despensa e validade. O destino é inferido do contexto autenticado retornado por M04 e da seleção de estoque salva; se não há seleção, apenas um estoque real permite inferência automática. Múltiplos estoques exigem escolher um nome real. Uma seleção salva inválida não é substituída silenciosamente. Nenhum formulário pede stockId.

O código original usava Firestore e cache local, chamadas de estoque na View e preço `1.0` como fallback, inclusive mostrando marcação em cenários de falha. Essas partes foram substituídas por ViewModel da Activity, Repository e respostas confirmadas do BFF. XML e composição visual não foram redesenhados.

## Contratos usados

Todas as URLs usam a origem configurada do BFF e a whitelist mobile. `/mobile/core/products` é o proxy autenticado publicado no BFF; o aplicativo não chama a origem Core.

| Operação | BFF | Confirmação |
| --- | --- | --- |
| Contexto e estoques do grupo | GET `/mobile/inventory/home?groupId` | 200, grupo e estoques validados |
| Listas paginadas | GET `/mobile/shopping-list/?stockId&page&size` | 200 |
| Conteúdo da lista | GET `/mobile/shopping-list/{uuid}` | 200 |
| Criar lista | POST `/mobile/shopping-list/` | 201 |
| Adicionar item | POST `/mobile/shopping-list/{uuid}/items` | 201 |
| Quantidade/marcação | PATCH `/mobile/shopping-list/{uuid}/items/{id}` | 200 |
| Excluir item | DELETE `/mobile/shopping-list/{uuid}/items/{id}` | exatamente 204 |
| Catálogo real, paginado e por grupo | GET `/mobile/core/products?groupId&page&size` | 200 |
| Novo produto do grupo | POST `/mobile/core/products` | 201, preço real informado |
| Novo lote | POST `/mobile/inventory/stocks/{id}/products?groupId` | 201 |
| Local de armazenamento | PUT `/mobile/inventory/stock-products/{id}` | 200 |
| Conferir lotes | GET `/mobile/inventory/stocks/{id}/products?page&size` | 200 |

O envio de lote referencia um productId real, quantidade e validade. Não envia preço inventado. Se o produto ainda não existe, o usuário informa seu preço real, positivo e com até duas casas decimais, para cadastrar o produto no grupo. Nome duplicado exige seleção do catálogo. Nome/categoria/unidade do item editado vêm do produto real e ficam somente leitura, pois o DTO de atualização do item aceita quantidade/status. Observações e foto são identificadas como metadados somente neste aparelho, isoladas por usuário autenticado/lista/item; o conteúdo da lista é remoto. A foto usa URI de documento com permissão de leitura persistente, sem imagem por nome/posição.

Se a lista atual foi concluída pelo Core, um novo acréscimo cria uma lista OPEN. O menu do cabeçalho permite escolher lista, estoque e atualizar. A pesquisa permanece no ViewModel da Activity. Main recebe revisão de inventário após lote confirmado para atualizar dados relacionados.

## Resultado incerto e interrupções

Antes de qualquer mutação há um marcador com commit síncrono em SharedPreferences privado, por usuário real, contendo grupo/estoque e identidades de recursos, sem tokens. Respostas confirmadas atualizam checkpoints. Antes do POST de lote são persistidos produto/quantidade/validade e os IDs dos lotes obtidos por GET. O ID do lote confirmado é gravado antes de continuar para mudança de local ou marcação de compra.

Timeout, transporte, 408/5xx, resposta inválida ou falha após uma escrita confirmada mantêm o bloqueio de novas mutações. Logout, troca de conta/contexto, rotação e morte do processo não apagam o marcador. GET automático de entrada não libera tentativas: é necessário Atualizar dados explicitamente, que consulta contexto original, listas/itens, catálogo e lotes antes de desbloquear. Não existe replay automático.

O checkpoint do lote persiste também entre processos. Uma nova tentativa explícita reutiliza o mesmo lote confirmado, validando estoque, ID, quantidade e validade, e executa somente os passos restantes. O Core pode clonar um produto global ao trocar seu local; por isso o ID confirmado do lote, e não a igualdade do productId anterior, identifica essa continuação. Para POST sem resposta, somente um novo lote observado, com correspondência inequívoca ao baseline/valores, pode ser reutilizado; múltiplos candidatos mantêm o bloqueio. Nunca é anunciado sucesso de transferência só porque um GET encontrou um candidato: a conclusão ainda requer comando explícito e confirmação dos passos restantes.

Limitação conservadora desta rodada: o marcador pendente é único por usuário. Uma pendência no grupo A bloqueia compras no grupo B até a conferência em A. Se o usuário perde acesso ao grupo A, o bloqueio permanece para impedir duplicação; não há descarte silencioso do marcador. Ambiguidade de lotes ou alteração externa da quantidade/validade também exige conferência manual e mantém a proteção. Formulários Add/Edit podem voltar à lista após rotação por causa da restauração segura da Main; a pesquisa e o estado confirmado ficam na Activity ViewModel.

401 usa o tratamento central de sessão expirada; 403 exibe restrição sem logout. Guardas verificam geração da sessão, grupo, estoque e requisição ativa entre chamadas. A UI usa displayMessage ou recursos XML amigáveis, sem payload técnico.

## Ajustes cirúrgicos necessários no BFF

Fonte Core inspecionada em `origin/dev` (`32c95fd`), sem alterar o repositório Core.

1. `src/services/core-api.service.ts`: `updateShoppingItem` encaminha PUT `/shopping-lists/{id}/items/{itemId}`; o BFF continua publicando PATCH ao mobile. O PATCH anterior encaminhado ao Core não existe no controller.
2. `src/channels/mobile/inventory/inventory.route.ts`: POST de lote aceita groupId opcional na query, valida UUID e repassa para `getActiveContext(headers, groupId)` antes de verificar se o estoque pertence ao contexto. Isso permite o grupo selecionado em vez de usar sempre o padrão.

Essas correções precisam estar no BFF utilizado pelo app. O ramo de criação manual de produto pelo endpoint de lote do BFF aponta para uma rota Core inexistente; este fluxo usa explicitamente POST `/mobile/core/products` e depois o ramo productId real do lote. Nenhuma rota Core inexistente ou fallback legado foi liberado. Não usamos as rotas de conclusão/limpeza de comprados sem correspondência confirmada no Core.

## Verificação

- Comparação dos cinco layouts com `93933ee` sem diferenças.
- `ShoppingRecoveryTest` cobre destino inválido/ambíguo, preço real, corpo de lote sem preço, classificação de resultado incerto, local aninhado do Core e reconciliação de lote sem duplicar candidato ambíguo. Execução Gradle centralizada pelo agente principal.
- BFF: `node node_modules/typescript/bin/tsc --noEmit`; teste focado `node --import tsx --test tests/shopping-recovery.test.ts` cobre PATCH mobile→PUT Core, grupo explícito na criação de lote e rejeição de grupo inválido/estoque fora do contexto sem enviar mutação. Os testes usam adapter em memória; não são evidência de BFF implantado.

## Roteiro manual com BFF

1. Entrar em grupo com estoque real; abrir compras, adicionar produto existente, editar quantidade, excluir outro item e conferir em outro aparelho via GET. DELETE deve retornar 204.
2. Criar produto novo informando preço real; verificar produto do grupo e item da lista. Conferir que foto/observação estão explicitamente locais.
3. Marcar Apenas marcar; recarregar e desmarcar. Marcar com Adicionar ao estoque, escolher local/validade e conferir o lote real no estoque. Nunca digitar ID nem aceitar preço padrão.
4. Trocar para grupo secundário e verificar que M04 e POST usam seu groupId e o estoque correto. Testar seleção salva que não pertence mais ao grupo e ausência/múltiplos estoques.
5. Interromper resposta do POST lote e matar/reabrir app; verificar bloqueio até Atualizar dados, GETs reais e ausência de POST automático. Após GET, retomar conscientemente a mesma transferência e conferir que o mesmo lote é reutilizado.
6. Interromper PUT de local/PATCH de marcação após 201 do lote, inclusive produto global clonado. Recriar processo/trocar grupo e retornar; conferir checkpoint e ausência de novo lote. Ambiguidade deve continuar bloqueada.
7. Simular 403 (sessão permanece), 401 (login limpo), timeout/5xx e resposta 2xx inválida. Em falha técnica a UI deve mostrar texto amigável, sem sucesso inventado. Logout/troca de conta durante chamadas não deve renderizar dados de outra geração.
