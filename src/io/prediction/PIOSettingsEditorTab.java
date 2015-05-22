package io.prediction;

import com.intellij.facet.Facet;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;

public class PIOSettingsEditorTab extends FacetEditorTab {
    private PioSettingsControl panel;
    private PIOFacetConfiguration pioFacetConfiguration;

    public static class PioSettingsControl {
        private TextFieldWithBrowseButton pioHomeChooser;
        private TextFieldWithBrowseButton sparkHomeChooser;
        private JPanel root;

        public PioSettingsControl() {
            FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
            pioHomeChooser.addBrowseFolderListener("PIO home", "Select PIO home", null, descriptor);
            sparkHomeChooser.addBrowseFolderListener("Spark home", "Select Spark home", null, descriptor);
        }
    }

    public PIOSettingsEditorTab(PIOFacetConfiguration facetConfiguration) {
        pioFacetConfiguration = facetConfiguration;
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "PIO Settings";
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    @NotNull
    @Override
    public JComponent createComponent() {
        panel = new PioSettingsControl();
        return panel.root;
    }

    @Override
    public boolean isModified() {
        PIOFacetConfiguration.State state = pioFacetConfiguration.getState();
        return state == null ||
               !state.pioHome.equals(panel.pioHomeChooser.getText()) ||
               !state.sparkHome.equals(panel.sparkHomeChooser.getText());
    }

    @Override
    public void apply() throws ConfigurationException {
        PIOFacetConfiguration.State state = new PIOFacetConfiguration.State();
        String pioConfigured = panel.pioHomeChooser.getText();
        validate(pioConfigured, "pio home");
        state.pioHome = pioConfigured;
        String sparkConfigured = panel.sparkHomeChooser.getText();
        validate(sparkConfigured, "spark home");
        state.sparkHome = sparkConfigured;
        pioFacetConfiguration.setState(state);
    }

    private void validate(String pathConfigured, String subj) throws ConfigurationException {
        if (pathConfigured.isEmpty())
            throw new ConfigurationException(subj + " is not configured.");
        if (!new File(pathConfigured).exists())
            throw new ConfigurationException("Cannot find '" + pathConfigured + "'.");
    }

    @Override
    public void reset() {
        PIOFacetConfiguration.State state = pioFacetConfiguration.getState();
        if (state != null) {
            panel.pioHomeChooser.setText(state.pioHome);
            panel.sparkHomeChooser.setText(state.sparkHome);
        }
    }

    @Override
    public void onFacetInitialized(@NotNull Facet facet) {
        PIOFacetConfiguration.State pioState = pioFacetConfiguration.getState();
        if (pioState != null) {
            Module module = facet.getModule();
            addLibraries(pioState, module);
            addRunConfigurations(module);
        }
    }

    private void addLibraries(PIOFacetConfiguration.State pioState, Module module) {
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        ModifiableRootModel model = rootManager.getModifiableModel();
        LibraryTable libraryTable = model.getModuleLibraryTable();
        ensureLibraryCreated(libraryTable, model, pioState.pioHome, new PrefixFilter("pio-assembly"), "pio-pio");
        ensureLibraryCreated(libraryTable, model, pioState.sparkHome, new PrefixFilter("spark-assembly"), "pio-spark");

        model.commit();
    }

    private void ensureLibraryCreated(LibraryTable libraryTable, ModifiableRootModel model, String home, FilenameFilter filter, String libName) {
        File jar = assembledJar(join(home, "assembly"), filter);
        if (jar == null) jar = assembledJar(join(home, "lib"), filter);
        if (jar != null && libraryTable.getLibraryByName(libName) == null) {
            Library library = libraryTable.createLibrary(libName);
            Library.ModifiableModel libraryModel = library.getModifiableModel();
            try {
                libraryModel.addRoot(jar.toURI().toURL().toString(), OrderRootType.CLASSES);
                libraryModel.commit();
                LibraryOrderEntry entry = model.findLibraryOrderEntry(library);
                assert entry != null;
                entry.setScope(DependencyScope.RUNTIME);
            } catch (MalformedURLException e) {
                // Shouldn't happen.
            }
        }
    }

    private File assembledJar(String dir, FilenameFilter filter) {
        File[] files = new File(dir).listFiles(filter);
        if (files != null && files.length == 1) return files[0];
        return null;
    }

    private static String join(String parent, String dir) {
        if (parent.endsWith("/")) return parent + dir;
        return parent +"/" + dir;
    }


    @Override
    public void disposeUIResources() {
        panel = null;
    }

    private static class PrefixFilter implements FilenameFilter {
        private String prefix;

        private PrefixFilter(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public boolean accept(File dir, String name) {
            return name.startsWith(prefix);
        }
    }

    private void addRunConfigurations(Module module) {
        // todo
    }
}
