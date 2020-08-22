package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.common.conf.equipment.IDataTagChanger;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.daq.config.ChangeReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.FAIL;
import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.SUCCESS;

/**
 * The {@link DataTagChanger} handles changes  at runtime to the {@link ISourceDataTag}s in  {@link
 * cern.c2mon.shared.common.process.IEquipmentConfiguration}.
 */
@Component("dataTagChanger")
@RequiredArgsConstructor
@Slf4j
public class DataTagChanger implements IDataTagChanger {

    private static final TriConsumer<Boolean, String, ChangeReport> applyToReport = (success, message, report) -> {
        final UnaryOperator<String> prevMsg = s -> s == null ? "" : s;
        if (success) {
            report.appendInfo(prevMsg.apply(report.getInfoMessage()) + message);
            report.setState(SUCCESS);
        } else {
            log.error(message);
            report.appendError(prevMsg.apply(report.getErrorMessage()) + message);
            report.setState(FAIL);
        }
    };

    private static final Function<Collection<ISourceDataTag>, String> tagsToIdString = tags -> tags.stream()
            .map(t -> t.getId().toString()).collect(Collectors.joining(", "));

    private final IDataTagHandler tagHandler;

    private static void gatherIds(Stream<ISourceDataTag> tagStream, Predicate<ISourceDataTag> predicate, String successMsg, String failMsg, ChangeReport report) {
        final Map<Boolean, List<ISourceDataTag>> collect = tagStream.collect(Collectors.partitioningBy(predicate));
        collect.forEach((succeeded, itemDefinitions) -> {
            final String tagIds = tagsToIdString.apply(itemDefinitions);
            if (!tagIds.isEmpty()) {
                report.appendInfo((succeeded ? successMsg : failMsg) + tagIds + ". ");
            }
        });
    }

    /**
     * Add and remove the changes tags in a batch operation such the server goes from the state described in oldTags to
     * the one in newTag
     * @param newTags      all {@link ISourceDataTag}s in the new configuration
     * @param oldTags      all {@link ISourceDataTag}s in the old configuration
     * @param changeReport a report of the performed operations and their successes.
     */
    public void onUpdateEquipmentConfiguration(Collection<ISourceDataTag> newTags, Collection<ISourceDataTag> oldTags, final ChangeReport changeReport) {
        final Stream<ISourceDataTag> toAdd = newTags.stream().filter(t -> !oldTags.contains(t));
        final Stream<ISourceDataTag> toRemove = oldTags.stream().filter(t -> !newTags.contains(t));
        gatherIds(toRemove, tagHandler::removeTag, "Removed Tags ", "Could not remove Tags with Ids ", changeReport);
        gatherIds(toAdd, tagHandler::subscribeTag, "Subscribed to Tags ", "Could not subscribe to Tags with Ids ", changeReport);
    }

    /**
     * Subscribes to the {@link org.eclipse.milo.opcua.stack.core.types.builtin.NodeId} associated with the {@link
     * ISourceDataTag} on the data source
     * @param sourceDataTag the {@link ISourceDataTag} that was added to the configuration
     * @param changeReport  a report of outcome of the operation
     */
    @Override
    public void onAddDataTag(final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        log.info("Adding data tag {} with ID {}.", sourceDataTag.getName(), sourceDataTag.getId());
        addAndReport(sourceDataTag, changeReport);
    }

    /**
     * Removes the subscription to the {@link org.eclipse.milo.opcua.stack.core.types.builtin.NodeId} associated with
     * the {@link ISourceDataTag} from the data source.
     * @param sourceDataTag the {@link ISourceDataTag} that was removed from the configuration
     * @param changeReport  a report of the outcome of the operation
     */
    @Override
    public void onRemoveDataTag(final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        log.info("Removing data tag {} with ID {}.", sourceDataTag.getName(), sourceDataTag.getId());
        removeAndReport(sourceDataTag, changeReport);
    }

    /**
     * Removes the old SourceDataTag subscription and replaced it with the new one. If the old tag cannot be removed
     * e.g. because it is missing, but the new tag can still be added, the changeReport will still report a successful
     * operation.
     * @param sourceDataTag    the Tag to add
     * @param oldSourceDataTag the Tag to remove
     * @param changeReport     a report of the taken actions and the outcome of the operation.
     */
    @Override
    public void onUpdateDataTag(final ISourceDataTag sourceDataTag, final ISourceDataTag oldSourceDataTag, final ChangeReport changeReport) {
        log.info("Updating data tag {}  with ID {}.", oldSourceDataTag.getName(), oldSourceDataTag.getId());
        if (sourceDataTag.getHardwareAddress().equals(oldSourceDataTag.getHardwareAddress())) {
            applyToReport.apply(true, "The new and old sourceDataTags have the same hardware address, no update was required.", changeReport);
        } else {
            removeAndReport(oldSourceDataTag, changeReport);
            addAndReport(sourceDataTag, changeReport);
        }
    }

    private void addAndReport(final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        final boolean success = tagHandler.subscribeTag(sourceDataTag);
        String msg = "Tag " + sourceDataTag.getName() + " with ID " + sourceDataTag.getId()
                + (success ? " was subscribed." : " could not be subscribed.");
        applyToReport.apply(success, msg, changeReport);
    }

    private void removeAndReport(final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        String msg = "Tag " + sourceDataTag.getName() + " with ID " + sourceDataTag.getId()
                + (tagHandler.removeTag(sourceDataTag) ? " was unsubscribed." : " was not previously configured. No change required. ");
        applyToReport.apply(true, msg, changeReport);
    }

    @FunctionalInterface
    private interface TriConsumer<A, B, C> {
        void apply(A a, B b, C c);
    }
}
