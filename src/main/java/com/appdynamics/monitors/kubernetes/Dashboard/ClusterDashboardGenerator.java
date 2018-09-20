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
        //cache searches
        ADQLSearchGenerator.loadAllSearches(config);
        //check if Dashboard exists
        String dashName = String.format("%s-%s-%s", Utilities.getClusterApplicationName(config), config.get(CONFIG_APP_TIER_NAME), config.get(CONFIG_DASH_NAME_SUFFIX));
        if (!dashboardExists(dashName, config)) {
            //if not, read template
            File file = new File(".");
            String currentDirectory = String.format("%s/monitors/KubernetesSnapshotExtension", file.getAbsolutePath());
            String path = String.format("%s/%s", currentDirectory, config.get(CONFIG_DASH_TEMPLATE_PATH));
            logger.info("Reading the dashboard template from {}", path);
            JsonNode template = readTemplate(path);
            JsonNode widgets = template.get("widgetTemplates");
            //update name
            ((ObjectNode)template).put("name", dashName);
            //create Dashboard
            logger.info("Creating the dashboard...");
            buildDashboard(metrics,  widgets);
            writeTemplate(config, template,  currentDirectory);
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

    private void writeTemplate(Map<String, String> config, JsonNode template, String currentDirectory){
        try{
            String originalPath = config.get(CONFIG_DASH_TEMPLATE_PATH);
            String ext = ".json";
            if (originalPath.contains(ext)) {
                originalPath = originalPath.substring(0, originalPath.length() - ext.length());
            }

            String path = String.format("%s/%s_UPDATED.json", currentDirectory, originalPath);
            ObjectMapper mapper = new ObjectMapper();
            ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
            writer.writeValue(new File(path), template);
            RestClient.createDashboard(config, path);
        }
        catch (Exception ex){
            logger.error("Unable to save the updated dashboard template", ex);
        }
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
            String link = String.format(AdqlSearchObj.searchUrlTemplate, config.get(Constants.CONFIG_CONTROLLER_URL), adqlSearchObj.getId());
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
                ((ObjectNode) scope).put("entityName", config.get(CONFIG_APP_TIER_NAME));
            }
    }

    private JsonNode findMetricNode(JsonNode metricTemplate, String metricPath){
        JsonNode metricNode = null;
        if (metricTemplate == null){
            return metricNode;
        }
        if (metricTemplate.get("metricPath") == null){
            //expression
            metricNode = findExpression(metricTemplate, metricPath);
        }
        else if (metricTemplate.get("metricPath").asText().equals(metricPath)){
            metricNode = metricTemplate;
        }
        return metricNode;
    }

    private JsonNode findExpression(JsonNode parentExpression, String metricPath){
        JsonNode theNode = null;
        for(int i = 0; i < 2; i++){
            String expNodeName = String.format("expression%d", i+1);
            JsonNode expNode = parentExpression.get(expNodeName);
            if (expNode != null){
                if (expNode.get("metricPath") != null && expNode.get("metricPath").asText().equals(metricPath)){
                    theNode = expNode;
                    break;
                }
                else{
                    theNode = findExpression(expNode, metricPath);
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
                        JsonNode metricTemplate = match.get("metricExpressionTemplate");
                        JsonNode metric = findMetricNode(metricTemplate, path);
                        if (metric != null) {
                            boolean isExpression = metricTemplate.get("metricPath") == null;
                            ((ObjectNode) match).put("applicationName", config.get(CONFIG_APP_NAME));
                            //token replace metric path
                            updateMetricPath(config, metric, metricObj);
                            if (!isExpression){
                                AdqlSearchObj adqlSearchObj =  ADQLSearchGenerator.getSearchForMetric(config, metricObj);
                                updateDrillDown(config, widget, adqlSearchObj);
                            }
                        }
                    }
                }
            }
        }
    }
}
