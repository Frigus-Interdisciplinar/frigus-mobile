# G09 — Conta e segurança

## Base e escopo

Branch: `feat/mobile-account-security`.
Base: `dev@17ce2b55ddc8ebc534e5beb82e3e889732c6cd7c`.
Entrega independente de G02-2/G05-1; único contrato consumido é M03, GET /mobile/user/profile, com Bearer.
Não há novos endpoints, dependências, persistência de domínio ou alterações na política HTTP.

## Comportamento

- Perfil em página única, somente leitura: nome, e-mail, nascimento e tipo de conta.
- Carregamento, vazio/inválido, erro de rede, 403 e nova tentativa são locais. Não há NetworkGuard bloqueante.
- Campo ausente é identificado como não informado; datas inválidas não recebem um valor artificial.
- O avatar é um ícone neutro e não simula foto real.
- A tela não apresenta dados do cache da sessão como uma resposta nova: campos ficam ocultos até M03 válido.
- A atualização é explícita. ViewModel da Activity preserva resposta em rotação/retorno à aba, sem carga em onResume.
- Recriar o processo exige a restauração validada de G01 e uma nova consulta M03.
- Não há navegação para MeusDadosFragment/SegurancaFragment ou outros fluxos legados fictícios.
- Edição, senha, recuperação, Google, 2FA e dispositivos são identificados como indisponíveis.
- Família, assinatura, pagamentos, preferências e economia não são habilitados nesta parte.

## Sessão e erros

ProfileRepository captura token e geração, cancela a requisição ao descartar a ViewModel e ignora respostas de outra credencial.
Exige HTTP 200, corpo presente e ID UUID canônico. Preserva ApiError com status, code, displayMessage e fields.
Usa a operação atômica SessionState.validate com a credencial capturada antes de persistir; não usa saveUserProfile sem guarda.
Falha de persistência local recebe erro genérico, sem exibir dados técnicos ou credenciais.
401 usa o encerramento de sessão existente; 403 preserva a sessão. Não há refresh nem logout remoto.

Após M03 validado, PerfilFragment chama MainActivity.refreshSessionIdentity para atualizar o cabeçalho.
O logout da página pede confirmação e chama MainActivity.signOut, compartilhado com o cabeçalho.
Esse fluxo limpa a sessão e a pilha e retorna ao login.

## Contratos futuros (propostas sem paths ou integração)

| Função | Request/response a definir com BFF |
| --- | --- |
| Alterar perfil | Campos editáveis, permissões, validação e perfil confirmado |
| Senha e recuperação | Verificação exigida, entrada, resultado, expiração, erros e efeito nas sessões |
| Google | Identidade aceita, validação e resultado de autenticação |
| 2FA | Cadastro, confirmação, consulta do estado, recuperação e desativação autorizada |
| Dispositivos | IDs, paginação, permissões e confirmação de revogação |

## Validação

assembleDebug, lintDebug e assembleDebugAndroidTest: passaram. Lint sem erros e 673 avisos; testes instrumentados apenas compilados.
Unit tests não são criados nem executados, conforme solicitado.
Dispositivo e BFF real: validar quando disponíveis; não considerar a compilação como teste visual ou de integração.

Cenários:
1. Conferir uma chamada M03 ao abrir Perfil, dados reais e atualização do cabeçalho.
2. Conferir campos nulos, data inválida, nomes longos, fontes ampliadas e rolagem no tablet.
3. Simular rede/timeout, resposta não JSON, corpo vazio e UUID inválido; repetir manualmente.
4. Verificar 403 sem apagar sessão e 401 retornando ao login, sem acesso pelo botão Voltar.
5. Rotacionar durante/depois da consulta e trocar de abas sem carga duplicada.
6. Atrasar M03, sair e entrar com outra conta; a resposta anterior não pode alterar perfil ou cabeçalho.
7. Cancelar a saída e manter a sessão; confirmar e limpar sessão/pilha.
8. Confirmar ausência de ações fictícias de 2FA, senha, dispositivos, planos ou preferências.

## Arquivos

- app/src/main/java/com/example/frigus_mobile/data/repository/ProfileRepository.kt
- app/src/main/java/com/example/frigus_mobile/ui/home/MainActivity.kt
- app/src/main/java/com/example/frigus_mobile/ui/profile/PerfilFragment.kt
- app/src/main/java/com/example/frigus_mobile/ui/profile/ProfileViewModel.kt
- app/src/main/res/layout/fragment_perfil.xml
- app/src/main/res/values/account_dimens.xml
- app/src/main/res/values/account_strings.xml
- docs/g09-account-security.md


## Entrega para commit e PR

### Branch, base e worktree

- Branch: `feat/mobile-account-security`.
- Base: `dev@17ce2b55ddc8ebc534e5beb82e3e889732c6cd7c`.
- Worktree: `C:\Users\gustavopm-ieg\Frigus repo\frigus-mobile-g09`.
- Escopo e decisões: Perfil M03 somente leitura, atualização protegida por token e geração, identidade atualizada e logout local compartilhado. Operações de conta e segurança sem contrato permanecem indisponíveis.

### Checks executados e riscos

- assembleDebug: passou.
- lintDebug: passou, zero erros e 673 avisos.
- assembleDebugAndroidTest: passou; somente compilação/empacotamento.
- git diff --check: passou; índice vazio.
- Unit tests não criados nem executados.
- Sem dispositivo conectado: cenários manuais, evidências visuais e BFF real pendentes.
- As três partes foram compiladas separadamente; o smoke test combinado aguarda os merges feitos pelo usuário.
- Arquivos exatos: os 8 caminhos listados na seção Arquivos acima; os mesmos aparecem nos comandos abaixo.
- Nenhum git add, commit, push, merge ou criação de PR foi executado.

### Comandos de staging — executar somente neste worktree

```powershell
Set-Location -LiteralPath "C:\Users\gustavopm-ieg\Frigus repo\frigus-mobile-g09"

git add -- "app/src/main/java/com/example/frigus_mobile/ui/home/MainActivity.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/profile/PerfilFragment.kt"
git add -- "app/src/main/res/layout/fragment_perfil.xml"
git add -- "app/src/main/java/com/example/frigus_mobile/data/repository/ProfileRepository.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/profile/ProfileViewModel.kt"
git add -- "app/src/main/res/values/account_dimens.xml"
git add -- "app/src/main/res/values/account_strings.xml"
git add -- "docs/g09-account-security.md"
```

### Mensagem de commit

```text
feat(account): add read-only mobile profile
```

### Título da PR

`G09: add read-only account and local logout`

### Descrição da PR — template preenchido

# 🚀 Pull Request

## 📌 Contexto

> G09: Perfil M03 somente leitura, atualização protegida por token e geração, identidade atualizada e logout local compartilhado. Operações de conta e segurança sem contrato permanecem indisponíveis.
>
> Base: dev@17ce2b55ddc8ebc534e5beb82e3e889732c6cd7c.
> Dependências necessárias já incorporadas nessa base. Esta PR pode ser validada individualmente e não depende das outras duas branches paralelas.
> A integração recomendada é G02-2 → G05-1 → G09; reconciliar as alterações compartilhadas de MainActivity ao atualizar a base.

## 🛠️ O que foi feito?

- [x] Nova tela ou funcionalidade adicionada
- [x] Correção de bug / crash
- [x] Refatoração de arquitetura (MVVM / Clean Architecture)
- [ ] Atualização de dependências ou configurações do Gradle / CI

Perfil M03 somente leitura, atualização protegida por token e geração, identidade atualizada e logout local compartilhado. Operações de conta e segurança sem contrato permanecem indisponíveis.

Nenhum endpoint, dependência ou persistência fictícia foi adicionado. As funcionalidades fora desta parte permanecem indisponíveis.

## 🧪 Como validar?

1. Executar ` .\gradlew.bat assembleDebug lintDebug assembleDebugAndroidTest ` no worktree desta branch.
   Os três checks passaram; testes instrumentados somente compilados/empacotados.
   Unit tests não foram criados nem executados, conforme solicitado.
2. Testar no emulador ou dispositivo físico com BFF acessível:
   - Abrir Perfil, conferir os dados M03, campos opcionais e atualização da identidade no cabeçalho.
   - Testar erro de rede, 403 mantendo a sessão e 401 retornando ao login.
   - Atrasar M03, sair e entrar com outra conta; conferir descarte da resposta anterior e impossibilidade de voltar à área autenticada após logout.
3. Seguir os demais cenários de `docs/g09-account-security.md`.

Lint: zero erros e 673 avisos no projeto. Validação manual/visual e com BFF real pendente, sem dispositivo conectado.
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
