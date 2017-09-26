package gt.hack.nfc.util;

import android.content.SharedPreferences;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class API {
    public static final String checkinUrl = "https://checkin.hack.gt";


    public static boolean login(String username, String password,
                                SharedPreferences preferences) throws IOException {
        OkHttpClient client = new OkHttpClient();
        RequestBody formBody = new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build();
        Request request = new Request.Builder().url(checkinUrl + "/api/user/login").post(formBody).build();
        Call call = client.newCall(request);
        try (Response response = call.execute()) {
            System.out.println(response);
            if (response.isSuccessful()) {
                List<String> cookieResponses = response.headers("set-cookie");
                System.out.println(cookieResponses);
                String authCookie = "";
                for (String cookie : cookieResponses) {
                    // Gross string manipulation
                    if (cookie.contains("auth=")) {
                        authCookie = cookie.substring(cookie.indexOf("auth=") + 5);
                        authCookie = authCookie.substring(0, authCookie.indexOf(";"));
                    }
                }
                preferences.edit().putString("cookie", authCookie).putBoolean("loggedIn", true).commit();
                return true;
            }
        }
        return false;
    }
}
