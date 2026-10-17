package com.pcberti.batalhanaval;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Rede local para dois jogadores, exposta ao jogo como window.Rede.
 *
 * Quem cria a partida abre um servidor TCP e se anuncia por broadcast UDP a cada segundo.
 * Quem procura escuta esses anúncios e mostra as partidas encontradas. Ao entrar, os dois
 * conversam pela conexão TCP, com uma mensagem JSON por linha.
 *
 * O jogo recebe os eventos em window.onRede(tipo, dadosJson):
 * hosting {ip} · found {nome, ip, porta} · connected {role} · msg (a linha recebida) · closed · error {texto}
 */
public class Rede {
    static final int PORTA_TCP = 47800;
    static final int PORTA_UDP = 47801;
    static final String ASSINATURA = "BNAVAL1";
    /** Sem ouvir nada por esse tempo, a conexão é dada como perdida (o jogo manda ping a cada 4 s). */
    static final int SILENCIO_MAX_MS = 15000;

    private final WebView web;
    private final WifiManager.MulticastLock trava;
    private final ExecutorService envio = Executors.newSingleThreadExecutor();

    private volatile ServerSocket servidor;
    private volatile Socket conexao;
    private volatile Writer saida;
    private volatile DatagramSocket escuta;
    private volatile boolean anunciando;
    private volatile boolean procurando;
    private volatile int geracao; // muda a cada sair(), para threads antigas não falarem com o jogo

    public Rede(Context contexto, WebView web) {
        this.web = web;
        WifiManager wifi = (WifiManager) contexto.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        trava = wifi == null ? null : wifi.createMulticastLock("batalha-naval");
        if (trava != null) trava.setReferenceCounted(false);
    }

    // ---------- chamadas do jogo ----------

    @JavascriptInterface
    public void criar(final String nome) {
        sair();
        final int g = geracao;
        anunciando = true;
        new Thread(new Runnable() {
            public void run() {
                ServerSocket s = null;
                try {
                    s = new ServerSocket();
                    s.setReuseAddress(true);
                    s.bind(new InetSocketAddress(PORTA_TCP));
                    servidor = s;
                    avisar(g, "hosting", new JSONObject().put("ip", meuIp()));
                    anunciar(g, nome);
                    Socket c = s.accept();
                    anunciando = false;
                    fechar(s);
                    conectado(g, c, "host");
                } catch (Exception e) {
                    anunciando = false;
                    fechar(s);
                    if (g == geracao) erro(g, "Não foi possível criar a partida. Confira se o Wi-Fi está ligado e tente de novo.");
                }
            }
        }, "rede-servidor").start();
    }

    @JavascriptInterface
    public void procurar() {
        sair();
        final int g = geracao;
        procurando = true;
        new Thread(new Runnable() {
            public void run() {
                DatagramSocket d = null;
                try {
                    if (trava != null) trava.acquire();
                    d = new DatagramSocket(null);
                    d.setReuseAddress(true);
                    d.setBroadcast(true);
                    d.setSoTimeout(1000);
                    d.bind(new InetSocketAddress(PORTA_UDP));
                    escuta = d;
                    byte[] buf = new byte[512];
                    while (procurando && g == geracao) {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        try {
                            d.receive(p);
                        } catch (SocketTimeoutException e) {
                            continue;
                        }
                        String[] partes = new String(p.getData(), 0, p.getLength(), "UTF-8").split("\\|", 3);
                        if (partes.length < 3 || !ASSINATURA.equals(partes[0])) continue;
                        int porta;
                        try {
                            porta = Integer.parseInt(partes[2].trim());
                        } catch (NumberFormatException e) {
                            continue;
                        }
                        avisar(g, "found", new JSONObject()
                                .put("nome", partes[1])
                                .put("ip", p.getAddress().getHostAddress())
                                .put("porta", porta));
                    }
                } catch (Exception e) {
                    if (procurando && g == geracao) erro(g, "Não foi possível procurar partidas. Confira se o Wi-Fi está ligado.");
                } finally {
                    fechar(d);
                    if (trava != null && trava.isHeld()) trava.release();
                }
            }
        }, "rede-busca").start();
    }

    @JavascriptInterface
    public void pararBusca() {
        procurando = false;
        fechar(escuta);
    }

    @JavascriptInterface
    public void entrar(final String ip, final int porta) {
        sair();
        final int g = geracao;
        new Thread(new Runnable() {
            public void run() {
                Socket c = new Socket();
                try {
                    c.connect(new InetSocketAddress(ip, porta), 6000);
                    conectado(g, c, "guest");
                } catch (Exception e) {
                    fechar(c);
                    if (g == geracao) erro(g, "Não consegui entrar na partida em " + ip + ". Confira se os dois celulares estão no mesmo Wi-Fi e se a partida ainda está aberta.");
                }
            }
        }, "rede-entrar").start();
    }

    @JavascriptInterface
    public void enviar(final String linha) {
        final Writer w = saida;
        if (w == null || linha == null) return;
        envio.execute(new Runnable() {
            public void run() {
                try {
                    synchronized (w) {
                        w.write(linha.replace('\n', ' '));
                        w.write('\n');
                        w.flush();
                    }
                } catch (Exception e) {
                    fechar(conexao); // a leitura percebe e avisa "closed"
                }
            }
        });
    }

    @JavascriptInterface
    public void sair() {
        geracao++;
        anunciando = false;
        procurando = false;
        fechar(servidor);
        fechar(escuta);
        fechar(conexao);
        servidor = null;
        escuta = null;
        conexao = null;
        saida = null;
    }

    @JavascriptInterface
    public String meuIp() {
        for (InterfaceAddress a : enderecos()) return a.getAddress().getHostAddress();
        return "";
    }

    // ---------- por dentro ----------

    private void anunciar(final int g, final String nome) {
        new Thread(new Runnable() {
            public void run() {
                DatagramSocket d = null;
                try {
                    d = new DatagramSocket();
                    d.setBroadcast(true);
                    byte[] msg = (ASSINATURA + "|" + limpar(nome) + "|" + PORTA_TCP).getBytes("UTF-8");
                    InetAddress geral = InetAddress.getByName("255.255.255.255");
                    while (anunciando && g == geracao) {
                        // o broadcast da sub-rede de cada interface (Wi-Fi e hotspot) e o broadcast geral
                        for (InterfaceAddress a : enderecos()) {
                            if (a.getBroadcast() != null) enviarUdp(d, msg, a.getBroadcast());
                        }
                        enviarUdp(d, msg, geral);
                        Thread.sleep(1000);
                    }
                } catch (Exception e) {
                    // sem anúncio, o outro ainda pode entrar digitando o endereço
                } finally {
                    fechar(d);
                }
            }
        }, "rede-anuncio").start();
    }

    private static void enviarUdp(DatagramSocket d, byte[] msg, InetAddress destino) {
        try {
            d.send(new DatagramPacket(msg, msg.length, destino, PORTA_UDP));
        } catch (Exception e) {
            // uma interface sem rota não impede as outras
        }
    }

    private void conectado(int g, Socket c, String papel) throws Exception {
        if (g != geracao) {
            fechar(c);
            return;
        }
        c.setTcpNoDelay(true);
        c.setSoTimeout(SILENCIO_MAX_MS);
        conexao = c;
        saida = new OutputStreamWriter(c.getOutputStream(), "UTF-8");
        avisar(g, "connected", new JSONObject().put("role", papel));
        try {
            BufferedReader entrada = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            String linha;
            while ((linha = entrada.readLine()) != null && g == geracao) {
                if (linha.length() > 4096) continue;
                avisar(g, "msg", linha);
            }
        } catch (Exception e) {
            // conexão caiu ou ficou em silêncio demais
        }
        fechar(c);
        if (g == geracao) {
            conexao = null;
            saida = null;
            avisar(g, "closed", "{}");
        }
    }

    /** Endereços IPv4 das interfaces ligadas, com o Wi-Fi primeiro. */
    private static List<InterfaceAddress> enderecos() {
        List<InterfaceAddress> wifi = new ArrayList<InterfaceAddress>();
        List<InterfaceAddress> outros = new ArrayList<InterfaceAddress>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                boolean ehWifi = ni.getName().startsWith("wlan") || ni.getName().startsWith("ap") || ni.getName().startsWith("swlan");
                for (InterfaceAddress a : ni.getInterfaceAddresses()) {
                    if (!(a.getAddress() instanceof Inet4Address)) continue;
                    (ehWifi ? wifi : outros).add(a);
                }
            }
        } catch (Exception e) {
            // sem lista de interfaces, sobra o broadcast geral
        }
        wifi.addAll(outros);
        return wifi;
    }

    private static String limpar(String nome) {
        String n = nome == null ? "" : nome.replace('|', ' ').replace('\n', ' ').trim();
        if (n.isEmpty()) n = "Jogador";
        return n.length() > 16 ? n.substring(0, 16) : n;
    }

    private void erro(int g, String texto) {
        try {
            avisar(g, "error", new JSONObject().put("texto", texto));
        } catch (Exception e) {
            // JSONObject.put só falha com chave nula
        }
    }

    private void avisar(int g, String tipo, JSONObject dados) {
        avisar(g, tipo, dados.toString());
    }

    private void avisar(final int g, String tipo, String dados) {
        if (g != geracao) return;
        final String js = "window.onRede&&window.onRede(" + JSONObject.quote(tipo) + "," + JSONObject.quote(dados) + ")";
        web.post(new Runnable() {
            public void run() {
                web.evaluateJavascript(js, null);
            }
        });
    }

    private static void fechar(java.io.Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception e) {
            // já estava fechado
        }
    }

    private static void fechar(Socket c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception e) {
            // já estava fechado
        }
    }

    private static void fechar(ServerSocket c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception e) {
            // já estava fechado
        }
    }

    private static void fechar(DatagramSocket c) {
        if (c == null) return;
        c.close();
    }
}
