package com.appdynamics.monitors.kubernetes.Dashboard;

import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.conf.MonitorConfiguration;
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
import com.google.gson.JsonArray;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
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
        //cache searches
        ADQLSearchGenerator.loadAllSearches(config);
        //check if Dashboard exists
        String dashName = String.format("%s-%s-%s", Utilities.getClusterApplicationName(config), config.get(CONFIG_APP_TIER_NAME), config.get(CONFIG_DASH_NAME_SUFFIX));
        if (!dashboardExists(dashName, config)) {
            //if not, read template
            String path = config.get(CONFIG_DASH_TEMPLATE_PATH);
            logger.info("Reading the dashboard template from {}", path);
            JsonNode template = readTemplate(path);
            JsonNode widgets = template.get("widgetTemplates");
            //create Dashboard
            logger.info("Creating the dashboard...");
            buildDashboard(config, metrics,  widgets);
            writeTemplate(config, template);
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
            logger.error("Unable to load dashboard template.", ex);
        }
        return node;
    }

    private void writeTemplate(Map<String, String> config, JsonNode template){
        try{
            String originalPath = config.get(CONFIG_DASH_TEMPLATE_PATH);
            String ext = ".json";
            if (originalPath.contains(ext)) {
                originalPath = originalPath.substring(0, originalPath.length() - ext.length());
            }

            String path = String.format("%s_UPDATED.json", originalPath);
            ObjectMapper mapper = new ObjectMapper();
            ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
            writer.writeValue(new File(path), template);
        }
        catch (Exception ex){
            logger.error("Unable to save the updated dashboard template", ex);
        }
    }

    public void buildDashboard(Map<String, String> config, ArrayList<AppDMetricObj> metrics, JsonNode widgets){
        try {
            logger.info("Building dashboard...");
            for(AppDMetricObj metricObj : metrics) {
                JsonNode widget = findWidget(widgets, metricObj.getWidgetName());
                AdqlSearchObj adqlSearchObj =  ADQLSearchGenerator.getSearchForMetric(config, metricObj);
                updateDrillDown(config, widget, adqlSearchObj);
                //token replace metric path
                updateMetricPath(config, widget, metricObj);
            }
            //saveTemplate
        }
        catch (Exception ex){
            logger.error("Unable to save search", ex);
        }
    }

    private void updateDrillDown(Map<String, String> config, JsonNode widget, AdqlSearchObj adqlSearchObj){
        if (adqlSearchObj != null) {
            String link = String.format(AdqlSearchObj.searchUrlTemplate, config.get(Constants.CONFIG_CONTROLLER_URL), adqlSearchObj.getId());
            ((ObjectNode)widget).put("drillDownUrl", link);
            ((ObjectNode)widget).put("useMetricBrowserAsDrillDown", false);
        }
        else{
            //use metric browser
            ((ObjectNode)widget).put("useMetricBrowserAsDrillDown", true);
        }
    }

    private void updateMetricPath (Map<String, String> config, JsonNode widget, AppDMetricObj metricObj){
        JsonNode templates = widget.get("dataSeriesTemplates");
        for(JsonNode t : templates){
            JsonNode match = t.get("metricMatchCriteriaTemplate");
            JsonNode metric = match.get("metricExpressionTemplate");
            String metricToken = metric.get("metricPath").asText();
            if (metricToken.equals(metricObj.getMetricToken())) {
                ((ObjectNode)match).put("applicationName", config.get(CONFIG_APP_NAME));
                ((ObjectNode) metric).put("metricPath", metricObj.getPath());
                JsonNode scope = metric.get("scopeEntity");
                ((ObjectNode)scope).put("applicationName", config.get(CONFIG_APP_NAME));
                ((ObjectNode)scope).put("entityName", config.get(CONFIG_APP_TIER_NAME));
            }
        }
    }

    private JsonNode findWidget(JsonNode widgets, String widgetName){
        JsonNode widget = null;
        for(JsonNode n : widgets){
            if (n.get("label") != null && n.get("label").asText().equals(widgetName)){
                widget = n;
                break;
            }
        }
        return widget;
    }
}
