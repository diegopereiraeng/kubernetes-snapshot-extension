import com.appdynamics.extensions.yml.YmlReader;
import com.appdynamics.monitors.kubernetes.Dashboard.ADQLSearchGenerator;
import com.appdynamics.monitors.kubernetes.KubernetesSnapshotExtension;
import com.appdynamics.monitors.kubernetes.Utilities;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Map;

import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_SCHEMA_DEF_POD;
import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_SCHEMA_NAME_POD;

public class AppInitTest {

    @Test
    public void testCreteAppWithTier() {
        try{
            File conFile = new File("src/test/resources/conf/config.yml");
            Map<String, String> config = (Map<String, String>) YmlReader.readFromFile(conFile);
            KubernetesSnapshotExtension extension = new KubernetesSnapshotExtension();
            extension.initClusterMonitoring(config);
            Assert.assertTrue(Utilities.tierID > 0);
        }
        catch (Exception ex){
            Assert.fail(ex.getMessage());
        }
    }
}
