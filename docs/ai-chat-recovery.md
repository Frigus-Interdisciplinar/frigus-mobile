# Recuperação do Chat IA de 93933ee

O complemento [Integração do assistente e receitas salvas](ai-chat-core-integration.md)
documenta a adaptação posterior entre Core e IA, as preferências estruturadas, a
autorização por usuário/estoque e o salvamento por IDs explícitos de receitas.

## Referência e apresentação

`93933ee` já continha `ChatIaActivity`, `ChatIaAdapter`, os balões, o compositor e os quatro atalhos de perguntas. A versão posterior havia substituído essa tela por uma seleção manual de estoque e pela consulta M18 de sugestões salvas.

O layout `activity_chat_ia.xml` foi recuperado diretamente de `93933ee`. Mantém a estrutura, cores, dimensões, ícones e os quatro atalhos originais. Textos e medidas passam a recursos XML. Foram acrescentados somente os controles necessários ao estado real da conversa: situação de carregamento/erro e acesso às sugestões salvas no cabeçalho. O campo manual de estoque foi retirado.

`item_chat_message.xml` conserva os balões originais. Cada mensagem da IA possui um `ImageView` `aiMessageAvatar`, de 32 dp, ao lado do balão. O ícone atual é `ic_chat_sparkle`; esse é o slot a substituir futuramente pelo mini pinguim. Mensagens do usuário não mostram esse avatar. Os estados enviando, enviado, não enviado e envio não confirmado são apresentados separadamente do texto do usuário.

## Funcionamento preservado e adaptação pontual

- Digitação, envio pelo botão/teclado, os quatro atalhos e rolagem até a última resposta permanecem.
- A atividade observa `AiChatViewModel`; o transporte passa por `AiChatRepository`. A View não executa chamadas Retrofit.
- Há uma única tentativa de `POST /mobile/recipes/ai/chat` por ação do usuário. A API/getter/whitelist já existiam; nenhuma rota nova ou chamada direta ao Core/AI foi adicionada.
- `GET /mobile/inventory/home?groupId=...` confirma o grupo ativo e seus estoques. O estoque ativo do aplicativo é usado se aparecer nessa resposta. Sem estoque selecionado, somente um estoque não ambíguo do grupo confirmado pode ser inferido. Um estoque ausente, grupo incompatível ou seleção ambígua exige revisar a seleção pelo aplicativo, sem digitar códigos no chat.
- `sessionId` retornado pelo servidor é mantido em memória e acompanha as próximas mensagens da mesma conversa. Mensagens, rascunho e contexto sobrevivem à rotação pela ViewModel. Não são persistidos no disco nem colocados em Bundle; fechar a atividade encerra a apresentação local daquele histórico.
- As preferências alimentares locais de `FoodPreferencesRepository.promptContext()` são anexadas à requisição após validar a geração da credencial. O balão continua mostrando exclusivamente a pergunta escrita pelo usuário. A entrada tem limite de 3000 caracteres; o payload combinado é validado contra o limite BFF de 4000.
- Texto da resposta, receita e instruções exibidos são exclusivamente os retornados pelo BFF. Respostas vazias/sem identificador de conversa são consideradas inválidas. Não há resposta local simulando sucesso, cálculo de economia local nem sugestão inventada.

## Histórico e consultas existentes

O Core consultado expõe `POST /ai/recipes/chat` e `GET /ai/recipes/suggestions/stock/{stockId}`. O BFF publica esses fluxos como M19 e M18. O serviço privado `frigus-ai` possui listagem de chats e `GET /chats/{chat_id}/messages`, mas o contrato mobile do BFF/Core não publica essa leitura de histórico. Rotas de conversas entre pessoas também não correspondem ao histórico do assistente.

Por isso, esta entrega não inventa uma consulta de histórico completo nem acessa o serviço AI diretamente. A funcionalidade M18 anterior é preservada pelo ícone de sugestões salvas no cabeçalho, com receitas reais e acesso ao detalhe já existente.

## Sessão e resultado incerto

Respostas são aceitas somente se a credencial, geração, grupo selecionado e estoque ativo ainda forem os que iniciaram a chamada. Troca de contexto descarta os resultados antigos. HTTP 401 segue a invalidação central da sessão; HTTP 403 mostra restrição e conserva o login.

Antes do POST, um marcador privado é gravado por usuário, grupo e estoque. Não contém mensagem, token ou `sessionId`. Esse marcador conserva a proteção mesmo ao sair da atividade ou encerrar o processo durante o envio.

Falha de transporte, HTTP 408/5xx ou resposta inválida deixam o envio bloqueado, identificado como não confirmado. Não existe retentativa automática. O usuário pode consultar M18 explicitamente para revisar eventuais receitas persistidas; a tela informa que essa leitura **não recupera nem confirma o texto da mensagem anterior**. Somente após essa consulta, e uma confirmação adicional do usuário, é possível abandonar a conversa sem confirmação e iniciar outra. Nenhuma pergunta anterior é reenviada nessa ação.

O repository antigo de `93933ee` fazia fallback para Core e, depois, gerava respostas locais, inclusive totais financeiros. A Activity antiga também reenviava o POST pelo diálogo de erro. Esses trechos não foram recuperados porque violavam a separação BFF e a política de envio único.

## Validação

Foram adicionados testes JVM para inferência de estoque, grupo incompatível, seleção ambígua, estoque explícito ausente, resposta vazia, limites do payload alimentar e classificação de resultado incerto. A execução Gradle fica a cargo do agente principal junto aos demais fluxos.

Roteiro manual: abrir pela Home; confirmar ausência de seletor/ID manual; enviar pergunta e os quatro atalhos; observar o avatar em cada resposta e instruções reais; rotacionar durante envio; consultar sugestões salvas; verificar 403/401, troca de grupo/conta, timeout e saída durante POST; confirmar que não há replay e que outra conversa exige consulta explícita após resultado incerto. BFF/AI precisam estar disponíveis para comprovar uma resposta real. Histórico remoto completo permanece fora do contrato mobile disponível.
