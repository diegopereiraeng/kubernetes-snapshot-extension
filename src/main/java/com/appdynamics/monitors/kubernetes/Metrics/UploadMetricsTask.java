package com.appdynamics.monitors.kubernetes.Metrics;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.monitors.kubernetes.Models.SummaryObj;
import com.appdynamics.monitors.kubernetes.Utilities;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class UploadMetricsTask implements Runnable{
    private List<Metric> finalMetricList;
    HashMap<String, SummaryObj> summaryMap;
    private MonitorConfiguration configuration;
    private MetricWriteHelper metricWriteHelper;
    private CountDownLatch countDownLatch;
    private static final Logger logger = LoggerFactory.getLogger(UploadMetricsTask.class);
    public UploadMetricsTask(MonitorConfiguration configuration, MetricWriteHelper metricWriteHelper, List<Metric> metricList, CountDownLatch countDownLatch) {
        this.configuration = configuration;
        this.metricWriteHelper = metricWriteHelper;
        this.finalMetricList = metricList;
        this.countDownLatch = countDownLatch;
    }

    @Override
    public void run() {
        try {
            if (finalMetricList != null) {
                logger.info("Executing Metrics update");

                metricWriteHelper.transformAndPrintMetrics(finalMetricList);
            }
            else{
                logger.info("No metrics");
            }

            logger.info("Exiting metrics task");
            countDownLatch.countDown();

        }
        catch(Exception e){
            countDownLatch.countDown();
            logger.error(e.getMessage());
        }
    }
}
