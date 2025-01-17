package com.eventyay.organizer.core.attendee.list;

import android.annotation.SuppressLint;
import androidx.annotation.VisibleForTesting;

import com.raizlabs.android.dbflow.structure.BaseModel;

import com.eventyay.organizer.common.mvp.presenter.AbstractDetailPresenter;
import com.eventyay.organizer.common.rx.Logger;
import com.eventyay.organizer.data.db.DatabaseChangeListener;
import com.eventyay.organizer.data.db.DbFlowDatabaseChangeListener;
import com.eventyay.organizer.data.attendee.Attendee;
import com.eventyay.organizer.data.attendee.AttendeeRepository;
import com.eventyay.organizer.utils.Utils;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

import static com.eventyay.organizer.common.rx.ViewTransformers.dispose;
import static com.eventyay.organizer.common.rx.ViewTransformers.emptiable;
import static com.eventyay.organizer.common.rx.ViewTransformers.erroneous;
import static com.eventyay.organizer.common.rx.ViewTransformers.progressiveErroneousRefresh;

public class AttendeesPresenter extends AbstractDetailPresenter<Long, AttendeesView> {

    private final AttendeeRepository attendeeRepository;
    private final DatabaseChangeListener<Attendee> attendeeListener;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    private final List<Attendee> attendeeList = new ArrayList<>();

    private static final long FIRST_PAGE = 1;

    @Inject
    public AttendeesPresenter(AttendeeRepository attendeeRepository, DatabaseChangeListener<Attendee> attendeeListener) {
        this.attendeeRepository = attendeeRepository;
        this.attendeeListener = attendeeListener;
    }

    @Override
    public void start() {
        loadAttendeesPageWise(FIRST_PAGE, false);
        listenToModelChanges();
    }

    @Override
    public void detach() {
        super.detach();
        attendeeListener.stopListening();
    }

    public List<Attendee> getAttendees() {
        return attendeeList;
    }

    public void loadAttendees(boolean forceReload) {
        if (getView() == null)
            return;

        getView().showScanButton(false);

        getAttendeeSource(forceReload)
            .compose(dispose(getDisposable()))
            .compose(progressiveErroneousRefresh(getView(), forceReload))
            .toSortedList()
            .compose(emptiable(getView(), attendeeList))
            .doFinally(() -> getView().showScanButton(!attendeeList.isEmpty()))
            .subscribe(Logger::logSuccess, Logger::logError);
    }

    public void loadAttendeesPageWise(long pageNumber, boolean forceReload) {
        if (getView() == null)
            return;

        getView().showScanButton(false);

        getAttendeeSourcePageWise(pageNumber, forceReload)
            .compose(dispose(getDisposable()))
            .compose(progressiveErroneousRefresh(getView(), forceReload))
            .toSortedList()
            .compose(emptiable(getView(), attendeeList))
            .doFinally(() -> getView().showScanButton(!attendeeList.isEmpty()))
            .subscribe(Logger::logSuccess, Logger::logError);
    }

    private Observable<Attendee> getAttendeeSource(boolean forceReload) {
        if (!forceReload && !getView().getAttendeeList().isEmpty() && isRotated())
            return Observable.fromIterable(getView().getAttendeeList());
        else
            return attendeeRepository.getAttendees(getId(), forceReload);
    }

    private Observable<Attendee> getAttendeeSourcePageWise(long pageNumber, boolean forceReload) {
        if (!forceReload && !getView().getAttendeeList().isEmpty() && isRotated())
            return Observable.fromIterable(getView().getAttendeeList());
        else
            return attendeeRepository.getAttendeesPageWise(getId(), pageNumber, forceReload);
    }

    private void updateLocal(Attendee attendee) {
        Utils.indexOf(getView().getAttendeeList(), attendee, (first, second) -> first.getId() == second.getId())
            .subscribeOn(Schedulers.computation())
            .subscribe(index -> getView().getAttendeeList().set(index, attendee), Logger::logError);
    }

    private void listenToModelChanges() {
        attendeeListener.startListening();

        attendeeListener.getNotifier()
            .compose(dispose(getDisposable()))
            .compose(erroneous(getView()))
            .filter(attendeeModelChange -> attendeeModelChange.getAction().equals(BaseModel.Action.UPDATE))
            .map(DbFlowDatabaseChangeListener.ModelChange::getModel)
            .flatMap(filterAttendee -> attendeeRepository.getAttendee(filterAttendee.getId(), false))
            .subscribe(attendee -> {
                getView().updateAttendee(attendee);
                updateLocal(attendee);
            }, Logger::logError);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public AttendeesView getView() {
        return super.getView();
    }

    @VisibleForTesting
    public void setAttendeeList(List<Attendee> attendeeList) {
        this.attendeeList.clear();
        this.attendeeList.addAll(attendeeList);
    }

    @SuppressLint("CheckResult")
    public void toggleCheckInState(List<Attendee> attendeeList, int swipedPosition) {
        Attendee attendee = attendeeList.get(swipedPosition);
        attendee.setChecking(true);
        attendee.isCheckedIn = !attendee.isCheckedIn;
        compositeDisposable.add(
            attendeeRepository.scheduleToggle(attendee)
                .subscribe(() -> {
                    // Nothing to do
                }, Logger::logError));
    }
}

