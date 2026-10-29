# G02-2 — Atalhos e estado de navegação

## Base e limite

Branch: feat/mobile-navigation-shortcuts.
Worktree: C:\Users\gustavopm-ieg\Frigus repo\frigus-mobile-g02-2.
Base: dev@17ce2b55ddc8ebc534e5beb82e3e889732c6cd7c, após a integração da G03-1.
Esta parte é independente de G05-1 e G09. Não habilita receitas/perfil nem adiciona endpoints ou dependências.

## Comportamento

- Geladeira, Freezer e Despensa abrem todos os estoques do grupo, filtrando FRIDGE, FREEZER e PANTRY, respectivamente; limpam estoque específico, categoria e busca anteriores.
- O atalho geral limpa os filtros. Entrar pela aba Estoque preserva a consulta existente.
- O alerta abre a consulta geral com aviso visível: a lista inclui todos os status. Não envia status/alias/sort, nem filtra somente a página carregada.
- O resumo continua sendo o agregado de M04; a lista usa M05/M06 e os filtros reais.
- Busca, filtros, resultados, páginas carregadas e rolagem permanecem na ViewModel da Activity durante rotação e troca de abas.
- Alterar filtros, usar um atalho ou atualizar reinicia na página zero e no topo. A revisão da consulta impede que uma View antiga sobrescreva a rolagem de uma consulta nova.
- O Bundle salva somente destino seguro, filtros e aviso de todos os status. Não salva credenciais, lotes, resultados nem páginas. Após recriação do processo, a consulta recarrega a primeira página.
- Main encaminha o Bundle seguro ao Login se precisa validar o token salvo. Login só o devolve quando M03 autentica a mesma credencial e geração. Não há token no Intent/Bundle.
- Troca de conta, tentativa manual de login, token ausente ou 401 descartam a navegação pendente. Rede indisponível/403 permitem repetir a validação da mesma sessão.
- O encaminhamento existente de abertura de notificações é mantido.
- Voltar de uma aba principal retorna à Home; recursos pendentes permanecem explicitamente indisponíveis. Não se restaura uma pilha arbitrária de Fragments legados.

## Integração interna

Home chama MainActivity.openInventory(storagePlace, showAllStatusesNotice).
Main aplica InventoryViewModel.openShortcut e seleciona a aba Estoque.
Main exporta/importa saveNavigationState/restoreNavigationState no Bundle da Activity.
A ponte de restauração usa MainActivity.EXTRA_RESTORE_NAVIGATION; o Login compara somente credenciais mantidas em memória.
Leituras canceláveis, erros estruturados, Bearer, 401/403 e ausência de refresh são os existentes.

## Validação manual

1. Selecionar estoque específico, categoria e busca. Voltar à Home e tocar Geladeira: conferir todos os estoques, FRIDGE, busca/categoria limpas e página zero. Repetir Freezer/Despensa.
2. Conferir o atalho geral sem filtros.
3. Abrir alerta: conferir aviso visível, todos os status e ausência de query status/alias/sort.
4. Combinar filtros, buscar, carregar páginas e rolar. Trocar de aba e retornar: conferir filtros/resultados/rolagem preservados sem novo GET.
5. Rotacionar durante consulta e após várias páginas: conferir estado e ausência de GET duplicado.
6. Trocar filtro em resposta lenta: conferir cancelamento e ausência de dados/rolagem da consulta anterior.
7. Atualizar: conferir primeira página e topo, mantendo filtros.
8. Encerrar o processo com a Activity salva em segundo plano e retornar: confirmar M03 antes do acesso, filtros/destino restaurados e GET da página zero.
9. Na restauração, simular falha de rede/403 e repetir: conferir preservação da navegação para a mesma sessão.
10. Na restauração, simular 401 ou escolher outra conta: conferir descarte do estado anterior.
11. Abrir uma notificação durante a restauração e conferir seu encaminhamento.
12. Conferir Voltar, teclado, fontes ampliadas, rolagem e aviso em dispositivo/emulador.

## Checks

- git diff --check: passou na revisão estática.
- XML da tela e dos textos: parse validado.
- assembleDebug/lintDebug/assembleDebugAndroidTest: passaram; lint sem erros, 669 avisos. Testes instrumentados apenas compilados.
- Unit tests não criados nem executados, conforme solicitado.
- Cenários manuais e BFF real pendentes.

## Arquivos desta parte

- app/src/main/java/com/example/frigus_mobile/ui/home/HomeFragment.kt
- app/src/main/java/com/example/frigus_mobile/ui/home/MainActivity.kt
- app/src/main/java/com/example/frigus_mobile/ui/login/LoginActivity.kt
- app/src/main/java/com/example/frigus_mobile/ui/stock/EstoqueFragment.kt
- app/src/main/java/com/example/frigus_mobile/ui/stock/InventoryViewModel.kt
- app/src/main/res/layout/fragment_estoque.xml
- app/src/main/res/values/navigation_strings.xml
- docs/g02-2-navigation-shortcuts.md


## Entrega para commit e PR

### Branch, base e worktree

- Branch: `feat/mobile-navigation-shortcuts`.
- Base: `dev@17ce2b55ddc8ebc534e5beb82e3e889732c6cd7c`.
- Worktree: `C:\Users\gustavopm-ieg\Frigus repo\frigus-mobile-g02-2`.
- Escopo e decisões: Atalhos por local, consulta geral com aviso de todos os status e preservação de filtros, páginas e rolagem. Restauração passa por M03 e descarta o contexto na troca de conta.

### Checks executados e riscos

- assembleDebug: passou.
- lintDebug: passou, zero erros e 669 avisos.
- assembleDebugAndroidTest: passou; somente compilação/empacotamento.
- git diff --check: passou; índice vazio.
- Unit tests não criados nem executados.
- Sem dispositivo conectado: cenários manuais, evidências visuais e BFF real pendentes.
- As três partes foram compiladas separadamente; o smoke test combinado aguarda os merges feitos pelo usuário.
- Arquivos exatos: os 8 caminhos listados na seção Arquivos acima; os mesmos aparecem nos comandos abaixo.
- Nenhum git add, commit, push, merge ou criação de PR foi executado.

### Comandos de staging — executar somente neste worktree

```powershell
Set-Location -LiteralPath "C:\Users\gustavopm-ieg\Frigus repo\frigus-mobile-g02-2"

git add -- "app/src/main/java/com/example/frigus_mobile/ui/home/HomeFragment.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/home/MainActivity.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/login/LoginActivity.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/stock/EstoqueFragment.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/stock/InventoryViewModel.kt"
git add -- "app/src/main/res/layout/fragment_estoque.xml"
git add -- "app/src/main/res/values/navigation_strings.xml"
git add -- "docs/g02-2-navigation-shortcuts.md"
```

### Mensagem de commit

```text
feat(navigation): preserve inventory shortcut state
```

### Título da PR

`G02-2: preserve inventory filters and shortcut navigation`

### Descrição da PR — template preenchido

# 🚀 Pull Request

## 📌 Contexto

> G02-2: Atalhos por local, consulta geral com aviso de todos os status e preservação de filtros, páginas e rolagem. Restauração passa por M03 e descarta o contexto na troca de conta.
>
> Base: dev@17ce2b55ddc8ebc534e5beb82e3e889732c6cd7c.
> Dependências necessárias já incorporadas nessa base. Esta PR pode ser validada individualmente e não depende das outras duas branches paralelas.
> A integração recomendada é G02-2 → G05-1 → G09; reconciliar as alterações compartilhadas de MainActivity ao atualizar a base.

## 🛠️ O que foi feito?

- [x] Nova tela ou funcionalidade adicionada
- [x] Correção de bug / crash
- [x] Refatoração de arquitetura (MVVM / Clean Architecture)
- [ ] Atualização de dependências ou configurações do Gradle / CI

Atalhos por local, consulta geral com aviso de todos os status e preservação de filtros, páginas e rolagem. Restauração passa por M03 e descarta o contexto na troca de conta.

Nenhum endpoint, dependência ou persistência fictícia foi adicionado. As funcionalidades fora desta parte permanecem indisponíveis.

## 🧪 Como validar?

1. Executar ` .\gradlew.bat assembleDebug lintDebug assembleDebugAndroidTest ` no worktree desta branch.
   Os três checks passaram; testes instrumentados somente compilados/empacotados.
   Unit tests não foram criados nem executados, conforme solicitado.
2. Testar no emulador ou dispositivo físico com BFF acessível:
   - Abrir atalhos de Geladeira, Freezer e Despensa após aplicar outros filtros; conferir o local e a limpeza de busca/categoria/estoque.
   - Abrir alertas e conferir o aviso de todos os status, sem filtro remoto inventado.
   - Trocar abas, rotacionar, recriar o processo e validar M03; conferir restauração e descarte na troca de conta.
3. Seguir os demais cenários de `docs/g02-2-navigation-shortcuts.md`.

Lint: zero erros e 669 avisos no projeto. Validação manual/visual e com BFF real pendente, sem dispositivo conectado.
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
