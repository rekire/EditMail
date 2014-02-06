/**
 * @copyright
 * This code is licensed under the Rekisoft Public License.
 * See http://www.rekisoft.eu/licenses/rkspl.html for more informations.
 */
package eu.rekisoft.android.util;

import android.os.Handler;
import android.os.Looper;

/**
 * Helper class for simple changing UI elements. If this object is created in UI thread the job will
 * be executed immediately, if not the job will be executed on the UI thread delayed.
 *
 * @author René Kilczan
 * @version 1.0
 * @copyright This code is licensed under the Rekisoft Public License.<br/>
 * See http://www.rekisoft.eu/licenses/rkspl.html for more informations.
 */
public abstract class UiWorker<T> extends Handler implements Runnable {
    private final boolean isCritical;
    private T data;

    /**
     * Creates a new UiWorker with the default priority.
     */
    public UiWorker() {
        this(null, false, true);
    }

    /**
     * Creates a new UiWorker with the ability to make this job critical.
     *
     * @param isCritical true if this job is critical.
     */
    public UiWorker(boolean isCritical) {
        this(null, isCritical, true);
    }

    /**
     * Creates a new UiWorker with the default priority and data.
     *
     * @param data The data which should be injected to doWork().
     */
    public UiWorker(T data) {
        this(data, false, true);
    }

    /**
     * Creates a new UiWorker with the ability to make this job critical and data.
     *
     * @param data       The data which should be injected to doWork().
     * @param isCritical true if this job is critical.
     */
    public UiWorker(T data, boolean isCritical) {
        this(null, isCritical, true);
    }

    /**
     * Base for extension of this UiWorker, you can control if the execution should been done
     * immediately or not.
     *
     * @param data       The data which should been used in doWork(data).
     * @param isCritical true if this job is critical.
     * @param runNow     should been executed right now in the constructor.
     */
    protected UiWorker(T data, boolean isCritical, boolean runNow) {
        super(Looper.getMainLooper());
        this.isCritical = isCritical;
        this.data = data;
        if(runNow) {
            run();
        }
    }

    /**
     * Executes the task on the Ui Thread.
     */
    @Override
    public final void run() {
        if(Thread.currentThread().getId() == getLooper().getThread().getId()) {
            doWork(data);
        } else {
            if(isCritical && postAtFrontOfQueue(this)) {
                return;
            }
            post(this);
        }
    }

    /**
     * This method is invoked on the UI Thread.
     *
     * @param data The data you defined in the constructor of this class.
     */
    protected abstract void doWork(T data);
}