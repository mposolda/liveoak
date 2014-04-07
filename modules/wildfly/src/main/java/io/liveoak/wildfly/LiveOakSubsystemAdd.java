package io.liveoak.wildfly;

import io.liveoak.wildfly.services.LiveOakSystemService;
import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceController;

import java.util.List;

/**
 * @author Bob McWhirter
 */
public class LiveOakSubsystemAdd extends AbstractBoottimeAddStepHandler {
    static final LiveOakSubsystemAdd INSTANCE = new LiveOakSubsystemAdd();

    private final Logger log = Logger.getLogger(LiveOakSubsystemAdd.class);

    private LiveOakSubsystemAdd() {
    }

    /** {@inheritDoc} */
    @Override
    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
        model.setEmptyObject();
    }

    /** {@inheritDoc} */
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

        //context.getServiceTarget().addService(SimpleService.NAME, new SimpleService()).install();
        context.getServiceTarget().addService(LiveOakSystemService.NAME, new LiveOakSystemService() ).install();
    }
}
