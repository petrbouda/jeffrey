package pbouda.jeffrey;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

import java.net.URL;

public class Application {

    public static void main(String[] args) throws Exception {
        Server server = new Server(8080);
        WebAppContext context = new WebAppContext();
        URL resource = Application.class.getResource("/webapp");
        context.setResourceBase(resource.toString());
        server.setHandler(context);
        server.start();
        server.join();
    }
}
