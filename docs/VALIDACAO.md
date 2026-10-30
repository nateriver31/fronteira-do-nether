# Relatório de validação

Atualizado em 29/10/2026 (America/Sao_Paulo). Este relatório descreve a máquina
de desenvolvimento, não o servidor do Survival+.

| Verificação | Resultado |
|---|---|
| `mvn -B clean verify`, JDK 25, Maven 3.9.9 | BUILD SUCCESS |
| API de compilação | `io.papermc.paper:paper-api:26.2.build.135-stable`, repositório oficial |
| Testes Maven | 24 passaram; 0 falhas, 0 erros, 0 ignorados |
| Configuração | 23 testes JUnit passaram |
| Geometria | 1 suíte JUnit com 1.106 verificações passou; também passou pelo script independente |
| Gerador e pacote de datapack | 22 testes Python passaram: 18 do gerador e 4 do pacote |
| JAR | Gerado; 35.506 bytes; bytecode Java 25 (major 69), descritor e configuração conferidos |
| Fluxo de eventos e leitura de escala | Conferidos na fonte oficial Paper 26.2, SHA em API-REVIEW.md |
| Testes em Minecraft, Geyser e outros plugins | Não executados |
| Datapack 26.2 | ZIP gerado com base no tipo padrão 26.2; formato 107.1, diferença somente na escala e integridade do ZIP conferidos; carga no servidor pendente |

JDK 25 (Oracle, checksum SHA256 conferido) e Maven 3.9.9 (Maven Central, SHA512
conferido) foram obtidos de fontes oficiais. O Maven usou o proxy da sessão e o
truststore do sistema; TLS e verificações de integridade foram preservados.

A entrega anterior estava bloqueada por HTTP 403 no repositório Paper. A política
agora libera os domínios oficiais, e a dependência foi resolvida. O POM fixa o
build `26.2.build.135-stable` usado para esta entrega, em lugar do intervalo anterior.

O JAR distribuído é uma cópia do artefato produzido por esse build, sem alterações.
Confira o checksum em `downloads/SurvivalPlusCore-0.1.0.jar.sha256`.

Permanecem pendentes:

1. Examinar os tipos de dimensão e datapacks reais; gerar o override somente a
   partir do JSON efetivo e metadados válidos da versão, se a escala ainda não for 6.
2. Testar em cópia do servidor seguindo TESTES.md e registrar os resultados reais.

O código não emite teletransportes nem escreve blocos. A busca vanilla por uma
saída existente ainda pode carregar chunks e consultar POIs; seu custo e eventuais
carregamentos adicionais precisam ser observados no servidor. `isChunkGenerated`
não gera terreno, mas pode fazer I/O síncrono. Não se promete ausência absoluta
de carregamento ou tickets do Minecraft.

## Pacote de escala 26.2

O usuário confirmou Paper 26.2 build 129 e apenas `vanilla`/`paper` habilitados.
Foi incluído o ZIP padrão 26.2 e referências fixadas do projeto de extração
comunitário misode/mcmeta. Quatro testes adicionais conferem que somente
`coordinate_scale` muda, os campos de formato coincidem com os dados 26.2,
o ZIP tem metadados na raiz e os hashes correspondem. Não houve instalação nem
inicialização do pacote no servidor real. Veja INSTALAR-ESCALA-6.md e as ressalvas
sobre tipos personalizados em datapacks/reference/26.2/SOURCES.md.
