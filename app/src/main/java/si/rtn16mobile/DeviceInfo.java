package si.rtn16mobile;

public class DeviceInfo {
    public String mac = "";
    public String ip = "";
    public String name = "";
    public String rssi = "";

    @Override public String toString() {
        String title = (name == null || name.isEmpty()) ? mac : name;
        StringBuilder b = new StringBuilder(title);
        if (ip != null && !ip.isEmpty()) b.append("\n").append(ip);
        b.append("   ").append(mac);
        if (rssi != null && !rssi.isEmpty()) b.append("   ").append(rssi).append(" dBm");
        return b.toString();
    }
}
