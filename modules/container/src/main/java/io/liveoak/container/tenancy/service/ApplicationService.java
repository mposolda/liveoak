package io.liveoak.container.tenancy.service;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.liveoak.common.codec.DefaultResourceState;
import io.liveoak.common.codec.json.JSONDecoder;
import io.liveoak.common.util.ConversionUtils;
import io.liveoak.common.util.ObjectMapperFactory;
import io.liveoak.container.extension.MediaTypeMountService;
import io.liveoak.container.tenancy.ApplicationConfigurationManager;
import io.liveoak.container.tenancy.ApplicationContext;
import io.liveoak.container.tenancy.ApplicationResource;
import io.liveoak.container.tenancy.InternalApplication;
import io.liveoak.container.tenancy.InternalApplicationRegistry;
import io.liveoak.container.tenancy.MountPointResource;
import io.liveoak.container.zero.extension.ZeroExtension;
import io.liveoak.container.zero.service.ApplicationClientsInstallService;
import io.liveoak.spi.LiveOak;
import io.liveoak.spi.MediaType;
import io.liveoak.spi.ResourcePath;
import io.liveoak.spi.state.ResourceState;
import org.jboss.logging.Logger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * @author Bob McWhirter
 * @author Ken Finnigan
 */
public class ApplicationService implements Service<InternalApplication> {

    public ApplicationService(String id, String name, File directory) {
        this.id = id;
        this.name = name != null ? name : id;
        this.directory = directory;
    }

    @Override
    public void start(StartContext context) throws StartException {
        ServiceTarget target = context.getChildTarget();

        File appDir = this.directory;

        if (appDir == null) {
            appDir = new File(this.applicationsDirectoryInjector.getValue(), this.id);
        }

        if (!appDir.exists()) {
            log.debug("attempt to create: " + appDir);
            appDir.mkdirs();
        }

        if (!appDir.exists()) {
            log.error("FAILED TO CREATE: " + appDir);
        }

        log.debug(appDir + " .mkdirs: " + appDir.mkdirs() + " // " + appDir.exists());
        File applicationJson = new File(appDir, "application.json");

        String appName = this.name;
        Boolean appVisible = Boolean.TRUE;
        ResourcePath htmlApp = null;
        ResourceState resourcesTree = null;

        if (applicationJson.exists()) {
            JSONDecoder decoder = new JSONDecoder();
            try {
                ResourceState state = decoder.decode(applicationJson);
                Object value;
                if ((value = state.getProperty("name")) != null) {
                    appName = (String) value;
                }
                if ((value = state.getProperty("html-app")) != null) {
                    htmlApp = new ResourcePath((String) value);
                    htmlApp.prependSegment(this.id);
                }
                if ((value = state.getProperty("visible")) != null) {
                    appVisible = (Boolean) value;
                }
                if ((value = state.getProperty("resources")) != null) {
                    resourcesTree = (ResourceState) value;
                }
            } catch (IOException e) {
                log.error("Error decoding content of application.json for " + appName, e);
            }
        } else {
            ObjectMapper mapper = ObjectMapperFactory.create();
            ObjectWriter writer = mapper.writer().with(new DefaultPrettyPrinter("\n"));
            ObjectNode tree = JsonNodeFactory.instance.objectNode();
            tree.put("id", this.id);
            tree.put("name", appName);
            tree.put("resources", JsonNodeFactory.instance.objectNode());
            try {
                applicationJson.getParentFile().mkdirs();
                writer.writeValue(applicationJson, tree);
            } catch (Exception e) {
                log.error(e);
            }
        }

        this.app = new InternalApplication(target, this.id, appName, appDir, htmlApp, appVisible);

        ServiceName configManagerName = LiveOak.applicationConfigurationManager(this.id);
        ApplicationConfigurationService configManager = new ApplicationConfigurationService(applicationJson);
        target.addService(configManagerName, configManager)
                .install();

        ServiceName appContextName = LiveOak.applicationContext(this.id);

        // Configure application-clients resource if it's not present
        if (resourcesTree == null || !resourcesTree.getPropertyNames().contains("application-clients")) {
            ApplicationClientsInstallService appClientInstaller = new ApplicationClientsInstallService();
            target.addService(appContextName.append("app-client-install"), appClientInstaller)
                    .addDependency(configManagerName)
                    .addInjectionValue(appClientInstaller.applicationInjector(), this)
                    .install();
        }

        // context resource

        ApplicationContextService appContext = new ApplicationContextService(this.app);
        target.addService(appContextName, appContext)
                .install();
        MediaTypeMountService<ApplicationContext> appContextMount = new MediaTypeMountService<>(null, MediaType.JSON, true);
        this.app.contextController(target.addService(LiveOak.defaultMount(appContextName), appContextMount)
                .addDependency(LiveOak.GLOBAL_CONTEXT, MountPointResource.class, appContextMount.mountPointInjector())
                .addDependency(appContextName, ApplicationContext.class, appContextMount.resourceInjector())
                .install());

        // admin resource

        ServiceName appResourceName = LiveOak.applicationAdminResource(this.id);
        ApplicationResourceService appResource = new ApplicationResourceService(this.app);
        target.addService(appResourceName, appResource)
                .addDependency(configManagerName, ApplicationConfigurationManager.class, appResource.configInjector())
                .addDependency(LiveOak.APPLICATION_REGISTRY, InternalApplicationRegistry.class, appResource.registryInjector())
                .install();
        MediaTypeMountService<ApplicationResource> appResourceMount = new MediaTypeMountService<>(null, MediaType.JSON, true);
        this.app.resourceController(target.addService(LiveOak.defaultMount(appResourceName), appResourceMount)
                .addDependency(LiveOak.resource(ZeroExtension.APPLICATION_ID, "applications"), MountPointResource.class, appResourceMount.mountPointInjector())
                .addDependency(appResourceName, ApplicationResource.class, appResourceMount.resourceInjector())
                .install());

        // Only install the application-clients resource for an application if it's not currently
        ResourceState state = new DefaultResourceState();
        state.putProperty("type", "application-clients");

        // Startup all resources defined for the application
        ApplicationResourcesStartupService resources = new ApplicationResourcesStartupService(resourcesTree);

        target.addService(LiveOak.application(this.id).append("resources"), resources)
                .addInjectionValue(resources.applicationInjector(), this)
                .install();
    }

    @Override
    public void stop(StopContext context) {
        this.app = null;
    }

    @Override
    public InternalApplication getValue() throws IllegalStateException, IllegalArgumentException {
        return this.app;
    }

    public Injector<File> applicationsDirectoryInjector() {
        return this.applicationsDirectoryInjector;
    }

    private String id;
    private String name;
    private File directory;
    private InjectedValue<File> applicationsDirectoryInjector = new InjectedValue<>();
    private InternalApplication app;

    private static final Logger log = Logger.getLogger(ApplicationService.class);
}
