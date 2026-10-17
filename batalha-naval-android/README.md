# Batalha Naval para Android

App Android do jogo em [`../batalha-naval/index.html`](../batalha-naval/index.html), com um modo a mais: **dois jogadores no mesmo Wi-Fi**, sem internet e sem servidor.

## Como jogar a dois

1. Os dois celulares entram no mesmo Wi-Fi. Sem roteador por perto, um celular liga o roteador Wi-Fi (hotspot) e o outro se conecta nele.
2. Nos dois, toque em **Amigo no Wi-Fi** e escreva seu nome.
3. Um toca em **Criar partida**. O outro toca em **Procurar partidas** e depois em **Entrar** na partida que aparecer.
4. Cada um arruma a frota e toca em **Estou pronto**. O app sorteia quem começa.

Se a partida não aparecer na lista (algumas redes bloqueiam broadcast, como Wi-Fi de visitante ou de empresa), toque em **A partida não aparece? Entre pelo endereço** e digite o endereço que aparece no celular que criou a partida.

## Como funciona a rede

Fica em [`src/com/pcberti/batalhanaval/Rede.java`](src/com/pcberti/batalhanaval/Rede.java), exposta ao jogo como `window.Rede`:

- Quem cria a partida abre um servidor TCP na porta **47800** e se anuncia por **broadcast UDP** na porta **47801** a cada segundo (`BNAVAL1|nome|47800`).
- Quem procura escuta essa porta UDP e mostra as partidas encontradas.
- Depois, os dois conversam pela conexão TCP, com uma mensagem JSON por linha. Cada celular guarda a própria frota e responde aos tiros do outro. O protocolo está descrito no começo da seção "dois jogadores no Wi-Fi" do `index.html`.
- O jogo manda um `ping` a cada 4 s. Se a conexão ficar 15 s em silêncio, o app avisa que ela caiu.

No navegador (página publicada ou arquivo baixado) `window.Rede` não existe, e o jogo fica só contra o computador.

## Como gerar o .apk

Não precisa de Android Studio nem Gradle. Num Ubuntu ou Debian:

```sh
sudo apt install openjdk-17-jdk-headless python3 aapt apksigner zipalign dalvik-exchange android-sdk-platform-23
./build.sh
```

O app sai em `build/batalha-naval.apk`. O script:

1. monta `build/assets/index.html` a partir do jogo (`montar_html.py`), trocando o Google Fonts pelas fontes de `assets/fonts`;
2. compila os recursos, o Java e o dex;
3. empacota, alinha e assina.

A chave de assinatura fica em `~/.android/batalha-naval.jks` e é criada na primeira vez. Guarde esse arquivo: o Android só instala uma versão nova por cima da antiga se as duas tiverem sido assinadas pela mesma chave. Com outra chave, é preciso desinstalar o app antes.

## Instalar no celular

Mande o `.apk` para o celular (por mensagem, cabo ou Google Drive), toque nele e permita instalar apps desta fonte quando o Android perguntar.
