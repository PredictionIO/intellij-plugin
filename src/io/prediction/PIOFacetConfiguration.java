package io.prediction;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

public class PIOFacetConfiguration implements FacetConfiguration, PersistentStateComponent<PIOFacetConfiguration.State> {
    @Override
    public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
        return new FacetEditorTab[] {new PIOSettingsEditorTab(this, editorContext)};
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {}

    @Override
    public void writeExternal(Element element) throws WriteExternalException {}

    private State state;

    public void setState(State state) {
        this.state = state;
    }

    @Nullable
    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(State state) {
        this.state = state;
    }

    public static class State {
        public String pioHome;
        public String sparkHome;
    }
}
