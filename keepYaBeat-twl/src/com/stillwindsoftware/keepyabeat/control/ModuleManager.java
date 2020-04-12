package com.stillwindsoftware.keepyabeat.control;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Stack;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.KeepYaBeat.RootPane;
import com.stillwindsoftware.keepyabeat.geometry.MutableRectangle;
import com.stillwindsoftware.keepyabeat.gui.BeatsAndSoundsModule;
import com.stillwindsoftware.keepyabeat.gui.PauseableModule;
import com.stillwindsoftware.keepyabeat.gui.PlayerModule;
import com.stillwindsoftware.keepyabeat.gui.TwlRhythmEditor;
import com.stillwindsoftware.keepyabeat.gui.WelcomeDialog;
import com.stillwindsoftware.keepyabeat.model.EditRhythm;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlPersistentStatesManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;


/**
 * Manages opening/closing modules in a stack, so if one is closed another is opened that was previously open
 * etc
 * @author tomas stubbs
 */
public class ModuleManager {

	// an exception that is thrown if a public method attempts to violate the exclusiveOperationInProcess
	// flag by either running something that can only run when already set (like invokeModule(child, parent))
	// or by attempting to run something that can only run when it is not already set
	@SuppressWarnings("serial")
	public class ModuleManagerSynchException extends RuntimeException {
		ModuleManagerSynchException(String desc) {
			super(desc);
		}
	}
	
	// the singleton subclass
	private static ModuleManager instance;	
	
	// the stack of modules that are running
	@SuppressWarnings("rawtypes")
	private Stack<Class> runningModules = new Stack<Class>();

	// flag to indicate a module is in process of invoking/closing, so accept no inputs
	private boolean exclusiveOperationInProcess = false;
	
	// the available space for modules whilst reserving the amount that the module bar takes
	protected MutableRectangle moduleLayoutSpace;

	protected final TwlPersistentStatesManager persistentStatesManager;
	protected final TwlResourceManager resourceManager;
	
	public static ModuleManager getNewInstance(TwlResourceManager resourceManager) {
		instance = new ModuleManager(resourceManager);
		return instance;
	}
	
	private ModuleManager(TwlResourceManager resourceManager) {
		this.resourceManager = resourceManager; 
		persistentStatesManager = (TwlPersistentStatesManager) resourceManager.getPersistentStatesManager();
	}
	
	private void resetExclusiveOperationInProcess() {
		exclusiveOperationInProcess = false;
		KeepYaBeat.root.setEnableMenuButtons(true);
	}

	/**
	 * All module manager high-level actions are mutually exclusive... they can't happen at the same time
	 * This flag acts globally to prevent user actions that will cause high-level actions to overlap, such
	 * as a new click on a menu item, or closing a module. 
	 * @return
	 */
	public boolean isExclusiveOperationInProcess() {
		return exclusiveOperationInProcess;
	}

	public void setExclusiveOperationInProcess() {
		this.exclusiveOperationInProcess = true;
	}

	public boolean isTopOfTheStack(PauseableModule module) {
		return (!runningModules.isEmpty() && runningModules.peek() == module.getClass());
	}
	
	/**
	 * Top level modules
	 * @param module
	 * @return
	 */
	public boolean isRunningTopLevelModule(PauseableModule module) {
		return runningModules.contains(module.getClass());
	}

	public boolean isRunningTopLevelModule(@SuppressWarnings("rawtypes") Class moduleClass) {
		return runningModules.contains(moduleClass);
	}

	/**
	 * Called during startup, checks for rhythm editor to start, and also for last open module.
	 * If both are true the callback to rhythm editor will open the last open module when 
	 * rhythm editor closes.
	 * 4 scenarios:
	 * 1) no welcome screen & edit rhythm is present
	 * 		show rhythm editor
	 * 		when editor is closed, last open module (ie. same as 2, but will always be rhythm player)
	 * 2) no welcome screen & edit rhythm is not present
	 * 		show last open module
	 * 3) welcome screen & edit rhythm is present
	 * 		show welcome
	 * 		when welcome closes same as 1)
	 * 4) welcome screen & edit rhythm is not present
	 * 		show welcome
	 * 		when welcome closes same as 2)
	 */
	public void restoreLastOpenModule() {
		final String openModule = persistentStatesManager.getLastOpenModule();
		final EditRhythm editRhythm = persistentStatesManager.getEditRhythm();
		
		Runnable doNext = null;
		
		// create a runnable of the next thing after either welcome page is closed
		// or after initialisation completes
		if (editRhythm != null) {
			doNext = new Runnable() {

				@Override
				public void run() {
					new TwlRhythmEditor(true, editRhythm, new Runnable() {
						@Override
						public void run() {
							restoreLastOpenModule(openModule);
						}					
					});
				}	
			};
		}
		else {
			doNext = new Runnable() {

				@Override
				public void run() {
					restoreLastOpenModule(openModule);
				}
			};
		}
		
		// show a welcome screen unless it's been turned off
		// after it closes it will run whatever was doNext last open module
		if (persistentStatesManager.isShowWelcomeDialog()) {
			new WelcomeDialog(doNext);
		}
		else {
			// init the menubar and start what's next
			KeepYaBeat.root.setupMenuBar();
			doNext.run();
		}
		
	}
	
	private void restoreLastOpenModule(String moduleName) {
		if (TwlPersistentStatesManager.BEATS_AND_SOUNDS.equals(moduleName)) {			
			toggleTopLevelModule(BeatsAndSoundsModule.getInstance());
		}
		else if (TwlPersistentStatesManager.PLAYER.equals(moduleName)) {			
			toggleTopLevelModule(PlayerModule.getInstance());
		}
	}

	public void toggleTopLevelModule(PauseableModule module) {
		toggleTopLevelModule(module, true);
	}
	
	/**
	 * Entry point for module operations
	 * If the module is not running, then start it
	 * If it's running but not top of the stack, then reopen (resume) it
	 * If it's running and top of the stack, then close it
	 * @param parentModule
	 * @param scheduleTask usually true, task is created as an undoable command, if false, command is not 
	 * created and instead the task is returned
	 */
	public PendingTask toggleTopLevelModule(PauseableModule module, boolean scheduleTask) {
		try {
			// check not already in process with another request
			if (exclusiveOperationInProcess) {
				resourceManager.log(LOG_TYPE.debug, this, "ModuleManager.toggleTopLevelModule: attempt to start a module while in process");
				throw new ModuleManagerSynchException("attempt to start a module while in process");
			}

			// several steps in common
			
			// 1. set the flag, in process, nothing else can start while this is happening (including undo operations)
			// note, this flag is unset as the last task in the list that is going to be built
			exclusiveOperationInProcess = true;

			// 2. determine if need to open/reopen/close the module and make a (composite) task for it and another for undo
			boolean moduleIsRunning = isRunningTopLevelModule(module);
			boolean moduleIsTopOfStack = moduleIsRunning && runningModules.peek() == module.getClass();
			
			// careful not to change the stack, all of these methods build the tasks that execute at the end, so everything
			// should remain as it is until then
			PendingTask doTask = null;

			if (moduleIsTopOfStack) {
				doTask = makeTaskToCloseTopLevel(module);
			}
			else if (moduleIsRunning) {
				doTask = makeTaskToReopenModule(module);
			}
			else {
				doTask = makeTaskToOpenModule(module);
			}
			
			// 3. add the reset in process flag task as a next task to each
			doTask.appendNextTask(new ResetOperationInProcessPendingTask());
			
			// 4. place it on the stack
			if (scheduleTask) {
				resourceManager.getPendingTasksScheduler().addTask(doTask);
			}
			else {
				return doTask;
			}
		} catch (ModuleManagerSynchException e) {}

		return null;
	}
	
	/**
	 * Entry point for module operations
	 * If the module is not running, then start it
	 * If it's running but not top of the stack, then reopen (resume) it
	 * Difference between this method and toggle... above, is that this method never closes the module
	 * @param parentModule
	 */
	public void openTopLevelModule(PauseableModule module) {
		try {
			// check not already in process with another request
			if (exclusiveOperationInProcess) {
				resourceManager.log(LOG_TYPE.debug, this, "ModuleManager.openTopLevelModule: attempt to start a module while in process");
				
				throw new ModuleManagerSynchException("attempt to start a module while in process");
			}

			// several steps in common
			
			// 1. set the flag, in process, nothing else can start while this is happening (including undo operations)
			// note, this flag is unset as the last task in the list that is going to be built
			exclusiveOperationInProcess = true;

			// 2. determine if need to open/reopen/close the module and make a (composite) task for it and another for undo
			boolean moduleIsRunning = isRunningTopLevelModule(module);
			boolean moduleIsTopOfStack = moduleIsRunning && runningModules.peek() == module.getClass();
			
			// careful not to change the stack, all of these methods build the tasks that execute at the end, so everything
			// should remain as it is until then
			PendingTask doTask = null;

			if (!moduleIsTopOfStack) { // otherwise nothing to do
				if (moduleIsRunning) {
					doTask = makeTaskToReopenModule(module);
				}
				else {
					doTask = makeTaskToOpenModule(module);
				}
				
				// 3. add the reset in process flag task as a next task to each
				doTask.appendNextTask(new ResetOperationInProcessPendingTask());
				
				// 4. place it on the stack
				resourceManager.getPendingTasksScheduler().addTask(doTask);
			}
			else {
				// even though didn't have anything to open, have to reset the flag
				exclusiveOperationInProcess = false;
			}
		} catch (ModuleManagerSynchException e) {}
	}
	
	/**
	 * Pass a top level module and have it start from scratch, will make a composite task to open
	 * the task and pass it back, or if there's another module running, will first pause that one. 
	 * In the case of having 2 tasks, wrap them in another task so that the calling method can set
	 * the next task itself.
	 * so will create
	 * @param module
	 */
	private PendingTask makeTaskToOpenModule(PauseableModule module) {

		PendingTask openTask = makeTaskToStartNewTopLevel(module);

		if (runningModules.isEmpty()) {
			return openTask;
		}
		else {
			// another module is running
			// get a task to pause it and put the open task as its next task
			return new PendingTask(openTask.getName(), 
					makeTaskToPauseTopLevel(getCurrentModuleInstance(runningModules.peek()), openTask));				
		}
	}

	/**
	 * Pass a top level module and have it resume itself 
	 * @param module
	 */
	private PendingTask makeTaskToReopenModule(PauseableModule module) {

		boolean moduleIsTopOfStack = runningModules.peek() == module.getClass();

		// another module is running, should always be the case for this condition
		if (!moduleIsTopOfStack) {
			PendingTask reopenTask = makeTaskToResumeTopLevel(module);
			
			// get a task to pause the top module and put the reopen task as its next task
			return new PendingTask(reopenTask.getName(), 
					makeTaskToPauseTopLevel(getCurrentModuleInstance(runningModules.peek()), reopenTask));				
		}
		else {
			// doesn't make sense to call to reopen if it's already top
			resourceManager.log(LOG_TYPE.debug, this, "ModuleManager.makeTaskToReopenModule: attempt to reopen a module that's at the top of the stack already");
			
			throw new ModuleManagerSynchException("attempt to reopen a module that's at the top of the stack already");
		}
	}

	private PauseableModule getNextModule(PauseableModule module) {
		boolean moduleIsTopOfStack = !runningModules.isEmpty() && runningModules.peek() == module.getClass();
		if (!moduleIsTopOfStack) {
			throw new IllegalArgumentException("ModuleManager.getNextModule: called for module that isn't top of the stack");
		}
		
		// easy out
		if (runningModules.size() == 1) {
			return null;
		}

		// index the module under the top one
		int nextModuleIdx = runningModules.size()-2;
		
		// whatever the index is now pointing at is the module to return
		return getCurrentModuleInstance(runningModules.get(nextModuleIdx));
	}
	
	/**
	 * A module is closing, remove it from the stack. If there's another module under it
	 * resume it.
	 * @param module
	 */
	private PendingTask makeTaskToCloseTopLevel(PauseableModule module) {

		// if it's at the top of the stack will have to resume what's under it
		boolean moduleIsTopOfStack = !runningModules.isEmpty() && runningModules.peek() == module.getClass();
		PendingTask closeTask = makeTaskToCloseModule(module, moduleIsTopOfStack);

		if (moduleIsTopOfStack) {			
			// make a wrapper to pass back, or if not needed fall through to pass back the close
			PauseableModule resumeModule = getNextModule(module);
			if (resumeModule != null) {
				PendingTask closeResumeTask = new PendingTask(makeTaskToResumeTopLevel(resumeModule), "sequential close/resume", closeTask);
				return new PendingTask(closeTask.getName(), closeResumeTask);				
			}
		}

		return closeTask;
	}

	/**
	 * Create a composite task that starts a new module in the render thread
	 * @param module
	 * @return
	 */
	private PendingTask makeTaskToStartNewTopLevel(PauseableModule module) {
		
		PendingTask startNew = new StartModulePendingTask(module);
		PendingTask putToTopOfStack = new PutModuleToTopOfStackPendingTask(module);
		
		// return a composite that starts the new module and also the module bar
		return new PendingTask(String.format("ModuleManager startNew %s", module.getClass().getSimpleName()), startNew, putToTopOfStack);
	}
	
	/**
	 * Create a simple task that pauses a new module in the render thread
	 * @param module
	 * @param nextTask the task to do when this one completes
	 * @return
	 */
	private PendingTask makeTaskToPauseTopLevel(PauseableModule module, PendingTask nextTask) {
		
		PendingTask pauseTask = new PauseModulePendingTask(module);
		
		// return the pause and, plus pass the next task to do
		return new PendingTask(nextTask, "composite pause module", pauseTask);
	}
	
	/**
	 * Create a composite task that resumes a new module and installs the module bar in the render thread
	 * @param module
	 * @return
	 */
	private PendingTask makeTaskToResumeTopLevel(PauseableModule module) {
		
		PendingTask resumeTask = new ResumeModulePendingTask(module);
		PendingTask putToTopOfStack = new PutModuleToTopOfStackPendingTask(module);
		
		// return the two tasks to resume 
		return new PendingTask(null, String.format("ModuleManager resume %s", module.getClass().getSimpleName()), resumeTask, putToTopOfStack);
	}
	
	/**
	 * Does 3 things: 
	 * 1. puts the module at the top of the modules stack
	 * 2. tells the persistent states class about it
	 * @param moduleClass
	 * @param module
	 */
	private void pushToTopOfStack(@SuppressWarnings("rawtypes") Class moduleClass, PauseableModule module) {
		if (runningModules.contains(moduleClass)) {
			runningModules.remove(moduleClass);
		}
		runningModules.push(moduleClass);
		KeepYaBeat.root.setModuleName(module.getName());
		
		persistentStatesManager.moduleOpened(module);
	}
	
	/**
	 * A task that closes a module
	 * @param module
	 * @return
	 */
	private PendingTask makeTaskToCloseModule(PauseableModule module, boolean isTopOfStack) {
		PendingTask removeFromStack = new RemoveModuleFromStackPendingTask(module);
		PendingTask endModule = new EndModulePendingTask(module);
		
		if (isTopOfStack) {
			// create the task to uninstall the module bar
			return new PendingTask("close "+module.getName(), removeFromStack, endModule);
		}
		else {
			// no module bar as isn't at the top
			return new PendingTask("close "+module.getName(), removeFromStack, endModule);
		}
	}
	
	/**
	 * Because of undo, at any time dealing with a pending task, it's possible the module being referred to 
	 * has been previously closed and there's a fresh instance that is now current.
	 * Using reflection return the latest and current instance of that module's class
	 * @param module
	 * @return
	 */
	private PauseableModule getCurrentModuleInstance(@SuppressWarnings("rawtypes") Class moduleClass) {

		if (moduleClass != null) {
			try {
				@SuppressWarnings("unchecked")
				Method currentInstanceMethod = moduleClass.getMethod("getCurrentInstance");
				PauseableModule currentInstance = (PauseableModule) currentInstanceMethod.invoke(null);
				return currentInstance;
	
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		}
		// either called with null or there was an error
		return null;
	}

	//--------------------- methods connected with layout of modules
	
	public void resetModuleLayoutSpace() {
		moduleLayoutSpace = null;
	}
	
	/**
	 * A rectangle with the available space on the screen for layout of modules.
	 * The y position gives room for the module bar above it.
	 * @return
	 */
	public MutableRectangle getModuleLayoutSpace() {
		if (moduleLayoutSpace == null) {
			 moduleLayoutSpace = new MutableRectangle(KeepYaBeat.root.getInnerX(), 
					 			KeepYaBeat.root.getInnerY() + RootPane.MENU_BAR_RESERVED_HEIGHT, 
					 			KeepYaBeat.root.getInnerWidth(), 
					 			KeepYaBeat.root.getInnerHeight() - RootPane.MENU_BAR_RESERVED_HEIGHT);
		}
		return moduleLayoutSpace;
	}
	
	/**
	 * Compute the layout and return the object passed in, and if null create it.
	 * The unfettered w/h is what the requestor would like to be if there were no limits, this might be modified
	 * if it doesn't fit.
	 * @param layoutRect
	 * @param unfetteredWidth
	 * @param unfetteredHeight
	 */
	public MutableRectangle computeLayout(MutableRectangle layoutRect, int unfetteredWidth, int unfetteredHeight, int maxWidth, int maxHeight) {
		if (layoutRect == null)
			layoutRect = new MutableRectangle(0f, 0f, 0f, 0f);
		
		// make sure layout space is initialised
		getModuleLayoutSpace();
		int totalWidth = (int)moduleLayoutSpace.getW();
		if (maxWidth != Library.INT_NOT_CHANGED)
			totalWidth = Math.min(maxWidth, totalWidth);
		int totalHeight = (int)moduleLayoutSpace.getH();
		if (maxHeight != Library.INT_NOT_CHANGED)
			totalHeight = Math.min(maxHeight, totalHeight);

		// adjust width/height by what is possible, and add scrollbars to other dimension if needed
		if (unfetteredHeight > totalHeight) {
			unfetteredHeight = totalHeight;
			unfetteredWidth += KeepYaBeat.vScrollbarWidth;
			unfetteredWidth = Math.min(unfetteredWidth, totalWidth);
		}
		else if (unfetteredWidth > totalWidth) {
			unfetteredWidth = totalWidth;
			unfetteredHeight += KeepYaBeat.hScrollbarHeight;
			unfetteredHeight = Math.min(unfetteredHeight, totalHeight);
		}
		
		layoutRect.setXYWH(moduleLayoutSpace.getX(), moduleLayoutSpace.getY(), Math.max(unfetteredWidth, 0), Math.max(unfetteredHeight, 0));

		return layoutRect;
	}
	
	public boolean isLowResLayout() {
		return false;
	}
	
	/**
	 * Returns the created module manager, it doesn't create one.
	 * @return
	 */
	public static ModuleManager getInstance() {
		return instance;
	}
	
	//--------------------------- atomic sub classes for PendingTask
	
	private class PutModuleToTopOfStackPendingTask extends PendingTask {
		@SuppressWarnings("rawtypes")
		private Class moduleClass;
		private PauseableModule module;
		
		private PutModuleToTopOfStackPendingTask(PauseableModule module) {
			super("make module top of stack "+module.getClass().getSimpleName(), (PendingTask[])null);
			this.moduleClass = module.getClass();
			this.module = module;
			KeepYaBeat.root.setMenuButtonActive(module, true);
		}
		
		@Override
		protected void startTask() {
			pushToTopOfStack(moduleClass, module);
		}
	}
	
	public class ResetOperationInProcessPendingTask extends PendingTask {
		
		public ResetOperationInProcessPendingTask() {
			super("reset module manager in process", (PendingTask[])null);
		}
		
		@Override
		protected void startTask() {
			resetExclusiveOperationInProcess();
		}
	}
	
	private class RemoveModuleFromStackPendingTask extends PendingTask {
		@SuppressWarnings("rawtypes")
		private Class moduleClass;
		
		private RemoveModuleFromStackPendingTask(PauseableModule module) {
			super("remove module from stack "+module.getClass().getSimpleName(), (PendingTask[])null);
			this.moduleClass = module.getClass();
		}

		@Override
		protected void startTask() {
			if (isRunningTopLevelModule(moduleClass)) {
				runningModules.remove(moduleClass);
			}
			
			persistentStatesManager.moduleClosed();
		}
	}
	
	private class EndModulePendingTask extends PendingTask {
		
		@SuppressWarnings("rawtypes")
		private Class moduleClass;
		
		private EndModulePendingTask(PauseableModule module) {
			super("terminate module "+module.getClass().getSimpleName(), (PendingTask[])null);
			this.moduleClass = module.getClass();
		}

		@Override
		protected void updateComplete() {
			PauseableModule module = getCurrentModuleInstance(moduleClass);

			// if the instance has gone, then it must have completed
			if (module == null) {
				complete = true;
			}
			else if (module.isClosed()) {
				complete = true;
			}			
		}
		@Override
		protected void startTask() {
			// clear the name
			KeepYaBeat.root.setModuleName("");
			
			PauseableModule module = getCurrentModuleInstance(moduleClass);
			module.finishAndClose();
		}
	}

	private class ResumeModulePendingTask extends PendingTask {

		@SuppressWarnings("rawtypes")
		private Class moduleClass;
		private boolean isResume = true;
		
		private ResumeModulePendingTask(PauseableModule module) {
			super("resume module "+module.getClass().getSimpleName(), (PendingTask[])null);
			this.moduleClass = module.getClass();
		}

		@Override
		protected void updateComplete() {
			// once it is resumed and all its children are too
			PauseableModule module = getCurrentModuleInstance(moduleClass);
			if (isResume) {
				if (module.isResumed()) {
					complete = true;
				}				
			}
			else {
				// if had to start afresh the test for completion is like starting a new module
				if (module.isStarted()) {
					complete = true;
				}
			}
		}

		@Override
		protected void startTask() {
			PauseableModule module = getCurrentModuleInstance(moduleClass);
			// check is ready to start, and if not start it afresh
			if (module.isStarted()) {
				module.resume();
			}
			else {
				// a different mode needed, starting from fresh
				isResume = false;
				module.startNew(ModuleManager.this);
			}
		}
	}

	private class PauseModulePendingTask extends PendingTask {

		@SuppressWarnings("rawtypes")
		private Class moduleClass;
		
		private PauseModulePendingTask(PauseableModule module) {
			super("pause module "+module.getClass().getSimpleName(), (PendingTask[])null);
			this.moduleClass = module.getClass();
			KeepYaBeat.root.setMenuButtonActive(module, false);
		}

		@Override
		protected void updateComplete() {
			PauseableModule module = getCurrentModuleInstance(moduleClass);
			if (module.isPaused()) {
				complete = true;
			}
		}

		@Override
		protected void startTask() {
			PauseableModule module = getCurrentModuleInstance(moduleClass);
			module.pause();
		}
	}
	
	private class StartModulePendingTask extends PendingTask {

		@SuppressWarnings("rawtypes")
		private Class moduleClass;
		
		private StartModulePendingTask(PauseableModule module) {
			super("start module "+module.getClass().getSimpleName(), (PendingTask[])null);
			this.moduleClass = module.getClass();
		}

		@Override
		protected void updateComplete() {
			PauseableModule module = getCurrentModuleInstance(moduleClass);
			if (module.isStarted()) {
				complete = true;
			}
		}
		@Override
		protected void startTask() {
			PauseableModule module = getCurrentModuleInstance(moduleClass);
			module.startNew(ModuleManager.this);
		}
	}
}
