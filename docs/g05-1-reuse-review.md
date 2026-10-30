# G05-1 — correção de reaproveitamento

## Branch e base

- Branch: `fix/mobile-recipe-ui-reuse`.
- Base: `dev@1e3a67e99dd3393a860628bc9132a52fea61e5fd`.
- Worktree: `C:\Users\gustavopm-ieg\Frigus repo\frigus-mobile-g05-1`.
- Correção independente da PR #22 e das outras branches desta rodada.
- Sem staging, commit, push ou criação de PR.

## Contexto

As telas, adapters e folha de preparo já existiam antes de 96f86c5. A entrega acrescentou consulta de detalhe por ID, busca/paginação e estados independentes, mas substituiu também componentes visuais que podiam ser preservados.

## Alterações

- Cabeçalho, busca, cartões, indicador de navegação e superfícies anteriores recuperados.
- Voltar à Home e abrir notificações conectados aos destinos reais existentes.
- Detalhe recupera cartão de ingredientes e botão fixo de preparo, ligado ao estado atual de M15; botão bloqueado sem receita carregada.
- O painel de instruções anterior agora recebe as instruções reais de M15; o BottomSheet existente conserva o mesmo conteúdo real e o aviso de ausência.
- Catálogo M12, sugestões M14, limite/paginação, debounce, cancelamento, sessão e tratamento de erros atuais preservados.
- Sem fotos por posição, avaliação/duração/dificuldade fictícias ou favorito local. Nenhum repositório/DTO/endpoint alterado.

## Validação

- assembleDebug: passou.
- lintDebug: passou, zero erros e 667 avisos.
- assembleDebugAndroidTest: passou; testes instrumentados apenas compilados/empacotados.
- git diff --check, XMLs e vínculos Fragment/Activity/adapter: conferidos.
- Unit tests não criados nem executados. Sem dispositivo conectado; validação visual/BFF ainda pendente.
- Build usou o Java do Android Studio e o fallback TCP temporário do Java (JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=Z:\frigus-gradle-sockets); nenhuma configuração versionada foi alterada.

## Comando usado no ambiente local

O Java falhava ao abrir o canal local do Gradle. O fallback foi aplicado somente ao processo, sem alteração de configuração versionada:

```powershell
$env:JAVA_HOME = "C:\Users\gustavopm-ieg\AppData\Local\Programs\Android Studio\jbr"
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=Z:\frigus-gradle-sockets'
.\gradlew.bat assembleDebug lintDebug assembleDebugAndroidTest --offline --no-daemon
```

O diretório de sockets inexistente faz o Java usar TCP em vez do mecanismo local que falhou. Não adicionar esse parâmetro ao Gradle versionado.

## Cenários manuais e riscos

1. Abrir Receitas: cabeçalho e ações funcionam; catálogo e sugestões continuam independentes.
2. Buscar e carregar mais: debounce/paginação não mudaram; erro em uma fonte não substitui a outra.
3. Abrir detalhe antes/depois de carregar: botão de preparo só habilita com dados válidos.
4. Conferir ingredientes, instruções inline e BottomSheet com a mesma resposta M15; ausência explícita.
5. Testar instruções longas, fonte ampliada, rotação, voltar e acesso sem sessão.

A recuperação visual precisa ser conferida em dispositivo/emulador. As integrações e seus contratos permanecem os já presentes na dev; não é necessário novo contrato do backend para esta correção.

## Arquivos exatos

- `app/src/main/java/com/example/frigus_mobile/ui/recipes/ReceitaDetalhesActivity.kt`
- `app/src/main/java/com/example/frigus_mobile/ui/recipes/ReceitasFragment.kt`
- `app/src/main/res/layout/activity_receita_detalhes.xml`
- `app/src/main/res/layout/bottom_sheet_modo_preparo.xml`
- `app/src/main/res/layout/fragment_receitas.xml`
- `app/src/main/res/layout/item_recipe_popular.xml`
- `docs/g05-1-reuse-review.md`

## Comandos para o usuário

```powershell
Set-Location -LiteralPath "C:\Users\gustavopm-ieg\Frigus repo\frigus-mobile-g05-1"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/recipes/ReceitaDetalhesActivity.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/recipes/ReceitasFragment.kt"
git add -- "app/src/main/res/layout/activity_receita_detalhes.xml"
git add -- "app/src/main/res/layout/bottom_sheet_modo_preparo.xml"
git add -- "app/src/main/res/layout/fragment_receitas.xml"
git add -- "app/src/main/res/layout/item_recipe_popular.xml"
git add -- "docs/g05-1-reuse-review.md"
git commit -m "fix(recipes): reconnect existing recipe components"
```

## Título sugerido da PR

fix(recipes): reconnect existing recipe components

## Descrição da PR

# 🚀 Pull Request

## 📌 Contexto

> As telas, adapters e folha de preparo já existiam antes de 96f86c5. A entrega acrescentou consulta de detalhe por ID, busca/paginação e estados independentes, mas substituiu também componentes visuais que podiam ser preservados.
> Base: dev@1e3a67e; independente da correção de estoque e da outra correção visual.

## 🛠️ O que foi feito?

- [ ] Nova tela ou funcionalidade adicionada
- [x] Correção de bug / crash
- [ ] Refatoração de arquitetura (MVVM / Clean Architecture)
- [ ] Atualização de dependências ou configurações do Gradle / CI

- Cabeçalho, busca, cartões, indicador de navegação e superfícies anteriores recuperados.
- Voltar à Home e abrir notificações conectados aos destinos reais existentes.
- Detalhe recupera cartão de ingredientes e botão fixo de preparo, ligado ao estado atual de M15; botão bloqueado sem receita carregada.
- O painel de instruções anterior agora recebe as instruções reais de M15; o BottomSheet existente conserva o mesmo conteúdo real e o aviso de ausência.
- Catálogo M12, sugestões M14, limite/paginação, debounce, cancelamento, sessão e tratamento de erros atuais preservados.
- Sem fotos por posição, avaliação/duração/dificuldade fictícias ou favorito local. Nenhum repositório/DTO/endpoint alterado.

## 🧪 Como validar?

1. Executar `.\gradlew.bat assembleDebug lintDebug assembleDebugAndroidTest`.
2. Testar no emulador ou dispositivo físico:
   - Abrir Receitas: cabeçalho e ações funcionam; catálogo e sugestões continuam independentes.
   - Buscar e carregar mais: debounce/paginação não mudaram; erro em uma fonte não substitui a outra.
   - Abrir detalhe antes/depois de carregar: botão de preparo só habilita com dados válidos.
   - Conferir ingredientes, instruções inline e BottomSheet com a mesma resposta M15; ausência explícita.
   - Testar instruções longas, fonte ampliada, rotação, voltar e acesso sem sessão.

Unit tests dispensados pelo usuário; testes instrumentados somente compilados/empacotados. Resultado dos checks: assembleDebug, lintDebug (zero erros, 667 avisos) e assembleDebugAndroidTest aprovados.

## 🔍 Checklist de Qualidade

- [x] **Auto-review:** Diff e vínculos dos componentes conferidos; sem logs de debug novos.
- [x] **Compilação & Testes:** Build, lint e empacotamento dos testes instrumentados aprovados; unit tests dispensados e dispositivo pendente.
- [x] **Arquitetura (MVVM):** A lógica permanece na ViewModel/Repository; a View reage aos estados existentes.
- [x] **Recursos Android:** Recursos anteriores reaproveitados; novos textos usam strings existentes.
- [ ] **Validação visual:** Dispositivo/emulador e BFF real pendentes.

---
### 📸 Evidências Visuais

<details>
<summary>Clique para expandir (Screenshots / Vídeo)</summary>

Pendentes de captura em dispositivo/emulador.

</details>
