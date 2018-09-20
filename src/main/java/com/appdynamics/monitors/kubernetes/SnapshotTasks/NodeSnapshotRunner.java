package com.appdynamics.monitors.kubernetes.SnapshotTasks;

import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.util.AssertUtils;
import com.appdynamics.monitors.kubernetes.Metrics.UploadMetricsTask;
import com.appdynamics.monitors.kubernetes.Models.AppDMetricObj;
import com.appdynamics.monitors.kubernetes.Models.SummaryObj;
import com.appdynamics.monitors.kubernetes.RestClient;
import com.appdynamics.monitors.kubernetes.Utilities;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static com.appdynamics.monitors.kubernetes.Constants.*;
import static com.appdynamics.monitors.kubernetes.Utilities.*;

public class NodeSnapshotRunner extends SnapshotRunnerBase {
    public NodeSnapshotRunner(){

    }

    public NodeSnapshotRunner(TasksExecutionServiceProvider serviceProvider, Map<String, String> config, CountDownLatch countDownLatch){
        super(serviceProvider, config, countDownLatch);
//        initMetrics(config);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        AssertUtils.assertNotNull(getConfiguration(), "The job configuration cannot be empty");
        generateNodeSnapshot();
    }

    private void generateNodeSnapshot(){
        logger.info("Proceeding to Node update...");
        Map<String, String> config = (Map<String, String>) getConfiguration().getConfigYml();
        if (config != null) {
            String apiKey = Utilities.getEventsAPIKey(config);
            String accountName = Utilities.getGlobalAccountName(config);
            URL publishUrl = Utilities.ensureSchema(config, apiKey, accountName, CONFIG_SCHEMA_NAME_NODE, CONFIG_SCHEMA_DEF_NODE);

            try {
                ApiClient client = Utilities.initClient(config);

                Configuration.setDefaultApiClient(client);
                CoreV1Api api = new CoreV1Api();

                V1NodeList nodeList;
                nodeList = api.listNode(null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
                String payload = createNodePayload(nodeList, config).toString();

                logger.debug("About to push Nodes to Events API: {}", payload);

                if(!payload.equals("[]")){
                    RestClient.doRequest(publishUrl, accountName, apiKey, payload, "POST");
                }

                //build and update metrics
//                serializeMetrics();
                List<Metric> metricList = Utilities.getMetricsFromSummary(getSummaryMap(), config);
                logger.info("About to send {} node metrics", metricList.size());
                UploadMetricsTask metricsTask = new UploadMetricsTask(getConfiguration(), getServiceProvider().getMetricWriteHelper(), metricList, countDownLatch);
                getConfiguration().getExecutorService().execute("UploadNodeMetricsTask", metricsTask);

                //check searches
            } catch (IOException e) {
                logger.error("Failed to push Node data", e);
                countDownLatch.countDown();
                e.printStackTrace();
            } catch (Exception e) {
                countDownLatch.countDown();
                logger.error("Failed to push Node data", e);
                e.printStackTrace();
            }
        }
    }

     ArrayNode createNodePayload(V1NodeList nodeList, Map<String, String> config) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();

        for(V1Node nodeObj : nodeList.getItems()) {
            ObjectNode nodeObject = mapper.createObjectNode();
            String nodeName = nodeObj.getMetadata().getName();
            nodeObject = checkAddObject(nodeObject, nodeName, "nodeName");
            String clusterName = Utilities.ensureClusterName(config, nodeObj.getMetadata().getClusterName());

            SummaryObj summary = getSummaryMap().get(ALL);
            if (summary == null) {
                summary = initNodeSummaryObject(config, ALL);
                getSummaryMap().put(ALL, summary);
            }

            SummaryObj summaryNode = getSummaryMap().get(nodeName);
            if (summaryNode == null) {
                summaryNode = initNodeSummaryObject(config, nodeName);
                getSummaryMap().put(nodeName, summaryNode);
            }


            nodeObject = checkAddObject(nodeObject, clusterName, "clusterName");
            nodeObject = checkAddObject(nodeObject, nodeObj.getSpec().getPodCIDR(), "podCIDR");
            String taints = "";

            if (nodeObj.getSpec().getTaints() != null) {
                for (V1Taint t : nodeObj.getSpec().getTaints()) {
                    taints += String.format("%s:", t.toString());
                }
            }
            nodeObject = checkAddObject(nodeObject, taints, "taints");
            Utilities.incrementField(summary, "TaintsTotal");

            nodeObject = checkAddObject(nodeObject, nodeObj.getStatus().getPhase(), "phase");
            String addresses = "";
            for (V1NodeAddress add : nodeObj.getStatus().getAddresses()) {
                addresses += add.getAddress();
            }
            nodeObject = checkAddObject(nodeObject, addresses, "addresses");

            //labels
            boolean isMaster = false;
            int masters = 0;
            int workers = 0;
            if (nodeObj.getMetadata().getLabels() != null) {
                String labels = "";
                Iterator it = nodeObj.getMetadata().getLabels().entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry) it.next();
                    if (!isMaster && pair.getKey().equals("node-role.kubernetes.io/master")) {
                        isMaster = true;
                    }
                    labels += String.format("%s:%s;", pair.getKey(), pair.getValue());
                    it.remove();
                }
                nodeObject = checkAddObject(nodeObject, labels, "labels");
            }
            if (isMaster) {
                nodeObject = checkAddObject(nodeObject, "master", "role");
                masters++;
            } else {
                nodeObject = checkAddObject(nodeObject, "worker", "role");
                workers++;
            }

            Utilities.incrementField(summary, "Masters", masters);
            Utilities.incrementField(summary, "Workers", workers);

            if (nodeObj.getStatus().getCapacity() != null) {
                Set<Map.Entry<String, Quantity>> set = nodeObj.getStatus().getCapacity().entrySet();
                for (Map.Entry<String, Quantity> s : set) {
                    if (s.getKey().equals("memory")) {
                        float val = s.getValue().getNumber().divide(new BigDecimal(1000000)).floatValue(); //MB
                        nodeObject = checkAddFloat(nodeObject, val, "memCapacity");
                        Utilities.incrementField(summaryNode, "CapacityMemory", val);
                    }
                    if (s.getKey().equals("cpu")) {
                        nodeObject = checkAddFloat(nodeObject, s.getValue().getNumber().floatValue(), "cpuCapacity");
                        Utilities.incrementField(summaryNode, "CapacityCpu", s.getValue().getNumber().floatValue());
                    }
                    if (s.getKey().equals("pods")) {
                        nodeObject = checkAddInt(nodeObject, s.getValue().getNumber().intValueExact(), "podCapacity");
                        Utilities.incrementField(summaryNode, "CapacityPods", s.getValue().getNumber().intValueExact());
                    }
                }
            }

            if (nodeObj.getStatus().getAllocatable() != null) {
                Set<Map.Entry<String, Quantity>> setAll = nodeObj.getStatus().getAllocatable().entrySet();
                for (Map.Entry<String, Quantity> s : setAll) {
                    if (s.getKey().equals("memory")) {
                        float val = s.getValue().getNumber().divide(new BigDecimal(1000000)).floatValue(); //MB
                        nodeObject = checkAddFloat(nodeObject, val, "memAllocations");
                        Utilities.incrementField(summaryNode, "AllocationsMemory", val);
                    }
                    if (s.getKey().equals("cpu")) {
                        nodeObject = checkAddFloat(nodeObject, s.getValue().getNumber().floatValue(), "cpuAllocations");
                        Utilities.incrementField(summaryNode, "AllocationsCpu", s.getValue().getNumber().floatValue());
                    }
                    if (s.getKey().equals("pods")) {
                        nodeObject = checkAddInt(nodeObject, s.getValue().getNumber().intValueExact(), "podAllocations");
                    }
                }
            }

            nodeObject = checkAddInt(nodeObject, nodeObj.getStatus().getDaemonEndpoints().getKubeletEndpoint().getPort(), "kubeletPort");

            nodeObject = checkAddObject(nodeObject, nodeObj.getStatus().getNodeInfo().getArchitecture(), "osArch");
            nodeObject = checkAddObject(nodeObject, nodeObj.getStatus().getNodeInfo().getKubeletVersion(), "kubeletVersion");
            nodeObject = checkAddObject(nodeObject, nodeObj.getStatus().getNodeInfo().getContainerRuntimeVersion(), "runtimeVersion");
            nodeObject = checkAddObject(nodeObject, nodeObj.getStatus().getNodeInfo().getMachineID(), "machineID");
            nodeObject = checkAddObject(nodeObject, nodeObj.getStatus().getNodeInfo().getOperatingSystem(), "osName");

            if (nodeObj.getStatus().getVolumesAttached() != null){
                String attachedValumes = "";
                for (V1AttachedVolume v : nodeObj.getStatus().getVolumesAttached()) {
                    attachedValumes += String.format("$s:$s;", v.getName(), v.getDevicePath());
                }
                nodeObject = checkAddObject(nodeObject, attachedValumes, "attachedVolumes");
            }

            if (nodeObj.getStatus().getVolumesInUse() != null) {
                String volumesInUse = "";
                for (String v : nodeObj.getStatus().getVolumesInUse()) {
                    volumesInUse += String.format("$s:", v);
                }
                nodeObject = checkAddObject(nodeObject, volumesInUse, "volumesInUse");
            }

            //conditions
            if (nodeObj.getStatus().getConditions() != null) {
                for (V1NodeCondition condition : nodeObj.getStatus().getConditions()) {
                    if (condition.getType().equals("Ready")) {
                        String status = condition.getStatus();
                        nodeObject = checkAddObject(nodeObject, status, "ready");
                        if (status.toLowerCase().equals("true")) {
                            Utilities.incrementField(summary, "ReadyNodes");
                        }
                    }
                    if (condition.getType().equals("OutOfDisk")) {
                        String status = condition.getStatus();
                        nodeObject = checkAddObject(nodeObject, status, "outOfDisk");
                        if (status.toLowerCase().equals("true")) {
                            Utilities.incrementField(summary, "OutOfDiskNodes");
                        }
                    }

                    if (condition.getType().equals("MemoryPressure")) {
                        String status = condition.getStatus();
                        nodeObject = checkAddObject(nodeObject, status, "memoryPressure");
                        if (status.toLowerCase().equals("true")) {
                            Utilities.incrementField(summary, "MemoryPressureNodes");
                        }
                    }

                    if (condition.getType().equals("DiskPressure")) {
                        String status = condition.getStatus();
                        nodeObject = checkAddObject(nodeObject, status, "diskPressure");
                        if (status.toLowerCase().equals("true")) {
                            Utilities.incrementField(summary, "DiskPressureNodes");
                        }
                    }
                }
            }

            arrayNode.add(nodeObject);
        }

        return arrayNode;
    }

    public  static SummaryObj initNodeSummaryObject(Map<String, String> config, String node){
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode summary = mapper.createObjectNode();
        summary.put("nodename", node);

        if (node.equals(ALL)) {
            summary.put("ReadyNodes", 0);
            summary.put("OutOfDiskNodes", 0);
            summary.put("MemoryPressureNodes", 0);
            summary.put("DiskPressureNodes", 0);
            summary.put("TaintsTotal", 0);
            summary.put("Masters", 0);
            summary.put("Workers", 0);
        }
        else{
            summary.put("CapacityMemory", 0);
            summary.put("CapacityCpu", 0);
            summary.put("CapacityPods", 0);
            summary.put("AllocationsMemory", 0);
            summary.put("AllocationsCpu", 0);
        }

        ArrayList<AppDMetricObj> metricsList = initMetrics(config, node);

        String path = Utilities.getMetricsPath(config, ALL, node);

        return new SummaryObj(summary, metricsList, path);
    }

    public static ArrayList<AppDMetricObj> initMetrics(Map<String, String> config, String nodeName){
        if (Utilities.ClusterName == null || Utilities.ClusterName.isEmpty()){
            return new ArrayList<AppDMetricObj>();
        }
        String clusterName = Utilities.ClusterName;
        String parentSchema = config.get(CONFIG_SCHEMA_NAME_NODE);
        String rootPath = String.format("Application Infrastructure Performance|%s|Custom Metrics|Cluster Stats|", Utilities.getClusterTierName(config));
        ArrayList<AppDMetricObj> metricsList = new ArrayList<AppDMetricObj>();
        if (nodeName.equals(ALL)) {
            //global
            metricsList.add(new AppDMetricObj("ReadyNodes", parentSchema, CONFIG_SCHEMA_DEF_NODE,
                    String.format("select * from %s where ready = \"True\" and clusterName = \"%s\"", parentSchema, clusterName), rootPath, ALL, nodeName));
            metricsList.add(new AppDMetricObj("OutOfDiskNodes", parentSchema, CONFIG_SCHEMA_DEF_NODE,
                    String.format("select * from %s where outOfDisk = \"True\" and clusterName = \"%s\"", parentSchema, clusterName), rootPath, ALL, nodeName));
            metricsList.add(new AppDMetricObj("MemoryPressureNodes", parentSchema, CONFIG_SCHEMA_DEF_NODE,
                    String.format("select * from %s where memoryPressure = \"True\" and clusterName = \"%s\"", parentSchema, clusterName), rootPath, ALL, nodeName));
            metricsList.add(new AppDMetricObj("DiskPressureNodes", parentSchema, CONFIG_SCHEMA_DEF_NODE,
                    String.format("select * from %s where diskPressure = \"True\" and clusterName = \"%s\"", parentSchema, clusterName), rootPath, ALL, nodeName));
            metricsList.add(new AppDMetricObj("TaintsTotal", parentSchema, CONFIG_SCHEMA_DEF_NODE,
                    String.format("select * from %s where taints IS NOT NULL and clusterName = \"%s\"", parentSchema, clusterName), rootPath, ALL, nodeName));
            metricsList.add(new AppDMetricObj("CapacityMemory", parentSchema, CONFIG_SCHEMA_DEF_NODE,
                    String.format("select * from %s where memCapacity > 0 and clusterName = \"%s\"", parentSchema, clusterName), rootPath, ALL, nodeName));
            metricsList.add(new AppDMetricObj("CapacityCpu", parentSchema, CONFIG_SCHEMA_DEF_NODE,
                    String.format("select * from %s where cpuCapacity > 0 and clusterName = \"%s\"", parentSchema, clusterName), rootPath, ALL, nodeName));
            metricsList.add(new AppDMetricObj("AllocationsMemory", parentSchema, CONFIG_SCHEMA_DEF_NODE,
                    String.format("select * from %s where memAllocations > 0 and clusterName = \"%s\"", parentSchema, clusterName), rootPath, ALL, nodeName));
            metricsList.add(new AppDMetricObj("AllocationsCpu", parentSchema, CONFIG_SCHEMA_DEF_NODE,
                    String.format("select * from %s where cpuAllocations > 0 and clusterName = \"%s\"", parentSchema, clusterName), rootPath, ALL, nodeName));
        }
//        else {
//            //node level
//            String nodePath = String.format("%s|%s|", rootPath, METRIC_PATH_NODES, nodeName);
//            metricsList.add(new AppDMetricObj("AllocationsCpu", parentSchema, CONFIG_SCHEMA_DEF_NODE, null, nodePath, ALL, nodeName));
//            metricsList.add(new AppDMetricObj("AllocationsMemory", parentSchema, CONFIG_SCHEMA_DEF_NODE, null, nodePath, ALL, nodeName));
//            metricsList.add(new AppDMetricObj("CapacityCpu", parentSchema, CONFIG_SCHEMA_DEF_NODE, null, nodePath, ALL, nodeName));
//            metricsList.add(new AppDMetricObj("CapacityMemory", parentSchema, CONFIG_SCHEMA_DEF_NODE, null, nodePath, ALL, nodeName));
//            metricsList.add(new AppDMetricObj("CapacityPods", parentSchema, CONFIG_SCHEMA_DEF_NODE, null, nodePath, ALL, nodeName));
//        }
        return metricsList;
    }
}
