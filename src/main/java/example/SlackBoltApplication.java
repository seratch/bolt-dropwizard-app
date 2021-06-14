package example;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

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

    @Override
    public void run(final SlackBoltConfiguration configuration,
                    final Environment environment) {
        // TODO: implement application
    }

}
