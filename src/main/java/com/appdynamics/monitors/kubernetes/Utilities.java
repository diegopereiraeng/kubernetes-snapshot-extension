package com.appdynamics.monitors.kubernetes;

import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.monitors.kubernetes.Models.AdqlSearchObj;
import com.appdynamics.monitors.kubernetes.Models.SummaryObj;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.util.Config;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;

import static com.appdynamics.monitors.kubernetes.Constants.*;

public class Utilities {
    private static final Logger logger = LoggerFactory.getLogger(Utilities.class);
    public static final String ALL = "all";
    public static int tierID = 0;
    public static String ClusterName = "";
    public static ArrayList<AdqlSearchObj> savedSearches = new ArrayList<AdqlSearchObj>();
    public static int FIELD_LENGTH_LIMIT = 4000;

    public static URL getUrl(String input){
        URL url = null;
        try {
            url = new URL(input);
        } catch (MalformedURLException e) {
            logger.error("Error forming URL from String {}", input, e);
        }
        return url;
    }


    public static Map<String, String> getEntityConfig(List<Map<String, String>> config, String entityType){
        Map<String, String>  entityConfig = null;
        logger.info("Checking section {}", entityType);
        for(Map<String, String> map : config){
            if (entityType.equals(map.get(CONFIG_ENTITY_TYPE))){
                entityConfig = map;
                break;
            }
        }
        return entityConfig;
    }

    public static boolean shouldCollectMetricsForNode(MonitorConfiguration configuration, String node){
        boolean should = false;
        try {
            List<Map<String, String>> nodes = (List<Map<String, String>>) configuration.getConfigYml().get(CONFIG_NODE_NODES);
            if (nodes != null) {
                for (Map<String, String> map : nodes) {
                    if (map.get("name").equals(node) || map.get("name").equals(ALL)) {
                        should = true;
                        break;
                    }
                }
            }
        }
        catch (Exception ex){
            logger.error("Issues when parsing nodes config", ex);
        }
        return should;
    }

    public static boolean shouldCollectMetricsForNamespace(MonitorConfiguration configuration, String ns){
        boolean should = false;
        try {
            List<Map<String, String>> namespaces = (List<Map<String, String>>) configuration.getConfigYml().get(CONFIG_NODE_NAMESPACES);
            if (namespaces != null) {
                for (Map<String, String> map : namespaces) {
                    if (map.get("name").equals(ns) || map.get("name").equals(ALL)) {
                        should = true;
                        break;
                    }
                }
            }
        }
        catch (Exception ex){
            logger.error("Issues when parsing namespace config", ex);
        }
        return should;
    }

    public static URL ensureSchema(Map<String, String> config, String apiKey, String accountName, String schemaName, String schemaDefinition){
        URL publishUrl = Utilities.getUrl(getEventsAPIUrl(config) + "/events/publish/" + config.get(schemaName));
        URL schemaUrl = Utilities.getUrl(getEventsAPIUrl(config) + "/events/schema/" + config.get(schemaName));
        String requestBody = config.get(schemaDefinition);
//        ObjectNode existingSchema = null;
//        try {
//            existingSchema = (ObjectNode) new ObjectMapper().readTree(requestBody);
//        }
//        catch (IOException ioEX){
//            logger.error("Unable to determine the latest Pod schema", ioEX);
//        }

        JsonNode serverSchema = RestClient.doRequest(schemaUrl, config,accountName, apiKey, "", "GET");
        if(serverSchema == null){

            logger.debug("Schema Url {} does not exists. creating {}", schemaUrl, requestBody);

            RestClient.doRequest(schemaUrl, config,accountName, apiKey, requestBody, "POST");
        }
        else {
            logger.info("Schema exists");
//            if (existingSchema != null) {
//                logger.info("Existing schema is not empty");
//                ArrayNode updated = Utilities.checkSchemaForUpdates(serverSchema, existingSchema);
//                if (updated != null) {
//                    //update schema changes
//                    logger.info("Schema changed, updating", schemaUrl);
//                      logger.debug("New schema fields: {}", updated.toString());

//                    RestClient.doRequest(schemaUrl, accountName, apiKey, updated.toString(), "PATCH");
//                }
//                else {
//                    logger.info("Nothing to update");
//                }
//            }
        }
        return publishUrl;
    }


    public static ObjectNode checkAddObject(ObjectNode objectNode, Object object, String fieldName){
        if(object != null && object.toString() != null){
            String objString = object.toString();
            byte[] bytes = objString.getBytes(Charset.forName("UTF-8"));
            if (bytes.length >= FIELD_LENGTH_LIMIT){
                logger.info("Field {} is greater than the allowed size of 4K. Skipping....", fieldName);
                objectNode.put(fieldName, "");
            }
            else{
                objectNode.put(fieldName, objString);
            }
        }
        return objectNode;
    }

    public static ObjectNode checkAddInt(ObjectNode objectNode, Integer val, String fieldName){
        if (val == null){
            val = 0;
        }
        objectNode.put(fieldName, val);

        return objectNode;
    }

    public static ObjectNode checkAddLong(ObjectNode objectNode, Long val, String fieldName){
        if (val == null){
            val = 0L;
        }
        objectNode.put(fieldName, val);

        return objectNode;
    }

    public static ObjectNode checkAddFloat(ObjectNode objectNode, Float val, String fieldName){
        if (val == null){
            val = new Float(0);
        }
        objectNode.put(fieldName, val);

        return objectNode;
    }


    public static ObjectNode checkAddDecimal(ObjectNode objectNode, BigDecimal val, String fieldName){
        if (val == null){
            val = new BigDecimal(0);
        }
        objectNode.put(fieldName, val);

        return objectNode;
    }

    public static ObjectNode checkAddBoolean(ObjectNode objectNode, Boolean val, String fieldName){
        if (val == null){
            val = false;
        }
        objectNode.put(fieldName, val);

        return objectNode;
    }


    public static ObjectNode incrementField(SummaryObj summaryObj, String fieldName){
        if (summaryObj == null){
            return null;
        }
        ObjectNode obj = summaryObj.getData();
        if(obj != null && obj.has(fieldName)) {
            int val = obj.get(fieldName).asInt() + 1;
            obj.put(fieldName, val);
        }

        return obj;
    }

    public static ObjectNode setField(SummaryObj summaryObj, String fieldName, int value){
        if (summaryObj == null){
            return null;
        }
        ObjectNode obj = summaryObj.getData();
        if(obj != null && obj.has(fieldName)) {
            int val = value;
            obj.put(fieldName, val);
        }

        return obj;
    }

    public static ObjectNode incrementField(SummaryObj summaryObj, String fieldName, int increment){
        if (summaryObj == null){
            return null;
        }
        ObjectNode obj = summaryObj.getData();
        if(obj != null && obj.has(fieldName)) {
            int val = obj.get(fieldName).asInt();
            obj.put(fieldName, val+increment);
        }

        return obj;
    }

    public static ObjectNode incrementField(SummaryObj summaryObj, String fieldName, float increment){
        if (summaryObj == null){
            return null;
        }
        ObjectNode obj = summaryObj.getData();
        if(obj != null && obj.has(fieldName)) {
            int val = obj.get(fieldName).asInt();
            obj.put(fieldName, val+increment);
        }

        return obj;
    }

    public static ObjectNode incrementField(SummaryObj summaryObj,  String fieldName, BigDecimal increment){
        if (summaryObj == null){
            return null;
        }
        ObjectNode obj = summaryObj.getData();
        if(obj != null && obj.has(fieldName)) {
            BigDecimal val = new BigDecimal(obj.get(fieldName).asDouble());
            val = val.add(increment);
            obj.put(fieldName, val);
        }

        return obj;
    }

    public  static ArrayList getSummaryDataList(HashMap<String, SummaryObj> summaryMap){
        ArrayList list = new ArrayList();
        Iterator it = summaryMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            SummaryObj summaryObj = (SummaryObj)pair.getValue();
            list.add(summaryObj);
//            it.remove();
        }
        return list;
    }

    public  static ArrayNode getSummaryData(HashMap<String, SummaryObj> summaryMap){
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode list = mapper.createArrayNode();
        Iterator it = summaryMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            SummaryObj summaryObj = (SummaryObj)pair.getValue();
            list.add(summaryObj.getData());
//            it.remove();
        }
        return list;
    }

    public static String getMetricsPath(Map<String, String> config){
        return String.format(config.get(DEFAULT_METRIC_PREFIX_NAME), tierID);
    }
    
    public static String getRestUITierPath(Map<String, String> config){
        String restuiTierPath = config.get(CONFIG_APP_TIER_NAME);
        if (StringUtils.isNotEmpty(restuiTierPath) == false){
            restuiTierPath = "restui";
        }
        return  restuiTierPath;
    }

    public static String getMetricsPath(Map<String, String> config, String namespace, String node){
        if(!node.equals(ALL)){
            //logger.debug("getMetricsPath: - Node : "+ String.format("%s%s%s%s", Utilities.getMetricsPath(config), METRIC_PATH_NODES, METRIC_SEPARATOR, node));
            return String.format("%s%s%s%s%s", Utilities.getMetricsPath(config), METRIC_SEPARATOR, METRIC_PATH_NODES, METRIC_SEPARATOR, node);  
        }
        else if (!namespace.equals(ALL)){
            //logger.debug("getMetricsPath: - Namespace : "+ String.format("%s%s%s%s", Utilities.getMetricsPath(config), METRIC_PATH_NAMESPACES, METRIC_SEPARATOR, namespace));
            return String.format("%s%s%s%s%s", Utilities.getMetricsPath(config), METRIC_SEPARATOR, METRIC_PATH_NAMESPACES, METRIC_SEPARATOR, namespace);
        }

        return getMetricsPath(config);
    }

    public static String getMetricsPathV2(Map<String, String> config, String name, String specific){
        //logger.debug("getMetricsPathV2 - 3 args : "+ String.format("%s%s%s%s", Utilities.getMetricsPath(config), name, METRIC_SEPARATOR, specific));
        return String.format("%s%s%s%s", Utilities.getMetricsPath(config), name, METRIC_SEPARATOR, specific);
    }

    public static String getMetricsPathV2(Map<String, String> config, String name){
        //logger.debug("getMetricsPathV2: - 2 args : "+String.format("%s%s", Utilities.getMetricsPath(config), name));
        return String.format("%s%s", Utilities.getMetricsPath(config), name);
    }


    public static String ensureClusterName(Map<String, String> config, String clusterName){
        if (clusterName == null || clusterName.isEmpty()){

            if (Utilities.ClusterName != null &&  !Utilities.ClusterName.isEmpty()) {
                clusterName = Utilities.ClusterName;
            }
            else {
                clusterName = Utilities.getClusterApplicationName(config);
            }
        }
        if (Utilities.ClusterName == null | Utilities.ClusterName.isEmpty()){
            Utilities.ClusterName = clusterName; //need this to build queries;
        }
        return clusterName;
    }

    public static String getClusterApplicationName(Map<String, String> config){
        String appName = System.getenv("APPLICATION_NAME");
        if (StringUtils.isNotEmpty(appName) == false){
            appName = config.get(CONFIG_APP_NAME);
        }
        return  appName;
    }

    public static String getClusterTierName(Map<String, String> config){
        String appName = System.getenv("TIER_NAME");
        if (StringUtils.isNotEmpty(appName) == false){
            appName = config.get(CONFIG_APP_TIER_NAME);
        }
        return  appName;
    }

    public static String getProxyHost(Map<String, String> config){
        String proxyHost = System.getenv("APPD_PROXY_HOST");
        if (StringUtils.isNotEmpty(proxyHost) == false){
            proxyHost = config.get("proxyHost");
        }

        return  proxyHost;
    }

    public static String getProxyPort(Map<String, String> config){
        String proxyPort = System.getenv("APPD_PROXY_PORT");
        if (StringUtils.isNotEmpty(proxyPort) == false){
            proxyPort = config.get("proxyPort");
        }
        return  proxyPort;
    }

    public static String getProxyUser(Map<String, String> config){
        String proxyUser = System.getenv("APPD_PROXY_USER");
        if (StringUtils.isNotEmpty(proxyUser) == false){
            proxyUser = config.get("proxyUser");
        }
        return  proxyUser;
    }

    public static String getProxyPass(Map<String, String> config){
        String proxyPass = System.getenv("APPD_PROXY_PASS");
        if (StringUtils.isNotEmpty(proxyPass) == false){
            proxyPass = config.get("proxyPass");
        }
        return  proxyPass;
    }


    public static String getEventsAPIKey(Map<String, String> config){
        String key = System.getenv("EVENT_ACCESS_KEY");
        if (StringUtils.isNotEmpty(key) == false){
            key = config.get(CONFIG_EVENTS_API_KEY);
        }
        return  key;
    }

    public static String getGlobalAccountName(Map<String, String> config){
        String key = System.getenv("GLOBAL_ACCOUNT_NAME");
        if (StringUtils.isNotEmpty(key) == false){
            key = config.get(CONFIG_GLOBAL_ACCOUNT_NAME);
        }
        return  key;
    }

    public static AdqlSearchObj getSavedSearch(String name){
        AdqlSearchObj theObj = null;
        for(AdqlSearchObj s : savedSearches){
            if(s.getName().equals(name)){
                theObj = s;
                break;
            }
        }
        return theObj;
    }

    public static ApiClient initClient(Map<String, String> config) throws Exception{
        ApiClient client;
        String apiMode = System.getenv("K8S_API_MODE");
        if (StringUtils.isNotEmpty(apiMode) == false){
            apiMode = config.get("apiMode");
        }

        if (apiMode.equals("server")) {
            try {
                client = Config.fromConfig(config.get("kubeClientConfig"));
            }
            catch (Exception ex){
                logger.info("K8s API client cannot be initialized form the config file {}. Reason {}. Trying cluster creds", config.get("kubeClientConfig"), ex.getMessage());
                client = Config.fromCluster();
            }
        }
        else if (apiMode.equals("cluster")){
            client = Config.fromCluster();
        }
        else{
            throw new Exception(String.format("apiMode %s not supported. Must be server or cluster", apiMode));
        }
        if (client == null){
            throw new Exception("Kubernetes API client is not initialized. Aborting...");
        }
        return client;
    }

    public static String getRootDirectory(){
        File file = new File(".");
        return String.format("%s/monitors/KubernetesSnapshotExtension", file.getAbsolutePath());
    }

    public static String getExtensionDirectory(){
        File file = new File("");
        return String.format("%s/monitors/KubernetesSnapshotExtension", file.getAbsolutePath());
    }

    public static String getControllerUrl(Map<String, String> config){
        String url = System.getenv("REST_API_URL");
        if (StringUtils.isNotEmpty(url) == false){
            url = config.get(CONFIG_CONTROLLER_URL);
        }
        return  url;
    }

    public static String getEventsAPIUrl(Map<String, String> config){
        String url = System.getenv("EVENTS_API_URL");
        if (StringUtils.isNotEmpty(url) == false){
            url = config.get(CONFIG_EVENTS_URL);
        }
        return  url;
    }

}
