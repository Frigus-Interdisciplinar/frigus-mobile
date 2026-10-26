# Contrato de notificações do BFF mobile

A central usa autenticação BFF em todas as rotas:

| Método | Rota | Resposta |
| --- | --- | --- |
| GET | `/mobile/notifications?page=&size=` | Página com `content`, `number` e `totalPages` |
| PATCH | `/mobile/notifications/{id}/read` | Notificação atualizada |
| DELETE | `/mobile/notifications` | 204 após remover todas as notificações da conta |

Para push Android, o BFF precisa oferecer `POST /mobile/push-tokens` com
`{"token":"...","platform":"ANDROID"}` e `DELETE /mobile/push-tokens` com
`{"token":"..."}`. Ambas as operações são autenticadas e idempotentes,
respondem com 2xx e associam o token somente à conta atual. O servidor deve
descartar tokens FCM inválidos após falhas de envio.

Somente `PRODUCT_NEAR_EXPIRATION` e `SHOPPING_LIST_REMINDER` geram push.
O envio usa payload FCM AndroidConfig v1 de notificação com título/corpo e dados
`notificationId` (UUID) e `type`. Com o app em primeiro plano, o serviço
Android exibe o aviso; em segundo plano, o Firebase o entrega na bandeja e
passa os dados ao abrir o app. O BFF e o envio de push não são implementados
neste repositório.
