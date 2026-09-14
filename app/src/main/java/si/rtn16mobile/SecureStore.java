package si.rtn16mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SecureStore {
    private static final String PREF = "rt_n16_mobile";
    private static final String ALIAS = "rtn16_mobile_key";
    private final SharedPreferences prefs;

    public SecureStore(Context ctx) {
        prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public String getRouter() {
        return prefs.getString("router", "http://192.168.50.1");
    }

    public void setRouter(String s) {
        prefs.edit().putString("router", s).apply();
    }

    public String getUser() {
        return prefs.getString("user", "root");
    }

    public void setUser(String s) {
        prefs.edit().putString("user", s).apply();
    }

    public void setPassword(String password) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] enc = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
        prefs.edit()
                .putString("pw_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString("pw_enc", Base64.encodeToString(enc, Base64.NO_WRAP))
                .apply();
    }

    public String getPassword() {
        try {
            String ivs = prefs.getString("pw_iv", null);
            String encs = prefs.getString("pw_enc", null);
            if (ivs == null || encs == null) return "";
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, Base64.decode(ivs, Base64.NO_WRAP));
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            byte[] dec = cipher.doFinal(Base64.decode(encs, Base64.NO_WRAP));
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return kg.generateKey();
    }
}
