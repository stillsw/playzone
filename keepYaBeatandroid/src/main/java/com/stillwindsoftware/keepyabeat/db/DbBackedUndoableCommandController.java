package com.stillwindsoftware.keepyabeat.db;

import android.util.Log;

import com.stillwindsoftware.keepyabeat.control.UndoableCommand;
import com.stillwindsoftware.keepyabeat.control.UndoableCommandController;
import com.stillwindsoftware.keepyabeat.platform.AndroidGuiManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation;
import com.stillwindsoftware.keepyabeat.platform.GuiManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;

import java.util.Stack;

/**
 * Android stores the undo commands in the db to save on memory, and to permit
 * restart of the app to also still be able to undo seamlessly.
 * The most current command is the only one sitting in the stack, so all the methods
 * that act on that stack can still use it, for the most part unchanged.
 * Created by tomas on 21/03/16.
 */
public class DbBackedUndoableCommandController extends UndoableCommandController {

    private static final String LOG_TAG = "KYB-"+DbBackedUndoableCommandController.class.getSimpleName();
    private int mStackKey; // editor or other

    /**
     * @param resourceManager
     * @param maxUndoHistory
     */
    public DbBackedUndoableCommandController(PlatformResourceManager resourceManager, int maxUndoHistory, int whichStack) {
        super(resourceManager, maxUndoHistory, whichStack);
    }

    @Override
    protected Stack<UndoableCommand> initStack(int whichStack) {
        stackMethodsOnlyOffUiThread = true;
        mStackKey = whichStack;
        return new DbBackedStack();
    }

    /**
     * Called during init of the controller/stack
     */
    @Override
    protected void doInitStack() {
        // get the description for the next command in the stack
        KybContentProvider contentProvider = (KybContentProvider) resourceManager.getLibrary().getBeatTypes();
        String desc = null;
        try {
            desc = contentProvider.peekNextUndoableCommand(mStackKey);
            if (desc != null) {
                commands.add(new DbBackedUndoableCommandProxy(desc));
                runStackChangeCallback();
            }
        }
        catch (Exception e) {
            AndroidResourceManager.loge(LOG_TAG, "doInitStack: could not complete undo init due to error (reported lower down)");
        }
    }

    @Override
    public void setMaxUndoHistory(int maxUndoHistory) {
        AndroidResourceManager.loge(LOG_TAG, "setMaxUndoHistory: should not impose added limit undos in db, ignored");
    }

    /**
     * Trap exceptions and particularly if there isn't one but there's something to report to the user
     * @param inBackground
     * @throws Exception
     */
    @Override
    protected synchronized void undoLastCommand(boolean inBackground) throws Exception {

        if (!commands.empty()) {
            try {
                UndoableCommand command = commands.pop();

                if (command != null) { // db backed (android) command could return null on pop if some error with inflating it

                    if (command.requiresTransactionForUndo()) {
                        resourceManager.startTransaction();
                    }

                    // most commands complete when running
                    if (inBackground) {
                        // background undo means user has cancelled changes (eg. Rhythm Editor)
                        // always check all commands can be undone before undoing
                        if (command.isUndoStillPossible()) {
                            command.batchUndo();
                        }
                    }
                    else {
                        command.undo();
                    }
                }
                else {
                    KybContentProvider.UnrecoverableUndoCommandDataError firstDataError = ((KybContentProvider) resourceManager.getLibrary().getBeatTypes()).takeLastUndoDataError();
                    resourceManager.getGuiManager().warnOnErrorMessage(CoreLocalisation.Key.UNDO_NOT_RECOVERABLE, true, firstDataError);
                }

                wasLastActionAddition = false;
                assertConsistentStack(true); // internal
            }
            finally {
                // callback if not a background run
                if (!inBackground && stackChangeCallback != null) {
                    stackChangeCallback.run();
                }
            }
        }
    }


    /**
     * Override because the internal call is already handled in undoLastCommand()
     * processing (see DbBackedStack.pop()), the command at the top of the dialog_simple_list
     * has already been tested for consistency, which means only external calls
     * to this method are needed
     * @param internal
     */
    @Override
    protected void assertConsistentStack(boolean internal) {
//        if (/*internal ||*/ commands.isEmpty()) { // no commands, nothing to do
//            return;
//        }

        KybContentProvider contentProvider = (KybContentProvider) resourceManager.getLibrary().getBeatTypes();
        String desc;
        try {
            desc = contentProvider.peekNextUndoableCommand(mStackKey);

            if (desc != null) {
                if (commands.isEmpty()) {
                    ((DbBackedStack)commands).pushCommandDesc(desc);
                    runStackChangeCallback();
                }
                else {
                    DbBackedUndoableCommandProxy command = (DbBackedUndoableCommandProxy) commands.peek();
                    if (!command.getDesc().equals(desc)) { // different desc, change it
                        command.setDesc(desc);
                        runStackChangeCallback();
                    }
                }
            }

            else if (!commands.isEmpty()) { // all commands on db must have been removed as inconsistent
                commands.clear();
                runStackChangeCallback();
                AndroidResourceManager.logd(LOG_TAG, "assertConsistentStack: no inconsistent commands found, stack cleared");
            }
        }
        catch (Exception e) {
            AndroidResourceManager.logd(LOG_TAG, "assertConsistentStack: failed with unexpected error (reported lower down)");
        }
    }

    /**
     * Used to remove all the rows in a stack, actually only needed for exiting rhythm editor, when
     * either save or cancel is used the undo stack is removed.
     */
    @Override
    public synchronized void clearStack() {
        if (resourceManager.getGuiManager().isUiThread()) {
            String msg = "clearStack: on ui thread, need to re-design";
            AndroidResourceManager.loge(LOG_TAG, msg);
            throw new RuntimeException(msg);
        }
        commands.clear();
        KybContentProvider contentProvider = (KybContentProvider) resourceManager.getLibrary().getBeatTypes();
        contentProvider.clearUndoStack(mStackKey);
    }

    @Override
    public void printUndoHistory() {
        AndroidResourceManager.logw(LOG_TAG, "printUndoHistory: not accurate and shouldn't be used in Android anyway");
    }

    /**
     * Lightweight proxy to sit in place for the latest command
     */
    private class DbBackedUndoableCommandProxy extends UndoableCommand.UndoableCommandAdaptor {

        protected DbBackedUndoableCommandProxy(UndoableCommand command) {
            super(false, command.getDesc());
        }

        protected DbBackedUndoableCommandProxy(String desc) {
            super(false, desc);
        }

        void setDesc(String desc) {
            this.desc = desc;
        }

        @Override
        protected void executeCommand() {
        }

        @Override
        public void undo() {
        }

        @Override
        public Object[] packForStorage() {
            return null; // not used
        }
    }

    private class DbBackedStack extends Stack<UndoableCommand> {

        private final AndroidGuiManager mGuiManager;

        public DbBackedStack() {
            mGuiManager = (AndroidGuiManager) resourceManager.getGuiManager();
        }

        @Override
        public synchronized UndoableCommand pop() {
            if (mGuiManager.isUiThread()) {
                String msg = "DbBackedStack.pop: can't pop() on ui thread, need to re-design";
                AndroidResourceManager.loge(LOG_TAG, msg);
                throw new RuntimeException(msg);
            }

            // pop the stack, but also sanity check the desc is correct
            KybContentProvider contentProvider = (KybContentProvider) resourceManager.getLibrary().getBeatTypes();
            UndoableCommand command;
            try {
                command = contentProvider.popUndoableCommand(mStackKey);
            }
            catch (Exception e) {
                AndroidResourceManager.loge(LOG_TAG, "DbBackedStack.pop: error getting next undo (reported lower down)");
                throw new RuntimeException(e); // this time it's a failure that the user needs to see
            }

            if (command != null) {
                String stackDesc = super.pop().getDesc(); // got the command, now it's safe to pop the stack
                if (stackDesc.equals(command.getDesc())) {
                    AndroidResourceManager.logv(LOG_TAG, "DbBackedStack.pop: desc matches for " + command);
                }
                else {
                    String msg = "DbBackedStack.pop: desc (" + stackDesc + ") does not match for " + command;
                    AndroidResourceManager.loge(LOG_TAG, msg);
                }

//                // get the description for the next command in the stack
//                String desc;
//                try {
//                    desc = contentProvider.peekNextUndoableCommand(mStackKey, false); // don't bypass inconsistent
//                    if (desc != null) {
//                        super.push(new DbBackedUndoableCommandProxy(desc));
//                    }
//                }
//                catch (Exception e) {
//                    AndroidResourceManager.loge(LOG_TAG, "DbBackedStack.pop: error getting next undo (reported lower down)");
//                }

                return command;
            }
            else {
                if (!commands.isEmpty()) {
                    AndroidResourceManager.logw(LOG_TAG, "DbBackedStack.pop: nothing from db stack, but commands have something");
                    commands.clear();
                }
                else {
                    AndroidResourceManager.logd(LOG_TAG, "DbBackedStack.pop: nothing to pop");
                }
                return null;
            }
        }

        void pushCommandDesc(String desc) {
            super.push(new DbBackedUndoableCommandProxy(desc));
        }

        @Override
        public UndoableCommand push(UndoableCommand command) {
            if (mGuiManager.isUiThread()) {
                String msg = "DbBackedStack.push: can't push() on ui thread, need to re-design";
                AndroidResourceManager.loge(LOG_TAG, msg);
                throw new RuntimeException(msg);
            }

            // any content provider is good
            KybContentProvider contentProvider = (KybContentProvider) resourceManager.getLibrary().getBeatTypes();
            try {
                AndroidResourceManager.logd(LOG_TAG, String.format("DbBackedStack.push: add to stack = %s", command.getDesc()));
                contentProvider.storeUndoableCommand(mStackKey, maxUndoHistory, command);
            }
            catch (Exception e) {
                AndroidResourceManager.loge(LOG_TAG, "DbBackedStack.push: unexpected error from storeUndoableCommand", e);
                throw new RuntimeException(e);
            }

            // create a proxy that just includes the desc only
            super.clear();
            return super.push(new DbBackedUndoableCommandProxy(command));
        }

        @Override
        public synchronized UndoableCommand remove(int location) {
            if (mGuiManager.isUiThread()) {
                String msg = "DbBackedStack.remove: can't remove() on ui thread, need to re-design";
                AndroidResourceManager.loge(LOG_TAG, msg);
                throw new RuntimeException(msg);
            }
            else {
                String msg = "DbBackedStack.remove: should never be needed because the history is culled at the db each pop";
                AndroidResourceManager.loge(LOG_TAG, msg);
                throw new RuntimeException(msg);
            }
            // if this is called, note that the location will be 0 since
            // it's intended for the 1st entry in the stack, but on the db it'll be the lowest id
//            return super.remove(location);
        }

    }
}
