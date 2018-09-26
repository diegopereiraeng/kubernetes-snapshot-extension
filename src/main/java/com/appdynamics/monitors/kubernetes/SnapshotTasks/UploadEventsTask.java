package com.appdynamics.monitors.kubernetes.SnapshotTasks;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.monitors.kubernetes.Models.SummaryObj;
import com.appdynamics.monitors.kubernetes.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class UploadEventsTask implements Runnable{
    private URL publishUrl;
    private String payload;
    private String accountName;
    private String apiKey;
    private String taskName;

    private static final Logger logger = LoggerFactory.getLogger(UploadEventsTask.class);
    public UploadEventsTask(String taskName, URL url, String accountName, String apiKey, String requestBody) {
        this.publishUrl = url;
        this.payload = requestBody;
        this.accountName = accountName;
        this.apiKey = apiKey;
        this.taskName = taskName;
    }

    @Override
    public void run() {
        try {

            if(!payload.equals("[]")){
                logger.info("Task {}. Sending data to AppD events API", this.taskName);
                logger.debug("Upload task: about to push Events API: {}", payload);
                RestClient.doRequest(publishUrl, accountName, apiKey, payload, "POST");
            }
        }
        catch(Exception e){
            logger.error("Event upload task error", e.getMessage());
        }
    }
}
