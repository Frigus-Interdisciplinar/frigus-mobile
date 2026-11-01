# Integração do assistente e receitas salvas

## Comportamento

Cada pergunta enviada pelo app inclui as preferências alimentares atuais, além do
estoque inferido do grupo ativo. O BFF valida os identificadores das preferências e
encaminha a chamada autenticada ao Core, que identifica o usuário.

O Core usa `POST /internal/core/chat` na IA. A IA confirma acesso ao estoque e à
conversa e consulta o banco compartilhado naquele turno. Itens vencidos, sem saldo
ou pertencentes a outro estoque não entram como disponíveis. As preferências
alimentares filtram candidatos conhecidos e orientam o especialista.

As recomendações salvas são receitas reais do catálogo compartilhado. A IA escolhe
IDs de candidatos consultados e os devolve ao Core; o Core grava ou atualiza o vínculo
da receita ao estoque em `recipe_suggestions`. Uma saudação não cria receita.
Não há associação de receitas por procurar nomes no texto da resposta.

O app consulta as sugestões salvas (M18), combina com as sugestões atuais (M14),
remove duplicatas e as apresenta na tela existente de receitas. Após uma resposta
confirmada do chat, a tela invalida essa consulta ao ser retomada. A gravação depende
de confirmação do Core; falhas de transporte continuam sem replay automático.

## Configuração dos serviços

- Core: `FRIGUS_AI_BASE_URL=http://localhost:8000` (origem sem `/v1`).
- Core: `FRIGUS_AI_API_KEY`; IA: `API_KEYS__CORE_SERVICE_KEY`. Mesmo segredo aleatório
  de pelo menos 32 caracteres, armazenado exclusivamente nos servidores.
- IA e Core precisam usar o mesmo Postgres. Mongo, Redis e provedor de IA devem estar disponíveis.
- O `.env` local da IA teve seus nomes legados migrados para os nomes atuais do Settings.
  A chave de serviço foi alinhada à configuração local do Core; os valores não constam neste documento.
- Reiniciar os serviços locais ou publicar Core, BFF e IA juntos para ativar esse contrato.
  O app continua usando a mesma rota mobile; nenhuma chave da IA é embarcada no APK.

## Validação manual

1. Entrar com uma conta e um estoque real. Ativar uma preferência no perfil.
2. Enviar uma pergunta sobre receitas e conferir os ingredientes disponíveis.
3. Voltar à tela de receitas e abrir uma recomendação salva, incluindo o modo de preparo.
4. Mudar estoque/preferências e fazer outra pergunta; verificar a nova seleção.
5. Usar outra conta e confirmar que não pode reutilizar a conversa ou estoque alheios.
6. Conferir a resposta para estoque vazio e falhas/timeout, sem reenvio automático.

Os testes automatizados exercitam contratos HTTP, autorização e persistência com
dependências controladas. Uma resposta do provedor real no aparelho requer os serviços
atualizados em execução e não é comprovada apenas pelos testes.

Validação executada nesta alteração: suíte completa da IA com 462 testes passando;
22 testes focados do cliente/serviço de IA no Core; 2 testes do BFF e TypeScript sem
erros; Ruff nos arquivos Python alterados sem erros. A configuração real foi carregada
com sucesso e a rota interna consta no OpenAPI. Core e IA locais têm o mesmo host e
nome de banco Postgres e a mesma credencial de serviço. Isso não substitui um teste
com o provedor real e o app no aparelho.

Após o teste adicional que impede confirmação fictícia fora do contexto Core,
os 17 testes afetados da IA também passaram. A leitura de receitas salvas no Core
foi coberta para não considerar lotes vencidos ou sem saldo como disponíveis.

Uma chamada curta ao `llm_especialista` com a configuração local real também recebeu
resposta do provedor. O teste usou somente uma mensagem de verificação, sem executar
tools de domínio nem criar conversa ou dados no banco. Isso confirma acesso ao provedor,
mas não substitui a conversa completa pelo aplicativo.

## Validação Android e trabalho concorrente

Atualização posterior: a integração de todos os fluxos terminou e a validação padrão conjunta passou com `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest`: 73 testes unitários, zero erros de lint e 765 avisos. Os resultados abaixo registram um snapshot anterior durante trabalho concorrente e não descrevem o estado final. Consulte também `ai-chat-stock-incident.md` e `pr-restore-a116b6a.md`. Testes instrumentados continuam apenas compilados.

`assembleDebug` passou em uma pasta temporária isolada, com os arquivos do chat e
de receitas desta alteração. A primeira tentativa compartilhando a saída padrão
encontrou alteração concorrente de `mergeDebugJavaResource/base.jar`; por isso a
validação passou a usar saídas e cache de projeto separados.

A compilação da suíte geral de testes depois falhou com referências ausentes em
`ConversationApiContractTest`, `MoneySavingHistoryContractTest` e
`RecoveredCategoryBrowserTest`. Esses testes de outros fluxos não foram alterados
para contornar a falha. Eles não estavam consistentes com as classes do snapshot
que gerou o APK. A suíte geral não pode ser registrada como aprovada por esta execução.

O teste do chat é validado separadamente contra as classes do mesmo snapshot, sem
recompilar módulos de outros fluxos que continuaram sendo alterados no repositório.
O ajuste de seleção de testes e os diretórios de saída existem apenas no script
temporário de validação, fora do projeto; o build padrão do repositório foi preservado.

Os 7 testes de `AiChatRecoveryTest` compilaram e passaram em execução direta pelo
JUnit 4.13.2, usando as classes compiladas do aplicativo e do teste. O executor
Gradle encontrou também falha em `bundleDebugClassesToRuntimeJar`: o AGP não permite
consultar o provider de `compileDebugKotlin` quando esse produtor é omitido da rodada.
A omissão foi usada apenas no procedimento temporário para reutilizar as classes
do APK enquanto outros fluxos mudavam. O teste direto usou as classes, sem depender
desse empacotamento intermediário. Isso não registra
a suíte geral Gradle como aprovada. O APK de validação foi gerado em
`C:/Users/eriksilva-ieg/AppData/Local/Temp/frigus-ai-chat-validation-20261101/build/app/outputs/apk/debug/app-debug.apk`.

`lintDebug` e `assembleDebugAndroidTest` também concluíram nessa execução isolada.
Os testes instrumentados foram apenas compilados; não foram executados em aparelho.
O relatório de lint contém 0 erros e 765 avisos. O APK do aplicativo tem 38.296.262
bytes e o APK instrumentado tem 1.305.663 bytes. A execução Gradle conjunta terminou
com falha pela restrição do provider de compilação descrita acima; os sucessos por
etapa não equivalem à aprovação de toda a suíte Android.
