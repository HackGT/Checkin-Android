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

public class SearchAdapter extends ArrayAdapter<Hacker> {
    private LayoutInflater inflater;
    private int viewResourceID;
    private ArrayList<Hacker> items;

    public SearchAdapter(@NonNull Context context, @LayoutRes int resource,
                         @NonNull ArrayList<Hacker> objects) {
        super(context, resource, objects);
        inflater = LayoutInflater.from(context);
        viewResourceID = resource;
        items = objects;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        Hacker hacker = items.get(position);
        if (v == null) {
            v = inflater.inflate(viewResourceID, parent, false);
            TextView name = v.findViewById(R.id.hacker_card_name);
            TextView email = v.findViewById(R.id.hacker_card_email);
            name.setText(hacker.getName());
            email.setText(hacker.getEmail());
        }
        return v;
    }
}
