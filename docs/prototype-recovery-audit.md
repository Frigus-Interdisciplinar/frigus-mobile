# Recuperação e comparação com o protótipo

> A integração posterior dos commits após `a116b6a`, com 73 testes JVM e o build mais recente, está em [post-a116b6a-integration.md](post-a116b6a-integration.md). Os resultados abaixo registram as etapas anteriores da recuperação.

> Este relatório registra a recuperação anterior. Os pedidos posteriores de preço real, lista offline/Firebase e Frigulino estão documentados em [shopping-local-offline.md](shopping-local-offline.md), [firebase-shopping-setup.md](firebase-shopping-setup.md) e [shopping-frigulino-validation.md](shopping-frigulino-validation.md). As referências abaixo a CRUD remoto de compras, layouts integralmente idênticos e 36 testes correspondem à etapa histórica.

Referência histórica: `93933eeba73bb71c466c532dbd8324140f0178a5`. Referência visual: Figma `s4sWZm7r8a6pH6X1Oecp9I`, página `82:83`, seção **Prototype - Doméstico - Design Mobile V2** (`3762:2138`). A seção foi localizada nos metadados reais do arquivo. A conferência abaixo distingue estrutura de telas, comportamento e dependências remotas; ela não declara equivalência visual em dispositivo sem uma renderização real do app.

## Pedidos desta rodada e origem

| Pedido | Em `93933ee` | Trabalho nesta rodada |
| --- | --- | --- |
| Chat IA | Tela, quatro atalhos e balões existiam; havia fallback Core/local e repetição de POST. | Recuperar a apresentação, conectar ao BFF com ViewModel e proteger resultado incerto. Ícone ao lado de cada balão IA é acréscimo solicitado agora. |
| Retirar toolbar com identidade/sino/sair | A apresentação anterior já tinha cabeçalhos próprios; a toolbar compartilhada pertence à integração posterior. | Retirar a toolbar de `activity_main` e suas ligações. Logout continua no perfil; notificações nos cabeçalhos das telas. |
| Marcar compras e oferecer inclusão no estoque | A interação existia; usava Firestore e chamadas legadas com preço inventado e confirmação após falha. | Recuperar a interação e formulários, usando contratos BFF e destino inferido da conta/grupo/estoque. Confirmar separadamente lote criado e item marcado. |
| Foto Google no perfil | Existia, com cache global e consulta ao Google. | Restabelecer imagem pública HTTPS, cache isolado por conta e gravação da URL após autenticação confirmada. |
| Retirar Segurança das configurações | Seção existia. | Remoção explícita solicitada pelo usuário, mesmo aparecendo no protótipo. |
| Preferências alimentares úteis | Eram gravadas em cache e Firestore, mas não havia consumidor fora da tela. | Persistência local durável por conta; preferências entram no contexto do assistente. O catálogo não declara filtros que seu contrato não aceita. |
| Recuperação de senha pelo BFF/Core | Tela existia, mas o repositório só retornava indisponibilidade. | Implementar solicitação, verificação de código e redefinição, com HTTP 204 e mensagens neutras sobre entrega do e-mail. |
| Planos conforme protótipo | Vários layouts já existiam; a integração G11 acrescentou dados e operações em uma apresentação extensa. | Reaproveitar layouts e componentes, reorganizando acessos sem perder as operações BFF de assinatura/grupos/convites/transações. |
| Destino de estoque automático | Algumas apresentações posteriores pediam identificador. | Chat e compras resolvem contexto autenticado. Se houver múltiplos estoques sem seleção salva, mostrar nomes para seleção, sem digitação de ID. |

## Mapa do frame de protótipo

| Telas do protótipo | Implementação e situação |
| --- | --- |
| Home `3762:2859`, receitas `3762:2229`, detalhe `3762:4112` | Apresentações recuperadas na rodada anterior; dados reais substituem exemplos estáticos. Fotos/avaliações/duração fictícias do protótipo não são apresentadas como dados da API. |
| Estoque `3762:2139`, detalhe `3762:2507`, edição `4094:6908`, remoção `3762:2561`, Freezer `3762:2576`, Despensa `3762:2666`, categorias `3762:3803/3871/3933` | Estoque validado pelo usuário preservado. A retirada da toolbar altera somente o cabeçalho compartilhado, sem reescrever os módulos de estoque. |
| Lista `3762:2271`, adicionar à lista `4085:5274`, adicionar ao estoque `3821:4496`, editar `4050:4271` | Layouts históricos reaproveitados, comandos remotos reconciliados com o contrato atual. Nome/categoria/unidade pertencem ao catálogo; observações e foto são metadados locais e recebem indicação explícita. |
| IA `3762:2326`, variante ajuda `3762:2469` | Chat recuperado e avatar acrescentado. A conversa permanece na ViewModel; histórico remoto completo não é publicado pelo BFF atual. |
| Hub de conversas `3762:2770`, Chat João `3762:2356`, Chat Família `3762:2384` | Divergência remanescente: são fluxos de mensagens entre pessoas/grupos, distintos do assistente IA. Não foram implementados por este pedido de recuperação do chat IA. |
| Perfil `3762:3664`, configurações `3762:4322`, alimentos `3762:4160` | Recuperados e ajustados nesta rodada. Segurança foi retirada conforme pedido; preferências realmente persistem e são usadas pelo assistente. |
| Assinatura `3762:3414`, alterar plano `3762:3345`, gerenciar `3762:3512`, grupo `3762:3307`, convite `3762:3329`, cancelamento `3762:3557` | Comparação visual e reorganização concentradas nesta rodada; dados e ações permanecem no contrato G11. Detalhes da execução em `plans-recovery.md`. |
| Pagamento `3762:3456`, sucesso `3762:3479`, forma de pagamento `3762:3539`, cartão `3762:3775` | Limite funcional: sem confirmação de pagamento pago/cartão, o app não apresenta o sucesso estático do Figma como uma transação concluída. |
| Login `3762:2798`, cadastro `3762:2827`, recuperação `3762:2757` | Login/cadastro existentes preservados, corrigindo a foto no login Google. Recuperação recebe código e nova senha para concluir o contrato público real, além do único frame de solicitação de e-mail. |
| Notificações `3762:2416/2465`, QR `3762:2932/2938/2947/2989` | Integrações atuais preservadas. Não houve nova verificação visual completa dessas telas nesta rodada. |
| MoneySaving `3762:3574/3011/3068/3119/3195/3236/3277/3640` | Integração G12 preservada; o app reúne informações em uma apresentação diferente das múltiplas variantes do protótipo. Nova adequação visual dessas variantes não foi executada nesta rodada. |
| Unidades `3762:4195`, dispositivos `3762:4225`, privacidade `3762:4245`, senha `3762:4270`, conta `3762:4296`, verificação de e-mail `3762:4314` | Telas legadas existem com coberturas diferentes no backend. Retirar Segurança não equivale a implantar dispositivos/privacidade/verificação de e-mail. Recuperação de senha não é verificação do endereço de e-mail da conta. |
| Ajuda `3762:3707`, solicitação `3762:4369` | Apresentação existente preservada. Sem envio confirmado pelo contrato, não há protocolo fictício ou confirmação falsa de atendimento. |

Os frames de conexão visual chamados `caminho perfil` e `caminho plano` são ligações do protótipo, não telas adicionais do aplicativo.

## Validação

O build conjunto `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest --console=plain --no-daemon --max-workers=2` terminou com **BUILD SUCCESSFUL em 9m15s**. Os **36 testes JVM** passaram sem falhas, erros ou casos ignorados. Lint com **zero erros e 796 avisos**. APK do aplicativo gerado em `app/build/outputs/apk/debug/app-debug.apk`; APK de testes instrumentados compilado, sem execução no aparelho.

No BFF, `tsc --noEmit` passou e os **três testes focados** de `tests/shopping-recovery.test.ts` passaram. Eles verificam o método encaminhado ao Core e a validação de grupo/estoque, usando adapter em memória. As duas correções de BFF precisam estar implantadas no ambiente usado pelo app. O e-mail depende do Core atual e da configuração do Brevo; retorno 204 confirma a solicitação, não entrega de e-mail.

A revisão cruzada conferiu IDs/casts, contratos, navegação, persistência de preferências, isolamento de conta e proteção de mutações incertas. Os cinco layouts de compras permanecem idênticos a `93933ee`. Os 17 arquivos de UI/layout de estoque da captura original continuam com os mesmos SHA-256 e StockRepository não mudou em relação à integração anterior. O padrão amplo de captura também incluía `fragment_preferencias_alimentos.xml`; esse arquivo foi alterado intencionalmente nesta rodada para informar a persistência e o uso real das preferências, conforme solicitado. `git diff --check` passou.

Conferir em dispositivo com conta própria: foto Google, persistência ao sair/reabrir, preferências no assistente, fluxos de compra e lote, navegação de planos e recuperação por e-mail. Nenhuma mutação de dados pessoais, pagamento ou envio real de e-mail foi realizado pela validação automatizada. A conferência dos screenshots e dos layouts não substitui a validação visual do aplicativo com dados reais.

Alterações locais na branch `restore-a116b6a`, sem staging, commit ou push. O checkout Core existente e seus arquivos em andamento foram preservados; somente as referências remotas foram atualizadas para consultar o contrato atual.
