package com.appdynamics.monitors.kubernetes.Models;


import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;

public class SummaryObj {
    private ObjectNode data;
    private ArrayList<AppDMetricObj> metricsMetadata;
    private String path = "";

    public SummaryObj(){

    }
    public SummaryObj(ObjectNode data, ArrayList<AppDMetricObj> metadata, String path){
        this.data = data;
        this.metricsMetadata = metadata;
        this.path = path;
    }

    public ObjectNode getData() {
        return data;
    }

    public void setData(ObjectNode data) {
        this.data = data;
    }

    public ArrayList<AppDMetricObj> getMetricsMetadata() {
        return metricsMetadata;
    }

    public void setMetricsMetadata(ArrayList<AppDMetricObj> metricsMetadata) {
        this.metricsMetadata = metricsMetadata;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
