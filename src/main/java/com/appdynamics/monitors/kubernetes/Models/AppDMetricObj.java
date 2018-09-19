package com.appdynamics.monitors.kubernetes.Models;

public class AppDMetricObj {
    private String name = ""; //metric name
    private String parentSchema = ""; //schema where the metric is stored
    private String parentSchemaDefinition = ""; //name of the parent schema definition
    private String path = ""; //metric path reference for the dashboard
    private String query = ""; //search query associated with the drill-down
    private String searchToken = ""; //reference to the search displayed on drill down. Replacement token on dashboard
    private String healthRuleToken = "";  //reference of the health rule associated with the metric. Replacement token on dashboard
    private String metricToken = "";//reference of the metric. Replacement token on dashboard
    private String widgetName = "";


    public AppDMetricObj(String name, String parentSchema, String schemaDefinitionName, String query, String rootPath){
        this.name = name;
        this.parentSchema = parentSchema;
        this.setParentSchemaDefinition(schemaDefinitionName);
        this.query = query;
        this.searchToken = String.format("q-%s", this.name);
        this.healthRuleToken = String.format("hr-%s", this.name);
        this.metricToken = String.format("m-%s", this.name);
        this.setPath(rootPath + this.name);
        this.setWidgetName(this.metricToken);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParentSchema() {
        return parentSchema;
    }

    public void setParentSchema(String parentSchema) {
        this.parentSchema = parentSchema;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getSearchToken() {
        return searchToken;
    }

    public void setSearchToken(String searchToken) {
        this.searchToken = searchToken;
    }

    public String getHealthRuleToken() {
        return healthRuleToken;
    }

    public void setHealthRuleToken(String healthRuleToken) {
        this.healthRuleToken = healthRuleToken;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getParentSchemaDefinition() {
        return parentSchemaDefinition;
    }

    public void setParentSchemaDefinition(String parentSchemaDefinition) {
        this.parentSchemaDefinition = parentSchemaDefinition;
    }

    public String getMetricToken() {
        return metricToken;
    }

    public String getWidgetName() {
        return widgetName;
    }

    public void setWidgetName(String widgetName) {
        this.widgetName = widgetName;
    }
}
