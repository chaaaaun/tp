package scrolls.elder.logic.commands;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import scrolls.elder.commons.core.index.Index;
import scrolls.elder.commons.util.ToStringBuilder;
import scrolls.elder.logic.Messages;
import scrolls.elder.logic.commands.exceptions.CommandException;
import scrolls.elder.model.LogStore;
import scrolls.elder.model.Model;
import scrolls.elder.model.PersonStore;
import scrolls.elder.model.log.Log;
import scrolls.elder.model.person.*;
import scrolls.elder.model.tag.Tag;


/**
 * Deletes the log identified using its displayed index from the address book.
 */
public class LogDeleteCommand extends Command {

    public static final String COMMAND_WORD_LOG_DELETE = "logdelete";
    public static final String COMMAND_WORD_LOG_DEL = "logdel";
    public static final String COMMAND_WORD_LOG_RM = "logrm";
    public static final String COMMAND_WORD_LOG_REMOVE = "logremove";

    public static final String MESSAGE_USAGE = COMMAND_WORD_LOG_DELETE
            + ": Deletes the log identified by the index number used in the displayed log list.\n"
            + "Parameters: INDEX (must be a positive integer)\n"
            + "Example: " + COMMAND_WORD_LOG_DELETE + " 1\n"
            + "Alternatively, you can also delete a log using the following commands as well.\n"
            + "Example: " + COMMAND_WORD_LOG_DEL + " 1\n"
            + "Example: " + COMMAND_WORD_LOG_RM + " 1\n"
            + "Example: " + COMMAND_WORD_LOG_REMOVE + " 1";

    public static final String MESSAGE_DELETE_LOG_SUCCESS = "Deleted Log: %1$s";
    public static final String MESSAGE_DELETE_LOG_ERROR = "Unable to delete log: ";
    private final Index targetIndex;

    /**
     * Creates a LogDeleteCommand to delete the log at the specified {@code targetIndex}.
     */
    public LogDeleteCommand(Index targetIndex) {
        this.targetIndex = targetIndex;
    }

    @Override
    public CommandResult execute(Model model) throws CommandException {
        requireNonNull(model);

        LogStore logStore = model.getMutableDatastore().getMutableLogStore();
        PersonStore personStore = model.getMutableDatastore().getMutablePersonStore();

        List<Log> lastShownList = logStore.getLogList();
        List<Person> lastShownPList = personStore.getPersonList();

        if (targetIndex.getZeroBased() >= lastShownList.size()) {
            throw new CommandException(MESSAGE_DELETE_LOG_ERROR + Messages.MESSAGE_INVALID_LOG_DISPLAYED_INDEX);
        }

        Log logToDelete = lastShownList.get(targetIndex.getZeroBased());
        int logIdToDelete = logToDelete.getLogId();

        // Update the timeServed of the volunteer and befriendee
        Person volunteer = lastShownPList.get(logToDelete.getVolunteerId());
        Person befriendee = lastShownPList.get(logToDelete.getBefriendeeId());

        int durationToDelete = logToDelete.getDuration();
        Person updatedVolunteer = updatePersonWithNewTimeServed(volunteer, durationToDelete);
        Person updatedBefriendee = updatePersonWithNewTimeServed(befriendee, durationToDelete);
        personStore.setPerson(volunteer, updatedVolunteer);
        personStore.setPerson(befriendee, updatedBefriendee);
        personStore.updateFilteredPersonList(Model.PREDICATE_SHOW_ALL);

        logStore.removeLog(logIdToDelete);
        model.commitDatastore();
        return new CommandResult(String.format(MESSAGE_DELETE_LOG_SUCCESS, Messages.formatLog(logToDelete)));
    }

    private Person updatePersonWithNewTimeServed(Person p, int duration) {
        assert p != null;

        Name name = p.getName();
        Phone phone = p.getPhone();
        Email email = p.getEmail();
        Address address = p.getAddress();
        Set<Tag> tags = p.getTags();
        Role role = p.getRole();
        Optional<Name> pairedWithName = p.getPairedWithName();
        Optional<Integer> pairedWithId = p.getPairedWithId();
        int updatedTimeServed = p.getTimeServed() - duration;

        return PersonFactory.withIdFromParams(p.getPersonId(), name, phone, email, address, role, tags, pairedWithName,
                pairedWithId, updatedTimeServed);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        //instanceof handles nulls
        if (!(other instanceof LogDeleteCommand)) {
            return false;
        }

        LogDeleteCommand otherLogDeleteCommand = (LogDeleteCommand) other;
        return targetIndex.equals(otherLogDeleteCommand.targetIndex);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .add("targetIndex", targetIndex)
                .toString();
    }
}
