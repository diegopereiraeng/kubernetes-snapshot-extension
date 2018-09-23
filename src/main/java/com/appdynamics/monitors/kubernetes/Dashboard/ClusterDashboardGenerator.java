package com.appdynamics.monitors.kubernetes.Dashboard;

import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.monitors.kubernetes.Constants;
import com.appdynamics.monitors.kubernetes.Models.AdqlSearchObj;
import com.appdynamics.monitors.kubernetes.Models.AppDMetricObj;
import com.appdynamics.monitors.kubernetes.RestClient;
import com.appdynamics.monitors.kubernetes.Utilities;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Map;

import static com.appdynamics.monitors.kubernetes.Constants.*;


public class ClusterDashboardGenerator implements AMonitorTaskRunnable {
    protected static final Logger logger = LoggerFactory.getLogger(ClusterDashboardGenerator.class);
    private ArrayList<AppDMetricObj> metrics = new ArrayList<AppDMetricObj>();
    private Map<String, String> config;
    public ClusterDashboardGenerator(Map<String, String> config, ArrayList<AppDMetricObj> metrics){
        this.metrics = metrics;
        this.config = config;
    }


    @Override
    public void onTaskComplete() {

    }

    @Override
    public void run() {
        logger.info("Checking the dashboard.");
        validateDashboard(config);
    }

    public void validateDashboard(Map<String, String> config){
        //check if Dashboard exists
        String dashName = String.format("%s-%s-%s", Utilities.getClusterApplicationName(config), Utilities.getClusterTierName(config), config.get(CONFIG_DASH_NAME_SUFFIX));
        if (!dashboardExists(dashName, config)) {
            //cache searches
            ADQLSearchGenerator.loadAllSearches(config);
            //if not, read template
            String path = config.get(CONFIG_DASH_TEMPLATE_PATH);
            logger.info("Reading the dashboard template from {}", path);
            JsonNode template = readTemplate(path);
            JsonNode widgets = template.get("widgetTemplates");
            //update name
            ((ObjectNode)template).put("name", dashName);
            //create Dashboard
            logger.info("Creating the dashboard...");
            buildDashboard(metrics,  widgets);
            String updatedPath = writeTemplate(config, template);
            RestClient.createDashboard(config, updatedPath);
        }
        else{
            logger.info("Dashboard exists");
        }
    }

    public boolean dashboardExists(String dashName, Map<String, String> config){
        boolean exists = false;
        String path =  "restui/dashboards/getAllDashboardsByType/false";

        JsonNode dashboards = RestClient.callControllerAPI(path, config, "", "GET");

        for (final JsonNode dash : dashboards) {
            if (dash.get("name") != null && dash.get("name").asText().equals(dashName)){
                exists = true;
                break;
            }
        }
        return exists;
    }

    public JsonNode readTemplate(String path){
        JSONParser parser = new JSONParser();
        JsonNode node  = null;
        try {
            JSONObject a = (JSONObject)parser.parse(new FileReader(path));
            if (a != null){
                String json = a.toJSONString();
                ObjectMapper mapper = new ObjectMapper();
                node = mapper.readTree(json);
            }
        }
        catch (Exception ex){
            logger.error("Unable to load dashboard template." + path, ex);
        }
        return node;
    }

    public String writeTemplate(Map<String, String> config, JsonNode template){
        String path = "";
        try{

            String originalPath = config.get(CONFIG_DASH_TEMPLATE_PATH);
            String ext = ".json";
            if (originalPath.contains(ext)) {
                originalPath = originalPath.substring(0, originalPath.length() - ext.length());
            }
            path = String.format("%s_UPDATED.json", originalPath);
            ObjectMapper mapper = new ObjectMapper();
            ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
            writer.writeValue(new File(path), template);
        }
        catch (Exception ex){
            logger.error("Unable to save the updated dashboard template", ex);
        }
        return path;
    }

    public void buildDashboard(ArrayList<AppDMetricObj> metrics, JsonNode widgets){
        try {
            logger.info("Building dashboard...");
            for(AppDMetricObj metricObj : metrics) {
                updatedWidget(widgets, metricObj);
            }
        }
        catch (Exception ex){
            logger.error("Unable to save search", ex);
        }
    }

    private void updateDrillDown(Map<String, String> config, JsonNode widget, AdqlSearchObj adqlSearchObj){
        if (adqlSearchObj != null) {
            String link = String.format(AdqlSearchObj.searchUrlTemplate, Utilities.getControllerUrl(config), adqlSearchObj.getId());
            ((ObjectNode)widget).put("drillDownUrl", link);
            ((ObjectNode)widget).put("useMetricBrowserAsDrillDown", false);
        }
        else{
            //use metric browser
            ((ObjectNode)widget).put("useMetricBrowserAsDrillDown", true);
        }
    }

    private void updateMetricPath (Map<String, String> config, JsonNode metric, AppDMetricObj metricObj){
            if (metric != null) {
                ((ObjectNode) metric).put("metricPath", metricObj.getPath());
                JsonNode scope = metric.get("scopeEntity");
                ((ObjectNode) scope).put("applicationName", config.get(CONFIG_APP_NAME));
                ((ObjectNode) scope).put("entityName", Utilities.getClusterTierName(config));
            }
    }

    private JsonNode updateMetricNode(JsonNode metricTemplate, String metricPath, AppDMetricObj metricObj, JsonNode parentWidget ){
        JsonNode metricNode = null;
        if (metricTemplate == null){
            return metricNode;
        }
        if (metricTemplate.get("metricPath") == null){
            //expression
            metricNode = updateExpression(metricTemplate, metricPath, metricObj);
        }
        else if (metricTemplate.get("metricPath").asText().equals(metricPath)){
            metricNode = metricTemplate;
            updateMetricPath(config, metricNode, metricObj);
            AdqlSearchObj adqlSearchObj =  ADQLSearchGenerator.getSearchForMetric(config, metricObj);
            updateDrillDown(config, parentWidget, adqlSearchObj);
        }
        return metricNode;
    }

    private JsonNode updateExpression(JsonNode parentExpression, String metricPath, AppDMetricObj metricObj){
        JsonNode theNode = null;
        for(int i = 0; i < 2; i++){
            String expNodeName = String.format("expression%d", i+1);
            JsonNode expNode = parentExpression.get(expNodeName);
            if (expNode != null){
                if (expNode.get("metricPath") != null && expNode.get("metricPath").asText().equals(metricPath)){
                    theNode = expNode;
                    updateMetricPath(config, expNode, metricObj);
                }
                else{
                    theNode = updateExpression(expNode, metricPath, metricObj);
                }
            }
        }
        return theNode;
    }

    private void updatedWidget(JsonNode widgets, AppDMetricObj metricObj){
        for(JsonNode widget : widgets){
            JsonNode templates = widget.get("dataSeriesTemplates");
            if (templates != null) {
                String path = metricObj.getPath();
                for (JsonNode t : templates) {
                    JsonNode match = t.get("metricMatchCriteriaTemplate");
                    if (match != null) {
                        if (match.has("applicationName")){
                            ((ObjectNode) match).put("applicationName", config.get(CONFIG_APP_NAME));
                        }
                        JsonNode metricTemplate = match.get("metricExpressionTemplate");
                        updateMetricNode(metricTemplate, path, metricObj, widget);
                    }
                }
            }
        }
    }
}
