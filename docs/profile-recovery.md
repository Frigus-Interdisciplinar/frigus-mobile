# Perfil, foto Google e preferências recuperadas

## Comparação com 93933ee

O menu e o layout principal de Perfil permanecem os recuperados de `93933ee`: avatar, identidade, cartão do plano, assinatura, configurações, ajuda, MoneySaving e saída. Dados pessoais continuam em M03/ProfileViewModel e o cartão do plano em SubscriptionViewModel; não há usuário ou plano de fallback inventado.

A versão 93933ee baixava a foto diretamente na View e mantinha um Bitmap estático global. Agora a View observa ProfileAvatarViewModel, que usa ProfileAvatarRepository. A foto vem da identidade Google/perfil da conta ativa; o fallback do SDK só é aceito quando o e-mail corresponde ao usuário BFF. A URL deve ser HTTPS em `googleusercontent.com`/`ggpht.com` ou seus subdomínios. O cliente de mídia não usa autenticação, cookies, redirects ou replay automático. Apenas imagens JPEG/PNG/WebP de até 2 MiB são aceitas, com decodificação limitada. Esse carregamento de imagem pública foi solicitado expressamente pelo usuário; as APIs do aplicativo continuam exclusivamente no BFF.

Cache em disco usa SHA-256 de usuário e URL. O estado em memória e os resultados assíncronos conferem usuário e credencial/generation. Logout/troca de conta impedem publicação de foto antiga. Ausência ou falha da imagem usa o ícone genérico do layout; não há outro usuário de exemplo. O SessionManager/Login são tratados separadamente pelo agente principal para salvar a URL apenas depois do login confirmado e associá-la ao usuário.

## Configurações

Conforme solicitado, a seção **Segurança** foi removida do XML e dos listeners: autenticação em duas etapas e dispositivos conectados. Geral e Meus dados permanecem, incluindo os atalhos existentes de preferências, unidades, privacidade, senha e informações da conta.

## Preferências alimentares

As nove escolhas de 93933ee já usavam chaves `food_*`, mas tinham escrita local misturada com listeners Firestore sem controle de ciclo de vida. ProfilePreferenceStore agora mantém as mesmas chaves `usuario.food_*` no SharedPreferences privado `profile_local`; isso preserva as escolhas locais existentes da conta. Não há fallback `anon` nem chamadas Firestore.

FoodPreferencesViewModel/Repository fazem a persistência local durável com `commit`, refletem falha de gravação e impedem instâncias de outra conta/generation de ler ou sobrescrever dados. No caso de falha de disco, a escolha em memória é revertida. Os controles da tela original observam esse estado, sem lógica de rede na View.

`FoodPreferencesRepository.snapshot()` lê as escolhas da conta ativa; `promptContext()` produz contexto alimentar localizado para o assistente. O agente responsável pelo chat integra esse contexto ao request, mantendo a mensagem do balão original. O catálogo de receitas não publica parâmetros dietéticos e não é filtrado por metadados visuais/defaults. O texto da tela informa o armazenamento local e o uso no assistente, sem prometer sincronização de conta ou garantia de restrições alimentares.

## Verificação

Foram adicionados testes unitários de isolamento/persistência do store (reabertura, contas diferentes, logout/generation e falha de commit) e de validação das URLs/chaves de cache da foto. Os testes usam armazenamento em memória e não limpam credenciais reais ou dados do telefone. A execução conjunta do Gradle cabe ao agente principal; esta tarefa não executou Gradle.

Roteiro manual:

1. Entrar pelo Google, abrir Perfil e conferir a foto; sair durante uma carga lenta e entrar em outra conta para confirmar ausência de foto antiga.
2. Reabrir Perfil e conferir cache; sem foto ou offline sem cache, conferir ícone genérico.
3. Abrir Configurações e conferir ausência da seção Segurança, preservando Geral e Meus dados.
4. Alterar preferências, sair e reabrir a tela, reiniciar o processo e confirmar os valores da mesma conta.
5. Entrar em outra conta e conferir escolhas isoladas; retornar à primeira e conferir persistência.
6. Solicitar uma receita ao assistente e conferir que o request recebe o contexto alimentar enquanto o balão mantém o texto digitado.

Nenhum commit, push, alteração de contrato backend ou envio externo de mensagem foi realizado nesta tarefa.
