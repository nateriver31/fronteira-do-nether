# Ativar a escala 1:6 no Crafty — Minecraft 26.2

Pacote preparado para Minecraft **26.2**, incluindo o Paper **build 129** mostrado
no console. Usa a definição padrão do tipo `minecraft:the_nether` dessa versão,
alterando somente `coordinate_scale` de 8 para 6. Nenhum arquivo de geração de
terreno, bioma, estrutura, mundo ou borda está incluído.

[Baixar SurvivalPlus-Nether-Scale6-MC26.2.zip](https://github.com/nateriver31/fronteira-do-nether/raw/refs/heads/main/downloads/SurvivalPlus-Nether-Scale6-MC26.2.zip)

1. Faça um backup pelo Crafty. Teste primeiro na cópia do servidor.
2. Pare o servidor e aguarde encerrar completamente.
3. Em **Files**, abra a pasta **world → datapacks** mostrada na captura.
4. Clique em **Upload** e envie **SurvivalPlus-Nether-Scale6-MC26.2.zip**.
   Deixe o arquivo ZIP ali, **sem extrair** e sem colocá-lo em `plugins/`.
5. Inicie o servidor novamente e confira se o console mostra algum erro de datapack.
6. No console, execute:

   ```text
   datapack list enabled
   survivalplus nether status
   ```

7. O pacote deve aparecer na lista habilitada, e o status deve mostrar escala
   real **1:6** e sistema **ATIVO**. Envie uma captura desse resultado antes dos
   testes de viagem. O reload do plugin não substitui esta reinicialização.

Se o pacote aparecer somente na lista de desabilitados, execute:

```text
datapack enable "file/SurvivalPlus-Nether-Scale6-MC26.2.zip" last
```

Depois pare e inicie completamente o servidor mais uma vez e confira os dois
comandos de status. Se houver erro ou a escala continuar 1:8, mantenha as viagens
bloqueadas e envie o erro completo. Não altere `expected-scale` para disfarçar a
divergência. Se precisar desfazer, pare o servidor, retire somente esse ZIP de
`world/datapacks` e reinicie; preserve os demais arquivos.

## Compatibilidade e limites da verificação

O pacote preserva as propriedades da referência **padrão** 26.2. O console mostrou
apenas `vanilla` e `paper` habilitados; não tivemos acesso aos arquivos reais do
servidor. Se um gerenciador de mundos ou outro plugin tiver alterado o tipo de
dimensão, precisamos examinar essa definição antes de aplicar um override padrão.
Customização de terreno por si só não exige substituir biomas ou regiões para
mudar a escala. A procedência exata está em
[SOURCES.md](../datapacks/reference/26.2/SOURCES.md).

O ZIP foi verificado para conter o `pack.mcmeta` na raiz e somente um recurso de
jogo: o tipo do Nether. Os testes confirmam que apenas a escala mudou em relação
à referência. O carregamento real e a preservação das características do seu
servidor ainda precisam ser conferidos em homologação.
