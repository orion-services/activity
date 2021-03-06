package dev.orion.fixture;

import dev.orion.client.UserClient;
import dev.orion.client.dto.UserClientResponse;
import dev.orion.commom.constant.UserRoles;
import dev.orion.entity.Activity;
import dev.orion.entity.User;
import dev.orion.services.dto.UserEnhancedWithExternalData;
import dev.orion.services.interfaces.UserService;
import io.quarkus.panache.mock.PanacheMock;
import lombok.val;
import net.datafaker.Faker;
import org.mockito.BDDMockito;

import java.util.*;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

final public class UserFixture {
    static public User generateUser() {
        User user = new User(UUID.randomUUID().toString());
        return user;
    }

    static public UserClientResponse generateClientResponseDto() {
        UserClientResponse userClientResponse = new UserClientResponse();
        userClientResponse.uuid = UUID.randomUUID().toString();
        userClientResponse.isActive = true;
        userClientResponse.name = Faker.instance().funnyName().name();
        userClientResponse.role = new LinkedList<>(Arrays.asList(new String[]{UserRoles.CREATOR, UserRoles.PARTICIPANT}));

        return userClientResponse;
    }

    static public UserEnhancedWithExternalData generateUserEnhancedWithExternalDataDto() {
        val user = generateUser();
        val userClientDto = generateClientResponseDto();
        userClientDto.uuid = user.getExternalId();

        return new UserEnhancedWithExternalData(user, userClientDto);
    }

    static public UserClientResponse mockUserClient(UserClient userClient, String commonUserExternalId) {
        val userClientResponse = UserFixture.generateClientResponseDto();
        userClientResponse.uuid = commonUserExternalId;
        given(userClient.getUserByExternalId(commonUserExternalId)).willReturn(userClientResponse);

        return userClientResponse;
    }

    static public UserEnhancedWithExternalData mockEnhancedUser(UserService userService, final String commonUserExternalId) {
        val userClientResponse = UserFixture.generateUserEnhancedWithExternalDataDto();

        userClientResponse.uuid = commonUserExternalId;
        userClientResponse.getUserEntity().setExternalId(commonUserExternalId);

        given(userService.getCompleteUserData(anyString()))
                .willReturn(userClientResponse);

        return userClientResponse;
    }

    public static User mockFindUserByExternalId(final String commonUserExternalId) {
        val user = UserFixture.generateUser();
        user.activity = new Activity();
        PanacheMock.mock(User.class);
        BDDMockito.given(User.findUserByExternalId(commonUserExternalId)).willReturn(Optional.of(user));

        return user;
    }

    public static LinkedHashSet<User> createParticipants(Activity activity, final Integer quantity) {
        val participants = new LinkedHashSet<User>();

        for (int i = 0; i < quantity; i++) {
            val user = generateUser();
            activity.addParticipant(user);
            participants.add(user);
        }

        return participants;
    }
}
