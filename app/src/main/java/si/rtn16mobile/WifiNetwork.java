package si.rtn16mobile;

public class WifiNetwork {
    public String ssid = "";
    public String bssid = "";
    public int channel = 0;
    public int rssi = -100;
    public String security = "OPEN";

    @Override public String toString() {
        String sec = "OPEN".equals(security) ? "odprto" : security;
        return ssid + "\n" + rssi + " dBm   kanal " + channel + "   " + sec;
    }
}
