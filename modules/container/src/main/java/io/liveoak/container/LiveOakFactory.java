/*
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at http://www.eclipse.org/legal/epl-v10.html
 */
package io.liveoak.container;

import io.liveoak.client.DefaultClient;
import io.liveoak.common.codec.ResourceCodec;
import io.liveoak.common.codec.ResourceCodecManager;
import io.liveoak.common.codec.ResourceDecoder;
import io.liveoak.common.codec.StateEncoder;
import io.liveoak.common.codec.html.HTMLEncoder;
import io.liveoak.common.codec.json.JSONDecoder;
import io.liveoak.common.codec.json.JSONEncoder;
import io.liveoak.container.extension.ExtensionInstaller;
import io.liveoak.container.extension.ExtensionLoader;
import io.liveoak.container.interceptor.InterceptorManagerImpl;
import io.liveoak.container.protocols.PipelineConfigurator;
import io.liveoak.container.service.*;
import io.liveoak.container.tenancy.GlobalContext;
import io.liveoak.container.tenancy.InternalApplicationRegistry;
import io.liveoak.container.tenancy.service.ApplicationRegistryService;
import io.liveoak.container.tenancy.service.ApplicationsDeployerService;
import io.liveoak.container.tenancy.service.ApplicationsDirectoryService;
import io.liveoak.container.zero.extension.ZeroExtension;
import io.liveoak.container.zero.service.ZeroBootstrapper;
import io.liveoak.spi.LiveOak;
import io.liveoak.spi.MediaType;
import io.liveoak.spi.client.Client;
import io.liveoak.spi.container.Address;
import io.liveoak.spi.container.SubscriptionManager;
import org.jboss.logging.Logger;
import org.jboss.msc.service.*;
import org.jboss.msc.value.ImmediateValue;
import org.vertx.java.core.Vertx;
import org.vertx.java.platform.PlatformManager;

import java.io.File;
import java.net.UnknownHostException;
import java.util.concurrent.Executor;

import static io.liveoak.spi.LiveOak.*;

/**
 * Bootstrapping <code>main()</code> method.
 *
 * @author Bob McWhirter
 * @author Ken Finnigan
 */
public class LiveOakFactory {

    private static final Logger log = Logger.getLogger(LiveOakFactory.class);

    public static LiveOakSystem create() throws Exception {
        return create(null, null, null);
    }

    public static LiveOakSystem create(ServiceContainer serviceContainer, ServiceTarget serviceTarget) throws Exception {
        return new LiveOakFactory(null, null, null, null, serviceContainer, serviceTarget).createInternal();
    }

    public static LiveOakSystem create(Vertx vertx) throws Exception {
        return create(null, null, vertx);
    }

    public static LiveOakSystem create(File configDir, File applicationsDir) throws Exception {
        return create(configDir, applicationsDir, null);
    }

    public static LiveOakSystem create(File configDir, File applicationsDir, Vertx vertx) throws Exception {
        return new LiveOakFactory(configDir, applicationsDir, vertx, "localhost").createInternal();
    }

    public static LiveOakSystem create(File configDir, File applicationsDir, Vertx vertx, String bindAddress) throws Exception {
        return new LiveOakFactory(configDir, applicationsDir, vertx, bindAddress).createInternal();
    }

    // ----------------------------------------------------------------------
    // ----------------------------------------------------------------------

    private LiveOakFactory(File configDir, File applicationsDir, Vertx vertx, String bindAddress) {
        this(configDir, applicationsDir, vertx, bindAddress, ServiceContainer.Factory.create());
    }

    private LiveOakFactory(File configDir, File applicationsDir, Vertx vertx, String bindAddress, ServiceContainer serviceContainer) {
        this(configDir, applicationsDir, vertx, bindAddress, serviceContainer, serviceContainer.subTarget());
    }

    private LiveOakFactory(File configDir, File applicationsDir, Vertx vertx, String bindAddress, ServiceContainer serviceContainer, ServiceTarget serviceTarget) {
        this.configDir = configDir;
        this.appsDir = applicationsDir;
        this.vertx = vertx;
        this.bindAddress = bindAddress;
        this.serviceContainer = serviceContainer;
        this.serviceTarget = serviceTarget;

        this.serviceTarget.addListener(new AbstractServiceListener<Object>() {
            @Override
            public void transition(ServiceController<?> controller, ServiceController.Transition transition) {
                if (transition.getAfter().equals(ServiceController.Substate.START_FAILED)) {
                    log.errorf(controller.getStartException(), "Unable to start service: %s", controller.getName());
                    controller.getStartException().printStackTrace();
                }
            }
        });

        this.stabilityMonitor = new StabilityMonitor();
        this.serviceTarget.addMonitor(this.stabilityMonitor);
    }

    public LiveOakSystem createInternal() throws Exception {
        prolog();
        createTenancy();
        createServers();
        createClient();
        createExtensions();
        createVertx();
        installCodecs();
        this.stabilityMonitor.awaitStability();
        return (LiveOakSystem) serviceContainer.getService(LIVEOAK).awaitValue();
    }

    protected void prolog() {
        LiveOakSystem system = new LiveOakSystem(serviceContainer);
        serviceContainer.addService(LIVEOAK, new ValueService<LiveOakSystem>(new ImmediateValue<>(system)))
                .install();

        serviceContainer.addService(SERVICE_REGISTRY, new ValueService<ServiceRegistry>(new ImmediateValue<>(serviceContainer)))
                .install();

        serviceContainer.addService(SERVICE_CONTAINER, new ValueService<ServiceRegistry>(new ImmediateValue<>(serviceContainer)))
                .install();

    }

    protected void createTenancy() {
        serviceContainer.addService(APPLICATIONS_DIR, new ApplicationsDirectoryService(this.appsDir))
                .install();

        serviceContainer.addService(APPLICATION_REGISTRY, new ApplicationRegistryService())
                .install();

        ApplicationsDeployerService deployerService = new ApplicationsDeployerService();
        serviceContainer.addService(APPLICATIONS_DEPLOYER, deployerService)
                .addDependency(APPLICATIONS_DIR, File.class, deployerService.applicationsDirectoryInjector())
                .addDependency(APPLICATION_REGISTRY, InternalApplicationRegistry.class, deployerService.applicationRegistryInjector())
                .install();

        Service<GlobalContext> globalContext = new ValueService<GlobalContext>(new ImmediateValue<>(new GlobalContext()));
        serviceContainer.addService(GLOBAL_CONTEXT, globalContext)
                .install();
    }

    protected void createExtensions() {
        ExtensionInstaller installer = new ExtensionInstaller(serviceContainer, LiveOak.resource(ZeroExtension.APPLICATION_ID, "system"));
        serviceContainer.addService(EXTENSION_INSTALLER, new ValueService<ExtensionInstaller>(new ImmediateValue<>(installer)))
                .install();

        ExtensionLoader extensionLoader = new ExtensionLoader(new File(configDir, "extensions").getAbsoluteFile());

        serviceContainer.addService(EXTENSION_LOADER, extensionLoader)
                .addDependency(EXTENSION_INSTALLER, ExtensionInstaller.class, extensionLoader.extensionInstallerInjector())
                .install();

        ZeroBootstrapper zero = new ZeroBootstrapper();

        serviceContainer.addService(LiveOak.LIVEOAK.append("zero", "bootstrapper"), zero)
                .addDependency(EXTENSION_INSTALLER, ExtensionInstaller.class, zero.extensionInstallerInjector())
                .install();
    }

    protected void createServers() throws UnknownHostException {
        AddressService address = new AddressService(bindAddress, 8080, 8383);
        serviceContainer.addService(ADDRESS, address).install();

        UnsecureServerService unsecureServer = new UnsecureServerService();
        serviceContainer.addService(server("unsecure", true), unsecureServer)
                .addDependency(PIPELINE_CONFIGURATOR, PipelineConfigurator.class, unsecureServer.pipelineConfiguratorInjector())
                .addDependency(ADDRESS, Address.class, unsecureServer.addressInjector())
                .install();

        LocalServerService localServer = new LocalServerService();
        serviceContainer.addService(server("local", false), localServer)
                .addDependency(PIPELINE_CONFIGURATOR, PipelineConfigurator.class, localServer.pipelineConfiguratorInjector())
                .install();


        SubscriptionManagerService subscriptionManager = new SubscriptionManagerService();

        serviceContainer.addService(SUBSCRIPTION_MANAGER, subscriptionManager)
                .addDependency(CLIENT, Client.class, subscriptionManager.clientInjector())
                .install();

        ValueService<InterceptorManagerImpl> interceptorManager = new ValueService<>(new ImmediateValue<InterceptorManagerImpl>(new InterceptorManagerImpl()));
        serviceContainer.addService(INTERCEPTOR_MANAGER, interceptorManager)
                .install();

        CodecManagerService codecManager = new CodecManagerService();
        serviceContainer.addService(CODEC_MANAGER, codecManager)
                .install();

        WorkerPoolService workerPool = new WorkerPoolService();
        serviceContainer.addService(WORKER_POOL, workerPool)
                .install();

        PipelineConfiguratorService pipelineConfigurator = new PipelineConfiguratorService();
        ServiceBuilder<PipelineConfigurator> pipelineBuilder = serviceContainer.addService(PIPELINE_CONFIGURATOR, pipelineConfigurator)
                .addDependency(SUBSCRIPTION_MANAGER, SubscriptionManager.class, pipelineConfigurator.subscriptionManagerInjector())
                .addDependency(INTERCEPTOR_MANAGER, InterceptorManagerImpl.class, pipelineConfigurator.interceptorManagerInjector())
                .addDependency(CODEC_MANAGER, ResourceCodecManager.class, pipelineConfigurator.codecManagerInjector())
                .addDependency(CLIENT, Client.class, pipelineConfigurator.clientInjector())
                .addDependency(GLOBAL_CONTEXT, GlobalContext.class, pipelineConfigurator.globalContextInjector())
                .addDependency(WORKER_POOL, Executor.class, pipelineConfigurator.workerPoolInjector());

        pipelineBuilder.install();


        NotifierService notifier = new NotifierService();
        serviceContainer.addService(NOTIFIER, notifier)
                .addDependency(SUBSCRIPTION_MANAGER, SubscriptionManager.class, notifier.subscriptionManagerInjector())
                .install();

    }

    protected void createClient() {
        ClientService client = new ClientService();

        ServiceTarget target = this.serviceContainer.subTarget();
        /*
        target.addListener(new AbstractServiceListener<Object>() {
            @Override
            public void transition(ServiceController<?> controller, ServiceController.Transition transition) {
                System.err.println(controller.getName() + " // " + transition);
            }
        });
        */
        target.addService(CLIENT, client)
                .install();

        ClientConnectorService clientConnector = new ClientConnectorService();
        target.addService(CLIENT.append("connect"), clientConnector)
                .addDependency(CLIENT, DefaultClient.class, clientConnector.clientInjector())
                .addDependency(server("local", false))
                .install();
    }

    protected void createVertx() {
        if (vertx == null) {
            VertxService vertxSvc = new VertxService();
            serviceContainer.addService(VERTX, vertxSvc)
                    .addDependency(VERTX_PLATFORM_MANAGER, PlatformManager.class, vertxSvc.platformManagerInjector())
                    .install();

            serviceContainer.addService(VERTX_PLATFORM_MANAGER, new PlatformManagerService())
                    .install();
        } else {
            serviceContainer.addService(VERTX, new ValueService<Vertx>(new ImmediateValue<>(vertx)))
                    .install();
        }
    }


    protected void installCodecs() {
        installCodec(serviceContainer, MediaType.JSON, JSONEncoder.class, new JSONDecoder());

        installCodec(serviceContainer, MediaType.HTML, HTMLEncoder.class, null);
    }


    private static void installCodec(ServiceContainer serviceContainer, MediaType mediaType, Class<? extends StateEncoder> encoderClass, ResourceDecoder decoder) {
        ServiceName name = codec(mediaType.toString());

        CodecService codec = new CodecService(encoderClass, decoder);
        serviceContainer.addService(name, codec)
                .install();

        CodecInstallationService installer = new CodecInstallationService(mediaType);
        serviceContainer.addService(name.append("install"), installer)
                .addDependency(name, ResourceCodec.class, installer.codecInjector())
                .addDependency(CODEC_MANAGER, ResourceCodecManager.class, installer.codecManagerInjector())
                .install();
    }

    private final File configDir;
    private final File appsDir;
    private final Vertx vertx;
    private final String bindAddress;
    private final ServiceContainer serviceContainer;
    private final ServiceTarget serviceTarget;
    private final StabilityMonitor stabilityMonitor;

}
