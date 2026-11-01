# Conversas do commit 60b9196

## Origem e preservação

O commit `60b9196` adicionou o hub, conversas privadas e de grupo, busca, avatares com iniciais, prévia de mensagem e horário, envio, renomeação local e marcação local de leitura. Também adicionou o avatar do assistente e accordions de receitas.

Foram recuperados diretamente os cinco XMLs do hub, conversa, item de conversa e balões enviados/recebidos, além do vetor de edição e da apresentação dos avatares/adapters. A tela Home mudou somente a ligação de `cardChats` para o hub humano. O CTA do Frigulino continua abrindo o assistente atual; seus assets, balões, recomendações e CTAs não foram alterados.

O repositório original usava um SharedPreferences global, familiares e mensagens de exemplo e uma resposta simulada após 1,2 segundo. Esses comportamentos não foram portados. Os layouts e interações de busca/leitura/envio permanecem, conectados a MVVM e dados reais.

## Contrato confirmado

Os contratos já estão publicados em `frigus-bff/docs/mobile-core-api-contract.md`, com proxy `/mobile/core` do commit `5e85c30`. As assinaturas foram conferidas no `ConversationController`, DTOs e `MessageService` do Core, cujo histórico registra `e7464a4` para esses arquivos.

| Operação | Contrato |
| --- | --- |
| Hub | `GET /mobile/core/conversations` retorna lista das conversas ativas |
| Detalhes | `GET /mobile/core/conversations/{id}` |
| Histórico | `GET /mobile/core/conversations/{id}/messages?page=&size=&sort=createdAt,desc` |
| Envio | `POST /mobile/core/conversations/{id}/messages`, corpo `messageType: TEXT` e `content`, exige `201` com mensagem real |
| Leitura | `POST /mobile/core/conversations/{id}/messages/{messageId}/read`, exige `204` |

O participante, o autor e o acesso são validados pelo Core. O aplicativo só navega usando UUIDs recebidos da API. Não pede IDs nem cria grupos/pessoas/conversas de exemplo. O commit original não tinha controle de criação de conversa; o hub atual exibe as conversas reais existentes.

Não há campo de presença nem total de não lidas no DTO publicado. A interface não fabrica o rótulo Online ou badges. Renomear não possui endpoint no controller; o ícone fica oculto e o gesto sobre um grupo informa a indisponibilidade. A leitura publicada marca uma mensagem, por isso as opções do cabeçalho oferecem marcar apenas a última recebida, sem afirmar que todo o histórico foi lido.

## Estado e falhas

As ViewModels preservam busca, páginas e rascunho durante rotação. API e repositório ficam fora das Activities. Leituras e resultados usam dono e credencial/geração capturados; logout, 401 ou troca de conta fecham a atividade e abrem o login com pilha limpa. 403 mantém a sessão e apresenta o erro.

Antes de cada POST é gravada uma pendência privada por conta, conversa e operação. Timeout, falha de transporte, resposta inválida ou erro 5xx bloqueiam novas mutações. Essa trava sobrevive ao encerramento do processo. Somente **Atualizar conversas**, ação explícita que faz GET de detalhes/histórico, libera uma nova tentativa do usuário. O aplicativo não reenvia texto automaticamente. A mensagem entra no balão e o rascunho é limpo somente depois da confirmação real do envio. Uma falha ao persistir o checkpoint não é tratada como envio bem-sucedido.

O histórico usa paginação real; as opções do cabeçalho permitem atualizar e carregar mensagens anteriores. Não foi adicionada conexão direta ao WebSocket/Core: novas mensagens recebidas aparecem ao atualizar pelos contratos REST publicados.

## Receitas: auditoria sem alteração

No `60b9196`, os listeners usam `headerSuggestions`, `contentSuggestions`, `ivSuggestionsChevron`, `headerCatalog`, `contentCatalog` e `ivCatalogChevron`, com dois booleans locais para expandir/recolher. O XML do commit mudou 318 linhas para comportar essas seções. A tela atual recuperada de `93933ee` usa `cardFeaturedRecipe`, `rvPopularRecipes`, `txtSectionParaVoce` e `txtSectionMaisPopulares` e não contém esses seis IDs. Portar apenas os listeners causaria falha; a tela de receitas atual foi preservada para decisão separada.

## Validação

Não houve Gradle, staging, commit ou push neste agente. A revisão estática confere IDs/casts, recursos XML, corpo de envio, paginação e checkpoints. O build conjunto e os testes ficam com o agente principal.

`ConversationApiContractTest` exercita a fronteira real Retrofit/Gson/MobileHttp com um interceptor local, sem Context nem rede: fixtures do Core, listagem/detalhes/paginação e query, texto UTF-8 e confirmação 201, leitura estrita 204, bloqueio de rename/identificadores inválidos/origem externa, proteção da credencial nova contra um 401 antigo, preservação no 403 e ausência de replay após falha de transporte. Os testes foram preparados para execução no build conjunto.

A revisão independente identificou uma corrida entre confirmação de envio e edição da mensagem seguinte. `ConversationComposer` agora confirma uma revisão capturada do rascunho; um 201 antigo não apaga uma edição nova, mesmo que o usuário volte ao mesmo texto. Dois testes JVM cobrem esses casos. A marcação de leitura também considera a mensagem recebida mais recente encontrada nas páginas anteriores.

Roteiro manual: abrir Chats na Home, buscar conversa real, abrir grupo e privada, conferir autores/horários e carregar páginas anteriores. Enviar texto entre duas contas reais e atualizar a outra; confirmar que não aparece resposta simulada. Marcar a última recebida e confirmar o 204. Simular timeout de POST, fechar/reabrir o processo e conferir bloqueio até GET explícito; não pode ocorrer reenvio automático. Validar 401, 403, rotação e troca de conta durante leitura/envio. Conferir que Frigulino e receitas mantêm o desenho e comportamento atuais.
