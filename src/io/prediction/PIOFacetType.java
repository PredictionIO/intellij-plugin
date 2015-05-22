package io.prediction;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PIOFacetType extends FacetType<Facet<PIOFacetConfiguration>, PIOFacetConfiguration> {
    public PIOFacetType() {
        super(new FacetTypeId<Facet<PIOFacetConfiguration>>("pio"), "prediction-io", "Prediction IO");
    }

    @Override
    public PIOFacetConfiguration createDefaultConfiguration() {
        return new PIOFacetConfiguration();
    }

    @Override
    public Facet<PIOFacetConfiguration> createFacet(@NotNull Module module, String name, @NotNull PIOFacetConfiguration conf, Facet underlying) {
        return new Facet<>(this, module, name, conf, null);
    }

    @Override
    public boolean isSuitableModuleType(ModuleType moduleType) {
        return moduleType instanceof JavaModuleType;
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return IconLoader.getIcon("/logo.png");
    }
}
