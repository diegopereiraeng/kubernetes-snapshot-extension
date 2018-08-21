package com.appdynamics.monitors.kubernetes;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kubernetes.client.models.*;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

class Utilities {
    private static final Logger logger = LoggerFactory.getLogger(Utilities.class);
    private static final String ALL = "all";
    static HashMap<String, ObjectNode> summaryMap = new HashMap<String, ObjectNode>();

    static URL getUrl(String input){
        URL url = null;
        try {
            url = new URL(input);
        } catch (MalformedURLException e) {
            logger.error("Error forming our from String {}", input, e);
        }
        return url;
    }

    static ArrayNode createPodPayload(V1PodList podList){
        Long loadTime = new Date().getTime();
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        ObjectNode summary = initSummaryObject(loadTime, ALL, ALL);
        summaryMap.put(ALL, summary);
        for(V1Pod podItem : podList.getItems()){
            ObjectNode podObject = mapper.createObjectNode();
            String namespace = podItem.getMetadata().getNamespace();
            String nodeName = podItem.getSpec().getNodeName();

            podObject = checkAddLong(podObject, loadTime, "batch_ts");

            ObjectNode summaryNamespace = summaryMap.get(namespace);
            if (summaryNamespace == null){
                summaryNamespace = initSummaryObject(loadTime, namespace, ALL);
                summaryMap.put(namespace, summaryNamespace);
            }

            ObjectNode summaryNode = summaryMap.get(nodeName);
            if (summaryNode == null){
                summaryNode = initSummaryObject(loadTime, ALL, nodeName);
                summaryMap.put(nodeName, summaryNode);
            }

            incrementField(summary, "pods");
            incrementField(summaryNamespace, "pods");
            incrementField(summaryNode, "pods");

            podObject = checkAddLong(podObject, podItem.getMetadata().getDeletionGracePeriodSeconds(), "deletionGracePeriod");
            podObject = checkAddObject(podObject, podItem.getMetadata().getUid(), "object_uid");
            podObject = checkAddObject(podObject, podItem.getMetadata().getClusterName(), "clusterName");
            podObject = checkAddObject(podObject, podItem.getMetadata().getCreationTimestamp(), "creationTimestamp");
            podObject = checkAddObject(podObject, podItem.getMetadata().getDeletionTimestamp(), "deletionTimestamp");
//            podObject = checkAddObject(podObject, podItem.getMetadata().getGenerateName(), "generateName");
//            podObject = checkAddObject(podObject, podItem.getMetadata().getGeneration(), "generation");
//            podObject = checkAddObject(podObject, podItem.getMetadata().getLabels(), "labels");
            podObject = checkAddObject(podObject, podItem.getMetadata().getName(), "name");
            podObject = checkAddObject(podObject, namespace, "namespace");
            podObject = checkAddObject(podObject, podItem.getMetadata().getResourceVersion(), "resourceVersion");
//            podObject = checkAddObject(podObject, podItem.getMetadata().getSelfLink(), "selfLink");
            podObject = checkAddLong(podObject, podItem.getSpec().getActiveDeadlineSeconds(), "activeDeadlineSeconds");
            int containerCount = podItem.getSpec().getContainers() != null ? podItem.getSpec().getContainers().size() : 0;
            podObject = checkAddInt(podObject, containerCount, "containerCount");

            if (containerCount > 0) {
                incrementField(summary, "containers", containerCount);
                incrementField(summaryNamespace, "containers", containerCount);
                incrementField(summaryNode, "containers", containerCount);
            }

            int initContainerCount = podItem.getSpec().getInitContainers() != null ? podItem.getSpec().getInitContainers().size() : 0;
            podObject = checkAddInt(podObject, initContainerCount, "initContainerCount");

            if (initContainerCount > 0) {
                incrementField(summary, "initcontainers", initContainerCount);
                incrementField(summaryNamespace, "initcontainers", initContainerCount);
                incrementField(summaryNode, "initcontainers", initContainerCount);
            }

            podObject = checkAddObject(podObject, podItem.getSpec().getDnsPolicy(), "dnsPolicy");
            podObject = checkAddBoolean(podObject, podItem.getSpec().isHostIPC(), "hostIPC");
            podObject = checkAddBoolean(podObject, podItem.getSpec().isHostNetwork(), "hostNetwork");
            podObject = checkAddBoolean(podObject, podItem.getSpec().isHostPID(), "hostPID");
            podObject = checkAddObject(podObject, podItem.getSpec().getHostname(), "hostname");
            podObject = checkAddObject(podObject, nodeName, "nodeName");
            podObject = checkAddObject(podObject, podItem.getSpec().getPriority(), "priority");
            podObject = checkAddObject(podObject, podItem.getSpec().getRestartPolicy(), "restartPolicy");
            podObject = checkAddObject(podObject, podItem.getSpec().getSchedulerName(), "schedulerName");
            podObject = checkAddObject(podObject, podItem.getSpec().getServiceAccountName(), "serviceAccountName");
            podObject = checkAddLong(podObject, podItem.getSpec().getTerminationGracePeriodSeconds(), "terminationGracePeriodSeconds");

            int tolerationsCount = podItem.getSpec().getTolerations() != null ? podItem.getSpec().getTolerations().size() : 0;
            podObject = checkAddInt(podObject, tolerationsCount, "tolerationsCount");

            int volumesCount = podItem.getSpec().getVolumes() != null ? podItem.getSpec().getVolumes().size() : 0;
            podObject = checkAddInt(podObject, volumesCount, "volumesCount");

            boolean hasNodeAffinity = podItem.getSpec().getAffinity() != null && podItem.getSpec().getAffinity().getNodeAffinity() != null;
            podObject = checkAddBoolean(podObject, hasNodeAffinity, "hasNodeAffinity");

            boolean hasPodAffinity = podItem.getSpec().getAffinity() != null && podItem.getSpec().getAffinity().getPodAffinity() != null;
            podObject = checkAddBoolean(podObject, hasPodAffinity, "hasPodAffinity");

            boolean hasPodAntiAffinity = podItem.getSpec().getAffinity() != null && podItem.getSpec().getAffinity().getPodAntiAffinity() != null;
            podObject = checkAddBoolean(podObject, hasPodAntiAffinity, "hasPodAntiAffinity");

            podObject = checkAddObject(podObject, podItem.getStatus().getHostIP(), "hostIP");
            podObject = checkAddObject(podObject, podItem.getStatus().getPhase(), "phase");
            podObject = checkAddObject(podObject, podItem.getStatus().getPodIP(), "podIP");
            podObject = checkAddObject(podObject, podItem.getStatus().getQosClass(), "qosClass");
            podObject = checkAddObject(podObject, podItem.getStatus().getReason(), "reason");
            if (podItem.getStatus().getReason() != null && podItem.getStatus().getReason().equals("Evicted")){
                incrementField(summary, "evictions");
                incrementField(summaryNamespace, "evictions");
                incrementField(summaryNode, "evictions");
            }
            podObject = checkAddObject(podObject, podItem.getStatus().getStartTime(), "startTime");

            if (podItem.getStatus().getConditions() != null && podItem.getStatus().getConditions().size() > 0) {
                V1PodCondition recentCondition = podItem.getStatus().getConditions().get(0);
                podObject = checkAddObject(podObject, recentCondition.getLastProbeTime(), "lastProbeTimeCondition");
                podObject = checkAddObject(podObject, recentCondition.getLastTransitionTime(), "lastTransitionTimeCondition");
                podObject = checkAddObject(podObject, recentCondition.getReason(), "reasonCondition");
                podObject = checkAddObject(podObject, recentCondition.getStatus(), "statusCondition");
                podObject = checkAddObject(podObject, recentCondition.getType(), "typeCondition");
                podObject = checkAddObject(podObject, recentCondition.getMessage(), "messageCondition");
            }

            boolean limitsDefined = false;
            for(V1Container container : podItem.getSpec().getContainers()){
                limitsDefined = container.getResources() != null &&
                        (container.getResources().getLimits() != null || container.getResources().getRequests() != null);
                if (!limitsDefined){
                    break;
                }
            }
            podObject = checkAddBoolean(podObject, limitsDefined, "limitsDefined");
            if (!limitsDefined){
                incrementField(summary, "limits");
                incrementField(summaryNamespace, "limits");
                incrementField(summaryNode, "limits");
            }

            int numPorts = 0;
            int numLive = 0;
            int numReady = 0;
            for(V1Container container : podItem.getSpec().getContainers()){
                numPorts += container.getPorts() != null ? container.getPorts().size() : 0;
                numLive += container.getLivenessProbe() != null ? 1 : 0;
                numReady += container.getReadinessProbe() != null ? 1 : 0;
            }
            podObject = checkAddInt(podObject, numPorts, "numPorts");
            podObject = checkAddInt(podObject, numLive, "liveProbes");
            podObject = checkAddInt(podObject, numReady, "readyProbes");
            arrayNode.add(podObject);

            if (numLive == 0){
                incrementField(summary, "lprobe");
                incrementField(summaryNamespace, "lprobe");
                incrementField(summaryNode, "lprobe");
            }

            if (numReady == 0){
                incrementField(summary, "rprobe");
                incrementField(summaryNamespace, "rprobe");
                incrementField(summaryNode, "rprobe");
            }
        }
        return  arrayNode;
    }

    static ArrayNode createEndpointSnapshot(V1EndpointsList epList) {
        Long loadTime = new Date().getTime();
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        ObjectNode summary = summaryMap.get(ALL);
        for (V1Endpoints ep : epList.getItems()) {
            ObjectNode objectNode = mapper.createObjectNode();
            objectNode = checkAddLong(objectNode, loadTime, "batch_ts");
            objectNode = checkAddObject(objectNode, ep.getMetadata().getName(), "name");

            String namespace = ep.getMetadata().getNamespace();
            objectNode = checkAddObject(objectNode, namespace, "namespace");

            ObjectNode summaryNamespace = summaryMap.get(namespace);
            if (summaryNamespace == null){
                summaryNamespace = initSummaryObject(loadTime, namespace, ALL);
                summaryMap.put(namespace, summaryNamespace);
            }

            incrementField(summary, "endpoints");
            incrementField(summaryNamespace, "endpoints");

            objectNode = checkAddObject(objectNode, ep.getMetadata().getUid(), "object_uid");
            objectNode = checkAddObject(objectNode, ep.getMetadata().getCreationTimestamp(), "creationTimestamp");
            objectNode = checkAddObject(objectNode, ep.getMetadata().getDeletionTimestamp(), "deletionTimestamp");
            int ups = 0;
            int downs = 0;
            String downContext = "";
            if (ep.getSubsets() != null) {

                for (V1EndpointSubset subset : ep.getSubsets()) {
                    if (subset.getAddresses() != null) {
                        ups += subset.getAddresses().size();
                    }

                    if (subset.getNotReadyAddresses() != null) {
                        downs += subset.getNotReadyAddresses().size();
                        for (V1EndpointAddress address : subset.getNotReadyAddresses()) {
                            String obj = address.getTargetRef() != null ? address.getTargetRef().getName() : "";
                            downContext += String.format("%s, %s", obj, address.getIp());
                        }

                    }
                }
            }

            objectNode = checkAddInt(objectNode, ups, "ip_up");
            objectNode = checkAddInt(objectNode, downs, "ip_down");
            objectNode = checkAddObject(objectNode, downContext, "downContext");

            if (ups > 0){
                incrementField(summary, "endpoints_healthy");
                incrementField(summaryNamespace, "endpoints_healthy");
            }

            incrementField(summary, "ip_up", ups);
            incrementField(summaryNamespace, "ip_up", ups);

            incrementField(summary, "ip_down", downs);
            incrementField(summaryNamespace, "ip_down", downs);

            arrayNode.add(objectNode);
        }

        return arrayNode;
    }

    static ArrayNode createDeployPayload(ExtensionsV1beta1DeploymentList deployList){
        Long loadTime = new Date().getTime();
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        ObjectNode summary = summaryMap.get(ALL);
        for(ExtensionsV1beta1Deployment deployItem : deployList.getItems()) {
            ObjectNode deployObject = mapper.createObjectNode();

            String namespace = deployItem.getMetadata().getNamespace();

            deployObject = checkAddLong(deployObject, loadTime, "batch_ts");

            ObjectNode summaryNamespace = summaryMap.get(namespace);
            if (summaryNamespace == null){
                summaryNamespace = initSummaryObject(loadTime, namespace, ALL);
                summaryMap.put(namespace, summaryNamespace);
            }

            incrementField(summary, "deploys");
            incrementField(summaryNamespace, "deploys");


            deployObject = checkAddLong(deployObject, deployItem.getMetadata().getDeletionGracePeriodSeconds(), "deletionGracePeriod");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getUid(), "object_uid");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getClusterName(), "clusterName");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getCreationTimestamp(), "creationTimestamp");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getDeletionTimestamp(), "deletionTimestamp");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getName(), "name");
            deployObject = checkAddObject(deployObject, namespace, "namespace");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getResourceVersion(), "resourceVersion");

            deployObject = checkAddInt(deployObject, deployItem.getSpec().getMinReadySeconds(), "minReadySecs");
            deployObject = checkAddInt(deployObject, deployItem.getSpec().getProgressDeadlineSeconds(), "progressDeadlineSecs");

            int replicas = deployItem.getSpec().getReplicas();
            deployObject = checkAddInt(deployObject, deployItem.getSpec().getReplicas(), "replicas");

            incrementField(summary, "deployReplicas", replicas);
            incrementField(summaryNamespace, "deployReplicas", replicas);


            deployObject = checkAddInt(deployObject, deployItem.getSpec().getRevisionHistoryLimit(), "revisionHistoryLimits");

            deployObject = checkAddObject(deployObject, deployItem.getSpec().getStrategy().getType(), "strategy");

            if (deployItem.getSpec().getStrategy().getRollingUpdate() != null){
                deployObject = checkAddObject(deployObject, deployItem.getSpec().getStrategy().getRollingUpdate().getMaxSurge(), "maxSurge");
                deployObject = checkAddObject(deployObject, deployItem.getSpec().getStrategy().getRollingUpdate().getMaxUnavailable(), "maxUnavailable");
            }


            deployObject = checkAddInt(deployObject, deployItem.getStatus().getAvailableReplicas(), "replicasAvailable");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getUnavailableReplicas(), "replicasUnAvailable");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getUpdatedReplicas(), "replicasUpdated");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getCollisionCount(), "collisionCount");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getReadyReplicas(), "replicasReady");

            if (deployItem.getStatus().getAvailableReplicas() != null) {
                incrementField(summary, "deployReplicasAvailable", deployItem.getStatus().getAvailableReplicas());
                incrementField(summaryNamespace, "deployReplicasAvailable", deployItem.getStatus().getAvailableReplicas());
            }

            if (deployItem.getStatus().getUnavailableReplicas() != null) {
                incrementField(summary, "deployReplicasUnAvailable", deployItem.getStatus().getUnavailableReplicas());
                incrementField(summaryNamespace, "deployReplicasUnAvailable", deployItem.getStatus().getUnavailableReplicas());
            }

            if (deployItem.getStatus().getCollisionCount() != null) {
                incrementField(summary, "deployCollisionCount", deployItem.getStatus().getCollisionCount());
                incrementField(summaryNamespace, "deployCollisionCount", deployItem.getStatus().getCollisionCount());
            }

            if (deployItem.getStatus().getReadyReplicas() != null) {
                incrementField(summary, "deployReplicasReady", deployItem.getStatus().getReadyReplicas());
                incrementField(summaryNamespace, "deployReplicasReady", deployItem.getStatus().getReadyReplicas());
            }


            arrayNode.add(deployObject);
        }


        return arrayNode;
    }


    static ArrayNode createDaemonsetPayload(V1beta1DaemonSetList dsList){
        Long loadTime = new Date().getTime();
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        ObjectNode summary = summaryMap.get(ALL);
        for(V1beta1DaemonSet deployItem : dsList.getItems()) {
            ObjectNode deployObject = mapper.createObjectNode();

            String namespace = deployItem.getMetadata().getNamespace();

            deployObject = checkAddLong(deployObject, loadTime, "batch_ts");

            ObjectNode summaryNamespace = summaryMap.get(namespace);
            if (summaryNamespace == null){
                summaryNamespace = initSummaryObject(loadTime, namespace, ALL);
                summaryMap.put(namespace, summaryNamespace);
            }

            incrementField(summary, "daemons");
            incrementField(summaryNamespace, "daemons");


            deployObject = checkAddLong(deployObject, deployItem.getMetadata().getDeletionGracePeriodSeconds(), "deletionGracePeriod");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getUid(), "object_uid");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getClusterName(), "clusterName");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getCreationTimestamp(), "creationTimestamp");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getDeletionTimestamp(), "deletionTimestamp");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getName(), "name");
            deployObject = checkAddObject(deployObject, namespace, "namespace");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getResourceVersion(), "resourceVersion");

            deployObject = checkAddInt(deployObject, deployItem.getSpec().getMinReadySeconds(), "minReadySecs");


            deployObject = checkAddInt(deployObject, deployItem.getSpec().getRevisionHistoryLimit(), "revisionHistoryLimits");




            deployObject = checkAddInt(deployObject, deployItem.getStatus().getNumberAvailable(), "replicasAvailable");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getNumberUnavailable(), "replicasUnAvailable");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getCollisionCount(), "collisionCount");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getNumberReady(), "replicasReady");

            deployObject = checkAddInt(deployObject, deployItem.getStatus().getCurrentNumberScheduled(), "numberScheduled");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getDesiredNumberScheduled(), "desiredNumber");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getNumberMisscheduled(), "missScheduled");

            deployObject = checkAddInt(deployObject, deployItem.getStatus().getUpdatedNumberScheduled(), "updatedNumberScheduled");

            if (deployItem.getStatus().getNumberAvailable() != null) {
                incrementField(summary, "daemonReplicasAvailable", deployItem.getStatus().getNumberAvailable());
                incrementField(summaryNamespace, "daemonReplicasAvailable", deployItem.getStatus().getNumberAvailable());
            }

            if (deployItem.getStatus().getNumberUnavailable() != null) {
                incrementField(summary, "daemonReplicasUnAvailable", deployItem.getStatus().getNumberUnavailable());
                incrementField(summaryNamespace, "daemonReplicasUnAvailable", deployItem.getStatus().getNumberUnavailable());
            }

            if (deployItem.getStatus().getCollisionCount() != null) {
                incrementField(summary, "daemonCollisionCount", deployItem.getStatus().getCollisionCount());
                incrementField(summaryNamespace, "daemonCollisionCount", deployItem.getStatus().getCollisionCount());
            }

            if (deployItem.getStatus().getNumberReady() != null) {
                incrementField(summary, "daemonReplicasReady", deployItem.getStatus().getNumberReady());
                incrementField(summaryNamespace, "daemonReplicasReady", deployItem.getStatus().getNumberReady());
            }

            if (deployItem.getStatus().getCurrentNumberScheduled() != null) {
                incrementField(summary, "daemonNumberScheduled", deployItem.getStatus().getCurrentNumberScheduled());
                incrementField(summaryNamespace, "daemonNumberScheduled", deployItem.getStatus().getCurrentNumberScheduled());
            }

            if (deployItem.getStatus().getDesiredNumberScheduled() != null) {
                incrementField(summary, "daemonDesiredNumber", deployItem.getStatus().getDesiredNumberScheduled());
                incrementField(summaryNamespace, "daemonDesiredNumber", deployItem.getStatus().getDesiredNumberScheduled());
            }

            if (deployItem.getStatus().getNumberMisscheduled() != null) {
                incrementField(summary, "daemonMissScheduled", deployItem.getStatus().getNumberMisscheduled());
                incrementField(summaryNamespace, "daemonMissScheduled", deployItem.getStatus().getNumberMisscheduled());
            }

            arrayNode.add(deployObject);
        }


        return arrayNode;
    }

    static ArrayNode createReplicasetPayload(V1beta1ReplicaSetList rsList) {
        Long loadTime = new Date().getTime();
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        ObjectNode summary = summaryMap.get(ALL);
        for (V1beta1ReplicaSet deployItem : rsList.getItems()) {
            ObjectNode deployObject = mapper.createObjectNode();

            String namespace = deployItem.getMetadata().getNamespace();

            deployObject = checkAddLong(deployObject, loadTime, "batch_ts");

            ObjectNode summaryNamespace = summaryMap.get(namespace);
            if (summaryNamespace == null) {
                summaryNamespace = initSummaryObject(loadTime, namespace, ALL);
                summaryMap.put(namespace, summaryNamespace);
            }

            incrementField(summary, "rs");
            incrementField(summaryNamespace, "rs");


            deployObject = checkAddLong(deployObject, deployItem.getMetadata().getDeletionGracePeriodSeconds(), "deletionGracePeriod");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getUid(), "object_uid");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getClusterName(), "clusterName");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getCreationTimestamp(), "creationTimestamp");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getDeletionTimestamp(), "deletionTimestamp");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getName(), "name");
            deployObject = checkAddObject(deployObject, namespace, "namespace");
            deployObject = checkAddObject(deployObject, deployItem.getMetadata().getResourceVersion(), "resourceVersion");

            deployObject = checkAddInt(deployObject, deployItem.getSpec().getMinReadySeconds(), "minReadySecs");

            int replicas = deployItem.getSpec().getReplicas();
            deployObject = checkAddInt(deployObject, deployItem.getSpec().getReplicas(), "replicas");

            incrementField(summary, "rsReplicas", replicas);
            incrementField(summaryNamespace, "rsReplicas", replicas);


            deployObject = checkAddInt(deployObject, deployItem.getStatus().getAvailableReplicas(), "rsReplicasAvailable");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getFullyLabeledReplicas(), "replicasLabeled");
            deployObject = checkAddInt(deployObject, deployItem.getStatus().getReadyReplicas(), "replicasReady");

            if (deployItem.getStatus().getAvailableReplicas() != null) {
                incrementField(summary, "rsReplicasAvailable", deployItem.getStatus().getAvailableReplicas());
                incrementField(summaryNamespace, "rsReplicasAvailable", deployItem.getStatus().getFullyLabeledReplicas());
            }


            arrayNode.add(deployObject);
        }

        return arrayNode;
    }


    static ArrayNode createPodSecurityPayload(V1beta1PodSecurityPolicyList list){
        Long loadTime = new Date().getTime();
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        ObjectNode summary = summaryMap.get(ALL);
        for (V1beta1PodSecurityPolicy policyItem : list.getItems()) {
            ObjectNode policyObject = mapper.createObjectNode();

            String namespace = policyItem.getMetadata().getNamespace();

            policyObject = checkAddLong(policyObject, loadTime, "batch_ts");

            ObjectNode summaryNamespace = summaryMap.get(namespace);
            if (summaryNamespace == null) {
                summaryNamespace = initSummaryObject(loadTime, namespace, ALL);
                summaryMap.put(namespace, summaryNamespace);
            }

            policyObject = checkAddObject(policyObject, policyItem.getMetadata().getUid(), "object_uid");
            policyObject = checkAddObject(policyObject, policyItem.getMetadata().getClusterName(), "clusterName");
            policyObject = checkAddObject(policyObject, policyItem.getMetadata().getCreationTimestamp(), "creationTimestamp");
            policyObject = checkAddObject(policyObject, policyItem.getMetadata().getDeletionTimestamp(), "deletionTimestamp");
            policyObject = checkAddObject(policyObject, policyItem.getMetadata().getName(), "name");
            policyObject = checkAddObject(policyObject, namespace, "namespace");

            boolean isPrivileged = policyItem.getSpec().isPrivileged();
            policyObject = checkAddBoolean(policyObject,  isPrivileged,"privileged");
            if (isPrivileged){
                incrementField(summary, "privilegedPods");
                incrementField(summaryNamespace, "privilegedPods");
            }

            policyObject = checkAddObject(policyObject, policyItem.getSpec().getRunAsUser().getRule(), "runAsUser");


            arrayNode.add(policyObject);
        }
        return arrayNode;
    }


    private static ObjectNode checkAddObject(ObjectNode objectNode, Object object, String fieldName){
        if(object != null){
            objectNode.put(fieldName, object.toString());
        }
        return objectNode;
    }

    private static ObjectNode checkAddInt(ObjectNode objectNode, Integer val, String fieldName){
        if (val == null){
            val = 0;
        }
        objectNode.put(fieldName, val);

        return objectNode;
    }

    private static ObjectNode checkAddLong(ObjectNode objectNode, Long val, String fieldName){
        if (val == null){
            val = 0L;
        }
        objectNode.put(fieldName, val);

        return objectNode;
    }

    private static ObjectNode checkAddBoolean(ObjectNode objectNode, Boolean val, String fieldName){
        if (val == null){
            val = false;
        }
        objectNode.put(fieldName, val);

        return objectNode;
    }

    static ArrayNode checkSchemaForUpdates(JsonNode serverSchema, ObjectNode newSchema){

        JsonNode serverNode = serverSchema.get("schema");
        ArrayNode updateSchema = null;
        ObjectNode updateNode = null;
        ObjectNode addNode = null;
        logger.info("Starting schema check");
        Iterator<Map.Entry<String, JsonNode>> nodes = newSchema.get("schema").fields();
        while (nodes.hasNext()){
            Map.Entry<String, JsonNode> entry = nodes.next();
            String fieldName = entry.getKey();
            logger.info("Checking field {}", fieldName);
            if (!serverNode.has(fieldName)) {
                logger.info("Field {} does not exist. Adding", fieldName);
                if (updateSchema == null) {
                    ObjectMapper mapper = new ObjectMapper();
                    updateSchema = mapper.createArrayNode();
                    updateNode = mapper.createObjectNode();
                    updateSchema.add(updateNode);
                    addNode = mapper.createObjectNode();
                    updateNode.set("add", addNode);
                    ObjectNode renameNode = mapper.createObjectNode();
                    updateNode.set("rename", renameNode);
                    logger.info("Initialized change schema object");
                }
                String type = entry.getValue().asText();
                addNode.put(fieldName, type);
            }
        }

        return updateSchema;
    }

    private  static ObjectNode initSummaryObject(long batchTS, String namespace, String node){
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode summary = mapper.createObjectNode();
        summary.put("batch_ts", batchTS);
        summary.put("namespace", namespace);
        summary.put("nodename", node);
        summary.put("pods", 0);
        summary.put("containers", 0);
        summary.put("initcontainers", 0);
        summary.put("evictions", 0);
        summary.put("limits", 0);
        summary.put("rprobe", 0);
        summary.put("lprobe", 0);
        summary.put("endpoints", 0);
        summary.put("endpoints_healthy", 0);
        summary.put("ip_up", 0);
        summary.put("ip_down", 0);
        summary.put("deploys", 0);
        summary.put("deployReplicas", 0);
        summary.put("deployReplicasAvailable", 0);
        summary.put("deployReplicasUnAvailable", 0);
        summary.put("deployCollisionCount", 0);
        summary.put("deployReplicasReady", 0);
        summary.put("daemons", 0);
        summary.put("daemonReplicasAvailable", 0);
        summary.put("daemonReplicasUnAvailable", 0);
        summary.put("daemonCollisionCount", 0);
        summary.put("daemonReplicasReady", 0);
        summary.put("daemonNumberScheduled", 0);
        summary.put("daemonDesiredNumber", 0);
        summary.put("daemonMissScheduled", 0);
        summary.put("rs", 0);
        summary.put("rsReplicas", 0);
        summary.put("rsReplicasAvailable", 0);
        summary.put("privilegedPods", 0);

        return summary;
    }

    private static ObjectNode incrementField(ObjectNode obj, String fieldName){
        if(obj != null && obj.has(fieldName)) {
            int val = obj.get(fieldName).asInt() + 1;
            obj.put(fieldName, val);
        }
        if (obj != null && !obj.has(fieldName)){
            obj.put(fieldName, 1);
        }
        return obj;
    }

    private static ObjectNode incrementField(ObjectNode obj, String fieldName, int increment){
        if(obj != null && obj.has(fieldName)) {
            int val = obj.get(fieldName).asInt();
            obj.put(fieldName, val+increment);
        }
        if (obj != null && !obj.has(fieldName)){
            obj.put(fieldName, increment);
        }
        return obj;
    }

    public  static ArrayNode getSummaryData(){
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode list = mapper.createArrayNode();
        Iterator it = summaryMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            list.add((ObjectNode)pair.getValue());
            it.remove();
        }
        return list;
    }

    public static void clearSummary(){
        //clear summary data
        summaryMap.clear();
    }

}
