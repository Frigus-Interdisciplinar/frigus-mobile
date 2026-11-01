# Integração dos commits posteriores a a116b6a

## Escopo confirmado

O usuário limitou esta rodada a integrar funcionalidades que já existiam nos commits posteriores, dando prioridade ao código recuperado atual. A migração para quantidades fracionadas foi interrompida e desfeita: estoque, lista, compras e MoneySaving mantêm os contratos inteiros existentes. Nenhuma migração de banco, regra financeira nova ou nova implementação Core foi acrescentada nesta rodada.

Foram consultados o histórico de `frigus-mobile-new`, as branches locais/remotas de `frigus_mobile` e o conteúdo original dos commits, sem aplicar commits inteiros ou substituir as telas recuperadas. `git fetch origin` atualizou as referências remotas; o checkout permanece `restore-a116b6a`.

| Commit | Conteúdo histórico | Integração no código atual |
| --- | --- | --- |
| `a8ed829` | Assinaturas, planos, cobranças, grupos/convites, contexto ativo, MoneySaving inicial, dados da conta e navegação de categorias | G11 já integrado e preservado; recuperadas as telas faltantes Meus dados/Todas as categorias/Itens da categoria com os repositórios e sessão atuais. Não copiadas as remoções de estoque/lista/perfil desse commit. |
| `4185242` | Ocultar métricas não verificadas | Mantida a distinção entre zero real e valor desconhecido. |
| `4193ab5` | Separar G11 de G12 e retirar temporariamente a integração financeira | Preservada a separação dos módulos; mantida a integração G12 funcional dos commits posteriores. |
| `fee2ee3` | Relatórios reais BFF, períodos, categorias, eventos e paginação | Compatibilizados com a apresentação e ViewModel atuais. |
| `8ba9669` | Manter detalhes do erro 403 do BFF | Sem logout em 403; mensagem específica preservada. |
| `65a5f2d` | Sincronizar filtros visíveis e consultas | Mantido estado consistente enquanto há consulta em andamento. |
| `df96db3` (branch original) | Entitlement doméstico/comercial, bloqueio por plano, dashboard e entradas na Home/perfil | BFF decide acesso; botão de assinatura somente no código de plano exigido. Recuperado o atalho Home; o perfil já tinha entrada financeira. |
| `f942ebc` (branch original) | Contratos, modelos e métodos de compras alimentares | Recuperados POST/GET/PUT/DELETE no BFF comercial, chave de idempotência do chamador, revisão de edição e DELETE204. O commit não tinha formulário; nenhum foi inventado nem houve segundo lançamento automático de compras do estoque. |
| `fad8e9a` (branch original) | Timeout de conexão40s e ampliação de rotas | Timeout40s recuperado; rotas exatas publicadas, com origem BFF e sem replay/redirect. Não copiados curingas que liberavam todo o proxy. |
| `4cfa4a0` (branch original) | Corrigir cor ausente no layout financeiro daquela versão | Layout financeiro atual usa os recursos existentes e não precisa da substituição integral do dashboard antigo. |
| `60b9196` (branch original) | Central de conversas, mensagens, avatares, avatar IA e accordion de receitas | Hub/mensagens recuperados com backend real e isolamento de sessão; sem respostas/pessoas simuladas. Frigulino preservado. Seções originais de receitas ganham expansão/recolhimento sem mudar catálogo, favoritos, sugestões ou layout dos cards. |
| `29acfee`, `34c9286`, `abdde9d` | Merges das integrações | Comparadas as alterações efetivas; nenhum merge integral foi aplicado sobre o trabalho atual. |

## Preservação e limites

- Mantidos Frigulino, CTA, foto Google, lista local/outbox Firebase, confirmação de entrada no estoque e preço real, detalhes com data/preço, planos recuperados e retirada de Segurança/toolbar da conta.
- Categorias usam os layouts existentes, mantêm o local selecionado e Grãos na despensa. Hortifruti reúne frutas e vegetais. Contexto inválido não vira contagem zero; todas as páginas e o grupo/estoque retornados são validados.
- Meus dados usa o mesmo ProfileViewModel/Repository de M03 e exibe nome/e-mail/nascimento/tipo. Não simula alteração de senha ou salvamento de perfil sem contrato.
- O chat original tinha SharedPreferences global, nomes fictícios e respostas temporizadas. Foram reaproveitados apresentação e fluxo, com os endpoints reais de conversas/mensagens do proxy BFF. Não foram acrescentados endpoints ou funcionalidades que o backend não publica, como renomear conversa.
- Sem suporte novo a kg fracionado nesta rodada, conforme orientação explícita. Isso exige uma mudança separada dos contratos atuais.
- Sem staging, commit, push ou alteração de regras Firebase no servidor.

Detalhes específicos: [MoneySaving](money-saving-history-adaptation.md), [conversas](conversations-recovery.md).

## Validação

Revisão independente de contratos, sessão, XML/IDs/casts, navegação e categorias concluída. Corrigidos durante a revisão o contexto inválido que poderia aparecer como contagem zero, a perda de um novo rascunho ao confirmar a mensagem anterior e a identificação de mensagens recebidas nas páginas mais antigas.

O build conjunto `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest --console=plain --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1024m' '-Pkotlin.compiler.execution.strategy=in-process'` terminou com **BUILD SUCCESSFUL em 34m14s**. O limite de memória e o paralelismo foram ajustados somente no comando, sem mudar os arquivos Gradle do projeto, devido à pouca memória livre do ambiente. O workaround de socket indicado em AGENTS.md foi aplicado ao processo.

**73 testes JVM aprovados**, sem falhas, erros ou casos ignorados. Incluem os contratos financeiros recuperados, transporte/rotas/sessão das conversas, rascunho editado durante envio, contexto de categorias inválido versus vazio real e agrupamento de frutas/vegetais. Lint com **zero erros e 765 avisos**. `git diff --check` passou; índice sem alterações. Permanecem avisos de depreciação/compatibilidade do compilador, sem bloqueio no toolchain atual.

APKs disponíveis em `app/build/outputs/apk/debug/app-debug.apk` e `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`. Os testes instrumentados foram compilados, sem execução no aparelho. Os testes de contrato usam transporte local em memória; não houve envio real de mensagens, compras, convites ou pagamentos para validar estas alterações. Conferência visual e funcional com conta e BFF reais permanece como roteiro manual abaixo.

## Conferência manual

1. Home → MoneySaving → voltar; rotacionar mantendo período/filtros e distinguir falta de plano de falta de acesso ao grupo.
2. Configurações → Informações da conta → Meus dados; conferir dados reais, atualização GET e preservação da foto/perfil ao retornar.
3. Estoque → Ver mais → categoria → buscar/abrir alimento; repetir na despensa para conferir Grãos e em Hortifruti para frutas/vegetais.
4. Home → Chats: conferir apenas conversas reais, abrir histórico/paginar/enviar com conta de teste autorizada, e usar recarga explícita após resultado incerto. Não esperar respostas simuladas.
5. Receitas: recolher/expandir sugestões e catálogo, alternar favoritos/busca/abas e rotacionar, preservando estado e conteúdo dos cards.
6. Conferir que a chamada Frigulino, lista offline e entrada com preço no estoque continuam funcionando como antes.
