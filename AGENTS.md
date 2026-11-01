# AGENTS.md — Guia de Desenvolvimento e Instruções para Agentes no Frigus Mobile

Este documento serve como diretriz técnica, arquitetural e operacional para agentes de IA e desenvolvedores que atuam no repositório `frigus-mobile`. Todas as modificações, implementações e correções devem seguir rigorosamente os princípios estabelecidos abaixo.

---

## 1. Visão Geral do Projeto

- **Nome do Projeto:** `frigus-mobile`
- **Domínio:** Aplicativo Android corporativo/doméstico para gestão inteligente de alimentos, controle de estoque (geladeira, freezer e despensa), acompanhamento de validades e lotes, importação de notas fiscais via NFC-e (QR Code), descoberta e preparo de receitas culinárias, notificações push e gestão de assinaturas/família.
- **Público e Dispositivos Alvo:** Smartphones e tablets com Android 11+ (`minSdk = 30`, `compileSdk = 36`, `targetSdk = 36`).
- **Backend Oficial:** BFF Mobile (`frigus-bff`) acessado via HTTPS. O aplicativo consome estritamente as rotas publicadas e aprovadas para o cliente mobile.

---

## 2. Stack Tecnológica e Ferramental

- **Linguagem:** Kotlin 2.1.0 (Compatibilidade com bytecode Java 11).
- **Build System:** Gradle 9.x com Android Gradle Plugin (AGP) 9.0.1 e Version Catalog (`gradle/libs.versions.toml`).
- **Arquitetura:** MVVM (Model-View-ViewModel) + Repository Pattern com separação estrita de camadas.
- **UI Toolkit:** Android Views tradicional com layouts XML baseados em `ConstraintLayout`, Material Components 3 (`com.google.android.material:material:1.14.0`), AppCompat 1.8.0 e AndroidX Core Splashscreen.
- **Comunicação de Rede:** Retrofit 2.11.0 + OkHttp 3 + Gson Converter 2.11.0.
- **Criptografia e Sessão Local:** AndroidKeyStore (AES-GCM 128-bit) integrado a `SharedPreferences` privado (`SessionManager` e `SessionState`).
- **Serviços Google / Push:** Firebase Messaging (BOM 35.1.0) e Google Play Code Scanner ML Kit (`play-services-code-scanner:16.1.0`).
- **Testes:** JUnit 4, AndroidX Test Ext JUnit e Espresso Core.

---

## 3. Estrutura do Código e Organização de Pastas

```text
app/src/
├── main/
│   ├── AndroidManifest.xml          # Configuração de componentes, permissões e serviços
│   ├── java/com/example/frigus_mobile/
│   │   ├── FrigusApplication.kt      # Inicialização de canais de notificação e app
│   │   ├── data/
│   │   │   ├── api/                  # Interfaces Retrofit, RetrofitClient, MobileHttp e BffCalls
│   │   │   ├── local/                # SessionManager, SessionState e SessionCredential
│   │   │   ├── model/                # DTOs, modelos de dados, enums e ApiError
│   │   │   └── repository/           # Repositórios por domínio (Auth, Stock, Recipe, Profile, etc.)
│   │   ├── ui/                       # Telas e apresentadores agrupados por fluxo de negócio
│   │   │   ├── home/                 # MainActivity, HomeFragment, HomeViewModel
│   │   │   ├── login/                # LoginActivity, LoginViewModel
│   │   │   ├── createaccount/        # Cadastro de usuários
│   │   │   ├── forgotpassword/       # Recuperação de senha
│   │   │   ├── stock/                # Estoque, lotes, detalhes e edição de alimentos
│   │   │   ├── recipes/              # Catálogo de receitas, sugestões e modo de preparo
│   │   │   ├── profile/              # Perfil, assinaturas, planos e suporte
│   │   │   ├── qrcode/               # Scanner de NFC-e, itens identificados e confirmação
│   │   │   ├── notifications/        # Visualização e leitura de notificações
│   │   │   └── ai/                   # Assistente inteligente e sugestões por IA
│   │   ├── push/                     # Integração com FCM (FrigusMessagingService)
│   │   └── utils/                    # Guardas de rede, validações e formatadores
│   └── res/
│       ├── layout/                   # Layouts XML desacoplados por tela/componente
│       └── values/                   # Strings, cores, estilos e dimensões segmentados
└── test/                             # Testes unitários JVM (BffErrorTest, MobileBffContractTest, etc.)
```

---

## 4. Diretrizes Arquiteturais e Padrões de Código

### 4.1. Camada de Apresentação (MVVM)
- **Papel das Views (Activities/Fragments):** As Views devem ser estritamente passivas. Apenas configuram listeners, observam `LiveData` expostos pela ViewModel e renderizam os estados visuais.
- **Papel das ViewModels:** Centralizam lógica de apresentação, orquestram chamadas aos Repositórios, mantêm estado de consultas, filtros e paginação, além de sobreviver a rotações de tela e transições de abas.
- **Preservação de Estado:** Estados de filtros, rolagem e páginas carregadas devem ser mantidos na ViewModel da Activity hospedeira (`MainActivity`). Ao recriar processos, nunca persista tokens de autenticação ou dados sensíveis em `Bundle`/`Intent`.

### 4.2. Camada de Dados (Repository & API)
- **Repositórios:** Toda chamada de rede deve passar por uma classe Repository (`StockRepository`, `RecipeRepository`, `AuthRepository`, etc.). Nunca invoque serviços Retrofit diretamente da UI.
- **Gerenciamento de Erros com `ApiError`:** Toda resposta com falha deve ser interceptada e convertida via `ApiError.parse(response)`. A interface deve exibir `displayMessage` ou mensagem genérica de fallback amigável ao usuário.
- **Proteção Contra Erros Técnicos:** Jamais exiba payloads brutos de JSON, stack traces técnicos, senhas ou tokens na UI do aplicativo.

### 4.3. Internacionalização e Recursos Android
- **Sem Strings Hardcoded:** É expressamente proibido inserir texto literal diretamente em arquivos `.kt` ou atributos `android:text` dos layouts XML. Use sempre recursos em `res/values/*strings.xml`.
- **Organização Modular de Strings:** Utilize os arquivos temáticos correspondentes:
  - `auth_strings.xml`: Autenticação e cadastro
  - `inventory_strings.xml` / `lot_strings.xml`: Estoque, produtos e lotes
  - `recipe_browser_strings.xml`: Receitas e sugestões culinárias
  - `account_strings.xml`: Perfil, conta e segurança
  - `receipt_strings.xml`: Leitura de notas e QR Code
- **Cores e Dimensões:** Reutilize cores centralizadas em `colors.xml` e dimensões padronizadas em `dimens.xml` para garantir consistência visual no smartphone e no tablet.

---

## 5. Regras Mandatórias de Integração com o BFF Mobile

### 5.1. Origem e Whitelist Estrita de Rotas (`MobileHttp.kt`)
- A URL base do BFF é configurada via `.env` (`FRIGUS_BFF_BASE_URL=https://.../`) ou variável de ambiente com precedência. A URL deve conter apenas a origem com `/` final.
- O aplicativo possui uma **whitelist estrita de rotas** em `MobileHttp.kt`. Qualquer tentativa de requisição para caminhos não autorizados é bloqueada antes de sair do dispositivo.
- Se uma funcionalidade ainda não possui contrato publicado no BFF, utilize `unavailableMobileContract()` ou marque a funcionalidade visualmente como indisponível.

### 5.2. Proibição Absoluta de Mock Data em Fluxos de Produção
- **Não invente dados fictícios:** Nunca simule respostas bem-sucedidas com dados inventados ou "falsos positivos" para contornar a ausência de backend.
- Recursos pendentes ou sem suporte na API do BFF devem ser explicitamente identificados como indisponíveis (`FeatureUnavailableFragment`, avisos claros na UI).

### 5.3. Política de Mutações e Reconciliação
- **Sem Replay Automático:** Operações de mutação (`POST`, `PUT`, `DELETE`) nunca devem possuir retentativa automática silenciosa em caso de timeout ou falha de transporte (`408`/`5xx`). Isso previne criação duplicada de estoques, ajuste duplicado de saldo ou exclusões acidentais.
- **Resultado Incerto:** Quando uma mutação não recebe confirmação definitiva, a interface deve alertar sobre o "resultado incerto" e exigir que o usuário recarregue explicitamente o recurso via `GET` antes de permitir qualquer nova tentativa.
- **Sucesso em 204 No Content:** Requisições de deleção ou comandos sem corpo de retorno (ex: M09 DELETE de lote) devem ser validadas estritamente com `requireNoContent()`, verificando `code() == 204`.
- **Diferenciação 401 vs 403:**
  - `HTTP 401 Unauthorized`: Sessão expirada ou inválida. Limpa a sessão local no `SessionManager` e redireciona ao login com a pilha de navegação limpa.
  - `HTTP 403 Forbidden`: Sessão válida, porém sem permissão ou plano insuficiente. A sessão **não** é destruída; a interface deve apenas exibir a mensagem de restrição de acesso.
- **Controle de Geração de Sessão (`SessionState.generation`):** Chamadas assíncronas em andamento quando ocorre logout ou troca de conta devem ser descartadas se a geração da credencial não corresponder à sessão ativa.

---

## 6. Comandos de Build, Teste e Verificação

Todos os comandos devem ser executados a partir da raiz do projeto.

### 6.1. Compilação e Montagem do APK
```bash
# Windows
.\gradlew.bat assembleDebug

# Linux / macOS
./gradlew assembleDebug
```

### 6.2. Testes Unitários
```bash
# Windows
.\gradlew.bat testDebugUnitTest

# Linux / macOS
./gradlew testDebugUnitTest
```

### 6.3. Verificação Estática de Código (Lint)
```bash
# Windows
.\gradlew.bat lintDebug

# Linux / macOS
./gradlew lintDebug
```

### 6.4. Compilação dos Testes Instrumentados
```bash
# Windows
.\gradlew.bat assembleDebugAndroidTest

# Linux / macOS
./gradlew assembleDebugAndroidTest
```

> [!NOTE]
> **Dica para ambiente Windows:** Caso o Gradle falhe com erro de socket loopback local (`Unable to establish loopback connection`), configure no terminal atual:
> ```powershell
> $env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\Users\<usuario>\AppData\Local\Temp\frigus-no-unix-sockets'
> ```

---

## 7. Guardrails e Instruções Específicas para Agentes de IA

1. **NÃO VERSIONAR SEM ORDEM EXPRESSA:**
   - O agente **não deve** executar `git add`, `git commit`, `git push` ou criação de PR a menos que o usuário solicite explicitamente.
2. **MODIFICAÇÃO DE ARQUIVOS:**
   - Use exclusivamente ferramentas dedicadas de edição da IDE (`write_file`, `replace_file_content`, `multi_replace_file_content`).
   - É estritamente proibido utilizar comandos de shell (`sed`, `echo >`, `cat << EOF`) para alterar ou criar arquivos de código.
3. **PRESERVAÇÃO DE CÓDIGO E REAPROVEITAMENTO:**
   - Respeite o trabalho já validado nas branches e documentos em `docs/` (`g02-2`, `g03-2`, `g05-1`, `g09`, `pr29`).
   - Não reescreva módulos inteiros se apenas uma alteração cirúrgica for solicitada.
   - Não reintroduza componentes legados descartados nem delete rotas mapeadas em `MobileHttp.kt`.
4. **PADRÃO DE PULL REQUEST:**
   - Toda alteração proposta para entrega deve preencher os critérios estipulados em `.github/pull_request_template.md`:
     - Compilação limpa (`assembleDebug`)
     - Testes unitários passando (`testDebugUnitTest`)
     - MVVM preservado
     - Recursos centralizados em XML
     - Roteiro de validação manual claro com o BFF
