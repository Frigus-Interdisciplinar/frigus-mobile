# Integração seletiva sobre a116b6a

Atualização posterior: o pedido de recuperar chat, transferência da lista ao estoque, foto Google, preferências úteis, recuperação de senha e planos está documentado em [prototype-recovery-audit.md](prototype-recovery-audit.md), com relatórios específicos por fluxo. As observações de lista somente local e toolbar restrita ao estoque abaixo registram o estado da recuperação anterior; nesta rodada elas foram substituídas pela integração remota de compras e pela remoção da toolbar compartilhada.

## Investigação da perda das telas — 01/11/2026

A base `a116b6a` já contém a perda visual que motivou a reclamação. O último commit imediatamente anterior à primeira ruptura é `93933eeba73bb71c466c532dbd8324140f0178a5`, de Kireway, 30/10/2026 às 13:23:38 (-03:00).

O merge `8ea75b7ae2e6a1c1ab38a0938bc126077e1c20dc`, em 30/10 às 17:52:57, tem pais `93933ee` e `ae7813e`. A comparação com seu primeiro pai mostra a substituição de Home, Receitas e Perfil (Kotlin e XML), além da retirada do import/case de `ShoppingListFragment` na Main. A lista passou a abrir Home apesar de seus arquivos continuarem presentes.

Os arquivos de shopping são idênticos entre `93933ee` e `a116b6a`. Os layouts e classes de Home/Receitas/Perfil anteriores já existiam em `c5cc0e4` e foram preservados por `93933ee`. `a116b6a`, `60b9196` e `f942ebc` herdam a primeira ruptura; portanto o último estado antes do pull posterior não é necessariamente o estado anterior à perda das telas.

Há uma segunda perda em `c2d6c30`, cujo reflog registra `pull origin feat/products-screens-logic -X theirs --no-edit`, em 31/10 às 18:15. Ela elimina vários arquivos de compras e perfil, mas não iniciou a perda visual de Home/Receitas/Perfil.

As adaptações iniciadas antes de identificar esse histórico foram interrompidas. O snapshot completo está preservado no stash `8f194ec`, com a descrição `Backup completo antes de retirar adaptacoes interrompidas; referencia original localizada em 93933ee`. Essas adaptações foram retiradas da árvore de trabalho. A recuperação autorizada aplica agora os arquivos originais de `93933ee`, com compatibilidade pontual para os contratos BFF atuais. O estado validado de G11/G12 anterior à recuperação também foi preservado em stash.

## Recuperação aplicada de `93933ee`

- Home recupera a composição original, os cartões e as ações. Apenas textos foram centralizados em recursos e três IDs foram acrescentados para receber sugestão real. Contagens vêm do HomeViewModel existente; valores desconhecidos não aparecem como zero confirmado.
- Os cinco layouts de receitas (catálogo, detalhe, preparo e dois itens), o perfil e os cinco layouts de compras (três telas e dois itens) são idênticos aos arquivos históricos. As classes de receitas usam os ViewModels e repositórios atuais, preservando catálogo, sugestões, favoritos, filtros e paginação nos controles recuperados. Dados demonstrativos de avaliação, duração e dificuldade são ocultados quando o contrato não os fornece.
- Perfil recupera avatar, identidade, plano e todos os acessos originais. Identidade e plano vêm dos ViewModels atuais. Assinatura e MoneySaving mantêm a integração G11/G12 validada.
- A barra volta a abrir `ShoppingListFragment` em `nav_lista`. O retorno entre abas mantém histórico sem alternância infinita; a barra de sessão adicional fica restrita ao estoque.
- Compras mantém adicionar, buscar, editar, excluir e marcar na lista local do usuário autenticado. Não importa cache global nem sincroniza via Firestore fora do BFF; a interface informa que os dados ficam no aparelho. A inclusão direta no estoque fica indisponível nesta versão, eliminando os antigos preços inventados e a mensagem de sucesso após falha. O BFF já publica rotas de lista, mas o aplicativo recuperado ainda não integra esse contrato remoto.
- Arquivos de apresentação e layouts de estoque foram comparados por SHA-256 com o estado anterior à recuperação e permanecem intactos.
- Configuração de dois fatores, envio de suporte e alteração de senha não simulam confirmação sem contrato publicado. As telas originais continuam acessíveis com indicação de indisponibilidade nessas ações.

A recuperação restaura a implementação histórica, sem desenhar novas versões das telas. Validação visual com sessão e BFF real em dispositivo permanece necessária.

A evidência comprova arquivos, composição e navegação históricos; não comprova funcionamento da versão antiga com o backend atualmente implantado. Algumas implementações originais usam endpoints antigos ou estado local e devem ser diferenciadas das integrações BFF da seção abaixo.

## Contexto

Branch de trabalho: `restore-a116b6a`. Base: `a116b6afe760079bc714b0bb542a2d572ee8e541`.
As telas de estoque, estoque por local, editor, lista de compras, receitas, login e seus recursos são a base recuperada. Esta integração não aplica o commit amplo que as excluiu.

Referência de build: `origin/dev` em `abdde9d`, consultada por fetch. A branch local `dev` permanece em `29acfee`. Não houve merge, cherry-pick, staging, commit ou push.

## O que os commits fizeram

| Commit | Alteração funcional | Efeito que não deve ser copiado integralmente |
| --- | --- | --- |
| `a8ed829` | Assinatura, planos/limites, transações, grupos, criação de grupo/estoque, membros/convites e MoneySaving pelo BFF; criação confirmada em HTTP 201; 403 preserva sessão. | Também excluiu o fluxo de compras e muitas telas de estoque/perfil, alterou recursos e reverteu implementações do autor. |
| `4185242` | Esconde valores do resumo financeiro quando `hasFinancialData` não confirma os dados; eventos dependem de `verifiedValue`. | Não representa nova funcionalidade independente; corrige a apresentação G12. |
| `4193ab5` | Separa a entrega G11 da G12. | Retira a integração MoneySaving e seu acesso no perfil. Essa remoção não é reaplicada aqui. |
| `29acfee` | Merge da PR 29 na dev. | A árvore final é igual à de `4193ab5`; não acrescenta implementação contra o segundo pai. |

## Configuração Gradle

Dos nove arquivos rastreados de configuração/wrapper, somente `app/build.gradle.kts` difere. Gradle 9.1.0, checksum do wrapper, AGP 9.0.1, Version Catalog, settings, propriedades e scripts já são iguais à dev consultada.

O módulo recebe Firebase BOM 35.1.0, fallback da origem BFF e validação dos valores usados em BuildConfig. `CORE_BASE_URL`, sem consumidores, é retirado. Google SignIn, seu client ID e Firestore são mantidos por compatibilidade com as telas restauradas; Firestore usa o módulo principal `firebase-firestore`, em vez do artefato KTX antigo. O client ID permite variável de ambiente com precedência sobre `.env`. Nenhuma configuração de SDK, credencial ou workaround de máquina é versionada.

## Implementação

- APIs e repositórios para G11 e G12 registrados no cliente BFF e na whitelist, mantendo as rotas anteriores.
- Telas originais de assinatura e MoneySaving passam a observar ViewModels, com strings XML para os novos estados e ações.
- Assinatura e família recebem dados confirmados do BFF; checkout pago e cartões permanecem indisponíveis sem uma integração real de pagamento.
- MoneySaving consulta resumo e eventos por grupo/período. Valores não confirmados são explicitamente desconhecidos, sem cálculo financeiro baseado em preço de estoque.
- Criação de grupo/estoque exige 201; revogação exige 204. Falha de transporte/408/5xx ou resposta de mutação inválida bloqueia novo envio até reconciliação por leitura explícita. Nenhuma mutação recebe replay automático.
- 401 invalida a sessão; 403 conserva o login. Respostas de sessão anterior são descartadas. As validações de origem e UUID do usuário são incorporadas sem retirar os acessores das telas recuperadas.
- A navegação de perfil mantém retorno e restauração das seções. O Manifest deixa de declarar a Activity inexistente `TodasCategoriasActivity`, que estava desabilitada.
- A seleção confirmada de grupo é compartilhada com Home, estoque e MoneySaving. As consultas de contexto recebem o grupo explicitamente e descartam respostas do grupo/sessão anterior. A resolução do estoque usa o agregado confirmado para aceitar itens cujo `groupId` individual esteja ausente.

## Validação automatizada de G11/G12 antes da recuperação visual

`assembleDebug`, `testDebugUnitTest`, `lintDebug` e `assembleDebugAndroidTest` passaram no build conjunto (`BUILD SUCCESSFUL`, 11m09s). Os 16 testes JVM passaram, sem falhas, erros ou testes ignorados. Lint sem erros; permanecem avisos do projeto.

A revisão independente aprovou a implementação após corrigir a atualização de contexto na reconciliação e impedir que respostas antigas sobrescrevam grupo/sessão. A Main observa mudanças confirmadas de contexto mesmo após trocar de aba; se Home estiver aberta, inicia a atualização do resumo.

A checagem incremental dos mesmos quatro targets também passou (`BUILD SUCCESSFUL`, 4m44s), incluindo recompilação dos últimos ajustes de navegação, nova execução dos testes e lint. Esses resultados correspondem à integração G11/G12 anterior à recuperação visual; a recuperação exige a nova execução registrada abaixo.

## Validação da recuperação visual

A revisão estática independente conferiu os IDs e casts de dez pares Kotlin/XML, navegação, histórico de retorno, ausência de confirmações fictícias no perfil e trava de favoritos após HTTP 408. Os 18 arquivos de estoque conferidos continuam idênticos ao estado anterior. `git diff --check` passou.

A primeira execução compilou Kotlin, mas falhou em `dexBuilderDebug` por ausência do `R.jar` gerado. O log de outro daemon confirma um build simultâneo do Android Studio no mesmo diretório, também encerrado com falha. Após os ajustes finais de compras, a execução `clean assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest --console=plain --no-daemon --max-workers=2` concluiu com `BUILD SUCCESSFUL` em 13m34s. Os 16 testes JVM passaram, sem falhas, erros ou testes ignorados. O lint reporta zero erros e 726 avisos, incluindo avisos dos layouts históricos recuperados. Ambos os APKs foram gerados; os testes instrumentados foram somente compilados.

A conferência final manteve os hashes do estoque e `git diff --check` limpo. Os 11 layouts de receitas, perfil e compras coincidem com `93933ee`. Todas as alterações seguem locais na branch `restore-a116b6a`, sem staging, commit ou push. Validação visual e dos fluxos com BFF em dispositivo permanece pendente.

Checklist da proposta:

- [x] Compilação do APK e testes JVM.
- [x] Lint e compilação/empacotamento dos testes instrumentados.
- [x] Novos fluxos preservam MVVM e Repository.
- [x] Novos textos de apresentação centralizados em XML.
- [x] Auto-review e revisão independente.
- [ ] Validação visual e roteiro de BFF real em dispositivo.

Os testes instrumentados foram somente compilados; não foram executados contra um dispositivo pessoal nem limparam suas credenciais.

## Roteiro manual com BFF

1. Entrar com conta existente; conferir Home, catálogo de receitas, detalhe/preparo, perfil e estoque. Alternar abas e retornar por gesto/botão. Em compras, adicionar, buscar, editar, marcar e excluir um item local; reabrir e trocar conta para conferir isolamento. Confirmar aviso de armazenamento no aparelho e indisponibilidade de envio direto ao estoque. Consultar favoritos e novas páginas pelo filtro de receitas; simular HTTP 408 em favorito e exigir recarga antes de novo envio.
2. Abrir Assinatura e planos; comparar nome, preço, status, período, benefícios e limites com respostas reais do BFF. Ausência/erro não deve produzir plano fictício.
3. Consultar transações vazias e paginadas. Conferir plano gratuito e indisponibilidade explícita de pagamento pago/cartões. Não usar dados de cartão de teste como pagamento confirmado.
4. Criar grupo e, quando permitido, seu primeiro estoque; confirmar 201 e atualização da Home/estoque. Navegar e reabrir para conferir persistência real.
5. Selecionar grupo, consultar membros/convites; criar/revogar um convite autorizado. Após timeout, consultar novamente antes de reenviar. Nenhum convite é enviado durante testes automatizados.
6. Em conta e plano apropriados, cancelar/reativar assinatura e comparar resposta do servidor. Verificar a trava de resultado incerto, inclusive após rotação e recriação.
7. Abrir MoneySaving com acesso comercial provisionado; conferir mês, mudança de mês, categorias, tipos e mais páginas. Filtros devem corresponder ao pedido enviado.
8. Resumo com `hasFinancialData=false`, valor nulo ou evento não verificado: exibir valor não confirmado e ocultar detalhes financeiros derivados. Dados vazios não devem parecer economia zero confirmada.
9. Testar 403 sem encerrar sessão, 401 com login/pilha limpa e logout/troca de conta durante uma resposta lenta. Nenhum dado da sessão anterior pode aparecer na seguinte.
10. Testar fonte ampliada, teclado nos formulários, TalkBack, telas pequenas e tablet. Capturas visuais e teste com BFF real ficam pendentes até execução em dispositivo.

## Limites desta entrega

Os fluxos legados de preferências, cartões, criação direta de produto e edição existentes na base não são reimplementados por esta integração. Compras recebe as guardas locais descritas na recuperação, sem novo contrato remoto. Preservar arquivos não comprova suporte do BFF. Em particular, rotas de Core sem prefixo mobile continuam bloqueadas; nenhum sucesso fictício desses fluxos deve ser considerado validação G11/G12.

O caso `nav_lista` foi corrigido na recuperação histórica. A persistência da lista no BFF ainda não foi integrada no aplicativo recuperado; a recuperação visual não valida integrações legadas com Core ou Firestore.

O stash `Preservar alteracoes locais antes de restaurar a116b6a` continua separado e não foi reaplicado.
