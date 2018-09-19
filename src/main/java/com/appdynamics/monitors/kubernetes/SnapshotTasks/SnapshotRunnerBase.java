package com.appdynamics.monitors.kubernetes.SnapshotTasks;

import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.monitors.kubernetes.Constants;
import com.appdynamics.monitors.kubernetes.Models.SummaryObj;
import com.appdynamics.monitors.kubernetes.Utilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public abstract class SnapshotRunnerBase implements AMonitorTaskRunnable {
    protected CountDownLatch countDownLatch;
    protected static final Logger logger = LoggerFactory.getLogger(SnapshotRunnerBase.class);
    protected HashMap<String, SummaryObj> summaryMap = new HashMap<String, SummaryObj>();
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
        return Utilities.getSummaryDataList(summaryMap);
    }

}
