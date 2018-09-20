package com.appdynamics.monitors.kubernetes;

public class Constants {
    public static final String METRIC_SEPARATOR  = "|";
    public static final String DEFAULT_METRIC_PREFIX_NAME  = "metricPrefix";
    public static final String METRIC_PATH_NODES  = "Nodes";
    public static final String METRIC_PATH_NAMESPACES  = "Namespaces";
    public static final String DEFAULT_METRIC_PREFIX = "Server|Component:%s|Custom Metrics|Cluster Stats|";
    public static final String CONFIG_DASH_TEMPLATE_PATH = "dashboardTemplatePath";
    public static final String CONFIG_DASH_NAME_SUFFIX = "dashboardNameSuffix";
    public static final String CONFIG_DASH_CHECK_INTERVAL = "dashboardCheckInterval";
    public static final String CONFIG_APP_NAME = "appName";
    public static final String CONFIG_APP_TIER_NAME = "appTierName";
    public static final String CONFIG_NODE_ENTITIES = "entities";
    public static final String CONFIG_CONTROLLER_URL = "controllerUrl";
    public static final String CONFIG_CONTROLLER_API_USER = "controllerAPIUser";

    public static final String CONFIG_ENTITY_TYPE = "type";
    public static final String CONFIG_ENTITY_TYPE_POD = "pod";
    public static final String CONFIG_ENTITY_TYPE_NODE = "node";
    public static final String CONFIG_ENTITY_TYPE_EVENT = "event";
    public static final String CONFIG_ENTITY_TYPE_DEPLOYMENT = "deployment";
    public static final String CONFIG_ENTITY_TYPE_DAEMON = "daemon";
    public static final String CONFIG_ENTITY_TYPE_REPLICA = "replica";
    public static final String CONFIG_ENTITY_TYPE_ENDPOINT = "endpoint";

    public static final String CONFIG_SCHEMA_DEF_POD = "podsSchemaDefinition";
    public static final String CONFIG_SCHEMA_NAME_POD = "podsSchemaName";

    public static final String CONFIG_SCHEMA_DEF_NODE = "nodeSchemaDefinition";
    public static final String CONFIG_SCHEMA_NAME_NODE = "nodeSchemaName";

    public static final String CONFIG_SCHEMA_DEF_EVENT = "eventsSchemaDefinition";
    public static final String CONFIG_SCHEMA_NAME_EVENT = "eventsSchemaName";

    public static final String CONFIG_SCHEMA_DEF_RS = "rsSchemaDefinition";
    public static final String CONFIG_SCHEMA_NAME_RS = "rsSchemaName";

    public static final String CONFIG_SCHEMA_DEF_DAEMON = "daemonSchemaDefinition";
    public static final String CONFIG_SCHEMA_NAME_DAEMON = "daemonSchemaName";

    public static final String CONFIG_SCHEMA_DEF_DEPLOY = "deploySchemaDefinition";
    public static final String CONFIG_SCHEMA_NAME_DEPLOY = "deploySchemaName";

    public static final String CONFIG_SCHEMA_DEF_EP = "endpointSchemaDefinition";
    public static final String CONFIG_SCHEMA_NAME_EP = "endpointSchemaName";
}
