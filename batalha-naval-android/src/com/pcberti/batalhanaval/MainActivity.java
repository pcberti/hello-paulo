package com.pcberti.batalhanaval;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Uma tela só: o jogo em HTML (assets/index.html) dentro de um WebView, com a rede local exposta como window.Rede. */
public class MainActivity extends Activity {
    private WebView web;
    private Rede rede;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#051019"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());

        rede = new Rede(this, web);
        web.addJavascriptInterface(rede, "Rede");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onDestroy() {
        rede.sair();
        web.destroy();
        super.onDestroy();
    }
}
