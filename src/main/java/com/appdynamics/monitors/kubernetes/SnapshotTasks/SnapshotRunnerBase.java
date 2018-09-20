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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public abstract class SnapshotRunnerBase implements AMonitorTaskRunnable {
    protected CountDownLatch countDownLatch;
    protected static final Logger logger = LoggerFactory.getLogger(SnapshotRunnerBase.class);
    private HashMap<String, SummaryObj> summaryMap = new HashMap<String, SummaryObj>();
    private TasksExecutionServiceProvider serviceProvider;

    private MonitorConfiguration configuration;
    private String taskName;
    private Map<String, String> entityConfig = null;

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

    public HashMap<String, SummaryObj> getSummaryMap() {
        return summaryMap;
    }

}
