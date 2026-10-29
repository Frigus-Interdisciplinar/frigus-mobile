# G05-1 — Descoberta e preparo de receitas

## Base e escopo

- Branch: `feat/mobile-recipe-discovery`.
- Base: `dev@17ce2b55ddc8ebc534e5beb82e3e889732c6cd7c`, com G03-1 integrada.
- PR independente de G02-2/G09; aba Receitas habilitada no próprio worktree.
- Não inclui favoritos (G05-2), IA (G06), criação ou consumo de estoque.

## Integração e decisões

| Uso | Contrato | Parâmetros |
| --- | --- | --- |
| Catálogo | M12 GET /mobile/recipes | page, size=20, q por nome |
| Sugestões do grupo | M14 GET /mobile/recipes/suggestions | limit=10, stockId omitido |
| Detalhe | M15 GET /mobile/recipes/:id | ID inteiro positivo, stockId omitido |

Catálogo e sugestões são fontes independentes, com estados locais e nova tentativa próprios.
Uma sugestão vazia ou com erro não é substituída pelo catálogo ou pela IA.
A busca aguarda 350 ms, afeta somente o catálogo e reinicia em page=0.
Carregar mais preserva a busca; falha em página posterior mantém os resultados carregados.
A paginação não fornece snapshot; atualizar reconcilia alterações ocorridas entre páginas.

GETs substituídos são cancelados; respostas de outra sessão são descartadas.
Catálogo e sugestões usam ViewModel da Activity para preservar consulta e resultados em rotação e troca de abas.
Não há persistência local de receitas/filtros após encerramento do processo.

O detalhe recebe somente recipe_id, consulta M15 e valida a identidade retornada.
A Activity continua não exportada e exige sessão validada; invalidação retorna ao login com pilha limpa.
401 usa a invalidação existente; 403 preserva sessão e mostra falta de permissão.
Erros preservam ApiError com status/code/displayMessage/fields sem exibir corpos técnicos.

A interface não usa avaliação, duração, dificuldade, popularidade, fotos atribuídas por posição nem favoritos simulados.
Os campos visuais antigos do DTO compartilhado permanecem por compatibilidade, mas não são consumidos pelo fluxo novo.
Não há chamadas a M13/M16/M17/M18/M19.
Ingredientes exibem metadados retornados, sem preencher quantidade/unidade ausente.
M15 sem stockId não inclui availability: a ausência é explícita e inStock=false não é usado como prova.
Sugestões mostram matchedIngredients/missingIngredients somente quando retornados.
Preparo é somente leitura; instruções ausentes têm mensagem explícita e não existe mutação de estoque.

## Validação manual

Com BFF acessível e conta autorizada:

1. Abrir Receitas; conferir M12 e M14 independentes, sem stockId e sem chamadas IA.
2. Buscar por nome; conferir espera de digitação, page=0 e sugestões preservadas.
3. Com mais de 20 receitas, carregar até a última página e conferir contagem sem duplicação.
4. Falhar a primeira página e uma página posterior; repetir manualmente, preservando resultados anteriores.
5. Testar sugestões vazias, erro e 403 sem bloquear o catálogo.
6. Abrir receitas por IDs reais; conferir M15 e dados atualizados.
7. Testar ID inválido, 404, resposta inválida, campos nulos e ingredientes vazios.
8. Conferir ausência de avaliação, tempo, dificuldade, ranking, fotos fictícias e favorito mutável.
9. Abrir preparo com instruções longas e ausentes; conferir rolagem e ausência de chamadas de estoque.
10. Rotacionar durante leitura, trocar de aba e voltar; conferir preservação sem consultas duplicadas.
11. Sair durante resposta lenta e testar 401; conferir proteção contra respostas da sessão anterior.
12. Validar teclado, nomes longos, fonte ampliada e acessibilidade no dispositivo/tablet.

## Checks

- Revisão estática de chamadas e XML: passou; nenhum chamador remanescente de getRecipeSuggestions.
- git diff --check: passou; sem staging.
- assembleDebug/lintDebug/assembleDebugAndroidTest: passaram; lint sem erros, 666 avisos. Testes instrumentados apenas compilados.
- Unit tests não criados nem executados, conforme solicitado.
- Validação visual e BFF real: pendentes de dispositivo/emulador e ambiente acessível.

## Arquivos desta parte

- app/src/main/AndroidManifest.xml
- app/src/main/java/com/example/frigus_mobile/data/repository/RecipeRepository.kt
- app/src/main/java/com/example/frigus_mobile/ui/home/MainActivity.kt
- app/src/main/java/com/example/frigus_mobile/ui/recipes/ModoPreparoBottomSheet.kt
- app/src/main/java/com/example/frigus_mobile/ui/recipes/ReceitaDetalhesActivity.kt
- app/src/main/java/com/example/frigus_mobile/ui/recipes/ReceitasFragment.kt
- app/src/main/java/com/example/frigus_mobile/ui/recipes/RecipesViewModel.kt
- app/src/main/java/com/example/frigus_mobile/ui/recipes/adapter/RecipeIngredientAdapter.kt
- app/src/main/java/com/example/frigus_mobile/ui/recipes/adapter/RecipePopularAdapter.kt
- app/src/main/res/layout/activity_receita_detalhes.xml
- app/src/main/res/layout/bottom_sheet_modo_preparo.xml
- app/src/main/res/layout/fragment_receitas.xml
- app/src/main/res/layout/item_recipe_ingredient.xml
- app/src/main/res/layout/item_recipe_popular.xml
- app/src/main/res/values/recipe_browser_dimens.xml
- app/src/main/res/values/recipe_browser_strings.xml
- docs/g05-1-recipe-discovery.md


## Entrega para commit e PR

### Branch, base e worktree

- Branch: `feat/mobile-recipe-discovery`.
- Base: `dev@17ce2b55ddc8ebc534e5beb82e3e889732c6cd7c`.
- Worktree: `C:\Users\gustavopm-ieg\Frigus repo\frigus-mobile-g05-1`.
- Escopo e decisões: Catálogo M12 paginado, sugestões M14 independentes por grupo, detalhe M15 por ID real e preparo somente leitura, sem avaliações, tempos, fotos ou favoritos simulados.

### Checks executados e riscos

- assembleDebug: passou.
- lintDebug: passou, zero erros e 666 avisos.
- assembleDebugAndroidTest: passou; somente compilação/empacotamento.
- git diff --check: passou; índice vazio.
- Unit tests não criados nem executados.
- Sem dispositivo conectado: cenários manuais, evidências visuais e BFF real pendentes.
- As três partes foram compiladas separadamente; o smoke test combinado aguarda os merges feitos pelo usuário.
- Arquivos exatos: os 17 caminhos listados na seção Arquivos acima; os mesmos aparecem nos comandos abaixo.
- Nenhum git add, commit, push, merge ou criação de PR foi executado.

### Comandos de staging — executar somente neste worktree

```powershell
Set-Location -LiteralPath "C:\Users\gustavopm-ieg\Frigus repo\frigus-mobile-g05-1"

git add -- "app/src/main/AndroidManifest.xml"
git add -- "app/src/main/java/com/example/frigus_mobile/data/repository/RecipeRepository.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/home/MainActivity.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/recipes/ModoPreparoBottomSheet.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/recipes/ReceitaDetalhesActivity.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/recipes/ReceitasFragment.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/recipes/RecipesViewModel.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/recipes/adapter/RecipeIngredientAdapter.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/recipes/adapter/RecipePopularAdapter.kt"
git add -- "app/src/main/res/layout/activity_receita_detalhes.xml"
git add -- "app/src/main/res/layout/bottom_sheet_modo_preparo.xml"
git add -- "app/src/main/res/layout/fragment_receitas.xml"
git add -- "app/src/main/res/layout/item_recipe_ingredient.xml"
git add -- "app/src/main/res/layout/item_recipe_popular.xml"
git add -- "app/src/main/res/values/recipe_browser_dimens.xml"
git add -- "app/src/main/res/values/recipe_browser_strings.xml"
git add -- "docs/g05-1-recipe-discovery.md"
```

### Mensagem de commit

```text
feat(recipes): add BFF catalog and recipe details
```

### Título da PR

`G05-1: integrate recipe discovery and preparation`

### Descrição da PR — template preenchido

# 🚀 Pull Request

## 📌 Contexto

> G05-1: Catálogo M12 paginado, sugestões M14 independentes por grupo, detalhe M15 por ID real e preparo somente leitura, sem avaliações, tempos, fotos ou favoritos simulados.
>
> Base: dev@17ce2b55ddc8ebc534e5beb82e3e889732c6cd7c.
> Dependências necessárias já incorporadas nessa base. Esta PR pode ser validada individualmente e não depende das outras duas branches paralelas.
> A integração recomendada é G02-2 → G05-1 → G09; reconciliar as alterações compartilhadas de MainActivity ao atualizar a base.

## 🛠️ O que foi feito?

- [x] Nova tela ou funcionalidade adicionada
- [x] Correção de bug / crash
- [x] Refatoração de arquitetura (MVVM / Clean Architecture)
- [ ] Atualização de dependências ou configurações do Gradle / CI

Catálogo M12 paginado, sugestões M14 independentes por grupo, detalhe M15 por ID real e preparo somente leitura, sem avaliações, tempos, fotos ou favoritos simulados.

Nenhum endpoint, dependência ou persistência fictícia foi adicionado. As funcionalidades fora desta parte permanecem indisponíveis.

## 🧪 Como validar?

1. Executar ` .\gradlew.bat assembleDebug lintDebug assembleDebugAndroidTest ` no worktree desta branch.
   Os três checks passaram; testes instrumentados somente compilados/empacotados.
   Unit tests não foram criados nem executados, conforme solicitado.
2. Testar no emulador ou dispositivo físico com BFF acessível:
   - Pesquisar por nome no catálogo e carregar páginas seguintes; conferir que a busca não finge filtrar sugestões M14.
   - Simular sugestões vazias ou com erro mantendo o catálogo disponível; testar nova tentativa de cada seção.
   - Abrir detalhe por ID real, conferir campos ausentes, preparo e proteção da sessão.
3. Seguir os demais cenários de `docs/g05-1-recipe-discovery.md`.

Lint: zero erros e 666 avisos no projeto. Validação manual/visual e com BFF real pendente, sem dispositivo conectado.
O smoke test das três partes juntas ficará para depois de sua integração na dev.

## 🔍 Checklist de Qualidade

- [x] **Auto-review:** Diff revisado, sem novos logs de debug, código comentado ou imports não usados.
- [x] **Compilação & Testes:** App e testes instrumentados compilam; lint sem erros. Unit tests dispensados e testes de dispositivo não executados.
- [x] **Arquitetura (MVVM):** Estado e consultas ficam na ViewModel/Repository; View reage aos estados.
- [x] **Recursos Android:** Novos textos e dimensões usam recursos; cores existentes reutilizadas.
- [ ] **Validação em dispositivo/BFF:** Executar os cenários manuais antes de considerar a integração real validada.

---
### 📸 Evidências Visuais

<details>
<summary>Clique para expandir (Screenshots / Vídeo)</summary>

Pendentes de captura em emulador ou dispositivo físico.

</details>

## Conferência de integração

O usuário fará commits e merges. Quando integrar G02-2, G05-1 e G09, preservar na MainActivity:
- o encaminhamento seguro de filtros da G02-2 e sua ponte Main/Login/M03/Main;
- a entrada de Receitas da G05-1 e o detalhe habilitado somente nessa frente;
- a entrada de Perfil, signOut e refreshSessionIdentity da G09.

As três PRs alteram MainActivity em seus próprios worktrees. Ao reconciliar conflitos, não substituir o arquivo inteiro pela versão de apenas uma parte.
Não executar rebase, merge ou atualização de base automaticamente; conferir a base e fornecer ao usuário os comandos pertinentes.

## Reproduzir os checks neste ambiente

A configuração local de SDK/BFF foi reutilizada apenas em arquivos ignorados; seus conteúdos não foram incluídos no diff.
O ajuste de loopback abaixo é restrito ao processo. O diretório indicado deve permanecer inexistente para o fallback de transporte do JDK.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\Users\gustavopm-ieg\AppData\Local\Temp\frigus-no-unix-sockets'
.\gradlew.bat assembleDebug lintDebug assembleDebugAndroidTest --offline --daemon --max-workers=2 --console=plain
```
