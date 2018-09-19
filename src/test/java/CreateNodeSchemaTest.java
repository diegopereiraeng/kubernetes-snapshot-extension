import com.appdynamics.extensions.yml.YmlReader;
import com.appdynamics.monitors.kubernetes.Utilities;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.Map;

import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_SCHEMA_DEF_NODE;
import static com.appdynamics.monitors.kubernetes.Constants.CONFIG_SCHEMA_NAME_NODE;

public class CreateNodeSchemaTest {

    @Test
    public void testCreteAppWithTier() {
        try{
            File conFile = new File("src/test/resources/conf/config.yml");

            Map<String, String> config = (Map<String, String>) YmlReader.readFromFile(conFile);
            String apiKey = config.get("eventsApiKey");
            String accountName = config.get("accountName");
            URL publishUrl = Utilities.ensureSchema(config, apiKey, accountName, CONFIG_SCHEMA_NAME_NODE, CONFIG_SCHEMA_DEF_NODE);
            Assert.assertTrue(publishUrl != null);
        }
        catch (Exception ex){
            Assert.fail(ex.getMessage());
        }
    }
}
