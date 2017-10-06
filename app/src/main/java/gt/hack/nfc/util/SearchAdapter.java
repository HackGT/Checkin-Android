package gt.hack.nfc.util;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

import gt.hack.nfc.R;
import gt.hack.nfc.fragment.UserFragment;

public class SearchAdapter extends ArrayAdapter<UserFragment> {
    private LayoutInflater inflater;
    private int viewResourceID;

    public SearchAdapter(@NonNull Context context, @LayoutRes int resource,
                         @NonNull ArrayList<UserFragment> objects) {
        super(context, resource, objects);
        inflater = LayoutInflater.from(context);
        viewResourceID = resource;
        addAll(objects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        UserFragment hacker = getItem(position);
        if (v == null) {
            v = inflater.inflate(viewResourceID, parent, false);
        }
        TextView name = v.findViewById(R.id.hacker_card_name);
        TextView email = v.findViewById(R.id.hacker_card_email);
        name.setText(hacker.name());
        email.setText(hacker.email());
        return v;
    }
}
