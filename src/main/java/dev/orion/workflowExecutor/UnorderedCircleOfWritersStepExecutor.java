package dev.orion.workflowExecutor;

import dev.orion.commom.exception.NotValidActionException;
import dev.orion.entity.*;
import dev.orion.entity.step_type.UnorderedCircleOfWriters;
import dev.orion.services.interfaces.DocumentService;
import io.quarkus.arc.log.LoggerName;
import lombok.val;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.text.MessageFormat;
import java.util.Objects;

@ApplicationScoped
public class UnorderedCircleOfWritersStepExecutor implements StepExecutor {
    @LoggerName("UnorderedCircleOfWriterStepExecutor")
    Logger logger;
    @Inject
    DocumentService documentService;

    @Override
    public void execute(Document document, User user, Step step) {
        documentNullValidation(document);

        val unorderedCircleOfWriter = (UnorderedCircleOfWriters) step;

        val groupActivity = user.getGroupActivity();
        if (!document.getParticipantsAssigned().contains(user)) {
            logger.warnv("User {0} is not a participant in document {1}", user.getExternalId(), document.getExternalId());
            return;
        }

        documentService.moveParticipantToEditedList(document, user);
        advanceDocumentRoundAndResetForNextRound(document, groupActivity, unorderedCircleOfWriter);
    }

    private void advanceDocumentRoundAndResetForNextRound(Document document, GroupActivity groupActivity, UnorderedCircleOfWriters unorderedCircleOfWriters) {
        if (isFinalRound(document, unorderedCircleOfWriters)) {
            return;
        }

        if (doesAllParticipantsHaveParticipated(document)) {
            val newRoundValue = document.getRounds() + 1;
            logger.infov("The document {0} will advance the round from {1} to {2}", document.getExternalId(), document.getRounds(), newRoundValue);
            document.setRounds(newRoundValue);
            documentService.moveAllUsersFromEditedToParticipantList(document.getExternalId());
        }

    }

    private boolean isFinalRound(Document document, UnorderedCircleOfWriters unorderedCircleOfWriters) {
        return unorderedCircleOfWriters.getRounds() <= document.getRounds();
    }

    private boolean doesAllParticipantsHaveParticipated(Document document) {
        return document.getParticipantsAssigned().isEmpty();
    }

    @Override
    public void validate(Document document, User user, Step step) {
        documentNullValidation(document);

        if (!document.getParticipantsAssigned().contains(user)) {
            val exceptionMessage = MessageFormat.format("User {0} is not a participant in document {1}", user.getExternalId(), document.getExternalId());
            throw new NotValidActionException(getStepRepresentation(), exceptionMessage);
        }
    }

    @Override
    public <T extends Step> boolean isFinished(Activity activity, T step) throws NotValidActionException {
        val documents = Document.findAllByGroupActivity(activity.uuid);
        if (documents.isEmpty()) {
            throw new NotValidActionException(getStepRepresentation(), "document must not be null");
        }
        val document = documents.get(0);
        return doesAllParticipantsHaveParticipated(document) && isFinalRound(document, (UnorderedCircleOfWriters) step);

    }


    private void documentNullValidation(Document document) {
        if (Objects.isNull(document)) {
            val exceptionMessage = "document must not be null";
            throw new NotValidActionException(getStepRepresentation(), exceptionMessage);
        }
    }

    @Override
    public String getStepRepresentation() {
        return new UnorderedCircleOfWriters().getStepType();
    }
}