# SurvivalPlusCore

Sistema de portais Nether/Overworld para Survival+, com escala esperada **1:6**,
zona de conexão retangular e exploração livre do restante dos mapas.
O plugin não altera escala, terreno, biomas, estruturas, worldborder ou plugins
existentes. O ChunkyBorder continua responsável pela barreira física.

**Estado desta entrega:** JAR compilado com **Java 25** e API oficial
**Paper 26.2 build 135**; 24 testes Maven e 18 testes Python passaram. A API e o
fluxo de portais foram conferidos no código oficial (commit identificado em
[docs/API-REVIEW.md](docs/API-REVIEW.md)). Não houve execução de Minecraft ou
teste com clientes Java/Bedrock neste ambiente.
Não instalar diretamente em produção sem os testes de [homologação](docs/TESTES.md).
O [relatório de validação](docs/VALIDACAO.md) separa os testes executados das etapas pendentes.

## Baixar e instalar

- [Baixar SurvivalPlusCore-0.2.0.jar](https://github.com/nateriver31/fronteira-do-nether/raw/refs/heads/main/downloads/SurvivalPlusCore-0.2.0.jar)
- [Arquivo e checksum no repositório](downloads/)
- [Instalação rápida no Crafty](docs/INSTALACAO.md)
- [Datapack de escala 1:6 para Minecraft 26.2](docs/INSTALAR-ESCALA-6.md)

O ZIP do projeto contém código-fonte; ele não pode ser instalado como plugin.
Com o servidor parado, coloque o **JAR** em `plugins/` e inicie usando Java 25.
Confira `/survivalplus nether status`. Se a escala real ainda for 1:8, o sistema
bloqueia as viagens até a configuração real da dimensão estar em 1:6; instalar o
JAR ou editar `expected-scale` não altera essa escala.

## Atualização 0.2.0

Todo o Overworld configurado pode iniciar uma viagem ao Nether. A zona efetiva
é calculada automaticamente de `overworld-bounds / expected-scale`; o bloco
legado `portal-zone` não reduz essa área. Configurações antigas são aceitas.
O datapack permanece o mesmo. Substitua o JAR antigo com o servidor parado; não
deixe duas versões em `plugins/`. Ainda é necessário construir as saídas.
Foram executados 26 testes Maven, incluindo regressões para a posição da captura
(6499.724, 6498.256), negativos, cantos e (7400, 0). O teste real de viagem continua pendente.

## Comportamento

- Nether autorizado: projeção de TODO o Overworld em 1:6: X ±1250 e Z ±1083,333… com os limites padrão.
- Nether → Overworld: origem deve estar autorizada; correspondência X/Z × 6.
- Overworld → Nether: correspondência X/Z ÷ 6 deve estar autorizada.
- Verifica também limites configurados dos mapas e `WorldBorder` da API.
- Não transforma coordenadas uma segunda vez: o Minecraft calcula a viagem com
  a escala interna. A conversão do plugin serve para conferir a autorização.
- Se os mundos ou a escala real não puderem ser confirmados, ou a escala diferir
  da expectativa, as viagens controladas são bloqueadas e o console recebe o motivo.
- Portais do End e comandos administrativos de teleporte não são controlados.
- Entidades seguem as restrições de portais sempre que os eventos da API
  notificam sua viagem. Não há mecanismo próprio de teleporte nem cópia de entidades.

**Preservação dos mapas:** a criação automática de portais de saída é bloqueada.
Construa e acenda manualmente as saídas existentes nos dois mundos, em locais
seguros. Acender um portal fora da zona continua permitido, mas viajar por ele é
bloqueado. Não há remoção de blocos, dano, retorno forçado ou barreira nova.

A busca não exige que uma margem de 24 blocos caiba na zona de conexão. A saída
real é validada antes da viagem, incluindo os limites e o volume da entidade.
Viagens ainda podem falhar se não existir saída segura já construída ou se os
chunks necessários não estiverem gerados.

## Compilar

Requisitos: **JDK 25**, **Maven 3.9+**, acesso ao Maven Central e ao repositório
oficial do Paper. O POM fixa a API publicada `26.2.build.135-stable`, utilizada
para compilar esta entrega. Paper 26.2 usa versões `26.2.build.N-stable`, em vez
do antigo formato `R0.1-SNAPSHOT`.

```sh
cd /caminho/fronteira-do-nether
java -version
mvn -version
mvn -B clean verify
```

Use o JAR produzido em `target/` somente se a compilação e os testes passarem.
Não substitua a dependência por uma API antiga para apresentar compatibilidade
26.2 como comprovada. Nenhuma dependência NMS/CraftBukkit é necessária.

### Validação executada nesta máquina

- 1.106 verificações da geometria passaram (`bash scripts/test-core.sh`).
- 18 testes Python do gerador de datapack passaram (`python3 -m unittest discover -s tools -v`).
- JDK 25 e Maven 3.9.9 foram obtidos de fontes oficiais, com checksums verificados.
- `mvn -B clean verify` passou com API Paper `26.2.build.135-stable`: todos os
  listeners compilaram; 24 testes passaram, sem falhas, erros ou testes ignorados.
  São 23 testes de configuração e uma suíte com as 1.106 verificações da geometria.
- JAR gerado e conferido: descritor, configuração e bytecode Java 25 presentes.
- Nenhum mundo ou arquivo real do servidor estava disponível para inspeção.

O bloqueio de rede da entrega anterior foi resolvido. Os repositórios oficiais
estão acessíveis nesta máquina; a homologação dentro do servidor permanece pendente.

## Configurar a escala real

`nether-portals.expected-scale: 6` **não** muda a escala do Minecraft. O sistema
confere a razão entre `coordinate_scale` do Nether e do Overworld; para o caso
normal, os valores são respectivamente 6 e 1.

Foi preparado um [pacote para o tipo padrão do Nether 26.2](docs/INSTALAR-ESCALA-6.md),
com base nos dados extraídos dessa versão e preservando todas as propriedades da
referência, exceto a escala. O console informado identifica Paper 26.2 build 129
e apenas os packs internos ativos. Arquivos do servidor e customizações em memória
ainda não foram examinados; valide em uma cópia antes de usar na produção.

Para um tipo com propriedades próprias, o gerador [tools/create_scale_datapack.py](tools/create_scale_datapack.py) copia
o JSON efetivo fornecido e modifica somente `coordinate_scale`, preservando as
demais propriedades. Também exige um `pack.mcmeta` comprovadamente válido na
versão exata do servidor, sem inventar o número de formato de 26.2.

Em uma **cópia de homologação**, com o servidor parado:

1. Faça backup integral dos mundos, configurações, plugins e datapacks.
2. Consulte a lista e prioridade dos datapacks ativos. Inspecione nos diretórios
   e ZIPs todos os arquivos `data/minecraft/dimension_type/the_nether.json`, os
   recursos `data/*/dimension/*.json` e eventuais tipos de dimensão customizados.
3. Determine qual tipo de dimensão o Nether realmente usa e qual JSON prevalece.
   Se o tipo tiver outra chave, não instale o override padrão do gerador: será
   necessário adaptar o recurso à chave correta depois de examinar os arquivos.
4. Obtenha o JSON efetivo e os metadados mínimos válidos para **a versão exata**.
   Se não houver override, obtenha a definição oficial dessa versão. Não use um
   arquivo de outra versão nem substitua propriedades personalizadas por vanilla.
5. Execute, fora dos mapas:

   ```sh
   python3 tools/create_scale_datapack.py \
     --dimension-type /copia/the_nether-efetivo.json \
     --pack-mcmeta /copia/pack.mcmeta \
     --output /copia/survivalplus-scale-6
   ```

6. Revise o JSON gerado: a única alteração semântica deve ser `coordinate_scale`.
   Se o Overworld não tiver escala 1, o valor 6 no Nether isoladamente não garante
   razão 1:6; investigue os tipos antes de aplicar a mudança.
7. Copie a pasta gerada para `datapacks/` do mundo principal/level-name, sem
   sobrescrever outro pack. Garanta a prioridade adequada sobre os outros packs.
   Quando necessário, habilite-o em homologação com
   `/datapack enable "file/survivalplus-scale-6" last`, sem executar `/reload`.
8. Faça uma reinicialização completa. Verifique os logs de carga, `/datapack list
   enabled` e o status do plugin. Só prossiga quando a escala real estiver confirmada.

O gerador não valida o schema da dimensão no Minecraft, não detecta todos os
conflitos de packs e não ativa o pack. Não há arquivos reais do servidor disponíveis
neste checkout; a inspeção desses conflitos permanece necessária.

## Instalar no Crafty Controller

1. Use primeiro uma cópia do servidor, isolada da produção, com backup restaurável.
   Confirme Paper 26.2 e selecione **Java 25** na configuração do processo do Crafty.
2. Pare o servidor pelo painel e aguarde encerrar. Copie o JAR validado para
   `plugins/`. Não remova nem substitua Território, Geyser ou outros plugins.
3. Se necessário, prepare o datapack como descrito acima e reinicie completamente.
4. Após a primeira execução, edite `plugins/SurvivalPlusCore/config.yml` com o
   servidor parado ou recarregue apenas a configuração do plugin pelo comando
   administrativo. Confira as chaves dos mundos efetivamente carregados, sem
   presumir nomes de pasta como `world_nether`.
5. Confira `/survivalplus nether status`, execute o roteiro e revise o console.
   `/survivalplus nether reload` não altera registries nem ativa uma nova escala.
6. Em produção, repita a instalação somente depois de homologar, mantendo backup
   e os mesmos arquivos/versões. Ao remover o plugin, a escala de um datapack
   instalado permanece até sua remoção e reinicialização.

## Comandos

Todos os comandos exigem a permissão administrativa `survivalplus.admin`, declarada no `plugin.yml`.
OP recebe a permissão por padrão; delegue-a pelo LuckPerms apenas a administradores.

| Comando | Uso |
|---|---|
| `/survivalplus nether status` | Estado, motivo de bloqueio, escala configurada/real e zona |
| `/survivalplus nether reload` | Recarrega e valida a configuração, sem mudar escala interna |
| `/survivalplus nether debug` | Coordenadas, dimensão e autorização da posição do jogador |

Avisos, cores, títulos, actionbar e sons são configuráveis. Os títulos de fronteira
são emitidos na mudança de estado no Nether; a entrada no servidor também verifica
o estado. O cooldown padrão para tentativas bloqueadas é de cinco segundos.

## Limitações e validação

- O getter público da escala e os eventos foram verificados na fonte oficial
  de 26.2. Indisponibilidade da leitura em um runtime divergente bloqueia o sistema.
- O formato do datapack depende dos arquivos reais da versão e do servidor.
- Não se promete compatibilidade já testada com Geyser, Território ou outros
  plugins; execute a integração na cópia do servidor.
- Outro plugin que modifique eventos depois das guardas pode interferir no
  resultado. Prioridades de eventos não isolam plugins entre si. Em particular,
  `EntityPortalExitEvent` não cancela a viagem e pode alterar sua posição depois
  da guarda `EntityTeleportEvent`; plugins que usem essa saída precisam ser testados.
- A busca vanilla pode carregar chunks previamente gerados. O plugin evita
  iniciar uma busca em região não gerada e não pré-gera mapas.
- Um JAR compilado ou testes de geometria não comprovam viagens reais. Veja
  [docs/TESTES.md](docs/TESTES.md) e [docs/API-REVIEW.md](docs/API-REVIEW.md).
