package com.appdynamics.monitors.kubernetes.Models;

public class NodeRole {
    private String nodeName = "";
    private String roleName = "";

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(int id) {
        this.nodeName = nodeName;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String name) {
        this.roleName = roleName;
    }
}
