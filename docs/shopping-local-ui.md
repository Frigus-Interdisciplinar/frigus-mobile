# Lista local e envio confirmado ao estoque

## Comportamento da interface

A composição existente da lista, da adição e da edição foi mantida. Salvar na lista aceita nome, quantidade inteira positiva, categoria, unidade, observações e foto locais. Não pede preço, validade, produto do catálogo nem estoque, e chama `submitDraft(draft, false)`. Editar permite alterar todos esses campos pelo novo `updateItem(item, draft)`. Nenhuma dessas ações depende de conectividade ou da resolução de uma transferência anterior.

O destino **Estoque** no formulário de adição exibe nativamente **Preço unitário (R$)**, local de armazenamento e validade obrigatórios. O preço não tem valor padrão e deve ser positivo, com até duas casas decimais. O envio usa `submitDraft(draft, true)`; o sucesso aparece somente após a conclusão publicada pela ViewModel. Não há pedido posterior de preço nem criação remota ao salvar na lista.

Quando o grupo tem múltiplos estoques e nenhum destino ativo válido, o erro de contexto oferece somente os nomes reais recebidos. A seleção passa por `selectStock`, persiste o contexto do app e pede nova confirmação do envio; não repete a operação automaticamente. Esse caso não impede o uso local da lista e nunca pede ao usuário um ID de estoque.

Marcar um item abre primeiro **Adicionar ao estoque?**. **Não, apenas marcar** chama somente `checkItem(item, true)` e confirma a gravação local. **Sim** abre um formulário com local, validade e preço unitário obrigatório, inclusive para produtos já existentes. Apenas sua confirmação chama `transferItem(item, expiry, place, price)`. Fechar qualquer diálogo não marca o item. O estoque de destino é inferido pela camada de dados; a interface não solicita IDs.

Grãos aparecem entre as categorias da lista e da despensa. A adição direta à geladeira/freezer não oferece essa categoria; o formulário de transferência de um item `GRAIN` oferece apenas `PANTRY`/Despensa. A ViewModel também valida essa regra, inclusive ao resolver um produto remoto.

Erros e resultado incerto do envio ficam visíveis sem esconder os itens nem impedir o CRUD local. Atualizar dados é uma ação explícita para reconciliar o envio. A interface não apresenta sucesso de estoque em falhas de conexão ou respostas incertas.

## Sincronização

Uma faixa discreta apresenta o estado real exposto pela ViewModel: pendente, sincronizando, sincronizada, autenticação indisponível ou erro. A ação **Sincronizar** chama `syncNow()`; a interface nunca muda o rótulo para sincronizada por conta própria. A contagem pendente é exibida quando disponível. O CRUD local continua habilitado em todos esses estados. A confirmação local e a confirmação do estoque têm mensagens distintas.

A integração Firebase e a persistência/isolamento por conta pertencem ao repositório e à ViewModel. Quando não houver uma credencial Firebase válida, a UI mostra a indisponibilidade de autenticação e mantém os itens locais; não promete que o servidor recebeu os dados.

## Abertura após encerramento do processo

O login oferece **Abrir lista salva neste aparelho** somente quando há token local recuperável e perfil em cache com UUID válido (`canReadCachedShopping`). Essa permissão libera apenas dados locais da conta e não altera `isLoggedIn` nem a guarda global da MainActivity. A atividade `OfflineShoppingActivity` não é exportada, reutiliza o contêiner existente e hospeda somente a lista e seus formulários, sem barra de navegação ou banner push. Voltar na raiz fecha a atividade; os formulários preservam a navegação normal de retorno.

Ao abrir essa entrada, o login cancela sua própria restauração em andamento para não navegar para Main por cima do formulário. A ViewModel da atividade offline pode revalidar a sessão por GET M03 ao retomar ou quando a conexão validada voltar. Mantém uma única leitura em andamento, compara dono e geração da credencial e cancela a chamada ao destruir a atividade. Após autenticação real, recarrega o estado local e permite sincronizar. Um 401 apaga a sessão pela infraestrutura existente e fecha a atividade; um 403 mantém a lista local. Logout ou troca de conta também fecham essa entrada. Nenhum token ou credencial é persistido em Bundle/Intent; o estado salvo contém somente o UUID do dono para impedir restaurar telas de outra conta.

A sincronização automática acontece enquanto o processo estiver ativo e ao reabrir o aplicativo. A outbox é durável após encerramento do processo. Não há promessa de execução em segundo plano com o processo encerrado e não foi adicionado WorkManager.

## Verificação

Foram conferidos os IDs/casts dos três fragments e dos layouts, os recursos de texto e os contratos `ShoppingDraft`, `updateItem`, `transferItem` e `ShoppingSyncStatus`. XMLs parseados e `git diff --check` sem erro de whitespace. Não foi executado Gradle neste agente; build e testes conjuntos ficam com o agente principal.

Roteiro manual: em modo avião, criar, renomear, mudar categoria/unidade, marcar com Não, desmarcar e remover; reiniciar o app e conferir a persistência. Com sessão e conexão válidas, adicionar diretamente ao estoque e transferir item marcado informando preço real e validade; verificar lote e MoneySaving somente após confirmação. Repetir com grãos (somente despensa), preço vazio/zero/mais de duas casas, validade vazia, falta de estoque e perda de conexão durante envio. Conferir que a lista segue editável e que nenhum reenvio automático ocorre. Validar o status de sincronização tanto sem credencial Firebase quanto com o contrato de autenticação configurado.

Coldstart: encerrar o processo após salvar um item, ativar modo avião e abrir o app; no login, abrir a lista salva e testar CRUD e volta Add/Edit. O envio ao estoque deve exigir revalidação. Restaurar a rede e conferir uma única validação em andamento e o status real de sincronização. Simular 401 (fecha a entrada), 403 (mantém dados locais), logout e troca de conta. Conferir rotação com um formulário aberto e retorno pela raiz.
