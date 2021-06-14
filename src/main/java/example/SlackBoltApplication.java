package example;

import com.codahale.metrics.health.HealthCheck;
import com.slack.api.bolt.App;
import com.slack.api.bolt.request.Request;
import com.slack.api.bolt.request.RequestHeaders;
import com.slack.api.bolt.servlet.SlackAppServlet;
import com.slack.api.bolt.util.QueryStringParser;
import com.slack.api.bolt.util.SlackRequestParser;
import com.slack.api.model.event.AppMentionEvent;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static com.slack.api.bolt.servlet.ServletAdapterOps.toHeaderMap;

public class SlackBoltApplication extends Application<SlackBoltConfiguration> {

    public static void main(final String[] args) throws Exception {
        new SlackBoltApplication().run(args);
    }

    @Override
    public String getName() {
        return "SlackBolt";
    }

    @Override
    public void initialize(final Bootstrap<SlackBoltConfiguration> bootstrap) {
        // TODO: application initialization
    }

    public class MyHealthCheck extends HealthCheck {
        @Override
        protected Result check() {
            return Result.healthy();
        }
    }

    // ---------------------------
    // Solution 1: Use SlackAppServlet and register a plain servlet to the app
    // The superclass SlackAppServlet handles most of the things for you.
    public static class MySlackAppServlet extends SlackAppServlet {
        public MySlackAppServlet(App app) {
            super(app);
        }
    }

    // ---------------------------
    // Solution 2: Create your own resource class
    // Perhaps, this may be preferable for Dropwizard users. The disadvantage of this approach is
    // that you need to maintain your own adapter for HTTP request/response handling.
    @Path("/slack/events2")
    public static class MySlackAppResource {
        private final App app;
        private final SlackRequestParser requestParser;

        public MySlackAppResource(App app) {
            this.app = app;
            this.requestParser = new SlackRequestParser(app.config());
        }

        // Convert the raw HTTP request to Bolt's abstraction data
        public Request<?> buildSlackRequest(HttpServletRequest req, InputStream rawRequestBody) {
            // Your Bolt app needs to access the raw request body to verify the x-slack-signature header.
            // Refer to https://api.slack.com/authentication/verifying-requests-from-slack for details.
            Scanner s = new Scanner(rawRequestBody).useDelimiter("\\A");
            String requestBody = s.hasNext() ? s.next() : "";
            RequestHeaders headers = new RequestHeaders(toHeaderMap(req));
            SlackRequestParser.HttpRequest rawRequest = SlackRequestParser.HttpRequest.builder()
                    .requestUri(req.getRequestURI())
                    .queryString(QueryStringParser.toMap(req.getQueryString()))
                    .headers(headers)
                    .requestBody(requestBody)
                    .remoteAddress(req.getRemoteAddr())
                    .build();
            return requestParser.parse(rawRequest);
        }

        // Handle the incoming requests from Slack
        @POST
        public Response post(@Context HttpServletRequest request, InputStream body) throws Exception {
            // Convert the raw HTTP request to Bolt's abstraction data
            Request<?> boltRequest = buildSlackRequest(request, body);

            // Run the middleware / listeners in your App
            com.slack.api.bolt.response.Response boltResponse = app.run(boltRequest);

            // Construct the JAX-RS compatible HTTP response
            Response.ResponseBuilder response = Response.status(boltResponse.getStatusCode());
            for (Map.Entry<String, List<String>> h : boltResponse.getHeaders().entrySet()) {
                if (h.getValue() != null && h.getValue().size() >= 0) {
                    response.header(h.getKey(), h.getValue().get(0));
                }
            }
            response.type(boltResponse.getContentType());
            response.entity(boltResponse.getBody());
            return response.build();
        }
    }

    @Override
    public void run(
            final SlackBoltConfiguration configuration,
            final Environment environment) {
        environment.healthChecks().register("template", new MyHealthCheck());

        // You can have the code using App as a component
        // (Prerequisites)
        // export SLACK_SIGNING_SECRET=(your own value)
        // export SLACK_BOT_TOKEN=(your own value)
        App app = new App();

        app.event(AppMentionEvent.class, (req, ctx) -> {
            ctx.say("Hi there!");
            return ctx.ack();
        });

        app.command("/http-mode-test", (req, ctx) -> ctx.ack("Hi there!"));

        // Solution 1: Use SlackAppServlet and register a plain servlet to the app
        // The superclass SlackAppServlet handles most of the things for you.
        environment.servlets()
                .addServlet("slack-app", new MySlackAppServlet(app))
                .addMapping("/slack/events");

        // Solution 2: Create your own resource class
        environment.jersey().register(new MySlackAppResource(app));

        // Please note that, if you support the Slack OAuth flow as well,
        // this app should have `GET /slack/install` & `GET /slack/oauth_redirect` endpoints
        // with relevant code in addition to `POST /slack/events`.
        // Refer to https://slack.dev/java-slack-sdk/guides/app-distribution for more details.
    }

}
