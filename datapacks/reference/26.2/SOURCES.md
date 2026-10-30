# Origem dos dados 26.2

O pacote usa dados extraídos do Minecraft Java 26.2 publicados pelo projeto
comunitário **misode/mcmeta**. Esse repositório não é mantido pela Mojang. As
revisões abaixo foram fixadas para tornar a comparação reproduzível:

- `the_nether.json` e `vanilla-pack.mcmeta`: [26.2-data, commit 4d12c0553e21e461085d08dcb2c5d412398c494e](https://github.com/misode/mcmeta/tree/4d12c0553e21e461085d08dcb2c5d412398c494e).
- `version.json`: [26.2-summary, commit 711a353b47d84e6cb592a1b72f682e5f44759284](https://github.com/misode/mcmeta/blob/711a353b47d84e6cb592a1b72f682e5f44759284/version.json).
- Formato de dados extraído: **107.1**. O formato de resource packs é diferente
  e não foi usado. Os campos de formato coincidem entre o metadado extraído e
  o resumo da versão.
- `scale-pack.mcmeta` preserva os campos de formato e troca apenas a descrição
  para identificar o nosso pacote.

O console fornecido pelo usuário identifica Paper **26.2 build 129**, API
`26.2.build.129-stable`, e somente `vanilla` e `paper` habilitados. Isso fundamenta
o uso da definição padrão. Não inspecionamos `level.dat`, arquivos do servidor
nem modificações que algum plugin faça nos registros em memória. Se houver tipo
de dimensão ou propriedades personalizadas nesses registros, use o JSON efetivo
com o gerador em vez deste pacote padrão. A lista de datapacks não prova sozinha
a ausência de toda customização possível.

O JSON gerado conserva todos os campos da referência, inclusive `attributes`,
`timelines`, alturas e regras de ambiente. A única diferença semântica é
`coordinate_scale: 8.0 → 6.0`. Não há arquivos de biomas, estruturas, geradores
ou regiões neste pacote. Os testes verificam essa diferença e os hashes, mas não
substituem o carregamento no servidor. Não foi executado Paper com o datapack.

Para reproduzir, na raiz do projeto e com uma saída inexistente:

```sh
python3 tools/create_scale_datapack.py \
  --dimension-type datapacks/reference/26.2/the_nether.json \
  --pack-mcmeta datapacks/reference/26.2/scale-pack.mcmeta \
  --output /tmp/survivalplus-scale-6-reproduzido
```
