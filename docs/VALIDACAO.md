# Relatório de validação

Versão **0.3.0**. Este relatório descreve a máquina de desenvolvimento, não o
servidor do Survival+. A compilação e os testes automatizados abaixo foram
executados para o código final da versão 0.3.0.

| Verificação | Resultado |
|---|---|
| `mvn -B clean verify`, JDK 25, Maven 3.9.9 | BUILD SUCCESS |
| API de compilação | `io.papermc.paper:paper-api:26.2.build.135-stable`, repositório oficial |
| Testes Maven | 44 passaram; 0 falhas, 0 erros, 0 ignorados |
| Configuração e geometria | 23 testes de configuração; 2 de projeção; 18 da guarda; 1 suíte com 1.106 verificações de geometria passaram |
| Gerador e pacote de datapack | 22 testes Python passaram novamente; pacote inalterado |
| JAR | `SurvivalPlusCore-0.3.0.jar`; 37063 bytes; bytecode Java 25, descritor, configuração e SHA256 conferidos |
| Fluxo de eventos e leitura de escala | Conferidos na fonte oficial Paper 26.2, SHA em API-REVIEW.md |
| Testes em Minecraft, Geyser e outros plugins | Não executados |
| Datapack 26.2 | ZIP gerado com base no tipo padrão 26.2; formato 107.1, diferença somente na escala e integridade do ZIP conferidos; escala real 1:6 confirmada no console do usuário; viagens da versão 0.3 pendentes |

JDK 25 (Oracle, checksum SHA256 conferido) e Maven 3.9.9 (Maven Central, SHA512
conferido) foram obtidos de fontes oficiais. O Maven usou o proxy da sessão e o
truststore do sistema; TLS e verificações de integridade foram preservados.

A entrega anterior estava bloqueada por HTTP 403 no repositório Paper. A política
agora libera os domínios oficiais, e a dependência foi resolvida. O POM fixa o
build `26.2.build.135-stable` usado para esta entrega, em lugar do intervalo anterior.

O JAR distribuído corresponde exatamente ao artefato produzido, sem alterações. Confira o checksum em
`downloads/SurvivalPlusCore-0.3.0.jar.sha256`.

Permanecem pendentes:

1. Examinar os tipos de dimensão e datapacks reais; gerar o override somente a
   partir do JSON efetivo e metadados válidos da versão, se a escala ainda não for 6.
2. Testar em cópia do servidor seguindo TESTES.md e registrar os resultados reais.

O código não emite teletransportes próprios nem escreve blocos diretamente. Ele
permite a busca e a criação vanilla de uma saída, com validação dos blocos propostos
antes da aplicação e validação do destino final. A operação local do Minecraft
pode construir obsidiana/base e limpar espaço; não há corte ou regeneração global.
Não se exige portal de saída construído manualmente nem chunks previamente gerados.
O custo da busca, criação e carregamento/geração dos chunks necessários à viagem
precisa ser observado no servidor. Não se promete ausência de I/O ou tickets.

## Correção 0.3.0

A versão anterior recusava viagens para regiões sem pré-geração e impedia criação
automática. Essas duas exigências foram removidas. A regra atual conserva a
conexão de todo o Overworld em 1:6, limita o raio de busca/criação e confere a área
completa dos blocos de saída no `PortalCreateEvent`, antes de aplicar os snapshots.
Perto da borda o alvo pode ser acomodado alguns blocos para dentro; o Minecraft
continua responsável pela escala. O datapack e a configuração existentes são
mantidos. Os testes reais de primeira viagem, retorno e clientes Java/Bedrock
continuam pendentes, conforme [TESTES.md](TESTES.md).

## Pacote de escala 26.2

O usuário confirmou Paper 26.2 build 129 e apenas `vanilla`/`paper` habilitados.
Foi incluído o ZIP padrão 26.2 e referências fixadas do projeto de extração
comunitário misode/mcmeta. Quatro testes adicionais conferem que somente
`coordinate_scale` muda, os campos de formato coincidem com os dados 26.2,
o ZIP tem metadados na raiz e os hashes correspondem. Não houve instalação nem
inicialização do pacote no servidor real. Veja INSTALAR-ESCALA-6.md e as ressalvas
sobre tipos personalizados em datapacks/reference/26.2/SOURCES.md.

Os testes da guarda usam interfaces Paper com fixtures de mundo/entidade. Eles
verificam ausência de consultas de pré-geração, autorizações, acomodação nas
bordas, raio de retorno, volume da entidade e cada bloco proposto. Não executam
a geração real do Minecraft nem comprovam a integração dos listeners em produção.
