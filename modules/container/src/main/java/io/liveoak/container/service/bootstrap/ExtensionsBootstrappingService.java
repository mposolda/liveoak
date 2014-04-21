package io.liveoak.container.service.bootstrap;

import io.liveoak.container.extension.ExtensionInstaller;
import io.liveoak.container.extension.ExtensionLoader;
import io.liveoak.container.zero.extension.ZeroExtension;
import io.liveoak.container.zero.service.ZeroBootstrapper;
import io.liveoak.spi.LiveOak;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.*;
import org.jboss.msc.value.ImmediateValue;
import org.jboss.msc.value.InjectedValue;

import java.io.File;

import static io.liveoak.spi.LiveOak.EXTENSION_INSTALLER;
import static io.liveoak.spi.LiveOak.EXTENSION_LOADER;

/**
 * @author Bob McWhirter
 */
public class ExtensionsBootstrappingService implements Service<Void> {

    @Override
    public void start(StartContext context) throws StartException {
        ServiceTarget target = context.getChildTarget();


        ExtensionLoader extensionLoader = new ExtensionLoader(new File( this.extensionsDirectoryInjector.getValue()).getAbsoluteFile());

        target.addService(EXTENSION_LOADER, extensionLoader)
                .addDependency(EXTENSION_INSTALLER, ExtensionInstaller.class, extensionLoader.extensionInstallerInjector())
                .install();


        ExtensionInstaller installer = new ExtensionInstaller(target, LiveOak.resource(ZeroExtension.APPLICATION_ID, "system"));
        target.addService(EXTENSION_INSTALLER, new ValueService<ExtensionInstaller>(new ImmediateValue<>(installer)))
                .install();

        ZeroBootstrapper zero = new ZeroBootstrapper();

        target.addService(LiveOak.LIVEOAK.append("zero", "bootstrapper"), zero)
                .addDependency(EXTENSION_INSTALLER, ExtensionInstaller.class, zero.extensionInstallerInjector())
                .install();
    }

    @Override
    public void stop(StopContext context) {

    }

    @Override
    public Void getValue() throws IllegalStateException, IllegalArgumentException {
        return null;
    }

    public Injector<String> extensionsDirectoryInjector() {
        return this.extensionsDirectoryInjector;
    }

    private InjectedValue<String> extensionsDirectoryInjector = new InjectedValue<>();
}
