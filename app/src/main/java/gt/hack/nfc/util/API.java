package gt.hack.nfc.util;

import android.content.SharedPreferences;
import android.util.Log;

import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.exception.ApolloException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


import gt.hack.nfc.CheckInTagMutation;
import gt.hack.nfc.CheckOutTagMutation;
import gt.hack.nfc.UserGetQuery;
import gt.hack.nfc.UserSearchQuery;
import gt.hack.nfc.fragment.TagFragment;
import gt.hack.nfc.fragment.UserFragment;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class API {
    public static final String BASE_URL = "https://checkin.hack.gt";


    public static boolean login(String username, String password,
                                SharedPreferences preferences) throws IOException {
        OkHttpClient client = new OkHttpClient();
        RequestBody formBody = new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build();
        Request request = new Request.Builder().url(BASE_URL + "/api/user/login").post(formBody).build();
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
                        authCookie = cookie.substring(cookie.indexOf("auth="));
                        authCookie = authCookie.substring(0, authCookie.indexOf(";"));
                    }
                }
                preferences.edit().putString("cookie", authCookie).putBoolean("loggedIn", true).commit();
                return true;
            }
        }
        return false;
    }
    private static ApolloClient getApolloClient (final SharedPreferences preferences) {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request original = chain.request();
                Request.Builder builder = original.newBuilder().method(original.method(),
                        original.body());
                builder.header("Cookie", preferences.getString("cookie", ""));
                System.out.println(chain.request().url());
                return chain.proceed(builder.build());
            }
        })
                .build();
        return ApolloClient.builder()
                .serverUrl(BASE_URL + "/graphql/")
                .okHttpClient(client).build();
    }


    public static ArrayList<UserFragment> getUsers(final SharedPreferences preferences, String query)
            throws ApolloException {
        ApolloClient apolloClient = getApolloClient(preferences);
        com.apollographql.apollo.api.Response<UserSearchQuery.Data> response =
                apolloClient.query(new UserSearchQuery(query, 20)).execute();
        ArrayList<UserFragment> users = new ArrayList<>();
        if (response.hasErrors()) {
            Log.e("apollo", response.errors().toString());
            return null;
        }
        for (UserSearchQuery.Search_user_simple user : response.data().search_user_simple()) {
            users.add(user.user().fragments().userFragment());
        }
        return users;
    }

    public static UserFragment getUserId(final SharedPreferences preferences, String id)
            throws ApolloException {
        ApolloClient apolloClient = getApolloClient(preferences);
        com.apollographql.apollo.api.Response<UserGetQuery.Data> response =
                apolloClient.query(new UserGetQuery(id)).execute();
        if (response.hasErrors()) {
            Log.e("apollo", response.errors().toString());
            return null;
        }
        if (response.data().user() != null && response.data().user().user() != null) {
             return response.data().user().user().fragments().userFragment();
        }
        return null;
    }

    public static HashMap<String, TagFragment>  checkInTag(final SharedPreferences preferences,
                                  String userid, String tag) throws ApolloException {
        ApolloClient apolloClient = getApolloClient(preferences);
        com.apollographql.apollo.api.Response<CheckInTagMutation.Data> response =
                apolloClient.mutate(new CheckInTagMutation(userid, tag)).execute();
        if (response.hasErrors()) {
            Log.e("apollo", response.errors().toString());
            return null;
        }
        HashMap<String, TagFragment> tags = new HashMap<>();
        if (response.data().check_in() != null && response.data().check_in().tags() != null) {
            for (CheckInTagMutation.Tag t : response.data().check_in().tags()) {
               tags.put(t.fragments().tagFragment().tag().name(),t.fragments().tagFragment());
            }
            return tags;
        }
        return null;
    }

    public static HashMap<String, TagFragment> checkOutTag(final SharedPreferences preferences,
                                                           String userid, String tag) throws ApolloException {
        ApolloClient apolloClient = getApolloClient(preferences);
        com.apollographql.apollo.api.Response<CheckOutTagMutation.Data> response =
                apolloClient.mutate(new CheckOutTagMutation(userid, tag)).execute();
        if (response.hasErrors()) {
            Log.e("apollo", response.errors().toString());
            return null;
        }
        HashMap<String, TagFragment> tags = new HashMap<>();
        if (response.data().check_in() != null && response.data().check_in().tags() != null) {
            for (CheckOutTagMutation.Tag t : response.data().check_in().tags()) {
                tags.put(t.fragments().tagFragment().tag().name(),t.fragments().tagFragment());
            }
            return tags;
        }
        return null;
    }
}
