package com.appdynamics.monitors.kubernetes.SnapshotTasks;

import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.monitors.kubernetes.Constants;
import com.appdynamics.monitors.kubernetes.Models.AppDMetricObj;
import com.appdynamics.monitors.kubernetes.Models.SummaryObj;
import com.appdynamics.monitors.kubernetes.Utilities;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.appdynamics.monitors.kubernetes.Constants.METRIC_SEPARATOR;
import static com.appdynamics.monitors.kubernetes.Utilities.ALL;

public abstract class SnapshotRunnerBase implements AMonitorTaskRunnable {
    protected CountDownLatch countDownLatch;
    protected static final Logger logger = LoggerFactory.getLogger(SnapshotRunnerBase.class);
    private HashMap<String, SummaryObj> summaryMap = new HashMap<String, SummaryObj>();
    private TasksExecutionServiceProvider serviceProvider;

    private MonitorConfiguration configuration;
    private String taskName;
    private Map<String, String> entityConfig = null;
    protected static int K8S_API_TIMEOUT = 240;

    public SnapshotRunnerBase(){

    }

    public SnapshotRunnerBase(TasksExecutionServiceProvider serviceProvider, Map<String, String> config, CountDownLatch countDownLatch){
        configuration = serviceProvider.getMonitorConfiguration();
        this.serviceProvider = serviceProvider;
        this.setEntityConfig(config);
        this.setTaskName(config.get(Constants.CONFIG_ENTITY_TYPE));
        this.countDownLatch = countDownLatch;
    }

    public MonitorConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(MonitorConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void run() {

    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    @Override
    public void onTaskComplete() {

    }

    public Map<String, String> getEntityConfig() {
        return entityConfig;
    }

    public void setEntityConfig(Map<String, String> entityConfig) {
        this.entityConfig = entityConfig;
    }

    public TasksExecutionServiceProvider getServiceProvider() {
        return serviceProvider;
    }

    public ArrayList<SummaryObj> getMetricsBundle(){
        return Utilities.getSummaryDataList(getSummaryMap());
    }

    public void serializeMetrics(){
        serializeMetrics(null);
    }

    public void serializeMetrics(String folder){
        try {
            ArrayList<AppDMetricObj> metrics = new ArrayList<AppDMetricObj>();

            ArrayList<SummaryObj> summaryList = getMetricsBundle();
            for (SummaryObj summaryObj : summaryList) {
                metrics.addAll(summaryObj.getMetricsMetadata());
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.valueToTree(metrics);
            String path = "";
            if (folder == null || folder.isEmpty()){
                path = String.format("%s/%s_STASH.json", Utilities.getRootDirectory(), taskName);
            }
            else{
                path = String.format("%s/%s_STASH.json", folder, taskName);
            }
            ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
            writer.writeValue(new File(path), node);
        }
        catch (Exception ex){
            logger.error(String.format("Unable to save metrics for task {} to disk", taskName), ex);
        }
    }
    protected void setAPIServerTimeout(ApiClient client, long seconds){
        if (client != null){
            client.getHttpClient().setConnectTimeout(seconds, TimeUnit.SECONDS);
            client.getHttpClient().setReadTimeout(seconds, TimeUnit.SECONDS);
            client.getHttpClient().setWriteTimeout(seconds, TimeUnit.SECONDS);
        }
    }

    protected void setCoreAPIServerTimeout(CoreV1Api api, long seconds){
        if (api != null){
            api.getApiClient().getHttpClient().setConnectTimeout(seconds, TimeUnit.SECONDS);
            api.getApiClient().getHttpClient().setReadTimeout(seconds, TimeUnit.SECONDS);
            api.getApiClient().getHttpClient().setWriteTimeout(seconds, TimeUnit.SECONDS);
        }
    }

    protected void setCoreAPIServerTimeout(ExtensionsV1beta1Api api, long seconds){
        if (api != null){
            api.getApiClient().getHttpClient().setConnectTimeout(seconds, TimeUnit.SECONDS);
            api.getApiClient().getHttpClient().setReadTimeout(seconds, TimeUnit.SECONDS);
            api.getApiClient().getHttpClient().setWriteTimeout(seconds, TimeUnit.SECONDS);
        }
    }

    public List<AppDMetricObj> deserializeMetrics(){
        return deserializeMetrics(null);
    }

    public List<AppDMetricObj> deserializeMetrics(String folder){
        List<AppDMetricObj> metricList = new ArrayList<AppDMetricObj>();
        try {
            String path = "";
            if (folder == null || folder.isEmpty()){
                path = String.format("%s/%s_STASH.json", Utilities.getRootDirectory(), taskName);
            }
            else{
                path = String.format("%s/%s_STASH.json", folder, taskName);
            }
            File f = new File(path);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode nodes = mapper.readTree(f);
            for(JsonNode n : nodes) {
                AppDMetricObj metricObj = mapper.treeToValue(n, AppDMetricObj.class);
                metricList.add(metricObj);
            }
        }
        catch (Exception ex){
            logger.error(String.format("Unable to save metrics for task {} to disk", taskName), ex);
        }
        return metricList;
    }

    public  List<Metric> getMetricsFromSummary(HashMap<String, SummaryObj> summaryMap, Map<String, String> config){
        List<Metric> metricList = new ArrayList<Metric>();
        ArrayList<SummaryObj> objList = getSummaryDataList(summaryMap, config);
        for(SummaryObj summaryObj : objList){
            JsonNode obj = summaryObj.getData();
            Iterator<Map.Entry<String, JsonNode>> nodes = obj.fields();
            while (nodes.hasNext()){
                Map.Entry<String, JsonNode> entry = nodes.next();
                String fieldName = entry.getKey();
                if (!fieldName.equals("batch_ts") && !fieldName.equals("nodename") && !fieldName.equals("namespace")) {
                    String path = String.format("%s%s%s", summaryObj.getPath(), METRIC_SEPARATOR, fieldName);
                    String val = entry.getValue().asText();
                    Metric m = new Metric(fieldName, val, path, "OBSERVATION", "CURRENT", "INDIVIDUAL");
                    metricList.add(m);
                }
            }
        }
        return metricList;
    }

    public  ArrayList getSummaryDataList(HashMap<String, SummaryObj> summaryMap, Map<String, String> config){
        ArrayList list = new ArrayList();
        Iterator it = summaryMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            SummaryObj summaryObj = (SummaryObj)pair.getValue();
            list.add(summaryObj);
        }
        if (list.size() == 0) //no data, send defaults
        {
            logger.info("No data received from API. Initializing default summary object");
            SummaryObj defaults = initDefaultSummaryMap(config);
            list.add(defaults);
        }
        return list;
    }

    protected SummaryObj initDefaultSummaryMap(Map<String, String> config){
        Utilities.ensureClusterName(config, "");

        SummaryObj summary = getSummaryMap().get(ALL);
        if (summary == null) {
            summary = initDefaultSummaryObject(config);
            getSummaryMap().put(ALL, summary);
        }
        return summary;
    }

    protected  abstract SummaryObj initDefaultSummaryObject(Map<String, String> config);


    public HashMap<String, SummaryObj> getSummaryMap() {
        return summaryMap;
    }

}
