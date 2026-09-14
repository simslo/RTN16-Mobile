package si.rtn16mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private SecureStore store;
    private RouterClient router;

    private LinearLayout root;
    private TextView statusTitle, statusDetails, contentTitle;
    private LinearLayout content;
    private ProgressBar progress;

    private int dp(float x) {
        return (int) (x * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new SecureStore(this);
        buildUi();
        if (store.getPassword().isEmpty()) {
            showSettings(true);
        } else {
            rebuildClient();
            refreshStatus();
        }
    }

    private void rebuildClient() {
        router = new RouterClient(store.getRouter(), store.getUser(), store.getPassword());
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(24));
        sv.addView(root);
        setContentView(sv);

        TextView title = text("RT-N16 MOBILE", 25, true);
        title.setTextColor(Color.rgb(17,24,39));
        root.addView(title);

        TextView sub = text("UPLINK + naprave na ASUS-MOBILE", 14, false);
        sub.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams sp = lpMatchWrap();
        sp.bottomMargin = dp(14);
        root.addView(sub, sp);

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setBackgroundResource(R.drawable.card_bg);
        status.setPadding(dp(14), dp(14), dp(14), dp(14));

        statusTitle = text("Povezujem se ...", 18, true);
        statusDetails = text("", 14, false);
        statusDetails.setPadding(0, dp(7), 0, 0);
        status.addView(statusTitle);
        status.addView(statusDetails);
        LinearLayout.LayoutParams cp = lpMatchWrap();
        cp.bottomMargin = dp(12);
        root.addView(status, cp);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = lpWrapWrap();
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        pp.bottomMargin = dp(8);
        root.addView(progress, pp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        root.addView(buttons, lpMatchWrap());

        Button uplink = button("SPREMENI UPLINK", true);
        uplink.setOnClickListener(v -> scanNetworks());
        buttons.addView(uplink, buttonLp());

        Button dev = button("NAPRAVE NA MOBILE", false);
        dev.setOnClickListener(v -> loadDevices());
        buttons.addView(dev, buttonLp());

        Button refresh = button("OSVEŽI STATUS", false);
        refresh.setOnClickListener(v -> refreshStatus());
        buttons.addView(refresh, buttonLp());

        Button captive = button("ODPRI PRIJAVO JAVNEGA WI-FI", false);
        captive.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://neverssl.com/"));
            startActivity(i);
        });
        buttons.addView(captive, buttonLp());

        Button settings = button("NASTAVITVE ROUTERJA", false);
        settings.setOnClickListener(v -> showSettings(false));
        buttons.addView(settings, buttonLp());

        contentTitle = text("", 19, true);
        LinearLayout.LayoutParams tp = lpMatchWrap();
        tp.topMargin = dp(14);
        tp.bottomMargin = dp(6);
        root.addView(contentTitle, tp);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, lpMatchWrap());
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams p = lpMatchWrap();
        p.bottomMargin = dp(9);
        return p;
    }

    private Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : Color.rgb(17,24,39));
        b.setBackgroundResource(primary ? R.drawable.button_bg : R.drawable.button_secondary_bg);
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        return b;
    }

    private TextView text(String s, int size, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(Color.rgb(30,41,59));
        if (bold) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        return v;
    }

    private LinearLayout.LayoutParams lpMatchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams lpWrapWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void busy(boolean b) {
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
    }

    private void refreshStatus() {
        busy(true);
        exec.execute(() -> {
            try {
                String cmd =
                        "echo SSID=$(nvram get wl0_ssid); " +
                        "echo WANIP=$(nvram get wan_ipaddr); " +
                        "echo GW=$(nvram get wan_gateway); " +
                        "wl -i eth1 status 2>/dev/null | grep -E 'RSSI:|BSSID:' | head -n2";
                String out = router.shell(cmd);
                ui.post(() -> {
                    busy(false);
                    statusTitle.setText("RT-N16 je dosegljiv");
                    statusDetails.setText(prettyStatus(out));
                });
            } catch (Exception e) {
                ui.post(() -> {
                    busy(false);
                    statusTitle.setText("Router ni dosegljiv");
                    statusDetails.setText(e.getMessage());
                });
            }
        });
    }

    private String prettyStatus(String out) {
        Map<String,String> m = new HashMap<>();
        for (String l : out.split("\\n")) {
            int x = l.indexOf('=');
            if (x > 0) m.put(l.substring(0,x).trim(), l.substring(x+1).trim());
        }
        String rssi = "";
        Matcher mr = Pattern.compile("RSSI:\\s*(-?\\d+)").matcher(out);
        if (mr.find()) rssi = mr.group(1) + " dBm";
        return "UPLINK: " + val(m,"SSID","-") +
                "\nWAN IP: " + val(m,"WANIP","-") +
                (rssi.isEmpty() ? "" : "\nSignal: " + rssi);
    }

    private String val(Map<String,String> m, String k, String d) {
        String v = m.get(k);
        return (v == null || v.isEmpty()) ? d : v;
    }

    private void scanNetworks() {
        busy(true);
        content.removeAllViews();
        contentTitle.setText("Iskanje omrežij ...");
        exec.execute(() -> {
            try {
                String out = router.shell("wl -i eth1 scan >/dev/null 2>&1; sleep 2; wl -i eth1 scanresults 2>/dev/null");
                List<WifiNetwork> nets = parseScan(out);
                ui.post(() -> {
                    busy(false);
                    showNetworkDialog(nets);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    busy(false);
                    error(e);
                });
            }
        });
    }

    private List<WifiNetwork> parseScan(String s) {
        List<WifiNetwork> out = new ArrayList<>();
        WifiNetwork cur = null;
        Set<String> seen = new HashSet<>();

        for (String raw : s.split("\\n")) {
            String l = raw.trim();
            Matcher ms = Pattern.compile("^SSID:\\s*[\"']?(.*?)[\"']?$").matcher(l);
            if (ms.find()) {
                if (cur != null && !cur.ssid.isEmpty() && seen.add(cur.ssid + "|" + cur.bssid)) out.add(cur);
                cur = new WifiNetwork();
                cur.ssid = ms.group(1).trim().replaceAll("^\"|\"$", "");
                continue;
            }
            if (cur == null) continue;

            Matcher mb = Pattern.compile("BSSID:\\s*([0-9A-Fa-f:]{17})").matcher(l);
            if (mb.find()) cur.bssid = mb.group(1).toUpperCase(Locale.ROOT);

            Matcher mr = Pattern.compile("RSSI:\\s*(-?\\d+)").matcher(l);
            if (mr.find()) cur.rssi = Integer.parseInt(mr.group(1));

            Matcher mc = Pattern.compile("Channel:\\s*(\\d+)").matcher(l);
            if (mc.find()) cur.channel = Integer.parseInt(mc.group(1));

            String up = l.toUpperCase(Locale.ROOT);
            if (up.startsWith("RSN:") || up.contains("WPA2")) cur.security = "WPA2";
            else if (up.startsWith("WPA:") && !"WPA2".equals(cur.security)) cur.security = "WPA";
        }
        if (cur != null && !cur.ssid.isEmpty() && seen.add(cur.ssid + "|" + cur.bssid)) out.add(cur);

        out.sort((a,b) -> Integer.compare(b.rssi, a.rssi));
        return out;
    }

    private void showNetworkDialog(List<WifiNetwork> nets) {
        if (nets.isEmpty()) {
            Toast.makeText(this, "Ni najdenih omrežij.", Toast.LENGTH_LONG).show();
            return;
        }
        ArrayAdapter<WifiNetwork> ad = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, nets);
        new AlertDialog.Builder(this)
                .setTitle("Izberi UPLINK")
                .setAdapter(ad, (d, which) -> askNetworkPassword(nets.get(which)))
                .setNegativeButton("Prekliči", null)
                .show();
    }

    private void askNetworkPassword(WifiNetwork n) {
        if ("OPEN".equals(n.security)) {
            confirmConnect(n, "", "OPEN");
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        EditText pw = new EditText(this);
        pw.setHint("Geslo omrežja");
        pw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(pw);

        new AlertDialog.Builder(this)
                .setTitle(n.ssid)
                .setMessage(n.rssi + " dBm, kanal " + n.channel + ", " + n.security)
                .setView(box)
                .setPositiveButton("Poveži", (d,w) -> confirmConnect(n, pw.getText().toString(), "WPA2"))
                .setNegativeButton("Prekliči", null)
                .show();
    }

    private void confirmConnect(WifiNetwork n, String password, String security) {
        busy(true);
        contentTitle.setText("Preklapljam UPLINK ...");
        content.removeAllViews();

        exec.execute(() -> {
            try {
                String qSsid = RouterClient.shQuote(n.ssid);
                StringBuilder c = new StringBuilder();
                c.append("nvram set wl0_ssid=").append(qSsid).append("; ");
                if (n.channel > 0) c.append("nvram set wl0_channel=").append(n.channel).append("; ");
                c.append("nvram set wl0_nbw_cap=0; ");

                if ("OPEN".equals(security)) {
                    c.append("nvram set wl0_security_mode=disabled; ");
                    c.append("nvram set wl0_akm=''; nvram set wl0_crypto=aes; ");
                    c.append("nvram set wl0_auth_mode=none; nvram set wl0_wep=disabled; ");
                    c.append("nvram set wl0_wpa_psk=''; ");
                } else {
                    c.append("nvram set wl0_security_mode=wpa2_personal; ");
                    c.append("nvram set wl0_akm=psk2; ");
                    c.append("nvram set wl0_crypto=aes; ");
                    c.append("nvram set wl0_auth_mode=none; ");
                    c.append("nvram set wl0_wep=disabled; ");
                    c.append("nvram set wl0_wpa_psk=").append(RouterClient.shQuote(password)).append("; ");
                }

                c.append("nvram commit; ");
                c.append("service wireless restart >/dev/null 2>&1; sleep 3; service wan restart >/dev/null 2>&1");

                router.shell(c.toString());

                ui.post(() -> {
                    busy(false);
                    Toast.makeText(this,
                            "Preklop začet. ASUS-MOBILE lahko za nekaj sekund izgine. Čez približno 20 s osveži status.",
                            Toast.LENGTH_LONG).show();
                    contentTitle.setText("UPLINK: " + n.ssid);
                    TextView t = text("Preklop poteka. Ker wl0 in wl0.1 uporabljata isti radio, se MOBILE med menjavo kanala za kratek čas ponovno zažene.", 14, false);
                    content.addView(t);
                    ui.postDelayed(this::refreshStatus, 18000);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    busy(false);
                    error(e);
                });
            }
        });
    }

    private void loadDevices() {
        busy(true);
        contentTitle.setText("Naprave na ASUS-MOBILE");
        content.removeAllViews();

        exec.execute(() -> {
            try {
                String cmd =
                        "echo __ASSOC__; wl -i wl0.1 assoclist 2>/dev/null; " +
                        "echo __ARP__; cat /proc/net/arp 2>/dev/null; " +
                        "echo __LEASES__; " +
                        "(cat /var/lib/misc/dnsmasq.leases 2>/dev/null || " +
                        "cat /tmp/dnsmasq.leases 2>/dev/null || " +
                        "cat /var/tmp/dhcp/leases 2>/dev/null)";
                String raw = router.shell(cmd);
                List<DeviceInfo> devices = parseDevices(raw);

                ui.post(() -> {
                    busy(false);
                    if (devices.isEmpty()) {
                        TextView t = text("Trenutno ni zaznanih klientov na wl0.1.", 15, false);
                        content.addView(t);
                    } else {
                        for (DeviceInfo d : devices) addDeviceCard(d);
                    }
                });
            } catch (Exception e) {
                ui.post(() -> {
                    busy(false);
                    error(e);
                });
            }
        });
    }

    private List<DeviceInfo> parseDevices(String raw) {
        String[] p1 = raw.split("__ARP__", 2);
        String assocPart = p1.length > 0 ? p1[0].replace("__ASSOC__", "") : "";
        String rest = p1.length > 1 ? p1[1] : "";
        String[] p2 = rest.split("__LEASES__", 2);
        String arpPart = p2.length > 0 ? p2[0] : "";
        String leasePart = p2.length > 1 ? p2[1] : "";

        Set<String> assoc = new HashSet<>();
        Matcher ma = Pattern.compile("(?i)assoclist\\s+([0-9a-f:]{17})").matcher(assocPart);
        while (ma.find()) assoc.add(ma.group(1).toUpperCase(Locale.ROOT));

        Map<String,String> ipByMac = new HashMap<>();
        for (String l : arpPart.split("\\n")) {
            String[] a = l.trim().split("\\s+");
            if (a.length >= 4 && a[0].matches("\\d+\\.\\d+\\.\\d+\\.\\d+") && a[3].matches("(?i)[0-9a-f:]{17}")) {
                ipByMac.put(a[3].toUpperCase(Locale.ROOT), a[0]);
            }
        }

        Map<String,String> nameByMac = new HashMap<>();
        for (String l : leasePart.split("\\n")) {
            String[] a = l.trim().split("\\s+");
            if (a.length >= 4 && a[1].matches("(?i)[0-9a-f:]{17}")) {
                String mac = a[1].toUpperCase(Locale.ROOT);
                String name = "-".equals(a[3]) ? "" : a[3];
                nameByMac.put(mac, name);
                if (!ipByMac.containsKey(mac)) ipByMac.put(mac, a[2]);
            }
        }

        List<DeviceInfo> out = new ArrayList<>();
        for (String mac : assoc) {
            DeviceInfo d = new DeviceInfo();
            d.mac = mac;
            d.ip = ipByMac.getOrDefault(mac, "");
            d.name = nameByMac.getOrDefault(mac, "");
            out.add(d);
        }
        return out;
    }

    private void addDeviceCard(DeviceInfo d) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.card_bg);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));

        String title = (d.name == null || d.name.isEmpty()) ? "Neznana naprava" : d.name;
        TextView a = text(title, 17, true);
        TextView b = text((d.ip == null || d.ip.isEmpty() ? "IP: -" : "IP: " + d.ip) + "\nMAC: " + d.mac, 13, false);
        b.setTextColor(Color.DKGRAY);
        box.addView(a);
        box.addView(b);

        LinearLayout.LayoutParams p = lpMatchWrap();
        p.bottomMargin = dp(8);
        content.addView(box, p);
    }

    private void showSettings(boolean required) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), 0);

        EditText host = new EditText(this);
        host.setHint("Router");
        host.setText(store.getRouter());
        box.addView(host);

        EditText user = new EditText(this);
        user.setHint("Uporabnik");
        user.setText(store.getUser());
        box.addView(user);

        EditText pw = new EditText(this);
        pw.setHint("FreshTomato geslo");
        pw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pw.setText(store.getPassword());
        box.addView(pw);

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Povezava z RT-N16")
                .setMessage("Telefon mora biti povezan na ASUS-MOBILE. Geslo je shranjeno šifrirano z Android Keystore.")
                .setView(box)
                .setPositiveButton("Shrani", null)
                .setNegativeButton(required ? "Zapri aplikacijo" : "Prekliči", null)
                .create();

        d.setOnShowListener(x -> {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String h = host.getText().toString().trim();
                if (!h.startsWith("http://") && !h.startsWith("https://")) h = "http://" + h;
                if (pw.getText().toString().isEmpty()) {
                    Toast.makeText(this, "Vpiši geslo FreshTomato.", Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    store.setRouter(h);
                    store.setUser(user.getText().toString().trim().isEmpty() ? "root" : user.getText().toString().trim());
                    store.setPassword(pw.getText().toString());
                    rebuildClient();
                    d.dismiss();
                    refreshStatus();
                } catch (Exception e) {
                    error(e);
                }
            });
            if (required) d.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> finish());
        });
        d.setCancelable(!required);
        d.show();
    }

    private void error(Exception e) {
        new AlertDialog.Builder(this)
                .setTitle("Napaka")
                .setMessage(e.getMessage() == null ? e.toString() : e.getMessage())
                .setPositiveButton("OK", null)
                .show();
    }
}
