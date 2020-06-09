/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.visualvm.sources.options;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.prefs.Preferences;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import org.graalvm.visualvm.core.options.UISupport;
import org.graalvm.visualvm.core.ui.components.SectionSeparator;
import org.graalvm.visualvm.lib.profiler.api.icons.GeneralIcons;
import org.graalvm.visualvm.lib.profiler.api.icons.Icons;
import org.graalvm.visualvm.lib.ui.swing.SmallButton;
import org.graalvm.visualvm.sources.impl.SourceRoots;
import org.graalvm.visualvm.sources.impl.SourceViewers;
import org.graalvm.visualvm.sources.SourcesViewer;
import org.openide.awt.Mnemonics;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;

/**
 *
 * @author Jiri Sedlacek
 */
@NbBundle.Messages({
    "SourcesOptionsPanel_DefinitionsCaption=Definitions",                       // NOI18N
    "SourcesOptionsPanel_Sources=&Source Roots:",                               // NOI18N
    "SourcesOptionsPanel_ViewerCaption=Viewer",                                 // NOI18N
    "SourcesOptionsPanel_OpenIn=&Open sources in:",                             // NOI18N
    "SourcesOptionsPanel_Config=Viewer settings:",                              // NOI18N
    "SourcesOptionsPanel_Add=Add new source root",                              // NOI18N
    "SourcesOptionsPanel_Delete=Delete selected source roots",                  // NOI18N
    "SourcesOptionsPanel_MoveUp=Move selected source root up",                  // NOI18N
    "SourcesOptionsPanel_MoveDown=Move selected source root down",              // NOI18N
    "SourcesOptionsPanel_SelectRootsCaption=Select Source Roots",               // NOI18N
    "SourcesOptionsPanel_SelectButton=Select",                                  // NOI18N
    "SourcesOptionsPanel_SourceDirectoriesFilter=Directories or Archives",      // NOI18N
    "SourcesOptionsPanel_ForcedRoots=Source roots have been set automatically for this session",    // NOI18N
    "SourcesOptionsPanel_ForcedViewer=Sources viewer has been set automatically for this session"   // NOI18N
})
final class SourcesOptionsPanel extends JPanel {
    
    SourcesOptionsPanel() {
        initUI();
    }
    
    
    void load(Preferences settings) {
        rootsForcedHint.setVisible(SourceRoots.areForcedRoots());
        
        rootsListModel = new DefaultListModel();
        for (String root : SourceRoots.getRoots()) rootsListModel.addElement(root);
        rootsList.setModel(rootsListModel);
        rootsList.setEnabled(!rootsForcedHint.isVisible());
        updateRootsButtons();
        
        
        viewerForcedHint.setVisible(SourceViewers.isForcedViewer());
        
        Collection<? extends SourcesViewer> viewers = SourceViewers.getRegisteredViewers();
        viewerSelector.setModel(new DefaultComboBoxModel(viewers.toArray(new SourcesViewer[0])));
        viewerSelector.setEnabled(!viewerForcedHint.isVisible());
        
        SourcesViewer selected = SourceViewers.getSelectedViewer();
        if (selected == null && !viewers.isEmpty()) {
            selected = viewers.iterator().next();
            SourceViewers.saveSelectedViewer(selected);
        }
        
        for (int i = 0; i < viewerSelector.getItemCount(); i++)
            ((SourcesViewer)viewerSelector.getItemAt(i)).loadSettings();
        
        if (selected != null) {
            viewerSelector.setSelectedItem(selected);
            viewerSelected(selected);
        }
    }
    
    void save(Preferences settings) {
        if (!SourceRoots.areForcedRoots()) SourceRoots.saveRoots(getDefinedRoots());
        
        if (!SourceViewers.isForcedViewer()) {
            SourceViewers.saveSelectedViewer(getSelectedViewer());
            
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    for (int i = 0; i < viewerSelector.getItemCount(); i++)
                        ((SourcesViewer)viewerSelector.getItemAt(i)).saveSettings();
                }
            });
        }
    }
    
    void cancel() {}
    
    boolean dirty(Preferences settings) {
        if (!SourceRoots.areForcedRoots()) {
            String[] definedStrings = SourceRoots.getRoots();
            String[] currentStrings = getDefinedRoots();
            if (!Arrays.equals(definedStrings, currentStrings)) return true;
        }
        
        if (!SourceViewers.isForcedViewer()) {
            SourcesViewer selectedViewer = SourceViewers.getSelectedViewer();
            String selectedViewerID = selectedViewer == null ? null : selectedViewer.getID();
            SourcesViewer currentlySelectedViewer = getSelectedViewer();
            String currentlySelectedViewerID = currentlySelectedViewer == null ? null : currentlySelectedViewer.getID();
            if (!Objects.equals(selectedViewerID, currentlySelectedViewerID)) return true;

            for (int i = 0; i < viewerSelector.getItemCount(); i++)
                if (((SourcesViewer)viewerSelector.getItemAt(i)).settingsDirty())
                    return true;
        }
        
        return false;
    }
    
    
    private String[] getDefinedRoots() {
        String[] roots = new String[rootsListModel.size()];
        rootsListModel.copyInto(roots);
        return roots;
    }
    
    
    private SourcesViewer getSelectedViewer() {
        return (SourcesViewer)viewerSelector.getSelectedItem();
    }
    
    
    private void viewerSelected(SourcesViewer viewer) {
        viewerDescription.setText(viewer.getDescription());
        
        viewerSettings.removeAll();
        JComponent settingsComponent = viewer.getSettingsComponent();
        if (settingsComponent != null) viewerSettings.add(settingsComponent, BorderLayout.NORTH);

        
        validate();
        repaint();
    }
    
    
    private void updateRootsButtons() {
        if (rootsForcedHint.isVisible()) {
            addButton.setEnabled(false);
            removeButton.setEnabled(false);
            upButton.setEnabled(false);
            downButton.setEnabled(false);
        } else {
            int[] selectedRows = rootsList.getSelectedIndices();
            int selectedRow = selectedRows.length == 1 ? selectedRows[0] : -1;

            addButton.setEnabled(true);
            removeButton.setEnabled(selectedRows.length > 0);

            if (selectedRow == -1) {
                upButton.setEnabled(false);
                downButton.setEnabled(false);
            } else {
                Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

                upButton.setEnabled(selectedRow > 0);
                downButton.setEnabled(selectedRow < rootsListModel.size() - 1);

                if (upButton == focusOwner && !upButton.isEnabled() && downButton.isEnabled()) downButton.requestFocusInWindow();
                else if (downButton == focusOwner && !downButton.isEnabled() && upButton.isEnabled()) upButton.requestFocusInWindow();
            }
        }
    }
    
    
    private void initUI() {
        setLayout(new GridBagLayout());
        
        GridBagConstraints c;
        int y = 0;
        int htab = 15;
        int vgap = 5;
        
        SectionSeparator definitionsSection = UISupport.createSectionSeparator(Bundle.SourcesOptionsPanel_DefinitionsCaption());
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, vgap * 3, 0);
        add(definitionsSection, c);
        
        JLabel definitionsCaption = new JLabel();
        Mnemonics.setLocalizedText(definitionsCaption, Bundle.SourcesOptionsPanel_Sources());
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y++;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, htab, vgap, 0);
        add(definitionsCaption, c);
        
        rootsListModel = new DefaultListModel();
        rootsList = new JList(rootsListModel);
        rootsList.setVisibleRowCount(0);
        definitionsCaption.setLabelFor(rootsList);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y++;
        c.weightx = 1;
        c.weighty = 1;
        c.gridheight = 5;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, htab, vgap * 3, 0);
        add(new JScrollPane(rootsList), c);
        
        addButton = new SmallButton(Icons.getIcon(GeneralIcons.ADD)) {
            {
                setToolTipText(Bundle.SourcesOptionsPanel_Add());
            }
            protected void fireActionPerformed(ActionEvent e) {
                super.fireActionPerformed(e);
                
                JFileChooser fileChooser = new JFileChooser((String)null);
                fileChooser.setDialogTitle(Bundle.SourcesOptionsPanel_SelectRootsCaption());
                fileChooser.setApproveButtonText(Bundle.SourcesOptionsPanel_SelectButton());
                fileChooser.setMultiSelectionEnabled(true);
                fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                fileChooser.setAcceptAllFileFilterUsed(false);
                fileChooser.addChoosableFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return f.isDirectory() || f.getName().endsWith(".zip") || f.getName().endsWith(".jar"); // NOI18N
                    }
                    @Override
                    public String getDescription() {
                        return Bundle.SourcesOptionsPanel_SourceDirectoriesFilter() + " (*.zip, *.jar)"; // NOI18N
                    }
                });
//                fileChooser.addPropertyChangeListener(new PropertyChangeListener() {
//                    @Override
//                    public void propertyChange(PropertyChangeEvent evt) {
//                        System.err.println(">>> PROPERTY " + evt.getPropertyName() + " changed -- " + evt);
//                    }
//                });

                if (fileChooser.showOpenDialog(WindowManager.getDefault().getMainWindow()) == JFileChooser.APPROVE_OPTION) {
                    String first = null;
                    for (File selected : fileChooser.getSelectedFiles()) {
                        String path = selected.getAbsolutePath();
                        if (!rootsListModel.contains(path)) rootsListModel.addElement(path);
                        if (first == null) first = path;
                    }
                    if (first != null) rootsList.setSelectedValue(first, true);
                }
            }
        };
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = y++;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, htab, 0, 0);
        add(addButton, c);
        
        removeButton = new SmallButton(Icons.getIcon(GeneralIcons.REMOVE)) {
            {
                setToolTipText(Bundle.SourcesOptionsPanel_Delete());
            }
            protected void fireActionPerformed(ActionEvent e) {
                for (Object selected : rootsList.getSelectedValuesList())
                    rootsListModel.removeElement(selected);
            }
        };
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = y++;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, htab, vgap * 2, 0);
        add(removeButton, c);
        
        final boolean[] internalSelectionChange = new boolean[] { false };
        
        upButton = new SmallButton(Icons.getIcon(GeneralIcons.UP)) {
            {
                setToolTipText(Bundle.SourcesOptionsPanel_MoveUp());
            }
            protected void fireActionPerformed(ActionEvent e) {
                int selected = rootsList.getSelectedIndex();
                if (selected < 1) return;
                
                String selectedRoot = rootsListModel.get(selected);
                internalSelectionChange[0] = true;
                try {
                    rootsListModel.remove(selected);
                    rootsListModel.add(selected - 1, selectedRoot);
                } finally {
                    internalSelectionChange[0] = false;
                }
                rootsList.setSelectedValue(selectedRoot, true);
            }
        };
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = y++;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, htab, 0, 0);
        add(upButton, c);
        
        downButton = new SmallButton(Icons.getIcon(GeneralIcons.DOWN)) {
            {
                setToolTipText(Bundle.SourcesOptionsPanel_MoveDown());
            }
            protected void fireActionPerformed(ActionEvent e) {
                int selected = rootsList.getSelectedIndex();
                if (selected == -1 || selected > rootsListModel.size() - 2) return;
                
                String selectedRoot = rootsListModel.get(selected);
                internalSelectionChange[0] = true;
                try {
                    rootsListModel.remove(selected);
                    rootsListModel.add(selected + 1, selectedRoot);
                } finally {
                    internalSelectionChange[0] = false;
                }
                rootsList.setSelectedValue(selectedRoot, true);
            }
        };
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = y++;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, htab, 0, 0);
        add(downButton, c);
        
        rootsForcedHint = new JLabel(Bundle.SourcesOptionsPanel_ForcedRoots(), Icons.getIcon(GeneralIcons.INFO), JLabel.LEADING);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(-vgap, htab, vgap * 3, 0);
        add(rootsForcedHint, c);
        
        SectionSeparator gotoSection = UISupport.createSectionSeparator(Bundle.SourcesOptionsPanel_ViewerCaption());
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, vgap * 3, 0);
        add(gotoSection, c);
        
        final JPanel chooserPanel = new JPanel(new GridBagLayout());
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, htab, vgap * 2, 0);
        add(chooserPanel, c);
        
        JLabel openInLabel = new JLabel();
        Mnemonics.setLocalizedText(openInLabel, Bundle.SourcesOptionsPanel_OpenIn());
        chooserPanel.add(openInLabel);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 0, vgap);
        chooserPanel.add(openInLabel, c);
        
        viewerSelector = new JComboBox();
        openInLabel.setLabelFor(viewerSelector);
        viewerSelector.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) viewerSelected((SourcesViewer)e.getItem());
            }
        });
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 0, htab);
        chooserPanel.add(viewerSelector, c);        
        
        viewerDescription = new JLabel();
        viewerDescription.setEnabled(false);
        c = new GridBagConstraints();
        c.gridx = 2;
        c.gridy = 0;
        c.weightx = 1.0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 0);
        chooserPanel.add(viewerDescription, c);
        
        viewerSettings = new JPanel(new BorderLayout()) {
            public Dimension getMinimumSize() {
                Dimension dim = super.getMinimumSize();
                dim.height = getPreferredSize().height;
                return dim;
            }
            public Dimension getPreferredSize() {
                Dimension dim = super.getPreferredSize();
                dim.height = Math.max(dim.height, chooserPanel.getPreferredSize().height + 10);
                return dim;
            }
        };
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y++;
        c.weightx = 1.0;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, htab, 0, 0);
        add(viewerSettings, c);
        
        viewerForcedHint = new JLabel(Bundle.SourcesOptionsPanel_ForcedViewer(), Icons.getIcon(GeneralIcons.INFO), JLabel.LEADING);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y++;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, htab, 0, 0);
        add(viewerForcedHint, c);
        
        
        ListSelectionListener selection = new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (!internalSelectionChange[0] && !e.getValueIsAdjusting()) updateRootsButtons();
            }
        };
        rootsList.addListSelectionListener(selection);
        updateRootsButtons();
    }
    
    
    private DefaultListModel<String> rootsListModel;
    private JList<String> rootsList;
    private JButton addButton, removeButton, upButton, downButton;
    private JLabel rootsForcedHint;
    
    private JComboBox<SourcesViewer> viewerSelector;
    private JLabel viewerDescription;
    private JPanel viewerSettings;
    private JLabel viewerForcedHint;
    
}
