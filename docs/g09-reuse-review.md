# G09 — correção de reaproveitamento

## Branch e base

- Branch: `fix/mobile-profile-ui-reuse`.
- Base: `dev@1e3a67e99dd3393a860628bc9132a52fea61e5fd`.
- Worktree: `C:\Users\gustavopm-ieg\Frigus repo\frigus-mobile-g09`.
- Correção independente da PR #22 e das outras branches desta rodada.
- Sem staging, commit, push ou criação de PR.

## Contexto

M03, identidade e logout já existiam antes de ff3b09e. A ViewModel e as validações de geração são melhorias reais; não representam a primeira implementação dessas operações. O layout anterior possuía identidade centralizada e uma ação de saída reutilizáveis.

## Alterações

- Título, avatar genérico, nome/e-mail centralizados e linha original de saída recuperados.
- Campos de nascimento/tipo de conta e avisos de indisponibilidade mantidos em cartões do desenho existente.
- IDs mantidos para que PerfilFragment/ProfileViewModel/ProfileRepository continuem responsáveis por M03, carregamento, erros e atualização da identidade.
- Saída continua usando confirmação e MainActivity.signOut, com limpeza de sessão/pilha.
- Nenhuma operação de conta, segurança, plano, família, preferências ou economia sem contrato foi reativada. Nenhum código de rede foi duplicado.

## Validação

- assembleDebug: passou.
- lintDebug: passou, zero erros e 675 avisos.
- assembleDebugAndroidTest: passou; testes instrumentados apenas compilados/empacotados.
- git diff --check, XML e vínculos PerfilFragment/layout: conferidos.
- Unit tests não criados nem executados. Sem dispositivo conectado; validação visual/BFF ainda pendente.
- Nenhuma configuração versionada foi alterada para o build.

## Comando usado no ambiente local

O Java falhava ao abrir o canal local do Gradle. O fallback foi aplicado somente ao processo, sem alteração de configuração versionada:

```powershell
$env:JAVA_HOME = "C:\Users\gustavopm-ieg\AppData\Local\Programs\Android Studio\jbr"
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=Z:\frigus-gradle-sockets'
.\gradlew.bat assembleDebug lintDebug assembleDebugAndroidTest --offline --no-daemon
```

O diretório de sockets inexistente faz o Java usar TCP em vez do mecanismo local que falhou. Não adicionar esse parâmetro ao Gradle versionado.

## Cenários manuais e riscos

1. Abrir Perfil: conferir identidade real, nascimento/tipo e campos ausentes.
2. Atualizar: carregamento, erro e nova tentativa continuam funcionando.
3. Confirmar/cancelar saída pela linha restaurada; conferir login e pilha limpa.
4. 403 preserva sessão; 401 encerra; resposta antiga não altera outra sessão.
5. Conferir fontes ampliadas, TalkBack e avisos de operações indisponíveis.

A recuperação visual precisa ser conferida em dispositivo/emulador. As integrações e seus contratos permanecem os já presentes na dev; não é necessário novo contrato do backend para esta correção.

## Arquivos exatos

- `app/src/main/res/layout/fragment_perfil.xml`
- `docs/g09-reuse-review.md`

## Comandos para o usuário

```powershell
Set-Location -LiteralPath "C:\Users\gustavopm-ieg\Frigus repo\frigus-mobile-g09"
git add -- "app/src/main/res/layout/fragment_perfil.xml"
git add -- "docs/g09-reuse-review.md"
git commit -m "fix(profile): restore existing profile presentation"
```

## Título sugerido da PR

fix(profile): restore existing profile presentation

## Descrição da PR

# 🚀 Pull Request

## 📌 Contexto

> M03, identidade e logout já existiam antes de ff3b09e. A ViewModel e as validações de geração são melhorias reais; não representam a primeira implementação dessas operações. O layout anterior possuía identidade centralizada e uma ação de saída reutilizáveis.
> Base: dev@1e3a67e; independente da correção de estoque e da outra correção visual.

## 🛠️ O que foi feito?

- [ ] Nova tela ou funcionalidade adicionada
- [x] Correção de bug / crash
- [ ] Refatoração de arquitetura (MVVM / Clean Architecture)
- [ ] Atualização de dependências ou configurações do Gradle / CI

- Título, avatar genérico, nome/e-mail centralizados e linha original de saída recuperados.
- Campos de nascimento/tipo de conta e avisos de indisponibilidade mantidos em cartões do desenho existente.
- IDs mantidos para que PerfilFragment/ProfileViewModel/ProfileRepository continuem responsáveis por M03, carregamento, erros e atualização da identidade.
- Saída continua usando confirmação e MainActivity.signOut, com limpeza de sessão/pilha.
- Nenhuma operação de conta, segurança, plano, família, preferências ou economia sem contrato foi reativada. Nenhum código de rede foi duplicado.

## 🧪 Como validar?

1. Executar `.\gradlew.bat assembleDebug lintDebug assembleDebugAndroidTest`.
2. Testar no emulador ou dispositivo físico:
   - Abrir Perfil: conferir identidade real, nascimento/tipo e campos ausentes.
   - Atualizar: carregamento, erro e nova tentativa continuam funcionando.
   - Confirmar/cancelar saída pela linha restaurada; conferir login e pilha limpa.
   - 403 preserva sessão; 401 encerra; resposta antiga não altera outra sessão.
   - Conferir fontes ampliadas, TalkBack e avisos de operações indisponíveis.

Unit tests dispensados pelo usuário; testes instrumentados somente compilados/empacotados. Resultado dos checks: assembleDebug, lintDebug (zero erros, 675 avisos) e assembleDebugAndroidTest aprovados.

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
