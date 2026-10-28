# Base de integração com o Frigus BFF

Referência: [`frigus-bff/dev`, contratos mobile M01–M29](https://github.com/Frigus-Interdisciplinar/frigus-bff/blob/dev/docs/mobile-api-contracts.md). A branch `main` do BFF ainda não contém essas rotas. O Android usa apenas a origem configurada por `FRIGUS_BFF_BASE_URL` e o canal `/mobile`.

| Área | Contratos | Ponto de entrada no app |
| --- | --- | --- |
| Cadastro, login, perfil | M01–M03 | `AuthRepository` / `AuthApiService` |
| Contexto, lotes e movimentos | M04–M11 | `InventoryHomeRepository`, `StockRepository` / `StockApiService` |
| Catálogo, favoritos, sugestões e chat IA | M12–M19 | `RecipeRepository`, `AiRecipeApiService` |
| Feed, leitura e limpeza | M20–M23 | `NotificationRepository` |
| Anúncios ativos, impressões e cliques | M24–M26 | `AdvertisementRepository` |
| QR de nota, revisão e confirmação | M27–M29 | `ReceiptRepository` |

As telas que já usam `StockRepository` recebem lotes do grupo ativo via M05. M04 resolve o primeiro `stockId` para o chat IA. Novas telas devem preferir as consultas paginadas e filtros do repositório. O catálogo M12 já inclui ingredientes; não é necessário buscar `/ingredients/recipe/{id}`. M14 devolve uma lista de sugestões com `recipe` aninhada, enquanto M18 devolve uma lista de sugestões da IA. Para QR, envie o conteúdo lido como `qrPayload`; o BFF não aceita upload de imagem ou PDF.

`MobileHttp` restringe host, esquema, método e caminho, injeta Bearer nas rotas protegidas, invalida a sessão em 401 e não repete mutações automaticamente. IA e leitura de QR têm leitura de até 135 segundos para acomodar o limite de 120 segundos do BFF. Respostas 204 não são decodificadas como JSON. `BffException` leva status, código, campos e mensagem de exibição quando o BFF os fornece.

## Contratos ainda ausentes

O canal `/mobile` não publica renovação/logout, recuperação ou troca de senha, criação de grupo/estoque/lote, lista de compras, família, assinatura/pagamentos, preferências, chat humano e registro de token push. Não use rotas `/web` nem chame a Core API diretamente para preencher essas lacunas. `StockRepository` sinaliza indisponibilidade para catálogo/criação de lote; o envio de token push fica inativo até existir contrato. As telas de QR ainda estão desabilitadas no manifesto e precisam consumir `ReceiptRepository` quando o fluxo visual estiver pronto.
