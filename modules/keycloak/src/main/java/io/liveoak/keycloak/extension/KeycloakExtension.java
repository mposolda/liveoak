package io.liveoak.keycloak.extension;

import io.liveoak.keycloak.KeycloakConfig;
import io.liveoak.keycloak.KeycloakServices;
import io.liveoak.keycloak.service.KeycloakConfigService;
import io.liveoak.keycloak.service.KeycloakConfigResourceService;
import io.liveoak.keycloak.service.KeycloakResourceService;
import io.liveoak.spi.LiveOak;
import io.liveoak.spi.extension.ApplicationExtensionContext;
import io.liveoak.spi.extension.Extension;
import io.liveoak.spi.extension.SystemExtensionContext;
import io.liveoak.spi.resource.async.DefaultRootResource;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * @author Bob McWhirter
 */
public class KeycloakExtension implements Extension {

    @Override
    public void extend(SystemExtensionContext context) throws Exception {
        ServiceTarget target = context.target();

        target.addListener(new AbstractServiceListener<Object>() {
            @Override
            public void transition(ServiceController<?> controller, ServiceController.Transition transition) {
                System.err.println(controller.getName() + " :: " + transition);
            }
        });

        target.addService(KeycloakServices.address(), new KeycloakConfigService())
                .install();

        ServiceName serviceName = LiveOak.systemResource(context.id());

        KeycloakConfigResourceService resource = new KeycloakConfigResourceService(context.id());
        target.addService(serviceName, resource)
                .addDependency(KeycloakServices.address(), KeycloakConfig.class, resource.address())
                .install();

        context.mountPrivate(serviceName);
    }

    @Override
    public void extend(ApplicationExtensionContext context) throws Exception {

        String appId = context.application().id();

        ServiceTarget target = context.target();

        target.addListener(new AbstractServiceListener<Object>() {
            @Override
            public void transition(ServiceController<?> controller, ServiceController.Transition transition) {
                System.err.println(controller.getName() + " :: " + transition);
            }
        });

        KeycloakResourceService resource = new KeycloakResourceService(context.resourceId());
        target.addService(LiveOak.resource(appId, context.resourceId()), resource)
                .addDependency(KeycloakServices.address(), KeycloakConfig.class, resource.address())
                .install();

        context.mountPublic();
        context.mountPrivate(new DefaultRootResource(context.resourceId()));
    }

    @Override
    public void unextend(ApplicationExtensionContext context) throws Exception {

    }
}
