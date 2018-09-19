package com.appdynamics.monitors.kubernetes;

public class AppDRestAuth {
    private String token;
    private String cookie;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }
}
