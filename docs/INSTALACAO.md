# Instalar no Crafty Controller

O arquivo instalável é **SurvivalPlusCore-0.1.0.jar**. O ZIP do projeto contém o
código-fonte; não coloque esse ZIP na pasta `plugins/`.

1. Use primeiro uma cópia do servidor para homologar, com os mesmos plugins e mundos.
2. [Baixe o JAR](https://github.com/nateriver31/fronteira-do-nether/raw/refs/heads/main/downloads/SurvivalPlusCore-0.1.0.jar).
   Se o navegador abrir a página do arquivo, use o botão **Download raw file**.
3. No Crafty, pare o servidor e aguarde o encerramento. Confirme **Paper 26.2** e
   **Java 25** na configuração de execução.
4. Envie o JAR para a pasta `plugins/`, sem extraí-lo. Não substitua outros plugins.
5. Inicie o servidor. Procure `SurvivalPlusCore` no console e confira a criação de
   `plugins/SurvivalPlusCore/config.yml`.
6. Como OP, execute `/survivalplus nether status`. No console do Crafty, normalmente
   digite `survivalplus nether status`, sem a barra inicial.
7. Confira as chaves reais dos mundos carregados mostradas pelo status e ajuste
   `dimensions` no `config.yml`, caso necessário. Use `/survivalplus nether reload`.
8. A escala real precisa ser **1:6**. Se o status indicar 1:8 ou outro motivo de
   bloqueio, as viagens ficarão canceladas até a correção. `expected-scale: 6`
   não altera o Minecraft. Siga [DATAPACK.md](DATAPACK.md) usando os arquivos
   efetivos da sua dimensão personalizada, e reinicie completamente após mudar
   o datapack. Não é seguro fornecer um override genérico sem inspecionar esses dados.
9. Construa e acenda manualmente os portais de saída dentro da zona: a criação
   automática é bloqueada para preservar o terreno. Execute [TESTES.md](TESTES.md).

O JAR compilou e passou nos testes automatizados descritos em
[VALIDACAO.md](VALIDACAO.md). As viagens reais com Java, Bedrock e os demais plugins
ainda exigem homologação no seu servidor.

Se falhar ao carregar, confira no console a primeira mensagem relacionada ao plugin:

- `UnsupportedClassVersionError`: o processo está usando um Java anterior ao 25.
- `Unsupported API version`: confira se o servidor é realmente Paper 26.2.
- Estado `BLOQUEADO`: leia o motivo no status; pode ser escala, chaves dos mundos
  ou configuração inválida. Isso é uma proteção ativa, não ausência de instalação.

Para conferir a integridade do download, compare o SHA256 com o arquivo `.sha256`
da pasta [downloads](../downloads/).
