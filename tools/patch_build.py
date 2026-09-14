from pathlib import Path
import re

p = Path('app/src/main/java/si/rtn16mobile/MainActivity.java')
s = p.read_text(encoding='utf-8')

# Router settings button opens FreshTomato GUI directly.
old = 'settings.setOnClickListener(v -> showSettings(false));'
new = '''settings.setOnClickListener(v -> {
    try {
        String url = store.getRouter();
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url;
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(i);
    } catch (Exception e) {
        error(e);
    }
});'''
if old in s:
    s = s.replace(old, new, 1)

# Rename device button and include wired LAN clients.
s = s.replace('Button dev = button("NAPRAVE NA MOBILE", false);',
              'Button dev = button("POVEZANE NAPRAVE", false);')
s = s.replace('contentTitle.setText("Naprave na ASUS-MOBILE");',
              'contentTitle.setText("Povezane naprave (Wi-Fi + LAN)");')

pattern = re.compile(r'    private List<DeviceInfo> parseDevices\(String raw\) \{.*?\n    \}\n\n    private void addDeviceCard', re.S)
replacement = '''    private List<DeviceInfo> parseDevices(String raw) {
        String[] p1 = raw.split("__ARP__", 2);
        String assocPart = p1.length > 0 ? p1[0].replace("__ASSOC__", "") : "";
        String rest = p1.length > 1 ? p1[1] : "";
        String[] p2 = rest.split("__LEASES__", 2);
        String arpPart = p2.length > 0 ? p2[0] : "";
        String leasePart = p2.length > 1 ? p2[1] : "";

        Set<String> assoc = new HashSet<>();
        Matcher ma = Pattern.compile("(?i)assoclist\\\\s+([0-9a-f:]{17})").matcher(assocPart);
        while (ma.find()) assoc.add(ma.group(1).toUpperCase(Locale.ROOT));

        Map<String,String> ipByMac = new HashMap<>();
        Set<String> activeLan = new HashSet<>();
        for (String l : arpPart.split("\\\\n")) {
            String[] a = l.trim().split("\\\\s+");
            if (a.length >= 6 && a[0].matches("192\\\\.168\\\\.50\\\\.\\\\d+") &&
                    a[3].matches("(?i)[0-9a-f:]{17}") && !"00:00:00:00:00:00".equals(a[3])) {
                String mac = a[3].toUpperCase(Locale.ROOT);
                ipByMac.put(mac, a[0]);
                if (!assoc.contains(mac) && !"192.168.50.1".equals(a[0])) activeLan.add(mac);
            }
        }

        Map<String,String> nameByMac = new HashMap<>();
        for (String l : leasePart.split("\\\\n")) {
            String[] a = l.trim().split("\\\\s+");
            if (a.length >= 4 && a[1].matches("(?i)[0-9a-f:]{17}")) {
                String mac = a[1].toUpperCase(Locale.ROOT);
                String name = "-".equals(a[3]) ? "" : a[3];
                nameByMac.put(mac, name);
                if (!ipByMac.containsKey(mac) && a[2].matches("192\\\\.168\\\\.50\\\\.\\\\d+")) ipByMac.put(mac, a[2]);
            }
        }

        List<DeviceInfo> out = new ArrayList<>();
        Set<String> all = new HashSet<>();
        all.addAll(assoc);
        all.addAll(activeLan);

        for (String mac : all) {
            DeviceInfo d = new DeviceInfo();
            d.mac = mac;
            d.ip = ipByMac.getOrDefault(mac, "");
            d.name = nameByMac.getOrDefault(mac, "");
            d.connection = assoc.contains(mac) ? "Wi-Fi / ASUS-MOBILE" : "LAN";
            out.add(d);
        }

        out.sort((a,b) -> {
            int ca = "LAN".equals(a.connection) ? 1 : 0;
            int cb = "LAN".equals(b.connection) ? 1 : 0;
            if (ca != cb) return Integer.compare(ca, cb);
            String na = (a.name == null || a.name.isEmpty()) ? a.mac : a.name;
            String nb = (b.name == null || b.name.isEmpty()) ? b.mac : b.name;
            return na.compareToIgnoreCase(nb);
        });
        return out;
    }

    private void addDeviceCard'''
if not pattern.search(s):
    raise SystemExit('parseDevices method pattern not found')
s = pattern.sub(lambda m: replacement, s, count=1)

old_card = 'TextView b = text((d.ip == null || d.ip.isEmpty() ? "IP: -" : "IP: " + d.ip) + "\\nMAC: " + d.mac, 13, false);'
new_card = 'TextView b = text("Povezava: " + d.connection + "\\n" + (d.ip == null || d.ip.isEmpty() ? "IP: -" : "IP: " + d.ip) + "\\nMAC: " + d.mac, 13, false);'
if old_card not in s:
    raise SystemExit('device card pattern not found')
s = s.replace(old_card, new_card, 1)

# Add Ping button after status refresh. It always sends exactly four ICMP echo requests.
ping_anchor = '''        Button refresh = button("OSVEŽI STATUS", false);
        refresh.setOnClickListener(v -> refreshStatus());
        buttons.addView(refresh, buttonLp());
'''
ping_ui = ping_anchor + '''
        Button ping = button("PING", false);
        ping.setOnClickListener(v -> showPingDialog());
        buttons.addView(ping, buttonLp());
'''
if 'Button ping = button("PING", false);' not in s:
    if ping_anchor not in s:
        raise SystemExit('refresh button anchor not found')
    s = s.replace(ping_anchor, ping_ui, 1)

# Add ping dialog and execution methods before settings dialog.
settings_anchor = '    private void showSettings(boolean required) {'
ping_methods = '''    private void showPingDialog() {
        EditText ip = new EditText(this);
        ip.setSingleLine(true);
        ip.setText("1.1.1.1");
        ip.setSelection(ip.getText().length());
        ip.setHint("IP naslov");
        ip.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(this)
                .setTitle("Ping - 4 paketi")
                .setMessage("Privzeto je 1.1.1.1. IP lahko prepišeš z drugim naslovom.")
                .setView(ip)
                .setPositiveButton("PING", (d, w) -> {
                    String target = ip.getText().toString().trim();
                    if (!isValidIpv4(target)) {
                        Toast.makeText(this, "Vpiši veljaven IPv4 naslov.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    runPing(target);
                })
                .setNegativeButton("Prekliči", null)
                .show();
    }

    private boolean isValidIpv4(String ip) {
        String[] parts = ip.split("\\\\.");
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            int value = 0;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < '0' || c > '9') return false;
                value = value * 10 + (c - '0');
            }
            if (value > 255) return false;
        }
        return true;
    }

    private void runPing(String target) {
        busy(true);
        contentTitle.setText("Ping: " + target + " (4x)");
        content.removeAllViews();

        exec.execute(() -> {
            try {
                String out = router.shell("ping -c 4 " + RouterClient.shQuote(target));
                ui.post(() -> {
                    busy(false);
                    TextView result = text(out.isEmpty() ? "Ni odgovora." : out, 14, false);
                    result.setTypeface(android.graphics.Typeface.MONOSPACE);
                    result.setTextIsSelectable(true);
                    result.setBackgroundResource(R.drawable.card_bg);
                    result.setPadding(dp(14), dp(12), dp(14), dp(12));
                    content.addView(result, lpMatchWrap());
                });
            } catch (Exception e) {
                ui.post(() -> {
                    busy(false);
                    error(e);
                });
            }
        });
    }

'''
if 'private void showPingDialog()' not in s:
    if settings_anchor not in s:
        raise SystemExit('settings method anchor not found')
    s = s.replace(settings_anchor, ping_methods + settings_anchor, 1)

p.write_text(s, encoding='utf-8')

p2 = Path('app/src/main/java/si/rtn16mobile/DeviceInfo.java')
d = p2.read_text(encoding='utf-8')
if 'public String connection' not in d:
    d = d.replace('    public String rssi = "";\n', '    public String rssi = "";\n    public String connection = "";\n')
p2.write_text(d, encoding='utf-8')
