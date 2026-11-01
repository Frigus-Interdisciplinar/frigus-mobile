# Recuperação de Assinatura e planos

## Referências e escopo

Referência visual: seção Prototype `3762:2138` do arquivo Figma `s4sWZm7r8a6pH6X1Oecp9I`.

- Assinatura e planos: [3762:3414](https://www.figma.com/design/s4sWZm7r8a6pH6X1Oecp9I?node-id=3762-3414).
- Gerenciar plano: [3762:3512](https://www.figma.com/design/s4sWZm7r8a6pH6X1Oecp9I?node-id=3762-3512).
- Carrossel de planos: [3762:3345](https://www.figma.com/design/s4sWZm7r8a6pH6X1Oecp9I?node-id=3762-3345).

Os três frames foram lidos com `get_design_context` de alta fidelidade e screenshot, seguindo `figma-design-to-code`. Os layouts XML existentes em `93933ee` foram reaproveitados e ajustados ao desenho, sem aplicar a imagem do protótipo como interface. O footer continua pertencendo à MainActivity e não foi duplicado nas telas de planos. Conforme os frames, Main oculta a barra inferior em Gerenciar e Alterar plano, restaurando-a ao retornar. As quatro novas seções de planos também entram na restauração segura de navegação, sem credenciais no Bundle.

## Composição visual

Assinatura mantém cabeçalho Sarabun 18, cartão de 126 dp com coroa de 32 dp, título 16, preço 13 e validade 11. A superfície do cartão é `#F5F8FC`, borda `#E2E8F0`, raio de 14 dp e margens laterais de 24 dp. O menu de 228 dp contém somente Gerenciar plano, Forma de pagamento, Convidar membro e Meu Grupo. Cancelamento fica em uma linha separada de 58 dp, na cor `#ED4747` do frame.

Gerenciar plano reutiliza seu XML original, conectando o cartão, informações e ações ao estado G11 existente. O carrossel usa cartões nativos de 208×626 dp, espaços de 8 dp e ícones/fundos/indicadores do Figma. O índice visível é mantido no SubscriptionViewModel e no bundle seguro de navegação, sem dados sensíveis.

## Assets

Os SVGs fornecidos pelo MCP foram baixados com `curl -L`, conforme instrução da resposta, e rasterizados sem alteração de proporção com Sharp em 4× para `res/drawable-nodpi/plan_figma_*.png`. Não há URLs temporárias do Figma na UI em execução. Foram conferidos 17 arquivos não vazios, totalizando 29.037 bytes.

| Família de arquivo | Slot de uso e geometria efetiva |
| --- | --- |
| `crown` | Cartões atuais, 32×32 dp |
| `manage` | Menu principal 20×20 dp; ação Gerenciar 24×24 dp |
| `invite`, `payment`, `group`, `cancel` | Menu principal, 20×20 dp |
| `cancel_manage` | Cancelamento em Gerenciar, 24×24 dp |
| `next`, `next_red` | Setas dos menus, 10×20 dp |
| `back` | Voltar, 17×35 dp, rotação de 180° como no frame |
| `free`, `premium`, `family` | Glifos dos cartões do carrossel, 35×35 dp |
| `free_bg`, `premium_bg` | Fundos dos glifos, 56×56 dp |
| `dot_active`, `dot` | Indicadores, respectivamente 18×8 e 8×8 dp |

As screenshots de referência estão em `C:/Users/eriksilva-ieg/AppData/Local/Temp/frigus-plan-assets/figma-main.png`, `figma-manage.png` e `figma-carousel.png` para comparação visual durante a validação local. Elas não são assets da interface.

## Compatibilidade funcional G11

A tela principal deixou de exibir o painel agregado de dados e diagnósticos. As mesmas funções continuam alcançáveis pelos acessos apropriados:

| Acesso | Funções preservadas |
| --- | --- |
| Cartão do plano | Benefícios e limites reais; plano alternativo sem limites não herda os limites do plano atual |
| Gerenciar → Alterar plano | Catálogo de planos ativos, preços, benefícios, limites, seleção do plano gratuito e bloqueio de contratação paga sem integração adequada |
| Forma de pagamento | Aviso verdadeiro de indisponibilidade de cartões e acesso ao histórico de transações |
| Histórico | Transações reais, valores ausentes sem substituição por zero e paginação |
| Meu Grupo | Seleção e criação de grupo, estoque do grupo e sua criação, membros reais, convites e revogação |
| Convidar membro | Validação e confirmação do convite no grupo administrado |
| Cancelar/Reativar | Comando real pelo ViewModel, com confirmação e tratamento de resultado incerto |

Os fluxos ativos não usam `PlanoAtual`, `CartoesLocais` ou `GrupoLocal`. Não há cartões, membros, valores ou datas de exemplo provenientes do protótipo. O frame mostra uma próxima cobrança e cartão fictícios como exemplo visual; como o contrato não confirma a próxima cobrança nem informa cartão salvo, a interface mostra valor a confirmar/ausência explícita nesses campos.

SubscriptionScreenFragment concentra apenas apresentação e ações de View. API, Repository, geração de sessão, bloqueio de mutações pendentes e reconciliação explícita por GET permanecem no G11 existente. Nenhum contrato backend novo foi criado para esta recuperação.

## Verificações e pendências

Os quatro XMLs de tela/item foram parseados, IDs e referências de strings conferidos, e `git diff --check` passou. Um validador independente conferiu a alcançabilidade das ações e identificou o fallback indevido de limites entre planos, que foi corrigido. Limites booleanos ausentes são exibidos como não informados.

O agente desta tarefa não executou Gradle. Build, testes e lint são executados conjuntamente pelo agente principal após estabilização das tarefas paralelas. A comparação visual final no aplicativo precisa usar dados reais da conta; diferenças de nome, preço, benefícios e disponibilidade em relação ao exemplo Figma são esperadas quando o serviço retornar outros dados.

Roteiro manual:

1. Perfil → Assinatura: conferir os quatro acessos e cancelamento separado, sem painel técnico embutido.
2. Gerenciar: conferir dados reais, abrir detalhes do plano e o carrossel; navegar e rolar pelos cartões.
3. Abrir plano alternativo sem limites publicados e conferir ausência de limites herdados do plano atual.
4. Abrir Forma de pagamento e histórico; conferir estado vazio/erro e carregar mais transações.
5. Meu Grupo: selecionar grupo e conferir estoque/membros/convites; executar apenas mutações autorizadas e confirmar retorno real.
6. Simular timeout de mutação: conferir mensagem de resultado incerto e exigir Atualizar dados antes de novo envio.
7. Testar volta, rotação, fonte ampliada e logout/troca de conta durante carregamento; dados de outra sessão não podem ser publicados.

Nenhum staging, commit, push ou pagamento real foi realizado nesta tarefa.
