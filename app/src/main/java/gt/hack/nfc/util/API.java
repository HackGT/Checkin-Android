package gt.hack.nfc.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;


import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.exception.ApolloException;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


import gt.hack.nfc.CheckInTagMutation;
import gt.hack.nfc.CheckOutTagMutation;
import gt.hack.nfc.TagsGetQuery;
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


    public static boolean login(String username, String password,
                                SharedPreferences preferences) throws IOException {
        OkHttpClient client = new OkHttpClient();
        RequestBody formBody = new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build();
        Request request = new Request.Builder().url(preferences.getString("url", Util.DEFAULT_SERVER)
                + "/api/user/login").post(formBody).build();
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
                .serverUrl(preferences.getString("url", Util.DEFAULT_SERVER) + "/graphql/")
                .okHttpClient(client).build();
    }


    public static ArrayList<UserFragment> getUsers(final SharedPreferences preferences, String query)
            throws ApolloException {
        ApolloClient apolloClient = getApolloClient(preferences);
//        com.apollographql.apollo.api.Response<UserSearchQuery.Data> response =
//                apolloClient.query(new UserSearchQuery(query, 20)).execute();
        final UserSearchQuery userSearchQuery = UserSearchQuery.builder()
                .number(20).text(query).build();

        apolloClient.query(userSearchQuery).enqueue(new ApolloCall.Callback<UserSearchQuery.Data>() {
            @Override
            public void onResponse(@NotNull com.apollographql.apollo.api.Response<UserSearchQuery.Data> response) {
                Log.i("CHECKIN-ANDROID", "inside onResponse");
                Log.i("CHECKIN-ANDROID", "response is " + response);
                ArrayList<UserFragment> users = new ArrayList<>();
                for (UserSearchQuery.Search_user_simple user : response.data().search_user_simple()) {
                    users.add(user.user().fragments().userFragment());
                }
                for (UserSearchQuery.Search_user_simple user : response.data().search_user_simple()) {
                    users.add(user.user().fragments().userFragment());
                    return users;
                }
            }

            @Override
            public void onFailure(@NotNull ApolloException e) {
                Log.i("CHECKIN-ANDROID", "It didn't work, classic");
            }
        });


    }

    public static ArrayList<String> getTags(final SharedPreferences preferences) throws ApolloException {
        ApolloClient apolloClient = getApolloClient(preferences);
        com.apollographql.apollo.api.Response<TagsGetQuery.Data> response =
                apolloClient.query(new TagsGetQuery());
        if (response.data().tags() != null) {
            ArrayList<String> items = new ArrayList<>();
            for (TagsGetQuery.Tag t : response.data().tags()) {
                items.add(t.name());
            }
            return items;
        }
        return null;
    }

    public static UserFragment getUserById(final SharedPreferences preferences, String id)
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

    public static HashMap<String, TagFragment> getTagsForUser(final SharedPreferences preferences, String id)
            throws ApolloException {
        ApolloClient apolloClient = getApolloClient(preferences);
        com.apollographql.apollo.api.Response<UserGetQuery.Data> response =
                apolloClient.query(new UserGetQuery(id)).execute();
        if (response.hasErrors()) {
            Log.e("apollo", response.errors().toString());
            return null;
        }
        HashMap<String, TagFragment> tags = new HashMap<>();
        if (response.data().user() != null && response.data().user().tags() != null) {
            for (UserGetQuery.Tag t : response.data().user().tags()) {
                tags.put(t.fragments().tagFragment().tag().name(),t.fragments().tagFragment());
            }
            return tags;
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
        if (response.data().check_out() != null && response.data().check_out().tags() != null) {
            for (CheckOutTagMutation.Tag t : response.data().check_out().tags()) {
                tags.put(t.fragments().tagFragment().tag().name(),t.fragments().tagFragment());
            }
            return tags;
        }
        return null;
    }

    public interface Supplier<T> {
        T get() throws ApolloException;
    }

    public interface Consumer<T> {
        void run(T data);
    }

    public static class AsyncGraphQlTask<O> extends AsyncTask<Supplier<O>, Void, List<O>> {
        private Context context;
        private Consumer<List<O>> callback;

        public AsyncGraphQlTask(Context context, Consumer<List<O>> callback) {
            this.context = context;
            this.callback = callback;
        }

        @Override
        protected List<O> doInBackground(Supplier<O>... params) {
            ArrayList<O> outputs = new ArrayList<>();
            for (Supplier<O> supplier : params) {
                try {
                    outputs.add(supplier.get());
                }
                catch (Exception err) {
                    err.printStackTrace();
                    Toast.makeText(context, err.getMessage(), Toast.LENGTH_LONG).show();
                    outputs.add(null);
                }
            }
            return outputs;
        }

        @Override
        protected void onPostExecute(List<O> data) {
            this.callback.run(data);
        }
    }
}
