import com.appdynamics.extensions.yml.YmlReader;
import com.appdynamics.monitors.kubernetes.Dashboard.ADQLSearchGenerator;
import com.appdynamics.monitors.kubernetes.Dashboard.ClusterDashboardGenerator;
import com.appdynamics.monitors.kubernetes.Models.AppDMetricObj;
import com.appdynamics.monitors.kubernetes.RestClient;
import com.appdynamics.monitors.kubernetes.SnapshotTasks.*;
import com.appdynamics.monitors.kubernetes.Utilities;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;

import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_DASH_NAME_SUFFIX;
import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_DASH_TEMPLATE_PATH;
import static com.appdynamics.monitors.kubernetes.Utilities.ALL;

public class DashboardTest {
    @Test
    public void testNonExistingDashboard() {
        try{
            File conFile = new File("src/test/resources/conf/config.yml");
            Map<String, String> config = (Map<String, String>) YmlReader.readFromFile(conFile);
            ClusterDashboardGenerator gen = new ClusterDashboardGenerator(config, null);
            boolean exists = gen.dashboardExists("Dummy", config);

            Assert.assertTrue(!exists);
        }
        catch (Exception ex){
            Assert.fail(ex.getMessage());
        }
    }

    @Test
    public void readDashboardTemplate() {
        try{
            File conFile = new File("src/test/resources/conf/config.yml");
            Map<String, String> config = (Map<String, String>) YmlReader.readFromFile(conFile);
            Utilities.ClusterName = Utilities.getClusterApplicationName(config);

            ArrayList<AppDMetricObj> list = PodSnapshotRunner.initMetrics(config, ALL, ALL);
            ArrayList<AppDMetricObj> single = new ArrayList<>();
            single.add(list.get(0));
            ClusterDashboardGenerator generator = new ClusterDashboardGenerator(config, single);
            JsonNode widgets = generator.readTemplate(config.get(CONFIG_DASH_TEMPLATE_PATH));
            Assert.assertTrue(widgets != null);
        }
        catch (Exception ex){
            Assert.fail(ex.getMessage());
        }
    }

    @Test
    public void buildDashboardTest() {
        try{
            File conFile = new File("src/test/resources/conf/config.yml");
            Map<String, String> config = (Map<String, String>) YmlReader.readFromFile(conFile);
            Utilities.ClusterName = Utilities.getClusterApplicationName(config);
            ADQLSearchGenerator.loadAllSearches(config);

            PodSnapshotRunner podSnapshotRunner = new PodSnapshotRunner();

            ArrayList<AppDMetricObj> list = podSnapshotRunner.initMetrics(config, ALL, ALL);
            ClusterDashboardGenerator generator = new ClusterDashboardGenerator(config, list);
            generator.validateDashboard(config);
            Assert.assertTrue(true);
        }
        catch (Exception ex){
            Assert.fail(ex.getMessage());
        }
    }

    @Test
    public void generateDashboardTemplate(){
        try{
            File conFile = new File("src/test/resources/conf/config.yml");
            Map<String, String> config = (Map<String, String>) YmlReader.readFromFile(conFile);
            String dashName = String.format("%s-%s-%s", Utilities.getClusterApplicationName(config), Utilities.getClusterTierName(config), config.get(CONFIG_DASH_NAME_SUFFIX));


            Utilities.ClusterName = Utilities.getClusterApplicationName(config);
            ArrayList<AppDMetricObj> list = PodSnapshotRunner.initMetrics(config, ALL, ALL);
            list.addAll(DaemonSnapshotRunner.initMetrics(config, ALL));
            list.addAll(ReplicaSnapshotRunner.initMetrics(config, ALL));
            list.addAll(EventSnapshotRunner.initMetrics(config, ALL));
            list.addAll(NodeSnapshotRunner.initMetrics(config, ALL));
            list.addAll(EndpointSnapshotRunner.initMetrics(config, ALL));
            list.addAll(DeploymentSnapshotRunner.initMetrics(config, ALL));

            ClusterDashboardGenerator generator = new ClusterDashboardGenerator(config, list);
            String path = config.get(CONFIG_DASH_TEMPLATE_PATH);
            JsonNode template = generator.readTemplate(path);
            JsonNode widgets = template.get("widgetTemplates");
            //update name
            ((ObjectNode)template).put("name", dashName);

            generator.buildDashboard(list,  widgets);
            generator.writeTemplate(config, template);
        }
        catch (Exception ex){
            Assert.fail(ex.getMessage());
        }
    }
}
