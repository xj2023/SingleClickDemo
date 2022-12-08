package com.android.singleclick

import org.gradle.api.Plugin
import org.gradle.api.Project
import com.android.build.gradle.AppExtension

class SingleClickPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        println("apply SingleClickPlugin ${this.class.getSimpleName()}")
        SingleClickExtension extension  = project.extensions.create("singleClick",SingleClickExtension)
        Iterator<PluginProvider> pluginProviderList = ServiceLoader.load(PluginProvider.class).iterator()
        while (pluginProviderList.hasNext()){
            PluginProvider pluginProvider = pluginProvider.next()
            project.plugins.apply(it.getPlugin())
        }
        // 注册 transform
        project.extensions.getByType(AppExtension.class).registerTransform(new SingleClickTransform(extension))
    }
}