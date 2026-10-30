# Escala real das dimensões: preparação do datapack

O campo `expected-scale` do plugin é uma expectativa, não altera o registro de
dimensões do Minecraft. Para a conversão vanilla produzir 1:6, a razão entre
`coordinate_scale` do tipo do Nether e do tipo do Overworld deve ser 6. Se o
Overworld efetivo usa 1, o tipo do Nether deve usar 6.

## O que está entregue e o que exige os arquivos do servidor

A ferramenta `tools/create_scale_datapack.py` produz um datapack de verdade a
partir dos arquivos **efetivos da sua instalação**. Ela preserva todas as
propriedades do tipo de dimensão e altera somente `coordinate_scale`, além da
formatação JSON. Não inclui definições de biomas, estruturas, recursos, blocos,
geradores de terreno nem arquivos de regiões. O `pack.mcmeta` é copiado sem
alterar seus bytes. O relatório `scale-provenance.json` registra hashes para
revisão; não é conteúdo de geração do Minecraft.

Não acompanha um JSON vanilla ou número de formato presumido para 26.2. Neste
ambiente não foi possível consultar os artefatos oficiais nem os datapacks do
servidor. Uma definição completa de uma versão anterior pode apagar propriedades
novas ou personalizadas. **Não instale um pack especulativo.** A ferramenta
valida estrutura básica de JSON e metadados; não certifica compatibilidade de
versão ou a origem dos arquivos fornecidos.

## Antes de gerar

1. No Crafty, pare o servidor e faça backup de todos os mundos, configurações e
   datapacks, incluindo `level.dat`. Trabalhe primeiro em uma cópia de homologação.
2. Identifique os mundos carregados e os tipos de dimensão que usam. A chave do
   mundo e o identificador do tipo de dimensão são conceitos diferentes. Confirme
   que o Nether usa o tipo `minecraft:the_nether`. Se usa, por exemplo,
   `custom:nether`, esta ferramenta **não** altera esse tipo: será necessária uma
   adaptação revisada para o caminho correto.
3. Examine a lista e a prioridade dos datapacks habilitados, e todos os recursos
   `data/*/dimension_type/*.json`. Confira também os recursos de dimensão que
   apontam para esses tipos. Procure especialmente overrides de
   `minecraft:the_nether` e definições do Overworld. Um tipo é substituído por
   inteiro, não mesclado campo a campo entre packs.
4. Obtenha o JSON **efetivo** completo do tipo do Nether na versão exata do seu
   servidor, incluindo as alterações dos packs com prioridade. Use uma exportação
   de registros compatível com essa versão, ou a definição do pack vencedor,
   conferida contra os dados oficiais da versão instalada. Um arquivo vanilla
   isolado não basta se existir override personalizado. Esta ferramenta não
   consegue descobrir automaticamente qual pack vence.
5. Obtenha um `pack.mcmeta` mínimo, comprovadamente válido como **datapack** na
   versão exata do servidor. Consulte os metadados oficiais dessa versão; não use
   o número de formato de resource packs ou de outra versão. O objeto raiz deve
   conter somente `pack`, com a descrição e os campos de formato válidos dessa
   versão. Metadados com `filter`, `features` ou `overlays` são recusados para não
   interferir com outros recursos. O gerador aceita a forma legada `pack_format`
   e a forma `min_format`/`max_format` sem escolher números por você.
6. Confirme a escala do tipo efetivo do Overworld. Se ela não for 1, `--scale 6`
   não produzirá a razão 1:6: a escala do Nether precisa ser seis vezes a do
   Overworld. Mantenha o plugin esperando a **razão** 6.

## Gerar e revisar

Na raiz do projeto, com Python 3.10 ou mais recente:

```bash
python3 tools/create_scale_datapack.py \
  --dimension-type /caminho/revisao/nether-efetivo.json \
  --pack-mcmeta /caminho/revisao/pack.mcmeta \
  --scale 6 \
  --output /caminho/revisao/survivalplus-scale-6
```

O diretório pai da saída deve existir; a saída não pode existir. Arquivos ou
diretórios ligados por links simbólicos são recusados. A ferramenta nunca mescla
arquivos em um pack existente. Confirme o sucesso antes de usar a saída; uma falha
de escrita pode deixar um diretório incompleto, que não deve ser instalado.

Arquivos gerados:

```text
survivalplus-scale-6/
  pack.mcmeta
  scale-provenance.json
  data/minecraft/dimension_type/the_nether.json
```

Compare o JSON de entrada com o de saída: a única diferença semântica deve ser
`coordinate_scale`. Confirme particularmente os campos novos da versão e os
campos personalizados. A ferramenta não deve receber credenciais ou arquivos de
configuração contendo segredos.

## Instalar e ativar no Crafty

1. Com o servidor parado, copie o diretório gerado inteiro para `datapacks/` do
   mundo principal definido em `level-name` no `server.properties`. Não presuma
   que o diretório se chama `world` ou que deve ser colocado em `DIM-1`.
2. Inicie a cópia de homologação e examine os logs de carregamento. Verifique
   `datapack list enabled` pelo console do Crafty e a prioridade dos packs. Se o
   pack estiver desabilitado, use `datapack enable "file/survivalplus-scale-6"`
   somente após revisar os conflitos. Ajuste a prioridade conscientemente: um
   override posterior do mesmo tipo pode anular a escala 6; colocar este pack por
   último sem usar o JSON efetivo correto pode anular propriedades personalizadas.
3. Faça uma parada e uma inicialização completas depois de qualquer alteração de
   tipo de dimensão ou prioridade. Não use `/reload` do servidor para considerar
   a mudança concluída. O reload do SurvivalPlusCore apenas relê seu `config.yml`.
4. Consulte `/survivalplus nether status`. Se a escala real não puder ser lida ou
   divergir de 6, preserve o bloqueio seguro dos portais e investigue os registros
   e os packs; não assuma que editar a configuração corrigiu a dimensão.
5. Valide os portais nos dois sentidos e a razão efetiva em homologação, incluindo
   Java e Bedrock. O Minecraft pode selecionar um portal existente próximo: teste
   a correspondência nominal sem portais concorrentes, e teste separadamente a
   busca por saídas.

Este procedimento não redimensiona mapas, não reduz bordas físicas e não altera
biomas ou estruturas. Ainda assim, a prioridade incorreta ou uma definição
efetiva errada de terceiros pode afetar propriedades da dimensão: a revisão é
necessária. Depois de atualizar Minecraft ou datapacks personalizados, reexporte
os dados efetivos e gere um novo pack revisado; não reutilize cegamente um
override completo de uma versão anterior.

## Testes da ferramenta

```bash
python3 -m unittest discover -s tools -p 'test_create_scale_datapack.py' -v
```

Os testes usam metadados sintéticos; seus números de formato não são instruções
de instalação. Validam preservação de campos desconhecidos, hashes, cópia exata
dos metadados, escalas inválidas, JSON ambíguo, recusa de sobrescrita e links.
Não substituem o carregamento do datapack na versão exata do servidor.
