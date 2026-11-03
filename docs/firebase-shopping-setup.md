# Lista offline com sincronização Firebase

A lista é pessoal, salva primeiro em armazenamento local durável. Adicionar/editar/excluir/marcar não cadastra produto ou compra no Core. Ao recuperar conexão com o processo ativo, ou ao reabrir o app, a outbox sincroniza documentos idempotentes no Firestore em `users/{CoreUUID}/shopping_list/{itemUUID}`. Não há envio automático de produtos ao estoque. Fotos com URI local não são publicadas como se fossem acessíveis em outro aparelho.

O BFF agora publica `POST /mobile/firebase/session`: valida a sessão real com GET `/profile` no Core, usa exclusivamente o UUID retornado e cria um custom token Firebase. O app valida UID/projeto antes de autenticar no Firebase. Token Core ou senha não viram credenciais Firebase; chave administrativa nunca fica no app. Logout limpa a autenticação Firebase. Falta de configuração mantém os dados e a outbox locais, com sincronização pendente.

## Configuração necessária no ambiente do BFF

O app atual usa o projeto Firebase **frigusmobile** (`google-services.json`). Configurar no servidor:

- `FIREBASE_PROJECT_ID=frigusmobile`;
- `FIREBASE_SERVICE_ACCOUNT_JSON` com a conta de serviço desse mesmo projeto, armazenada no gerenciador de segredos do servidor; ou `GOOGLE_APPLICATION_CREDENTIALS` apontando para arquivo privado de credencial/ADC válido;
- em ambiente Google gerenciado, ADC também é aceito com a identidade de serviço e permissão de assinar tokens;
- Firebase Authentication e Firestore habilitados, com regras de acesso por UID publicadas. O arquivo `docs/firebase-shopping.rules` contém uma regra restrita para esta coleção; revisar e incorporar às regras existentes, sem apagar regras de outras funções.

Não colocar credenciais administrativas no repositório, no APK ou nesta conversa. Nenhuma credencial foi inventada, copiada para o app ou publicada. O usuário informou que não sabe se essa configuração já existe; a execução real de sincronização depende de verificá-la/ativá-la no ambiente. O endpoint retorna indisponibilidade sem detalhes técnicos se faltarem credenciais.

As regras não permitem acesso anônimo nem acesso a listas de outra conta. O UID Firebase é o UUID Core, autenticado por custom token; não há suposição de que um UID Google preexistente seja igual ao UUID Core. O BFF mantém a validação Core antes de emitir tokens.

## Validação e comportamento

Testes BFF usam emissor simulado e não criam contas Firebase reais. Verificar: usuário inválido não recebe token; identidade enviada no corpo é ignorada; indisponibilidade preserva lista offline. Testes locais cobrem reabertura, isolamento de contas, tombstones, revisão da outbox e falha de gravação. A confirmação visual de sincronização exige confirmação do servidor, sem confundir cache local com nuvem.

O primeiro uso exige login normal. Com uma identidade previamente salva neste aparelho, há entrada exclusiva para a lista local após reinício sem rede; isso não autoriza estoque ou Firebase sem revalidar a sessão por GET. Quando a rede volta, a sessão é revalidada antes de enviar dados. Se o app estiver encerrado, a outbox permanece até a próxima abertura; não foi criado um trabalho WorkManager neste ajuste.

Fontes oficiais: [custom tokens Firebase](https://firebase.google.com/docs/auth/admin/create-custom-tokens), [autenticação customizada Android](https://firebase.google.com/docs/auth/android/custom-auth), [Firestore offline](https://firebase.google.com/docs/firestore/manage-data/enable-offline), [regras por autenticação](https://firebase.google.com/docs/firestore/security/rules-conditions#authentication).
