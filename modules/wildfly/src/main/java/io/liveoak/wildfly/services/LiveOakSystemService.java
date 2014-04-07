package io.liveoak.wildfly.services;

import io.liveoak.container.LiveOakFactory;
import io.liveoak.container.LiveOakSystem;
import io.liveoak.container.server.AbstractServer;
import org.jboss.msc.service.*;

/**
 * @author Bob McWhirter
 */
public class LiveOakSystemService implements Service<LiveOakSystem> {

    public static final ServiceName NAME = ServiceName.of( "liveoak", "wildfly", "system" );

    @Override
    public void start(StartContext context) throws StartException {
        try {
            this.system = LiveOakFactory.create( context.getController().getServiceContainer(), context.getChildTarget() );
        } catch (Exception e) {
            throw new StartException( e );
        }
    }

    @Override
    public void stop(StopContext context) {
        this.system.stop();
        this.system = null;
    }

    @Override
    public LiveOakSystem getValue() throws IllegalStateException, IllegalArgumentException {
        return this.system;
    }

    private LiveOakSystem system;

}
