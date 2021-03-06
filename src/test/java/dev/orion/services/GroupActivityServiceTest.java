package dev.orion.services;

import dev.orion.client.DocumentClient;
import dev.orion.client.dto.CreateDocumentResponse;
import dev.orion.commom.constant.ActivityStage;
import dev.orion.commom.exception.UserInvalidOperationException;
import dev.orion.entity.Activity;
import dev.orion.entity.Document;
import dev.orion.entity.GroupActivity;
import dev.orion.entity.User;
import dev.orion.entity.step_type.SendEmailStep;
import dev.orion.fixture.UserFixture;
import dev.orion.fixture.WorkflowFixture;
import dev.orion.services.interfaces.DocumentService;
import dev.orion.services.interfaces.GroupService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import lombok.val;
import net.datafaker.Faker;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.spi.NotImplementedYetException;
import org.junit.jupiter.api.*;
import org.mockito.BDDMockito;
import org.mockito.MockitoAnnotations;

import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.NotFoundException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;

@QuarkusTest
@Transactional
public class GroupActivityServiceTest {

    @Inject
    GroupService testThis;

    @InjectMock
    @RestClient
    DocumentClient documentClient;

    @Inject
    DocumentService documentService;


    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        val createDocumentResponse = new CreateDocumentResponse(
                UUID.randomUUID().toString(),
                Faker.instance().backToTheFuture().quote());

        BDDMockito.given(documentClient.createDocument(any())).willReturn(createDocumentResponse);
    }

    //    User scenarios tests
    @Test
    @DisplayName("[addUserToGroup] Should add user to the group")
    public void testAddToTheGroup() {
        val activity = new Activity();
        val user = UserFixture.generateUser();

        injectUserInActivity(activity, user);
        injectWorkflowInActivity(activity);

        val document = spy(documentService.createDocument(UUID.randomUUID(), "", Set.of(user)));

        val group = testThis.createGroup(activity);
        val spyGroup = spy(group);
        activity.groupActivities.add(group);

        testThis.addUserToGroup(spyGroup, user, document);

        Assertions.assertEquals(group.getParticipants().size(), 1);
        Assertions.assertEquals(user.getGroupActivity(), spyGroup);
        Assertions.assertTrue(document.getParticipantsAssigned().contains(user));
        Assertions.assertTrue(group.getDocuments().contains(document));

        BDDMockito.then(document).should().addParticipant(user);
        BDDMockito.then(spyGroup).should().addParticipant(user);
        BDDMockito.then(spyGroup).should().addDocument(document);
    }

    @Test
    @DisplayName("[addUserToGroup] Should add user to the group by group UUID")
    public void testAddToTheGroupByUuid() {
        val activity = new Activity();
        val user = UserFixture.generateUser();

        injectUserInActivity(activity, user);
        injectWorkflowInActivity(activity);

        val document = spy(documentService.createDocument(UUID.randomUUID(), "", Set.of(user)));

        val group = testThis.createGroup(activity);
        group.persist();
        activity.groupActivities.add(group);

        testThis.addUserToGroup(group.getUuid(), user, document);

        Assertions.assertEquals(group.getParticipants().size(), 1);
        Assertions.assertEquals(user.getGroupActivity(), group);
        Assertions.assertTrue(document.getParticipantsAssigned().contains(user));
        Assertions.assertTrue(group.getDocuments().contains(document));

        BDDMockito.then(document).should().addParticipant(user);
    }

    @Test
    @DisplayName("[addUserToGroup] Should throw when group is not found")
    public void testValidateFindGroupByUuidWhenAddUserToGroup() {
        val user = UserFixture.generateUser();
        user.persist();

        val document = spy(documentService.createDocument(UUID.randomUUID(), "", Set.of(user)));
        val groupUUID = UUID.randomUUID();
        val exceptionMessage = Assertions.assertThrows(NotFoundException.class, () ->
                testThis.addUserToGroup(groupUUID, user, document)).getMessage();

        val expectedExceptionMessage = MessageFormat.format("Group {0} not found", groupUUID);
        Assertions.assertEquals(expectedExceptionMessage, exceptionMessage);
        BDDMockito.then(document).should(never()).setGroupActivity(any());
    }

    @Test
    @DisplayName("[addUserToGroup] Should add user to the group and not re-add an already added document")
    public void testAddToTheGroupWithSameDocument() {
        val activity = new Activity();
        val user = UserFixture.generateUser();
        user.persist();

        injectUserInActivity(activity, user);
        injectWorkflowInActivity(activity);

        val document = spy(documentService.createDocument(UUID.randomUUID(), "", Set.of(user)));

        val group = testThis.createGroup(activity);
        val spyGroup = spy(group);

        activity.groupActivities.add(group);
        group.addDocument(document);
        document.setGroupActivity(group);

        testThis.addUserToGroup(spyGroup, user, document);

        BDDMockito.then(document).should().addParticipant(user);
        BDDMockito.then(spyGroup).should().addParticipant(user);
        BDDMockito.then(spyGroup).should(never()).addDocument(document);
    }

    @Test
    @DisplayName("[addUserToGroup] Should throw exception when try to add an user that is already in another group")
    public void testAddUserInMoreThanOneGroup() {
        val activity = new Activity();
        val user = UserFixture.generateUser();
        user.persist();

        injectUserInActivity(activity, user);
        injectWorkflowInActivity(activity);

        val document = spy(documentService.createDocument(UUID.randomUUID(), "", Set.of(user)));

        val groupOrigin = testThis.createGroup(activity);
        val groupDestination = testThis.createGroup(activity);

        activity.groupActivities.add(groupOrigin);
        activity.groupActivities.add(groupDestination);

        testThis.addUserToGroup(groupOrigin, user, document);
        String errorMessage = Assertions.assertThrows(UserInvalidOperationException.class, () -> {
            testThis.addUserToGroup(groupDestination, user, document);
        }).getMessage();
        Assertions.assertEquals(MessageFormat.format("There are {0} users that are already in another group", 1), errorMessage);
        Assertions.assertNotEquals(user.getGroupActivity(), groupDestination);
    }

    @Test
    @DisplayName("[addUserToGroup] Should not let add participant if group is full")
    public void testAddToFullGroupValidation() {
        val activity = new Activity();
        val user = UserFixture.generateUser();
        injectUserInActivity(activity, user);
        injectWorkflowInActivity(activity);

        val document = spy(documentService.createDocument(UUID.randomUUID(), "", Set.of(user)));

        val group = testThis.createGroup(activity);
        testThis.addUserToGroup(group, user, document);

        val secondUser = UserFixture.generateUser();
        activity.addParticipant(secondUser);

        String message = Assertions.assertThrows(UserInvalidOperationException.class, () -> {
            testThis.addUserToGroup(group, secondUser, document);
        }).getMessage();

        String expectedErrorMessage = MessageFormat.format("There are {0} users that can''t be placed on group {1} because its above the capacity", 1, group.getUuid());
        Assertions.assertEquals(expectedErrorMessage, message);
        BDDMockito.then(document).should(atMostOnce()).addParticipant(any());
    }

    @Test
    @DisplayName("[addUserToGroup] Should not let add participant if they not belongs to same activity that owns group")
    public void testAddUserThatNotBelongToActivityOwnerValidation() {
        val activity = new Activity();
        val mainUser = UserFixture.generateUser();
        val document = spy(new Document());

        val group = testThis.createGroup(activity);
        group.setCapacity(2);

        activity.groupActivities.add(group);

        String message = Assertions.assertThrows(UserInvalidOperationException.class, () -> {
            testThis.addUserToGroup(group, mainUser, document);
        }).getMessage();
        String expectedErrorMessage = MessageFormat
                .format("There are {0} users that can''t be placed on group {1} because it not belongs to activity {2}",
                        1, group.getUuid(), activity.uuid);
        Assertions.assertEquals(expectedErrorMessage, message);
    }


//  GROUP REMOVE USER FROM GROUP
    @Test
    @DisplayName("[removeUserFromGroup] Should remove user from group")
    public void testRemoveUser() {
        val activity = new Activity();
        val user = UserFixture.generateUser();
        injectUserInActivity(activity, user);
        injectWorkflowInActivity(activity);

        val document = spy(documentService.createDocument(UUID.randomUUID(), "", Set.of(user)));
        val group = spy(testThis.createGroup(activity));
        testThis.addUserToGroup(group, user, document);

        Assertions.assertTrue(document.getParticipantsAssigned().contains(user));
        Assertions.assertTrue(group.getParticipants().contains(user));

        testThis.removeUserFromGroup(activity, user);
        Assertions.assertFalse(document.getParticipantsAssigned().contains(user));
        Assertions.assertFalse(group.getParticipants().contains(user));

        BDDMockito.then(document).should().removeParticipant(user);
        BDDMockito.then(group).should().removeParticipant(user);
    }

    @Test
    @DisplayName("[removeUserFromGroup] Should delete group after last user is removed")
    public void testEmptyGroupAfterRemove() {
        val activity = new Activity();
        val user = UserFixture.generateUser();
        injectUserInActivity(activity, user);
        injectWorkflowInActivity(activity);

        val document = documentService.createDocument(UUID.randomUUID(), "", Set.of(user));
        val group = testThis.createGroup(activity);
        testThis.addUserToGroup(group, user, document);
        testThis.removeUserFromGroup(activity, user);

        Assertions.assertFalse(activity.getGroupActivities().contains(group));

        Assertions.assertEquals(0, GroupActivity.find("uuid", group.getUuid()).count());
        Assertions.assertNull(document.getGroupActivity());
    }

    @Test
    @DisplayName("[removeUserFromGroup] Test not clean group that has another groups")
    public void testNotEmptyGroupAfterRemove() {
        val activity = new Activity();
        val user = UserFixture.generateUser();
        val user2 = UserFixture.generateUser();
        injectUserInActivity(activity, user);
        injectUserInActivity(activity, user2);
        injectWorkflowInActivity(activity);

        val document = spy(documentService.createDocument(UUID.randomUUID(), "", Set.of(user)));
        val group = testThis.createGroup(activity);
        testThis.addUserToGroup(group, user, document);
        testThis.addUserToGroup(group, user2, document);
        testThis.removeUserFromGroup(activity, user);

        Assertions.assertTrue(activity.getGroupActivities().contains(group));

        Assertions.assertEquals(1L, GroupActivity.find("uuid", group.getUuid()).count());
        Assertions.assertNotNull(document.getGroupActivity());
        BDDMockito.then(document).should(never()).setGroupActivity(null);
    }

//  GROUP transferUserToGroup
    @Test
    @DisplayName("Should transfer an user between groups")
    @Disabled
    public void testTransferUserBetweenGroups() {
        throw new NotImplementedYetException();
    }

    @Test
    @DisplayName("Should not transfer an user if it's not present in activity")
    @Disabled
    public void testTransferUserNotBelongingToActivityBetweenGroupsValidation() {
        throw new NotImplementedYetException();
    }

    @Test
    @DisplayName("Should delete group from activity if there's no more users in group")
    @Disabled
    public void testTransferLastUserFromGroup() {
        throw new NotImplementedYetException();
    }

    @Test
    @DisplayName("Should not let transfer from group if destination group is full")
    @Disabled
    public void testTransferUserToFullGroup() {
        throw new NotImplementedYetException();
    }

    //    Capacity scenarios
    @Test
    @DisplayName("[createGroup] Should create a group with list same capacity number of activity participants")
    public void testGroupCapacityAsSameActivityParticipantNumber() {
        val activity = new Activity();
        val user = UserFixture.generateUser();
        val user1 = UserFixture.generateUser();
        val user2 = UserFixture.generateUser();

        injectUserInActivity(activity, user);
        injectWorkflowInActivity(activity);

        activity.addParticipant(user1);
        activity.addParticipant(user2);
        activity.persistAndFlush();

        val group = testThis.createGroup(activity, Set.of(new User[]{user, user1, user2}));
        group.persist();

        Assertions.assertEquals(3, group.getCapacity());
        Assertions.assertEquals(activity.getParticipants().size(), group.getCapacity());
    }

    @Test
    @DisplayName("Should create a group with capacity with same number of provided user list")
    public void testGroupCapacityAsSameProvidedUserList() {
        val activity = new Activity();
        val author = UserFixture.generateUser();

        var users = generateSetUsers();
        activity.getParticipants().addAll(users);
        injectUserInActivity(activity, author);
        injectWorkflowInActivity(activity);

        activity.persist();

        val group = testThis.createGroup(activity, users);

        Assertions.assertEquals(users.size(), group.getParticipants().size());
        Assertions.assertEquals(users.size(), group.getCapacity());
    }

    @Test
    @DisplayName("[changeGroupCapacity] Should change capacity of group")
    public void testChangeGroupCapacity() {
        val activity = new Activity();
        val author = UserFixture.generateUser();

        injectUserInActivity(activity, author);
        injectWorkflowInActivity(activity);

        val users = generateSetUsers();
        activity.getParticipants().addAll(users);

        users.add(author);
        val group = testThis.createGroup(activity, users);
        group.persist();

        val newCapacity = users.size();
        testThis.changeGroupCapacity(activity, group, newCapacity);
        users.size(); group.getCapacity();
        Assertions.assertEquals(newCapacity, group.getCapacity());
    }

    @Test
    @DisplayName("[changeGroupCapacity] Should not change capacity when the new quantity is less than the number of GROUP participants")
    public void testChangeGroupCapacityBelowParticipantsNumberValidation() {
        val activity = new Activity();
        val user = UserFixture.generateUser();

        injectUserInActivity(activity, user);
        injectWorkflowInActivity(activity);

        val users = generateSetUsers();
        activity.getParticipants().addAll(users);

        val group = testThis.createGroup(activity, users);
        group.persist();
        val groupCapacityHolder = group.getCapacity();

        val newCapacity = 2;
        val errorMessage = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            testThis.changeGroupCapacity(activity, group, newCapacity);
        }).getMessage();

        String expectedMessage = MessageFormat.format("Capacity {0} is less than the number of group {1} participants ({2})", newCapacity, group.getUuid(), group.getParticipants().size());
        Assertions.assertEquals(expectedMessage, errorMessage);
        Assertions.assertEquals(groupCapacityHolder, group.getCapacity());
    }

    @Test
    @DisplayName("[changeGroupCapacity] Should not change capacity when the new quantity is higher than the number of ACTIVITY participants")
    public void testChangeGroupCapacityAboveOfActivityParticipants() {
        val activity = new Activity();
        val user = UserFixture.generateUser();

        injectUserInActivity(activity, user);
        injectWorkflowInActivity(activity);

        val users = generateSetUsers();
        activity.getParticipants().addAll(users);
        activity.persist();

        val group = testThis.createGroup(activity, users);
        val groupCapacityHolder = group.getCapacity();

        val overCapacity = activity.getParticipants().size() + 1;

        val errorMessage = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            testThis.changeGroupCapacity(activity, group, overCapacity);
        }).getMessage();

        String expectedMessage = MessageFormat.format("Capacity {0} is more than the number of activity {1} participants ({2})", overCapacity, activity.getUuid(), activity.getParticipants().size());
        Assertions.assertEquals(expectedMessage, errorMessage);
        Assertions.assertEquals(groupCapacityHolder, group.getCapacity());
    }


    @Test
    @DisplayName("Should add document when create a group with list")
    public void testAddDocumentAndUsers() {
        val activity = new Activity();
        val user = UserFixture.generateUser();

        injectUserInActivity(activity, user);
        injectWorkflowInActivity(activity);

        val users = generateSetUsers();
        activity.getParticipants().addAll(users);
        activity.persist();

        val group = testThis.createGroup(activity, users);

        Assertions.assertFalse(group.getDocuments().isEmpty());
    }

    @Test
    @DisplayName("[transferUserToGroup] Not implemented yet")
    public void testNotImplementedDisconnectUserFromActivity() {
        Assertions.assertThrows(RuntimeException.class, () -> testThis.transferUserToGroup(new Activity(), new User(), new GroupActivity()));
    }

    private Set<User> generateSetUsers() {
        return Stream.of(new User[]{
                UserFixture.generateUser(), UserFixture.generateUser(),
                UserFixture.generateUser(), UserFixture.generateUser(),
                UserFixture.generateUser(), UserFixture.generateUser()}).collect(Collectors.toSet());
    }

    private void injectWorkflowInActivity(Activity activity) {
        val workflow = WorkflowFixture.generateWorkflow(
                List.of(WorkflowFixture.generateStage(
                        ActivityStage.PRE, List.of(new SendEmailStep()))));

        activity.setWorkflow(workflow);
    }

    private void injectUserInActivity(Activity activity, User user) {
        activity.addParticipant(user);
        activity.setCreator(user);
    }
}
