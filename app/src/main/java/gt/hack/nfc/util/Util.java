package gt.hack.nfc.util;


import java.util.List;

import gt.hack.nfc.fragment.UserFragment;

public class Util {
    public static String getValueOfQuestion(List<UserFragment.Question> questions, String name) {
        for (UserFragment.Question q : questions) {
            if (q.name().equals(name)) {
                return q.value();
            }
        }
        return null;
    }
}
