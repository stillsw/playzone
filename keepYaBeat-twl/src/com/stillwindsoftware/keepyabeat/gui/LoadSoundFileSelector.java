/*
 * Copyright (c) 2008-2010, Matthias Mann
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.stillwindsoftware.keepyabeat.gui;

import java.io.File;

import com.stillwindsoftware.keepyabeat.platform.twl.TestedSoundFile;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.PopupWindow;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.FileSystemModel;
import de.matthiasmann.twl.model.FileSystemModel.FileFilter;
import de.matthiasmann.twl.model.JavaFileSystemModel;


/**
 * Taken from LoadFileSelector in the ThemeEditor 
 * @author Tomas Stubbs
 */
public class LoadSoundFileSelector {

    private static SoundFileCallback callback;
    private static PopupWindow popupWindow;
    final TwlFileSelector fileSelector;
    private static TestedSoundFile testedFile;

    public LoadSoundFileSelector(Widget owner, SoundFileCallback callback) {
    	LoadSoundFileSelector.callback = callback;

        TwlFileSelector.NamedFileFilter filter = 
        		new TwlFileSelector.NamedFileFilter(
        				TwlResourceManager.getInstance().getLocalisedString(
        						TwlLocalisationKeys.SOUND_FILES_DESCRIPTION)
        					, new ExtFilter("wav", "aif", "wave", "aiff"));

        fileSelector = new TwlFileSelector();
        fileSelector.setFileSystemModel(JavaFileSystemModel.getInstance());
        fileSelector.setAllowMultiSelection(false);
        fileSelector.setAllowFolderSelection(false);
        fileSelector.addCallback(new SoundFileCallback());
        fileSelector.addFileFilter(TwlFileSelector.AllFilesFilter);
        fileSelector.addFileFilter(filter);
        fileSelector.setFileFilter(filter);

        popupWindow = new PopupWindow(owner);
        popupWindow.setTheme("fileselector-popup");
        popupWindow.add(fileSelector);
        popupWindow.setCloseOnEscape(false);
        popupWindow.setCloseOnClickedOutside(false);
    }

    public void openPopup() {
        if(popupWindow.openPopup()) {
            GUI gui = popupWindow.getGUI();
            popupWindow.setSize(gui.getWidth()*4/5, gui.getHeight()*4/5);
            popupWindow.setPosition(
                    (gui.getWidth() - popupWindow.getWidth())/2,
                    (gui.getHeight() - popupWindow.getHeight())/2);
        }
    }

    public TestedSoundFile getTestedSoundFile() {
    	return testedFile;
    }
    

    public static class SoundFileCallback implements TwlFileSelector.Callback {
        public void filesSelected(Object[] files) {
            if(files.length == 1 && (files[0] instanceof File)) {
                popupWindow.closePopup();
                testedFile = new TestedSoundFile((File)files[0]);
                callback.filesSelected(files);
            }
        }

        public void canceled() {
            popupWindow.closePopup();
            callback.canceled();
        }
    }
    
    public static class ExtFilter implements FileFilter {
        private final String[] extensions;
        public ExtFilter(String ... extensions) {
            this.extensions = extensions;
        }
        public boolean accept(FileSystemModel fsm, Object file) {
            String name = fsm.getName(file).toLowerCase();
            for(String extension : extensions) {
                if(name.endsWith(extension)) {
                    return true;
                }
            }
            return false;
        }
    }

}
