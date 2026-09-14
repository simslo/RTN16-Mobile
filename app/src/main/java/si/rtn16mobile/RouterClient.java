package si.rtn16mobile;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RouterClient {
    private final String baseUrl;
    private final String user;
    private final String pass;
    private final String host;
    private final int port;
    private String httpId = "";

    public RouterClient(String baseUrl, String user, String pass) {
        try {
            String u = baseUrl.trim();
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
            while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
            URL parsed = new URL(u);
            if (!"http".equalsIgnoreCase(parsed.getProtocol())) {
                throw new IllegalArgumentException("RT-N16 mora uporabljati http://, ne https://");
            }
            this.baseUrl = u;
            this.host = parsed.getHost();
            this.port = parsed.getPort() > 0 ? parsed.getPort() : 80;
            this.user = user;
            this.pass = pass;
        } catch (Exception e) {
            throw new IllegalArgumentException("Neveljaven naslov routerja: " + baseUrl, e);
        }
    }

    public String shell(String command) throws Exception {
        ensureHttpId();
        String body = "action=execute&command=" + URLEncoder.encode(command, "UTF-8");
        if (!httpId.isEmpty()) body += "&_http_id=" + URLEncoder.encode(httpId, "UTF-8");

        RawResponse r = request("POST", "/shell.cgi", body);
        if (r.status >= 400) {
            throw new Exception("FreshTomato HTTP " + r.status + (r.status == 401 ? " - napačno uporabniško ime ali geslo" : ""));
        }
        return decodeCmdResult(r.body);
    }

    public String getPage(String path) throws Exception {
        RawResponse r = request("GET", path, null);
        if (r.status >= 400) {
            throw new Exception("FreshTomato HTTP " + r.status + (r.status == 401 ? " - napačno uporabniško ime ali geslo" : ""));
        }
        return r.body;
    }

    private void ensureHttpId() throws Exception {
        if (!httpId.isEmpty()) return;
        String page = getPage("/tools-shell.asp");

        Pattern[] p = new Pattern[] {
                Pattern.compile("http_id\\s*[:=]\\s*['\\\"]([^'\\\"]+)['\\\"]", Pattern.CASE_INSENSITIVE),
                Pattern.compile("_http_id[^>]*value=['\\\"]([^'\\\"]+)['\\\"]", Pattern.CASE_INSENSITIVE),
                Pattern.compile("_http_id\\s*[:=]\\s*['\\\"]([^'\\\"]+)['\\\"]", Pattern.CASE_INSENSITIVE)
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

    /*
     * FreshTomato on the RT-N16 uses an old embedded HTTP server. Android's
     * HttpURLConnection is backed by OkHttp and some Android versions reject
     * the router's short/old HTTP responses with "unexpected end of stream".
     * Use a tiny HTTP/1.0 client over a raw socket instead. This is local LAN
     * traffic only and exactly matches the simple protocol the router expects.
     */
    private RawResponse request(String method, String path, String formBody) throws Exception {
        byte[] bodyBytes = formBody == null ? new byte[0] : formBody.getBytes(StandardCharsets.UTF_8);
        String rawCreds = user + ":" + pass;
        String auth = Base64.encodeToString(rawCreds.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

        StringBuilder h = new StringBuilder();
        h.append(method).append(" ").append(path).append(" HTTP/1.0\r\n");
        h.append("Host: ").append(host).append("\r\n");
        h.append("Authorization: Basic ").append(auth).append("\r\n");
        h.append("User-Agent: RTN16-Mobile/0.2\r\n");
        h.append("Accept: */*\r\n");
        h.append("Connection: close\r\n");
        if (formBody != null) {
            h.append("Content-Type: application/x-www-form-urlencoded\r\n");
            h.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
        }
        h.append("\r\n");

        byte[] all;
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(15000);
            OutputStream out = socket.getOutputStream();
            out.write(h.toString().getBytes(StandardCharsets.ISO_8859_1));
            if (bodyBytes.length > 0) out.write(bodyBytes);
            out.flush();

            InputStream in = socket.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) != -1) buf.write(tmp, 0, n);
            all = buf.toByteArray();
        }

        if (all.length == 0) throw new Exception("FreshTomato je zaprl HTTP povezavo brez odgovora");

        String raw = new String(all, StandardCharsets.ISO_8859_1);
        int split = raw.indexOf("\r\n\r\n");
        int sepLen = 4;
        if (split < 0) {
            split = raw.indexOf("\n\n");
            sepLen = 2;
        }
        if (split < 0) throw new Exception("Neveljaven HTTP odgovor iz FreshTomato");

        String headers = raw.substring(0, split);
        byte[] payload = java.util.Arrays.copyOfRange(all,
                raw.substring(0, split + sepLen).getBytes(StandardCharsets.ISO_8859_1).length,
                all.length);

        int status = 0;
        Matcher sm = Pattern.compile("^HTTP/\\S+\\s+(\\d{3})", Pattern.CASE_INSENSITIVE).matcher(headers);
        if (sm.find()) status = Integer.parseInt(sm.group(1));

        String lower = headers.toLowerCase(Locale.ROOT);
        if (lower.contains("transfer-encoding: chunked")) payload = decodeChunked(payload);

        RawResponse rr = new RawResponse();
        rr.status = status == 0 ? 200 : status;
        rr.body = new String(payload, StandardCharsets.UTF_8);
        return rr;
    }

    private static byte[] decodeChunked(byte[] input) throws Exception {
        int pos = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (pos < input.length) {
            int lineEnd = indexOf(input, pos, new byte[]{'\r','\n'});
            int eol = 2;
            if (lineEnd < 0) {
                lineEnd = indexOf(input, pos, new byte[]{'\n'});
                eol = 1;
            }
            if (lineEnd < 0) break;
            String line = new String(input, pos, lineEnd - pos, StandardCharsets.US_ASCII).trim();
            int semi = line.indexOf(';');
            if (semi >= 0) line = line.substring(0, semi);
            int size = Integer.parseInt(line.trim(), 16);
            pos = lineEnd + eol;
            if (size == 0) break;
            if (pos + size > input.length) throw new Exception("Neveljaven chunked HTTP odgovor");
            out.write(input, pos, size);
            pos += size;
            if (pos + 1 < input.length && input[pos] == '\r' && input[pos + 1] == '\n') pos += 2;
            else if (pos < input.length && input[pos] == '\n') pos += 1;
        }
        return out.toByteArray();
    }

    private static int indexOf(byte[] a, int start, byte[] needle) {
        outer:
        for (int i = start; i <= a.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (a[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static class RawResponse {
        int status;
        String body;
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
