/*******************************************************************************
 * Copyright (c) 2010 Neil Bartlett.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Neil Bartlett - initial API and implementation
 *******************************************************************************/
package bndtools.wizards.project;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bndtools.api.BndtoolsConstants;
import org.bndtools.api.ProjectLayout;
import org.bndtools.api.ProjectPaths;
import org.bndtools.templating.Template;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ui.wizards.NewJavaProjectWizardPageOne;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.ObjectClassDefinition;

import bndtools.Plugin;

public class NewBndProjectWizardPageOne extends NewJavaProjectWizardPageOne {

    private final ProjectNameGroup nameGroup = new ProjectNameGroup();
    private final ProjectLocationGroup locationGroup = new ProjectLocationGroup("Location");
    private final ProjectLayoutGroup layoutGroup = new ProjectLayoutGroup("Project Layout");
    @SuppressWarnings("unused")
    private Template template;

    NewBndProjectWizardPageOne() {
        setTitle("Create a Bnd OSGi Project");

        nameGroup.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                IStatus status = nameGroup.getStatus();
                if (status.isOK()) {
                    setPageComplete(true);
                    setErrorMessage(null);
                    locationGroup.setProjectName(nameGroup.getProjectName());
                } else {
                    setPageComplete(false);
                    setErrorMessage(status.getMessage());
                }
            }
        });

        locationGroup.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                IStatus status = locationGroup.getStatus();
                setPageComplete(status.isOK());
                if (status.isOK()) {
                    setErrorMessage(null);
                } else {
                    setErrorMessage(status.getMessage());
                }
            }
        });
    }

    @Override
    public String getProjectName() {
        return nameGroup.getProjectName();
    }

    public String getPackageName() {
        return nameGroup.getPackageName();
    }

    @Override
    public URI getProjectLocationURI() {
        IPath location = locationGroup.getLocation();
        if (isDirectlyInWorkspace(location))
            return null;

        return URIUtil.toURI(location);
    }

    private static boolean isDirectlyInWorkspace(IPath location) {
        File wslocation = Platform.getLocation().toFile();
        return location.toFile().getAbsoluteFile().getParentFile().equals(wslocation);
    }

    @Override
    /*
     * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets .Composite) This has been cut
     * and pasted from the superclass because we wish to customize the contents of the page.
     */
    public void createControl(Composite parent) {
        initializeDialogUnits(parent);

        final Composite composite = new Composite(parent, SWT.NULL);
        composite.setFont(parent.getFont());
        composite.setLayout(new GridLayout(1, false));
        composite.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));

        Control nameControl = nameGroup.createControl(composite);
        nameControl.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Control locationControl = locationGroup.createControl(composite);
        locationControl.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Control jreControl = createJRESelectionControl(composite);
        jreControl.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Control layoutControl = layoutGroup.createControl(composite);
        layoutControl.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Control workingSetControl = createWorkingSetControl(composite);
        workingSetControl.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Control infoControl = createInfoControl(composite);
        infoControl.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        setControl(composite);
    }

    @Override
    public IClasspathEntry[] getDefaultClasspathEntries() {
        IClasspathEntry[] entries = super.getDefaultClasspathEntries();
        List<IClasspathEntry> result = new ArrayList<IClasspathEntry>(entries.length + 2);
        result.addAll(Arrays.asList(entries));

        // Add the Bnd classpath container entry
        IPath bndContainerPath = BndtoolsConstants.BND_CLASSPATH_ID;
        IClasspathEntry bndContainerEntry = JavaCore.newContainerEntry(bndContainerPath, false);
        result.add(bndContainerEntry);

        return result.toArray(new IClasspathEntry[result.size()]);
    }

    @Override
    public IClasspathEntry[] getSourceClasspathEntries() {
        IPath projectPath = new Path(getProjectName()).makeAbsolute();

        ProjectPaths projectPaths = ProjectPaths.get(layoutGroup.getProjectLayout());

        List<IClasspathEntry> newEntries = new ArrayList<IClasspathEntry>(2);
        newEntries.add(JavaCore.newSourceEntry(projectPath.append(projectPaths.getSrc()), null, projectPath.append(projectPaths.getBin())));

        boolean enableTestSrcDir;
        try {
            if (template == null)
                enableTestSrcDir = true;
            else {
                ObjectClassDefinition templateMeta = template.getMetadata();
                enableTestSrcDir = findAttribute(templateMeta, ProjectTemplateParam.TEST_SRC_DIR.getString()) != null;
            }
        } catch (Exception e) {
            Plugin.getDefault().getLog().log(new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Error accessing template parameters", e));
            enableTestSrcDir = true;
        }
        if (enableTestSrcDir)
            newEntries.add(JavaCore.newSourceEntry(projectPath.append(projectPaths.getTestSrc()), null, projectPath.append(projectPaths.getTestBin())));

        return newEntries.toArray(new IClasspathEntry[newEntries.size()]);
    }

    private AttributeDefinition findAttribute(ObjectClassDefinition ocd, String name) {
        AttributeDefinition[] attDefs = ocd.getAttributeDefinitions(ObjectClassDefinition.ALL);

        if (attDefs == null)
            return null;

        for (AttributeDefinition attDef : attDefs) {
            if (name.equals(attDef.getName()))
                return attDef;
        }
        return null;
    }

    @Override
    public IPath getOutputLocation() {
        return new Path(getProjectName()).makeAbsolute().append(ProjectPaths.get(layoutGroup.getProjectLayout()).getBin());
    }

    public ProjectLayout getProjectLayout() {
        return layoutGroup.getProjectLayout();
    }

    public void setTemplate(Template template) {
        this.template = template;
    }

}
