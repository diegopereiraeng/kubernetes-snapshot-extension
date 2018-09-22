package com.appdynamics.monitors.kubernetes.Dashboard;

import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.monitors.kubernetes.Constants;
import com.appdynamics.monitors.kubernetes.Models.AdqlSearchObj;
import com.appdynamics.monitors.kubernetes.Models.AppDMetricObj;
import com.appdynamics.monitors.kubernetes.RestClient;
import com.appdynamics.monitors.kubernetes.Utilities;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_SCHEMA_NAME_EVENT;


public class ADQLSearchGenerator{
    protected static final Logger logger = LoggerFactory.getLogger(ADQLSearchGenerator.class);




    public static void loadAllSearches(Map<String, String> config){
        if (Utilities.savedSearches.size() > 0){
            return;
        }
        try {
            String path = "restui/analyticsSavedSearches/getAllAnalyticsSavedSearches";
            JsonNode searchObj = RestClient.callControllerAPI(path, config, "", "GET");
            if (searchObj != null) {
                for (JsonNode searchNode : searchObj) {
                    AdqlSearchObj obj = new AdqlSearchObj();
                    obj.setId(searchNode.get("id").asInt());
                    obj.setName(searchNode.get("searchName").asText());
                    Utilities.savedSearches.add(obj);
                }
            }
            logger.info("Loaded {} saved searches", Utilities.savedSearches.size());
        }
        catch (Exception ex){
            logger.error("Unable to build the cache of saved searched", ex);
        }
    }

    public static AdqlSearchObj getSearchForMetric(Map<String, String> config, AppDMetricObj metricObj){
        try {
            String query = metricObj.getQuery();
            if (query == null || query.isEmpty()){
                return null;
            }
            logger.debug("Processing search for metric {}", metricObj.getName());
            String clusterName = Utilities.getClusterApplicationName(config);
            String levelName = metricObj.getLevelName();
            if (levelName != null && !levelName.isEmpty()){
                levelName += ".";
            }
            String searchName = String.format("%s.%s %s", clusterName, levelName, metricObj.getName());
            AdqlSearchObj adqlSearchObj = Utilities.getSavedSearch(searchName);

            if (adqlSearchObj == null) {
                String path = "restui/analyticsSavedSearches/createAnalyticsSavedSearch";

                ArrayList<String> columns = getColumns(config, metricObj.getParentSchemaDefinition());
                ObjectNode requestBody = buildSearchObj(searchName, query, columns);
                JsonNode searchObj = RestClient.callControllerAPI(path, config, requestBody.toString(), "POST");
                if (searchObj != null && searchObj.get("id") != null) {
                    int searchID = searchObj.get("id").asInt();
                    adqlSearchObj = new AdqlSearchObj();
                    adqlSearchObj.setId(searchID);
                    adqlSearchObj.setName(searchObj.get("searchName").asText());
                    Utilities.savedSearches.add(adqlSearchObj);
                }
            }
            return adqlSearchObj;
        }
        catch (Exception ex){
            logger.error("Unable to save search", ex);
            return null;
        }
    }

    private static ArrayList<String> getColumns(Map<String, String> config, String schemaDefinitionName){
        ArrayList<String> columns = new ArrayList<String>();
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode schemaNode = objectMapper.readTree(config.get(schemaDefinitionName));

            Iterator<Map.Entry<String, JsonNode>> nodes = schemaNode.get("schema").fields();
            while (nodes.hasNext()) {
                Map.Entry<String, JsonNode> entry = nodes.next();
                String fieldName = entry.getKey();
                columns.add(fieldName);
            }
        }
        catch (Exception ex){
            logger.error("Unable to parse list of columns for the search", ex);
        }
        return columns;
    }

    public static ObjectNode buildSearchObj(String searchName, String query, ArrayList<String> fields){
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode obj = mapper.createObjectNode();
        ArrayNode queries = obj.putArray("adqlQueries");
        queries.add(query);
        obj.put("name", UUID.randomUUID().toString());
        obj.put("searchMode", "ADVANCED");
        obj.put("searchName", searchName);
        obj.put("searchType", "SINGLE");
        ArrayNode columns = obj.putArray("selectedFields");
        for(String field : fields){
            columns.add(field);
        }
        obj.put("viewMode", "DATA");
        obj.put("visualization", "TABLE");
        obj.putArray("widgets");
        return obj;
    }

}
