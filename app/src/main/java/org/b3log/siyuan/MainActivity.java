/*
 * SiYuan - 源于思考，饮水思源
 * Copyright (c) 2020-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.siyuan;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.blankj.utilcode.util.KeyboardUtils;
import com.zackratos.ultimatebarx.ultimatebarx.java.UltimateBarX;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.TimeZone;

import mobile.Mobile;

/**
 * 程序入口.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.2.3, Feb 22, 2022
 * @since 1.0.0
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ImageView bootLogo;
    private ProgressBar bootProgressBar;
    private TextView bootDetailsText;
    private Handler handler;
    private final String version = BuildConfig.VERSION_NAME;

    public ValueCallback<Uri[]> uploadMessage;
    public static final int REQUEST_SELECT_FILE = 100;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bootLogo = findViewById(R.id.bootLogo);
        bootProgressBar = findViewById(R.id.progressBar);
        bootDetailsText = findViewById(R.id.bootDetails);
        webView = findViewById(R.id.webView);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView mWebView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                }
                uploadMessage = filePathCallback;
                final Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, REQUEST_SELECT_FILE);
                } catch (final Exception e) {
                    uploadMessage = null;
                    Toast.makeText(getApplicationContext(), "Cannot open file chooser", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }
        });
        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(final Message msg) {
                final String cmd = msg.getData().getString("cmd");
                if ("startKernel".equals(cmd)) {
                    bootKernel();
                } else if ("agreement-y".equals(cmd)) {
                } else if ("agreement-n".equals(cmd)) {
                    final String dataDir = getFilesDir().getAbsolutePath();
                    final String appDir = dataDir + "/app";
                    final File appDirFile = new File(appDir);
                    try {
                        FileUtils.deleteQuietly(appDirFile);
                    } catch (final Exception e) {
                        Log.e("", "Delete [" + appDirFile.getAbsolutePath() + "] failed", e);
                    }

                    finishAndRemoveTask();
                    Log.i("", "User did not accept the agreement, exit");
                    System.exit(0);
                } else {
                    showBootIndex();
                }
            }
        };

        // 沉浸式状态栏设置
        UltimateBarX.statusBarOnly(this).
                transparent().
                light(false).
                color(Color.parseColor("#212224")).
                apply();
        ((ViewGroup) webView.getParent()).setPadding(0, UltimateBarX.getStatusBarHeight(), 0, 0);

        // fixAndroidBug5497
        KeyboardUtils.fixAndroidBug5497(this);

        final String dataDir = getFilesDir().getAbsolutePath();
        final String appDir = dataDir + "/app";
        final File appDirFile = new File(appDir);
        if (!appDirFile.exists()) {
            // 首次运行弹窗提示用户隐私条款和使用授权
            showAgreements();
        }

        init();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showBootIndex() {
        webView.setVisibility(View.VISIBLE);
        bootLogo.setVisibility(View.GONE);
        bootProgressBar.setVisibility(View.GONE);
        bootDetailsText.setVisibility(View.GONE);
        final ImageView bootLogo = findViewById(R.id.bootLogo);
        bootLogo.setVisibility(View.GONE);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(final WebView view, final String url) {
                if (url.contains("127.0.0.1")) {
                    view.loadUrl(url);
                } else if (url.contains("siyuan://api/system/exit")) {
                    finishAndRemoveTask();
                    System.exit(0);
                } else {
                    final Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    final ActivityInfo info = i.resolveActivityInfo(getPackageManager(), PackageManager.MATCH_ALL);
                    if (null == info || !info.exported) {
//                        Toast.makeText(getApplicationContext(), "No application that can handle this link [" + url + "]", Toast.LENGTH_LONG).show();
                    } else {
                        startActivity(i);
                    }
                }
                return true;
            }
        });

        final JSAndroid JSAndroid = new JSAndroid(this);
        webView.addJavascriptInterface(JSAndroid, "JSAndroid");
        final WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        ws.setTextZoom(100);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUserAgentString("SiYuan/" + version + " https://b3log.org/siyuan " + ws.getUserAgentString());
        waitFotKernelHttpServing();
        webView.loadUrl("http://127.0.0.1:6806/appearance/boot/index.html");

        new Thread(this::keepLive).start();
    }

    private void bootKernel() {
        final String dataDir = getFilesDir().getAbsolutePath();
        final String appDir = dataDir + "/app";

        final Locale locale = getResources().getConfiguration().locale;
        final String lang = locale.getLanguage() + "_" + locale.getCountry();
        Mobile.setDefaultLang(lang);
        final String localIP = Utils.getIpAddressString();
        final String workspaceDir = getWorkspacePath();
        final String timezone = TimeZone.getDefault().getID();
        new Thread(() -> {
            Mobile.startKernel("android", appDir, workspaceDir, getApplicationInfo().nativeLibraryDir, dataDir, localIP, timezone);
        }).start();
        sleep(100);
        final Bundle b = new Bundle();
        b.putString("cmd", "bootIndex");
        final Message msg = new Message();
        msg.setData(b);
        handler.sendMessage(msg);
    }

    /**
     * 等待内核 HTTP 服务伺服。
     */
    private void waitFotKernelHttpServing() {
        for (int i = 0; i < 500; i++) {
            sleep(10);
            if (Mobile.isHttpServing()) {
                break;
            }
        }
    }

    private void init() {
        if (needUnzipAssets()) {
            bootLogo.setVisibility(View.VISIBLE);
            bootProgressBar.setVisibility(View.VISIBLE);
            bootDetailsText.setVisibility(View.VISIBLE);

            final String dataDir = getFilesDir().getAbsolutePath();
            final String appDir = dataDir + "/app";
            final File appVerFile = new File(appDir, "VERSION");

            setBootProgress("Clearing appearance...", 20);
            try {
                FileUtils.deleteDirectory(new File(appDir));
            } catch (final Exception e) {
                Log.wtf("", "Delete dir [" + appDir + "] failed, exit application", e);
                System.exit(-1);
            }

            setBootProgress("Initializing appearance...", 60);
            Utils.unzipAsset(getAssets(), "app.zip", appDir + "/app");

            try {
                FileUtils.writeStringToFile(appVerFile, version, StandardCharsets.UTF_8);
            } catch (final Exception e) {
                Log.w("", "Write version failed", e);
            }

            setBootProgress("Booting kernel...", 80);
            final Bundle b = new Bundle();
            b.putString("cmd", "startKernel");
            final Message msg = new Message();
            msg.setData(b);
            handler.sendMessage(msg);
        } else {
            final Bundle b = new Bundle();
            b.putString("cmd", "startKernel");
            final Message msg = new Message();
            msg.setData(b);
            handler.sendMessage(msg);
        }
    }

    /**
     * 通知栏保活。
     */
    private void keepLive() {
        while (true) {
            try {
                final Intent intent = new Intent(MainActivity.this, WhiteService.class);
                ContextCompat.startForegroundService(this, intent);
                sleep(45 * 1000);
                stopService(intent);
            } catch (final Throwable t) {
            }
        }
    }

    private void setBootProgress(final String text, final int progressPercent) {
        runOnUiThread(() -> {
            bootDetailsText.setText(text);
            bootProgressBar.setProgress(progressPercent);
        });
    }

    private String getWorkspacePath() {
        return getExternalFilesDir("siyuan").getAbsolutePath();
    }

    private void sleep(final long time) {
        try {
            Thread.sleep(time);
        } catch (final Exception e) {
            Log.e("", e.getMessage());
        }
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("javascript:window.goBack()", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_SELECT_FILE) {
            if (uploadMessage == null) {
                return;
            }
            uploadMessage.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent));
            uploadMessage = null;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    private boolean needUnzipAssets() {
        final String dataDir = getFilesDir().getAbsolutePath();
        final String appDir = dataDir + "/app";
        final File appDirFile = new File(appDir);
        appDirFile.mkdirs();

        boolean ret = true;
        final File appVerFile = new File(appDir, "VERSION");
        if (appVerFile.exists()) {
            try {
                final String ver = FileUtils.readFileToString(appVerFile, StandardCharsets.UTF_8);
                ret = !ver.equals(version);
            } catch (final Exception e) {
                Log.w("", "Check version failed", e);
            }
        }
        return ret;
    }

    private void showAgreements() {
        final TextView msg = new TextView(this);
        msg.setPadding(32, 32, 32, 32);
        msg.setMovementMethod(new ScrollingMovementMethod());
        msg.setText(Html.fromHtml("<div class=\"protyle-wysiwyg protyle-wysiwyg--attr\" style=\"max-width: 800px;margin: 0 auto;\" id=\"preview\"><h1 id=\"隐私政策\">隐私政策</h1>\n" +
                "<ul>\n" +
                "<li id=\"20220212223827-wj4ictk\">\n" +
                "<p>发布日期：2022 年 2 月 11 日</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-lhwo4ut\">\n" +
                "<p>最新日期：2022 年 2 月 11 日</p>\n" +
                "</li>\n" +
                "</ul>\n" +
                "<p><strong>SiYuan（思源笔记）</strong>是云南链滴科技有限公司（以下简称“我们”）通过合法拥有并运营的、包括且不限于思源笔记桌面端软件、移动端应用、<strong>思源笔记云端服务</strong>。思源笔记云端服务以及该服务所包括的各种业务功能统称为“我们的产品和服务”。</p>\n" +
                "<p>我们深知个人隐私信息对您的重要性，所以<strong>我们在此承诺保护使用我们的产品和服务的用户的个人信息及隐私安全</strong>。</p>\n" +
                "<p>我们的目的是希望能尽量帮您记录更多信息，让您方便的整理和搜索关于您的信息。我们会在尽量提供便利性的基础上，尽量少的收集关于您的信息，尽可能的保护您的隐私。</p>\n" +
                "<p>所以我们希望通过本协议，向您说明我们在收集和使用您相关个人信息时对应的处理规则，以及我们为您提供的访问、更正、删除和保护这些个人信息的方式，以便更好的保障您的权益。</p>\n" +
                "<p>本《隐私政策》将帮助您了解以下内容：</p>\n" +
                "<p>一、我们将收集哪些信息，以及如何收集和使用您的个人信息；</p>\n" +
                "<p>二、我们如何使用 cookies 或相关技术；</p>\n" +
                "<p>三、我们可能分享、转让和披露的个人信息；</p>\n" +
                "<p>四、我们如何保留、储存和保护您的个人信息安全；</p>\n" +
                "<p>五、如何管理您的个人信息；</p>\n" +
                "<p>六、第三方服务；</p>\n" +
                "<p>七、隐私政策的通知和修订；</p>\n" +
                "<p>八、如何联系我们。</p>\n" +
                "<h2 id=\"一-我们将收集哪些信息及如何收集和使用您的个人信息\">一、我们将收集哪些信息及如何收集和使用您的个人信息</h2>\n" +
                "<p>我们收集您的个人信息主要是为了您更容易和更满意地使用思源笔记云端服务。而这些个人信息有助于我们实现这一目标。</p>\n" +
                "<h3 id=\"我们将通过以下途径收集和获得您的个人信息\">我们将通过以下途径收集和获得您的个人信息</h3>\n" +
                "<p>您提供的个人信息。例如：</p>\n" +
                "<ul>\n" +
                "<li id=\"20220212223827-z3yugwz\">\n" +
                "<p>您在注册思源笔记云端服务的账号或使用思源笔记云端服务时，向我们提供的个人信息；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-chlu6js\">\n" +
                "<p>您通过思源笔记云端服务向其他方提供的共享个人信息，以及您使用思源笔记云端服务时所储存的个人信息。</p>\n" +
                "</li>\n" +
                "</ul>\n" +
                "<p><strong>如果您不使用思源笔记本云端服务，我们不会收集和获得您的个人信息</strong>。</p>\n" +
                "<h3 id=\"我们会出于以下目的-收集和使用您以下类型的个人信息\">我们会出于以下目的，收集和使用您以下类型的个人信息</h3>\n" +
                "<h4 id=\"帮助您完成思源云端服务的注册及登录\">帮助您完成思源云端服务的注册及登录</h4>\n" +
                "<p>为便于我们为您提供完整的服务，您需要提供基本注册或登录个人信息，包括手机号码、电子邮箱地址，并创建您的账号、用户名和密码。</p>\n" +
                "<p>同时，依照相关法律法规的要求或者特定功能及服务需要，您在使用特定功能及服务前，可能需要您提供其他个人信息（例如姓名、身份证、面部特征及其他身份证明信息信息）。如果您不提供上述个人信息，我们将不能向您提供相关的功能及服务。</p>\n" +
                "<p>如果您不再使用思源笔记云端服务，在符合服务协议约定条件及国家相关法律法规规定的情况下，您可以自行注销您的账号，届时我们将停止为您提供思源笔记云端服务。（当您的账号注销后，与该账号相关的思源笔记云端服务项下的全部服务资料和数据将被删除或匿名化处理，但法律法规另有规定的除外。）</p>\n" +
                "<h4 id=\"向您提供服务\">向您提供服务</h4>\n" +
                "<p>我们所收集和使用的个人信息是为您提供思源笔记云端服务的必要条件，如缺少相关个人信息，我们将无法为您提供思源笔记云端服务的核心内容：</p>\n" +
                "<ol>\n" +
                "<li id=\"20220212223827-3ysdjmj\">\n" +
                "<p>个人信息的发布：您在使用思源笔记云端服务时、上传和/或发布个人信息以及进行相关行为（例如您附加于任何页面上的文件、项目、列表和企业名称或者对某一对象的描述，您在文字、分享、文件上的评论，您上传的图片、表情以及文件等）时，我们将收集您上传、发布或形成的个人信息，并有权在您授权的情况下，展示您的昵称、头像和发布内容。</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-ydnac5x\">\n" +
                "<p>支付结算：如您在使用我们的服务过程中产生支付结算，您可以选择思源关联方或与思源合作的第三方支付机构（以下称“支付机构”）所提供的支付服务。您可能会提供付款账号信息，如银行卡卡号等。支付或售后服务过程中我们可能需要将您的订单号与交易金额信息同这些支付机构共享以实现其确认您的支付指令并完成支付。</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-ku0la9r\">\n" +
                "<p>订单管理：为展示您的账号的订单信息及保障您的售后权益，思源会收集您在使用思源过程中产生的订单信息、交易和消费记录、虚拟财产信息用于向您展示及便于您对订单进行管理。</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-rca0gj6\">\n" +
                "<p>客服与售后服务：当您与我们联系时，我们可能会保存您的通信/通话记录和内容或您留下的联系方式等个人信息，以便与您联系或帮助您解决问题，或记录相关问题的处理方案及结果。为确认交易状态及为您提供售后与争议解决服务，我们会通过您基于交易所选择的交易对象、支付机构、物流公司等收集与交易进度相关的您的交易、支付、物流信息，或将您的交易信息共享给上述服务提供者。</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-co7sdv4\">\n" +
                "<p>我们在您使用服务过程中可能收集的个人信息</p>\n" +
                "</li>\n" +
                "</ol>\n" +
                "<p>为向您提供更契合您需求的页面展示和推荐个性化内容、进行用户画像、了解产品适配性、识别账号异常状态，我们可能会收集关于您使用的服务以及使用方式的个人信息并将这些个人信息进行关联，这些个人信息包括：</p>\n" +
                "<ol>\n" +
                "<li id=\"20220212223827-lp6e5aq\">\n" +
                "<p>日志信息：当您使用思源笔记云端服务时，我们可能会自动收集您对我们服务的详细使用情况，作为有关网络日志保存。例如您的登录账号、搜索查询内容、IP 地址、浏览器的类型、电信运营商、网络环境、使用的语言、访问日期和时间及您访问的网页浏览记录、Push 打开记录、停留时长、刷新记录、发布记录及分享。</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-hfbzxi6\">\n" +
                "<p>设备信息：我们可能会根据您在软件安装及使用中授予的具体权限，接收并记录您所使用的设备相关信息（例如 <strong>IMEI、MAC、Serial、SIM 卡 IMSI 识别码、设备机型、操作系统及版本、客户端版本、设备分辨率、包名、设备设置、进程及软件列表、唯一设备标识符、软硬件特征</strong>信息）、设备所在位置相关信息（<strong>例如 IP 地址、GPS 位置以及能够提供相关个人信息的 WLAN 接入点、蓝牙和基站传感器</strong>信息）。</p>\n" +
                "</li>\n" +
                "</ol>\n" +
                "<p>请注意：单独的设备信息、日志信息是无法识别特定自然人身份的个人信息。如果我们将这类非个人信息与其他个人信息结合用于识别特定自然人身份，或者将其与个人信息结合使用，则在结合使用期间，这类非个人信息将被视为个人信息，除取得您授权或法律法规另有规定外，我们会将该类个人信息做匿名化、去标识化处理。</p>\n" +
                "<h4 id=\"我们通过间接方式收集到的您的个人信息\">我们通过间接方式收集到的您的个人信息</h4>\n" +
                "<p><strong>我们可能从管理方、第三方合作伙伴获取您授权共享的相关个人信息。例如，我们可能从第三方获取您授权共享的账户个人信息（头像、昵称、登录时间）并在您同意本《隐私政策》后将您的第三方账户与您的思源笔记云端服务账户绑定，使您可以通过第三方账户直接登录并使用我们的产品和服务。</strong> 我们将在符合相关法律法规规定，并依据与关联方或第三方合作伙伴的约定、确信其提供的个人信息来源合法的前提下，收集并使用您的这些个人信息。</p>\n" +
                "<ol>\n" +
                "<li id=\"20220212223827-3uvjpcy\">\n" +
                "<p>基于上述您向我们提供的个人信息、我们可能收集的个人信息及我们通过间接方式收集到的您的个人信息，我们可能会基于上述一项或几项个人信息的结合，进行推荐算法建模、程序化广告推荐算法建模、用户行为分析及用户画像，以便向您提供更契合您需求的页面展示和推荐个性化内容。</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-wnhaeno\">\n" +
                "<p>向您推送消息或发送通知</p>\n" +
                "<p>我们可能在必需时（例如当我们由于系统维护而暂停某一单项服务、变更、终止提供某一单项服务时）向您发出与服务有关的通知。</p>\n" +
                "<p>如您不希望继续接收我们推送的消息，您可要求我们停止推送，例如：根据短信退订指引要求我们停止发送推广短信，或在移动端设备中进行设置，不再接收我们推送的消息；但我们依法律规定或单项服务的服务协议约定发送消息的情形除外。</p>\n" +
                "</li>\n" +
                "</ol>\n" +
                "<h4 id=\"为您提供安全保障\">为您提供安全保障</h4>\n" +
                "<p><strong>为提高您使用我们及合作伙伴提供服务的安全性，保护您或其他用户或公众的人身财产安全免遭侵害，更好地预防钓鱼网站、欺诈、网络漏洞、计算机病毒、网络攻击、网络侵入等安全风险，更准确地识别违反法律法规或 思源 服务相关协议规则的情况，我们可能会收集、使用或整合您的账户信息、交易信息、设备信息、日志信息以及我们关联公司、合作伙伴取得您授权或依据法律共享的个人信息，来综合判断您账户及交易风险、进行身份验证、检测及防范安全事件，并依法采取必要的记录、审计、分析、处置措施。</strong></p>\n" +
                "<h4 id=\"改进我们的服务\">改进我们的服务</h4>\n" +
                "<p>我们可能将通过某一项思源笔记云端服务所收集的个人信息，用于我们的其他服务。例如，在您使用某一项思源笔记云端服务时所收集的您的个人信息，可能在另一项思源笔记云端服务中用于向您提供特定内容或向您展示与您相关的、而非普遍推送的信息；我们可能让您参与有关思源笔记云端服务的调查，帮助我们改善现有服务或设计新服务；同时，我们可能将您的个人信息用于软件更新。</p>\n" +
                "<p><strong>您了解并同意，在收集您的个人信息后，我们将通过技术手段对数据进行去标识化处理，去标识化处理的个人信息将无法识别您的身份，在此情况下我们有权使用已经去标识化的个人信息，对用户数据库进行分析并予以商业化的利用。</strong></p>\n" +
                "<h4 id=\"其他用途\">其他用途</h4>\n" +
                "<p><strong>请您注意，如果我们要将您的个人信息用于本《隐私政策》中未载明的其他用途或额外收集未提及的其他个人信息，我们会另行事先请您同意（确认同意的方式：如勾选、弹窗、站内信、邮件、短信等方式）。一旦您同意，该等额外用途将视为本《隐私政策》的一部分，该等额外个人信息也将适用本《隐私政策》。</strong></p>\n" +
                "<h3 id=\"征得授权同意的例外\">征得授权同意的例外</h3>\n" +
                "<p>根据相关法律法规规定，以下情形中收集您的个人信息无需征得您的授权同意：</p>\n" +
                "<ol>\n" +
                "<li id=\"20220212223827-q4sl9yt\">\n" +
                "<p>与国家安全、国防安全有关的；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-jbsr7dn\">\n" +
                "<p>与公共安全、公共卫生、重大公共利益有关的；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-mnk35ou\">\n" +
                "<p>与犯罪侦查、起诉、审判和判决执行等有关的；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-t9y4a11\">\n" +
                "<p>出于维护个人信息主体或其他个人的生命、财产等重大合法权益但又很难得到您本人同意的；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-df2yra3\">\n" +
                "<p>所收集的个人信息是您自行向社会公众公开的；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-9enevis\">\n" +
                "<p>从合法公开披露的信息中收集个人信息的，如合法的新闻报道、政府信息公开等渠道；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-pdr90jn\">\n" +
                "<p>根据您的要求签订合同所必需的；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-z8qw2fw\">\n" +
                "<p>用于维护思源笔记云端服务的安全稳定运行所必需的，例如发现、处置产品或服务的故障；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-fd1fm2b\">\n" +
                "<p>为合法的新闻报道所必需的；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-6maxrdq\">\n" +
                "<p>学术研究机构基于公共利益开展统计或学术研究所必要，且对外提供学术研究或描述的结果时，对结果中所包含的个人信息进行去标识化处理的；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-nqbktf1\">\n" +
                "<p>法律法规规定的其他情形。</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-hia7gxk\">\n" +
                "<p>您理解并同意，思源笔记云端服务可能需要您在您的设备中开启特定的访问权限（例如您的位置信息 、摄像头、相册、麦克风、通讯录及/或日历），以实现这些权限所涉及个人信息的收集和使用。当您需要关闭该功能时，大多数移动设备都会支持您的这项需求，具体方法请参考或联系您移动设备的服务商或生产商。请您注意，您开启任一权限即代表您授权我们可以收集和使用相关个人信息来为您提供对应服务，您一旦关闭任一权限即代表您取消了授权，我们将不再基于对应权限继续收集和使用相关个人信息，也无法为您提供该权限所对应的服务。但是，您关闭权限的决定不会影响此前基于您的授权所进行的个人信息收集及使用。</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-ckuclar\">\n" +
                "<p>有关敏感个人信息的提示。</p>\n" +
                "</li>\n" +
                "</ol>\n" +
                "<p>以上由您提供或我们收集您的个人信息中，可能包含您的个人敏感信息，例如银行账号、交易和消费记录、虚拟财产信息、系统账号、邮箱地址及其有关的密码、电话号码、网页浏览记录、位置信息。请您谨慎并留意个人敏感信息，您同意您的个人敏感信息我们可以按本《隐私政策》所述的目的和方式来处理。</p>\n" +
                "<h2 id=\"二-我们如何使用-Cookies-或同类技术\">二、我们如何使用 Cookies 或同类技术</h2>\n" +
                "<p>我们或我们的第三方合作伙伴可能通过 Cookies 获取和使用您的个人信息，并将该等个人信息储存为日志信息。</p>\n" +
                "<p>通过使用 Cookies，我们向您提供简单易行并富个性化的网络体验。一个 Cookies 是少量的数据，它们从一个网络服务器送至您的浏览器并存在计算机硬盘上。我们使用 Cookies 是为了让您可以受益。比如，为使得思源的登录过程更快捷，您可以选择把用户名存在一个 Cookies 中。这样下次当您要登录思源的服务时能更加方便快捷。Cookies 能帮助我们确定您连接的页面和内容，节省您在思源笔记云端服务上花费的时间。</p>\n" +
                "<p>Cookies 使得我们能更好、更快地为您服务，并且使您在思源笔记云端服务上的经历更富个性化。然而，您应该能够控制 Cookies 是否以及怎样被你的浏览器接受。请查阅您的浏览器附带的帮助以获得更多这方面的个人信息。</p>\n" +
                "<p>我们和第三方合作伙伴可能通过 Cookies 收集和使用您的个人信息，并将该等个人信息储存。</p>\n" +
                "<p>我们使用自己的 Cookies，可能用于以下用途：</p>\n" +
                "<ol>\n" +
                "<li id=\"20220212223827-qdhyl1p\">\n" +
                "<p>记住您的身份。例如：Cookies 有助于我们辨认您作为我们的注册用户的身份，或保存您向我们提供有关您的喜好或其他个人信息；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-ljbrwh6\">\n" +
                "<p>分析您使用我们服务的情况。我们可利用 Cookies 来了解您使用思源笔记云端服务进行什么活动、或哪些服务或服务最受欢迎；</p>\n" +
                "</li>\n" +
                "</ol>\n" +
                "<p>您可以通过浏览器或用户选择机制拒绝或管理 Cookies。但请您注意，如果您停用 Cookies，我们有可能无法为您提供最佳的服务体验，某些服务也可能无法正常使用。</p>\n" +
                "<h2 id=\"三-我们可能分享-转让或披露的个人信息\">三、我们可能分享、转让或披露的个人信息</h2>\n" +
                "<h3 id=\"分享\">分享</h3>\n" +
                "<p>除以下情形外，未经您同意，我们不会与我们及我们的关联方之外的任何第三方分享您的个人信息：</p>\n" +
                "<ol>\n" +
                "<li id=\"20220212223827-vvputi1\">\n" +
                "<p>向您提供我们的服务。我们可能向合作伙伴及其他第三方分享您的个人信息，以实现您需要的核心功能或提供您需要的服务，例如：向短信服务商提供对应的手机号码；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-eoja481\">\n" +
                "<p>维护和改善我们的服务。我们可能向合作伙伴及其他第三方分享您的个人信息，以帮助我们为您提供更有针对性、更完善的服务，例如：代表我们发出电子邮件或推送通知的通讯服务提供商等；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-jbe8vlx\">\n" +
                "<p>实现本《隐私政策》第一条“我们如何收集和使用您的个人信息”部分所述目的；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-v55w8a6\">\n" +
                "<p>履行我们在本《隐私政策》或我们与您达成的其他协议中的义务和行使我们的权利；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-to7l8ge\">\n" +
                "<p>在法律法规允许的范围内，为了遵守法律、维护我们及我们的关联方或合作伙伴、您或其他思源用户或社会公众利益、财产或安全免遭损害，比如为防止欺诈等违法活动和减少信用风险，我们可能与其他公司和组织交换个人信息。不过,这并不包括违反本《隐私政策》中所作的承诺而为获利目的出售、出租、共享或以其它方式披露的个人信息。</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-xwvumko\">\n" +
                "<p>应您合法需求，协助处理您与他人的纠纷或争议；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-3dfyg4h\">\n" +
                "<p>应您的监护人合法要求而提供您的个人信息；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-xsaoftu\">\n" +
                "<p>根据与您签署的单项服务协议（包括在线签署的电子协议以及相应的平台规则）或其他的法律文件约定所提供；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-gzi2k8s\">\n" +
                "<p>基于学术研究而提供；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-yojd35u\">\n" +
                "<p>基于符合法律法规的社会公共利益而提供。</p>\n" +
                "</li>\n" +
                "</ol>\n" +
                "<p>我们仅会出于合法、正当、必要、特定、明确的目的共享您的个人信息。对我们与之共享个人信息的公司、组织和个人，我们会与其签署严格的保密协定，要求他们按照我们的说明、本《隐私政策》以及其他任何相关的保密和安全措施来处理个人信息。</p>\n" +
                "<h3 id=\"转让\">转让</h3>\n" +
                "<ol>\n" +
                "<li id=\"20220212223827-hnf5h09\">\n" +
                "<p>随着我们业务的持续发展，我们有可能进行合并、收购、资产转让或类似的交易，而您的个人信息有可能作为此类交易的一部分而被转移。我们会要求新的持有您个人信息的公司、组织继续受本《隐私政策》的约束，否则，我们将要求该公司、组织重新向您征求授权同意。</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-kf3sujp\">\n" +
                "<p>在获得您的明确同意后，我们会向其他方转让您的个人信息。</p>\n" +
                "</li>\n" +
                "</ol>\n" +
                "<h3 id=\"披露\">披露</h3>\n" +
                "<p>我们仅会在以下情况下，且采取符合业界标准的安全防护措施的前提下，才会披露您的个人信息：</p>\n" +
                "<ol>\n" +
                "<li id=\"20220212223827-l0gllbw\">\n" +
                "<p>根据您的需求，在您明确同意的披露方式下披露您所指定的个人信息；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-kkzlpc1\">\n" +
                "<p>根据法律、法规的要求、强制性的行政执法或司法要求所必须提供您个人信息的情况下，我们可能会依据所要求的个人信息类型和披露方式披露您的个人信息。在符合法律法规的前提下，当我们收到上述披露个人信息的请求时，我们会要求接收方必须出具与之相应的法律文件，如传票或调查函。我们坚信，对于要求我们提供的个人信息，应该在法律允许的范围内尽可能保持透明。我们对所有的请求都进行了慎重的审查，以确保其具备合法依据，且仅限于执法部门因特定调查目的且有合法权利获取的数据。</p>\n" +
                "</li>\n" +
                "</ol>\n" +
                "<h3 id=\"分享-转让-披露个人信息时事先征得授权同意的例外\">分享、转让、披露个人信息时事先征得授权同意的例外</h3>\n" +
                "<p>以下情形中，分享、转让、披露您的个人信息无需事先征得您的授权同意：</p>\n" +
                "<ol>\n" +
                "<li id=\"20220212223827-4lfcl5g\">\n" +
                "<p>与国家安全、国防安全有关的；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-98kuu6w\">\n" +
                "<p>与公共安全、公共卫生、重大公共利益有关的；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-nsu00o2\">\n" +
                "<p>与犯罪侦查、起诉、审判和判决执行等司法或行政执法有关的；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-aroshng\">\n" +
                "<p>出于维护您或其他个人的生命、财产等重大合法权益但又很难得到本人同意的；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-vzxtkwd\">\n" +
                "<p>您自行向社会公众公开的个人信息；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-bzz09ck\">\n" +
                "<p>从合法公开披露的信息中收集个人信息的，如合法的新闻报道、政府信息公开等渠道。</p>\n" +
                "</li>\n" +
                "</ol>\n" +
                "<p>根据法律规定，共享、转让经去标识化处理的个人信息，且确保数据接收方无法复原并重新识别个人信息主体的，不属于个人信息的对外共享、转让及公开披露行为，对此类数据的保存及处理将无需另行向您通知并征得您的同意。</p>\n" +
                "<h3 id=\"我们如何保留-储存和保护您的个人信息安全\">我们如何保留、储存和保护您的个人信息安全</h3>\n" +
                "<p>我们仅在本《隐私政策》所述目的所必需期间和法律法规及监管规定的时限内保存您的个人信息。如我们终止服务或运营，我们将及时停止继续收集您个人信息的活动，同时会遵守相关法律法规要求提前向您通知，并在终止服务或运营后对您的个人信息进行删除或匿名化处理，法律法规或监管部门另有规定的除外。</p>\n" +
                "<p>我们在中华人民共和国境内运营中收集和产生的个人信息，存储在中国境内。以下情形除外：</p>\n" +
                "<ol>\n" +
                "<li id=\"20220212223827-nkwuav1\">\n" +
                "<p>法律法规有明确规定；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-1mpo1wu\">\n" +
                "<p>获得您的授权同意；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-68fxxc0\">\n" +
                "<p>您使用思源笔记云端服务，且需要向境外传输您的个人信息完成交易的；</p>\n" +
                "</li>\n" +
                "</ol>\n" +
                "<p>针对以上情形，我们会确保依据本《隐私政策》及国家法律法规要求对您的个人信息提供足够的保护。</p>\n" +
                "<p>我们非常重视个人信息安全，我们将采取一切合理可行的措施，保护您的个人信息：</p>\n" +
                "<h3 id=\"数据安全技术措施\">数据安全技术措施</h3>\n" +
                "<p>我们会采用符合业界标准的安全防护措施，包括建立合理的制度规范、安全技术来防止您的个人信息遭到未经授权的访问使用、修改,避免数据的损坏或丢失。网络服务采取了多种加密技术，例如在某些服务中，我们将利用加密技术（例如 SSL）来保护您的个人信息，采取加密技术对您的个人信息进行加密保存，并通过隔离技术进行隔离。 在个人信息使用时，例如个人信息展示、个人信息关联计算，我们会采用多种数据脱敏技术增强个人信息在使用中的安全性。采用严格的数据访问权限控制和多重身份认证技术保护个人信息，避免数据被违规使用。</p>\n" +
                "<ol>\n" +
                "<li id=\"20220212223827-x28fkar\">\n" +
                "<p>我们为保护个人信息采取的其他安全措施：</p>\n" +
                "<ol>\n" +
                "<li id=\"20220212223827-jasvddy\">\n" +
                "<p>我们通过建立数据例行备份制度、数据分类分级制度、数据安全管理规范、数据安全开发规范来管理规范个人信息的存储和使用。</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-8z9yisg\">\n" +
                "<p>我们通过个人信息接触者保密协议、监控和审计机制来对数据进行全面安全控制。并对个人信息接触者所有操作过程进行记录，以便遇到泄露时方便回溯。</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-qwuvv7q\">\n" +
                "<p>我们仅允许有必要知晓这些个人信息的我们及我们关联方的员工、合作伙伴访问您的个人信息，并为此设置了严格的访问权限控制和监控机制。我们同时要求可能接触到您的个人信息的所有人员履行相应的保密义务。如果未能履行这些义务，可能会被追究法律责任或被中止与我们的合作关系。</p>\n" +
                "</li>\n" +
                "</ol>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-9havfbl\">\n" +
                "<p>互联网并非绝对安全的环境，而且电子邮件、即时通讯、社交软件或其他服务软件等与其他用户的交流方式无法确定是否完全加密，我们建议您使用此类工具时请使用复杂密码，并注意保护您的个人信息安全。</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-drxoq2n\">\n" +
                "<p>我们将尽力确保或担保您发送给我们的任何个人信息的安全性。如果我们的物理、技术、或管理防护设施遭到破坏，导致个人信息被非授权访问、公开披露、篡改、或毁坏，导致您的合法权益受损，我们将承担相应的法律责任。</p>\n" +
                "</li>\n" +
                "</ol>\n" +
                "<h3 id=\"安全事件处置\">安全事件处置</h3>\n" +
                "<p>在不幸发生个人信息安全事件后，我们将按照法律法规的要求，及时向您告知：安全事件的基本情况和可能的影响、我们已采取或将要采取的处置措施、您可自主防范和降低风险的建议、对您的补救措施等。我们同时将及时将事件相关情况以邮件、信函、短信、电话、推送通知等方式告知您，难以逐一告知个人信息主体时，我们会采取合理、有效的方式发布公告。同时，我们还将按照监管部门要求，主动上报个人信息安全事件的处置情况。</p>\n" +
                "<p><strong>请您理解，由于技术的限制以及风险防范的局限，即便我们已经尽量加强安全措施，也无法始终保证个人信息百分之百的安全。您需要了解，您接入思源笔记云端服务所用的系统和通讯网络，有可能因我们可控范围外的情况而发生问题。</strong></p>\n" +
                "<h2 id=\"四-如何管理您的个人信息\">四、如何管理您的个人信息</h2>\n" +
                "<p>我们鼓励您更新和修改您的个人信息以使其更准确有效，也请您理解，您更正、删除、撤回授权或停止使用思源笔记云端服务的决定，并不影响我们此前基于您的授权而开展的个人信息处理。除法律法规另有规定，当您更正、删除您的个人信息或申请注销账号时，我们可能不会立即从备份系统中更正或删除相应的个人信息，但会在备份更新时更正或删除这些个人信息。</p>\n" +
                "<p>您可以通过以下方式来管理您的个人信息：</p>\n" +
                "<h3 id=\"访问-更正和删除您的个人信息\">访问、更正和删除您的个人信息</h3>\n" +
                "<p>您能通过思源笔记云端服务访问您的个人信息，并根据对应个人信息的管理方式自行完成或要求我们进行访修改、补充和删除。我们将采取适当的技术手段或提供提交申请的联系渠道，尽可能保证您可以访问、更新和更正自己的个人信息或使用思源笔记云端服务时提供的其他个人信息。</p>\n" +
                "<p>在访问、更正和删除前述个人信息时，我们可能会要求您进行身份验证，以保障个人信息安全。对于通过 Cookies 或同类技术收集的您的个人信息，我们还在本《隐私政策》第二条“我们如何使用 Cookies 或同类技术”部分说明了向您提供的选择机制。如果您无法通过上述路径访问、更正该等个人信息，您可以通过本《隐私政策》第八条“如何联系我们”约定的联系方式与我们取得联系。对于您在使用思源笔记云端服务过程中产生的其他个人信息需要访问、更正或删除，我们会根据本《隐私政策》所列明的方式、期限及范围来响应您的请求。在部分个人信息删除时，我们可能会要求您进行身份验证，以保障个人信息安全。</p>\n" +
                "<p>在以下情形中，您可以向我们提出删除个人信息的请求：</p>\n" +
                "<ol>\n" +
                "<li id=\"20220212223827-m1kbc3h\">\n" +
                "<p>如果我们处理个人信息的行为违反法律法规；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-pv0dc9d\">\n" +
                "<p>如果我们收集、使用您的个人信息，却未征得您的授权同意；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-brkh8l3\">\n" +
                "<p>如果我们处理个人信息的行为严重违反了与您的约定；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-tvy3am4\">\n" +
                "<p>如果我们不再为您提供思源笔记云端服务。</p>\n" +
                "</li>\n" +
                "</ol>\n" +
                "<p>若我们决定响应您的删除请求，我们还将同时尽可能通知从我们处获得您的个人信息的实体，要求其及时删除，除非法律法规另有规定，或这些实体获得您的独立授权。</p>\n" +
                "<h3 id=\"公开与分享\">公开与分享</h3>\n" +
                "<p>我们的多项服务可让您不仅与您的社交网络、也与使用该服务的所有用户公开分享您的相关个人信息，例如，您在思源笔记云端服务中所上传或发布的个人信息、您对其他人上传或发布的个人信息作出的回应，通过电子邮件或在思源笔记云端服务中不特定用户可见的公开区域内上传或公布您的个人信息，以及包括与这些个人信息有关的位置数据和日志信息。只要您不删除您所公开或共享的个人信息，有关个人信息可能一直留存在公众领域；即使您删除共享个人信息，有关个人信息仍可能由其他用户或不受我们控制的第三方独立地缓存、复制或储存，或由其他用户或该等第三方在公众领域保存。如您将个人信息通过上述渠道公开或共享，由此造成您的个人信息泄露，我们不承担责任。因此，我们提醒并请您慎重考虑是否通过上述渠道公开或共享您的个人信息。</p>\n" +
                "<h3 id=\"改变您授权同意的范围\">改变您授权同意的范围</h3>\n" +
                "<p>您总是可以选择是否披露个人信息。有些个人信息是使用思源笔记云端服务所必需的，但大多数其他个人信息的提供是由您决定的。您可以通过删除个人信息、关闭设备功能等方式改变您授权我们继续收集个人信息的范围或撤回您的授权。</p>\n" +
                "<p>当您撤回授权后，我们无法继续为您提供撤回授权所对应的服务，也不再处理您相应的个人信息。但您撤回授权的决定，不会影响此前基于您的授权而开展的个人信息处理。</p>\n" +
                "<h3 id=\"注销你的账户\">注销你的账户</h3>\n" +
                "<p>如您需要注销账户，请登录链滴，进入 <code>设置 - 账号</code> 自行停用。</p>\n" +
                "<p>在注销账户之后，我们将停止为您提供产品或服务，并依据您的要求，删除您的个人信息，但法律法规另有规定的除外。</p>\n" +
                "<h3 id=\"获取个人信息副本\">获取个人信息副本</h3>\n" +
                "<p>您有权获取您的个人信息副本，您可以通过以下方式自行操作：登录 思源 网页端，进入「设置」-「账号详情」-「导出」。我们会在 24 小时内为你生成全部个人信息副本的下载链接。</p>\n" +
                "<h2 id=\"五-第三方-SDK-服务\">五、第三方 SDK 服务</h2>\n" +
                "<p>思源笔记云端服务可能链接至第三方提供的社交媒体或其他服务（包括网站或其他服务形式），包括：</p>\n" +
                "<ol>\n" +
                "<li id=\"20220212223827-8bv4b7w\">\n" +
                "<p>您可利用“分享”键将某些内容分享到第三方平台（如微信），或您可利用第三方服务登录思源笔记云端服务。这些功能可能会收集您的个人信息（包括您的日志信息），并可能在您的电脑装置 Cookie，从而正常运行上述功能；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-zgu7wr9\">\n" +
                "<p>我们通过广告或我们服务的其他方式向您提供链接，使您可以接入第三方的服务或网站；</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-uiyju04\">\n" +
                "<p>其他接入第三方服务的情形。</p>\n" +
                "</li>\n" +
                "</ol>\n" +
                "<p>以上第三方社交媒体或其他服务由相关的第三方负责运营。若您需要使用以上这些第三方的社交媒体服务或其他服务（包括您向该等第三方提供的任何个人信息）时，须受第三方自己的服务条款及个人信息保护声明（而非本《隐私政策》）约束，请仔细阅读其条款。本《隐私政策》仅适用于“思源”所收集的任何个人信息，并不适用于任何第三方提供的服务或第三方的个人信息使用规则，而我们对任何第三方使用由您提供的个人信息不承担任何责任。</p>\n" +
                "<p>下面是详细 SDK 情况说明：</p>\n" +
                "<p>社交类服务 SDK：</p>\n" +
                "<blockquote>\n" +
                "<p>SDK 名称：微信开放平台</p>\n" +
                "<p>公司名称：腾讯科技（深圳）有限公司</p>\n" +
                "<p>SDK 官网：<a href=\"https://open.weixin.qq.com/\">https://open.weixin.qq.com (opens new window)</a></p>\n" +
                "<p>使用目的：用于帮助用户分享内容至微信</p>\n" +
                "<p>收集的个人信息类型/字段：设备标识信息<br>\n" +
                "<a href=\"https://open.weixin.qq.com/cgi-bin/frame?t=news/protocol_developer_tmpl\">https://open.weixin.qq.com/cgi-bin/frame?t=news/protocol_developer_tmpl <strong>(opens new window)</strong></a></p>\n" +
                "</blockquote>\n" +
                "<p>支付服务类 SDK：</p>\n" +
                "<blockquote>\n" +
                "<p>SDK 名称：微信开放平台</p>\n" +
                "<p>公司名称：腾讯科技（深圳）有限公司</p>\n" +
                "<p>SDK 官网：<a href=\"https://pay.weixin.qq.com/\">https://pay.weixin.qq.com/ <strong>(opens new window)</strong></a></p>\n" +
                "<p>使用目的：用于实现微信支付服务</p>\n" +
                "<p>收集的个人信息类型/字段：设备标识信息</p>\n" +
                "<p>相关协议：<a href=\"https://pay.weixin.qq.com/index.php/public/apply_sign/protocol_v2\">https://pay.weixin.qq.com/index.php/public/apply_sign/protocol_v2 <strong>(opens new window)</strong></a></p>\n" +
                "</blockquote>\n" +
                "<blockquote>\n" +
                "<p>SDK 名称：支付宝</p>\n" +
                "<p>公司名称：支付宝（中国）网络技术有限公司</p>\n" +
                "<p>SDK 官网：<a href=\"https://open.alipay.com/\">https://open.alipay.com <strong>(opens new window)</strong></a></p>\n" +
                "<p>使用目的：用于实现支付宝支付服务</p>\n" +
                "<p>收集的个人信息类型/字段：设备标识信息</p>\n" +
                "<p>隐私协议：<a href=\"https://opendocs.alipay.com/open/54/01g6qm\">https://opendocs.alipay.com/open/54/01g6qm <strong>(opens new window)</strong></a></p>\n" +
                "</blockquote>\n" +
                "<h2 id=\"六-应用内会申请的权限\">六、应用内会申请的权限</h2>\n" +
                "<p>我们需要申请部分涉及个人隐私的系统权限，确保应用内某些功能可以正常使用。我们会在您使用这些功能时，向您申请授权，明确获得您的授权后，我们才会访问您的个人信息。若您不需要使用这些功能，可以拒绝授权；若您从未使用过这些功能，我们不会向您申请权限、也不会访问您的个人信息。</p>\n" +
                "<p>我们会申请的权限及这些权限对应的功能包括：</p>\n" +
                "<p><strong style=\"background-color: var(--b3-theme-background); font-family: var(--b3-font-family);\">Android 应用会申请的权限允许访问网络：</strong></p>\n" +
                "<ul>\n" +
                "<li id=\"20220212223827-gkiyp7i\">\n" +
                "<p><strong>允许访问网络：</strong>当您安装应用时，我们会申请此权限。</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-foe3qek\">\n" +
                "<p><strong>允许常驻通知：</strong>当您安装应用，我们会申请此权限。</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-nn6zsjw\">\n" +
                "<p><strong>允许拍照/访问相册：</strong>当您希望添加照片时，我们会申请此权限。</p>\n" +
                "</li>\n" +
                "</ul>\n" +
                "<p><strong>iOS 应用内会申请的权限</strong></p>\n" +
                "<ul>\n" +
                "<li id=\"20220212223827-e2nrj38\">\n" +
                "<p><strong>允许访问网络：</strong>当您安装应用时，我们会申请此权限。</p>\n" +
                "</li>\n" +
                "<li id=\"20220212223827-5j2bfkm\">\n" +
                "<p><strong>允许访问系统相册：</strong>当您希望添加照片时，我们会申请此权限。</p>\n" +
                "</li>\n" +
                "</ul>\n" +
                "<h2 id=\"七-隐私政策的通知和修订\">七、隐私政策的通知和修订</h2>\n" +
                "<p>我们可能适时修改本《隐私政策》的条款，该等修改构成本《隐私政策》的一部分。对于重大变更，我们会提供更显著的通知，您如果不同意该等变更，可以选择停止使用思源笔记云端服务；如您仍然继续使用思源笔记云端服务的，即表示同意受经修订的本《隐私政策》的约束。</p>\n" +
                "<p>我们鼓励您在每次使用思源笔记云端服务时都查阅我们的隐私政策。</p>\n" +
                "<p>我们可能在必需时（例如当我们由于系统维护而暂停某一项服务时）发出与服务有关的公告。您可能无法取消这些与服务有关、性质不属于推广的公告。</p>\n" +
                "<p>最后，您必须对您的账号和密码信息负有保密义务。任何情况下，请小心妥善保管。</p>\n" +
                "<h2 id=\"八-如何联系我们\">八、如何联系我们</h2>\n" +
                "<p>请发送邮件到 845765@qq.com。</p>\n" +
                "</div>"));

        AlertDialog.Builder ab = new AlertDialog.Builder(this);
        ab.setTitle("使用须知 / Notice");
        ab.setView(msg);
        ab.setCancelable(false);
        ab.setPositiveButton("同意/Agree", (dialog, which) -> {
            final Bundle b = new Bundle();
            b.putString("cmd", "agreement-y");
            final Message m = new Message();
            m.setData(b);
            handler.sendMessage(m);
        });
        ab.setNegativeButton("拒绝/Decline", (dialog, which) -> {
            final Bundle b = new Bundle();
            b.putString("cmd", "agreement-n");
            final Message m = new Message();
            m.setData(b);
            handler.sendMessage(m);
        });

        ab.show();
    }
}