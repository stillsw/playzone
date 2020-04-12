package com.stillwindsoftware.keepyabeat.gui;

import com.stillwindsoftware.keepyabeat.control.ModuleManager;

public interface PauseableModule {

	public void startNew(ModuleManager moduleManager);

	public void finishAndClose();
	public void pause();
	public void resume();
	
	public boolean isStarted();
	public boolean isPaused();
	public boolean isResumed();
	public boolean isClosed();
	
	public String getName();
}
