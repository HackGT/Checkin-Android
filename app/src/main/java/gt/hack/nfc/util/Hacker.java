package gt.hack.nfc.util;


public class Hacker {
    private String name;
    private String school;
    private String tShirt;
    private String email;

    public Hacker(String name, String school, String tShirt, String email) {
        this.name = name;
        this.school = school;
        this.tShirt = tShirt;
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSchool() {
        return school;
    }

    public void setSchool(String school) {
        this.school = school;
    }

    public String gettShirt() {
        return tShirt;
    }

    public void settShirt(String tShirt) {
        this.tShirt = tShirt;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
