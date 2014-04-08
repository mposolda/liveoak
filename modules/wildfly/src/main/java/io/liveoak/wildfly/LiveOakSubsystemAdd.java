package io.liveoak.wildfly;

import io.liveoak.container.service.bootstrap.*;
import io.liveoak.spi.LiveOak;
import org.jboss.as.controller.*;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.Services;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ValueService;
import org.jboss.msc.value.ImmediateValue;

import java.net.URL;
import java.util.List;

import static io.liveoak.spi.LiveOak.SERVICE_REGISTRY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;

/**
 * @author Bob McWhirter
 */
public class LiveOakSubsystemAdd extends AbstractBoottimeAddStepHandler {
    static final LiveOakSubsystemAdd INSTANCE = new LiveOakSubsystemAdd();

    private final Logger log = Logger.getLogger(LiveOakSubsystemAdd.class);

    private LiveOakSubsystemAdd() {
    }

    @Override
    protected void populateModel(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        resource.getModel().setEmptyObject();
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model, ServiceVerificationHandler verificationHandler, List<ServiceController<?>> newControllers) throws OperationFailedException {
        super.performRuntime(context, operation, model, verificationHandler, newControllers);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void performBoottime(OperationContext context, ModelNode operation, ModelNode model,
                                ServiceVerificationHandler verificationHandler, List<ServiceController<?>> newControllers)
            throws OperationFailedException {

        //Add deployment processors here
        //Remove this if you don't need to hook into the deployers, or you can add as many as you like
        //see SubDeploymentProcessor for explanation of the phases
        context.addStep(new AbstractDeploymentChainStep() {
            public void execute(DeploymentProcessorTarget processorTarget) {
                //processorTarget.addDeploymentProcessor(LiveOakExtension.SUBSYSTEM_NAME, SimpleSubsystemDeploymentProcessor.PHASE, SimpleSubsystemDeploymentProcessor.PRIORITY, new SimpleSubsystemDeploymentProcessor());
            }
        }, OperationContext.Stage.RUNTIME);

        ServiceName name = LiveOak.LIVEOAK.append("wildfly", "subsystem");

        PropertiesManagerService properties = new PropertiesManagerService();

        context.getServiceTarget().addService(name.append("properties"), properties)
                .addDependency(ServiceName.of("jboss", "server", "path", "jboss.home.dir"), String.class, properties.jbossHomeInjector())
                .install();

        ApplicationsDirectoryPathService appsDirPath = new ApplicationsDirectoryPathService();
        context.getServiceTarget().addService(name.append("apps-dir", "path"), appsDirPath)
                .addDependency(ServiceName.of("jboss", "server", "path", "jboss.home.dir"), String.class, appsDirPath.jbossHomeInjector())
                .install();

        TenancyBootstrappingService tenancy = new TenancyBootstrappingService();
        context.getServiceTarget().addService(name.append("tenancy"), tenancy)
                .addDependency(name.append("apps-dir", "path"), String.class, tenancy.applicationsDirectoryInjector())
                .install();

        context.getServiceTarget().addService(name.append("servers"), new ServersBootstrappingService("localhost")).install();
        context.getServiceTarget().addService(name.append("codecs"), new CodecBootstrappingService()).install();
        context.getServiceTarget().addService(name.append("client"), new ClientBootstrappingService()).install();

        ExtensionsDirectoryPathService extsDirPath = new ExtensionsDirectoryPathService();
        context.getServiceTarget().addService(name.append("exts-dir", "path"), extsDirPath)
                .addDependency(ServiceName.of("jboss", "server", "path", "jboss.home.dir"), String.class, extsDirPath.jbossHomeInjector())
                .install();

        ExtensionsBootstrappingService extensions = new ExtensionsBootstrappingService();
        context.getServiceTarget().addService(name.append("extensions"), extensions)
                .addDependency(name.append("exts-dir", "path"), String.class, extensions.extensionsDirectoryInjector())
                .addDependency(name.append("properties"))
                .install();

        context.getServiceTarget().addService(LiveOak.SERVICE_REGISTRY, new ValueService<ServiceRegistry>(new ImmediateValue<>(context.getServiceRegistry(false))))
                .install();

        context.getServiceTarget().addService(name.append("vertx"), new VertxBootstrappingService())
                .install();

    }
}
