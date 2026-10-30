# Revisão de segurança e API

## Evidência disponível

O acesso ao proxy inicialmente falhou dentro do sandbox. A execução autorizada
fora desse sandbox, mantendo o proxy e TLS, permitiu consultar o código oficial
da branch `ver/26.2`, commit `e4d6dfd1597968e78a3d405dce73d1d1f14a4b81`.
Isso verifica o contrato de API descrito abaixo; não substitui compilar o plugin
contra o artefato publicado nem testá-lo em um servidor real.

- [`World#getCoordinateScale`](https://github.com/PaperMC/Paper/blob/e4d6dfd1597968e78a3d405dce73d1d1f14a4b81/paper-api/src/main/java/org/bukkit/World.java)
  existe na API de 26.2.
- [`NetherPortalBlock.java.patch`](https://github.com/PaperMC/Paper/blob/e4d6dfd1597968e78a3d405dce73d1d1f14a4b81/paper-server/patches/sources/net/minecraft/world/level/block/NetherPortalBlock.java.patch)
  confirma alvo inicial escalado e ajustado à borda, evento inicial, busca e cálculo
  posterior da posição final. Ambos os eventos iniciais expõem `setCanCreatePortal`
  e `setCreationRadius`. Na versão 0.3, a criação segue habilitada por padrão vanilla,
  preservando eventual desativação feita por outro plugin; o raio é limitado.
- [`PortalForcer.java.patch`](https://github.com/PaperMC/Paper/blob/e4d6dfd1597968e78a3d405dce73d1d1f14a4b81/paper-server/patches/sources/net/minecraft/world/level/portal/PortalForcer.java.patch)
  usa busca quadrada com o raio informado, consulta POIs, pode carregar chunks
  e emite `PortalCreateEvent` antes de aplicar blocos para uma saída automática.
- [`ServerPlayer.java.patch`](https://github.com/PaperMC/Paper/blob/e4d6dfd1597968e78a3d405dce73d1d1f14a4b81/paper-server/patches/sources/net/minecraft/server/level/ServerPlayer.java.patch)
  confirma um `PlayerTeleportEvent` final distinto do `PlayerPortalEvent`; cancelar
  esse evento final impede a viagem.
- [`Entity.java.patch`](https://github.com/PaperMC/Paper/blob/e4d6dfd1597968e78a3d405dce73d1d1f14a4b81/paper-server/patches/sources/net/minecraft/world/entity/Entity.java.patch)
  confirma `EntityTeleportEvent` final antes de `EntityPortalExitEvent`. Cancelar
  o primeiro impede a viagem; **cancelar o segundo NÃO impede a viagem** e pode
  repor coordenadas da origem mantendo o mundo destino. Por isso, o plugin usa
  `EntityTeleportEvent`, não cancela `EntityPortalExitEvent`.

Referências oficiais para conferir na homologação:

- <https://jd.papermc.io/paper/>
- <https://docs.papermc.io/paper/dev/project-setup/>
- <https://docs.papermc.io/paper/reference/configuration/>
- <https://minecraft.wiki/w/Dimension_type>

## Escala e identidade

`expected-scale` é uma expectativa, não uma alteração da dimensão. O cálculo real
é a razão entre `coordinate_scale` do Nether e do Overworld. Alterar ambos sem
validar a razão pode produzir conversões erradas. A escala deve ser obtida do
servidor em execução por uma API pública disponível; uma ausência de API não
justifica tratar o valor configurado como se fosse o valor real.

A chave do mundo, a chave do tipo de dimensão e nomes de pastas são conceitos
diferentes. Servidores com gerenciadores de mundos precisam confirmar as chaves
efetivamente carregadas. Não selecionar arbitrariamente o primeiro mundo
`NETHER` ou inferir o Nether de um sufixo de pasta.

## Camadas de defesa

1. Cancelar a viagem se a escala real não puder ser verificada, as configurações
   forem inválidas ou os mundos estiverem indisponíveis.
2. Validar a origem e sua conversão teórica, antes da busca. O Minecraft pode
   ajustar um alvo à borda física; esse ajuste não deve tornar uma origem
   originalmente proibida em uma viagem permitida.
3. `PlayerPortalEvent` e `EntityPortalEvent` descrevem uma etapa anterior à busca
   e não comprovam a saída final. Limitar o raio e verificar a saída efetiva; uma
   busca que encontre uma saída proibida não autoriza a viagem.
4. Permitir criação automática segura em `PortalCreateEvent`, razão `NETHER_PAIR`.
   Conferir origem, escala, contexto da tentativa e **todos** os blocos propostos
   antes de permitir sua aplicação. Moldura, base e limpeza local do fallback
   vanilla estão incluídas na lista. Acender uma estrutura manualmente fora da
   zona continua permitido; `FIRE` e `END_PLATFORM` não são bloqueados por essa regra.
5. Validar o destino final nos eventos de teleporte/saída aplicáveis à versão.
   Conferir o comportamento real para jogadores e entidades, que podem seguir
   caminhos internos diferentes.

O evento final genérico de entidades não possui causa. A correlação usa somente
uma tentativa de portal Nether aceita no mesmo tick; o contexto expira no próximo
tick e é retirado quando os eventos iniciais são cancelados. Isso evita um contexto
antigo bloquear comandos administrativos executados posteriormente. Não é possível
distinguir perfeitamente um plugin que dispare outro teleporte de entidade,
sincronamente, dentro da mesma tentativa de portal.

O sistema deve cancelar eventos, sem implementar viagens adicionais por comando
ou `teleport()`: isso evita duplicação, loops e substituição da lógica vanilla.
Os portais do End e os comandos administrativos ficam fora desse controle.

## Limites da integração por eventos

Não é exigido que os chunks estejam previamente gerados. A busca e criação vanilla
podem carregar/gerar chunks durante a viagem; não há tarefa de pré-geração global.
Isso é diferente de cortar, apagar ou regenerar os mapas existentes. A criação
local de saída pode modificar obsidiana, base e espaço livre; não se afirma que
nenhum bloco muda quando uma nova saída é criada.

Um plugin que modifica um evento depois da guarda de segurança pode alterar seu
resultado. Prioridade `HIGHEST` e respeito a eventos já cancelados cooperam com
outros plugins, mas não constituem isolamento contra plugins que forçam viagens.
`MONITOR` é destinado à observação e não deve ser usado para modificar eventos.
Para entidades não jogadoras, um plugin que altere ou cancele `EntityPortalExitEvent`
depois da guarda final também pode afetar a posição, devido à semântica desse evento;
essa integração exige teste e cooperação do outro plugin, não há isolamento público.
Compatibilidade com Território, Geyser, ChunkyBorder e outros depende de teste no
servidor real, incluindo listeners com a mesma prioridade.

## Datapack

Sem os arquivos reais do servidor e o formato oficial de 26.2, não substituir o
tipo de dimensão por um JSON inventado. Um datapack seguro deve partir do JSON
efetivo existente (incluindo customizações), mudar somente `coordinate_scale`,
usar o formato confirmado da versão e revisar sobreposições de datapacks.
Alterar o tipo de dimensão requer reinicialização e validação da escala após a
carga; `/survivalplus nether reload` não altera registries do Minecraft.

A revisão específica da criação automática na versão 0.3 está em
[AUTO-CREATION-REVIEW.md](AUTO-CREATION-REVIEW.md).
