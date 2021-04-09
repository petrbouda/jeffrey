package pbouda.jeffrey;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import pbouda.jeffrey.scheduler.GeneratingTask;
import pbouda.jeffrey.scheduler.Scheduler;

import java.net.URL;
import java.nio.file.Path;

public class Application {

    public static void main(String[] args) throws Exception {
        Config config = ConfigFactory.load();

        Config filesystem = config.getConfig("filesystem");
        Directory.init(filesystem);

        GeneratingTask generatingTask = new GeneratingTask(
                Path.of(filesystem.getString("jfr")),
                Path.of(filesystem.getString("repository"))
        );

        Scheduler.periodic(generatingTask);

        Server server = new Server(8080);
        WebAppContext context = new WebAppContext();
        URL resource = Application.class.getResource("/webapp");
        context.setResourceBase(resource.toString());
        server.setHandler(context);
        server.start();
        server.join();
    }
}
