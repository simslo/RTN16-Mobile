package si.rtn16mobile;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RouterClient {
    private final String baseUrl;
    private final String user;
    private final String pass;
    private String httpId = "";

    public RouterClient(String baseUrl, String user, String pass) {
        String u = baseUrl.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        this.baseUrl = u;
        this.user = user;
        this.pass = pass;
    }

    public String shell(String command) throws Exception {
        ensureHttpId();
        String body = "action=execute&command=" + URLEncoder.encode(command, "UTF-8");
        if (!httpId.isEmpty()) body += "&_http_id=" + URLEncoder.encode(httpId, "UTF-8");

        HttpURLConnection c = connection("/shell.cgi", "POST");
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(data.length);
        c.setDoOutput(true);
        try (OutputStream out = c.getOutputStream()) {
            out.write(data);
        }
        String response = readResponse(c);
        if (c.getResponseCode() >= 400) {
            throw new Exception("HTTP " + c.getResponseCode() + ": " + response);
        }
        return decodeCmdResult(response);
    }

    public String getPage(String path) throws Exception {
        HttpURLConnection c = connection(path, "GET");
        String s = readResponse(c);
        if (c.getResponseCode() >= 400) throw new Exception("HTTP " + c.getResponseCode());
        return s;
    }

    private void ensureHttpId() throws Exception {
        if (!httpId.isEmpty()) return;
        String page = getPage("/tools-shell.asp");

        Pattern[] p = new Pattern[] {
                Pattern.compile("http_id\\s*[:=]\\s*['\\\"]([^'\\\"]+)['\\\"]", Pattern.CASE_INSENSITIVE),
                Pattern.compile("_http_id[^>]*value=['\\\"]([^'\\\"]+)['\\\"]", Pattern.CASE_INSENSITIVE)
        };
        for (Pattern x : p) {
            Matcher m = x.matcher(page);
            if (m.find()) {
                httpId = m.group(1);
                return;
            }
        }
        httpId = "";
    }

    private HttpURLConnection connection(String path, String method) throws Exception {
        URL url = new URL(baseUrl + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(5000);
        c.setReadTimeout(15000);
        String raw = user + ":" + pass;
        String auth = Base64.encodeToString(raw.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        c.setRequestProperty("Authorization", "Basic " + auth);
        c.setRequestProperty("Accept", "*/*");
        c.setRequestProperty("Connection", "close");
        return c;
    }

    private static String readResponse(HttpURLConnection c) throws Exception {
        InputStream in;
        try {
            in = c.getInputStream();
        } catch (Exception e) {
            in = c.getErrorStream();
            if (in == null) throw e;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String decodeCmdResult(String s) {
        Matcher m = Pattern.compile("cmdresult\\s*=\\s*(['\\\"])(.*?)\\1\\s*;", Pattern.DOTALL).matcher(s);
        if (!m.find()) return s.trim();
        String x = m.group(2);
        x = x.replace("\\\\n", "\n")
             .replace("\\\\r", "\r")
             .replace("\\\\t", "\t")
             .replace("\\\\'", "'")
             .replace("\\\\\"", "\"")
             .replace("\\\\\\\\", "\\");
        Matcher hx = Pattern.compile("\\\\x([0-9A-Fa-f]{2})").matcher(x);
        StringBuffer out = new StringBuffer();
        while (hx.find()) {
            int v = Integer.parseInt(hx.group(1), 16);
            hx.appendReplacement(out, Matcher.quoteReplacement(Character.toString((char) v)));
        }
        hx.appendTail(out);
        return out.toString().trim();
    }

    public static String shQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
