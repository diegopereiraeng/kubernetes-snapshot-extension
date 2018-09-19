import com.appdynamics.extensions.yml.YmlReader;
import com.appdynamics.monitors.kubernetes.Dashboard.ADQLSearchGenerator;
import com.appdynamics.monitors.kubernetes.Dashboard.ClusterDashboardGenerator;
import com.appdynamics.monitors.kubernetes.Models.AppDMetricObj;
import com.appdynamics.monitors.kubernetes.RestClient;
import com.appdynamics.monitors.kubernetes.SnapshotTasks.PodSnapshotRunner;
import com.appdynamics.monitors.kubernetes.Utilities;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;

import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_DASH_TEMPLATE_PATH;
import static com.appdynamics.monitors.kubernetes.Utilities.ALL;

public class DashboardTest {
//    @Test
//    public void testNonExistingDashboard() {
//        try{
//            File conFile = new File("src/test/resources/conf/config.yml");
//            Map<String, String> config = (Map<String, String>) YmlReader.readFromFile(conFile);
//            ClusterDashboardGenerator gen = new ClusterDashboardGenerator();
//            boolean exists = gen.dashboardExists("Dummy", config);
//
//            Assert.assertTrue(!exists);
//        }
//        catch (Exception ex){
//            Assert.fail(ex.getMessage());
//        }
//    }

//    @Test
//    public void testCreteDashboard() {
//        try{
//            File conFile = new File("src/test/resources/conf/config.yml");
//            Map<String, String> config = (Map<String, String>) YmlReader.readFromFile(conFile);
//            JsonNode answer = RestClient.createDashboard(config);
//
//            Assert.assertTrue(answer != null);
//        }
//        catch (Exception ex){
//            Assert.fail(ex.getMessage());
//        }
//    }

//    @Test
//    public void readDashboardTempate() {
//        try{
//            File conFile = new File("src/test/resources/conf/config.yml");
//            Map<String, String> config = (Map<String, String>) YmlReader.readFromFile(conFile);
//            Utilities.ClusterName = Utilities.getClusterApplicationName(config);
//            PodSnapshotRunner podSnapshotRunner = new PodSnapshotRunner();
//
//            ArrayList<AppDMetricObj> list = podSnapshotRunner.initMetrics(config);
//            ArrayList<AppDMetricObj> single = new ArrayList<>();
//            single.add(list.get(0));
//            ClusterDashboardGenerator generator = new ClusterDashboardGenerator(single);
//            JsonNode widgets = generator.readTemplate(config.get(CONFIG_DASH_TEMPLATE_PATH));
//            Assert.assertTrue(widgets != null);
//        }
//        catch (Exception ex){
//            Assert.fail(ex.getMessage());
//        }
//    }

    @Test
    public void buildDashboardTest() {
        try{
            File conFile = new File("src/test/resources/conf/config.yml");
            Map<String, String> config = (Map<String, String>) YmlReader.readFromFile(conFile);
            Utilities.ClusterName = Utilities.getClusterApplicationName(config);
            ADQLSearchGenerator.loadAllSearches(config);

            PodSnapshotRunner podSnapshotRunner = new PodSnapshotRunner();

            ArrayList<AppDMetricObj> list = podSnapshotRunner.initMetrics(config, ALL, ALL);
            ArrayList<AppDMetricObj> single = new ArrayList<AppDMetricObj>();
            single.add(list.get(0));
            ClusterDashboardGenerator generator = new ClusterDashboardGenerator(config, single);
            generator.validateDashboard(config);
            Assert.assertTrue(true);
        }
        catch (Exception ex){
            Assert.fail(ex.getMessage());
        }
    }
}
