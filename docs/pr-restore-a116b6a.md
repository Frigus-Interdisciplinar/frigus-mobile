# 🚀 Pull Request

## 📌 Contexto
<!-- O que motivou essa alteração? Link para a issue/tarefa se houver -->
> Recuperar a interface e os fluxos perdidos após a resolução de merges, partindo de `a116b6a` na branch `restore-a116b6a`, usando o código histórico de `93933ee` como referência de telas e integrando seletivamente funcionalidades posteriores. As telas recuperadas permanecem conectadas aos contratos atuais do BFF; os módulos não recebem dados fictícios para substituir respostas ausentes.

## 🛠️ O que foi feito?
- [x] Nova tela ou funcionalidade adicionada
- [x] Correção de bug / crash
- [x] Refatoração de arquitetura (MVVM / Clean Architecture)
- [x] Atualização de dependências ou configurações do Gradle / CI
<!-- Adicione ou marque os itens acima conforme o que foi desenvolvido -->

- Recuperados início, receitas/detalhes/preparo, lista de compras, perfil e planos; categorias e detalhes do estoque receberam integrações pontuais, preservando o fluxo principal já validado.
- Receitas usam catálogo, sugestões reais por estoque, favoritos, busca, paginação e acordeões recuperados do histórico. O início usa estoque e sugestões confirmados.
- A lista permite CRUD e marcação locais por conta, inclusive entrada offline após um login anterior. A sincronização Firebase mantém outbox, revisões, exclusões e status de confirmação. Adicionar ao estoque exige confirmação explícita, destino inferido ou seleção entre nomes reais, validade, local compatível e preço unitário real; mutações incertas exigem consulta explícita, sem reenvio automático.
- Perfil inclui dados atuais, avatar e preferências por conta; planos, grupo e cobrança usam contratos disponíveis e avisam quando uma ação não está publicada. Recuperação de senha e Google OAuth seguem o BFF.
- MoneySaving mantém métricas, períodos, filtros e paginação reais, protege valores desconhecidos e distingue acesso negado de sessão expirada. Os contratos históricos de compras financeiras foram recuperados sem criar um formulário novo nem registrar uma segunda compra automaticamente.
- Frigulino usa contexto validado do estoque e mensagens reais; conversas entre pessoas usam histórico paginado, envio e leitura confirmados. Ambos protegem troca de conta e resultados incertos.
- Configuração de build, whitelist, sessão, navegação e atualização entre módulos foram reunidas na integração compartilhada. Não foram introduzidas quantidades fracionadas.

Dependências de execução ainda precisam ser verificadas no ambiente de destino:

- **BFF:** rotas mobile atuais, ponte de sessão Firebase, whitelist e contratos de autenticação, estoque, receitas, assinaturas, MoneySaving e conversas.
- **Core:** contratos correspondentes, persistência financeira atômica do preço real informado ao criar o lote e encaminhamento correto do usuário/estoque ao serviço de IA.
- **IA:** rota de serviço autenticada `/internal/core/chat`, banco compatível com o Core e configuração da chave de serviço. A inspeção pública do Core Render mostrou o contrato antigo de chat, sem `foodPreferences`/`recipeIds`; isso não confirma que as alterações locais foram implantadas.
- **Firebase:** projeto compatível com o app, credenciais de serviço no BFF e regras restritas ao UID da conta Core. Sem essa configuração a lista continua local e a outbox permanece pendente; não há confirmação fictícia de sincronização.
- Nenhuma alteração dos repositórios BFF/Core/IA é incluída nos commits deste repositório Android. Deploy e validação dessas dependências permanecem pendentes.

<details>
<summary>Agrupamento proposto por responsabilidade: 12 commits, inventário completo</summary>

Inventário baseado em `git status --porcelain=v1 --untracked-files=all`: 200 arquivos pendentes antes deste documento; 201 incluindo este documento, cada um atribuído uma única vez. Os grupos são unidades de revisão por responsabilidade; a compilação foi validada sobre a árvore completa, não sobre cada commit intermediário. O grupo compartilhado reúne arquivos que conectam mais de uma funcionalidade.

**1. `build: restaurar configuração e integração compartilhada` (16 arquivos)**

```text
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/example/frigus_mobile/data/api/BffCalls.kt
app/src/main/java/com/example/frigus_mobile/data/api/MobileHttp.kt
app/src/main/java/com/example/frigus_mobile/data/api/RetrofitClient.kt
app/src/main/java/com/example/frigus_mobile/data/local/SessionManager.kt
app/src/main/java/com/example/frigus_mobile/data/repository/InventoryHomeRepository.kt
app/src/main/java/com/example/frigus_mobile/data/repository/StockRepository.kt
app/src/main/java/com/example/frigus_mobile/ui/home/MainActivity.kt
app/src/main/res/layout/activity_main.xml
AGENTS.md
app/src/test/java/com/example/frigus_mobile/RestoredBffIntegrationTest.kt
docs/post-a116b6a-integration.md
docs/prototype-recovery-audit.md
docs/restore-bff-integration.md
docs/pr-restore-a116b6a.md
```

**2. `feat(auth): recuperar login Google e recuperação de senha` (13 arquivos)**

```text
app/src/main/java/com/example/frigus_mobile/data/api/AuthApiService.kt
app/src/main/java/com/example/frigus_mobile/data/repository/AuthRepository.kt
app/src/main/java/com/example/frigus_mobile/ui/forgotpassword/ForgotPasswordActivity.kt
app/src/main/java/com/example/frigus_mobile/ui/forgotpassword/ForgotPasswordViewModel.kt
app/src/main/java/com/example/frigus_mobile/ui/login/LoginActivity.kt
app/src/main/java/com/example/frigus_mobile/ui/login/LoginViewModel.kt
app/src/main/res/layout/activity_forgot_password.xml
app/src/main/res/layout/activity_login.xml
app/src/main/java/com/example/frigus_mobile/data/model/PasswordRecoveryModels.kt
app/src/main/java/com/example/frigus_mobile/data/repository/PasswordRecoveryRepository.kt
app/src/main/res/values/password_recovery_strings.xml
app/src/test/java/com/example/frigus_mobile/PasswordRecoveryContractTest.kt
docs/password-recovery.md
```

**3. `feat(home): recuperar início e dados reais do estoque` (5 arquivos)**

```text
app/src/main/java/com/example/frigus_mobile/ui/home/HomeFragment.kt
app/src/main/java/com/example/frigus_mobile/ui/home/HomeViewModel.kt
app/src/main/res/layout/fragment_home.xml
app/src/main/res/values/home_recovered_colors.xml
app/src/main/res/values/home_recovered_strings.xml
```

**4. `feat(stock): recuperar categorias e detalhes de alimentos` (13 arquivos)**

```text
app/src/main/java/com/example/frigus_mobile/ui/stock/AlimentoDetalhesActivity.kt
app/src/main/java/com/example/frigus_mobile/ui/stock/EstoqueFragment.kt
app/src/main/java/com/example/frigus_mobile/ui/stock/InventoryViewModel.kt
app/src/main/res/layout/activity_alimento_detalhes.xml
app/src/main/res/layout/activity_categoria_itens.xml
app/src/main/res/layout/activity_todas_categorias.xml
app/src/main/res/values/lot_strings.xml
app/src/main/java/com/example/frigus_mobile/ui/stock/CategoryBrowserActivity.kt
app/src/main/java/com/example/frigus_mobile/ui/stock/CategoryBrowserViewModel.kt
app/src/main/java/com/example/frigus_mobile/ui/stock/StockDetailFormatting.kt
app/src/main/res/values/category_browser_strings.xml
app/src/test/java/com/example/frigus_mobile/RecoveredCategoryBrowserTest.kt
app/src/test/java/com/example/frigus_mobile/StockDetailFormattingTest.kt
```

**5. `feat(recipes): recuperar telas, sugestões e acordeões` (14 arquivos)**

```text
app/src/main/java/com/example/frigus_mobile/data/repository/RecipeRepository.kt
app/src/main/java/com/example/frigus_mobile/ui/recipes/ModoPreparoBottomSheet.kt
app/src/main/java/com/example/frigus_mobile/ui/recipes/ReceitaDetalhesActivity.kt
app/src/main/java/com/example/frigus_mobile/ui/recipes/ReceitasFragment.kt
app/src/main/java/com/example/frigus_mobile/ui/recipes/RecipesViewModel.kt
app/src/main/java/com/example/frigus_mobile/ui/recipes/adapter/RecipeIngredientAdapter.kt
app/src/main/java/com/example/frigus_mobile/ui/recipes/adapter/RecipePopularAdapter.kt
app/src/main/res/layout/activity_receita_detalhes.xml
app/src/main/res/layout/bottom_sheet_modo_preparo.xml
app/src/main/res/layout/fragment_receitas.xml
app/src/main/res/layout/item_recipe_ingredient.xml
app/src/main/res/layout/item_recipe_popular.xml
app/src/main/res/values/recipe_browser_strings.xml
app/src/main/res/values/recipe_accordion_strings.xml
```

**6. `feat(shopping): persistir lista offline e sincronizar por conta` (20 arquivos)**

```text
app/src/main/java/com/example/frigus_mobile/data/model/ShoppingListItem.kt
app/src/main/java/com/example/frigus_mobile/data/repository/ShoppingListRepository.kt
app/src/main/java/com/example/frigus_mobile/data/api/FirebaseSessionApiService.kt
app/src/main/java/com/example/frigus_mobile/data/api/ShoppingListApiService.kt
app/src/main/java/com/example/frigus_mobile/data/model/ShoppingContracts.kt
app/src/main/java/com/example/frigus_mobile/data/repository/FirebaseShoppingSession.kt
app/src/main/java/com/example/frigus_mobile/data/repository/ShoppingFirebaseSync.kt
app/src/main/java/com/example/frigus_mobile/data/repository/ShoppingLocalStore.kt
app/src/main/java/com/example/frigus_mobile/ui/shopping/OfflineShoppingActivity.kt
app/src/main/java/com/example/frigus_mobile/ui/shopping/OfflineShoppingViewModel.kt
app/src/main/java/com/example/frigus_mobile/ui/shopping/ShoppingListViewModel.kt
app/src/main/res/values/offline_shopping_auth_strings.xml
app/src/test/java/com/example/frigus_mobile/FirebaseSessionContractTest.kt
app/src/test/java/com/example/frigus_mobile/ShoppingLocalStoreTest.kt
app/src/test/java/com/example/frigus_mobile/ShoppingRecoveryTest.kt
docs/firebase-shopping-setup.md
docs/firebase-shopping.rules
docs/shopping-frigulino-validation.md
docs/shopping-local-offline.md
docs/shopping-recovery.md
```

**7. `feat(shopping): recuperar formulários e transferência confirmada` (12 arquivos)**

```text
app/src/main/java/com/example/frigus_mobile/ui/shopping/AddShoppingListItemFragment.kt
app/src/main/java/com/example/frigus_mobile/ui/shopping/EditShoppingListItemFragment.kt
app/src/main/java/com/example/frigus_mobile/ui/shopping/ShoppingListAdapter.kt
app/src/main/java/com/example/frigus_mobile/ui/shopping/ShoppingListFragment.kt
app/src/main/res/layout/fragment_add_shopping_list_item.xml
app/src/main/res/layout/fragment_shopping_list.xml
app/src/main/res/values/shopping_list_strings.xml
app/src/main/java/com/example/frigus_mobile/ui/shopping/ShoppingUiMessages.kt
app/src/main/res/layout/dialog_shopping_stock_transfer.xml
app/src/main/res/values/shopping_form_strings.xml
app/src/main/res/values/shopping_local_ui_strings.xml
docs/shopping-local-ui.md
```

**8. `feat(profile): recuperar dados, avatar e preferências` (26 arquivos)**

```text
app/src/main/java/com/example/frigus_mobile/ui/profile/AlterarSenhaFragment.kt
app/src/main/java/com/example/frigus_mobile/ui/profile/ConfiguracoesFragment.kt
app/src/main/java/com/example/frigus_mobile/ui/profile/EnviarSolicitacaoFragment.kt
app/src/main/java/com/example/frigus_mobile/ui/profile/PerfilFragment.kt
app/src/main/java/com/example/frigus_mobile/ui/profile/PreferenciasAlimentosFragment.kt
app/src/main/java/com/example/frigus_mobile/ui/profile/ProfileUtils.kt
app/src/main/res/layout/fragment_configuracoes.xml
app/src/main/res/layout/fragment_perfil.xml
app/src/main/res/layout/fragment_preferencias_alimentos.xml
app/src/main/res/values/account_strings.xml
app/src/main/java/com/example/frigus_mobile/data/local/ProfilePreferenceStore.kt
app/src/main/java/com/example/frigus_mobile/data/model/FoodPreferences.kt
app/src/main/java/com/example/frigus_mobile/data/repository/FoodPreferencesRepository.kt
app/src/main/java/com/example/frigus_mobile/data/repository/ProfileAvatarLocation.kt
app/src/main/java/com/example/frigus_mobile/data/repository/ProfileAvatarRepository.kt
app/src/main/java/com/example/frigus_mobile/ui/profile/FoodPreferencesViewModel.kt
app/src/main/java/com/example/frigus_mobile/ui/profile/MeusDadosFragment.kt
app/src/main/java/com/example/frigus_mobile/ui/profile/ProfileAvatarViewModel.kt
app/src/main/res/layout/fragment_meus_dados.xml
app/src/main/res/values/profile_contract_guard_strings.xml
app/src/main/res/values/profile_data_strings.xml
app/src/main/res/values/profile_preferences_state_strings.xml
app/src/main/res/values/profile_recovery_strings.xml
app/src/test/java/com/example/frigus_mobile/ProfileAvatarLocationTest.kt
app/src/test/java/com/example/frigus_mobile/ProfilePreferenceStoreTest.kt
docs/profile-recovery.md
```

**9. `feat(subscription): integrar planos, grupos e cobrança` (35 arquivos)**

```text
app/src/main/java/com/example/frigus_mobile/ui/profile/AssinaturaPlanosFragment.kt
app/src/main/java/com/example/frigus_mobile/ui/profile/GerenciarPlanoFragment.kt
app/src/main/java/com/example/frigus_mobile/ui/profile/MeuGrupoFragment.kt
app/src/main/res/layout/fragment_alterar_plano.xml
app/src/main/res/layout/fragment_assinatura_planos.xml
app/src/main/res/layout/fragment_gerenciar_plano.xml
app/src/main/res/layout/item_profile_plan_option.xml
app/src/main/java/com/example/frigus_mobile/data/api/SubscriptionApiService.kt
app/src/main/java/com/example/frigus_mobile/data/model/SubscriptionModels.kt
app/src/main/java/com/example/frigus_mobile/data/repository/SubscriptionRepository.kt
app/src/main/java/com/example/frigus_mobile/ui/profile/AlterarPlanoFragment.kt
app/src/main/java/com/example/frigus_mobile/ui/profile/HistoricoCobrancasFragment.kt
app/src/main/java/com/example/frigus_mobile/ui/profile/SubscriptionScreenFragment.kt
app/src/main/java/com/example/frigus_mobile/ui/profile/SubscriptionViewModel.kt
app/src/main/res/drawable-nodpi/plan_figma_back.png
app/src/main/res/drawable-nodpi/plan_figma_cancel.png
app/src/main/res/drawable-nodpi/plan_figma_cancel_manage.png
app/src/main/res/drawable-nodpi/plan_figma_crown.png
app/src/main/res/drawable-nodpi/plan_figma_dot.png
app/src/main/res/drawable-nodpi/plan_figma_dot_active.png
app/src/main/res/drawable-nodpi/plan_figma_family.png
app/src/main/res/drawable-nodpi/plan_figma_free.png
app/src/main/res/drawable-nodpi/plan_figma_free_bg.png
app/src/main/res/drawable-nodpi/plan_figma_group.png
app/src/main/res/drawable-nodpi/plan_figma_invite.png
app/src/main/res/drawable-nodpi/plan_figma_manage.png
app/src/main/res/drawable-nodpi/plan_figma_next.png
app/src/main/res/drawable-nodpi/plan_figma_next_red.png
app/src/main/res/drawable-nodpi/plan_figma_payment.png
app/src/main/res/drawable-nodpi/plan_figma_premium.png
app/src/main/res/drawable-nodpi/plan_figma_premium_bg.png
app/src/main/res/drawable/bg_plan_carousel_card.xml
app/src/main/res/values/plans_recovery_values.xml
app/src/main/res/values/subscription_strings.xml
docs/plans-recovery.md
```

**10. `feat(money-saving): integrar métricas e contratos financeiros` (10 arquivos)**

```text
app/src/main/java/com/example/frigus_mobile/ui/profile/MoneySavingFragment.kt
app/src/main/res/layout/fragment_money_saving.xml
app/src/main/java/com/example/frigus_mobile/data/api/MoneySavingApiService.kt
app/src/main/java/com/example/frigus_mobile/data/model/MoneySavingModels.kt
app/src/main/java/com/example/frigus_mobile/data/repository/MoneySavingRepository.kt
app/src/main/java/com/example/frigus_mobile/ui/profile/MoneySavingViewModel.kt
app/src/main/res/values/money_saving_strings.xml
app/src/test/java/com/example/frigus_mobile/MoneySavingHistoryContractTest.kt
app/src/test/java/com/example/frigus_mobile/MoneySavingValueTest.kt
docs/money-saving-history-adaptation.md
```

**11. `feat(ai): recuperar Frigulino e contexto do estoque` (17 arquivos)**

```text
app/src/main/java/com/example/frigus_mobile/data/model/AiChatModels.kt
app/src/main/java/com/example/frigus_mobile/ui/ai/ChatIaActivity.kt
app/src/main/java/com/example/frigus_mobile/ui/ai/ChatIaAdapter.kt
app/src/main/res/layout/activity_chat_ia.xml
app/src/main/res/layout/item_chat_message.xml
app/src/main/java/com/example/frigus_mobile/data/repository/AiChatRepository.kt
app/src/main/java/com/example/frigus_mobile/data/repository/AiRecipeUpdates.kt
app/src/main/java/com/example/frigus_mobile/ui/ai/AiChatViewModel.kt
app/src/main/res/drawable-nodpi/frigulino.png
app/src/main/res/drawable/bg_ai_chat_avatar.xml
app/src/main/res/values/ai_chat_dimens.xml
app/src/main/res/values/ai_chat_strings.xml
app/src/test/java/com/example/frigus_mobile/AiChatRecoveryTest.kt
docs/ai-chat-core-integration.md
docs/ai-chat-recovery.md
docs/ai-chat-stock-incident.md
docs/frigulino-asset.md
```

**12. `feat(conversations): integrar conversas e mensagens reais` (20 arquivos)**

```text
app/src/main/res/layout/activity_conversation.xml
app/src/main/res/layout/fragment_conversations.xml
app/src/main/res/layout/item_conversation.xml
app/src/main/res/values/conversation_strings.xml
app/src/main/java/com/example/frigus_mobile/data/api/ConversationApiService.kt
app/src/main/java/com/example/frigus_mobile/data/model/ConversationModels.kt
app/src/main/java/com/example/frigus_mobile/data/repository/ConversationRepository.kt
app/src/main/java/com/example/frigus_mobile/ui/conversation/AvatarUtils.kt
app/src/main/java/com/example/frigus_mobile/ui/conversation/ConversationActivity.kt
app/src/main/java/com/example/frigus_mobile/ui/conversation/ConversationComposer.kt
app/src/main/java/com/example/frigus_mobile/ui/conversation/ConversationMessagesAdapter.kt
app/src/main/java/com/example/frigus_mobile/ui/conversation/ConversationViewModels.kt
app/src/main/java/com/example/frigus_mobile/ui/conversation/ConversationsActivity.kt
app/src/main/java/com/example/frigus_mobile/ui/conversation/ConversationsAdapter.kt
app/src/main/java/com/example/frigus_mobile/ui/conversation/HumanConversationActivity.kt
app/src/main/res/drawable/ic_conversation_edit.xml
app/src/main/res/values/conversation_integration_strings.xml
app/src/test/java/com/example/frigus_mobile/ConversationApiContractTest.kt
app/src/test/java/com/example/frigus_mobile/ConversationComposerTest.kt
docs/conversations-recovery.md
```

</details>
Arquivos fora da entrega: `.env`, `local.properties`, credenciais, tokens, dados de conta, caches e diretórios de build/APKs não entram nos grupos. O inventário não contém exclusões de arquivos rastreados nem arquivos pendentes sem grupo. `AGENTS.md` é uma instrução explícita fornecida para este repositório e está no grupo compartilhado. Documentos históricos, como `docs/shopping-recovery.md`, são preservados com a indicação de decisões substituídas.

## 🧪 Como validar?
<!-- Passos objetivos para testar e garantir que funciona -->
1. Verificações automatizadas finais executadas pelo agente responsável pelo build, sem concorrência:
   - `.\gradlew.bat assembleDebug`: sucesso.
   - `.\gradlew.bat testDebugUnitTest`: sucesso; os relatórios XML confirmam **73 testes, 0 falhas, 0 erros e 0 ignorados**.
   - `.\gradlew.bat lintDebug`: sucesso; XML/texto confirmam **0 erros e 765 avisos**. Os avisos continuam pendentes de tratamento e não são apresentados como uma execução sem avisos.
   - `.\gradlew.bat assembleDebugAndroidTest`: sucesso de compilação do APK instrumentado; testes em dispositivo não foram executados por esse comando.
   - `git diff --check`: sem erros de espaços em branco; Git informa normalização LF/CRLF em alguns arquivos.
2. **Roteiro manual pendente**, em emulador/dispositivo conectado ao BFF com dependências compatíveis:
   - Autenticar por senha e Google; validar recuperação de senha; confirmar que 401 encerra a sessão e 403 preserva o login. Alternar contas e verificar isolamento de estados.
   - Navegar por início, estoque, categorias, detalhes, receitas e perfil; verificar dados reais, retorno entre abas, filtros e rotação. Testar busca, favoritos, paginação, acordeões, detalhes e preparo de receitas.
   - Criar, editar, excluir e marcar itens da lista offline; reiniciar o processo e conferir persistência. Reconectar e verificar status pendente/sincronizado/erro somente após confirmação Firebase; testar outra conta e exclusões sincronizadas.
   - Marcar item e escolher NÃO: somente marcação local. Escolher SIM: confirmar estoque por nome quando necessário, validade, local e preço real; grãos aceitam apenas despensa. Conferir lote e compra financeira uma única vez. Interromper uma resposta e fazer a consulta explícita antes de qualquer nova tentativa.
   - Conferir perfil, avatar e preferências após rotação/troca de conta; testar planos, grupo, cobrança e restrições com contas autorizadas. Confirmar mensagens claras para contratos indisponíveis.
   - Conferir MoneySaving contra os registros financeiros do Core, inclusive filtros, meses, paginação, plano insuficiente e valores sem custo conhecido. Não tratar estoque remanescente como economia.
   - Após implantar/configurar os contratos de IA compatíveis, enviar manualmente uma pergunta sobre o estoque selecionado e pedir receita; conferir o estoque real e as sugestões persistidas, sem reenvio automático em falha incerta. Validar que a CTA Frigulino permanece separada das conversas entre pessoas.
   - Em conversas entre pessoas, testar histórico paginado, remetentes, envio, leitura 204, preservação do rascunho durante envio e consulta explícita após resultado incerto.

## 🔍 Checklist de Qualidade
- [ ] **Auto-review:** Deletei códigos comentados, logs de debug (`Log.d`/`println`) e limpei imports não usados.
- [x] **Compilação & Testes:** O projeto compila e os testes unitários passam sem erros localmente.
- [x] **Arquitetura (MVVM):** A lógica de negócio está na ViewModel/Repository e a View apenas reage aos estados.
- [ ] **Recursos Android:** Strings no `strings.xml`, dimensões e cores centralizadas sem *hardcoded values*.

As revisões cruzadas dos módulos foram concluídas. O item de auto-review permanece aberto porque há diagnóstico sanitizado condicionado a DEBUG para investigar a integração de IA; a revisão global de imports/código legado não foi afirmada como concluída. Os novos textos usam recursos temáticos XML, mas a revisão integral de valores literais nos layouts históricos e dos 765 avisos de lint permanece pendente. A validação manual e a implantação das dependências externas não foram concluídas.

---
<!-- Se houver alteração visual de telas ou componentes, arraste prints ou gravação de tela abaixo -->
### 📸 Evidências Visuais
<details>
<summary>Clique para expandir (Screenshots / Vídeo)</summary>

Capturas e gravações finais ainda pendentes de coleta no roteiro manual. Referências e proveniência da recuperação estão em `docs/prototype-recovery-audit.md`, `docs/profile-recovery.md`, `docs/plans-recovery.md` e `docs/frigulino-asset.md`; elas não substituem a validação visual da versão final em execução.

</details>
