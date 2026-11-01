# Compras, preço e Frigulino — validação da atualização

> Relatório desta etapa histórica. O build posterior, que preserva estes fluxos e integra as funcionalidades dos commits após `a116b6a`, está em [post-a116b6a-integration.md](post-a116b6a-integration.md).

## Comportamento entregue

- Início: chamada **Converse com o Frigulino**, com o mascote fornecido pelo usuário. A imagem dos avatares tem transparência externa sobre a mesma cor do chat; o branco do personagem permanece opaco. [Origem, arquivo e prompt da edição](frigulino-asset.md).
- Lista: CRUD local durável, separado por conta, com acesso após reinício sem internet e outbox Firebase. Não exige preço nem cria produtos/lotes/compras no Core ao adicionar, editar ou marcar sem enviar ao estoque.
- Adicionar ao estoque: preço **unitário** real obrigatório. A confirmação de compra solicita validade, local e preço; grãos só aceitam despensa. O destino vem dos estoques reais da conta, sem digitar IDs.
- Detalhes: validade em `DD-MM-YYYY` e preço em reais. Preço de catálogo, quando usado como informação alternativa, recebe identificação explícita; preço ausente não vira zero fictício.
- Core: lote alimentar com preço real gera registro de compra, linha financeira e supply na mesma transação, sem incrementar a quantidade duas vezes. Há chave estável por lote e rollback conjunto. O fluxo exige a migração financeira existente `006_money_saving`.

## Dependências externas

A sincronização real ainda depende de configurar Firebase Admin no ambiente do BFF e incorporar as regras de acesso por UID. Não foram encontradas variáveis Firebase administrativas no ambiente local do BFF; o usuário não sabe se existem no servidor. O app continua funcionando localmente e preserva a fila quando a autenticação/sincronização está indisponível. Não foi feita escrita real no Firebase. [Configuração necessária](firebase-shopping-setup.md).

A outbox retoma com internet enquanto o processo está ativo ou na próxima abertura da lista. Não há WorkManager para acordar um app encerrado. As alterações do BFF/Core precisam ser integradas e implantadas no ambiente usado pelo app; os testes focados não comprovam uma implantação remota.

Firebase Auth foi fixado em `24.0.1`: `25.0.0`, selecionado pelo BoM atual, contém metadados Kotlin 2.4 incompatíveis com o compilador embutido neste AGP. Mantidos os demais componentes Gradle. A possibilidade de definir uma versão específica com BoM consta na [documentação oficial Firebase](https://firebase.google.com/docs/android/learn-more#bom).

## Verificação

- BFF: TypeScript sem erros e **8 testes focados aprovados**, cobrindo preço informado pelo cliente, recusa de preço inválido, identidade Core para custom token, bloqueio de identidade inválida e configuração Firebase ausente. Emissor Firebase simulado; nenhuma conta/token real criado nos testes.
- Core: **9 testes focados aprovados** (`StockProductServiceTest`, `StockPurchaseRecorderTest`), com H2 e proxies transacionais para registro financeiro, idempotência e rollback. Não executada a suíte completa PostgreSQL/Testcontainers.
- Android: `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest --console=plain --no-daemon --max-workers=2` terminou com **BUILD SUCCESSFUL em 22m48s**. **55 testes JVM**, sem falhas, erros ou casos ignorados. Lint com **zero erros e 804 avisos**. APK do app em `app/build/outputs/apk/debug/app-debug.apk`; APK de testes instrumentados compilado em `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, sem execução no aparelho. `git diff --check` aprovado; índice sem alterações.

Não houve staging, commit, push ou publicação de regras Firebase. O checkout Core e seus trabalhos paralelos foram preservados.

## Roteiro manual com BFF configurado

1. Entrar com conta própria, abrir o Frigulino pela Home e conferir o mascote sem quadrado claro nos balões.
2. Criar/editar/excluir itens em modo avião, encerrar e reabrir o app, usar a entrada da lista salva e conferir a persistência. Trocar de conta e conferir isolamento.
3. Marcar um item e escolher não adicionar ao estoque: somente a lista muda. Confirmar outro item para estoque, informando preço/validade/local; grão permite apenas despensa. Conferir lote, preço em R$, validade e registro financeiro único.
4. Recuperar conexão com BFF/Firebase configurados e aguardar confirmação real de sincronização; conferir a lista em outro aparelho da mesma conta. Sem configuração, conferir pendência e preservação local.
5. Em falha de transporte de uma transferência, usar a recarga explícita para reconciliar antes de tentar novamente. Logout/401 não permite continuar uma operação da conta anterior; 403 preserva a sessão.
