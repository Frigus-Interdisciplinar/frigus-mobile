# Revisão de reaproveitamento — PR #22

## Estado e escopo

- Branch: feat/mobile-stock-lot-management.
- Base da PR: dev@1e3a67e99dd3393a860628bc9132a52fea61e5fd.
- Commit anterior à correção: cab286cc62070343cd8c15bcdeee28b6995b1cd0.
- Dev local e remota conferidas sem divergência nesta rodada.
- Correção dos casos identificados na auditoria da PR #22, sem iniciar G03-3 ou outras funções pendentes.
- Sem staging, commit, push ou alteração da PR remota.

## Código anterior conectado ao atual

| Peça existente | Conexão/correção |
| --- | --- |
| Layout do detalhe | Mantidos cabeçalho, categoria ilustrada por ícone, chips, cartão de quantidade, tabela e botões; bind dos dados da StockLotViewModel nesses mesmos componentes. |
| Layout de edição | Mantidos campos, seletores e botão fixo de salvar; campos iniciais/mínimo/batch acrescentados no formulário. |
| Atualização/removal M07–M09 | Mesmas rotas/DTO; mantidos cancelamento, validação e sessão da PR; nenhuma API paralela. |
| Validador de 204 | Reutilizado pela execução síncrona e pela resposta cancelável. |
| Normalização de data | Mesmo ponto de normalização no repositório, agora estrito; dd/MM/aaaa e ISO aceitos, apenas ISO enviado. |
| Ícones e unidades | Uma apresentação compartilhada por lista/detalhe/edição; sem imagens atribuídas a produtos. |
| Feedback após sucesso | Toasts de edição/remoção restaurados, emitidos somente após confirmação. |
| Retorno à lista/Home | Callback centralizado na Main; registro salvo/restaurado; atualização somente após mudança ou necessidade de reconciliação. |
| Operação rejeitada | Marcador de alteração não é ativado antes do request; 400/403 definitivos não reiniciam páginas/rolagem. |
| Resultado incerto/saída pendente | Mantém consulta de reconciliação e invalidação ao voltar; não repete mutações. |
| Recursos | Rótulos anteriores reaproveitados em vez das duplicações criadas na PR. |

## Conferência das outras entregas

Foram inspecionados os commits de navegação (3295555), receitas (96f86c5) e perfil (ff3b09e), comparando seus predecessores e a dev integrada.

- G02-2 já preservava consulta/páginas/rolagem pela InventoryViewModel. A correção conecta o detalhe a essa navegação em vez de manter uma marca que causava atualização incondicional no retorno. A InventoryViewModel volta a não carregar esse marcador adicional.
- G05-1: telas de receita, ingredientes e preparo existiam. A consulta de detalhe por ID e os estados independentes são melhorias da entrega, não criação de todas as telas. Fotos por posição, avaliação/duração/dificuldade inventadas e favoritos locais não podem ser recuperados como integração real. As classes de tela, adapters e BottomSheet existentes continuam sendo usados na dev. Os componentes visuais compatíveis foram recuperados em `fix/mobile-recipe-ui-reuse`, baseada na mesma dev, sem misturar os arquivos à PR #22. O detalhe também religa seu painel anterior de instruções ao mesmo conteúdo real do BottomSheet.
- G09: consulta M03, identidade e logout existiam. A ViewModel e as verificações de token/geração protegem esse fluxo; não são a primeira implementação de perfil/logout. As antigas rotas de edição, segurança, planos e economia não podem ser religadas sem contrato. A identidade centralizada, as superfícies e a ação de saída anteriores foram recuperadas em `fix/mobile-profile-ui-reuse`, baseada na mesma dev, mantendo os IDs esperados pela ViewModel/Fragment atuais e sem misturar os arquivos à PR #22.

Esta conferência não prova comportamento histórico contra um BFF em produção. Ela identifica chamadas e componentes existentes no código e as fronteiras contratuais; não habilita operações ausentes.

## Validação da correção

- assembleDebug: passou.
- lintDebug: passou, zero erros e 663 avisos.
- assembleDebugAndroidTest: passou; testes instrumentados somente compilados/empacotados.
- git diff --check e verificação dos vínculos Activities/layouts: passaram.
- Unit tests não criados nem executados, conforme solicitado.
- Sem dispositivo conectado para validar interface, rotação e BFF real.

O JDK falhava antes da compilação com Unable to establish loopback connection. A validação usou apenas no processo o fallback TCP do Java; nenhuma configuração do projeto foi alterada:

```powershell
$env:JAVA_HOME = "C:\Users\gustavopm-ieg\AppData\Local\Programs\Android Studio\jbr"
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=Z:\frigus-gradle-sockets'
.\gradlew.bat assembleDebug lintDebug assembleDebugAndroidTest --offline --no-daemon
```

O diretório de sockets inexistente força o fallback TCP em vez do mecanismo local que falhou nesta máquina; o parâmetro não deve ser adicionado ao Gradle versionado.

## Roteiro manual obrigatório

1. Abrir detalhe: conferir cabeçalho, ícone de categoria, chips, tabela e botões; inicial e mínimo distintos.
2. Quantidade inicial nula/zero: nenhum percentual inventado; quantidade zero continua válida.
3. Editar com teclado aberto: botão salvar permanece acessível; rolar até os novos campos.
4. Digitar 29/02/2028 e 2028-02-29: mesmo ISO no request; 31/02/2028 e 29/02/2027 rejeitados.
5. Não alterar campos: nenhuma mutação; trocar só o formato da mesma data: nenhuma alteração de validade enviada.
6. Confirmar edição/remoção: feedback somente após 200/204; atualizar lista e Home.
7. Retornar sem editar ou após 400/403: manter páginas e rolagem; não consultar novamente por marcação antecipada.
8. Sair com mutação pendente ou após timeout: reconciliar ao retornar e impedir replay.
9. Rotacionar Main em segundo plano com detalhe/edição abertos: retorno recebido uma vez pelo registro da Activity.
10. Logout/troca de sessão durante request: descartar resposta antiga e proteger a pilha.

## Arquivos exatos desta correção

Esta lista é relativa a cab286c e inclui a remoção de lot_dimens.xml e a retirada do marcador adicional da InventoryViewModel. O diff completo da PR tem também os arquivos já presentes no commit anterior.

- `app/src/main/java/com/example/frigus_mobile/data/api/BffCalls.kt`
- `app/src/main/java/com/example/frigus_mobile/data/repository/StockRepository.kt`
- `app/src/main/java/com/example/frigus_mobile/ui/home/MainActivity.kt`
- `app/src/main/java/com/example/frigus_mobile/ui/stock/AlimentoDetalhesActivity.kt`
- `app/src/main/java/com/example/frigus_mobile/ui/stock/EditarAlimentoActivity.kt`
- `app/src/main/java/com/example/frigus_mobile/ui/stock/EstoqueFragment.kt`
- `app/src/main/java/com/example/frigus_mobile/ui/stock/InventoryViewModel.kt`
- `app/src/main/java/com/example/frigus_mobile/ui/stock/StockLotViewModel.kt`
- `app/src/main/java/com/example/frigus_mobile/ui/stock/StockPresentation.kt`
- `app/src/main/java/com/example/frigus_mobile/ui/stock/adapter/EstoqueProdutoAdapter.kt`
- `app/src/main/res/layout/activity_alimento_detalhes.xml`
- `app/src/main/res/layout/activity_editar_alimento.xml`
- `app/src/main/res/values/lot_dimens.xml`
- `app/src/main/res/values/lot_strings.xml`
- `docs/g03-2-reuse-review.md`
- `docs/g03-2-stock-lot-management.md`

## Comandos para o usuário

```powershell
Set-Location -LiteralPath "C:\Users\gustavopm-ieg\Frigus repo\frigus-mobile-g03-2"
git add -- "app/src/main/java/com/example/frigus_mobile/data/api/BffCalls.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/data/repository/StockRepository.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/home/MainActivity.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/stock/AlimentoDetalhesActivity.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/stock/EditarAlimentoActivity.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/stock/EstoqueFragment.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/stock/InventoryViewModel.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/stock/StockLotViewModel.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/stock/StockPresentation.kt"
git add -- "app/src/main/java/com/example/frigus_mobile/ui/stock/adapter/EstoqueProdutoAdapter.kt"
git add -- "app/src/main/res/layout/activity_alimento_detalhes.xml"
git add -- "app/src/main/res/layout/activity_editar_alimento.xml"
git add -- "app/src/main/res/values/lot_dimens.xml"
git add -- "app/src/main/res/values/lot_strings.xml"
git add -- "docs/g03-2-reuse-review.md"
git add -- "docs/g03-2-stock-lot-management.md"
git commit -m "fix(inventory): preserve existing lot screens and navigation state"
```

## Título sugerido da PR #22

G03-2: reconnect existing stock lot screens and complete BFF editing

## Descrição da PR — template preenchido

# 🚀 Pull Request

## 📌 Contexto

> Detalhe, edição de quantidade/validade/local e remoção já estavam implementados com M07–M09. As telas haviam sido desabilitadas na G01 e o clique removido na G03-1. Esta PR recupera esses fluxos preservando a apresentação anterior e completa os campos e proteções realmente ausentes.
> Base: dev@1e3a67e99dd3393a860628bc9132a52fea61e5fd. G01/G02-2/G03-1 e camada BFF incorporadas. G03-3 permanece fora do escopo. Correções visuais de receitas/perfil são independentes.

## 🛠️ O que foi feito?

- [x] Nova tela ou funcionalidade adicionada: campos de edição ausentes, sem recriar as telas existentes
- [x] Correção de bug / crash
- [x] Refatoração de arquitetura (MVVM / Clean Architecture)
- [ ] Atualização de dependências ou configurações do Gradle / CI

- Cabeçalhos, ícones, chips, cartões, tabela e botão fixo de salvar anteriores reconectados à ViewModel.
- Nome/categoria efetivamente enviados; controles de quantidade inicial, mínimo e batch acrescentados; somente campos alterados enviados.
- Quantidade inicial real e campos ausentes tratados sem valores inventados.
- Datas dd/MM/aaaa ou ISO aceitas, com validação estrita e envio ISO pelo normalizador existente.
- Validação de 204 e apresentação de categorias/unidades compartilhadas; feedback após confirmação restaurado.
- Cancelamento, sessão, rascunhos e reconciliação de resultado incerto preservados, sem replay automático.
- Rejeição definitiva ou retorno sem alteração preservam páginas/rolagem; callback na Main sobrevive à recriação dos Fragments.
- Nenhum endpoint ou DTO novo, criação de entidade ou movimento adicional.

## 🧪 Como validar?

1. Executar `.\gradlew.bat assembleDebug lintDebug assembleDebugAndroidTest`.
2. Testar no emulador ou dispositivo físico:
   - Conferir apresentação recuperada, quantidades inicial/mínima, nulos e unidade somente leitura.
   - Editar um campo, saldo zero e datas equivalentes; conferir payload parcial e feedback após confirmação.
   - Rejeitar 31/02 e outros valores inválidos; conferir erros estruturados.
   - Cancelar/confirmar remoção e conferir 204 sem corpo.
   - Voltar sem editar ou após 400/403: manter páginas/rolagem; após alteração ou resultado incerto: reconciliar estoque/Home.
   - Rotacionar Main em segundo plano, sair durante mutação e trocar sessão durante resposta lenta.
3. Seguir o roteiro completo deste documento.

Unit tests dispensados; testes instrumentados somente compilados/empacotados. Resultado desta correção: assembleDebug, lintDebug (zero erros, 663 avisos) e assembleDebugAndroidTest aprovados. Validação visual e BFF real pendentes.

## 🔍 Checklist de Qualidade

- [x] **Auto-review:** Diff e vínculos entre Activities/layouts conferidos; sem logs novos.
- [x] **Compilação & Testes:** Build, lint e empacotamento dos testes instrumentados aprovados; unit tests dispensados e testes em dispositivo pendentes.
- [x] **Arquitetura (MVVM):** Estado e operações na ViewModel/Repository; View reage aos estados.
- [x] **Recursos Android:** Rótulos e dimensões anteriores reaproveitados, duplicações removidas.
- [ ] **Dispositivo/BFF:** Executar roteiro manual com lotes de teste.

---
### 📸 Evidências Visuais

<details>
<summary>Clique para expandir (Screenshots / Vídeo)</summary>

Pendentes de captura em emulador ou dispositivo físico.

</details>
