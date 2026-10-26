# frigus-mobile

## Executar no tablet Android 11

O app aceita Android 11 (API 30) ou superior. No Android Studio, selecione o tablet conectado por USB e execute a configuração `app`. O Android Studio instala a versão de desenvolvimento automaticamente; não é necessária uma chave de assinatura própria.

Antes de executar, configure a origem pública HTTPS do **BFF mobile** em um arquivo `.env` na raiz do projeto (o arquivo é ignorado pelo Git):

```properties
FRIGUS_BFF_BASE_URL=https://seu-bff.example.com/
```

Use somente a origem, com `/` final, sem caminho, parâmetros ou credenciais. Também é possível definir `FRIGUS_BFF_BASE_URL` como variável de ambiente antes de abrir o Android Studio; a variável de ambiente tem prioridade sobre `.env`. Depois de alterar o endereço, sincronize/recompile o projeto. A URL do `frigus-core-api` não substitui o BFF. O endereço padrão `http://10.0.2.2:3001/` serve apenas para o emulador Android.

Para conferir a compilação no Windows, execute `.\gradlew.bat testDebugUnitTest assembleDebug`. No tablet, valide login, rotação, rolagem, teclado e saída. O login e os dados reais dependem de o BFF estar publicado e acessível pela rede do tablet.
