package dev.orion.services;

import dev.orion.commom.constant.ActivityStages;
import dev.orion.commom.constant.CircularStepFlowDirectionTypes;
import dev.orion.commom.exception.IncompleteWorkflowException;
import dev.orion.commom.exception.NotValidActionException;
import dev.orion.entity.*;
import dev.orion.entity.step_type.CircleOfWriters;
import dev.orion.entity.step_type.ReverseSnowball;
import dev.orion.fixture.UserFixture;
import dev.orion.fixture.WorkflowFixture;
import dev.orion.util.AggregateException;
import dev.orion.workflowExecutor.CircleStepExecutor;
import dev.orion.workflowExecutor.ReverseSnowBallStepExecutor;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import lombok.val;
import net.datafaker.Faker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.mockito.MockitoAnnotations;

import javax.inject.Inject;
import javax.transaction.Transactional;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

@QuarkusTest
@Transactional
public class WorkflowManageServiceTest {
    
    @Inject
    WorkflowManageServiceImpl testThis;

    @InjectMock
    CircleStepExecutor circleStepExecutor;

    @InjectMock
    ReverseSnowBallStepExecutor reverseSnowBallStepExecutor;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(circleStepExecutor.getStepRepresentation()).thenCallRealMethod();
        when(reverseSnowBallStepExecutor.getStepRepresentation()).thenCallRealMethod();
    }

    @Test
    @DisplayName("[apply] Should call the right step by activity phase")
    public void testShouldCallTheRightStepByActivityPhase() {
        User user = UserFixture.generateUser();
        user.persist();

        Activity activity = new Activity();
        activity.creator = user;
        activity.workflow = generateWorkflow();
        activity.persist();

        //  Test incoming from database
        Activity persistedActivity = Activity.findById(activity.uuid);

        testThis.apply(persistedActivity, user);
        BDDMockito.then(circleStepExecutor).should().execute(any(), any());
        BDDMockito.then(reverseSnowBallStepExecutor).should(times(0)).execute(any(), any());

        persistedActivity.actualStage = ActivityStages.DURING;
        testThis.apply(persistedActivity, user);
        BDDMockito.then(circleStepExecutor).should(times(1)).execute(any(), any());
        BDDMockito.then(reverseSnowBallStepExecutor).should().execute(any(), any());
    }

    @Test
    @DisplayName("[apply] Should call the validation of each step")
    public void testShouldCallValidationForEachStep() {
        User user = UserFixture.generateUser();
        user.persist();

        Activity activity = new Activity();
        activity.creator = user;

        //  Test incoming from database
        activity.workflow = generateWorkflow();

        activity.persist();

        Activity persistedActivity = Activity.findById(activity.uuid);

        testThis.apply(persistedActivity, user);
        persistedActivity.actualStage = ActivityStages.DURING;
        testThis.apply(persistedActivity, user);
        BDDMockito.then(circleStepExecutor).should(atLeastOnce()).validate(any(), any());
        BDDMockito.then(reverseSnowBallStepExecutor).should(atLeastOnce()).validate(any(), any());
    }

    @Test
    @DisplayName("[apply] Should throw error when a stage do not validate")
    public void testStageThrowValidation() {
        BDDMockito
                .willThrow(new NotValidActionException("reverseSnowBallStepExecutor", "error"))
                .given(reverseSnowBallStepExecutor)
                .validate(any(), any());
        BDDMockito
                .willThrow(new NotValidActionException("reverseSnowBallStepExecutor", "error"))
                .given(circleStepExecutor)
                .validate(any(), any());

        User user = UserFixture.generateUser();
        user.persist();

        Activity activity = new Activity();
        activity.creator = user;
        activity.workflow = generateWorkflow();
        activity.workflow.getStages().forEach(stage -> {
            if (stage.getStage().equals(ActivityStages.PRE)) {
                stage.addStep(new ReverseSnowball());
            }
        });

        activity.persist();

        val aggregateException = Assertions.assertThrows(AggregateException.class, () -> testThis.apply(activity, user));

        Assertions.assertEquals(2, aggregateException.getExceptions().size());
        BDDMockito.then(circleStepExecutor).should(never()).execute(any(), any());
        BDDMockito.then(reverseSnowBallStepExecutor).should(never()).execute(any(), any());
        BDDMockito.then(circleStepExecutor).should().validate(any(), any());
        BDDMockito.then(reverseSnowBallStepExecutor).should().validate(any(), any());
    }

    @Test
    @DisplayName("[apply] Should do nothing when there's no step in stage")
    public void testNoActualStageThrowValidation() {
        Activity activity = new Activity();
        activity.creator = UserFixture.generateUser();
        activity.workflow = generateWorkflow();

        activity.setActualStage(ActivityStages.AFTER);

        testThis.apply(activity, activity.getCreator());
        BDDMockito.then(circleStepExecutor).should(never()).execute(any(), any());
        BDDMockito.then(reverseSnowBallStepExecutor).should(never()).execute(any(), any());
    }

    @Test
    @DisplayName("[apply] Should throw error when workflow has no stages")
    public void testShouldThrowErrorWhenWorkflowHasNoStep() {
        Workflow workflow = new Workflow();
        Stage emptyStage = new Stage();
        emptyStage.setStage(ActivityStages.PRE);

        workflow.setName(Faker.instance().rickAndMorty().character());
        workflow.setDescription(Faker.instance().science().element());
        workflow.addStepStage(emptyStage);

        User user = UserFixture.generateUser();
        Activity activity = new Activity();
        activity.creator = user;
        activity.workflow = workflow;

        Assertions.assertThrows(IncompleteWorkflowException.class, () -> testThis.apply(activity, user));
        BDDMockito.then(reverseSnowBallStepExecutor).should(never()).execute(any(), any());
        BDDMockito.then(circleStepExecutor).should(never()).execute(any(), any());
    }

//   Workflow creation with createWorkflow
    @Test
    @DisplayName("[createOrUpdateWorkflow] Should create an workflow")
    public void testWorkflowCreation() {
        val name = Faker.instance().rickAndMorty().character();
        val description = Faker.instance().science().element();
        val stepList = List.of(new Step[]{new CircleOfWriters(CircularStepFlowDirectionTypes.FROM_BEGIN_TO_END)});
        val stages = Set.of(WorkflowFixture.generateStage(ActivityStages.DURING, stepList));

        val workflow = testThis.createOrUpdateWorkflow(stages, name, description);

        Assertions.assertNotNull(workflow);
        Assertions.assertNotNull(workflow.id);
        Assertions.assertEquals(name, workflow.getName());
        Assertions.assertEquals(description, workflow.getDescription());
        Assertions.assertFalse(workflow.getStages().isEmpty());
    }

    @Test
    @DisplayName("[createOrUpdateWorkflow] Workflow creation must have at least stage for during phase")
    public void test() {
        val name = Faker.instance().rickAndMorty().character();
        val description = Faker.instance().science().element();
        val stepList = List.of(new Step[]{new CircleOfWriters(CircularStepFlowDirectionTypes.FROM_BEGIN_TO_END)});
        val stages = Set.of(WorkflowFixture.generateStage(ActivityStages.PRE, stepList));

        Assertions.assertThrows(IncompleteWorkflowException.class, () -> testThis.createOrUpdateWorkflow(stages, name, description));
    }

    @Test
    @DisplayName("[createOrUpdateWorkflow] Should update an workflow")
    public void testWorkflowUpdate() {
        val name = Faker.instance().rickAndMorty().character();
        val description = Faker.instance().science().element();
        val stepList = List.of(new Step[]{new CircleOfWriters(CircularStepFlowDirectionTypes.FROM_BEGIN_TO_END)});
        val stages = Set.of(WorkflowFixture.generateStage(ActivityStages.DURING, stepList));

        var workflow = testThis.createOrUpdateWorkflow(stages, name, description);
        val expectedId = workflow.id;
        val workflowName = workflow.getName();

        val newWorkflow = testThis.createOrUpdateWorkflow(stages, workflowName, Faker.instance().science().scientist());

        newWorkflow.setName(Faker.instance().rickAndMorty().character());
        Assertions.assertNotNull(newWorkflow);
        Assertions.assertEquals(expectedId, newWorkflow.id);
        Assertions.assertNotEquals(newWorkflow.getDescription(), description);
    }

    private Workflow generateWorkflow() {
        val workflow = new Workflow();
        workflow.setName(Faker.instance().rickAndMorty().character());
        workflow.setDescription(Faker.instance().science().element());
        workflow.addStepStage(generateStage(ActivityStages.PRE));
        workflow.addStepStage(generateStage(ActivityStages.DURING));
        workflow.persist();

        return workflow;
    }

    private Stage generateStage(ActivityStages activityStages) {
        val stage = new Stage();
        stage.setStage(activityStages);
        if (activityStages.equals(ActivityStages.PRE)) {
            stage.addStep(new CircleOfWriters(CircularStepFlowDirectionTypes.FROM_BEGIN_TO_END));
            return stage;
        }
        stage.addStep(new ReverseSnowball());
        return stage;
    }
}
