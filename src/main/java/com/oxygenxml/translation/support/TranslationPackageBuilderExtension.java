package com.oxygenxml.translation.support;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;

import org.apache.log4j.Logger;

import com.oxygenxml.translation.support.core.ChangeDetector;
import com.oxygenxml.translation.support.util.ZipFileUtil;

import ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension;
import ro.sync.exml.workspace.api.editor.WSEditor;
import ro.sync.exml.workspace.api.editor.page.ditamap.WSDITAMapEditorPage;
import ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace;
import ro.sync.exml.workspace.api.standalone.actions.MenusAndToolbarsContributorCustomizer;

/**
 * Plugin extension - workspace access extension.
 */
public class TranslationPackageBuilderExtension implements WorkspaceAccessPluginExtension {
  /**
   * Logger for logging.
   */
  private static Logger logger = Logger.getLogger(TranslationPackageBuilderExtension.class); 

  /**
   * Predefined name of the file that stores a hash for each file.
   */
  private final static String MILESTONE_FILE_NAME = "milestone.xml";
  /**
   * @see ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension#applicationStarted(ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace)
   */
  public void applicationStarted(final StandalonePluginWorkspace pluginWorkspaceAccess) {
    //You can set or read global options.
    //The "ro.sync.exml.options.APIAccessibleOptionTags" contains all accessible keys.
    //		  pluginWorkspaceAccess.setGlobalObjectProperty("can.edit.read.only.files", Boolean.FALSE);
    // Check In action

    //You can access the content inside each opened WSEditor depending on the current editing page (Text/Grid or Author).  
    // A sample action which will be mounted on the main menu, toolbar and contextual menu.
    
    final Action generateMilestoneAction = createMilestoneAction(pluginWorkspaceAccess);
    final Action generateChangedFilesZipAction = createChangedFilesZipAction(pluginWorkspaceAccess);
    final Action applyTranslatedFilesAction = createApplyTranslatedFilesAction(pluginWorkspaceAccess);

    //Mount the action on the contextual menus for the Text and Author modes.
    pluginWorkspaceAccess.addMenusAndToolbarsContributorCustomizer(new MenusAndToolbarsContributorCustomizer() {

      @Override
      public void customizeDITAMapPopUpMenu(JPopupMenu popUp, WSDITAMapEditorPage ditaMapEditorPage) {
        //Create a submenu "Translation Package Builder" for the 3 actions.
        // Tooltips for all actions.
        JMenu submenu = new JMenu("Translation Package Builder");
        submenu.setMnemonic(KeyEvent.VK_S);
        
        // Action 1: Generate Milestone
        JMenuItem menuItemMilestone = new JMenuItem("Generate Milestone");
        menuItemMilestone.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_2, ActionEvent.ALT_MASK));
        menuItemMilestone.addActionListener(generateMilestoneAction);
        menuItemMilestone.setToolTipText("Generates a predefined file called \"milestone.xml\" in the root directory of the selected"
            + " ditamap which contains an unique hash and the relative path of every file in that directory.");
        
        // Action 2: Create Changed Files Package
        JMenuItem menuItemPakage = new JMenuItem("Create Modified Files Pakage");
        menuItemPakage.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_3, ActionEvent.ALT_MASK));
        menuItemPakage.addActionListener(generateChangedFilesZipAction);
        menuItemPakage.setToolTipText("Creates a package with all the files that were modified (after generating a milestone.xml file) at a chosen location.");
        
        // Action 3: Unzip package that came from translation.
        JMenuItem menuItemApply = new JMenuItem("Apply Package");
        menuItemApply.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_4, ActionEvent.ALT_MASK));
        menuItemApply.addActionListener(applyTranslatedFilesAction);
        menuItemApply.setToolTipText("Applies a chosen archive over the root directory of the current ditamap.");
        
        submenu.add(menuItemMilestone);
        submenu.add(menuItemPakage);
        submenu.add(menuItemApply);
        
        popUp.add(submenu);

      }
    });
  }

  /**
   * Creates an action that store the state of the files.
   * 
   * @param pluginWorkspaceAccess  Entry point for accessing the DITA Maps area
   * @return A new action called "Generate Milestone"
   */
  private AbstractAction createMilestoneAction(
      final StandalonePluginWorkspace pluginWorkspaceAccess) {
    return new AbstractAction("Generate Milestone") {
      public void actionPerformed(ActionEvent actionevent) {

        // 1. Extract the parent directory of the current map.
        // 2. Generate the milestone file in the dir

        WSEditor editor = pluginWorkspaceAccess.getCurrentEditorAccess(StandalonePluginWorkspace.DITA_MAPS_EDITING_AREA);

        try {
          URL editorLocation = editor.getEditorLocation();

          File rootDir = new File(editorLocation.getPath()).getParentFile();

          ChangeDetector.generateChangeMilestone(rootDir);

          pluginWorkspaceAccess.showInformationMessage("Milestone created at: " + rootDir);
        } catch (Exception e) {
          // Present the error to the user.
          logger.error(e, e);
          pluginWorkspaceAccess.showErrorMessage("Milestone creation failed because of: " + e.getMessage());
        }
      }
    };
  }

  /**
   * Creates an action that packs the changed files into an archive.
   * 
   * @param pluginWorkspaceAccess  Entry point for accessing the DITA Maps area
   * @return  A new action called "Create Modified Files Package"
   */
  private AbstractAction createChangedFilesZipAction(
      final StandalonePluginWorkspace pluginWorkspaceAccess) {
    return new AbstractAction("Create Modified Files Package") {
      public void actionPerformed(ActionEvent actionevent) {

        File chosenDir = pluginWorkspaceAccess.chooseFile("Package location", new String[] {"zip"}, "Zip files", true);

        if(chosenDir != null){
          WSEditor editor = pluginWorkspaceAccess.getCurrentEditorAccess(StandalonePluginWorkspace.DITA_MAPS_EDITING_AREA);

          // 1. Extract the parent directory of the current map. This is the rootDir
          URL editorLocation = editor.getEditorLocation();
          File rootDir = new File(editorLocation.getPath()).getParentFile();

          try {
            // What to do if the milestone file doesn't exist? 
            // Inform the user and offer the possibility to pack the entire dir
            File milestoneFile = new File(rootDir,  MILESTONE_FILE_NAME);
            if(!milestoneFile.exists()){

              int buttonId = pluginWorkspaceAccess.showConfirmDialog("Didn't find \"milestone.xml\"", "The milestone file doesn't exist."
                  + " Do you want to pack the entire directory?", new String[] {"Yes", "No"}, new int[] {0, 1});

              if(buttonId == 0){
                File chosenDirectory = pluginWorkspaceAccess.chooseFile("Package location", new String[] {"zip"}, "Zip files", true);

                try {
                  ZipFileUtil.zipDirectory(rootDir, chosenDirectory);
                  pluginWorkspaceAccess.showInformationMessage("Package created.");
                } catch (IOException e1) {
                  logger.error(e1, e1);
                  pluginWorkspaceAccess.showErrorMessage("Package creation failed because of: " + e1.getMessage());
                }
              }

            }else{
              ChangeDetector.generateChangedFilesPackage(rootDir, chosenDir);
              pluginWorkspaceAccess.showInformationMessage("Package created.");
            }
          } catch (Exception e) {
            //  Preset error to user.
            logger.error(e, e);
            pluginWorkspaceAccess.showErrorMessage("Package creation failed because of: " + e.getMessage());
          }
        }
      }
    };
  }

  /**
   * Creates the action that applies the translated files.
   * 
   * @param pluginWorkspaceAccess Entry point for accessing the DITA Maps area
   * 
   * @return  A new action called "Apply Package"
   */
  private AbstractAction createApplyTranslatedFilesAction(
      final StandalonePluginWorkspace pluginWorkspaceAccess) {
    return new AbstractAction("Apply Package") {
      /**
       * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
       */
      public void actionPerformed(ActionEvent actionevent) {

        /**
         * Flow.
         * 
         * 1. The user opens map-french.ditamap.
         * 2. From the contextual menu chooses "Apply package"
         * 3. The action presents a chooser so that the user can select the ZIP
         * 4. Unzip the package in the root directory of the map
         */

        File chosenDir = pluginWorkspaceAccess.chooseFile("Choose the translated package", new String[] {"zip"},  null);

        if(chosenDir != null) {
          WSEditor editor = pluginWorkspaceAccess.getCurrentEditorAccess(StandalonePluginWorkspace.DITA_MAPS_EDITING_AREA);

          URL editorLocation = editor.getEditorLocation();
          File rootDir = new File(editorLocation.getFile()).getParentFile();

          try {
            //Perhaps present a log with the overridden files.
            ArrayList<String> list = ZipFileUtil.unzipDirectory(chosenDir, rootDir);

            JTextArea text = new JTextArea(10, 40);

            //text.setText("Package applied over the current map. The overridden files are : ");
            for(String path : list){
              text.append(path);
              if(!path.equals(list.get(list.size()-1))){
                text.append("\n");
              }
            }
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setEditable(false);

            JScrollPane scroll = new JScrollPane(text);
            scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

            JPanel panel = new JPanel(new GridBagLayout());
            
            GridBagConstraints gbcLabel = new GridBagConstraints();
            gbcLabel.gridx = 0;
            gbcLabel.gridy = 0;
            gbcLabel.gridwidth = 1;
            gbcLabel.gridheight = 1;
            gbcLabel.weightx = 0;
            gbcLabel.weighty = 0;
            gbcLabel.fill = GridBagConstraints.HORIZONTAL;
            gbcLabel.anchor = GridBagConstraints.NORTH;
            
            panel.add(new JLabel("Package applied over the current map. The overridden files are : "), gbcLabel);
            
            GridBagConstraints gbcScroll = new GridBagConstraints();
            gbcScroll.gridx = 0;
            gbcScroll.gridy = 1;
            gbcLabel.gridwidth = 1;
            gbcLabel.gridheight = 1;
            gbcLabel.weightx = 0;
            gbcLabel.weighty = 0;
            gbcScroll.fill = GridBagConstraints.BOTH;
            gbcScroll.anchor = GridBagConstraints.LINE_START;
            panel.add(scroll , gbcScroll);
            

            JOptionPane.showMessageDialog((JFrame) pluginWorkspaceAccess.getParentFrame(), panel, "Applied files", JOptionPane.INFORMATION_MESSAGE);
          } catch (Exception e) {
            // Preset error to user.
            logger.error(e, e);
            pluginWorkspaceAccess.showErrorMessage("Failed to apply package because of: " + e.getMessage());
          }
        } 
      }
    };
  }



  /**
   * @see ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension#applicationClosing()
   */
  public boolean applicationClosing() {
    //You can reject the application closing here
    return true;
  }
}