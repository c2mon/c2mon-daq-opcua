package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.common.conf.equipment.IDataTagChanger;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.daq.config.ChangeReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Function;

import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.FAIL;
import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.SUCCESS;

@Component("tagChanger")
@RequiredArgsConstructor
@Slf4j
public class TagChanger  implements IDataTagChanger  {

    private final Controller controller;

    @Override
    public void onAddDataTag(final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        addDataTagFutureAndReturnFuture(sourceDataTag, changeReport);
    }

    @Override
    public void onRemoveDataTag(final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        removeDataTagFutureAndReturnFuture(sourceDataTag, changeReport);
    }

    /**
     * Removes the old SourceDataTag subscription and replaced it with the new one. If the old tag cannot be removed e.g. because it is missing, but the new tag can still be added, the changeReport will still report a successful operation.
     * @param sourceDataTag the Tag to add
     * @param oldSourceDataTag the Tag to remove
     * @param changeReport a report of the taken actions and the outpome of the operation.
     */
    @Override
    public void onUpdateDataTag(final ISourceDataTag sourceDataTag, final ISourceDataTag oldSourceDataTag, final ChangeReport changeReport) {
        if (sourceDataTag.getHardwareAddress().equals(oldSourceDataTag.getHardwareAddress())) {
            r.apply(true, "The new and old sourceDataTags have the same hardware address, no update was required.", changeReport);
        } else {
            removeDataTagFutureAndReturnFuture(oldSourceDataTag, changeReport);
            addDataTagFutureAndReturnFuture(sourceDataTag, changeReport);
        }
    }

    public void addDataTagFutureAndReturnFuture(final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        boolean success = false;
        String msg = "Tag " + sourceDataTag.getName() + " with ID " + sourceDataTag.getId();
        try {
            success = controller.subscribeTag(sourceDataTag);
            msg += success ? " was subscribed." : " could not be subscribed.";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            msg += " could not be subscribed since the thread was interrupted.";
        }
        r.apply(success, msg, changeReport);
    }

    private void removeDataTagFutureAndReturnFuture(final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        String msg = "Tag " + sourceDataTag.getName() + " with ID " + sourceDataTag.getId();
        try {
            msg += controller.removeTag(sourceDataTag) ? " was unsubscribed." : " was not previously configured. No change required. ";
            r.apply(true, msg, changeReport);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            msg += " could not be subscribed since the thread was interrupted.";
            r.apply(false, msg, changeReport);
        }
    }

    private final static TriConsumer<Boolean, String, ChangeReport> r = (success, message, report) -> {
        final Function<String, String> prevMsg = s -> s == null ? "" : s;
        if (success) {
            report.appendInfo(prevMsg.apply(report.getInfoMessage()) + message);
            report.setState(SUCCESS);
        } else {
            log.error(message);
            report.appendError(prevMsg.apply(report.getErrorMessage()) + message);
            report.setState(FAIL);
        }
    };

    @FunctionalInterface
    private interface TriConsumer<A, B, C> {
        void apply(A a, B b, C c);
    }
}