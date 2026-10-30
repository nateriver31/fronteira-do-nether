# Homologação curta

Faça estes testes em uma cópia do servidor com os mesmos mundos, datapacks,
Paper/Java, Geyser e plugins da produção. Mantenha backup. A versão 0.3 permite a
criação automática segura de saídas e não exige pré-geração. Registre as coordenadas
reais de entrada e saída no console
e marque cada resultado como passou/falhou/não executado.

## Estado da validação

Neste ambiente não foi iniciado Minecraft. Os testes Java/Bedrock e de integração
abaixo **não foram executados**. O fluxo de eventos foi examinado na fonte oficial
do Paper 26.2; isso não substitui homologação no servidor. A execução dos testes
automatizados deve ser consultada no relatório final da entrega; a existência
de um teste no código não significa que ele passou.

## Preparação

- Build com JDK 25 e `mvn -B clean verify`;
  confira o artefato Paper utilizado e o relatório real da compilação.
- Inicie a cópia do servidor; confirme ausência de erros de carga do datapack.
- `/survivalplus nether status`: mundos corretos, escala real 1:6 e sistema ativo.
- `/survivalplus nether debug`: confira chave da dimensão e coordenadas em cada mundo.
- Instale a configuração padrão. Não construa antecipadamente todas as saídas:
  a primeira viagem sem saída existente é um teste obrigatório desta versão.
- Use permissões administrativas para os comandos; um jogador comum deve recebê-los
  como indisponíveis. Não dê bypass de portais a administradores: teleporte por
  comando é independente, viagem por portal obedece à mesma regra.

## Viagens e fronteira

Repita as viagens permitidas e bloqueadas **nos dois sentidos**, com cliente Java
e com cliente Bedrock via Geyser. Para testar escala, use portais isolados, sem
saídas concorrentes próximas; portais existentes são procurados num raio pequeno,
portanto tolerância de alinhamento deve ser distinguida da razão de escala.

| Caso | Situação | Esperado |
|---|---|---|
| 1 | Nether (0, 0), sem saída no Overworld | Vanilla cria saída segura e permite viagem |
| 2 | Nether (1000, 500), saída em Overworld (6000, 3000) | Viagem permitida |
| 3 | Nether (1300, 0) | Cancelada; permanece no mesmo local |
| 4 | Nether (0, 1100) | Cancelada; permanece no mesmo local |
| 5 | Overworld (6000, 3000), sem saída no Nether | Vanilla cria saída segura perto de (1000, 500) e permite viagem |
| 6 | Overworld (7400, 0), com saída segura correspondente | Permitida |
| 7 | Cruze a fronteira do Nether andando, correndo, voando e com Elytra | Um título vermelho e chat por saída |
| 8 | Retorne à zona central | Um título verde por retorno |
| 9 | Permaneça sobre um portal bloqueado por 20 segundos | Sem viagem; actionbar no máximo a cada cinco segundos |
| 10 | Saída existente fora da zona, próxima do alvo seguro | Não pode ser selecionada; cancelar se necessário |
| 11 | Não existe saída construída, área permitida e espaço seguro | Saída criada automaticamente; não exigir construção manual |
| 12 | Fonte/destino nos quatro cantos e em coordenadas negativas | Mesma regra retangular, sem contorno pelos cantos |
| 13 | Portal grande vanilla junto da borda | Saída inteira segura ou viagem cancelada |
| 14 | Destino perto de `WorldBorder` menor que o limite configurado | Não ultrapassa a borda efetiva |
| 15 | Overworld (6499.724, 6498.256), sem saída | Não bloquear por chunks não gerados ou por falta de portal manual; saída segura próxima do alvo ajustado |
| 16 | PortalCreateEvent propõe moldura/base/limpeza fora dos limites | Cancelar criação antes de aplicar o conjunto de blocos |
| 17 | PortalCreateEvent propõe todos os blocos inteiros dentro dos limites | Permitir se escala, origem e contexto também forem válidos |

O caso (7800, 0) do pedido original já está fora do mapa planejado; não remova a
barreira física para chegar ali. Na regra atual, (7400, 0) é permitido: toda a
largura configurada do Overworld está conectada ao Nether. Pontos próximos da borda
podem ter o alvo acomodado alguns blocos para dentro. A área não é reduzida por
uma faixa fixa de 24 blocos. Teste X e Z independentemente, inclusive os quatro
cantos. O Minecraft ainda pode falhar se nenhum destino/criação seguros forem
possíveis ou outro plugin cancelar a operação.

## Estado, configuração e coexistência

1. Saia da zona, desconecte e entre novamente: estado é conferido, sem mensagens
   a cada bloco. Saia e entre por teleporte administrativo: teleporte funciona e
   o estado dos avisos acompanha a nova posição.
2. Troque para outro mundo: nenhum aviso de fronteira; End funciona normalmente.
3. Teste um item e um mob que normalmente atravessariam portal: mesma restrição,
   sem desaparecimento/duplicação. Teste veículos/passengers suportados pela versão.
4. Desative títulos, actionbar e sons, um de cada vez; configure cores e verifique
   clientes Java/Bedrock. Símbolos e exibição de fontes podem variar entre clientes.
5. Ajuste `expected-scale` para um valor diferente da escala real e recarregue:
   console/status indicam divergência; viagens ficam bloqueadas; não há segunda
   conversão. Restaure 6 e confirme a recuperação.
6. Teste configuração inválida (limites invertidos, número não finito, mundo
   ausente): deve haver falha segura, sem queda do servidor. Restaure a configuração.
7. Recarregue corretamente: não afirmar que o registry mudou. Para testar novo
   datapack, pare e reinicie completamente a cópia do servidor.
8. Com Território, PvPManager e demais plugins ativos, verifique que um cancelamento
   externo é respeitado. Teste claims/guerras no lado de origem e de destino;
   o plugin não concede permissões adicionais nem substitui a política desses plugins.
9. Use um destino sem chunks gerados em uma cópia descartável: **não** deve surgir
   exigência de pré-geração. A busca/criação vanilla pode carregar/gerar chunks
   necessários à viagem. Verifique que não há tarefa global de pré-geração nem
   construção de saída fora dos limites. Observe o custo da primeira viagem.
10. Reinicie a cópia e repita os casos 1, 3 e 5. Confira logs e perda/duplicação de
    entidades antes de aprovar instalação definitiva.

Se um caso de segurança falhar, não publique o plugin em produção; guarde versões,
logs sem segredos, coordenadas e lista de plugins para diagnóstico.
