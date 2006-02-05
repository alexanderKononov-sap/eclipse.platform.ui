/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ltk.internal.ui.refactoring.scripting;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptorProxy;
import org.eclipse.ltk.core.refactoring.history.IRefactoringHistoryService;
import org.eclipse.ltk.core.refactoring.history.RefactoringHistory;

import org.eclipse.ltk.internal.ui.refactoring.Messages;
import org.eclipse.ltk.internal.ui.refactoring.RefactoringPluginImages;
import org.eclipse.ltk.internal.ui.refactoring.RefactoringUIMessages;
import org.eclipse.ltk.internal.ui.refactoring.RefactoringUIPlugin;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.Wizard;

/**
 * Wizard to create a refactoring script.
 * 
 * @since 3.2
 */
public final class CreateRefactoringScriptWizard extends Wizard {

	/** The dialog settings key */
	private static String DIALOG_SETTINGS_KEY= "CreateRefactoringScriptWizard"; //$NON-NLS-1$

	/** Has the wizard new dialog settings? */
	private boolean fNewSettings;

	/** The selected refactoring descriptors, or the empty array */
	private RefactoringDescriptorProxy[] fRefactoringDescriptors= {};

	/** The refactoring history */
	private final RefactoringHistory fRefactoringHistory;

	/** The refactoring script location, or <code>null</code> */
	private URI fScriptLocation= null;

	/** The create refactoring script wizard page */
	private final CreateRefactoringScriptWizardPage fWizardPage;

	/**
	 * Creates a new create refactoring script wizard.
	 */
	public CreateRefactoringScriptWizard() {
		setNeedsProgressMonitor(false);
		setWindowTitle(ScriptingMessages.CreateRefactoringScriptWizard_caption);
		setDefaultPageImageDescriptor(RefactoringPluginImages.DESC_WIZBAN_REFACTOR);
		final IDialogSettings settings= RefactoringUIPlugin.getDefault().getDialogSettings();
		final IDialogSettings section= settings.getSection(DIALOG_SETTINGS_KEY);
		if (section == null)
			fNewSettings= true;
		else {
			fNewSettings= false;
			setDialogSettings(section);
		}
		fWizardPage= new CreateRefactoringScriptWizardPage(this);
		final IRefactoringHistoryService service= RefactoringCore.getRefactoringHistoryService();
		try {
			service.connect();
			fRefactoringHistory= service.getWorkspaceHistory(null);
		} finally {
			service.disconnect();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void addPages() {
		super.addPages();
		addPage(fWizardPage);
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean canFinish() {
		return fScriptLocation != null && fRefactoringDescriptors.length > 0;
	}

	/**
	 * Retruns the selected refactoring descriptors.
	 * 
	 * @return the selected refactoring descriptors
	 */
	public RefactoringDescriptorProxy[] getRefactoringDescriptors() {
		return fRefactoringDescriptors;
	}

	/**
	 * Returns the refactoring history to create a script from.
	 * 
	 * @return the refactoring history.
	 */
	public RefactoringHistory getRefactoringHistory() {
		return fRefactoringHistory;
	}

	/**
	 * Returns the refactoring script location.
	 * 
	 * @return the refactoring script location
	 */
	public URI getScriptLocation() {
		return fScriptLocation;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean performFinish() {
		if (fNewSettings) {
			final IDialogSettings settings= RefactoringUIPlugin.getDefault().getDialogSettings();
			IDialogSettings section= settings.getSection(DIALOG_SETTINGS_KEY);
			section= settings.addNewSection(DIALOG_SETTINGS_KEY);
			setDialogSettings(section);
		}
		return performScriptExport();
	}

	/**
	 * Performs the actual refactoring script export.
	 * 
	 * @return <code>true</code> if the wizard can be finished,
	 *         <code>false</code> otherwise
	 */
	private boolean performScriptExport() {
		RefactoringDescriptorProxy[] writable= fRefactoringDescriptors;
		if (fScriptLocation != null) {
			final File file= new File(fScriptLocation);
			if (file.exists()) {
				final MessageDialog message= new MessageDialog(getShell(), RefactoringUIMessages.RefactoringWizard_refactoring, null, Messages.format(ScriptingMessages.CreateRefactoringScriptWizard_overwrite_query, new String[] { ScriptingMessages.CreateRefactoringScriptWizard_merge_button, ScriptingMessages.CreateRefactoringScriptWizard_overwrite_button}), MessageDialog.QUESTION, new String[] { ScriptingMessages.CreateRefactoringScriptWizard_merge_button, ScriptingMessages.CreateRefactoringScriptWizard_overwrite_button, IDialogConstants.CANCEL_LABEL}, 0);
				final int result= message.open();
				if (result == 0) {
					InputStream stream= null;
					try {
						stream= new BufferedInputStream(new FileInputStream(file));
						final RefactoringDescriptorProxy[] existing= RefactoringCore.getRefactoringHistoryService().readRefactoringHistory(stream, RefactoringDescriptor.NONE).getDescriptors();
						final Set set= new HashSet();
						for (int index= 0; index < existing.length; index++)
							set.add(existing[index]);
						for (int index= 0; index < fRefactoringDescriptors.length; index++)
							set.add(fRefactoringDescriptors[index]);
						writable= new RefactoringDescriptorProxy[set.size()];
						set.toArray(writable);
					} catch (FileNotFoundException exception) {
						MessageDialog.openError(getShell(), RefactoringUIMessages.ChangeExceptionHandler_refactoring, exception.getLocalizedMessage());
						return true;
					} catch (CoreException exception) {
						final Throwable throwable= exception.getStatus().getException();
						if (throwable instanceof IOException) {
							MessageDialog.openError(getShell(), RefactoringUIMessages.ChangeExceptionHandler_refactoring, throwable.getLocalizedMessage());
							return true;
						} else {
							RefactoringUIPlugin.log(exception);
							return false;
						}
					} finally {
						if (stream != null) {
							try {
								stream.close();
							} catch (IOException exception) {
								// Do nothing
							}
						}
					}
				} else if (result == 2)
					return false;
			}
			OutputStream stream= null;
			try {
				stream= new BufferedOutputStream(new FileOutputStream(file));
				Arrays.sort(writable, new Comparator() {

					public final int compare(final Object first, final Object second) {
						final RefactoringDescriptorProxy predecessor= (RefactoringDescriptorProxy) first;
						final RefactoringDescriptorProxy successor= (RefactoringDescriptorProxy) second;
						return (int) (predecessor.getTimeStamp() - successor.getTimeStamp());
					}
				});
				RefactoringCore.getRefactoringHistoryService().writeRefactoringDescriptors(writable, stream, RefactoringDescriptor.NONE, new NullProgressMonitor());
				return true;
			} catch (CoreException exception) {
				final Throwable throwable= exception.getStatus().getException();
				if (throwable instanceof IOException) {
					MessageDialog.openError(getShell(), RefactoringUIMessages.ChangeExceptionHandler_refactoring, throwable.getLocalizedMessage());
					return true;
				} else {
					RefactoringUIPlugin.log(exception);
					return false;
				}
			} catch (FileNotFoundException exception) {
				MessageDialog.openError(getShell(), RefactoringUIMessages.ChangeExceptionHandler_refactoring, exception.getLocalizedMessage());
				return true;
			} finally {
				if (stream != null) {
					try {
						stream.close();
					} catch (IOException exception) {
						// Do nothing
					}
				}
			}
		}
		return false;
	}

	/**
	 * Sets the selected refactoring descriptors.
	 * 
	 * @param proxies
	 *            the selected refactoring descriptors
	 */
	public void setRefactoringDescriptors(final RefactoringDescriptorProxy[] proxies) {
		Assert.isNotNull(proxies);
		fRefactoringDescriptors= proxies;
		getContainer().updateButtons();
	}

	/**
	 * Sets the refactoring script location.
	 * 
	 * @param location
	 *            the script location
	 */
	public void setScriptLocation(final URI location) {
		fScriptLocation= location;
		getContainer().updateButtons();
	}
}