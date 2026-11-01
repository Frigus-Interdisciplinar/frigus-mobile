# Recuperação de senha

Em `93933ee`, a tela existia, mas `AuthRepository.sendPasswordReset` apenas informava indisponibilidade. Não havia envio de e-mail funcional a recuperar.

O aplicativo agora usa os contratos públicos `POST /mobile/core/auth/forgot-password`, `verify-reset-code` e `reset-password`, encaminhados pelo proxy do frigus-bff ao Core. Os corpos são, respectivamente, `{email}`, `{email,code}` e `{email,code,newPassword}`. Cada confirmação exige HTTP 204. Nenhuma chamada envia o token da sessão nem possui replay automático.

A tela histórica foi mantida e recebe os campos de código de seis dígitos e nova senha nos passos seguintes. Código e senha não são gravados em Bundle, Intent ou preferências; ficam somente em memória durante a operação. A ViewModel mantém o passo durante rotação. Resultado incerto de solicitação não reenvia o e-mail: permite informar o código recebido. Verificação incerta de código também bloqueia novo envio, pois o Core conta tentativas incorretas. Resultado incerto de redefinição bloqueia novo envio e orienta conferir a senha no login; não existe GET público de reconciliação de senha no contrato.

Fonte verificada: frigus-core-api `origin/dev` em `32c95fd`, atualizado por fetch sem modificar o checkout local `f824262` nem seus arquivos em andamento. `PasswordRecoveryController` já publica as três rotas; `PasswordRecoveryService` usa código com validade de dez minutos, cinco tentativas e cooldown de sessenta segundos. `EmailService`/`BrevoEmailClient` fazem o envio. O código local antigo do Core não possui esses endpoints; executar esse checkout antigo não valida o fluxo atual.

A entrega real do e-mail depende do Core implantado, Redis e configuração válida de Brevo/remetente. O Core deliberadamente retorna uma resposta neutra, inclusive se a conta não existe; sua implementação também oculta falha do provedor. Por isso o app confirma a solicitação recebida, sem afirmar que o e-mail foi entregue. Nenhum e-mail real foi disparado nesta validação.

Validação manual: abrir Esqueci minha senha no login; solicitar código para uma conta própria; conferir caixa de entrada/spam; validar código; redefinir com senha válida; entrar novamente. Código inválido/expirado deve mostrar mensagem amigável. Após timeout não deve haver reenvio automático nem confirmação falsa.
