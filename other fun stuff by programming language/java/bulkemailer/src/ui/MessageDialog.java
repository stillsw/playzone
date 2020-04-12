package ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

public class MessageDialog {

	 public static void showMessageDialog(Shell shell, String title, String message) {
		 final Shell dialog = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		 dialog.setText(title);
		 dialog.setLayout(new GridLayout(2, true));

		    Label label = new Label(dialog, SWT.NONE);
		    label.setText(message);
		    GridData data = new GridData();
		    data.horizontalSpan = 2;
		    label.setLayoutData(data);

		    Button ok = new Button(dialog, SWT.PUSH);
		    ok.setText("  OK  ");
		    data = new GridData(GridData.FILL_HORIZONTAL);
		    ok.setLayoutData(data);
		    ok.addSelectionListener(new SelectionAdapter() {
		      public void widgetSelected(SelectionEvent event) {
		    	  dialog.close();
		      }
		    });
		    dialog.setDefaultButton(ok);
		    dialog.pack();
		    dialog.open();
		    Display display = shell.getDisplay();
		    while (!dialog.isDisposed()) {
		      if (!display.readAndDispatch())
		        display.sleep();
		    }
		 

	}

	 public static boolean confirmMessageDialog(Shell shell, String title, String message) {
		 final Shell dialog = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		 dialog.setText(title);
		 dialog.setLayout(new GridLayout(2, true));

		    Label label = new Label(dialog, SWT.NONE);
		    label.setText(message);
		    GridData data = new GridData();
		    data.horizontalSpan = 2;
		    label.setLayoutData(data);

		    final Button ok = new Button(dialog, SWT.PUSH);
		    ok.setText("Confirm");
		    data = new GridData(GridData.FILL_HORIZONTAL);
		    ok.setLayoutData(data);
		    final Button cancel = new Button(dialog, SWT.PUSH);
		    cancel.setText("Cancel");
		    data = new GridData(GridData.FILL_HORIZONTAL);
		    cancel.setLayoutData(data);
		    final boolean[] retValue = new boolean[1];
		    SelectionListener listener = new SelectionAdapter() {
			      public void widgetSelected(SelectionEvent event) {
			    	  retValue[0] = (event.widget == ok);
			    	  dialog.close();
			      }
			    };
		    ok.addSelectionListener(listener);
		    cancel.addSelectionListener(listener);
		    dialog.setDefaultButton(ok);
		    dialog.pack();
		    dialog.open();
		    Display display = shell.getDisplay();
		    while (!dialog.isDisposed()) {
		      if (!display.readAndDispatch())
		        display.sleep();
		    }
		    
		    return retValue[0];

	}

}
