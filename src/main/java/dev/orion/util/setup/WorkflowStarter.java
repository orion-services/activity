package dev.orion.util.setup;

import dev.orion.commom.constant.ActivityStage;
import dev.orion.entity.Stage;
import dev.orion.entity.step_type.UnorderedCircleOfWriters;
import dev.orion.services.interfaces.WorkflowManageService;
import io.quarkus.arc.log.LoggerName;
import io.quarkus.runtime.StartupEvent;
import lombok.val;
import net.datafaker.Faker;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.text.MessageFormat;
import java.util.Set;

@ApplicationScoped
public class WorkflowStarter {
    @LoggerName("WorkflowStarter")
    Logger LOGGER;

    @Inject
    WorkflowManageService manageService;

    public static final String GENERIC_WORKFLOW_NAME =  "simple_workflow";

    void onStart(@Observes StartupEvent ev) {
        LOGGER.info("Creating or updating workflows");

        val stages = generateMockStages();
        val name = GENERIC_WORKFLOW_NAME;
        val description = Faker.instance().yoda().quote();

        val workflow = manageService.createOrUpdateWorkflow(stages, name, description);

        LOGGER.info(MessageFormat.format("Created workflow with name {0}", workflow.getName()));
    }

    private Set<Stage> generateMockStages() {
        val stage = new Stage();
        stage.setActivityStage(ActivityStage.DURING);
        stage.addStep(new UnorderedCircleOfWriters());

        return Set.of(stage);
    }
}
