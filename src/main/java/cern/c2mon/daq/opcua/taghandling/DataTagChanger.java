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

@Component("tagChanger")
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

    private static void gatherIds(Stream<ISourceDataTag> tagStream, Predicate<ISourceDataTag> predicate, String successMsg, String failMsg, ChangeReport report) {
        final Map<Boolean, List<ISourceDataTag>> collect = tagStream.collect(Collectors.partitioningBy(predicate));
        collect.forEach((succeeded, itemDefinitions) -> {
            final String tagIds = tagsToIdString.apply(itemDefinitions);
            if (!tagIds.isEmpty()) {
                report.appendInfo((succeeded ? successMsg : failMsg) + tagIds + ". ");
            }
        });
    }
    private final IDataTagHandler controller;

    public void onUpdateEquipmentConfiguration(Collection<ISourceDataTag> newTags, Collection<ISourceDataTag> oldTags, final ChangeReport changeReport) {
        final Stream<ISourceDataTag> toAdd = newTags.stream().filter(t -> !oldTags.contains(t));
        final Stream<ISourceDataTag> toRemove = oldTags.stream().filter(t -> !newTags.contains(t));
        gatherIds(toRemove, controller::removeTag, "Could not remove Tags with Ids " , "Removed Tags " , changeReport);
        gatherIds(toAdd, controller::subscribeTag, "Could not subscribe to Tags with Ids ", "Subscribed to Tags ", changeReport);
    }

    @Override
    public void onAddDataTag(final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        log.info("Adding data tag {}", sourceDataTag);
        addAndReport(sourceDataTag, changeReport);
    }

    @Override
    public void onRemoveDataTag(final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        log.info("Removing data tag {}", sourceDataTag);
        removeAndReport(sourceDataTag, changeReport);
    }

    /**
     * Removes the old SourceDataTag subscription and replaced it with the new one. If the old tag cannot be removed
     * e.g. because it is missing, but the new tag can still be added, the changeReport will still report a successful
     * operation.
     * @param sourceDataTag    the Tag to add
     * @param oldSourceDataTag the Tag to remove
     * @param changeReport     a report of the taken actions and the outpome of the operation.
     */
    @Override
    public void onUpdateDataTag(final ISourceDataTag sourceDataTag, final ISourceDataTag oldSourceDataTag, final ChangeReport changeReport) {
        log.info("Updating data tag {} to {}", oldSourceDataTag, sourceDataTag);
        if (sourceDataTag.getHardwareAddress().equals(oldSourceDataTag.getHardwareAddress())) {
            applyToReport.apply(true, "The new and old sourceDataTags have the same hardware address, no update was required.", changeReport);
        } else {
            removeAndReport(oldSourceDataTag, changeReport);
            addAndReport(sourceDataTag, changeReport);
        }
    }

    private void addAndReport(final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        final boolean success = controller.subscribeTag(sourceDataTag);
        String msg = "Tag " + sourceDataTag.getName() + " with ID " + sourceDataTag.getId()
                + (success ? " was subscribed." : " could not be subscribed.");
        applyToReport.apply(success, msg, changeReport);
    }

    private void removeAndReport(final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        String msg = "Tag " + sourceDataTag.getName() + " with ID " + sourceDataTag.getId()
                + (controller.removeTag(sourceDataTag) ? " was unsubscribed." : " was not previously configured. No change required. ");
        applyToReport.apply(true, msg, changeReport);
    }

    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void apply(A a, B b, C c);
    }
}
