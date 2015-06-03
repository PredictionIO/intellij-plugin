package io.prediction;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.facet.Facet;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.FilenameFilter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PIOSettingsEditorTab extends FacetEditorTab {
    private PIOSettingsControl panel;
    private PIOFacetConfiguration pioFacetConfiguration;
    private FacetEditorContext editorContext;

    public static class PIOSettingsControl {
        private TextFieldWithBrowseButton pioHomeChooser;
        private TextFieldWithBrowseButton sparkHomeChooser;
        private JPanel root;

        public PIOSettingsControl() {
            FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
            pioHomeChooser.addBrowseFolderListener("PIO home", "Select PIO home", null, descriptor);
            sparkHomeChooser.addBrowseFolderListener("Spark home", "Select Spark home", null, descriptor);
        }
    }

    public PIOSettingsEditorTab(PIOFacetConfiguration facetConfiguration, FacetEditorContext editorContext) {
        pioFacetConfiguration = facetConfiguration;
        this.editorContext = editorContext;
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
        panel = new PIOSettingsControl();
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
            Module sbtModule = facet.getModule();
            Module runnerModule = addModule(pioState, sbtModule);
            addRunConfigurations(pioState, runnerModule);
        }
    }

    private Module addModule(PIOFacetConfiguration.State pioState, Module sbtModule) {
        ModuleManager moduleManager = ModuleManager.getInstance(sbtModule.getProject());
        String moduleName = "pio-runner-" + sbtModule.getName();
        Module runnerModule = moduleManager.findModuleByName(moduleName);
        if (runnerModule == null) {
            VirtualFile moduleFile = sbtModule.getModuleFile();
            assert moduleFile != null;
            String imlPath = moduleFile.getParent().getPath() + "/" + moduleName + ".iml";
            runnerModule = moduleManager.newModule(imlPath, JavaModuleType.getModuleType().getId());
            ModifiableRootModel model = ModuleRootManager.getInstance(runnerModule).getModifiableModel();
            model.addModuleOrderEntry(sbtModule);
            model.setSdk(ModuleRootManager.getInstance(sbtModule).getSdk());
            model.commit();
            ensureLibraryCreated(pioState.pioHome, new PrefixFilter("pio-assembly"), "pio-pio", runnerModule);
            ensureLibraryCreated(pioState.sparkHome, new PrefixFilter("spark-assembly"), "pio-spark", runnerModule);
        }
        return runnerModule;
    }

    private void ensureLibraryCreated(String home, FilenameFilter filter, String libName, Module module) {
        File jar = assembledJar(join(home, "assembly"), filter);
        if (jar == null) jar = assembledJar(join(home, "lib"), filter);
        Library library = editorContext.findLibrary(libName);
        if (jar != null && library == null) {
            VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(jar);
            library = editorContext.createProjectLibrary(libName, new VirtualFile[]{vFile}, VirtualFile.EMPTY_ARRAY);
            Library.ModifiableModel libraryModel = library.getModifiableModel();
            libraryModel.addRoot("file://" + jar.getAbsolutePath(), OrderRootType.CLASSES);
            libraryModel.commit();
        }
        ModuleRootModificationUtil.addDependency(module, library, DependencyScope.RUNTIME, false);
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

    private void addRunConfigurations(PIOFacetConfiguration.State pioState, Module module) {
        addRunConfiguration(module, pioState, "pio train",
                "--engine-id dummy --engine-version dummy --engine-variant engine.json",
                "io.prediction.workflow.CreateWorkflow");
        addRunConfiguration(module, pioState, "pio deploy",
                "--engineId dummy --engineVersion dummy --engineInstanceId dummy",
                "io.prediction.workflow.CreateServer");
    }

    private void addRunConfiguration(Module module, PIOFacetConfiguration.State pioState, String confName,
                                     String programArgs, String mainClass) {
        RunManager runManager = RunManager.getInstance(module.getProject());
        List<RunConfiguration> existing = runManager.getAllConfigurationsList();
        for (RunConfiguration configuration : existing) {
            if (confName.equals(configuration.getName())) return;
        }

        ConfigurationFactory factory = ApplicationConfigurationType.getInstance().getConfigurationFactories()[0];
        RunnerAndConfigurationSettings configurationSettings = runManager.createRunConfiguration(confName, factory);
        ApplicationConfiguration configuration = (ApplicationConfiguration) configurationSettings.getConfiguration();
        configuration.setMainClassName(mainClass);
        String vmParameters = String.format("-Dspark.master=local -Dlog4j.configuration=file:%s",
                join(pioState.pioHome, "conf/log4j.properties"));
        configuration.setVMParameters(vmParameters);
        configuration.setProgramParameters(programArgs);
        configuration.setModule(module);

        Map<String, String> env = new HashMap<>();
        String pioStore = join(pioState.pioHome, ".pio_store");
        env.put("SPARK_HOME", pioState.sparkHome);
        env.put("PIO_FS_BASEDIR", pioStore);
        env.put("PIO_FS_ENGINESDIR", pioStore + "/engines");
        env.put("PIO_FS_TMPDIR", pioStore + "/tmp");
        env.put("PIO_STORAGE_SOURCES_LOCALFS_HOSTS", pioStore + "/models");
        env.put("PIO_STORAGE_REPOSITORIES_METADATA_NAME", "predictionio_metadata");
        env.put("PIO_STORAGE_REPOSITORIES_METADATA_SOURCE", "ELASTICSEARCH");
        env.put("PIO_STORAGE_REPOSITORIES_MODELDATA_NAME", "pio");
        env.put("PIO_STORAGE_REPOSITORIES_MODELDATA_SOURCE", "LOCALFS");
        env.put("PIO_STORAGE_REPOSITORIES_APPDATA_NAME", "predictionio_appdata");
        env.put("PIO_STORAGE_REPOSITORIES_APPDATA_SOURCE", "ELASTICSEARCH");
        env.put("PIO_STORAGE_REPOSITORIES_EVENTDATA_NAME", "predictionio_eventdata");
        env.put("PIO_STORAGE_REPOSITORIES_EVENTDATA_SOURCE", "HBASE");
        env.put("PIO_STORAGE_SOURCES_ELASTICSEARCH_TYPE", "elasticsearch");
        env.put("PIO_STORAGE_SOURCES_ELASTICSEARCH_HOSTS", "localhost");
        env.put("PIO_STORAGE_SOURCES_ELASTICSEARCH_PORTS", "9300");
        env.put("PIO_STORAGE_SOURCES_LOCALFS_TYPE", "localfs");
        env.put("PIO_STORAGE_SOURCES_LOCALFS_PORTS", "0");
        env.put("PIO_STORAGE_SOURCES_HBASE_TYPE", "hbase");
        env.put("PIO_STORAGE_SOURCES_HBASE_HOSTS", "0");
        env.put("PIO_STORAGE_SOURCES_HBASE_PORTS", "0");
        configuration.setEnvs(env);

        runManager.addConfiguration(configurationSettings, false);
    }
}
