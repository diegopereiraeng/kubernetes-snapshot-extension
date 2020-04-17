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
import io.sundr.shaded.org.apache.velocity.anakia.NodeList;

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
                V1NodeList nodeList;

                try {
                    ApiClient client = Utilities.initClient(config);
                    this.setAPIServerTimeout(client, K8S_API_TIMEOUT);
                    Configuration.setDefaultApiClient(client);
                    CoreV1Api api = new CoreV1Api();
                    this.setCoreAPIServerTimeout(api, K8S_API_TIMEOUT);
                    nodeList = api.listNode(null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null);
                }
                catch (Exception ex){
                    throw new Exception("Unable to connect to Kubernetes API server because it may be unavailable or the cluster credentials are invalid", ex);
                }
                logger.debug("Analyzing Nodes - Number of nodes: "+ nodeList.getItems().size());
                createNodePayload(nodeList, config, publishUrl, accountName, apiKey);


                /* Config to get Total metrics collected */
                /* SummaryObj summaryMetrics = getSummaryMap().get(ALL);
                if (summaryMetrics == null) {
                    summaryMetrics =  initNodeSummaryObject(config, ALL);
                    getSummaryMap().put("NodeMetricsCollected", summaryMetrics);
                } */
                /* Config to get Total metrics collected */
                SummaryObj summaryScript = getSummaryMap().get("NodeScript");
                if (summaryScript == null) {
                    summaryScript = initScriptSummaryObject(config, "Node");
                    getSummaryMap().put("NodeScript", summaryScript);
                }

                Integer metrics_count = getMetricsFromSummary(getSummaryMap(), config).size();
                //incrementField(summaryMetrics, "NodeMetricsCollected", metrics_count);
                incrementField(summaryScript, "NodeMetricsCollected", metrics_count);

                /* End config Summary Metrics */


                //build and update metrics
                List<Metric> metricList = getMetricsFromSummary(getSummaryMap(), config);
                logger.info("About to send {} node metrics", metricList.size());
                UploadMetricsTask metricsTask = new UploadMetricsTask(getConfiguration(), getServiceProvider().getMetricWriteHelper(), metricList, countDownLatch);
                getConfiguration().getExecutorService().execute("UploadNodeMetricsTask", metricsTask);

                //check searches
            } catch (IOException e) {
                logger.error("Failed to push Node data", e);
                countDownLatch.countDown();
            } catch (Exception e) {
                countDownLatch.countDown();
                logger.error("Failed to push Node data", e);
            }
        }
    }

     ArrayNode createNodePayload(V1NodeList nodeList, Map<String, String> config, URL publishUrl, String accountName, String apiKey) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();

        long batchSize = Long.parseLong(config.get(CONFIG_RECS_BATCH_SIZE));

        SummaryObj summaryWorker = getSummaryMap().get("Workers");
        if (summaryWorker == null) {
            summaryWorker = initNodeSummaryObject(config, "Workers");
            getSummaryMap().put("Workers", summaryWorker);
        }

        SummaryObj summaryMaster = getSummaryMap().get("Masters");
        if (summaryMaster == null) {
            summaryMaster = initNodeSummaryObject(config, "Masters");
            getSummaryMap().put("Masters", summaryMaster);
        }
        logger.info("NodeList size: "+nodeList.getItems().size());

        ArrayList<V1Node> nodeListCustom = new ArrayList<V1Node>();
        
        //nodeListCustom = nodeList;
        Integer nodeCount_2 = 1;
        //V1Node baseNode = new V1Node();
        //baseNode = nodeList.getItems().get(0);
        for(int i = 1; i < 5; i++ ){
            V1Node baseNode = new V1Node();
            V1Node newNode = new V1Node(); 
            baseNode = nodeList.getItems().get(0);
            newNode.setApiVersion(baseNode.getApiVersion());
            newNode.setKind(baseNode.getKind());
            newNode.setMetadata(baseNode.getMetadata());
            newNode.setSpec(baseNode.getSpec());
            newNode.setStatus(baseNode.getStatus());
            //String nodeName = newNode.getMetadata().getName();
            String nodeName = "nodeTest";
            nodeName = nodeName+(String) nodeCount_2.toString();
            //logger.info("nodename test:"+nodeName);
            newNode.getMetadata().setName(nodeName);
            newNode.getMetadata().setGenerateName(nodeName);
            newNode.getMetadata().setUid(nodeName);
            newNode.getMetadata().setInitializers(baseNode.getMetadata().getInitializers());
            
            nodeListCustom.add(newNode);
            logger.info("nodename test:"+nodeName);
            nodeCount_2++;
            
        }
        /* for(V1Node nodeObj : nodeList.getItems()) {          
            V1Node newNode = new V1Node(); 
            newNode.setApiVersion(nodeObj.getApiVersion());
            newNode.setKind(nodeObj.getKind());
            newNode.setMetadata(nodeObj.getMetadata());
            newNode.setSpec(nodeObj.getSpec());
            newNode.setStatus(nodeObj.getStatus());            
            nodeListCustom = nodeListCustom.addItemsItem(newNode);
            //nodeList = nodeList.addItemsItem(newNode);
        } */
        logger.info("NodeListCustom size after: "+nodeListCustom.getItems().size());
        logger.info("NodeList size after: "+nodeList.getItems().size());


        for(V1Node nodeObj : nodeListCustom.getItems()) {
            ObjectNode nodeObject = mapper.createObjectNode();
            String nodeName = nodeObj.getMetadata().getName();
            logger.info("nodename orig:"+nodeName);
            nodeObject = checkAddObject(nodeObject, nodeName, "nodeName");
            String clusterName = Utilities.ensureClusterName(config, nodeObj.getMetadata().getClusterName());

            SummaryObj summary = getSummaryMap().get(ALL);
            if (summary == null) {
                summary = initNodeSummaryObject(config, ALL);
                getSummaryMap().put(ALL, summary);
            }

            

            SummaryObj summaryNode = getSummaryMap().get(nodeName);
            if(Utilities.shouldCollectMetricsForNode(getConfiguration(), nodeName)) {
                if (summaryNode == null) {
                    summaryNode = initNodeSummaryObject(config, nodeName);
                    getSummaryMap().put(nodeName, summaryNode);
                }
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
                nodeObject = checkAddObject(nodeObject, "Masters", "role");
                masters++;
            } else {
                nodeObject = checkAddObject(nodeObject, "Workers", "role");
                workers++;
            }


            Utilities.incrementField(summary, "Masters", masters);
            Utilities.incrementField(summary, "Workers", workers);
            Utilities.incrementField(summary, "Nodes", 1);

            if (nodeObj.getStatus().getCapacity() != null) {
                Set<Map.Entry<String, Quantity>> set = nodeObj.getStatus().getCapacity().entrySet();
                for (Map.Entry<String, Quantity> s : set) {
                    if (s.getKey().equals("memory")) {
                        float val = s.getValue().getNumber().divide(new BigDecimal(1000000)).floatValue(); //MB
                        nodeObject = checkAddFloat(nodeObject, val, "memCapacity");
                        Utilities.incrementField(summaryNode, "CapacityMemory", val);
                        Utilities.incrementField(summary, "CapacityMemory", val);
                        if (isMaster) {
                            Utilities.incrementField(summaryMaster, "CapacityMemory", val);
                        } else {
                            Utilities.incrementField(summaryWorker, "CapacityMemory", val);
                        }
                    }
                    if (s.getKey().equals("cpu")) {
                        float val = s.getValue().getNumber().floatValue();
                        nodeObject = checkAddFloat(nodeObject, val, "cpuCapacity");
                        Utilities.incrementField(summaryNode, "CapacityCpu", val);
                        Utilities.incrementField(summary, "CapacityCpu", val);
                        if (isMaster) {
                            Utilities.incrementField(summaryMaster, "CapacityCpu", val);
                        } else {
                            Utilities.incrementField(summaryWorker, "CapacityCpu", val);
                        }
                    }
                    if (s.getKey().equals("pods")) {
                        int val = s.getValue().getNumber().intValueExact();
                        nodeObject = checkAddInt(nodeObject, val, "podCapacity");
                        Utilities.incrementField(summaryNode, "CapacityPods", val);
                        Utilities.incrementField(summary, "CapacityPods", val);
                        if (isMaster) {
                            Utilities.incrementField(summaryMaster, "CapacityPods", val);
                        } else {
                            Utilities.incrementField(summaryWorker, "CapacityPods", val);
                        }
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
                        Utilities.incrementField(summary, "AllocationsMemory", val);
                        if (isMaster) {
                            Utilities.incrementField(summaryMaster, "AllocationsMemory", val);
                        } else {
                            Utilities.incrementField(summaryWorker, "AllocationsMemory", val);
                        }
                    }
                    if (s.getKey().equals("cpu")) {
                        float val = s.getValue().getNumber().floatValue();
                        nodeObject = checkAddFloat(nodeObject, val, "cpuAllocations");
                        Utilities.incrementField(summaryNode, "AllocationsCpu", val);
                        Utilities.incrementField(summary, "AllocationsMemory", val);
                        if (isMaster) {
                            Utilities.incrementField(summaryMaster, "AllocationsMemory", val);
                        } else {
                            Utilities.incrementField(summaryWorker, "AllocationsMemory", val);
                        }
                    }
                    if (s.getKey().equals("pods")) {
                        int val = s.getValue().getNumber().intValueExact();
                        nodeObject = checkAddInt(nodeObject, val , "podAllocations");
                        Utilities.incrementField(summary, "AllocationsPods", val);
                        if (isMaster) {
                            Utilities.incrementField(summaryMaster, "AllocationsPods", val);
                        } else {
                            Utilities.incrementField(summaryWorker, "AllocationsPods", val);
                        }
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
            logger.debug("Number of nodes collected: "+arrayNode.size()+" and BatchSize: "+batchSize);
            if (arrayNode.size() >= batchSize){
                logger.info("Sending batch of {} Node records", arrayNode.size());
                String payload = arrayNode.toString();
                arrayNode = arrayNode.removeAll();
                if(!payload.equals("[]")){
                    UploadEventsTask uploadEventsTask = new UploadEventsTask(getTaskName(), config, publishUrl, accountName, apiKey, payload);
                    getConfiguration().getExecutorService().execute("UploadNodeData", uploadEventsTask);
                }
            }
        }
        logger.debug("Number of nodes collected: "+arrayNode.size()+" and BatchSize: "+batchSize);
         if (arrayNode.size() > 0){
             logger.info("Sending last batch of {} Node records", arrayNode.size());
             String payload = arrayNode.toString();
             arrayNode = arrayNode.removeAll();
             if(!payload.equals("[]")){
                 UploadEventsTask uploadEventsTask = new UploadEventsTask(getTaskName(), config, publishUrl, accountName, apiKey, payload);
                 getConfiguration().getExecutorService().execute("UploadNodeData", uploadEventsTask);
             }
         }

        return arrayNode;
    }

    protected SummaryObj initDefaultSummaryObject(Map<String, String> config){
        return initNodeSummaryObject(config, ALL);
    }

    

    public  static SummaryObj initNodeSummaryObject(Map<String, String> config, String node){
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode summary = mapper.createObjectNode();
        summary.put("nodename", node);
        logger.debug("Init nodename: "+ node);
        if (node.equals(ALL)) {
            summary.put("ReadyNodes", 0);
            summary.put("OutOfDiskNodes", 0);
            summary.put("MemoryPressureNodes", 0);
            summary.put("DiskPressureNodes", 0);
            summary.put("TaintsTotal", 0);
            summary.put("Masters", 0);
            summary.put("Workers", 0);
            summary.put("CapacityMemory", 0);
            summary.put("CapacityCpu", 0);
            summary.put("CapacityPods", 0);
            summary.put("AllocationsMemory", 0);
            summary.put("AllocationsCpu", 0);
            summary.put("AllocationsPods", 0);
            summary.put("CapacityMemory", 0);
            summary.put("CapacityCpu", 0);
            summary.put("CapacityPods", 0);
            summary.put("Nodes", 0);
            summary.put("NodeMetricsCollected", 0);
        }
        else{
            summary.put("CapacityMemory", 0);
            summary.put("CapacityCpu", 0);
            summary.put("CapacityPods", 0);
            summary.put("AllocationsMemory", 0);
            summary.put("AllocationsCpu", 0);
            if (node.equals("Workers") || node.equals("Masters") ) {
                summary.put("AllocationsPods", 0);
            }
        }

        ArrayList<AppDMetricObj> metricsList = initMetrics(config, node);

        String path = "";

        if (node.equals("Workers") || node.equals("Masters")) {
            path = Utilities.getMetricsPathV2(config, "Summary", node);
        }
        else{
            path = Utilities.getMetricsPath(config, ALL, node);
        }
        logger.debug("Init path: "+ path);

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
/*         if (nodeName.equals("Workers")) {
            //global
            metricsList.add(new AppDMetricObj("ReadyNodes", parentSchema, CONFIG_SCHEMA_DEF_NODE,
                    String.format("select * from %s where ready = \"True\" and clusterName = \"%s\"", parentSchema, clusterName), rootPath, ALL, nodeName));
        } */
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
