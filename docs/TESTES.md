# Homologação curta

Faça estes testes em uma cópia do servidor com os mesmos mundos, datapacks,
Paper/Java, Geyser e plugins da produção. Mantenha backup. Construa previamente
portais de saída dentro dos limites; a criação automática é desabilitada para
preservar os mapas. Registre as coordenadas reais de entrada e saída no console
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
- Instale a configuração padrão e deixe chunks dos dois mapas previamente gerados.
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
| 1 | Nether (0, 0), saída existente correspondente | Viagem permitida |
| 2 | Nether (1000, 500), saída em Overworld (6000, 3000) | Viagem permitida |
| 3 | Nether (1300, 0) | Cancelada; permanece no mesmo local |
| 4 | Nether (0, 1100) | Cancelada; permanece no mesmo local |
| 5 | Overworld (6000, 3000), saída em Nether (1000, 500) | Viagem permitida |
| 6 | Overworld (7400, 0), com saída segura correspondente | Permitida |
| 7 | Cruze a fronteira do Nether andando, correndo, voando e com Elytra | Um título vermelho e chat por saída |
| 8 | Retorne à zona central | Um título verde por retorno |
| 9 | Permaneça sobre um portal bloqueado por 20 segundos | Sem viagem; actionbar no máximo a cada cinco segundos |
| 10 | Saída existente fora da zona, próxima do alvo seguro | Não pode ser selecionada; cancelar se necessário |
| 11 | Não existe saída construída | Viagem pode falhar; nenhuma estrutura é criada |
| 12 | Fonte/destino nos quatro cantos e em coordenadas negativas | Mesma regra retangular, sem contorno pelos cantos |
| 13 | Portal grande vanilla junto da borda | Saída inteira segura ou viagem cancelada |
| 14 | Destino perto de `WorldBorder` menor que o limite configurado | Não ultrapassa a borda efetiva |

O caso (7800, 0) do pedido original já está fora do mapa planejado; não remova a
barreira física para chegar ali. (7400, 0) verifica o mesmo bloqueio dentro do mapa.
Pontos muito próximos da borda podem ser bloqueados pela margem conservadora,
mesmo com o centro dentro da zona. Teste X e Z independentemente.

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
9. Use chunks não gerados em uma cópia descartável: viagem deve ser recusada sem
   disparar busca de saída. Confira arquivos/regiões e logs, sem pré-gerar novos
   chunks para contornar a verificação.
10. Reinicie a cópia e repita os casos 1, 3 e 5. Confira logs e perda/duplicação de
    entidades antes de aprovar instalação definitiva.

Se um caso de segurança falhar, não publique o plugin em produção; guarde versões,
logs sem segredos, coordenadas e lista de plugins para diagnóstico.
