# Criação automática segura — revisão 0.3

A primeira viagem não pode depender de uma saída que alguém já construiu na
outra dimensão. A versão 0.3 permite a criação vanilla e retira a exigência de
chunks previamente gerados. A criação local de obsidiana, base e espaço livre
faz parte do portal normal do Minecraft; isso não corta, redimensiona, apaga nem
regenera os mapas existentes.

## Contratos conferidos

Referência oficial: Paper `ver/26.2`, commit
`e4d6dfd1597968e78a3d405dce73d1d1f14a4b81`, consultado novamente nesta revisão.

- [PlayerPortalEvent](https://github.com/PaperMC/Paper/blob/e4d6dfd1597968e78a3d405dce73d1d1f14a4b81/paper-api/src/main/java/org/bukkit/event/player/PlayerPortalEvent.java)
  e [EntityPortalEvent](https://github.com/PaperMC/Paper/blob/e4d6dfd1597968e78a3d405dce73d1d1f14a4b81/paper-api/src/main/java/org/bukkit/event/entity/EntityPortalEvent.java)
  expõem permissão de criação, raio de criação e raio de busca. A implementação
  vanilla inicia com criação permitida; o plugin não deve reativar a criação que
  outro plugin já desativou.
- [NetherPortalBlock.java.patch](https://github.com/PaperMC/Paper/blob/e4d6dfd1597968e78a3d405dce73d1d1f14a4b81/paper-server/patches/sources/net/minecraft/world/level/block/NetherPortalBlock.java.patch)
  primeiro busca um portal existente. Se não encontrar e `canCreatePortal` for
  verdadeiro, chama `createPortal` passando a entidade e o raio escolhido.
- [PortalForcer.java.patch](https://github.com/PaperMC/Paper/blob/e4d6dfd1597968e78a3d405dce73d1d1f14a4b81/paper-server/patches/sources/net/minecraft/world/level/portal/PortalForcer.java.patch)
  captura as escritas da plataforma/base, do espaço limpo no fallback, da moldura
  e dos blocos de portal em `BlockStateListPopulator`. Só depois emite
  `PortalCreateEvent` com razão `NETHER_PAIR` e a entidade de origem. Se cancelado,
  retorna sem chamar `placeBlocks` para esse conjunto.
- [BlockStateListPopulator.java](https://github.com/PaperMC/Paper/blob/e4d6dfd1597968e78a3d405dce73d1d1f14a4b81/paper-server/src/main/java/org/bukkit/craftbukkit/util/BlockStateListPopulator.java)
  fornece os snapshots de estados **propostos** e aplica essa lista em
  `placeBlocks`. Assim, a validação não pode filtrar somente `NETHER_PORTAL`:
  blocos `AIR`, `OBSIDIAN` e demais estados também precisam caber nos limites.
- [PortalCreateEvent](https://github.com/PaperMC/Paper/blob/e4d6dfd1597968e78a3d405dce73d1d1f14a4b81/paper-api/src/main/java/org/bukkit/event/world/PortalCreateEvent.java)
  expõe `getBlocks`, `getEntity` e `getReason`. `getEntity` pode ser nulo; uma
  criação automática sem origem confiável nos mundos controlados deve ser recusada.

## Defesas da versão

1. Validar a origem e a correspondência 1:6 antes de acomodar qualquer ponto de saída.
2. Perto dos limites, acomodar o alvo alguns blocos para dentro, com espaço para a
   estrutura e a entidade. Não multiplicar/dividir a coordenada uma segunda vez.
3. Limitar os raios de busca e criação, respeitando configurações e restrições
   anteriores dos eventos. Não executar pré-geração global.
4. No `PortalCreateEvent`, associar a entidade à tentativa aceita no mesmo tick e
   validar novamente a escala e o par de mundos. Conferir **todos** os blocos
   inteiros: X até X+1, Z até Z+1 e altura válida. Uma origem válida não autoriza
   uma moldura, plataforma ou limpeza local fora da área permitida.
5. Cancelar o evento se qualquer bloco proposto ultrapassar os limites. Isso
   ocorre antes da aplicação dos snapshots; não construir e depois desfazer.
6. Conferir a posição final nos eventos reais de teleporte. A criação aprovada
   não prova, sozinha, que a posição exata de saída é segura.

`FIRE` continua fora dessa restrição de criação automática: jogadores podem
construir/acender portais fora da zona, embora a viagem fique bloqueada.
`END_PLATFORM` e viagens do End não são modificados.

## Limites

Criar um portal de saída pode modificar blocos localmente e pode exigir chunks
carregados/gerados pelo Minecraft. Não se promete zero alteração de blocos nem
zero I/O. Outro plugin pode cancelar o evento ou alterar snapshots depois da
guarda; as prioridades não constituem isolamento entre plugins. A integração com
claims, Geyser e os demais plugins precisa do teste no servidor real.

Os testes de primeira viagem, retorno, bordas e recusa antes de aplicar blocos
estão em [TESTES.md](TESTES.md). Resultados automatizados e verificações do JAR
ficam em [VALIDACAO.md](VALIDACAO.md).
