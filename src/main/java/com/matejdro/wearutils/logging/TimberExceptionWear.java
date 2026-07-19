package com.matejdro.wearutils.logging;

import android.app.Application;
import android.util.Log;

import java.util.concurrent.CancellationException;

import pl.tajchert.exceptionwear.ExceptionService;
import timber.log.Timber;

public class TimberExceptionWear extends Timber.Tree {
    private final Application application;

    public TimberExceptionWear(Application application) {
        this.application = application;
    }

    @Override
    protected void log(int priority, String tag, String message, Throwable t) {
        if (t != null && priority >= Log.ERROR && !(t instanceof CancellationException)) {
            ExceptionService.reportException(application, t);
        }
    }
}
