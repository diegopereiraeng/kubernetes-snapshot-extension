package com.appdynamics.monitors.kubernetes.Models;

public class AdqlSearchObj {
    private int id = 0;
    private  String name = "";
    public static String searchUrlTemplate = "%s#/location=ANALYTICS_ADQL_SEARCH&searchId=%d";

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
